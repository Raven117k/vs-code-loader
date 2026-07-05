package com.example.proot

import android.content.Context
import android.os.Build
import com.example.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

class RootfsBootstrapper(private val context: Context) {

    private val client = OkHttpClient()

    private val _progress = MutableStateFlow<Float>(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _status = MutableStateFlow<String>("")
    val status: StateFlow<String> = _status.asStateFlow()

    private val filesDir: File = context.filesDir
    private val nativeLibDir = context.applicationInfo.nativeLibraryDir
    val prootBinary = File(nativeLibDir, "libproot.so")
    val prootLoader = File(nativeLibDir, "libprootloader.so")
    val rootfsDir = File(filesDir, "rootfs")
    val tmpDir = File(filesDir, "tmp")

    init {
        if (!rootfsDir.exists()) rootfsDir.mkdirs()
        if (!tmpDir.exists()) tmpDir.mkdirs()
    }

    fun isSetupComplete(): Boolean {
        val completeFlag = File(filesDir, ".setup_complete")
        return completeFlag.exists() && prootBinary.exists() && prootLoader.exists() && rootfsDir.exists() && rootfsDir.list()?.isNotEmpty() == true
    }

    private fun verifyBundledProotBinaries(): Boolean {
        val available = prootBinary.exists() && prootBinary.canExecute() && prootLoader.exists() && prootLoader.canExecute()
        if (!available) {
            AppLogger.log(
                "Bootstrapper",
                "proot binaries missing from native lib dir: ${prootBinary.absolutePath}, ${prootLoader.absolutePath}"
            )
        }
        return available
    }

    fun getDeviceAbi(): String {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        return if (abi.contains("x86_64")) "x86_64" else "arm64"
    }

    fun getDefaultRootfsUrl(distro: String): String {
        val abi = getDeviceAbi()
        return if (distro == "Alpine") {
            if (abi == "x86_64") {
                "https://dl-cdn.alpinelinux.org/alpine/v3.19/releases/x86_64/alpine-minirootfs-3.19.1-x86_64.tar.gz"
            } else {
                "https://dl-cdn.alpinelinux.org/alpine/v3.19/releases/aarch64/alpine-minirootfs-3.19.1-aarch64.tar.gz"
            }
        } else {
            val version = "v1.10.1"
            val distroAbi = if (abi == "x86_64") "x86_64" else "aarch64"
            "https://github.com/termux/proot-distro/releases/download/$version/debian-$distroAbi-pd-$version.tar.xz"
        }
    }

    suspend fun bootstrap(distro: String, customRootfsUrl: String? = null): Boolean = withContext(Dispatchers.IO) {
        AppLogger.log("Bootstrapper", "Starting bootstrap process for $distro...")
        _progress.value = 0f
        
        val completeFlag = File(filesDir, ".setup_complete")
        if (completeFlag.exists()) completeFlag.delete()
        
        try {
            _status.value = "Checking bundled proot binaries..."
            if (!verifyBundledProotBinaries()) {
                _status.value = "proot binaries missing from native lib dir — check jniLibs packaging"
                AppLogger.log("Bootstrapper", "Bundled proot binaries are missing or not executable")
                return@withContext false
            }

            AppLogger.log("Bootstrapper", "Using bundled proot binaries from $nativeLibDir")

            // 1. Download Linux rootfs
            val rootfsUrl = customRootfsUrl ?: getDefaultRootfsUrl(distro)
            _status.value = "Downloading $distro rootfs (~5MB - 50MB)..."
            AppLogger.log("Bootstrapper", "Downloading rootfs from $rootfsUrl")
            
            val archiveExt = if (distro == "Alpine") "tar.gz" else "tar.xz"
            val archiveFile = File(tmpDir, "rootfs.$archiveExt")
            if (archiveFile.exists()) archiveFile.delete()

            val rootfsDownloaded = downloadFile(rootfsUrl, archiveFile)
            if (!rootfsDownloaded) {
                AppLogger.log("Bootstrapper", "Failed to download rootfs archive")
                return@withContext false
            }

            // 3. Extract rootfs
            _status.value = "Extracting rootfs (this may take a moment)..."
            AppLogger.log("Bootstrapper", "Extracting $archiveFile to $rootfsDir")
            
            if (rootfsDir.exists()) {
                rootfsDir.deleteRecursively()
            }
            rootfsDir.mkdirs()

            val success = extractTarball(archiveFile, rootfsDir, archiveExt)
            if (success) {
                ensureRootfsFallbackDirs()
                normalizeRootfsPermissions()
                File(filesDir, ".setup_complete").createNewFile()
                AppLogger.log("Bootstrapper", "Rootfs setup complete!")
                _status.value = "Setup completed successfully!"
                _progress.value = 1.0f
                true
            } else {
                AppLogger.log("Bootstrapper", "Failed to extract rootfs archive")
                _status.value = "Extraction failed"
                false
            }
        } catch (e: Exception) {
            AppLogger.log("Bootstrapper", "Error during bootstrap: ${e.message}")
            _status.value = "Error: ${e.message}"
            false
        }
    }

    private suspend fun downloadFile(urlStr: String, destination: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(urlStr).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    AppLogger.log("Bootstrapper", "Download failed: Server returned ${response.code}")
                    return@use false
                }

                val body = response.body
                if (body == null) {
                    AppLogger.log("Bootstrapper", "Download failed: Empty response body")
                    return@use false
                }

                val totalBytes = body.contentLength()
                var downloadedBytes = 0L
                val buffer = ByteArray(8192)
                var bytesRead: Int

                body.byteStream().use { inputStream ->
                    FileOutputStream(destination).use { outputStream ->
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            if (totalBytes > 0) {
                                val progressPercent = downloadedBytes.toFloat() / totalBytes
                                _progress.value = progressPercent
                            }
                        }
                    }
                }
                true
            }
        } catch (e: Exception) {
            AppLogger.log("Bootstrapper", "Download exception: ${e.message}")
            false
        }
    }

    private fun extractTarball(archive: File, destinationDir: File, ext: String): Boolean {
        try {
            AppLogger.log("Bootstrapper", "Executing system tar command to extract rootfs...")
            val tarFlag = if (ext == "tar.xz") "-xJf" else "-xzf"
            val process = ProcessBuilder()
                .command(
                    "tar",
                    "--no-same-owner",
                    tarFlag,
                    archive.absolutePath,
                    "-C",
                    destinationDir.absolutePath
                )
                .redirectErrorStream(true)
                .start()

            val reader = process.inputStream.bufferedReader()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                AppLogger.log("Tar", line ?: "")
            }

            val exitCode = process.waitFor()
            AppLogger.log("Bootstrapper", "Tar finished with exit code: $exitCode")

            if (archive.exists()) {
                archive.delete()
            }

            return exitCode == 0
        } catch (e: Exception) {
            AppLogger.log("Bootstrapper", "Tar extraction exception: ${e.message}")
            return false
        }
    }

    private fun ensureRootfsFallbackDirs() {
        File(rootfsDir, "tmp").apply { if (!exists()) mkdirs() }
        File(rootfsDir, "var/tmp").apply { if (!exists()) mkdirs() }
        File(rootfsDir, "run").apply { if (!exists()) mkdirs() }
        File(rootfsDir, "root").apply { if (!exists()) mkdirs() }
    }

    private fun normalizeRootfsPermissions() {
        AppLogger.log("Bootstrapper", "Normalizing executable permissions for rootfs files...")
        normalizeDirectory(rootfsDir)
    }

    private fun normalizeDirectory(directory: File) {
        directory.listFiles()?.forEach { entry ->
            if (entry.isDirectory) {
                normalizeDirectory(entry)
            } else {
                val canExecute = entry.canExecute()
                if (canExecute) {
                    entry.setWritable(false, false)
                    entry.setExecutable(true, false)
                    AppLogger.log("Bootstrapper", "Normalized exec perms for ${entry.absolutePath}")
                }
            }
        }
    }
}

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
    val prootDir = File(filesDir, "proot")
    val prootBinary = File(prootDir, "proot")
    val prootLoader = File(prootDir, "loader")
    val rootfsDir = File(filesDir, "rootfs")
    val tmpDir = File(filesDir, "tmp")

    init {
        if (!prootDir.exists()) prootDir.mkdirs()
        if (!rootfsDir.exists()) rootfsDir.mkdirs()
        if (!tmpDir.exists()) tmpDir.mkdirs()
    }

    fun isSetupComplete(): Boolean {
        val completeFlag = File(filesDir, ".setup_complete")
        return completeFlag.exists() && prootBinary.exists() && prootLoader.exists() && rootfsDir.exists() && rootfsDir.list()?.isNotEmpty() == true
    }

    fun getDeviceAbi(): String {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        return if (abi.contains("x86_64")) "x86_64" else "arm64"
    }

    fun getProotUrl(): String {
        val abi = getDeviceAbi()
        return if (abi == "x86_64") {
            "https://github.com/Raven117k/proot/releases/download/v1.0-proot/proot-x86_64"
        } else {
            "https://github.com/Raven117k/proot/releases/download/v1.0-proot/proot-aarch64"
        }
    }

    fun getProotLoaderUrl(): String {
        val abi = getDeviceAbi()
        return if (abi == "x86_64") {
            "https://github.com/Raven117k/proot/releases/download/v1.0-proot/proot-loader-x86_64"
        } else {
            "https://github.com/Raven117k/proot/releases/download/v1.0-proot/proot-loader-aarch64"
        }
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
            // 1. Download proot binary + loader
            if (!prootBinary.exists()) {
                val prootUrl = getProotUrl()
                _status.value = "Downloading proot binary..."
                AppLogger.log("Bootstrapper", "Downloading proot from $prootUrl")
                val prootDownloaded = downloadFile(prootUrl, prootBinary)
                if (!prootDownloaded) {
                    AppLogger.log("Bootstrapper", "Failed to download proot binary")
                    return@withContext false
                }
                prootBinary.setExecutable(true, false)
                AppLogger.log("Bootstrapper", "proot binary downloaded and marked executable")
            }

            if (!prootLoader.exists()) {
                val loaderUrl = getProotLoaderUrl()
                _status.value = "Downloading proot loader..."
                AppLogger.log("Bootstrapper", "Downloading loader from $loaderUrl")
                val loaderDownloaded = downloadFile(loaderUrl, prootLoader)
                if (!loaderDownloaded) {
                    AppLogger.log("Bootstrapper", "Failed to download proot loader")
                    return@withContext false
                }
                prootLoader.setExecutable(true, false)
                AppLogger.log("Bootstrapper", "proot loader downloaded and marked executable")
            }

            // 2. Download Linux rootfs
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
}
package com.example.termux

import android.content.Context
import android.os.Build
import android.system.Os
import com.example.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipInputStream

class TermuxBootstrapper(private val context: Context) {
    private val env = TermuxEnvironment(context)

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()
    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status.asStateFlow()

    fun isSetupComplete(): Boolean {
        val flag = File(context.filesDir, ".termux_setup_complete")
        return flag.exists() && env.isReady()
    }

    private fun getBootstrapPayload(): File {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        val abiFolder = when {
            abi.contains("x86_64") -> "x86_64"
            abi.contains("arm64") || abi.contains("aarch64") -> "arm64-v8a"
            else -> "arm64-v8a"
        }
        val candidate = File(context.filesDir.parentFile, "app/src/main/jniLibs/$abiFolder/libtermuxbootstrap.so")
        return if (candidate.exists()) candidate else File(context.applicationInfo.nativeLibraryDir, "libtermuxbootstrap.so")
    }

    suspend fun bootstrap(distro: String = "Alpine", customRootfsUrl: String? = null): Boolean = withContext(Dispatchers.IO) {
        AppLogger.log("TermuxBootstrapper", "Starting Termux-native bootstrap...")
        _progress.value = 0f
        if (customRootfsUrl != null) {
            AppLogger.log("TermuxBootstrapper", "Ignoring custom rootfs URL for Termux native bootstrap: $customRootfsUrl")
        }
        try {
            _status.value = "Preparing environment..."
            env.ensureLayout()

            _status.value = "Checking bundled Termux payload..."
            val bootstrapPayload = getBootstrapPayload()
            if (!bootstrapPayload.exists()) {
                AppLogger.log("TermuxBootstrapper", "Termux bootstrap payload missing: ${bootstrapPayload.absolutePath}")
                _status.value = "Setup failed — bootstrap payload missing"
                return@withContext false
            }
            AppLogger.log("TermuxBootstrapper", "Using bundled Termux payload ${bootstrapPayload.absolutePath}")

            _status.value = "Extracting Termux bootstrap..."
            ZipInputStream(bootstrapPayload.inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val outFile = File(env.usrDir, entry.name)
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        outFile.outputStream().use { zip.copyTo(it) }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            _progress.value = 0.6f

            _status.value = "Creating symlinks..."
            processSymlinks(env.usrDir)
            _progress.value = 0.8f

            fixExecutablePermissions(env.usrDir)

            _status.value = "Verifying Termux binaries..."
            if (!env.isReady()) {
                AppLogger.log("TermuxBootstrapper", "Termux binaries missing after extraction")
                _status.value = "Setup failed — binaries missing"
                return@withContext false
            }

            File(context.filesDir, ".termux_setup_complete").createNewFile()
            _status.value = "Setup completed successfully!"
            _progress.value = 1.0f
            AppLogger.log("TermuxBootstrapper", "Termux bootstrap complete!")
            true
        } catch (e: Exception) {
            AppLogger.log("TermuxBootstrapper", "Error during bootstrap: ${e.message}")
            _status.value = "Error: ${e.message}"
            false
        }
    }

    private fun processSymlinks(usrDir: File) {
        val symlinksFile = File(usrDir, "SYMLINKS.txt")
        if (!symlinksFile.exists()) {
            AppLogger.log("TermuxBootstrapper", "No SYMLINKS.txt found — skipping symlink step")
            return
        }
        symlinksFile.forEachLine { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEachLine

            val parts = line.split("←").takeIf { it.size == 2 }
                ?: line.split("<-").takeIf { it.size == 2 }

            if (parts == null) {
                AppLogger.log("TermuxBootstrapper", "Malformed symlink line, skipping: $line")
                return@forEachLine
            }

            val (target, linkRelative) = parts
            val linkFile = File(usrDir, linkRelative)
            linkFile.parentFile?.mkdirs()
            if (linkFile.exists()) linkFile.delete()

            try {
                Os.symlink(target, linkFile.absolutePath)
            } catch (e: Exception) {
                AppLogger.log("TermuxBootstrapper", "Symlink failed ($target -> ${linkFile.absolutePath}): ${e.message}")
            }
        }
        symlinksFile.delete()
    }

    private fun fixExecutablePermissions(directory: File) {
        directory.walkTopDown().forEach { file ->
            if (file.isFile && (file.parentFile?.name == "bin" || file.parentFile?.name == "local" || file.parentFile?.name == "lib")) {
                file.setExecutable(true, false)
            }
        }
    }
}
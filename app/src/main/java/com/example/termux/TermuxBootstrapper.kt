package com.example.termux

import android.content.Context
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
    private val nativeLibDir = context.applicationInfo.nativeLibraryDir

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()
    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status.asStateFlow()

    fun isSetupComplete(): Boolean {
        val flag = File(context.filesDir, ".termux_setup_complete")
        return flag.exists() && env.isReady()
    }

    private fun verifyBootstrapPayload(): File? {
        val bootstrapZip = File(nativeLibDir, "libtermuxbootstrap.so")
        if (!bootstrapZip.exists()) {
            AppLogger.log("TermuxBootstrapper", "libtermuxbootstrap.so missing from native lib dir: ${bootstrapZip.absolutePath}")
            return null
        }
        return bootstrapZip
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
            val bootstrapZip = verifyBootstrapPayload()
            if (bootstrapZip == null) {
                _status.value = "Setup failed — libtermuxbootstrap.so missing from native lib dir"
                return@withContext false
            }
            AppLogger.log("TermuxBootstrapper", "Using bundled bootstrap from ${bootstrapZip.absolutePath}")

            _status.value = "Extracting Termux bootstrap..."
            ZipInputStream(bootstrapZip.inputStream()).use { zip ->
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
            _progress.value = 0.7f
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

    private fun fixExecutablePermissions(directory: File) {
        directory.walkTopDown().forEach { file ->
            if (file.isFile && (file.parentFile?.name == "bin" || file.parentFile?.name == "local" || file.parentFile?.name == "lib")) {
                file.setExecutable(true, false)
            }
        }
    }
}
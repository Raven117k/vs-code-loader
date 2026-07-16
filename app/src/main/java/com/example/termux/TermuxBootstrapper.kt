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

    suspend fun bootstrap(): Boolean = withContext(Dispatchers.IO) {
        AppLogger.log("TermuxBootstrapper", "Starting Termux-native bootstrap...")
        _progress.value = 0f

        try {
            _status.value = "Preparing environment..."
            // Wipe any stale usr/ from a previous failed/partial bootstrap so leftover
            // files or dangling symlinks (e.g. bin/su) can never survive into a retry.
            if (env.usrDir.exists()) {
                env.usrDir.deleteRecursively()
            }
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
                        // File.exists() follows symlinks and returns false for a dangling
                        // one, so a stale broken link here would slip past a plain exists()
                        // check and then fail with ENOENT when opened for writing. Always
                        // delete whatever's at this path first — File.delete() acts on the
                        // link itself, not its target, so this is safe either way.
                        outFile.delete()
                        outFile.outputStream().use { zip.copyTo(it) }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            _progress.value = 0.5f

            _status.value = "Creating symlinks..."
            processSymlinks(env.usrDir)
            _progress.value = 0.7f

            _status.value = "Patching hardcoded Termux paths..."
            patchHardcodedPrefix(env.usrDir)
            _progress.value = 0.85f

            _status.value = "Restoring executable permissions..."
            fixExecutablePermissions(env.usrDir)
            _progress.value = 0.95f

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

    /**
     * Termux's own shell scripts (pkg, apt wrappers, etc.) have their interpreter
     * path hardcoded at build time to /data/data/com.termux/files/usr/... — which
     * doesn't exist under our package name. Rewrite any such reference in text
     * scripts to point at our actual extracted usr/ directory instead.
     */
    private fun patchHardcodedPrefix(usrDir: File) {
        val oldPrefix = "/data/data/com.termux/files/usr"
        val newPrefix = usrDir.absolutePath
        var patchedCount = 0

        usrDir.walkTopDown().forEach { file ->
            if (!file.isFile) return@forEach
            try {
                val head = ByteArray(2)
                val bytesRead = file.inputStream().use { it.read(head) }
                // Only touch text scripts starting with a shebang line ("#!"),
                // never binary ELF files (which start with 0x7f 'E' 'L' 'F').
                if (bytesRead == 2 && head[0] == '#'.code.toByte() && head[1] == '!'.code.toByte()) {
                    val text = file.readText()
                    if (text.contains(oldPrefix)) {
                        file.writeText(text.replace(oldPrefix, newPrefix))
                        patchedCount++
                    }
                }
            } catch (_: Exception) {
                // Skip unreadable/binary files silently
            }
        }
        AppLogger.log("TermuxBootstrapper", "Patched hardcoded prefix in $patchedCount script(s)")
    }

    /**
     * Java's ZipInputStream does not preserve Unix executable permission bits
     * during extraction, so every extracted file lands as non-executable
     * regardless of what the original archive entry specified. This restores
     * the executable bit on anything that needs to run as a binary — checking
     * the full path rather than just the immediate parent folder, since
     * binaries like apt's http/https methods live several directories deep
     * (usr/lib/apt/methods/http), not just directly inside bin/ or lib/.
     */
    private fun fixExecutablePermissions(directory: File) {
        var fixedCount = 0

        directory.walkTopDown().forEach { file ->
            if (!file.isFile) return@forEach

            val path = file.absolutePath
            val shouldBeExecutable =
                path.contains("/bin/") ||
                path.contains("/lib/apt/methods/") ||
                path.contains("/libexec/") ||
                path.endsWith(".so") ||
                file.name == "dpkg" ||
                file.name == "apt"

            if (shouldBeExecutable) {
                if (file.setExecutable(true, false)) {
                    fixedCount++
                } else {
                    AppLogger.log("TermuxBootstrapper", "WARNING: failed to set +x on ${file.name}")
                }
            }

            // Everything should be at least readable
            file.setReadable(true, false)
        }

        AppLogger.log("TermuxBootstrapper", "Restored executable permission on $fixedCount file(s)")
    }
}
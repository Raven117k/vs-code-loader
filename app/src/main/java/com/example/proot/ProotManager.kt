package com.example.proot

import android.content.Context
import com.example.util.AppLogger
import java.io.File

class ProotManager(private val context: Context) {
    private val filesDir = context.filesDir
    private val nativeLibDir = context.applicationInfo.nativeLibraryDir
    val prootBinary = File(nativeLibDir, "libproot.so")
    val prootLoader = File(nativeLibDir, "libprootloader.so")
    val rootfsDir = File(filesDir, "rootfs")
    
    // Ensure host tmp folder exists
    val tmpDir = File(filesDir, "tmp").apply { if (!exists()) mkdirs() }

    // 🛠️ FIX: Force-create the actual structural folders INSIDE the extracted rootfs
    fun ensureRootfsLayout() {
        if (!rootfsDir.exists()) rootfsDir.mkdirs()
        File(rootfsDir, "tmp").apply { if (!exists()) mkdirs() }
        File(rootfsDir, "var/tmp").apply { if (!exists()) mkdirs() }
        File(rootfsDir, "run").apply { if (!exists()) mkdirs() }
        File(rootfsDir, "root").apply { if (!exists()) mkdirs() }
        File(rootfsDir, "dev").apply { if (!exists()) mkdirs() }
        File(rootfsDir, "proc").apply { if (!exists()) mkdirs() }
        File(rootfsDir, "sys").apply { if (!exists()) mkdirs() }
        File(rootfsDir, "bin").apply { if (!exists()) mkdirs() }
    }

    private fun resolveGuestShell(): Pair<String, List<String>> {
        val candidates = listOf(
            "/bin/sh",
            "/bin/ash",
            "/bin/bash",
            "/bin/busybox",
            "/usr/bin/sh",
            "/usr/bin/ash",
            "/usr/bin/bash"
        )

        val existingShell = candidates.firstOrNull { candidate ->
            val guestPath = candidate.removePrefix("/")
            File(rootfsDir, guestPath).exists()
        } ?: "/bin/sh"

        return when (existingShell) {
            "/bin/busybox" -> existingShell to listOf("sh", "-c")
            "/bin/bash", "/bin/ash", "/usr/bin/bash", "/usr/bin/ash" -> existingShell to listOf("-c")
            else -> existingShell to listOf("-c")
        }
    }

    private fun getQemuBinary(): File? {
        val qemuName = if (getDeviceAbi() == "x86_64") {
            "libqemu-x86_64.so"
        } else {
            "libqemu-aarch64.so"
        }
        val qemuFile = File(nativeLibDir, qemuName)
        return if (qemuFile.exists()) {
            qemuFile
        } else {
            AppLogger.log("ProotManager", "QEMU binary not found in native libs: ${qemuFile.absolutePath}")
            null
        }
    }

    fun buildProotCommand(guestCommand: String): List<String> {
        val args = mutableListOf<String>()

        // Force-ensure the layout folders are present right before execution runs
        ensureRootfsLayout()

        args.add(prootBinary.absolutePath)
        args.add("-0") // Simulate root user privileges
        args.add("--link2symlink")

        getQemuBinary()?.let { qemuFile ->
            AppLogger.log("ProotManager", "Using QEMU binary ${qemuFile.absolutePath}")
            args.add("-q")
            args.add(qemuFile.absolutePath)
        }
        
        args.add("-r")
        args.add(rootfsDir.absolutePath)

        args.add("-b")
        args.add("/dev:/dev")
        args.add("-b")
        args.add("/proc:/proc")
        args.add("-b")
        args.add("/sys:/sys")
        
        // Bind host tmp folder directly to multiple guest temp paths
        args.add("-b")
        args.add("${tmpDir.absolutePath}:/tmp")
        args.add("-b")
        args.add("${tmpDir.absolutePath}:/var/tmp")
        args.add("-b")
        args.add("${tmpDir.absolutePath}:/run")

        args.add("-w")
        args.add("/root")

        val (shellPath, shellArgs) = resolveGuestShell()
        AppLogger.log("ProotManager", "Using guest shell $shellPath")
        args.add(shellPath)
        shellArgs.forEach { args.add(it) }
        args.add(guestCommand)

        return args
    }

    fun getProotEnvironment(): Map<String, String> {
        val env = mutableMapOf<String, String>()
        
        env["PROOT_LOADER"] = prootLoader.absolutePath
        
        env["LD_PRELOAD"] = ""
        env["LD_LIBRARY_PATH"] = ""
        
        // Pass the host-side temp directory to unblock internal probes and guest TMP handling
        env["PROOT_TMPDIR"] = tmpDir.absolutePath
        env["PROOT_TMP_DIR"] = tmpDir.absolutePath
        env["TMPDIR"] = tmpDir.absolutePath
        env["TEMP"] = tmpDir.absolutePath
        env["TMP"] = tmpDir.absolutePath
        env["PROOT_VERBOSE"] = "1"
        
        env["HOME"] = "/root"
        env["PATH"] = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
        
        env["PROOT_NO_SECCOMP"] = "1"
        
        return env
    }
}
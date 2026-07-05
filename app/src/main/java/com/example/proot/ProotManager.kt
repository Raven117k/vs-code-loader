package com.example.proot

import android.content.Context
import java.io.File

class ProotManager(private val context: Context) {
    private val filesDir = context.filesDir
    val prootBinary = File(File(filesDir, "proot"), "proot")
    val prootLoader = File(File(filesDir, "proot"), "loader")
    val rootfsDir = File(filesDir, "rootfs")
    
    // Ensure the tmp folder exists inside your app sandbox with absolute write access
    val tmpDir = File(filesDir, "tmp").apply { if (!exists()) mkdirs() }

    fun buildProotCommand(guestCommand: String): List<String> {
        val args = mutableListOf<String>()

        // Host path to proot binary
        args.add(prootBinary.absolutePath)

        // Core proot options
        args.add("-0") // Simulate root user privileges
        
        // Root directory definition
        args.add("-r")
        args.add(rootfsDir.absolutePath)

        // Standard system directories bind mounts using strict space separation
        args.add("-b")
        args.add("/system:/system")

        // ❌ REMOVED: Binding all of /data causes a recursive crash because your app's files are inside /data.
        // Instead, we bind only what is strictly necessary or safe.
        args.add("-b")
        args.add("/dev:/dev")

        args.add("-b")
        args.add("/proc:/proc")

        args.add("-b")
        args.add("/sys:/sys")

        // Bind mount host tmp folder to guest /tmp
        args.add("-b")
        args.add("${tmpDir.absolutePath}:/tmp")

        // Keep the guest isolated from Android-specific paths safely
        args.add("-b")
        args.add("/sdcard:/sdcard")

        // Set working directory inside guest
        args.add("-w")
        args.add("/root")

        // Environment execution inside the guest shell
        args.add("/bin/sh")
        args.add("-c")
        args.add(guestCommand)

        return args
    }

    fun getProotEnvironment(): Map<String, String> {
        return mapOf(
            "LD_PRELOAD" to "",
            "LD_LIBRARY_PATH" to "",
            "PROOT_TMPDIR" to tmpDir.absolutePath,
            "PROOT_TMP_DIR" to tmpDir.absolutePath,
            "HOME" to "/root",
            "PATH" to "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
        )
    }
}
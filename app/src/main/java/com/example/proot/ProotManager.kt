package com.example.proot

import android.content.Context
import java.io.File

class ProotManager(private val context: Context) {
    private val filesDir = context.filesDir
    val prootBinary = File(File(filesDir, "proot"), "proot")
    val prootLoader = File(File(filesDir, "proot"), "loader")
    val rootfsDir = File(filesDir, "rootfs")
    val tmpDir = File(filesDir, "tmp")

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

        args.add("-b")
        args.add("/data:/data")

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
            "PROOT_LOADER" to prootLoader.absolutePath,
            "PROOT_TMPDIR" to tmpDir.absolutePath,
            "HOME" to "/root",
            "PATH" to "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
        )
    }
}
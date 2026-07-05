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

        // Host path to proot
        args.add(prootBinary.absolutePath)

        // Core proot options
        args.add("-0") // Simulate root user
        args.add("--bind")
        args.add("/system:/system")
        args.add("--bind")
        args.add("/data:/data")

        // Root directory definition
        args.add("-r")
        args.add(rootfsDir.absolutePath)

        // Standard virtual filesystems bind mounts
        args.add("-b")
        args.add("/dev")
        args.add("-b")
        args.add("/proc")
        args.add("-b")
        args.add("/sys")

        // Bind mount tmp folder
        args.add("-b")
        args.add("${tmpDir.absolutePath}:/tmp")

        // Keep the guest isolated from Android-specific paths that trigger weird proot behavior
        args.add("-b")
        args.add("/sdcard:/sdcard")

        // Set working directory inside guest
        args.add("-w")
        args.add("/root")

        // Environment settings
        args.add("/bin/sh")
        args.add("-c")
        args.add(guestCommand)

        return args
    }

    fun getProotEnvironment(): Map<String, String> {
        // Clear host's LD_PRELOAD and library paths to prevent clash inside guest,
        // and point proot at its companion loader binary (required for the
        // ZhymabekRoman/proot-static build -- proot cannot execve() correctly without this).
        return mapOf(
            "LD_PRELOAD" to "",
            "LD_LIBRARY_PATH" to "",
            "PROOT_TMPDIR" to tmpDir.absolutePath,
            "HOME" to "/root",
            "PATH" to "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
        )
    }
}
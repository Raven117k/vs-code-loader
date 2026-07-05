package com.example.proot

import android.content.Context
import java.io.File

class ProotManager(private val context: Context) {

    private val filesDir = context.filesDir
    val prootBinary = File(File(filesDir, "proot"), "proot")
    val rootfsDir = File(filesDir, "rootfs")
    val tmpDir = File(filesDir, "tmp")

    fun buildProotCommand(guestCommand: String): List<String> {
        val args = mutableListOf<String>()
        
        // Host path to proot
        args.add(prootBinary.absolutePath)
        
        // Core proot options
        args.add("--link2symlink")
        args.add("-0") // Simulate root user
        
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
        
        // Set working directory inside guest
        args.add("-w")
        args.add("/root")
        
        // Environment settings
        args.add("/usr/bin/env")
        args.add("HOME=/root")
        args.add("PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin")
        args.add("TERM=xterm-256color")
        args.add("PROOT_TMPDIR=/tmp")
        args.add("LD_PRELOAD=") // Clean Android preload variables to prevent guest linkage crashes

        // Execute guest command inside sh shell
        args.add("/bin/sh")
        args.add("-c")
        args.add(guestCommand)
        
        return args
    }

    fun getProotEnvironment(): Map<String, String> {
        // Clear host's LD_PRELOAD and library paths to prevent clash inside guest
        return mapOf(
            "LD_PRELOAD" to "",
            "LD_LIBRARY_PATH" to "",
            "PROOT_TMPDIR" to tmpDir.absolutePath
        )
    }
}

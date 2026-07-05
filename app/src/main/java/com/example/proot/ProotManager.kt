package com.example.proot

import android.content.Context
import java.io.File

class ProotManager(private val context: Context) {
    private val filesDir = context.filesDir
    val prootBinary = File(File(filesDir, "proot"), "proot")
    val prootLoader = File(File(filesDir, "proot"), "loader")
    val rootfsDir = File(filesDir, "rootfs")
    val tmpDir = File(filesDir, "tmp").apply { if (!exists()) mkdirs() }

    fun buildProotCommand(guestCommand: String): List<String> {
        val args = mutableListOf<String>()

        args.add(prootBinary.absolutePath)
        args.add("-0") // Simulate root user privileges
        
        // CRITICAL: Force PRoot to use link2symlink extension.
        // This stops Android from throwing hard-link permission errors on the emulator filesystem!
        args.add("--link2symlink")
        
        args.add("-r")
        args.add(rootfsDir.absolutePath)

        // Fix the bind syntax to separate system bindings accurately
        args.add("-b")
        args.add("/dev:/dev")
        args.add("-b")
        args.add("/proc:/proc")
        args.add("-b")
        args.add("/sys:/sys")
        
        // Bind the host app data directory to clear out the "can't canonicalize /tmp/" warning
        args.add("-b")
        args.add("${tmpDir.absolutePath}:/tmp")

        args.add("-w")
        args.add("/root")
        
        args.add("/bin/sh")
        args.add("-c")
        args.add(guestCommand)

        return args
    }

    fun getProotEnvironment(): Map<String, String> {
        val env = mutableMapOf<String, String>()
        
        // Point PRoot explicitly to the companion executable loader stub we extracted
        env["PROOT_LOADER"] = prootLoader.absolutePath
        
        // Keep these completely empty so the dynamic linker doesn't throw e_type mismatch errors
        env["LD_PRELOAD"] = ""
        env["LD_LIBRARY_PATH"] = ""
        
        // Match the directory bindings exactly
        env["PROOT_TMPDIR"] = "/tmp"
        env["HOME"] = "/root"
        env["PATH"] = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
        
        // Absolutely force user-space emulation to skip the ptrace kernel trap entirely
        env["PROOT_NO_SECCOMP"] = "1"
        
        return env
    }
}
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
        
        // Pass the loader explicitly via CLI flags as well to ensure fallback stability
        if (prootLoader.exists()) {
            args.add("-l")
            args.add(prootLoader.absolutePath)
        }
        
        // Root directory definition
        args.add("-r")
        args.add(rootfsDir.absolutePath)

        // Standard system directories bind mounts using strict space separation
        args.add("-b")
        args.add("/system:/system")

        // Safe system bindings
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
        val env = mutableMapOf<String, String>()
        
        // CRITICAL: Point LD_PRELOAD to our actual companion loader asset.
        // This stops PRoot from crashing out on the restricted ptrace engine.
        env["LD_PRELOAD"] = prootLoader.absolutePath
        
        env["LD_LIBRARY_PATH"] = ""
        env["PROOT_TMPDIR"] = tmpDir.absolutePath
        env["PROOT_TMP_DIR"] = tmpDir.absolutePath
        env["HOME"] = "/root"
        env["PATH"] = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
        
        // Force PRoot to bypass kernel system call filters that are blocked on emulators
        env["PROOT_NO_SECCOMP"] = "1"
        
        return env
    }
}
package com.example.proot

import android.content.Context
import java.io.File

class ProotManager(private val context: Context) {
    private val filesDir = context.filesDir
    val prootBinary = File(File(filesDir, "proot"), "proot")
    val prootLoader = File(File(filesDir, "proot"), "loader")
    val rootfsDir = File(filesDir, "rootfs")
    
    // Ensure both the guest rootfs /tmp directory target AND our host cache space exist completely
    val tmpDir = File(filesDir, "tmp").apply { if (!exists()) mkdirs() }
    private val guestTmpDir = File(rootfsDir, "tmp").apply { if (!exists()) mkdirs() }

    fun buildProotCommand(guestCommand: String): List<String> {
        val args = mutableListOf<String>()

        args.add(prootBinary.absolutePath)
        args.add("-0") // Simulate root user privileges
        args.add("--link2symlink")
        
        args.add("-r")
        args.add(rootfsDir.absolutePath)

        // Standard system directory bindings using strict individual parameters
        args.add("-b")
        args.add("/dev:/dev")
        args.add("-b")
        args.add("/proc:/proc")
        args.add("-b")
        args.add("/sys:/sys")
        
        // CRITICAL BIND: Explicitly mount the physical host sandbox tmp folder to the guest's virtualized /tmp
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
        
        // Pass the standalone companion loader binary through the correct environment variable
        env["PROOT_LOADER"] = prootLoader.absolutePath
        
        // Keep these completely empty since Termux uses static execution links now
        env["LD_PRELOAD"] = ""
        env["LD_LIBRARY_PATH"] = ""
        
        // CRITICAL PATH FIX: Force PRoot to use the absolute host-side directory path 
        // for its internal initialization probes BEFORE it applies the rootfs virtualizations!
        env["PROOT_TMPDIR"] = tmpDir.absolutePath
        
        env["HOME"] = "/root"
        env["PATH"] = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
        
        // Completely skip kernel-level system filters to let the binary run natively in user space
        env["PROOT_NO_SECCOMP"] = "1"
        
        return env
    }
}
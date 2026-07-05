package com.example.proot

import android.content.Context
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

    fun buildProotCommand(guestCommand: String): List<String> {
        val args = mutableListOf<String>()

        // Force-ensure the layout folders are present right before execution runs
        ensureRootfsLayout()

        args.add(prootBinary.absolutePath)
        args.add("-0") // Simulate root user privileges
        args.add("--link2symlink")
        
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
        
        args.add("/bin/sh")
        args.add("-c")
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
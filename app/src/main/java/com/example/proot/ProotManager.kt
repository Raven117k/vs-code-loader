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

        // NOTE: proot has no "-l" CLI flag for specifying the loader.
        // The loader is passed via the PROOT_LOADER environment variable
        // instead -- see getProotEnvironment() below.

        args.add("-r")
        args.add(rootfsDir.absolutePath)

        args.add("-b")
        args.add("/dev:/dev")
        args.add("-b")
        args.add("/proc:/proc")
        args.add("-b")
        args.add("/sys:/sys")

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

        // CRITICAL FIX: PROOT_LOADER, not LD_PRELOAD.
        // LD_PRELOAD is for shared libraries (ELF type ET_DYN) only.
        // Our loader binary is an executable (ET_EXEC) -- setting it via
        // LD_PRELOAD is exactly what caused:
        //   "unexpected e_type: 2"
        // proot itself reads PROOT_LOADER to find its companion loader stub.
        env["PROOT_LOADER"] = prootLoader.absolutePath

        env["LD_PRELOAD"] = ""
        env["LD_LIBRARY_PATH"] = ""
        env["PROOT_TMPDIR"] = tmpDir.absolutePath
        env["HOME"] = "/root"
        env["PATH"] = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

        return env
    }
}
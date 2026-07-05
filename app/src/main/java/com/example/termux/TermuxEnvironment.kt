package com.example.termux

import android.content.Context
import com.example.util.AppLogger
import java.io.File

class TermuxEnvironment(private val context: Context) {
    private val nativeLibDir = context.applicationInfo.nativeLibraryDir
    private val filesDir = context.filesDir

    val usrDir = File(filesDir, "usr")
    val homeDir = File(filesDir, "home")
    val binDir = File(usrDir, "bin")
    val localBinDir = File(usrDir, "local/bin")
    val tmpDir = File(usrDir, "tmp")

    fun ensureLayout() {
        listOf(
            usrDir,
            homeDir,
            binDir,
            localBinDir,
            tmpDir,
            File(usrDir, "lib"),
            File(usrDir, "etc"),
            File(usrDir, "local/lib")
        ).forEach { if (!it.exists()) it.mkdirs() }
    }

    fun resolveBinary(name: String): File {
        val distroCandidate = File(binDir, name)
        val localCandidate = File(localBinDir, name)
        val nativeCandidate = File(nativeLibDir, "libtermux$name.so")

        return when {
            distroCandidate.exists() -> distroCandidate
            localCandidate.exists() -> localCandidate
            nativeCandidate.exists() -> nativeCandidate
            else -> File(binDir, name)
        }
    }

    fun isReady(): Boolean {
        val ready = listOf("bash", "sh", "busybox").any { resolveBinary(it).exists() }
        if (!ready) AppLogger.log("TermuxEnvironment", "No Termux shell binaries found yet")
        return ready
    }

    fun getEnvironment(): Map<String, String> = mapOf(
        "HOME" to homeDir.absolutePath,
        "PREFIX" to usrDir.absolutePath,
        "PATH" to listOf(localBinDir.absolutePath, binDir.absolutePath, nativeLibDir).joinToString(":"),
        "LD_LIBRARY_PATH" to File(usrDir, "lib").absolutePath,
        "TMPDIR" to tmpDir.absolutePath
    )
}

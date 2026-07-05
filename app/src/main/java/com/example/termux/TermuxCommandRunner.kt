package com.example.termux

import android.content.Context
import com.example.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.coroutines.coroutineContext

class TermuxCommandRunner(context: Context) {
    private val env = TermuxEnvironment(context)
    private var activeProcess: Process? = null

    suspend fun runCommand(guestCommand: String, onLine: (String) -> Unit): Int =
        withContext(Dispatchers.IO) {
            val shell = env.resolveBinary("bash").takeIf { it.exists() }
                ?: env.resolveBinary("busybox").takeIf { it.exists() }
                ?: env.resolveBinary("sh").takeIf { it.exists() }
                ?: throw IllegalStateException("No shell available in Termux environment")
            val command = if (shell.name.contains("busybox")) {
                listOf(shell.absolutePath, "sh", "-c", guestCommand)
            } else {
                listOf(shell.absolutePath, "-c", guestCommand)
            }

            AppLogger.log("TermuxCommandRunner", "Running: $guestCommand")
            try {
                val builder = ProcessBuilder(command)
                builder.environment().putAll(env.getEnvironment())
                builder.redirectErrorStream(true)

                val process = builder.start()
                activeProcess = process
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?
                while (coroutineContext.isActive && reader.readLine().also { line = it } != null) {
                    line?.let { onLine(it); AppLogger.log("Guest", it) }
                }
                if (!coroutineContext.isActive) {
                    process.destroy()
                    return@withContext -1
                }
                val exitCode = process.waitFor()
                AppLogger.log("TermuxCommandRunner", "Finished with exit code: $exitCode")
                exitCode
            } catch (e: Exception) {
                AppLogger.log("TermuxCommandRunner", "Failed: ${e.message}")
                onLine("Error: ${e.message}")
                -1
            } finally {
                activeProcess = null
            }
        }

    fun stopActiveCommand() {
        activeProcess?.destroy()
        activeProcess = null
    }
}

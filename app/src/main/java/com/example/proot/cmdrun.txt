package com.example.proot

import android.content.Context
import com.example.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.coroutines.coroutineContext

class CommandRunner(context: Context) {
    private val prootManager = ProotManager(context)
    private var activeProcess: Process? = null

    suspend fun runCommand(guestCommand: String, onLine: (String) -> Unit): Int = withContext(Dispatchers.IO) {
        val fullCommand = prootManager.buildProotCommand(guestCommand)
        AppLogger.log("CommandRunner", "Running command: $guestCommand")
        
        try {
            val builder = ProcessBuilder(fullCommand)
            // Inject cleaner environment
            builder.environment().apply {
                put("LD_PRELOAD", "")
                put("LD_LIBRARY_PATH", "")
            }
            builder.redirectErrorStream(true)
            
            val process = builder.start()
            activeProcess = process

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String? = null

            // Read output line by line as long as the process is alive and coroutine is active
            while (coroutineContext.isActive && reader.readLine().also { line = it } != null) {
                line?.let {
                    onLine(it)
                    AppLogger.log("Guest", it)
                }
            }

            if (!coroutineContext.isActive) {
                AppLogger.log("CommandRunner", "Coroutine cancelled, killing process...")
                process.destroy()
                return@withContext -1
            }

            val exitCode = process.waitFor()
            AppLogger.log("CommandRunner", "Command finished with exit code: $exitCode")
            exitCode
        } catch (e: Exception) {
            AppLogger.log("CommandRunner", "Command execution failed: ${e.message}")
            onLine("Error: ${e.message}")
            -1
        } finally {
            activeProcess = null
        }
    }

    fun stopActiveCommand() {
        activeProcess?.let {
            AppLogger.log("CommandRunner", "Forcibly killing active command...")
            it.destroy()
        }
        activeProcess = null
    }
}

package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.WebViewActivity
import com.example.proot.CommandRunner
import com.example.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ServerForegroundService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private lateinit var commandRunner: CommandRunner

    companion object {
        const val CHANNEL_ID = "vs_code_server_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"

        private val _serverState = MutableStateFlow(false)
        val serverState: StateFlow<Boolean> = _serverState.asStateFlow()

        val isServerRunning: Boolean
            get() = _serverState.value
    }

    override fun onCreate() {
        super.onCreate()
        commandRunner = CommandRunner(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        if (action == ACTION_STOP) {
            AppLogger.log("Service", "Stop action received. Shutting down foreground service...")
            stopServer()
            stopSelf()
            return START_NOT_STICKY
        }

        if (action == ACTION_START) {
            if (_serverState.value) {
                AppLogger.log("Service", "Server is already running.")
                return START_STICKY
            }

            AppLogger.log("Service", "Starting VS Code Server Foreground Service...")
            startForeground(NOTIFICATION_ID, createNotification())
            _serverState.value = true

            // Start the code-server process inside the guest environment
            serviceScope.launch {
                // Command to start code-server bound to local loopback port 8080 with auth disabled for offline ease
                val startCmd = "code-server --bind-addr 127.0.0.1:8080 --auth none --cert false"
                AppLogger.log("Service", "Executing command: $startCmd")
                
                val exitCode = commandRunner.runCommand(startCmd) { line ->
                    AppLogger.log("CodeServerLog", line)
                }

                AppLogger.log("Service", "code-server exited with code $exitCode")
                _serverState.value = false
                stopSelf()
            }
        }

        return START_STICKY
    }

    private fun stopServer() {
        try {
            commandRunner.stopActiveCommand()
            serviceScope.cancel()
        } catch (e: Exception) {
            AppLogger.log("Service", "Error stopping server: ${e.message}")
        } finally {
            _serverState.value = false
        }
    }

    override fun onDestroy() {
        stopServer()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = "VS Code Server Monitor"
            val descriptionText = "Keeps the VS Code background environment running securely"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, channelName, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        // Pending intent to open MainActivity
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Pending intent to open WebViewActivity directly
        val webViewIntent = Intent(this, WebViewActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val webViewPendingIntent = PendingIntent.getActivity(
            this, 1, webViewIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Pending intent to stop the server
        val stopIntent = Intent(this, ServerForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 2, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VS Code Server is Active")
            .setContentText("Local development environment running on :8080")
            .setSmallIcon(android.R.drawable.ic_media_play) // Standard system play icon as fallback
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_view, "Open IDE", webViewPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Server", stopPendingIntent)
            .build()
    }
}

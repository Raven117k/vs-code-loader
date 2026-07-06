package com.example.termux

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.MyApplicationTheme
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import java.io.File

class TermuxTerminalActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                RealTerminalScreen()
            }
        }
    }
}

@Composable
fun RealTerminalScreen() {
    val context = LocalContext.current

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val env = TermuxEnvironment(ctx.applicationContext)
            env.ensureLayout()

            val shell = env.resolveBinary("bash").takeIf { it.exists() }
                ?: env.resolveBinary("busybox")

            val terminalView = TerminalView(ctx, null)
            val sessionClient = AppTerminalSessionClient()
            val viewClient = AppTerminalViewClient()

            val session = TerminalSession(
                shell.absolutePath,
                env.homeDir.absolutePath,
                arrayOf(shell.absolutePath, "-l"),
                env.getEnvironment().map { "${it.key}=${it.value}" }.toTypedArray(),
                2000,
                sessionClient
            )

            terminalView.attachSession(session)
            terminalView.setTerminalViewClient(viewClient)
            terminalView.requestFocus()

            terminalView
        }
    )
}

package com.example.termux

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.MyApplicationTheme
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient

class TermuxTerminalActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                TermuxTerminalScreen()
            }
        }
    }
}

private enum class SessionStatus { STARTING, RUNNING, EXITED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermuxTerminalScreen() {
    val context = LocalContext.current
    val environment = remember(context) { TermuxEnvironment(context.applicationContext) }

    var status by remember { mutableStateOf(SessionStatus.STARTING) }
    var statusDetail by remember { mutableStateOf("Booting shell…") }
    var terminalTitle by remember { mutableStateOf("Terminal") }
    var terminalViewRef by remember { mutableStateOf<TerminalView?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(terminalTitle) },
                actions = {
                    IconButton(onClick = {
                        terminalViewRef?.let { view ->
                            view.requestFocus()
                            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                            imm.hideSoftInputFromWindow(view.windowToken, 0)
                        }
                    }) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Hide keyboard")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            StatusBar(status = status, detail = statusDetail)

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    TerminalView(ctx, null).apply {
                        setTextSize(28)
                        keepScreenOn = true
                        terminalViewRef = this

                        val session = createSession(
                            environment = environment,
                            onStatusChanged = { newStatus, detail ->
                                Handler(Looper.getMainLooper()).post {
                                    status = newStatus
                                    statusDetail = detail
                                }
                            },
                            onTitleChanged = { title ->
                                Handler(Looper.getMainLooper()).post {
                                    terminalTitle = title.ifBlank { "Terminal" }
                                }
                            }
                        )
                        attachSession(session)
                        setTerminalViewClient(BasicTerminalViewClient())
                        setOnClickListener {
                            requestFocus()
                            val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                            imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun StatusBar(status: SessionStatus, detail: String) {
    val (dotColor, label) = when (status) {
        SessionStatus.STARTING -> Color(0xFFFFA726) to "Starting"
        SessionStatus.RUNNING -> Color(0xFF4CAF50) to "Running"
        SessionStatus.EXITED -> Color(0xFFE53935) to "Exited"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color = dotColor, shape = CircleShape)
        )
        Text(
            text = "  $label — $detail",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun createSession(
    environment: TermuxEnvironment,
    onStatusChanged: (SessionStatus, String) -> Unit,
    onTitleChanged: (String) -> Unit
): TerminalSession {
    val shellPath = environment.resolveShellBinary().absolutePath
    val envVars = environment.buildEnvironmentArray()
    val args = emptyArray<String>()

    val client = object : TerminalSessionClient {
        override fun onTextChanged(session: TerminalSession) {
            onStatusChanged(SessionStatus.RUNNING, shellPath.substringAfterLast('/'))
        }
        override fun onTitleChanged(session: TerminalSession) {
            onTitleChanged(session.title ?: "Terminal")
        }
        override fun onSessionFinished(session: TerminalSession) {
            val code = session.exitStatus
            onStatusChanged(SessionStatus.EXITED, "exit code $code")
        }
        override fun onCopyTextToClipboard(session: TerminalSession, text: String) {}
        override fun onPasteTextFromClipboard(session: TerminalSession?) {}
        override fun onBell(session: TerminalSession) {}
        override fun onColorsChanged(session: TerminalSession) {}
        override fun onTerminalCursorStateChange(state: Boolean) {}
        override fun getTerminalCursorStyle(): Int? = null
        override fun logError(tag: String?, message: String?) {}
        override fun logWarn(tag: String?, message: String?) {}
        override fun logInfo(tag: String?, message: String?) {}
        override fun logDebug(tag: String?, message: String?) {}
        override fun logVerbose(tag: String?, message: String?) {}
        override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}
        override fun logStackTrace(tag: String?, e: Exception?) {}
    }

    val session = TerminalSession(
        shellPath,
        environment.homeDir.absolutePath,
        args,
        envVars,
        4000,
        client
    )
    onStatusChanged(SessionStatus.RUNNING, shellPath.substringAfterLast('/'))
    return session
}

private class BasicTerminalViewClient : TerminalViewClient {
    override fun onScale(scale: Float): Float = scale
    override fun onSingleTapUp(e: MotionEvent) {}
    override fun onLongPress(event: MotionEvent): Boolean = false
    override fun shouldBackButtonBeMappedToEscape(): Boolean = false
    override fun shouldEnforceCharBasedInput(): Boolean = true
    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
    override fun isTerminalViewSelected(): Boolean = true
    override fun copyModeChanged(copyMode: Boolean) {}
    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean = false
    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false
    override fun readControlKey(): Boolean = false
    override fun readAltKey(): Boolean = false
    override fun readShiftKey(): Boolean = false
    override fun readFnKey(): Boolean = false
    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean = false
    override fun onEmulatorSet() {}
    override fun logError(tag: String?, message: String?) {}
    override fun logWarn(tag: String?, message: String?) {}
    override fun logInfo(tag: String?, message: String?) {}
    override fun logDebug(tag: String?, message: String?) {}
    override fun logVerbose(tag: String?, message: String?) {}
    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}
    override fun logStackTrace(tag: String?, e: Exception?) {}
}
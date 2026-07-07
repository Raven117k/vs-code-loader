package com.example.termux

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

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

@Composable
fun TermuxTerminalScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val runner = remember(context) { TermuxCommandRunner(context.applicationContext) }

    var command by rememberSaveable { mutableStateOf("") }
    var output by rememberSaveable { mutableStateOf("Termux shell ready.\n") }
    var isRunning by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(output) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    fun submitCommand() {
        if (command.isBlank() || isRunning) return
        val trimmed = command.trim()
        scope.launch {
            isRunning = true
            output += "\n$ $trimmed\n"
            val lines = mutableListOf<String>()
            val exitCode = runner.runCommand(trimmed) { line ->
                lines.add(line)
            }
            output += lines.joinToString("\n")
            output += "\n[exit $exitCode]\n"
            isRunning = false
            command = ""
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07110F))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "termux",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFB7F7C2)
                )
                Text(
                    text = "linux environment • local shell",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF7FD4A6)
                )
            }
            Text(
                text = if (isRunning) "running…" else "ready",
                color = if (isRunning) Color(0xFFFFD54F) else Color(0xFF6EE7B7),
                fontWeight = FontWeight.SemiBold
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF0B1A17), shape = RoundedCornerShape(18.dp))
                .border(1.dp, Color(0xFF2D6A4F), RoundedCornerShape(18.dp))
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "┌─ Terminal",
                        color = Color(0xFF7FD4A6),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "● ● ●",
                        color = Color(0xFF5A8F77),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }
                Text(
                    text = output,
                    color = Color(0xFFE6FFF0),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F241D), shape = RoundedCornerShape(14.dp))
                .border(1.dp, Color(0xFF2D6A4F), RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "root@localhost:~$",
                    color = Color(0xFF7FD4A6),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                BasicTextField(
                    value = command,
                    onValueChange = { command = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { submitCommand() }),
                    textStyle = TextStyle(
                        color = Color(0xFFE6FFF0),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 6.dp)
                        .focusRequester(focusRequester)
                )
            }
        }

        Text(
            text = "Tap the prompt and use the keyboard to run commands.",
            color = Color(0xFF8CBFA8),
            fontSize = 12.sp
        )
    }
}

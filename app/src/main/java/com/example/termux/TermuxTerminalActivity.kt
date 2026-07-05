package com.example.termux

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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

    var command by rememberSaveable { mutableStateOf("pwd") }
    var output by rememberSaveable { mutableStateOf("Termux shell ready.\n") }
    var isRunning by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1C1B1F))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Termux Terminal",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = "Run commands directly inside the Termux environment.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFCAC4D0)
        )

        OutlinedTextField(
            value = command,
            onValueChange = { command = it },
            label = { Text("Command") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color(0xFF49454F)
            )
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    if (command.isBlank()) return@Button
                    scope.launch {
                        isRunning = true
                        output += "\n$ $command\n"
                        val lines = mutableListOf<String>()
                        val exitCode = runner.runCommand(command) { line ->
                            lines.add(line)
                        }
                        output += lines.joinToString("\n")
                        output += "\n[exit $exitCode]\n"
                        isRunning = false
                    }
                },
                enabled = !isRunning,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFD0BCFF),
                    contentColor = Color(0xFF381E72)
                )
            ) {
                Text("Run")
            }

            Button(
                onClick = { output = "Termux shell ready.\n" },
                enabled = !isRunning,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF49454F),
                    contentColor = Color.White
                )
            ) {
                Text("Clear")
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1C1B1F), shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                .padding(12.dp)
                .verticalScroll(scrollState)
        ) {
            Text(
                text = output,
                color = Color(0xFFCAC4D0),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
}

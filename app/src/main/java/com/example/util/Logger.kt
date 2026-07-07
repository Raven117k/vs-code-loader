package com.example.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val _lastLogLine = MutableStateFlow("")
    val lastLogLine: StateFlow<String> = _lastLogLine.asStateFlow()

    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun log(tag: String, message: String) {
        val timestamp = dateFormat.format(Date())
        val formattedLine = "[$timestamp] [$tag] $message"
        android.util.Log.d(tag, message)
        _logs.update { current ->
            current + formattedLine
        }
        _lastLogLine.value = message
    }

    fun clear() {
        _logs.value = emptyList()
        _lastLogLine.value = ""
    }
}

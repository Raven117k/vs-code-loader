package com.termux.terminal

class TerminalSession(
    private val shellPath: String,
    private val cwd: String,
    private val args: Array<String>,
    private val env: Array<String>,
    private val transcriptRows: Int,
    private val client: TerminalSessionClient
) {
    fun start() = Unit
    fun finish() = Unit
}

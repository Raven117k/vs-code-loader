package com.termux.terminal

interface TerminalSessionClient {
    fun onTextChanged(changedSession: TerminalSession) = Unit
    fun onTitleChanged(changedSession: TerminalSession) = Unit
    fun onSessionFinished(finishedSession: TerminalSession) = Unit
    fun onCopyTextToClipboard(session: TerminalSession, text: String?) = Unit
    fun onPasteTextFromClipboard(session: TerminalSession?) = Unit
    fun onBell(session: TerminalSession) = Unit
    fun onColorsChanged(session: TerminalSession) = Unit
    fun onTerminalCursorStateChange(state: Boolean) = Unit
    fun setTerminalShellPid(session: TerminalSession, pid: Int) = Unit
    fun getTerminalCursorStyle(): Int? = null
    fun logError(tag: String?, message: String?) = Unit
    fun logWarn(tag: String?, message: String?) = Unit
    fun logInfo(tag: String?, message: String?) = Unit
    fun logDebug(tag: String?, message: String?) = Unit
    fun logVerbose(tag: String?, message: String?) = Unit
    fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) = Unit
    fun logStackTrace(tag: String?, e: Exception?) = Unit
}

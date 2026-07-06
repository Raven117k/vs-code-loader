package com.example.termux

import android.view.KeyEvent
import android.view.MotionEvent
import com.termux.terminal.TerminalSession

interface TerminalViewClient {
    fun onScale(scale: Float): Float = scale
    fun onSingleTapUp(e: MotionEvent?) = Unit
    fun shouldBackButtonBeMappedToEscape(): Boolean = false
    fun shouldEnforceCharBasedInput(): Boolean = true
    fun shouldUseCtrlSpaceWorkaround(): Boolean = false
    fun isTerminalViewSelected(): Boolean = true
    fun copyModeChanged(copyMode: Boolean) = Unit
    fun onKeyDown(keyCode: Int, e: KeyEvent?, session: TerminalSession?): Boolean = false
    fun onKeyUp(keyCode: Int, e: KeyEvent?): Boolean = false
    fun onLongPress(event: MotionEvent?): Boolean = false
    fun readControlKey(): Boolean = false
    fun readAltKey(): Boolean = false
    fun readShiftKey(): Boolean = false
    fun readFnKey(): Boolean = false
    fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession?): Boolean = false
    fun onEmulatorSet() = Unit
    fun logError(tag: String?, message: String?) = Unit
    fun logWarn(tag: String?, message: String?) = Unit
    fun logInfo(tag: String?, message: String?) = Unit
    fun logDebug(tag: String?, message: String?) = Unit
    fun logVerbose(tag: String?, message: String?) = Unit
    fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) = Unit
    fun logStackTrace(tag: String?, e: Exception?) = Unit
}

class AppTerminalViewClient : TerminalViewClient

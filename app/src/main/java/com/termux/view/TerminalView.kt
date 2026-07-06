package com.termux.view

import android.content.Context
import android.view.ViewGroup
import com.termux.terminal.TerminalSession

class TerminalView(context: Context, attrs: Any?) : ViewGroup(context) {
    fun attachSession(session: TerminalSession) = Unit
    fun setTerminalViewClient(client: Any?) = Unit
    fun requestFocus() = Unit
}

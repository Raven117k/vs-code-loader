package com.termux.view

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import com.termux.terminal.TerminalSession

class TerminalView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {
    fun attachSession(session: TerminalSession) = Unit
    fun setTerminalViewClient(client: Any?) = Unit

    override fun requestFocus(): Boolean = super.requestFocus()

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        // No child views yet; keep the layout empty.
    }
}

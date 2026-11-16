package com.tvtext.mobile.app

import android.app.Activity
import android.graphics.Rect
import android.os.Build
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.widget.ScrollView

/** Keeps the send action visible as soon as the IME starts resizing the window. */
internal class KeyboardViewport(
    private val activity: Activity,
    private val page: ScrollView,
    private val editor: View,
    private val action: View,
    private val dp: (Int) -> Int
) : AutoCloseable {
    private val decor = activity.window.decorView
    private var observer: ViewTreeObserver? = null
    private val revealTask = Runnable { revealAction() }
    private var revealPending = false
    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        if (!closed && editor.hasFocus() && isImeVisible()) scheduleReveal()
        else cancelReveal()
    }
    private var closed = false

    fun start() {
        if (closed) return
        val tree = decor.viewTreeObserver
        observer = tree
        tree.addOnGlobalLayoutListener(layoutListener)
    }

    override fun close() {
        if (closed) return
        closed = true
        cancelReveal()
        observer?.takeIf { it.isAlive }?.removeOnGlobalLayoutListener(layoutListener)
        observer = null
    }

    private fun isImeVisible(): Boolean = if (Build.VERSION.SDK_INT >= 30) {
        decor.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true
    } else {
        val frame = Rect()
        decor.getWindowVisibleDisplayFrame(frame)
        val screenHeight = maxOf(decor.rootView.height, activity.resources.displayMetrics.heightPixels)
        screenHeight - frame.bottom > screenHeight / 6
    }

    private fun scheduleReveal() {
        if (revealPending) return
        revealPending = true
        page.post(revealTask)
    }

    private fun cancelReveal() {
        revealPending = false
        page.removeCallbacks(revealTask)
    }

    private fun revealAction() {
        revealPending = false
        if (closed || !editor.hasFocus() || !isImeVisible() || action.height == 0) return
        val frame = Rect()
        decor.getWindowVisibleDisplayFrame(frame)
        val position = IntArray(2)
        action.getLocationOnScreen(position)
        val top = position[1]
        val bottom = top + action.height
        val margin = dp(10)
        val scrollBy = when {
            bottom > frame.bottom - margin -> bottom - frame.bottom + margin
            top < frame.top + margin -> top - frame.top - margin
            else -> 0
        }
        if (scrollBy != 0) page.scrollBy(0, scrollBy)
    }
}

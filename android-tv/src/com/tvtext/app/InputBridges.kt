package com.tvtext.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.InputMethod
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.view.accessibility.AccessibilityNodeInfo
import java.util.UUID

/** API 33+ path. Kept in its own class so Android 9-12 never resolves these classes. */
object Api33InputBridge {
    private fun connection(service: AccessibilityService): InputMethod? = service.inputMethod?.takeIf {
        it.currentInputStarted && it.currentInputConnection != null && it.currentInputEditorInfo != null &&
            it.currentInputEditorInfo?.inputType?.let { type -> type != 0 } == true &&
            it.currentInputEditorInfo?.let { editor ->
                editor.packageName != service.packageName ||
                    (MainActivity.keyboardTestOpen &&
                        editor.fieldId == service.resources.getIdentifier("keyboard_test_input", "id", service.packageName))
            } == true
    }

    fun status(service: AccessibilityService): Protocol.Response {
        val input = connection(service)
        return Protocol.Response(Protocol.OK,
            if (input == null) "Подключено. Откройте поле ввода и клавиатуру на ТВ." else "Подключено",
            ready = input != null, target = input?.currentInputEditorInfo?.packageName ?: "")
    }

    fun insert(service: AccessibilityService, text: String, allowed: () -> Boolean = { true }): Protocol.Response {
        val input = connection(service)
            ?: return Protocol.Response(Protocol.NO_FIELD, "На ТВ нет активного поля. Откройте поле ввода с клавиатурой.")
        val target = input.currentInputEditorInfo?.packageName
        if (target == null)
            return Protocol.Response(Protocol.NO_FIELD, "Откройте поле в нужном приложении на ТВ.")
        val connection = input.currentInputConnection
            ?: return Protocol.Response(Protocol.NO_FIELD, "Поле ввода закрыто. Откройте его снова.")
        if (!allowed()) return Protocol.Response(Protocol.AUTH, "Подключение отозвано на ТВ.")
        try { connection.commitText(text, 1, null) }
        catch (_: Exception) { return Protocol.Response(Protocol.NO_FIELD, "Поле ввода не приняло текст. Откройте его снова.", target = target) }
        return Protocol.Response(Protocol.OK, "Команда вставки передана полю на ТВ.", ready = true, target = target)
    }
}

/** API 28-32 fallback. It uses only public AccessibilityNodeInfo actions. */
object LegacyInputBridge {
    private val paste = AccessibilityNodeInfo.AccessibilityAction.ACTION_PASTE.id

    // AccessibilityNodeInfo.recycle() is deprecated on newer SDKs but remains
    // required for the Android 9-12 compatibility path.
    @Suppress("DEPRECATION")
    private fun release(node: AccessibilityNodeInfo) {
        node.recycle()
    }

    private fun focusedNode(service: AccessibilityService): AccessibilityNodeInfo? {
        val root = service.rootInActiveWindow ?: return null
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null) {
            release(root)
            return focused
        }
        return if (root.isEditable) root else {
            release(root)
            null
        }
    }

    private fun supports(node: AccessibilityNodeInfo, action: Int) =
        node.isEditable && node.isEnabled && node.actionList.any { it.id == action }

    private fun target(node: AccessibilityNodeInfo, service: AccessibilityService): String? =
        node.packageName?.toString()?.takeIf {
            it != service.packageName || (MainActivity.keyboardTestOpen &&
                node.viewIdResourceName == "${service.packageName}:id/keyboard_test_input")
        }

    private const val clipboardMarker = "com.tvtext.clipboard-write"
    private class ClipboardWrite(val manager: ClipboardManager, val original: ClipData?, var marker: String = "")
    private val clipboardHandler = Handler(Looper.getMainLooper())
    private var clipboardWrite: ClipboardWrite? = null
    private var clipboardRestore: Runnable? = null

    private fun ownsClipboard(write: ClipboardWrite) =
        write.manager.primaryClip?.description?.extras?.getString(clipboardMarker) == write.marker

    @Synchronized private fun prepareClipboard(service: AccessibilityService, text: String): ClipboardWrite {
        val clipboard = service.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        // Consecutive inserts share the original clipboard, never the text of
        // the previous insert. An intervening user copy starts a new session.
        val write = clipboardWrite?.takeIf { it.manager === clipboard && ownsClipboard(it) }
            ?: ClipboardWrite(clipboard, clipboard.primaryClip)
        val marker = UUID.randomUUID().toString()
        val clip = ClipData.newPlainText("TVText", text).apply {
            description.extras = PersistableBundle().apply { putString(clipboardMarker, marker) }
        }
        clipboard.setPrimaryClip(clip)
        clipboardRestore?.let { clipboardHandler.removeCallbacks(it) }
        write.marker = marker
        clipboardWrite = write
        return write
    }

    @Synchronized private fun restoreClipboard(write: ClipboardWrite, delayMillis: Long) {
        val marker = write.marker
        clipboardRestore = Runnable { synchronized(this) {
            if (clipboardWrite !== write || write.marker != marker) return@synchronized
            try {
                if (ownsClipboard(write)) {
                    if (write.original != null) write.manager.setPrimaryClip(write.original)
                    else write.manager.clearPrimaryClip()
                }
            } catch (_: Exception) { }
            finally { clipboardWrite = null; clipboardRestore = null }
        } }.also { clipboardHandler.postDelayed(it, delayMillis) }
    }

    fun status(service: AccessibilityService): Protocol.Response {
        val node = focusedNode(service)
        if (node == null) return Protocol.Response(Protocol.OK,
            "Подключено. Откройте поле ввода и клавиатуру на ТВ.", ready = false)
        val packageName = target(node, service)
        val ready = packageName != null && supports(node, paste)
        val response = Protocol.Response(Protocol.OK,
            if (ready) "Подключено" else "Поле не поддерживает вставку текста на этом ТВ.",
            ready = ready, target = packageName ?: "")
        release(node)
        return response
    }

    fun insert(service: AccessibilityService, text: String, allowed: () -> Boolean = { true }): Protocol.Response {
        val node = focusedNode(service)
            ?: return Protocol.Response(Protocol.NO_FIELD, "На ТВ нет активного поля. Откройте поле ввода с клавиатурой.")
        val packageName = target(node, service)
        if (packageName == null) {
            release(node)
            return Protocol.Response(Protocol.NO_FIELD, "Откройте поле в нужном приложении на ТВ.")
        }
        if (!allowed()) {
            release(node)
            return Protocol.Response(Protocol.AUTH, "Подключение отозвано на ТВ.")
        }
        // Some editors expose Paste only when the clipboard contains text.
        // Populate it before refreshing the node's actions, including when the
        // original clipboard is empty. Restore it even if Paste is unavailable.
        val clipboard = try { prepareClipboard(service, text) } catch (_: Exception) {
            release(node)
            return Protocol.Response(Protocol.NO_FIELD, "Буфер обмена недоступен для вставки.", target = packageName)
        }
        try { node.refresh() } catch (_: Exception) { }
        if (supports(node, paste)) {
            if (!allowed()) {
                restoreClipboard(clipboard, 0)
                release(node)
                return Protocol.Response(Protocol.AUTH, "Подключение отозвано на ТВ.")
            }
            val pasted = try { node.performAction(paste) } catch (_: Exception) { false }
            if (pasted) {
                restoreClipboard(clipboard, 750)
                release(node)
                return if (allowed()) Protocol.Response(Protocol.OK, "Команда вставки передана полю на ТВ.", ready = true, target = packageName)
                else Protocol.Response(Protocol.AUTH, "Подключение отозвано на ТВ.")
            }
        }
        restoreClipboard(clipboard, 0)
        // ACTION_SET_TEXT replaces the entire field. Never use it as a fallback
        // for insertion: preserving existing text must not require reading it.
        release(node)
        return Protocol.Response(Protocol.NO_FIELD, "Это поле не поддерживает удалённую вставку текста.", target = packageName)
    }
}

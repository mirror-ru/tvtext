package com.tvtext.app

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.StateListDrawable
import android.text.Layout
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*

/** Connection/revoke/keyboard dialogs and their focus/lifetime, separate from the main screen. */
class ReceiverDialogs(context: Context, private val runtime: BridgeRuntime,
    private val refresh: () -> Unit, private val keyboardState: (Boolean) -> Unit,
    private val keyboardClosed: () -> Unit) : TvWidgets(context) {
    private var dialog: AlertDialog? = null
    val current get() = dialog
    fun dismiss() { dialog?.dismiss() }
    private fun toast(message: String) { Toast.makeText(context,message,Toast.LENGTH_LONG).show() }
    fun showComputers() {
        val content = column().apply { tag="connections-panel";setPadding(dp(24),dp(24),dp(24),dp(24)) }
        var listDialog: AlertDialog? = null
        val tokens = runtime.engine.pairedTokens()
        val header=row().apply { gravity=Gravity.CENTER_VERTICAL }
        header.addView(computerBadge(40),LinearLayout.LayoutParams(dp(40),dp(40)))
        header.addView(label(tr("connected_pcs"),22,ink).apply {
            typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
        },LinearLayout.LayoutParams(0,-2,1f).apply { marginStart=dp(12);marginEnd=dp(12) })
        header.addView(label(tokens.size.toString(),13,accent).apply {
            tag="connections-count";gravity=Gravity.CENTER;contentDescription=tr("connections_count_format",tokens.size)
            typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL);background=shape(TvTheme.icon,10)
            setPadding(dp(10),0,dp(10),0)
        },LinearLayout.LayoutParams(-2,dp(28)))
        content.addView(header,full())
        var firstRemove: View? = null
        val deleteActions=mutableListOf<View>()
        if (tokens.isEmpty()) {
            val empty=column().apply {
                tag="connections-empty";gravity=Gravity.CENTER;setPadding(dp(16),dp(24),dp(16),dp(24))
                background=shape(TvTheme.inset,16)
            }
            empty.addView(computerBadge(48),LinearLayout.LayoutParams(dp(48),dp(48)))
            empty.addView(label(tr("no_connected_pcs"),15,muted).apply { gravity=Gravity.CENTER },full().apply { topMargin=dp(12) })
            content.addView(empty,full().apply { topMargin=dp(20) })
        } else {
            val list=column()
            val itemHeight=maxOf(dp(72),TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,40f,resources.displayMetrics).toInt()+dp(24))
            tokens.forEachIndexed { index, token ->
                val item = row().apply {
                    tag="connection-item"
                    gravity = Gravity.CENTER_VERTICAL
                    background=StateListDrawable().apply {
                        addState(intArrayOf(android.R.attr.state_activated),shape(TvTheme.inset,16,accent))
                        addState(intArrayOf(),shape(TvTheme.inset,16).apply { setStroke(dp(1),TvTheme.border) })
                    }
                    setPadding(dp(14),dp(12),dp(12),dp(12))
                }
                item.addView(computerBadge(40),LinearLayout.LayoutParams(dp(40),dp(40)))
                val text = column()
                text.addView(label(tr("pc_title_format", index + 1),17,ink).apply {
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END
                }, full())
                text.addView(label(tr("pc_id_format", token.takeLast(6)), 12, muted), full().apply { topMargin = dp(3) })
                item.addView(text,LinearLayout.LayoutParams(0,-2,1f).apply { marginStart=dp(12) })
                val remove = deleteButton {
                    listDialog?.dismiss()
                    confirmRevoke(token,index+1)
                }.apply {
                    tag="connection-delete";contentDescription=tr("delete_connection")+": "+tr("pc_title_format",index+1)
                    setOnFocusChangeListener { _,focused -> item.isActivated=focused }
                }
                if(firstRemove==null)firstRemove=remove
                deleteActions.add(remove)
                item.addView(remove,LinearLayout.LayoutParams(dp(48),dp(48)).apply { marginStart=dp(12) })
                list.addView(item,full(itemHeight).apply { if(index>0)topMargin=dp(8) })
            }
            val desiredHeight=minOf(tokens.size,3)*itemHeight+dp(8)*(minOf(tokens.size,3)-1)
            val availableHeight=maxOf(itemHeight,dp(resources.configuration.screenHeightDp-240))
            val scroll=ScrollView(context).apply {
                tag="connections-list";isFocusable=false;isVerticalScrollBarEnabled=true;isScrollbarFadingEnabled=true
                descendantFocusability=ViewGroup.FOCUS_AFTER_DESCENDANTS;addView(list)
            }
            content.addView(scroll,full(minOf(desiredHeight,availableHeight)).apply { topMargin=dp(20) })
        }
        val footer=row().apply { gravity=Gravity.END }
        val close=button(tr("close")) { listDialog?.dismiss() }.apply { tag="connections-close" }
        deleteActions.forEachIndexed { index,action ->
            action.setOnKeyListener { _,key,event ->
                if(event.action!=KeyEvent.ACTION_DOWN)false
                else when {
                    key==KeyEvent.KEYCODE_DPAD_DOWN -> (deleteActions.getOrNull(index+1) ?: close).requestFocus()
                    key==KeyEvent.KEYCODE_DPAD_UP&&index>0 -> deleteActions[index-1].requestFocus()
                    else -> false
                }
            }
        }
        close.setOnKeyListener { _,key,event ->
            if(event.action==KeyEvent.ACTION_DOWN&&key==KeyEvent.KEYCODE_DPAD_UP)deleteActions.lastOrNull()?.requestFocus() ?: false else false
        }
        footer.addView(close,LinearLayout.LayoutParams(dp(112),dp(44)))
        content.addView(footer,full().apply { topMargin=dp(20) })
        listDialog=showDialog(AlertDialog.Builder(context),customView=content)
        val width=minOf(520,resources.configuration.screenWidthDp-64)
        listDialog?.window?.setLayout(dp(width),WindowManager.LayoutParams.WRAP_CONTENT)
        content.post { (firstRemove ?: close).requestFocus() }
    }
    private fun dialogContent(title: String, glyph: String, color: Int = accent)=column().apply {
        setPadding(dp(24),dp(24),dp(24),dp(24))
        val header=row().apply { gravity=Gravity.CENTER_VERTICAL }
        header.addView(label(glyph,22,color).apply {
            gravity=Gravity.CENTER;typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
            background=shape(TvTheme.inset,12)
            importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
        },LinearLayout.LayoutParams(dp(42),dp(42)))
        header.addView(label(title,22,ink).apply {
            typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
        },LinearLayout.LayoutParams(0,-2,1f).apply { marginStart=dp(14) })
        addView(header,full())
    }
    private fun dialogMessage(value: String)=label(value,15,muted).apply {
        breakStrategy=Layout.BREAK_STRATEGY_SIMPLE;setLineSpacing(dp(3).toFloat(),1f)
    }
    private fun confirmRevoke(token: String, number: Int) {
        val content=dialogContent(tr("delete_connection_title"),"!",red).apply { tag="revoke-panel" }
        val device=row().apply {
            gravity=Gravity.CENTER_VERTICAL;setPadding(dp(12),dp(12),dp(12),dp(12))
            background=shape(TvTheme.inset,14)
        }
        device.addView(computerBadge(36),LinearLayout.LayoutParams(dp(36),dp(36)))
        device.addView(column().apply {
            addView(label(tr("pc_title_format",number),16,ink),full())
            addView(label(tr("pc_id_format",token.takeLast(6)),12,muted),full().apply { topMargin=dp(4) })
        },LinearLayout.LayoutParams(0,-2,1f).apply { marginStart=dp(12) })
        content.addView(device,full().apply { topMargin=dp(18) })
        content.addView(dialogMessage(tr("delete_connection_message")),full().apply { topMargin=dp(16) })
        val actions=row()
        val cancel=button(tr("cancel")) { dialog?.dismiss();showComputers() }.apply { tag="revoke-cancel" }
        val remove=button(tr("delete_connection_action")) {
            try { runtime.engine.revokeToken(token) }
            catch (_: Exception) { toast(tr("save_change_failed")) }
            dialog?.dismiss();refresh();showComputers()
        }.apply { tag="revoke-confirm";setTextColor(Color.WHITE);background=dangerBackground() }
        actions.addView(cancel,LinearLayout.LayoutParams(0,dp(46),1f))
        actions.addView(remove,LinearLayout.LayoutParams(0,dp(46),1f).apply { marginStart=dp(12) })
        content.addView(actions,full().apply { topMargin=dp(22) })
        showDialog(AlertDialog.Builder(context),customView=content)
        content.post { remove.requestFocus() }
    }
    fun showKeyboardTest() {
        val content=dialogContent(tr("keyboard_test"),"T").apply { tag="keyboard-test-panel" }
        content.addView(dialogMessage(tr("keyboard_test_hint")),full().apply { topMargin=dp(16) })
        val editor=EditText(context).apply {
            id=resources.getIdentifier("keyboard_test_input","id",packageName);tag="keyboard-test-input"
            hint=tr("keyboard_test_placeholder");contentDescription=tr("keyboard_test_field")
            textSize=18f;setTextColor(ink);setHintTextColor(muted)
            inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions=EditorInfo.IME_FLAG_NO_EXTRACT_UI
            minLines=2;maxLines=3;gravity=Gravity.TOP or Gravity.START
            setPadding(dp(14),dp(12),dp(14),dp(12))
            background=StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused),shape(TvTheme.inset,13,accent))
                addState(intArrayOf(),shape(TvTheme.inset,13,TvTheme.border))
            }
        }
        content.addView(editor,full().apply { topMargin=dp(18) })
        val actions=row()
        val close=button(tr("close"),true) { dialog?.dismiss() }.apply { tag="keyboard-test-close" }
        actions.addView(close,LinearLayout.LayoutParams(0,dp(44),1f))
        content.addView(actions,full().apply { topMargin=dp(18) })
        editor.requestFocus()
        val current=showDialog(AlertDialog.Builder(context),customView=content,showKeyboard=true)
        keyboardState(true)
        current.setOnDismissListener {
            keyboardState(false)
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(editor.windowToken,0)
            if(dialog===current)dialog=null
            keyboardClosed()
        }
        editor.post {
            if(current.isShowing) {
                editor.requestFocus()
                (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(editor,InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }
    private fun showDialog(builder: AlertDialog.Builder, customView: View, showKeyboard: Boolean = false): AlertDialog {
        val current=builder.create()
        current.setView(customView,0,0,0,0)
        dialog?.dismiss();dialog=current
        current.setOnDismissListener { if(dialog===current)dialog=null }
        current.setOnShowListener {
            current.window?.setBackgroundDrawable(shape(TvTheme.surface,18,TvTheme.border))
        }
        // The legacy resize flag keeps the dialog usable on Android 9-10 TV firmware.
        @Suppress("DEPRECATION")
        if(showKeyboard)current.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        current.show()
        current.window?.setLayout(dp(minOf(520,resources.configuration.screenWidthDp-64)),WindowManager.LayoutParams.WRAP_CONTENT)
        return current
    }
}

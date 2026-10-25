@file:Suppress("DEPRECATION")
package com.tvtext.mobile.app

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.*

/** Dialog layout, validation and focus. Sender operations are provided as callbacks. */
class SenderDialogs(private val activity: Activity, private val theme: () -> Palette, private val strings: (String) -> String) {
    private var dialog: AlertDialog? = null
    val current get() = dialog
    fun dismiss() { dialog?.dismiss() }
    private val palette get() = theme()
    private val ink get() = palette.ink
    private val muted get() = palette.muted
    private val blue get() = palette.accent
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density + .5f).toInt()
    private fun text(key: String) = strings(key)
    private fun background(fill: Int, radius: Int = 14, border: Int = Color.TRANSPARENT) = GradientDrawable().apply {
        setColor(fill); cornerRadius = dp(radius).toFloat(); if (border != Color.TRANSPARENT) setStroke(dp(1), border)
    }
    private fun label(value: String, size: Float, color: Int = ink, bold: Boolean = false) = TextView(activity).apply {
        setText(value); textSize = size; setTextColor(color); if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        includeFontPadding = false
    }
    private fun column() = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private fun styleInput(editor: EditText) {
        EditorStyle.apply(editor,palette)
    }
    private fun LinearLayout.add(view: View, width: Int = -1, height: Int = -2, top: Int = 0) {
        addView(view, LinearLayout.LayoutParams(width, height).apply { topMargin = dp(top) })
    }
    private fun showDialog(builder: AlertDialog.Builder, ready: (AlertDialog) -> Unit = {}) {
        dialog?.dismiss(); dialog = builder.create().also { current ->
            current.setOnShowListener {
                current.window?.setBackgroundDrawable(background(palette.surface,22))
                current.findViewById<TextView>(android.R.id.message)?.setTextColor(ink)
                for(which in listOf(AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_POSITIVE)) {
                    current.getButton(which)?.apply {
                        isAllCaps=false
                        setTextColor(ColorStateList(arrayOf(intArrayOf(android.R.attr.state_enabled),intArrayOf()),intArrayOf(if(which==AlertDialog.BUTTON_POSITIVE) Color.WHITE else palette.buttonInk,muted)))
                        background=RippleDrawable(ColorStateList.valueOf(palette.ripple),background(if(which==AlertDialog.BUTTON_POSITIVE) palette.primary else palette.button,12),null)
                    }
                }
                ready(current)
            }
            current.setOnDismissListener { if(dialog === current) dialog = null }; current.show()
        }
    }
    private fun dialogHeading(key: String): View = LinearLayout(activity).apply {
        gravity=Gravity.CENTER_VERTICAL;setPadding(dp(24),dp(22),dp(24),dp(14))
        addView(Artwork(activity,"tv",palette).apply { background=background(palette.icon,12) },LinearLayout.LayoutParams(dp(40),dp(40)))
        addView(label(text(key),21f,ink,true),LinearLayout.LayoutParams(0,-2,1f).apply { leftMargin=dp(12) })
    }
    fun pair(onCode: (String) -> Unit) {
        val body = column().apply { setPadding(dp(24),dp(4),dp(24),dp(8)) }
        body.add(label(text("pair_hint"),14f,muted).apply { setLineSpacing(dp(3).toFloat(),1f) })
        val code = EditText(activity).apply {
            tag="pair-code"; inputType=InputType.TYPE_CLASS_NUMBER; filters=arrayOf(InputFilter.LengthFilter(8)); textSize=24f; gravity=Gravity.CENTER
            styleInput(this);setPadding(dp(12),dp(12),dp(12),dp(12)); backgroundTintList=null;background=background(palette.input,12,palette.border); hint=text("pair_code")
            setSingleLine(); imeOptions=EditorInfo.IME_ACTION_DONE
        }
        body.add(code,top=18)
        showDialog(AlertDialog.Builder(activity).setCustomTitle(dialogHeading("pair_heading")).setView(body).setNegativeButton(text("cancel"),null).setPositiveButton(text("connect"),null)) { current ->
            val submit = current.getButton(AlertDialog.BUTTON_POSITIVE); submit.isEnabled=false
            code.addTextChangedListener(object:TextWatcher {
                override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int)=Unit
                override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) { submit.isEnabled=s?.matches(Regex("[0-9]{8}")) == true }
                override fun afterTextChanged(s:Editable?)=Unit
            })
            submit.setOnClickListener {
                val pin=code.text.toString(); if(!pin.matches(Regex("[0-9]{8}"))) return@setOnClickListener
                current.dismiss();onCode(pin)
            }
            code.setOnEditorActionListener { _, action, _ -> if(action==EditorInfo.IME_ACTION_DONE && submit.isEnabled) { submit.performClick(); true } else false }
            code.requestFocus(); current.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        }
    }
    fun fingerprint(fingerprint: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
        val body=column().apply { setPadding(dp(24),dp(8),dp(24),dp(8)) }
        body.add(label(Wire.shortFingerprint(fingerprint),23f,blue,true).apply { typeface=Typeface.MONOSPACE; gravity=Gravity.CENTER; setPadding(0,dp(12),0,dp(12)); background=background(palette.button,12) })
        body.add(label(text("fingerprint_hint"),14f,muted).apply { setLineSpacing(dp(3).toFloat(),1f) },top=16)
        showDialog(AlertDialog.Builder(activity).setTitle(text("fingerprint_title")).setView(body).setNegativeButton(text("cancel")) { _,_ -> onCancel() }.setPositiveButton(text("confirm")) { _,_ -> onConfirm() })
    }
    fun rename(value: String, onSave: (String) -> Boolean) {
        val body=column().apply { setPadding(dp(24),dp(4),dp(24),dp(8)) }
        body.add(label(text("rename_hint"),14f,muted))
        val name=EditText(activity).apply {
            tag="rename-input";textSize=18f;setSingleLine();imeOptions=EditorInfo.IME_ACTION_DONE;filters=arrayOf(InputFilter.LengthFilter(80));styleInput(this)
            backgroundTintList=null;background=background(palette.input,12,palette.border);setPadding(dp(14),dp(14),dp(14),dp(14));setText(value)
            contentDescription=text("rename_title")
        }
        body.add(name,top=18)
        showDialog(AlertDialog.Builder(activity).setCustomTitle(dialogHeading("rename_title")).setView(body).setNegativeButton(text("cancel"),null).setPositiveButton(text("save"),null)) { current ->
            val submit=current.getButton(AlertDialog.BUTTON_POSITIVE)
            name.addTextChangedListener(object:TextWatcher {
                override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int)=Unit
                override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){ submit.isEnabled=Wire.validName(s.toString()) }
                override fun afterTextChanged(s:Editable?)=Unit
            });submit.isEnabled=Wire.validName(name.text.toString())
            submit.setOnClickListener {
                if(!Wire.validName(name.text.toString()))return@setOnClickListener
                if(onSave(name.text.toString()))current.dismiss()
            }
            name.setOnEditorActionListener { _,action,_ -> if(action==EditorInfo.IME_ACTION_DONE&&submit.isEnabled){submit.performClick();true}else false }
            name.requestFocus();name.selectAll();current.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        }
    }
    fun confirmUnpair(onConfirm: () -> Unit) {
        showDialog(AlertDialog.Builder(activity).setTitle(text("unbind_title")).setMessage(text("unbind_hint")).setNegativeButton(text("cancel"),null).setPositiveButton(text("unbind")) { _,_ -> onConfirm() })
    }
    fun remoteFailure() {
        showDialog(AlertDialog.Builder(activity).setTitle(text("unbind_failed_title")).setMessage(text("unbind_failed")).setPositiveButton(android.R.string.ok,null))
    }
}

package com.tvtext.mobile.app

import android.content.res.ColorStateList
import android.os.Build
import android.widget.EditText

object EditorStyle {
    fun apply(editor: EditText, palette: Palette) {
        editor.setTextColor(palette.ink); editor.setHintTextColor(palette.muted); editor.highlightColor=palette.selected
        editor.backgroundTintList=ColorStateList.valueOf(palette.accent)
        if(Build.VERSION.SDK_INT>=29) editor.textCursorDrawable?.mutate()?.setTint(palette.accent)
    }
}

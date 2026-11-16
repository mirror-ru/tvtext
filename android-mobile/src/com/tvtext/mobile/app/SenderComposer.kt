package com.tvtext.mobile.app

import android.app.Activity
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

/** Builds and owns the message editor, send action, and compact language/theme controls. */
internal class SenderComposer(
    private val activity: Activity,
    private val widgets: SenderWidgets,
    private val text: (String) -> String,
    private val language: () -> String,
    private val darkMode: () -> Boolean,
    private val onInputChanged: () -> Unit,
    private val onSend: () -> Unit,
    private val onLanguageClick: () -> Unit,
    private val onThemeClick: () -> Unit,
) {
    lateinit var input: EditText
        private set
    lateinit var sendButton: TextView
        private set
    lateinit var languageButton: TextView
        private set
    lateinit var themeButton: View
        private set
    lateinit var view: LinearLayout
        private set

    fun build(draft: String) {
        view = widgets.column()
        buildTextCard(draft)
        buildSendButton()
        buildFooter()
    }

    private fun buildTextCard(draft: String) {
        val textCard = widgets.card().apply { tag = "text-card" }
        textCard.addView(widgets.label(text("text_prompt"), 16f, widgets.colors().ink, true))

        input = EditText(activity).apply {
            tag = "text-input"
            contentDescription = text("text_accessible")
            textSize = 18f
            setTextColor(widgets.colors().ink)
            EditorStyle.apply(this, widgets.colors())
            background = null
            setPadding(0, widgets.dp(6), 0, widgets.dp(4))
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            setLines(3)
            maxLines = 3
            isVerticalScrollBarEnabled = true
            isScrollbarFadingEnabled = true
            filters = arrayOf(InputFilter.LengthFilter(32768))
            setText(draft)
            setOnEditorActionListener { _, action, event ->
                val enterPressed = event != null &&
                    event.keyCode == KeyEvent.KEYCODE_ENTER &&
                    event.action == KeyEvent.ACTION_DOWN &&
                    !event.isShiftPressed
                if (action == EditorInfo.IME_ACTION_SEND || enterPressed) {
                    onSend()
                    true
                } else {
                    false
                }
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = onInputChanged()
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }

        textCard.addView(input, LinearLayout.LayoutParams(-1, -2).apply { topMargin = widgets.dp(10) })
        view.addView(textCard)
    }

    private fun buildSendButton() {
        val sendPanel = FrameLayout(activity)
        sendButton = widgets.button(text("send"), primary = true).apply {
            textSize = 16f
            tag = "send"
            setOnClickListener { onSend() }
        }
        sendPanel.addView(sendButton, FrameLayout.LayoutParams(-1, -1))
        sendPanel.addView(
            Artwork(activity, "send", widgets.colors()),
            FrameLayout.LayoutParams(widgets.dp(40), widgets.dp(40)).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                rightMargin = widgets.dp(10)
            },
        )
        view.addView(sendPanel, LinearLayout.LayoutParams(-1, widgets.dp(54)).apply { topMargin = widgets.dp(16) })
    }

    private fun buildFooter() {
        val footer = LinearLayout(activity).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL }
        languageButton = widgets.button(language().uppercase(Locale.ROOT)).apply {
            textSize = 13f
            contentDescription = text("language")
            tag = "language"
            setOnClickListener { onLanguageClick() }
        }
        themeButton = widgets.iconButton(
            if (darkMode()) "sun" else "moon",
            text(if (darkMode()) "theme_light" else "theme_dark"),
        )
            .apply {
                tag = "theme"
                setOnClickListener { onThemeClick() }
            }
        footer.addView(languageButton, LinearLayout.LayoutParams(widgets.dp(54), widgets.dp(48)))
        footer.addView(themeButton, LinearLayout.LayoutParams(widgets.dp(48), widgets.dp(48)).apply {
            marginStart = widgets.dp(8)
        })
        view.addView(footer, LinearLayout.LayoutParams(-1, -2).apply { topMargin = widgets.dp(10) })
    }
}

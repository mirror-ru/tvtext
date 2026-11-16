package com.tvtext.mobile.app

import android.app.Activity
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView

/** Builds the sender's home layout and owns its screen controls. */
internal class SenderScreen(
    private val activity: Activity,
    private val palette: () -> Palette,
    private val dp: (Int) -> Int,
    private val text: (String) -> String,
    private val versionLabel: () -> String,
    private val language: () -> String,
    private val darkMode: () -> Boolean,
    private val rowHeight: () -> Int,
    private val onDeviceSelected: (Tv) -> Unit,
    private val onRefresh: () -> Unit,
    private val onRename: () -> Unit,
    private val onConnect: () -> Unit,
    private val onInputChanged: () -> Unit,
    private val onSend: () -> Unit,
    private val onLanguageClick: () -> Unit,
    private val onThemeClick: () -> Unit,
) {
    private val widgets = SenderWidgets(activity, palette, dp)

    lateinit var page: ScrollView
        private set
    private lateinit var rootContent: LinearLayout
    lateinit var devicePanel: DeviceListPanel
        private set
    lateinit var composer: SenderComposer
        private set
    lateinit var connectButton: TextView
        private set
    lateinit var renameButton: TextView
        private set
    lateinit var refreshButton: View
        private set

    fun build(draft: String): ScrollView {
        page = ScrollView(activity).apply {
            tag = "page"
            setBackgroundColor(palette().canvas)
            isFillViewport = true
            isVerticalScrollBarEnabled = false
        }

        val wide = activity.resources.configuration.screenWidthDp >= 840
        val maxWidth = if (wide) 1080 else 680
        val stage = FrameLayout(activity)
        val content = widgets.column().apply {
            setPadding(dp(20), dp(18), dp(20), dp(12))
            isFocusableInTouchMode = true
        }
        rootContent = content
        val contentWidth = dp(minOf(activity.resources.configuration.screenWidthDp, maxWidth))
        stage.addView(content, FrameLayout.LayoutParams(
            contentWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL,
        ))
        page.addView(stage, FrameLayout.LayoutParams(-1, -2))

        buildHeader(content)
        val workspace = buildWorkspace(content, wide)
        buildDeviceCard(workspace, wide, onDeviceSelected, onRefresh, onRename, onConnect)
        buildComposer(workspace, wide, draft, onInputChanged, onSend, onLanguageClick, onThemeClick)
        return page
    }

    fun requestContentFocus() {
        rootContent.requestFocus()
    }

    private fun buildHeader(content: LinearLayout) {
        val header = FrameLayout(activity)
        val heading = widgets.column().apply {
            addView(widgets.label("TVText", 32f, palette().ink, bold = true))
            addView(widgets.label(versionLabel(), 12f, palette().muted),
                LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(7) })
        }
        header.addView(heading, FrameLayout.LayoutParams(-2, -2).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
        })
        header.addView(Artwork(activity, "link", palette()), FrameLayout.LayoutParams(dp(124), dp(68)).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        })
        content.addView(header, LinearLayout.LayoutParams(-1, dp(76)))
    }

    private fun buildWorkspace(content: LinearLayout, wide: Boolean): LinearLayout {
        val workspace = LinearLayout(activity).apply {
            orientation = if (wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            gravity = Gravity.TOP
        }
        content.addView(workspace, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })
        return workspace
    }

    private fun buildDeviceCard(
        workspace: LinearLayout,
        wide: Boolean,
        onDeviceSelected: (Tv) -> Unit,
        onRefresh: () -> Unit,
        onRename: () -> Unit,
        onConnect: () -> Unit,
    ) {
        val deviceCard = widgets.card()
        if (wide) workspace.addView(deviceCard, LinearLayout.LayoutParams(0, -2, 1f))
        else workspace.addView(deviceCard)

        val title = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        title.addView(widgets.label(text("televisions"), 11f, palette().muted, bold = true),
            LinearLayout.LayoutParams(0, -2, 1f))
        refreshButton = widgets.iconButton("refresh", text("refresh")).apply {
            tag = "refresh"
            setOnClickListener { onRefresh() }
        }
        title.addView(refreshButton, LinearLayout.LayoutParams(dp(48), dp(48)))
        deviceCard.addView(title)

        devicePanel = DeviceListPanel(activity, palette, dp, rowHeight, onDeviceSelected)
        deviceCard.addView(devicePanel.view, LinearLayout.LayoutParams(-1, rowHeight() * 3 + dp(8)).apply {
            topMargin = dp(12)
        })

        val actions = LinearLayout(activity).apply { gravity = Gravity.END }
        renameButton = widgets.button(text("rename")).apply {
            tag = "rename"
            setOnClickListener { onRename() }
        }
        connectButton = widgets.button(text("connect")).apply {
            tag = "connect"
            setOnClickListener { onConnect() }
        }
        actions.addView(renameButton, LinearLayout.LayoutParams(0, dp(44), 1f))
        actions.addView(Space(activity), LinearLayout.LayoutParams(dp(8), 1))
        actions.addView(connectButton, LinearLayout.LayoutParams(0, dp(44), 1f))
        deviceCard.addView(actions, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
    }

    private fun buildComposer(
        workspace: LinearLayout,
        wide: Boolean,
        draft: String,
        onInputChanged: () -> Unit,
        onSend: () -> Unit,
        onLanguageClick: () -> Unit,
        onThemeClick: () -> Unit,
    ) {
        composer = SenderComposer(
            activity = activity,
            widgets = widgets,
            text = text,
            language = language,
            darkMode = darkMode,
            onInputChanged = onInputChanged,
            onSend = onSend,
            onLanguageClick = onLanguageClick,
            onThemeClick = onThemeClick,
        )
        composer.build(draft)
        if (wide) workspace.addView(composer.view, LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginStart = dp(20)
        }) else {
            workspace.addView(composer.view, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        }
    }

}

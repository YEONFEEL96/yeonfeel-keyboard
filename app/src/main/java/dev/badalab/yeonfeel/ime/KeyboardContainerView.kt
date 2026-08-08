package dev.badalab.yeonfeel.ime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.badalab.yeonfeel.R
import dev.badalab.yeonfeel.clipboard.ClipboardHistory
import dev.badalab.yeonfeel.settings.KeyboardSettings
import dev.badalab.yeonfeel.settings.LanguageSwitchMethod
import dev.badalab.yeonfeel.settings.ThemeMode

/**
 * IME 입력 뷰 전체: 상단 툴바 + (키보드 | 클립보드 패널 | 여백 조정 오버레이).
 * 여백은 키 영역을 감싸는 래퍼의 패딩으로 적용하며,
 * 레이아웃 메뉴의 화살표 핸들 드래그로 조정한다.
 */
@SuppressLint("ViewConstructor")
class KeyboardContainerView(
    context: Context,
    private val callbacks: Callbacks,
) : LinearLayout(context) {

    interface Callbacks {
        fun onKey(key: Key)
        fun onPaste(text: String)
        fun onOpenSettings()
        fun onLanguageSwipe()
        fun onMarginsCommitted(topDp: Int, bottomDp: Int, sideDp: Int, heightDp: Int)
        fun clipboardEntries(): List<ClipboardHistory.Entry>
        fun onClipboardDelete(texts: List<String>)
        fun onClipboardPin(texts: List<String>, pinned: Boolean)
    }

    val keyboardView = KeyboardView(context) { callbacks.onKey(it) }.apply {
        onLanguageSwipe = { callbacks.onLanguageSwipe() }
    }

    private var theme: KeyboardTheme = KeyboardTheme.DARK
    private val toolbar = LinearLayout(context)
    private val keyboardWrapper = FrameLayout(context)
    private val contentFrame = FrameLayout(context)
    private var clipboardPanel: View? = null
    private var clipboardHeader: View? = null
    private var adjustOverlay: MarginAdjustOverlay? = null

    // 클립보드 다중 선택 모드: 목적(삭제/고정)에 따라 헤더가 달라진다.
    private enum class ClipboardMode { NORMAL, DELETE, PIN }

    private var clipboardMode = ClipboardMode.NORMAL
    private val clipboardSelected = mutableSetOf<String>()

    private var toolbarEnabled = true
    private var marginTopDp = 0
    private var marginBottomDp = 0
    private var marginSideDp = 0
    private var keyboardHeightDp = KeyboardSettings.HEIGHT_DEFAULT

    private lateinit var settingsButton: TextView
    private lateinit var layoutButton: TextView
    private lateinit var clipboardButton: TextView

    init {
        orientation = VERTICAL

        toolbar.orientation = HORIZONTAL
        toolbar.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(44)).apply {
            // 툴바와 키 영역 사이 여백
            bottomMargin = dp(6)
        }
        settingsButton = toolbarButton(
            context.getString(R.string.toolbar_settings),
            context.getString(R.string.toolbar_settings_desc),
        ) { callbacks.onOpenSettings() }
        layoutButton = toolbarButton(
            context.getString(R.string.toolbar_layout),
            context.getString(R.string.toolbar_layout_desc),
        ) { toggleAdjustMode() }
        clipboardButton = toolbarButton(
            context.getString(R.string.toolbar_clipboard),
            context.getString(R.string.toolbar_clipboard_desc),
        ) { toggleClipboardPanel() }
        toolbar.addView(settingsButton)
        toolbar.addView(layoutButton)
        toolbar.addView(clipboardButton)
        addView(toolbar)

        keyboardWrapper.addView(
            keyboardView,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT),
        )
        contentFrame.addView(
            keyboardWrapper,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT),
        )
        addView(contentFrame, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    /** 설정 변경(테마·여백)을 반영한다. 열린 패널·조정 모드는 닫는다. */
    fun applySettings(settings: KeyboardSettings) {
        toolbarEnabled = settings.showToolbar
        val dark = when (settings.themeMode) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.SYSTEM ->
                resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
        theme = KeyboardTheme.of(dark, settings.highContrast)
        keyboardView.theme = theme
        keyboardView.showNumberRow = settings.showNumberRow
        keyboardView.koreanLayout = settings.koreanLayout
        keyboardView.shiftNumberRowSymbols = settings.shiftNumberRowSymbols
        keyboardView.showKeyBackground = settings.showKeyBackground
        keyboardView.hapticEnabled = settings.hapticEnabled
        keyboardView.hapticStrength = settings.hapticStrength
        KeyboardLayouts.favoriteSymbol = settings.favoriteSymbol.first()
        KeyboardLayouts.favoriteSymbolEnabled = settings.favoriteSymbolEnabled
        KeyboardLayouts.leftSymbolEnabled = settings.leftSymbolEnabled
        KeyboardLayouts.leftSymbol = settings.leftSymbol.first()
        val multiLanguage = settings.koreanEnabled && settings.englishEnabled
        keyboardView.showLangKey =
            multiLanguage && settings.languageSwitchMethod != LanguageSwitchMethod.SWIPE
        keyboardView.languageSwipeEnabled =
            multiLanguage && settings.languageSwitchMethod != LanguageSwitchMethod.BUTTON
        applyMargins(settings.marginTopDp, settings.marginBottomDp, settings.marginSideDp, settings.keyboardHeightDp)
        setBackgroundColor(theme.background)
        keyboardWrapper.setBackgroundColor(theme.background)
        toolbar.setBackgroundColor(theme.specialKey)
        listOf(settingsButton, layoutButton, clipboardButton).forEach { it.setTextColor(theme.text) }
        showKeyboard()
    }

    fun showKeyboard() {
        clipboardPanel?.let { contentFrame.removeView(it) }
        clipboardPanel = null
        clipboardHeader?.let { removeView(it) }
        clipboardHeader = null
        toolbar.visibility = if (toolbarEnabled) VISIBLE else GONE
        adjustOverlay?.let { contentFrame.removeView(it) }
        adjustOverlay = null
        clipboardMode = ClipboardMode.NORMAL
        clipboardSelected.clear()
        keyboardView.visibility = VISIBLE
    }

    private fun applyMargins(topDp: Int, bottomDp: Int, sideDp: Int, heightDp: Int) {
        marginTopDp = topDp
        marginBottomDp = bottomDp
        marginSideDp = sideDp
        keyboardHeightDp = heightDp
        keyboardView.heightDp = heightDp
        keyboardWrapper.setPadding(dp(sideDp), dp(topDp), dp(sideDp), dp(bottomDp))
    }

    private fun toggleAdjustMode() {
        val wasAdjusting = adjustOverlay != null
        showKeyboard()
        if (wasAdjusting) return

        val overlay = MarginAdjustOverlay(
            context, marginTopDp, marginBottomDp, marginSideDp, keyboardHeightDp, theme,
            object : MarginAdjustOverlay.Listener {
                override fun onMarginsChanged(topDp: Int, bottomDp: Int, sideDp: Int, heightDp: Int) {
                    applyMargins(topDp, bottomDp, sideDp, heightDp)
                }

                override fun onCommit(topDp: Int, bottomDp: Int, sideDp: Int, heightDp: Int) {
                    callbacks.onMarginsCommitted(topDp, bottomDp, sideDp, heightDp)
                }

                override fun onDone() = showKeyboard()
            },
        )
        adjustOverlay = overlay
        contentFrame.addView(
            overlay,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
        )
    }

    private fun toggleClipboardPanel() {
        val wasOpen = clipboardPanel != null
        showKeyboard()
        if (wasOpen) return

        val panel = buildClipboardPanel()
        clipboardPanel = panel
        // 키 영역을 INVISIBLE로 두어 패널이 같은 높이를 유지하게 한다.
        keyboardView.visibility = INVISIBLE
        contentFrame.addView(
            panel,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
        )
        attachClipboardHeader()
    }

    /** 클립보드 헤더가 툴바 자리를 대체한다. */
    private fun attachClipboardHeader() {
        toolbar.visibility = GONE
        clipboardHeader?.let { removeView(it) }
        val header = buildClipboardHeader()
        clipboardHeader = header
        addView(
            header,
            0,
            LayoutParams(LayoutParams.MATCH_PARENT, dp(44)).apply { bottomMargin = dp(6) },
        )
    }

    private fun buildClipboardHeader(): View {
        val entries = callbacks.clipboardEntries()

        fun headerIcon(
            type: GlyphIconView.Type,
            description: String,
            onClick: () -> Unit,
        ) = GlyphIconView(context, type, theme.text).apply {
            contentDescription = description
            setOnClickListener { onClick() }
        }

        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(theme.specialKey)
            setPadding(dp(8), 0, dp(8), 0)
        }
        // 좌측: 키보드 아이콘(누르면 키보드로 복귀) + 제목
        header.addView(
            headerIcon(GlyphIconView.Type.KEYBOARD, context.getString(R.string.clipboard_back_to_keyboard)) {
                showKeyboard()
            },
        )
        header.addView(TextView(context).apply {
            text = context.getString(R.string.toolbar_clipboard_desc)
            setTextColor(theme.text)
            setTypeface(null, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(dp(6), 0, 0, 0)
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        })
        when (clipboardMode) {
            ClipboardMode.NORMAL -> {
                header.addView(
                    headerIcon(GlyphIconView.Type.PIN, context.getString(R.string.clipboard_pin)) {
                        if (entries.isNotEmpty()) enterClipboardMode(ClipboardMode.PIN)
                    },
                )
                header.addView(
                    headerIcon(GlyphIconView.Type.DELETE, context.getString(R.string.clipboard_delete)) {
                        if (entries.isNotEmpty()) enterClipboardMode(ClipboardMode.DELETE)
                    },
                )
            }
            ClipboardMode.DELETE -> {
                header.addView(
                    headerIcon(GlyphIconView.Type.DELETE, context.getString(R.string.clipboard_delete)) {
                        if (clipboardSelected.isNotEmpty()) {
                            callbacks.onClipboardDelete(clipboardSelected.toList())
                        }
                        exitClipboardSelection()
                    },
                )
                header.addView(
                    headerIcon(GlyphIconView.Type.CANCEL, context.getString(R.string.clipboard_cancel)) {
                        exitClipboardSelection()
                    },
                )
            }
            ClipboardMode.PIN -> {
                header.addView(
                    headerIcon(GlyphIconView.Type.PIN, context.getString(R.string.clipboard_pin)) {
                        if (clipboardSelected.isNotEmpty()) {
                            val allPinned = entries
                                .filter { it.text in clipboardSelected }
                                .all { it.pinned }
                            callbacks.onClipboardPin(clipboardSelected.toList(), !allPinned)
                        }
                        exitClipboardSelection()
                    },
                )
                header.addView(
                    headerIcon(GlyphIconView.Type.CANCEL, context.getString(R.string.clipboard_cancel)) {
                        exitClipboardSelection()
                    },
                )
            }
        }
        return header
    }

    private fun buildClipboardPanel(): View {
        val column = LinearLayout(context).apply {
            orientation = VERTICAL
            setBackgroundColor(theme.background)
            setPadding(dp(12), dp(2), dp(12), dp(8))
        }
        val entries = callbacks.clipboardEntries()

        if (entries.isEmpty()) {
            column.addView(TextView(context).apply {
                text = context.getString(R.string.clipboard_empty)
                setTextColor(theme.subText)
                gravity = Gravity.CENTER
                setPadding(0, dp(40), 0, 0)
            })
        } else {
            val list = LinearLayout(context).apply { orientation = VERTICAL }
            entries.forEach { entry -> list.addView(clipboardRow(entry)) }
            column.addView(ScrollView(context).apply { addView(list) })
        }
        return column
    }

    private fun clipboardRow(entry: ClipboardHistory.Entry): View {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(theme.key)
                cornerRadius = dp(10).toFloat()
            }
            setPadding(dp(12), dp(4), dp(12), dp(4))
            val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(6)
            layoutParams = lp
        }
        if (clipboardMode != ClipboardMode.NORMAL) {
            row.addView(android.widget.CheckBox(context).apply {
                isChecked = entry.text in clipboardSelected
                buttonTintList = android.content.res.ColorStateList.valueOf(0xFF3D8BFF.toInt())
                setOnCheckedChangeListener { _, checked ->
                    if (checked) clipboardSelected.add(entry.text) else clipboardSelected.remove(entry.text)
                }
            })
        }
        if (entry.pinned) {
            row.addView(GlyphIconView(context, GlyphIconView.Type.PIN, theme.subText, 0.55f))
        }
        row.addView(TextView(context).apply {
            text = entry.text
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(theme.text)
            setPadding(0, dp(6), 0, dp(6))
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        })
        row.setOnClickListener {
            if (clipboardMode != ClipboardMode.NORMAL) {
                if (entry.text in clipboardSelected) clipboardSelected.remove(entry.text)
                else clipboardSelected.add(entry.text)
                refreshClipboardPanel()
            } else {
                callbacks.onPaste(entry.text)
            }
        }
        row.setOnLongClickListener {
            if (clipboardMode == ClipboardMode.NORMAL) {
                clipboardMode = ClipboardMode.DELETE
                clipboardSelected.clear()
                clipboardSelected.add(entry.text)
                refreshClipboardPanel()
                true
            } else {
                false
            }
        }
        return row
    }

    private fun enterClipboardMode(mode: ClipboardMode) {
        clipboardMode = mode
        clipboardSelected.clear()
        refreshClipboardPanel()
    }

    private fun exitClipboardSelection() {
        clipboardMode = ClipboardMode.NORMAL
        clipboardSelected.clear()
        refreshClipboardPanel()
    }

    /** 선택 모드 상태를 유지한 채 헤더·패널만 다시 그린다 (showKeyboard는 상태를 리셋하므로 쓰면 안 된다). */
    private fun refreshClipboardPanel() {
        val panel = clipboardPanel ?: return
        contentFrame.removeView(panel)
        val newPanel = buildClipboardPanel()
        clipboardPanel = newPanel
        contentFrame.addView(
            newPanel,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
        )
        attachClipboardHeader()
    }

    private fun toolbarButton(label: String, description: String, onClick: () -> Unit): TextView =
        TextView(context).apply {
            text = label
            contentDescription = description
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setPadding(dp(20), 0, dp(20), 0)
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
            setOnClickListener { onClick() }
        }

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics,
        ).toInt()

}

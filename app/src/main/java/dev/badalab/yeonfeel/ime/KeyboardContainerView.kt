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
        fun onEmoji(emoji: String)
        fun onEmojiSearchStateChanged(open: Boolean)
        fun onOpenSettings()
        fun onLanguageSwipe()
        fun onToolbarOrderChanged(order: String)
        fun onRememberSymbol(symbol: Char)
        fun onOneHandedCycle()
        fun onSkinToneChanged(tone: Int)
        fun onTerminalKey(keyCode: Int)
        fun onOneHandedModeChanged(mode: dev.badalab.yeonfeel.settings.OneHandedMode)
        fun onMarginsCommitted(topDp: Int, bottomDp: Int, sideDp: Int, heightDp: Int)
        fun onSplitGapCommitted(percent: Int)
        fun clipboardEntries(): List<ClipboardHistory.Entry>
        fun onClipboardDelete(texts: List<String>)
        fun onClipboardPin(texts: List<String>, pinned: Boolean)
    }

    val keyboardView = KeyboardView(context) { callbacks.onKey(it) }.apply {
        onLanguageSwipe = { callbacks.onLanguageSwipe() }
        onVariantPicked = { callbacks.onRememberSymbol(it) }
        onShortcutSelected = { shortcut ->
            when (shortcut) {
                KeyboardView.SHORTCUT_CLIPBOARD -> toggleClipboardPanel()
                KeyboardView.SHORTCUT_SETTINGS -> callbacks.onOpenSettings()
            }
        }
    }

    private var theme: KeyboardTheme = KeyboardTheme.DARK
    private val toolbar = LinearLayout(context)
    private val keyboardWrapper = FrameLayout(context)
    private val contentFrame = FrameLayout(context)
    private var clipboardPanel: View? = null
    private var emojiPanel: View? = null
    private var kaomojiPanel: View? = null
    private var clipboardHeader: View? = null
    private var adjustOverlay: MarginAdjustOverlay? = null

    private var emojiSearchOpen = false
    private var skinTone = 0
    private var emojiScrollView: ScrollView? = null
    private var skinTonePopup: android.widget.PopupWindow? = null
    private var emojiSearchResultsRow: View? = null
    private var emojiSearchQueryView: TextView? = null
    private var emojiSearchResultsList: LinearLayout? = null

    fun isEmojiSearchOpen(): Boolean = emojiSearchOpen

    // 클립보드 다중 선택 모드: 목적(삭제/고정)에 따라 헤더가 달라진다.
    private enum class ClipboardMode { NORMAL, DELETE, PIN }

    private var clipboardMode = ClipboardMode.NORMAL
    private val clipboardSelected = mutableSetOf<String>()

    private var toolbarEnabled = true
    private var oneHandedMode = dev.badalab.yeonfeel.settings.OneHandedMode.OFF
    private var oneHandControls: View? = null
    private var marginTopDp = 0
    private var marginBottomDp = 0
    private var marginSideDp = 0
    private var keyboardHeightDp = KeyboardSettings.HEIGHT_DEFAULT

    private lateinit var settingsButton: android.widget.ImageView
    private lateinit var layoutButton: android.widget.ImageView
    private lateinit var clipboardButton: android.widget.ImageView
    private lateinit var emojiButton: android.widget.ImageView
    private lateinit var editButton: android.widget.ImageView
    private lateinit var oneHandButton: android.widget.ImageView
    private val toolbarButtons = linkedMapOf<String, android.widget.ImageView>()
    private val toolbarItemIcons = linkedMapOf(
        "settings" to R.drawable.ic_toolbar_settings,
        "layout" to R.drawable.ic_toolbar_keyboard,
        "clipboard" to R.drawable.ic_toolbar_clipboard,
        "emoji" to R.drawable.ic_toolbar_emoji,
        "kaomoji" to R.drawable.ic_toolbar_kaomoji,
        "onehand" to R.drawable.ic_toolbar_onehand,
    )
    private var currentToolbarOrder = KeyboardSettings.TOOLBAR_ORDER_DEFAULT
    private val terminalRow = LinearLayout(context)
    private var terminalRowEnabled = false
    private var ctrlArmed = false
    private var altArmed = false
    private var ctrlButton: TextView? = null
    private var altButton: TextView? = null
    private var toolbarEditPanel: View? = null

    init {
        orientation = VERTICAL

        toolbar.orientation = HORIZONTAL
        toolbar.gravity = Gravity.CENTER_VERTICAL
        toolbar.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(44)).apply {
            bottomMargin = dp(6)
        }
        settingsButton = toolbarIcon(
            R.drawable.ic_toolbar_settings,
            context.getString(R.string.toolbar_settings_desc),
        ) { callbacks.onOpenSettings() }
        layoutButton = toolbarIcon(
            R.drawable.ic_toolbar_keyboard,
            context.getString(R.string.toolbar_layout_desc),
        ) { toggleAdjustMode() }
        clipboardButton = toolbarIcon(
            R.drawable.ic_toolbar_clipboard,
            context.getString(R.string.toolbar_clipboard_desc),
        ) { toggleClipboardPanel() }
        emojiButton = toolbarIcon(
            R.drawable.ic_toolbar_emoji,
            context.getString(R.string.toolbar_emoji_desc),
        ) { toggleEmojiPanel() }
        toolbarButtons["settings"] = settingsButton
        toolbarButtons["layout"] = layoutButton
        toolbarButtons["clipboard"] = clipboardButton
        toolbarButtons["emoji"] = emojiButton
        toolbarButtons["kaomoji"] = toolbarIcon(
            R.drawable.ic_toolbar_kaomoji,
            context.getString(R.string.toolbar_kaomoji_desc),
        ) { toggleKaomojiPanel() }
        oneHandButton = toolbarIcon(
            R.drawable.ic_toolbar_onehand,
            context.getString(R.string.toolbar_onehand_desc),
        ) { callbacks.onOneHandedCycle() }
        toolbarButtons["onehand"] = oneHandButton
        editButton = toolbarIcon(
            R.drawable.ic_toolbar_chevron_down,
            context.getString(R.string.toolbar_edit_desc),
        ) { toggleToolbarEditPanel() }
        setupToolbarReorder()
        applyToolbarOrder(KeyboardSettings.TOOLBAR_ORDER_DEFAULT)
        addView(toolbar)
        buildTerminalRow()
        addView(terminalRow, LayoutParams(LayoutParams.MATCH_PARENT, dp(36)).apply { bottomMargin = dp(4) })

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

    /** 터미널 도구 줄: esc·tab·ctrl·alt·화살표. ctrl/alt는 원샷 스티키. */
    private fun buildTerminalRow() {
        terminalRow.orientation = HORIZONTAL
        terminalRow.gravity = Gravity.CENTER_VERTICAL
        fun termButton(label: String, onClick: () -> Unit): TextView =
            TextView(context).apply {
                text = label
                gravity = Gravity.CENTER
                // 시스템 폰트 확대에도 36dp 행 안에서 한 줄을 유지하도록 dp 단위로 고정한다.
                setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f)
                maxLines = 1
                setOnClickListener { onClick() }
                addIconPressEffect(this)
                terminalRow.addView(this, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
            }
        termButton("esc") { callbacks.onTerminalKey(android.view.KeyEvent.KEYCODE_ESCAPE) }
        termButton("tab") { callbacks.onTerminalKey(android.view.KeyEvent.KEYCODE_TAB) }
        ctrlButton = termButton("ctrl") {
            ctrlArmed = !ctrlArmed
            refreshTerminalColors()
        }
        altButton = termButton("alt") {
            altArmed = !altArmed
            refreshTerminalColors()
        }
        termButton("←") { callbacks.onTerminalKey(android.view.KeyEvent.KEYCODE_DPAD_LEFT) }
        termButton("↓") { callbacks.onTerminalKey(android.view.KeyEvent.KEYCODE_DPAD_DOWN) }
        termButton("↑") { callbacks.onTerminalKey(android.view.KeyEvent.KEYCODE_DPAD_UP) }
        termButton("→") { callbacks.onTerminalKey(android.view.KeyEvent.KEYCODE_DPAD_RIGHT) }
    }

    private fun refreshTerminalColors() {
        for (i in 0 until terminalRow.childCount) {
            (terminalRow.getChildAt(i) as? TextView)?.setTextColor(theme.subText)
        }
        val accent = 0xFF3D8BFF.toInt()
        if (ctrlArmed) ctrlButton?.setTextColor(accent)
        if (altArmed) altButton?.setTextColor(accent)
    }

    /** 무장된 ctrl/alt 메타 마스크를 소비(해제)하고 돌려준다. */
    fun consumeModifierMeta(): Int {
        var meta = 0
        if (ctrlArmed) {
            meta = meta or android.view.KeyEvent.META_CTRL_ON or android.view.KeyEvent.META_CTRL_LEFT_ON
        }
        if (altArmed) {
            meta = meta or android.view.KeyEvent.META_ALT_ON or android.view.KeyEvent.META_ALT_LEFT_ON
        }
        if (meta != 0) {
            ctrlArmed = false
            altArmed = false
            refreshTerminalColors()
        }
        return meta
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
        theme = KeyboardTheme.of(dark, settings.highContrast, settings.highContrastStyle)
        keyboardView.theme = theme
        keyboardView.showNumberRow = settings.showNumberRow
        keyboardView.koreanLayout = settings.koreanLayout
        keyboardView.englishLayout = settings.englishLayout
        keyboardView.shiftNumberRowSymbols = settings.shiftNumberRowSymbols
        // 고대비 모드는 옵션에 따라 테마의 키캡 배경 설정을 오버라이드해 항상 표시한다.
        keyboardView.showKeyBackground = settings.showKeyBackground ||
            (settings.highContrast && settings.highContrastForceKeycap)
        keyboardView.keyPreviewEnabled = settings.keyPreviewEnabled
        keyboardView.deleteRepeatIntervalMs = settings.backspaceSpeed.intervalMs
        keyboardView.longPressDelayMs = settings.longPressDelayMs.toLong()
        keyboardView.fontScale = settings.keyFontSize.scale
        val landscape = resources.configuration.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE
        // 세로 분할은 폭이 넓은 화면(내부 화면 등)에서만 적용한다. 폴드 커버 화면 세로는
        // smallestScreenWidthDp가 600 미만이라 저장값과 무관하게 단일 키보드로 남는다.
        // 이 폭 기준이 곧 내부/외부 화면을 구분하므로 별도 상태값 없이 분리가 성립한다.
        val canSplitPortrait = resources.configuration.smallestScreenWidthDp >=
            dev.badalab.yeonfeel.settings.KeyboardSettings.LARGE_SCREEN_SW_DP
        keyboardView.splitEnabled =
            if (landscape) settings.splitLandscape else (canSplitPortrait && settings.splitPortrait)
        keyboardView.splitGapRatio = settings.splitGapPercent / 100f
        keyboardView.touchModelEnabled =
            settings.touchCorrectionEnabled && settings.touchCorrectionBasic
        oneHandedMode = settings.oneHandedMode
        skinTone = settings.skinTone
        KeyboardLayouts.lastSymbol3x4 = settings.rememberedSymbol.first()
        keyboardView.soundEnabled = settings.soundEnabled
        keyboardView.hapticEnabled = settings.hapticEnabled
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
        terminalRowEnabled = settings.terminalRowEnabled
        terminalRow.setBackgroundColor(theme.background)
        ctrlArmed = false
        altArmed = false
        refreshTerminalColors()
        toolbarButtons.values.forEach {
            it.imageTintList = android.content.res.ColorStateList.valueOf(theme.subText)
        }
        editButton.imageTintList = android.content.res.ColorStateList.valueOf(theme.subText)
        applyToolbarOrder(settings.toolbarOrder)
        showKeyboard()
        updateOneHandControls()
    }

    /** 한 손 모드의 빈 영역에 위치 전환·해제 버튼을 띄운다. */
    private fun updateOneHandControls() {
        oneHandControls?.let { contentFrame.removeView(it) }
        oneHandControls = null
        val mode = oneHandedMode
        if (mode == dev.badalab.yeonfeel.settings.OneHandedMode.OFF) return

        val stripDp = (resources.configuration.screenWidthDp * 0.25f).toInt()
        val emptyOnLeft = mode == dev.badalab.yeonfeel.settings.OneHandedMode.RIGHT
        fun controlIcon(res: Int, desc: String, rotate: Float, onClick: () -> Unit) =
            android.widget.ImageView(context).apply {
                setImageResource(res)
                contentDescription = desc
                rotation = rotate
                imageTintList = android.content.res.ColorStateList.valueOf(theme.subText)
                setPadding(dp(10), dp(10), dp(10), dp(10))
                setOnClickListener { onClick() }
                addIconPressEffect(this)
            }

        // 전환 화살표는 세로 중앙, 해제 버튼은 하단 모서리로 떨어뜨린다.
        val column = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        column.addView(View(context), LayoutParams(0, 0, 1f))
        column.addView(
            controlIcon(
                R.drawable.ic_toolbar_chevron_down,
                context.getString(R.string.onehand_switch_desc),
                if (emptyOnLeft) 90f else -90f,
            ) {
                callbacks.onOneHandedModeChanged(
                    if (emptyOnLeft) {
                        dev.badalab.yeonfeel.settings.OneHandedMode.LEFT
                    } else {
                        dev.badalab.yeonfeel.settings.OneHandedMode.RIGHT
                    },
                )
            },
            LayoutParams(dp(44), dp(44)),
        )
        column.addView(View(context), LayoutParams(0, 0, 1f))
        column.addView(
            controlIcon(R.drawable.ic_icon_expand, context.getString(R.string.onehand_exit_desc), 0f) {
                callbacks.onOneHandedModeChanged(dev.badalab.yeonfeel.settings.OneHandedMode.OFF)
            },
            LayoutParams(dp(44), dp(44)).apply { bottomMargin = dp(10) },
        )

        oneHandControls = column
        // 키보드 바로 위 레이어 — 이후 열리는 패널(클립보드·이모지)이 자연스럽게 덮는다.
        contentFrame.addView(
            column,
            contentFrame.indexOfChild(keyboardWrapper) + 1,
            FrameLayout.LayoutParams(
                dp(stripDp),
                FrameLayout.LayoutParams.MATCH_PARENT,
                if (emptyOnLeft) Gravity.START else Gravity.END,
            ).apply {
                topMargin = dp(marginTopDp)
                bottomMargin = dp(marginBottomDp) + navInsetPx
            },
        )
    }

    /** 툴바 아이콘 순서·표시 적용. 목록에 없는 항목은 숨겨지고 편집 패널에서 다시 추가한다. */
    private fun applyToolbarOrder(orderCsv: String) {
        currentToolbarOrder = orderCsv
        toolbar.removeAllViews()
        orderCsv.split(',').map { it.trim() }.forEach { id ->
            toolbarButtons[id]?.let { toolbar.addView(it) }
        }
        // 아이콘·편집 버튼 모두 가중치 셀이라 균등 분배된다 (스페이서 불필요).
        toolbar.addView(editButton)
    }

    private fun setupToolbarReorder() {
        toolbarButtons.forEach { (id, view) ->
            view.tag = id
            view.setOnLongClickListener { v ->
                val clip = android.content.ClipData.newPlainText("toolbar", id)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    v.startDragAndDrop(clip, View.DragShadowBuilder(v), v, 0)
                } else {
                    @Suppress("DEPRECATION")
                    v.startDrag(clip, View.DragShadowBuilder(v), v, 0)
                }
                true
            }
        }
        toolbar.setOnDragListener { _, event ->
            when (event.action) {
                android.view.DragEvent.ACTION_DRAG_STARTED -> {
                    // 드래그 중엔 원래 칸의 아이콘을 숨기고, 슬롯 그리드를 연하게 보여준다.
                    (event.localState as? android.widget.ImageView)?.imageAlpha = 0
                    toolbarButtons.values.forEach { button ->
                        button.background = android.graphics.drawable.GradientDrawable().apply {
                            cornerRadius = dp(8).toFloat()
                            setStroke(dp(1), theme.subText and 0x50FFFFFF.toInt())
                        }
                    }
                    true
                }
                android.view.DragEvent.ACTION_DROP -> {
                    val dragged = event.localState as? View ?: return@setOnDragListener false
                    toolbar.removeView(dragged)
                    // 스페이서·편집 버튼(태그 없음)은 건너뛰고 아이콘 사이 위치만 센다.
                    var index = 0
                    for (i in 0 until toolbar.childCount) {
                        val child = toolbar.getChildAt(i)
                        if (child.tag == null) continue
                        if (event.x > child.x + child.width / 2f) index++
                    }
                    toolbar.addView(dragged, index)
                    val order = (0 until toolbar.childCount)
                        .mapNotNull { toolbar.getChildAt(it).tag as? String }
                        .joinToString(",")
                    callbacks.onToolbarOrderChanged(order)
                    applyToolbarOrder(order)
                    true
                }
                android.view.DragEvent.ACTION_DRAG_ENDED -> {
                    (event.localState as? android.widget.ImageView)?.imageAlpha = 255
                    toolbarButtons.values.forEach { it.background = null }
                    true
                }
                else -> true
            }
        }
    }

    fun showKeyboard() {
        // 키보드가 다시 뜰 때 하단 바 높이를 재확인한다(회전·기기별 지연 대비).
        androidx.core.view.ViewCompat.getRootWindowInsets(this)?.let { updateNavInset(it) }
        clipboardPanel?.let { contentFrame.removeView(it) }
        clipboardPanel = null
        emojiPanel?.let { contentFrame.removeView(it) }
        emojiPanel = null
        kaomojiPanel?.let { contentFrame.removeView(it) }
        kaomojiPanel = null
        clipboardHeader?.let { removeView(it) }
        clipboardHeader = null
        toolbar.visibility = if (toolbarEnabled) VISIBLE else GONE
        terminalRow.visibility = if (terminalRowEnabled) VISIBLE else GONE
        // 도구 줄이 켜져 있으면 툴바와 사이를 붙인다 (여백은 도구 줄 아래로).
        (toolbar.layoutParams as MarginLayoutParams).bottomMargin =
            if (terminalRowEnabled && toolbarEnabled) 0 else dp(6)
        adjustOverlay?.let { contentFrame.removeView(it) }
        adjustOverlay = null
        skinTonePopup?.dismiss()
        skinTonePopup = null
        toolbarEditPanel?.let { contentFrame.removeView(it) }
        toolbarEditPanel = null
        editButton.rotation = 0f
        clipboardMode = ClipboardMode.NORMAL
        clipboardSelected.clear()
        emojiSearchResultsRow?.let { removeView(it) }
        emojiSearchResultsRow = null
        emojiSearchQueryView = null
        emojiSearchResultsList = null
        if (emojiSearchOpen) {
            emojiSearchOpen = false
            callbacks.onEmojiSearchStateChanged(false)
        }
        keyboardView.visibility = VISIBLE
    }

    private var navInsetPx = 0

    private fun isLandscape(): Boolean =
        resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    /** 가로는 화면 대부분을 덮지 않도록 키 높이를 화면 40%로 제한한다. */
    private fun effectiveHeightDp(heightDp: Int): Int =
        if (isLandscape()) {
            minOf(heightDp, (resources.configuration.screenHeightDp * 2 / 5).coerceAtLeast(120))
        } else {
            heightDp
        }

    /** 가로는 세로 공간이 귀해 제스처 바 위 여백을 최소화한다 (인셋은 유지). */
    private fun effectiveBottomDp(bottomDp: Int): Int =
        if (isLandscape()) minOf(bottomDp, 10) else bottomDp

    private fun applyMargins(topDp: Int, bottomDp: Int, sideDp: Int, heightDp: Int) {
        marginTopDp = topDp
        marginBottomDp = bottomDp
        marginSideDp = sideDp
        keyboardHeightDp = heightDp
        val config = resources.configuration
        val effectiveHeightDp = effectiveHeightDp(heightDp)
        val effectiveBottomDp = effectiveBottomDp(bottomDp)
        keyboardView.heightDp = effectiveHeightDp
        // 한 손 모드: 반대쪽에 여백을 몰아 키 영역을 한쪽으로 붙인다.
        val oneHandDp = if (oneHandedMode == dev.badalab.yeonfeel.settings.OneHandedMode.OFF) {
            0
        } else {
            (config.screenWidthDp * 0.25f).toInt()
        }
        val leftExtra = if (oneHandedMode == dev.badalab.yeonfeel.settings.OneHandedMode.RIGHT) oneHandDp else 0
        val rightExtra = if (oneHandedMode == dev.badalab.yeonfeel.settings.OneHandedMode.LEFT) oneHandDp else 0
        // 저장된 좌우 여백이 화면 폭 대비 과도하면(기기 변경·화면 확대) 1/4로 제한한다.
        @Suppress("NAME_SHADOWING")
        val sideDp = minOf(sideDp, config.screenWidthDp / 4)
        // 하단에는 시스템 내비게이션 바 인셋을 더해 제스처 영역과 겹치지 않게 한다.
        keyboardWrapper.setPadding(
            dp(sideDp + leftExtra),
            dp(topDp),
            dp(sideDp + rightExtra),
            dp(effectiveBottomDp) + navInsetPx,
        )
        // 콘텐츠 영역 높이를 키보드 높이로 고정 — 패널(클립보드·이모지) 내용이
        // 길어도 창이 위로 자라지 않고 패널 안에서 스크롤된다.
        contentFrame.layoutParams = (contentFrame.layoutParams as LayoutParams).apply {
            height = dp(effectiveHeightDp + topDp + effectiveBottomDp) + navInsetPx
        }
    }

    /**
     * 좌우 가장자리 키를 빠르게 칠 때 백 스와이프로 오인되어 키보드가 닫히는 것을 막는다.
     * 시스템이 가장자리당 최대 200dp 높이까지만 제외를 허용하므로 아래쪽부터 적용된다.
     */
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val strip = dp(32)
            systemGestureExclusionRects = listOf(
                android.graphics.Rect(0, 0, strip, h),
                android.graphics.Rect(w - strip, 0, w, h),
            )
        }
    }

    /**
     * 하단 시스템 바(내비게이션 바·제스처 바, 삼성은 IME 전용 띠) 높이를 비운다.
     * targetSdk 35+ 에서 IME 창은 edge-to-edge 로 그려지므로 이 인셋을 앱이 직접
     * 적용해야 겹치지 않는다. OEM 편차를 줄이려 AndroidX 리스너로 받고,
     * navigationBars 와 tappableElement 중 큰 값을 쓴다.
     */
    private fun updateNavInset(insets: androidx.core.view.WindowInsetsCompat) {
        val nav = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
        val tappable = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.tappableElement())
        val newInset = maxOf(nav.bottom, tappable.bottom)
        if (newInset != navInsetPx) {
            navInsetPx = newInset
            applyMargins(marginTopDp, marginBottomDp, marginSideDp, keyboardHeightDp)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            updateNavInset(insets)
            insets
        }
        androidx.core.view.ViewCompat.requestApplyInsets(this)
        // 리스너가 즉시 불리지 않는 기기를 위해 현재 인셋을 직접 한 번 읽는다(폴백).
        androidx.core.view.ViewCompat.getRootWindowInsets(this)?.let { updateNavInset(it) }
    }

    /** 설정 화면에서 요청된 여백 조정 진입 (열려 있지 않을 때만). */
    fun startAdjustMode() {
        if (adjustOverlay == null) toggleAdjustMode()
    }

    private fun toggleAdjustMode() {
        val wasAdjusting = adjustOverlay != null
        showKeyboard()
        if (wasAdjusting) return

        // 저장값은 가로·세로가 공유하므로 커밋 시 원본 값을 그대로 넘긴다.
        // 오버레이는 자체 onMeasure에서 화면 높이로 클램프해 off-screen 추적을 막는다.
        val overlay = MarginAdjustOverlay(
            context, marginTopDp, marginBottomDp, marginSideDp, keyboardHeightDp, theme,
            splitActive = keyboardView.splitActive,
            splitGapPercent = (keyboardView.splitGapRatio * 100).toInt(),
            listener = object : MarginAdjustOverlay.Listener {
                override fun onMarginsChanged(topDp: Int, bottomDp: Int, sideDp: Int, heightDp: Int) {
                    applyMargins(topDp, bottomDp, sideDp, heightDp)
                }

                override fun onCommit(topDp: Int, bottomDp: Int, sideDp: Int, heightDp: Int) {
                    callbacks.onMarginsCommitted(topDp, bottomDp, sideDp, heightDp)
                }

                override fun onSplitGapChanged(percent: Int) {
                    keyboardView.splitGapRatio = percent / 100f
                }

                override fun onSplitGapCommitted(percent: Int) {
                    callbacks.onSplitGapCommitted(percent)
                }

                override fun onDone() = showKeyboard()
            },
        )
        adjustOverlay = overlay
        contentFrame.addView(
            overlay,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
        )
        fadeIn(overlay)
    }

    /** 툴바 편집: 키보드 영역에 전체 항목을 펼쳐 놓고 눌러서 툴바에 추가/제거한다. */
    private fun toggleToolbarEditPanel() {
        val wasOpen = toolbarEditPanel != null
        showKeyboard()
        if (wasOpen) {
            fadeIn(keyboardView)
            return
        }

        val panel = buildToolbarEditPanel()
        toolbarEditPanel = panel
        keyboardView.visibility = INVISIBLE
        editButton.rotation = 180f
        contentFrame.addView(
            panel,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
        )
        fadeIn(panel)
    }

    private fun buildToolbarEditPanel(): View {
        val active = currentToolbarOrder.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val panel = LinearLayout(context).apply {
            orientation = VERTICAL
            setBackgroundColor(theme.background)
            setPadding(dp(16), dp(10), dp(16), dp(marginBottomDp) + navBarInset())
        }
        panel.addView(TextView(context).apply {
            text = context.getString(R.string.toolbar_edit_hint)
            setTextColor(theme.subText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(dp(4), 0, 0, dp(12))
        })
        // 4열 그리드를 유지하며 줄바꿈하고, 위에서부터 채운다.
        val grid = LinearLayout(context).apply { orientation = VERTICAL }
        var gridRow = LinearLayout(context).apply { orientation = HORIZONTAL }
        grid.addView(gridRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        toolbarItemIcons.forEach { (id, iconRes) ->
            if (gridRow.childCount == 4) {
                gridRow = LinearLayout(context).apply { orientation = HORIZONTAL }
                grid.addView(
                    gridRow,
                    LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                        topMargin = dp(8)
                    },
                )
            }
            lateinit var applyCellState: (Boolean) -> Unit
            val cell = LinearLayout(context).apply {
                orientation = VERTICAL
                gravity = Gravity.CENTER
                setPadding(0, dp(12), 0, dp(12))
                setOnClickListener {
                    // 누른 셀과 툴바만 갱신한다 — 패널 전체를 다시 그리지 않는다.
                    val current = currentToolbarOrder.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                    val nowIn = id in current
                    val orderCsv = (if (nowIn) current - id else current + id).joinToString(",")
                    callbacks.onToolbarOrderChanged(orderCsv)
                    applyToolbarOrder(orderCsv)
                    applyCellState(!nowIn)
                }
            }
            applyCellState = { inBar ->
                cell.background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat()
                    if (inBar) {
                        setColor(theme.specialKey)
                    } else {
                        setStroke(dp(1), theme.subText and 0x50FFFFFF.toInt())
                    }
                }
                cell.animate().alpha(if (inBar) 1f else 0.55f).setDuration(140).start()
            }
            applyCellState(id in active)
            cell.addView(
                android.widget.ImageView(context).apply {
                    setImageResource(iconRes)
                    imageTintList = android.content.res.ColorStateList.valueOf(theme.subText)
                },
                LayoutParams(dp(26), dp(26)),
            )
            cell.addView(TextView(context).apply {
                text = toolbarButtons[id]?.contentDescription
                setTextColor(theme.subText)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(dp(2), dp(6), dp(2), 0)
            })
            // 고정 높이로 모든 셀을 동일하게 맞춘다 (라벨은 한 줄 제한).
            gridRow.addView(
                cell,
                LayoutParams(0, dp(72), 1f).apply {
                    marginStart = dp(4)
                    marginEnd = dp(4)
                },
            )
        }
        // 마지막 줄이 4칸 미만이면 빈 자리로 채워 셀 폭을 유지한다.
        repeat(4 - gridRow.childCount) {
            gridRow.addView(
                View(context),
                LayoutParams(0, dp(72), 1f).apply {
                    marginStart = dp(4)
                    marginEnd = dp(4)
                },
            )
        }
        // 가로 모드 등 낮은 키보드 높이에서 둘째 줄이 잘리지 않도록 스크롤로 감싼다.
        panel.addView(
            android.widget.ScrollView(context).apply {
                isVerticalScrollBarEnabled = false
                addView(grid)
            },
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
        return panel
    }

    private fun toggleKaomojiPanel() {
        val wasOpen = kaomojiPanel != null
        showKeyboard()
        if (wasOpen) {
            fadeIn(keyboardView)
            return
        }

        val content = LinearLayout(context).apply {
            orientation = VERTICAL
            // 하단 여백은 패널이 아니라 스크롤 내용의 패딩으로 — 항목이 그 영역까지 보인다.
            setPadding(dp(8), dp(4), dp(8), dp(8) + dp(marginBottomDp) + navBarInset())
        }
        EmojiData.kaomojiGroupList().forEach { (title, items) ->
            content.addView(TextView(context).apply {
                text = title
                setTextColor(theme.subText)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(dp(6), dp(8), 0, dp(2))
            })
            items.chunked(3).forEach { rowItems ->
                val row = LinearLayout(context).apply { orientation = HORIZONTAL }
                rowItems.forEach { item ->
                    row.addView(
                        TextView(context).apply {
                            text = item
                            gravity = Gravity.CENTER
                            maxLines = 1
                            setTextColor(theme.text)
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                            setOnClickListener { callbacks.onEmoji(item) }
                        },
                        LayoutParams(0, dp(46), 1f),
                    )
                }
                repeat(3 - rowItems.size) {
                    row.addView(View(context), LayoutParams(0, dp(46), 1f))
                }
                content.addView(row)
            }
        }
        val panel = LinearLayout(context).apply {
            orientation = VERTICAL
            setBackgroundColor(theme.background)
            addView(
                ScrollView(context).apply {
                    isVerticalScrollBarEnabled = false
                    addView(content)
                },
                LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f),
            )
        }
        kaomojiPanel = panel
        keyboardView.visibility = INVISIBLE
        contentFrame.addView(
            panel,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
        )
        attachHeader(buildPanelTitleHeader(R.string.toolbar_kaomoji_desc))
        fadeIn(clipboardHeader, panel)
    }

    /** 키보드 복귀·제목·백스페이스로 구성된 공용 패널 헤더. */
    private fun buildPanelTitleHeader(titleRes: Int): View {
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(theme.specialKey)
            setPadding(dp(8), 0, dp(8), 0)
        }
        header.addView(
            headerImage(R.drawable.ic_toolbar_keyboard, context.getString(R.string.clipboard_back_to_keyboard)) {
                showKeyboard()
                fadeIn(keyboardView)
            },
        )
        header.addView(TextView(context).apply {
            text = context.getString(titleRes)
            setTextColor(theme.subText)
            setTypeface(null, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(dp(6), 0, 0, 0)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        })
        header.addView(
            headerImage(R.drawable.ic_icon_backspace, context.getString(R.string.clipboard_delete)) {
                callbacks.onKey(Key(KeyType.DELETE, "⌫"))
            },
        )
        return header
    }

    private fun toggleEmojiPanel() {
        val wasOpen = emojiPanel != null
        showKeyboard()
        if (wasOpen) {
            fadeIn(keyboardView)
            return
        }

        val panel = buildEmojiPanel()
        emojiPanel = panel
        keyboardView.visibility = INVISIBLE
        contentFrame.addView(
            panel,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
        )
        // 헤더와 탭 스트립을 여백 없이 붙이되, 없앤 6dp를 헤더 높이에 더해
        // 키보드 모드와 전체 높이를 똑같이 유지한다 (전환 시 높이 흔들림 방지).
        attachHeader(buildEmojiHeader(), gapBelow = false, heightDp = 50)
        fadeIn(clipboardHeader, panel)
    }

    private fun buildEmojiHeader(): View {
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(theme.specialKey)
            // 늘어난 헤더 높이(50dp) 안에서 내용은 위쪽 44dp에 중앙 정렬되게 한다.
            setPadding(dp(8), 0, dp(8), dp(6))
        }
        header.addView(
            headerImage(R.drawable.ic_toolbar_keyboard, context.getString(R.string.clipboard_back_to_keyboard)) {
                showKeyboard()
                fadeIn(keyboardView)
            },
        )
        header.addView(TextView(context).apply {
            text = context.getString(R.string.toolbar_emoji_desc)
            setTextColor(theme.subText)
            setTypeface(null, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(dp(6), 0, 0, 0)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        })
        header.addView(
            headerImage(R.drawable.ic_icon_backspace, context.getString(R.string.clipboard_delete)) {
                callbacks.onKey(Key(KeyType.DELETE, "⌫"))
            },
        )
        return header
    }

    /**
     * 이모지 패널: 상단에 검색+카테고리 탭 바, 아래에 세로 스크롤 8열 그리드.
     * 탭을 누르면 해당 섹션으로 스크롤하고, 스크롤에 따라 활성 탭이 바뀐다.
     */
    private fun buildEmojiPanel(): View {
        val tabBarHeightDp = 40
        val content = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(8), dp(2), dp(8), dp(8))
        }
        val blocks = mutableListOf<View>()
        val allCategories = EmojiData.categories()
        allCategories.forEach { category ->
            val block = LinearLayout(context).apply { orientation = VERTICAL }
            block.addView(TextView(context).apply {
                text = category.title
                setTextColor(theme.subText)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(dp(6), dp(8), 0, dp(2))
            })
            val columns = if (category.wide) 3 else 8
            val textSp = if (category.wide) 14f else 24f
            category.emojis.chunked(columns).forEach { rowEmojis ->
                val row = LinearLayout(context).apply { orientation = HORIZONTAL }
                rowEmojis.forEach { emoji ->
                    val shown = EmojiData.applySkinTone(emoji, skinTone)
                    row.addView(
                        TextView(context).apply {
                            text = shown
                            gravity = Gravity.CENTER
                            maxLines = 1
                            setTextColor(theme.text)
                            setTextSize(TypedValue.COMPLEX_UNIT_DIP, textSp)
                            setOnClickListener { callbacks.onEmoji(shown) }
                            if (EmojiData.supportsSkinTone(emoji)) {
                                setOnLongClickListener {
                                    showSkinTonePopup(this, emoji)
                                    true
                                }
                            }
                        },
                        LayoutParams(0, dp(44), 1f),
                    )
                }
                repeat(columns - rowEmojis.size) {
                    row.addView(View(context), LayoutParams(0, dp(44), 1f))
                }
                block.addView(row)
            }
            content.addView(block)
            blocks.add(block)
        }
        val emojiScroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            addView(content)
        }
        emojiScrollView = emojiScroll

        val tabs = mutableListOf<TextView>()
        fun highlightTab(active: Int) {
            tabs.forEachIndexed { index, tab ->
                tab.background = if (index == active) {
                    android.graphics.drawable.InsetDrawable(
                        android.graphics.drawable.GradientDrawable().apply {
                            shape = android.graphics.drawable.GradientDrawable.OVAL
                            setColor(theme.key)
                        },
                        dp(5),
                    )
                } else {
                    null
                }
            }
        }

        val tabRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, dp(8), 0)
        }
        allCategories.forEachIndexed { index, category ->
            val tab = TextView(context).apply {
                text = category.tabLabel
                gravity = Gravity.CENTER
                maxLines = 1
                setTextColor(theme.text)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, if (category.wide) 12f else 20f)
                contentDescription = category.title
                layoutParams = LayoutParams(dp(38), dp(38)).apply {
                    marginStart = dp(4)
                    marginEnd = dp(4)
                }
                setOnClickListener {
                    emojiScroll.smoothScrollTo(0, blocks[index].top)
                    highlightTab(index)
                }
            }
            tabs.add(tab)
            tabRow.addView(tab)
        }
        highlightTab(0)
        emojiScroll.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            var active = 0
            blocks.forEachIndexed { index, block ->
                if (block.top <= scrollY + dp(48)) active = index
            }
            highlightTab(active)
        }

        tabRow.addView(
            headerImage(R.drawable.ic_icon_search, context.getString(R.string.emoji_search_hint)) {
                openEmojiSearch()
            },
            0,
        )

        val panel = LinearLayout(context).apply {
            orientation = VERTICAL
            setBackgroundColor(theme.background)
            // 시스템 하단바·키보드 하단 여백만큼 그리드가 일찍 끝나도록 띄운다.
            setPadding(0, 0, 0, dp(marginBottomDp) + navBarInset())
        }
        panel.addView(
            android.widget.HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                setBackgroundColor(blendColor(theme.specialKey, theme.background, 0.5f))
                addView(tabRow)
            },
            LayoutParams(LayoutParams.MATCH_PARENT, dp(tabBarHeightDp)),
        )
        panel.addView(emojiScroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        return panel
    }

    private fun navBarInset(): Int = navInsetPx

    /** 스킨톤 지원 이모지 롱프레스: 기본+5톤 선택 팝업. 선택한 톤은 기본값으로 저장된다. */
    private fun showSkinTonePopup(anchor: View, baseEmoji: String) {
        skinTonePopup?.dismiss()
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(theme.key)
                setStroke(dp(1), theme.subText and 0x50FFFFFF.toInt())
            }
            setPadding(dp(6), dp(4), dp(6), dp(4))
        }
        // 화면이 252dp보다 좁으면(작은 기기·확대) 셀 폭을 줄여 팝업이 화면 안에 들어오게 한다.
        val cellW = minOf(dp(40), (width - dp(20)) / 6)
        (0..5).forEach { tone ->
            val toned = EmojiData.applySkinTone(baseEmoji, tone)
            row.addView(
                TextView(context).apply {
                    text = toned
                    gravity = Gravity.CENTER
                    setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24f)
                    setOnClickListener {
                        callbacks.onEmoji(toned)
                        if (skinTone != tone) {
                            skinTone = tone
                            callbacks.onSkinToneChanged(tone)
                            refreshEmojiPanel()
                        }
                        skinTonePopup?.dismiss()
                        skinTonePopup = null
                    }
                },
                LayoutParams(cellW, dp(46)),
            )
        }
        val popupWidth = cellW * 6 + dp(12)
        val popup = android.widget.PopupWindow(row, popupWidth, dp(54), false).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(0x00000000))
            isClippingEnabled = false
        }
        val location = IntArray(2)
        anchor.getLocationInWindow(location)
        val x = (location[0] + anchor.width / 2 - popupWidth / 2)
            .coerceIn(dp(4), maxOf(dp(4), width - popupWidth - dp(4)))
        popup.showAtLocation(this, Gravity.NO_GRAVITY, x, location[1] - dp(58))
        skinTonePopup = popup
    }

    /** 스킨톤 변경 등으로 이모지 패널을 스크롤 위치를 유지한 채 다시 그린다. */
    private fun refreshEmojiPanel() {
        val open = emojiPanel ?: return
        val scrollY = emojiScrollView?.scrollY ?: 0
        contentFrame.removeView(open)
        val rebuilt = buildEmojiPanel()
        emojiPanel = rebuilt
        contentFrame.addView(
            rebuilt,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
        )
        emojiScrollView?.post { emojiScrollView?.scrollTo(0, scrollY) }
    }

    /**
     * 이모지 검색 모드: 이모지 헤더는 그대로 두고 탭 스트립 자리의 분류가
     * 검색 칸으로 바뀐다. 그 아래 결과 스트립 + 일반 키보드로 입력한다.
     */
    private fun openEmojiSearch() {
        showKeyboard()
        emojiSearchOpen = true
        attachHeader(buildEmojiSearchHeader(), gapBelow = true, heightDp = 40)
        val results = buildEmojiSearchResultsRow()
        emojiSearchResultsRow = results
        addView(results, 1, LayoutParams(LayoutParams.MATCH_PARENT, dp(46)).apply { bottomMargin = dp(4) })
        fadeIn(clipboardHeader, results)
        callbacks.onEmojiSearchStateChanged(true)
        updateEmojiSearch("")
    }

    private fun fadeIn(vararg views: View?) {
        views.filterNotNull().forEach { v ->
            v.alpha = 0f
            v.animate().alpha(1f).setDuration(180).start()
        }
    }

    fun updateEmojiSearch(text: String) {
        val queryView = emojiSearchQueryView ?: return
        if (text.isEmpty()) {
            queryView.text = context.getString(R.string.emoji_search_hint)
            queryView.setTextColor(theme.subText)
        } else {
            queryView.text = text
            queryView.setTextColor(theme.text)
        }
        val list = emojiSearchResultsList ?: return
        list.removeAllViews()
        val found = EmojiData.search(text)
        if (found.isEmpty()) {
            list.addView(
                TextView(context).apply {
                    this.text = context.getString(R.string.emoji_search_empty)
                    gravity = Gravity.CENTER
                    setTextColor(theme.subText)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                },
                LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
            )
            return
        }
        found.forEach { emoji ->
            val shown = EmojiData.applySkinTone(emoji, skinTone)
            list.addView(
                TextView(context).apply {
                    this.text = shown
                    gravity = Gravity.CENTER
                    setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24f)
                    setOnClickListener { callbacks.onEmoji(shown) }
                },
                LayoutParams(dp(46), LayoutParams.MATCH_PARENT),
            )
        }
    }

    private fun buildEmojiSearchHeader(): View {
        val searchRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(blendColor(theme.specialKey, theme.background, 0.5f))
            setPadding(dp(8), 0, dp(8), 0)
        }
        searchRow.addView(
            android.widget.ImageView(context).apply {
                setImageResource(R.drawable.ic_icon_search)
                imageTintList = android.content.res.ColorStateList.valueOf(theme.subText)
                layoutParams = LayoutParams(dp(22), dp(22)).apply {
                    marginStart = dp(8)
                    marginEnd = dp(4)
                }
            },
        )
        emojiSearchQueryView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(dp(8), 0, 0, 0)
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        searchRow.addView(emojiSearchQueryView)
        searchRow.addView(
            headerImage(R.drawable.ic_icon_close, context.getString(R.string.clipboard_cancel)) {
                showKeyboard()
                toggleEmojiPanel()
                fadeIn(clipboardHeader, emojiPanel)
            },
        )

        return searchRow
    }

    private fun buildEmojiSearchResultsRow(): View {
        emojiSearchResultsList = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setPadding(dp(8), 0, dp(8), 0)
        }
        return android.widget.HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            // 빈 상태 가이드 문구를 가로 중앙에 놓을 수 있게 뷰포트를 채운다.
            isFillViewport = true
            setBackgroundColor(theme.background)
            addView(
                emojiSearchResultsList,
                android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
        }
    }

    private fun headerImage(drawableRes: Int, description: String, onClick: () -> Unit) =
        toolbarIcon(drawableRes, description, onClick).apply {
            imageTintList = android.content.res.ColorStateList.valueOf(theme.subText)
            // 헤더는 제목이 가중치를 가져가야 하므로 아이콘은 고정 크기로 되돌린다.
            layoutParams = LayoutParams(dp(44), dp(36)).apply {
                marginStart = dp(4)
                marginEnd = dp(4)
            }
        }

    /** 두 색을 [t] 비율로 섞는다 (0=from, 1=to). 알파는 불투명 고정. */
    private fun blendColor(from: Int, to: Int, t: Float): Int {
        fun channel(shift: Int): Int {
            val a = (from shr shift) and 0xFF
            val b = (to shr shift) and 0xFF
            return (a + ((b - a) * t)).toInt() and 0xFF
        }
        return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private fun attachHeader(header: View, gapBelow: Boolean = true, heightDp: Int = 44) {
        toolbar.visibility = GONE
        terminalRow.visibility = GONE
        clipboardHeader?.let { removeView(it) }
        clipboardHeader = header
        addView(
            header,
            0,
            LayoutParams(LayoutParams.MATCH_PARENT, dp(heightDp)).apply {
                bottomMargin = if (gapBelow) dp(6) else 0
            },
        )
    }

    private fun toggleClipboardPanel() {
        val wasOpen = clipboardPanel != null
        showKeyboard()
        if (wasOpen) {
            fadeIn(keyboardView)
            return
        }

        val panel = buildClipboardPanel()
        clipboardPanel = panel
        // 키 영역을 INVISIBLE로 두어 패널이 같은 높이를 유지하게 한다.
        keyboardView.visibility = INVISIBLE
        contentFrame.addView(
            panel,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
        )
        attachClipboardHeader()
        fadeIn(clipboardHeader, panel)
    }

    /** 클립보드 헤더가 툴바 자리를 대체한다. */
    private fun attachClipboardHeader() {
        attachHeader(buildClipboardHeader())
    }

    private fun buildClipboardHeader(): View {
        val entries = callbacks.clipboardEntries()

        fun headerIcon(
            drawableRes: Int,
            description: String,
            onClick: () -> Unit,
        ) = toolbarIcon(drawableRes, description, onClick).apply {
            imageTintList = android.content.res.ColorStateList.valueOf(theme.subText)
            layoutParams = LayoutParams(dp(44), dp(36)).apply {
                marginStart = dp(4)
                marginEnd = dp(4)
            }
        }

        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(theme.specialKey)
            setPadding(dp(8), 0, dp(8), 0)
        }
        header.addView(
            headerIcon(R.drawable.ic_toolbar_keyboard, context.getString(R.string.clipboard_back_to_keyboard)) {
                showKeyboard()
                fadeIn(keyboardView)
            },
        )
        header.addView(TextView(context).apply {
            text = context.getString(R.string.toolbar_clipboard_desc)
            setTextColor(theme.subText)
            setTypeface(null, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(dp(6), 0, 0, 0)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        })
        when (clipboardMode) {
            ClipboardMode.NORMAL -> {
                header.addView(
                    headerIcon(R.drawable.ic_icon_pin, context.getString(R.string.clipboard_pin)) {
                        if (entries.isNotEmpty()) enterClipboardMode(ClipboardMode.PIN)
                    },
                )
                header.addView(
                    headerIcon(R.drawable.ic_icon_trash, context.getString(R.string.clipboard_delete)) {
                        if (entries.isNotEmpty()) enterClipboardMode(ClipboardMode.DELETE)
                    },
                )
            }
            ClipboardMode.DELETE -> {
                header.addView(
                    headerIcon(R.drawable.ic_icon_trash, context.getString(R.string.clipboard_delete)) {
                        if (clipboardSelected.isNotEmpty()) {
                            callbacks.onClipboardDelete(clipboardSelected.toList())
                        }
                        exitClipboardSelection()
                    },
                )
                header.addView(
                    headerIcon(R.drawable.ic_icon_close, context.getString(R.string.clipboard_cancel)) {
                        exitClipboardSelection()
                    },
                )
            }
            ClipboardMode.PIN -> {
                header.addView(
                    headerIcon(R.drawable.ic_icon_pin, context.getString(R.string.clipboard_pin)) {
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
                    headerIcon(R.drawable.ic_icon_close, context.getString(R.string.clipboard_cancel)) {
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
            row.addView(
                android.widget.ImageView(context).apply {
                    setImageResource(R.drawable.ic_icon_pin)
                    imageTintList = android.content.res.ColorStateList.valueOf(theme.subText)
                    layoutParams = LayoutParams(dp(18), dp(18)).apply { marginEnd = dp(6) }
                },
            )
        }
        row.addView(TextView(context).apply {
            text = entry.text
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
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

    private fun toolbarIcon(
        drawableRes: Int,
        description: String,
        onClick: () -> Unit,
    ): android.widget.ImageView = android.widget.ImageView(context).apply {
        setImageResource(drawableRes)
        contentDescription = description
        scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
        setPadding(dp(10), dp(6), dp(10), dp(6))
        setOnClickListener { onClick() }
        addIconPressEffect(this)
        // 고정 폭이면 아이콘 수·화면 폭(예: S24 Ultra ~384dp)에 따라 툴바가 넘쳐
        // 오른쪽 버튼이 잘린다. 가중치로 균등 분배해 항상 폭 안에 들어오게 한다.
        layoutParams = LayoutParams(0, dp(36), 1f)
    }

    /** 아이콘 버튼용 누름 효과: 아이콘 크기에 맞는 작은 원형 하이라이트가 즉시 나타난다. */
    @SuppressLint("ClickableViewAccessibility")
    private fun addIconPressEffect(view: View) {
        // 셀이 가로로 넓어도(가중치 분배 툴바) 타원으로 늘어나지 않도록,
        // 짧은 변 기준의 정원을 뷰 중앙에 그린다.
        val overlay = object : android.graphics.drawable.Drawable() {
            private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            private var alphaValue = 255

            override fun draw(canvas: android.graphics.Canvas) {
                val b = bounds
                val radius = minOf(b.width(), b.height()) / 2f - dp(1)
                if (radius <= 0f) return
                paint.color = 0x000000
                paint.alpha = 0x22 * alphaValue / 255
                canvas.drawCircle(b.exactCenterX(), b.exactCenterY(), radius, paint)
            }

            override fun setAlpha(alpha: Int) {
                alphaValue = alpha
                invalidateSelf()
            }

            override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) = Unit

            @Deprecated("Deprecated in Java")
            override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
        }
        var fadeOut: android.animation.ValueAnimator? = null
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    fadeOut?.cancel()
                    overlay.alpha = 255
                    view.foreground = overlay
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    fadeOut = android.animation.ValueAnimator.ofInt(255, 0).apply {
                        duration = 140
                        addUpdateListener {
                            overlay.alpha = it.animatedValue as Int
                            view.invalidate()
                        }
                        addListener(object : android.animation.AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: android.animation.Animator) {
                                view.foreground = null
                            }
                        })
                        start()
                    }
                }
            }
            false
        }
    }

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics,
        ).toInt()

}

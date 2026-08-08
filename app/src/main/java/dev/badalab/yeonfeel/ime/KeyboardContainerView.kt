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
    private var emojiPanel: View? = null
    private var clipboardHeader: View? = null
    private var adjustOverlay: MarginAdjustOverlay? = null

    // 이모지 검색 모드 상태
    private var emojiSearchOpen = false
    private var emojiSearchResultsRow: View? = null
    private var emojiSearchQueryView: TextView? = null
    private var emojiSearchResultsList: LinearLayout? = null

    fun isEmojiSearchOpen(): Boolean = emojiSearchOpen

    // 클립보드 다중 선택 모드: 목적(삭제/고정)에 따라 헤더가 달라진다.
    private enum class ClipboardMode { NORMAL, DELETE, PIN }

    private var clipboardMode = ClipboardMode.NORMAL
    private val clipboardSelected = mutableSetOf<String>()

    private var toolbarEnabled = true
    private var marginTopDp = 0
    private var marginBottomDp = 0
    private var marginSideDp = 0
    private var keyboardHeightDp = KeyboardSettings.HEIGHT_DEFAULT

    private lateinit var settingsButton: android.widget.ImageView
    private lateinit var layoutButton: android.widget.ImageView
    private lateinit var clipboardButton: android.widget.ImageView
    private lateinit var emojiButton: android.widget.ImageView
    private val toolbarButtons = linkedMapOf<String, android.widget.ImageView>()

    init {
        orientation = VERTICAL

        toolbar.orientation = HORIZONTAL
        toolbar.gravity = Gravity.CENTER_VERTICAL
        toolbar.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(44)).apply {
            // 툴바와 키 영역 사이 여백
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
        setupToolbarReorder()
        applyToolbarOrder(KeyboardSettings.TOOLBAR_ORDER_DEFAULT)
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
        theme = KeyboardTheme.of(dark, settings.highContrast, settings.highContrastStyle)
        keyboardView.theme = theme
        keyboardView.showNumberRow = settings.showNumberRow
        keyboardView.koreanLayout = settings.koreanLayout
        keyboardView.shiftNumberRowSymbols = settings.shiftNumberRowSymbols
        keyboardView.showKeyBackground = settings.showKeyBackground
        keyboardView.keyPreviewEnabled = settings.keyPreviewEnabled
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
        toolbarButtons.values.forEach {
            it.imageTintList = android.content.res.ColorStateList.valueOf(theme.text)
        }
        applyToolbarOrder(settings.toolbarOrder)
        showKeyboard()
    }

    /** 툴바 아이콘 순서 적용. 목록에 없는 아이콘은 뒤에 붙인다. */
    private fun applyToolbarOrder(orderCsv: String) {
        toolbar.removeAllViews()
        val added = mutableSetOf<String>()
        orderCsv.split(',').map { it.trim() }.forEach { id ->
            toolbarButtons[id]?.let {
                toolbar.addView(it)
                added.add(id)
            }
        }
        toolbarButtons.forEach { (id, view) -> if (id !in added) toolbar.addView(view) }
    }

    /** 아이콘을 길게 눌러 드래그하면 순서를 바꾼다. */
    private fun setupToolbarReorder() {
        toolbarButtons.forEach { (id, view) ->
            view.tag = id
            view.setOnLongClickListener { v ->
                v.startDragAndDrop(
                    android.content.ClipData.newPlainText("toolbar", id),
                    View.DragShadowBuilder(v),
                    v,
                    0,
                )
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
                    var index = 0
                    for (i in 0 until toolbar.childCount) {
                        val child = toolbar.getChildAt(i)
                        if (event.x > child.x + child.width / 2f) index = i + 1
                    }
                    toolbar.addView(dragged, index)
                    val order = (0 until toolbar.childCount)
                        .mapNotNull { toolbar.getChildAt(it).tag as? String }
                        .joinToString(",")
                    callbacks.onToolbarOrderChanged(order)
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
        clipboardPanel?.let { contentFrame.removeView(it) }
        clipboardPanel = null
        emojiPanel?.let { contentFrame.removeView(it) }
        emojiPanel = null
        clipboardHeader?.let { removeView(it) }
        clipboardHeader = null
        toolbar.visibility = if (toolbarEnabled) VISIBLE else GONE
        adjustOverlay?.let { contentFrame.removeView(it) }
        adjustOverlay = null
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

    private fun applyMargins(topDp: Int, bottomDp: Int, sideDp: Int, heightDp: Int) {
        marginTopDp = topDp
        marginBottomDp = bottomDp
        marginSideDp = sideDp
        keyboardHeightDp = heightDp
        keyboardView.heightDp = heightDp
        // 하단에는 시스템 내비게이션 바 인셋을 더해 제스처 영역과 겹치지 않게 한다.
        keyboardWrapper.setPadding(dp(sideDp), dp(topDp), dp(sideDp), dp(bottomDp) + navInsetPx)
        // 콘텐츠 영역 높이를 키보드 높이로 고정 — 패널(클립보드·이모지) 내용이
        // 길어도 창이 위로 자라지 않고 패널 안에서 스크롤된다.
        contentFrame.layoutParams = (contentFrame.layoutParams as LayoutParams).apply {
            height = dp(heightDp + topDp + bottomDp) + navInsetPx
        }
    }

    override fun onApplyWindowInsets(insets: android.view.WindowInsets): android.view.WindowInsets {
        val newInset =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom
            } else {
                @Suppress("DEPRECATION")
                insets.systemWindowInsetBottom
            }
        if (newInset != navInsetPx) {
            navInsetPx = newInset
            applyMargins(marginTopDp, marginBottomDp, marginSideDp, keyboardHeightDp)
        }
        return super.onApplyWindowInsets(insets)
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

    private fun toggleEmojiPanel() {
        val wasOpen = emojiPanel != null
        showKeyboard()
        if (wasOpen) return

        val panel = buildEmojiPanel()
        emojiPanel = panel
        keyboardView.visibility = INVISIBLE
        contentFrame.addView(
            panel,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
        )
        attachHeader(buildEmojiHeader())
    }

    private fun buildEmojiHeader(): View {
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(theme.specialKey)
            setPadding(dp(8), 0, dp(8), 0)
        }
        header.addView(
            headerImage(R.drawable.ic_toolbar_keyboard, context.getString(R.string.clipboard_back_to_keyboard)) {
                showKeyboard()
            },
        )
        header.addView(TextView(context).apply {
            text = context.getString(R.string.toolbar_emoji_desc)
            setTextColor(theme.text)
            setTypeface(null, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(dp(6), 0, 0, 0)
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
     * 이모지 패널: 키보드 높이를 유지하고 카테고리 블록을 가로로 스크롤한다.
     * 하단에는 카테고리 대표 이모지 탭 바 — 누르면 해당 섹션으로 이동한다.
     */
    private fun buildEmojiPanel(): View {
        val tabBarHeightDp = 40
        val rowCount = ((keyboardHeightDp - 28 - tabBarHeightDp) / 46).coerceIn(3, 6)
        val content = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setPadding(dp(8), dp(2), dp(8), dp(2))
        }
        val blocks = mutableListOf<View>()
        EmojiData.categories.forEach { category ->
            val block = LinearLayout(context).apply {
                orientation = VERTICAL
                setPadding(0, 0, dp(10), 0)
            }
            block.addView(TextView(context).apply {
                text = category.title
                setTextColor(theme.subText)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(dp(4), dp(4), 0, dp(2))
            })
            val grid = LinearLayout(context).apply { orientation = HORIZONTAL }
            category.emojis.chunked(rowCount).forEach { columnEmojis ->
                val columnView = LinearLayout(context).apply { orientation = VERTICAL }
                columnEmojis.forEach { emoji ->
                    columnView.addView(
                        TextView(context).apply {
                            text = emoji
                            gravity = Gravity.CENTER
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                            setOnClickListener { callbacks.onEmoji(emoji) }
                        },
                        LayoutParams(dp(46), dp(46)),
                    )
                }
                grid.addView(columnView)
            }
            block.addView(grid)
            content.addView(block)
            blocks.add(block)
        }
        val emojiScroll = android.widget.HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(content)
        }

        // 하단 카테고리 탭 바
        val tabs = mutableListOf<TextView>()
        fun highlightTab(active: Int) {
            tabs.forEachIndexed { index, tab ->
                tab.background = if (index == active) {
                    android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.OVAL
                        setColor(theme.specialKey)
                    }
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
        EmojiData.categories.forEachIndexed { index, category ->
            val tab = TextView(context).apply {
                text = category.emojis.first()
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                contentDescription = category.title
                layoutParams = LayoutParams(dp(38), dp(38)).apply {
                    marginStart = dp(4)
                    marginEnd = dp(4)
                }
                setOnClickListener {
                    emojiScroll.smoothScrollTo(blocks[index].left, 0)
                    highlightTab(index)
                }
            }
            tabs.add(tab)
            tabRow.addView(tab)
        }
        highlightTab(0)
        // 본문 스크롤 위치에 따라 활성 탭을 갱신한다.
        emojiScroll.setOnScrollChangeListener { _, scrollX, _, _, _ ->
            var active = 0
            blocks.forEachIndexed { index, block ->
                if (block.left <= scrollX + dp(40)) active = index
            }
            highlightTab(active)
        }

        // 맨 왼쪽 검색 아이콘
        tabRow.addView(
            headerImage(R.drawable.ic_icon_search, context.getString(R.string.emoji_search_hint)) {
                openEmojiSearch()
            },
            0,
        )

        val panel = LinearLayout(context).apply {
            orientation = VERTICAL
            setBackgroundColor(theme.background)
            // 시스템 하단바(제스처 영역)와 겹치지 않게 탭 바를 위로 띄운다.
            setPadding(0, 0, 0, navBarInset())
        }
        panel.addView(emojiScroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        panel.addView(
            android.widget.HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                // 탭 바 섹션은 배경색으로 구분한다.
                setBackgroundColor(theme.specialKey)
                addView(tabRow)
            },
            LayoutParams(LayoutParams.MATCH_PARENT, dp(tabBarHeightDp)),
        )
        return panel
    }

    private fun navBarInset(): Int =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            rootWindowInsets?.getInsets(android.view.WindowInsets.Type.navigationBars())?.bottom ?: 0
        } else {
            @Suppress("DEPRECATION")
            rootWindowInsets?.systemWindowInsetBottom ?: 0
        }

    /** 이모지 검색 모드: 헤더가 검색창이 되고, 그 아래 결과 스트립 + 일반 키보드로 입력한다. */
    private fun openEmojiSearch() {
        showKeyboard()
        emojiSearchOpen = true
        attachHeader(buildEmojiSearchHeader())
        val results = buildEmojiSearchResultsRow()
        emojiSearchResultsRow = results
        addView(results, 1, LayoutParams(LayoutParams.MATCH_PARENT, dp(46)).apply { bottomMargin = dp(4) })
        callbacks.onEmojiSearchStateChanged(true)
        updateEmojiSearch("")
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
        EmojiData.search(text).forEach { emoji ->
            list.addView(
                TextView(context).apply {
                    this.text = emoji
                    gravity = Gravity.CENTER
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                    setOnClickListener { callbacks.onEmoji(emoji) }
                },
                LayoutParams(dp(46), LayoutParams.MATCH_PARENT),
            )
        }
    }

    private fun buildEmojiSearchHeader(): View {
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(theme.specialKey)
            setPadding(dp(8), 0, dp(8), 0)
        }
        header.addView(
            android.widget.ImageView(context).apply {
                setImageResource(R.drawable.ic_icon_search)
                imageTintList = android.content.res.ColorStateList.valueOf(theme.subText)
                layoutParams = LayoutParams(dp(24), dp(24)).apply { marginStart = dp(8) }
            },
        )
        emojiSearchQueryView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(dp(8), 0, 0, 0)
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        header.addView(emojiSearchQueryView)
        header.addView(
            headerImage(R.drawable.ic_icon_close, context.getString(R.string.clipboard_cancel)) {
                showKeyboard()
                toggleEmojiPanel()
            },
        )
        return header
    }

    private fun buildEmojiSearchResultsRow(): View {
        emojiSearchResultsList = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setPadding(dp(8), 0, dp(8), 0)
        }
        return android.widget.HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
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
            imageTintList = android.content.res.ColorStateList.valueOf(theme.text)
            (layoutParams as MarginLayoutParams).apply {
                marginStart = dp(4)
                marginEnd = dp(4)
            }
        }

    private fun attachHeader(header: View) {
        toolbar.visibility = GONE
        clipboardHeader?.let { removeView(it) }
        clipboardHeader = header
        addView(
            header,
            0,
            LayoutParams(LayoutParams.MATCH_PARENT, dp(44)).apply { bottomMargin = dp(6) },
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
        attachHeader(buildClipboardHeader())
    }

    private fun buildClipboardHeader(): View {
        val entries = callbacks.clipboardEntries()

        fun headerIcon(
            drawableRes: Int,
            description: String,
            onClick: () -> Unit,
        ) = toolbarIcon(drawableRes, description, onClick).apply {
            imageTintList = android.content.res.ColorStateList.valueOf(theme.text)
            (layoutParams as MarginLayoutParams).apply {
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
        // 좌측: 키보드 아이콘(누르면 키보드로 복귀) + 제목
        header.addView(
            headerIcon(R.drawable.ic_toolbar_keyboard, context.getString(R.string.clipboard_back_to_keyboard)) {
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
        setPadding(dp(8), dp(6), dp(8), dp(6))
        setOnClickListener { onClick() }
        layoutParams = LayoutParams(dp(40), dp(36)).apply {
            marginStart = dp(10)
            marginEnd = dp(10)
        }
    }

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics,
        ).toInt()

}

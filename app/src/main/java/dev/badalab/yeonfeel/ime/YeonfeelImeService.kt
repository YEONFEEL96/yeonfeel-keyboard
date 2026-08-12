package dev.badalab.yeonfeel.ime

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import dev.badalab.yeonfeel.R
import dev.badalab.yeonfeel.clipboard.ClipboardHistory
import dev.badalab.yeonfeel.clipboard.SecureClipboardStore
import dev.badalab.yeonfeel.hangul.ChunjiinComposer
import dev.badalab.yeonfeel.hangul.HangulComposer
import dev.badalab.yeonfeel.hangul.KoreanComposer
import dev.badalab.yeonfeel.hangul.NaratgulComposer
import dev.badalab.yeonfeel.settings.KeyboardSettings
import dev.badalab.yeonfeel.settings.KoreanLayoutType
import dev.badalab.yeonfeel.settings.SymbolBoardStyle
import dev.badalab.yeonfeel.settings.SettingsActivity

class YeonfeelImeService : InputMethodService() {

    private val dubeolComposer = HangulComposer()
    private val chunjiinComposer = ChunjiinComposer()
    private val naratgulComposer = NaratgulComposer()
    private var composer: KoreanComposer = dubeolComposer
    private val clipboardHistory = ClipboardHistory()
    private lateinit var clipboardStore: SecureClipboardStore
    private lateinit var touchStats: dev.badalab.yeonfeel.debug.TouchStatsStore
    private lateinit var touchModel: TouchModel
    private val wordCorrector = dev.badalab.yeonfeel.hangul.WordCorrector()

    /** 직전 자동 교정 (원래 어절, 교정 어절) — 백스페이스 한 번으로 되돌린다. */
    private var lastCorrection: Pair<String, String>? = null
    private var pendingTouchSample: dev.badalab.yeonfeel.debug.TouchStatsStore.Sample? = null
    private var container: KeyboardContainerView? = null
    private var mode = LayoutMode.KOREAN

    /** 비밀번호류 입력란 여부. 타점 수집·키 미리보기·MZ 모드를 끈다. */
    private var sensitiveField = false

    /** 앱이 개인화 학습을 거부한 필드(IME_FLAG_NO_PERSONALIZED_LEARNING) — 타점 수집·교정을 끈다. */
    private var noLearnField = false

    /** 자동완성·자동 대문자를 끄는 필드(NO_SUGGESTIONS·이메일·URL 등). */
    private var noAutoTextHelp = false
    private lateinit var settings: KeyboardSettings
    private lateinit var clipboardManager: ClipboardManager

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { captureClip() }
    private val ioExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /** 파일이 바뀐 경우에만 백그라운드에서 읽어 메인에서 반영한다 (복호화 비용 절약). */
    private fun reloadStoresAsync() {
        ioExecutor.execute {
            val entries = clipboardStore.loadIfChanged()
            val statsChanged = touchStats.reload()
            mainHandler.post {
                entries?.let { clipboardHistory.restore(it) }
                if (statsChanged) touchModel.invalidate()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        settings = KeyboardSettings(this)
        clipboardStore = SecureClipboardStore(this)
        touchStats = dev.badalab.yeonfeel.debug.TouchStatsStore(this)
        touchModel = TouchModel(touchStats)
        ioExecutor.execute {
            runCatching { assets.open("ko_freq.txt").use(wordCorrector::load) }
            runCatching {
                assets.open("ko_known.bloom").use { wordCorrector.loadKnown(it.readBytes()) }
            }
        }
        reloadStoresAsync()
        clipboardManager = getSystemService(ClipboardManager::class.java)
        clipboardManager.addPrimaryClipChangedListener(clipListener)
    }

    override fun onDestroy() {
        clipboardManager.removePrimaryClipChangedListener(clipListener)
        // 남은 표본을 마지막으로 반영한 뒤 종료한다 (io 큐가 순서대로 처리).
        val tail = pendingTouchSample
        pendingTouchSample = null
        ioExecutor.execute {
            tail?.let { touchStats.add(it) }
            touchStats.flush()
        }
        ioExecutor.shutdown()
        // 파괴 이후 도착할 mainHandler.post 콜백을 제거한다.
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    /** 자동완성·자동 대문자를 끌 필드인지 — NO_SUGGESTIONS 플래그나 이메일·URL·필터 변형. */
    private fun isNoAutoHelpField(inputType: Int): Boolean {
        if (inputType and android.text.InputType.TYPE_MASK_CLASS !=
            android.text.InputType.TYPE_CLASS_TEXT
        ) {
            return false
        }
        if (inputType and android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0) return true
        return when (inputType and android.text.InputType.TYPE_MASK_VARIATION) {
            android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            android.text.InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            android.text.InputType.TYPE_TEXT_VARIATION_URI,
            android.text.InputType.TYPE_TEXT_VARIATION_FILTER,
            android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD,
            android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            -> true
            else -> false
        }
    }

    /** 엔터 키에 표시할 동작 라벨 (다음/검색/완료 등). 없으면 null → ⏎ 아이콘. */
    private fun enterActionLabel(info: EditorInfo?): CharSequence? {
        info ?: return null
        if (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return null
        info.actionLabel?.let { return it }
        return when (info.imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_GO -> getString(R.string.ime_action_go)
            EditorInfo.IME_ACTION_SEARCH -> getString(R.string.ime_action_search)
            EditorInfo.IME_ACTION_SEND -> getString(R.string.ime_action_send)
            EditorInfo.IME_ACTION_NEXT -> getString(R.string.ime_action_next)
            EditorInfo.IME_ACTION_DONE -> getString(R.string.ime_action_done)
            EditorInfo.IME_ACTION_PREVIOUS -> getString(R.string.ime_action_previous)
            else -> null
        }
    }

    /**
     * 숫자 키패드를 띄울 입력 필드인지 — 숫자·전화 클래스 (웹 inputmode=numeric 포함).
     * 날짜/시간은 '/'·':'·'-' 등 구분자가 필요해 숫자패드로는 입력이 막히므로 제외하고
     * 일반 자판(기호 페이지로 구분자 입력)으로 둔다.
     */
    private fun isNumericInput(inputType: Int): Boolean =
        when (inputType and android.text.InputType.TYPE_MASK_CLASS) {
            android.text.InputType.TYPE_CLASS_NUMBER,
            android.text.InputType.TYPE_CLASS_PHONE,
            -> true
            else -> false
        }

    /** 숫자 키패드 변형 비트 — 전화면 다이얼패드, 소수점·부호면 해당 기호 키를 추가한다. */
    private fun numberVariant(inputType: Int): Int {
        if (inputType and android.text.InputType.TYPE_MASK_CLASS ==
            android.text.InputType.TYPE_CLASS_PHONE
        ) {
            return KeyboardLayouts.NUM_PHONE
        }
        var v = 0
        if (inputType and android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL != 0) {
            v = v or KeyboardLayouts.NUM_DECIMAL
        }
        if (inputType and android.text.InputType.TYPE_NUMBER_FLAG_SIGNED != 0) {
            v = v or KeyboardLayouts.NUM_SIGNED
        }
        return v
    }

    private fun isPasswordInput(inputType: Int): Boolean {
        val cls = inputType and android.text.InputType.TYPE_MASK_CLASS
        val variation = inputType and android.text.InputType.TYPE_MASK_VARIATION
        return when (cls) {
            android.text.InputType.TYPE_CLASS_TEXT -> variation in setOf(
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD,
                android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            )
            android.text.InputType.TYPE_CLASS_NUMBER ->
                variation == android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
    }

    /** 가로 모드에서 전체 화면(extract) 모드로 전환되며 키보드가 사라지는 것을 막는다. */
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onCreateInputView(): View {
        val view = KeyboardContainerView(this, callbacks)
        view.keyboardView.mode = mode
        view.keyboardView.touchStatsProvider = { board -> touchModel.statsFor(board) }
        view.keyboardView.hasTextToDelete = {
            composer.isComposing ||
                currentInputConnection?.let { ic ->
                    ic.getSelectedText(0)?.isNotEmpty() == true ||
                        ic.getTextBeforeCursor(1, 0)?.isNotEmpty() == true
                } == true
        }
        view.keyboardView.onTapRecorded = { key, ax, ay, rx, ry ->
            if (settings.touchStatsEnabled && !sensitiveField && !noLearnField &&
                key.type != KeyType.SPACER
            ) {
                val keyId = when (key.type) {
                    KeyType.CHAR, KeyType.GHOST -> key.char.toString()
                    else -> key.type.name
                }
                val board = view.keyboardView.currentBoardId()
                val sample =
                    dev.badalab.yeonfeel.debug.TouchStatsStore.Sample(board, keyId, ax, ay, rx, ry)
                // 지연 커밋: 바로 백스페이스가 따라오면 오타 탭으로 보고 표본을 버린다.
                // 파일 쓰기는 입력 핸들러를 막지 않도록 io 스레드로 넘긴다(스토어는 @Synchronized).
                if (key.type == KeyType.DELETE) {
                    pendingTouchSample = null
                    ioExecutor.execute { touchStats.add(sample) }
                } else {
                    pendingTouchSample?.let { s -> ioExecutor.execute { touchStats.add(s) } }
                    pendingTouchSample = sample
                }
            }
        }
        view.keyboardView.onLanguageSelected = { index ->
            availableLanguages.getOrNull(index)?.first?.let { selectLanguage(it) }
        }
        container = view
        view.applySettings(settings)
        return view
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val fieldInputType = info?.inputType ?: 0
        sensitiveField = isPasswordInput(fieldInputType)
        noLearnField =
            (info?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0
        noAutoTextHelp = isNoAutoHelpField(fieldInputType)
        container?.keyboardView?.enterActionLabel = enterActionLabel(info)
        composer.reset()
        composer = when (settings.koreanLayout) {
            KoreanLayoutType.CHUNJIIN -> chunjiinComposer
            KoreanLayoutType.NARATGUL, KoreanLayoutType.NARATGUL_CENTER -> naratgulComposer
            else -> dubeolComposer
        }
        dubeolComposer.doubleTapIotation = settings.koreanLayout == KoreanLayoutType.DANMOEUM
        dubeolComposer.doubleTapDoubling = settings.koreanLayout == KoreanLayoutType.DANMOEUM
        dubeolComposer.fixDwaet = settings.dwaetFixEnabled
        chunjiinComposer.fixDwaet = settings.dwaetFixEnabled
        val multiTapDelay = settings.multiTapDelayMs.toLong()
        dubeolComposer.multiTapTimeoutMs = multiTapDelay
        // 천지인 자동 방식: 연타 대기가 사실상 무한 — 같은 키는 스페이스바로 끊기 전까지 계속 사이클.
        chunjiinComposer.multiTapTimeoutMs =
            if (settings.chunjiinSpaceCommits) Long.MAX_VALUE else multiTapDelay
        naratgulComposer.multiTapTimeoutMs = multiTapDelay
        // 설정에서 꺼진 언어가 현재 모드면 켜진 언어로 강제 전환한다.
        if (mode == LayoutMode.ENGLISH && !settings.englishEnabled) mode = LayoutMode.KOREAN
        if (mode == LayoutMode.KOREAN && !settings.koreanEnabled) mode = LayoutMode.ENGLISH
        // 숫자·전화 입력 필드에서는 숫자 키패드를 띄운다. 언어 기억(mode)은 그대로 두어
        // 일반 필드로 돌아가면 이전 자판이 복원된다. OTP처럼 칸이 넘어가도 계속 숫자판이다.
        val numericField = isNumericInput(fieldInputType)
        container?.let {
            it.applySettings(settings)
            // 비밀번호 입력란에서는 어깨너머·화면 녹화로 노출되는 키 미리보기를 끈다.
            if (sensitiveField) it.keyboardView.keyPreviewEnabled = false
            it.keyboardView.numberVariant = if (numericField) numberVariant(fieldInputType) else 0
            it.keyboardView.mode = if (numericField) LayoutMode.NUMBER else mode
            it.keyboardView.shifted = false
            it.keyboardView.capsLock = false
        }
        // 설정의 '키보드 여백' 화면에서 조정 모드로 열어달라는 1회성 요청.
        if (settings.adjustModeRequested) {
            settings.adjustModeRequested = false
            container?.startAdjustMode()
        }
        updateLanguageNames()
        lastSpaceTime = 0
        updateAutoCapitalize()
        // 설정 화면에서 데이터를 삭제한 경우를 반영한다. 표시를 막지 않게 비동기로.
        reloadStoresAsync()
    }

    /**
     * 사용자가 커서를 직접 옮기면 조합 중이던 글자를 그 자리에서 확정한다.
     * 확정하지 않으면 다음 입력이 이전 조합 위치에서 일어난다.
     */
    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ) {
        super.onUpdateSelection(
            oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd,
        )
        // candidatesStart == -1은 앱의 지연·역순 콜백에서 일시적으로 나타날 수 있어
        // (연타 조합이 끊기는 오동작) 조합 영역이 유효할 때만 판정한다.
        lastCorrection = null
        if (composer.isComposing && candidatesStart >= 0 &&
            (newSelStart < candidatesStart || newSelStart > candidatesEnd)
        ) {
            composer.reset()
            currentInputConnection?.finishComposingText()
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        finishComposition()
        val tail = pendingTouchSample
        pendingTouchSample = null
        ioExecutor.execute {
            tail?.let { touchStats.add(it) }
            touchStats.flush()
        }
        super.onFinishInputView(finishingInput)
    }

    private val callbacks = object : KeyboardContainerView.Callbacks {
        override fun onKey(key: Key) = handleKey(key)

        override fun onPaste(text: String) {
            finishComposition()
            currentInputConnection?.commitText(text, 1)
            container?.showKeyboard()
        }

        override fun onEmoji(emoji: String) {
            finishComposition()
            currentInputConnection?.commitText(emoji, 1)
        }

        override fun onEmojiSearchStateChanged(open: Boolean) {
            emojiSearchBuffer.clear()
            emojiSearchComposing = ""
            emojiSearchComposer.reset()
        }

        override fun onRememberSymbol(symbol: Char) {
            settings.rememberedSymbol = symbol.toString()
        }

        override fun onSkinToneChanged(tone: Int) {
            settings.skinTone = tone
        }

        override fun onTerminalKey(keyCode: Int) {
            finishComposition()
            sendKeyWithMeta(keyCode, container?.consumeModifierMeta() ?: 0)
        }

        override fun onOneHandedModeChanged(mode: dev.badalab.yeonfeel.settings.OneHandedMode) {
            settings.oneHandedMode = mode
            container?.applySettings(settings)
        }

        override fun onOneHandedCycle() {
            settings.oneHandedMode = when (settings.oneHandedMode) {
                dev.badalab.yeonfeel.settings.OneHandedMode.OFF ->
                    dev.badalab.yeonfeel.settings.OneHandedMode.RIGHT
                dev.badalab.yeonfeel.settings.OneHandedMode.RIGHT ->
                    dev.badalab.yeonfeel.settings.OneHandedMode.LEFT
                dev.badalab.yeonfeel.settings.OneHandedMode.LEFT ->
                    dev.badalab.yeonfeel.settings.OneHandedMode.OFF
            }
            container?.applySettings(settings)
        }

        override fun onToolbarOrderChanged(order: String) {
            settings.toolbarOrder = order
        }

        override fun onOpenSettings() {
            startActivity(
                Intent(this@YeonfeelImeService, SettingsActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            requestHideSelf(0)
        }

        override fun onLanguageSwipe() = switchLanguage()

        override fun onSplitGapCommitted(percent: Int) {
            settings.splitGapPercent = percent
        }

        override fun onMarginsCommitted(topDp: Int, bottomDp: Int, sideDp: Int, heightDp: Int) {
            settings.marginTopDp = topDp
            settings.marginBottomDp = bottomDp
            settings.marginSideDp = sideDp
            settings.keyboardHeightDp = heightDp
        }

        override fun clipboardEntries(): List<ClipboardHistory.Entry> =
            clipboardHistory.entries(System.currentTimeMillis())

        override fun onClipboardDelete(texts: List<String>) {
            texts.forEach { clipboardHistory.remove(it) }
            persistClipboard()
        }

        override fun onClipboardPin(texts: List<String>, pinned: Boolean) {
            texts.forEach { clipboardHistory.setPinned(it, pinned) }
            persistClipboard()
        }
    }

    /**
     * 클립보드 변경을 이력에 반영한다. 민감 표시(EXTRA_IS_SENSITIVE)가 있는 클립은
     * ClipboardHistory 정책에 따라 저장되지 않는다.
     */
    private fun captureClip() {
        val clip = clipboardManager.primaryClip ?: return
        if (clip.itemCount == 0) return
        val sensitiveKey = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ClipDescription.EXTRA_IS_SENSITIVE
        } else {
            "android.content.extra.IS_SENSITIVE"
        }
        val isSensitive = clip.description.extras?.getBoolean(sensitiveKey, false) ?: false
        val text = clip.getItemAt(0).coerceToText(this)?.toString() ?: return
        if (clipboardHistory.add(text, isSensitive, System.currentTimeMillis())) {
            persistClipboard()
        }
    }

    private fun persistClipboard() {
        // Keystore 암복호화 + 파일 쓰기는 바인더 왕복이라 메인 스레드에서 ANR 위험이
        // 있어 io 스레드로 넘긴다. 스토어·이력 모두 @Synchronized 라 안전하다.
        val snapshot = clipboardHistory.entries(System.currentTimeMillis())
        ioExecutor.execute { clipboardStore.save(snapshot) }
    }

    // 이모지 검색 모드: 키 입력을 앱이 아니라 검색어 버퍼로 보낸다.
    private val emojiSearchComposer = HangulComposer()
    private val emojiSearchBuffer = StringBuilder()
    private var emojiSearchComposing = ""

    private fun handleEmojiSearchKey(key: Key) {
        val view = container?.keyboardView ?: return
        when (key.type) {
            KeyType.CHAR, KeyType.GHOST -> {
                val c = key.char
                if (mode == LayoutMode.KOREAN && isComposerInput(c)) {
                    val result = emojiSearchComposer.input(c, System.currentTimeMillis())
                    emojiSearchBuffer.append(result.commit)
                    emojiSearchComposing = result.composing
                } else {
                    flushEmojiSearchComposer()
                    emojiSearchBuffer.append(c)
                }
                if (view.shifted) view.shifted = false
            }
            KeyType.DELETE -> {
                val result = emojiSearchComposer.backspace()
                if (result != null) {
                    emojiSearchComposing = result.composing
                } else if (emojiSearchBuffer.isNotEmpty()) {
                    emojiSearchBuffer.deleteCharAt(emojiSearchBuffer.length - 1)
                }
            }
            KeyType.SPACE -> {
                flushEmojiSearchComposer()
                emojiSearchBuffer.append(' ')
            }
            KeyType.SHIFT -> view.shifted = !view.shifted
            KeyType.LANG -> switchLanguage()
            else -> Unit
        }
        container?.updateEmojiSearch(emojiSearchBuffer.toString() + emojiSearchComposing)
    }

    private fun flushEmojiSearchComposer() {
        emojiSearchBuffer.append(emojiSearchComposer.flush())
        emojiSearchComposing = ""
    }

    private fun handleKey(key: Key) {
        if (container?.isEmojiSearchOpen() == true) {
            handleEmojiSearchKey(key)
            return
        }
        val view = container?.keyboardView ?: return
        when (key.type) {
            KeyType.CHAR, KeyType.GHOST -> {
                // ctrl/alt가 무장돼 있으면 문자 대신 조합 키 이벤트로 보낸다 (터미널용).
                val meta = container?.consumeModifierMeta() ?: 0
                if (meta != 0 && sendCharWithMeta(key.char, meta)) {
                    if (view.shifted && !view.capsLock) view.shifted = false
                    return
                }
                onChar(key.char)
                if (view.shifted && !view.capsLock) view.shifted = false
                updateAutoCapitalize()
            }
            KeyType.SHIFT -> {
                val now = System.currentTimeMillis()
                when {
                    // 고정 상태에서 한 번 더 누르면 완전 해제
                    view.capsLock -> {
                        view.capsLock = false
                        view.shifted = false
                    }
                    // 켜진 시프트를 빠르게 한 번 더 → 고정
                    view.shifted && now - lastShiftTime < CAPS_LOCK_TAP_MS -> view.capsLock = true
                    else -> view.shifted = !view.shifted
                }
                lastShiftTime = now
            }
            KeyType.DELETE -> {
                onDelete()
                updateAutoCapitalize()
            }
            KeyType.SPACE -> {
                onSpace()
                updateAutoCapitalize()
            }
            KeyType.ENTER -> {
                onEnter()
                updateAutoCapitalize()
            }
            KeyType.LANG -> switchLanguage()
            KeyType.SYMBOLS -> {
                finishComposition()
                view.symbolsPage = 0
                if (view.mode != LayoutMode.SYMBOLS) {
                    view.compactSymbols = when (settings.symbolBoardStyle) {
                        SymbolBoardStyle.QWERTY -> false
                        SymbolBoardStyle.GRID_3X4 -> true
                        // 연동: 3x4 나랏글 계열에서 들어왔을 때만 컴팩트 배치.
                        SymbolBoardStyle.AUTO -> mode == LayoutMode.KOREAN &&
                            settings.koreanLayout in setOf(
                                KoreanLayoutType.NARATGUL,
                                KoreanLayoutType.NARATGUL_CENTER,
                                KoreanLayoutType.CHUNJIIN,
                            )
                    }
                }
                view.mode = if (view.mode == LayoutMode.SYMBOLS) mode else LayoutMode.SYMBOLS
                view.shifted = false
                view.capsLock = false
            }
            KeyType.PAGE -> view.symbolsPage = when (key.char) {
                KeyboardLayouts.PAGE_TO_NUMPAD -> 3
                KeyboardLayouts.PAGE_TO_SYMBOLS -> 0
                KeyboardLayouts.PAGE_CYCLE -> (view.symbolsPage + 1) % 3
                else -> if (view.symbolsPage == 0) 1 else 0
            }
            KeyType.SPACER -> Unit
        }
    }

    private var availableLanguages: List<Pair<LayoutMode, String>> = emptyList()

    private fun switchLanguage() {
        if (!(settings.koreanEnabled && settings.englishEnabled)) return
        selectLanguage(if (mode == LayoutMode.ENGLISH) LayoutMode.KOREAN else LayoutMode.ENGLISH)
    }

    private fun selectLanguage(target: LayoutMode) {
        val view = container?.keyboardView ?: return
        if (target == mode) return
        finishComposition()
        mode = target
        view.mode = target
        view.shifted = false
        view.capsLock = false
        updateLanguageNames()
    }

    /** 언어 팝업·목록에 쓸 현재/다음 언어 이름과 전체 목록. */
    private fun updateLanguageNames() {
        val view = container?.keyboardView ?: return
        val korean = mode != LayoutMode.ENGLISH
        view.languageName = getString(if (korean) R.string.subtype_korean else R.string.subtype_english)
        view.nextLanguageName = getString(if (korean) R.string.subtype_english else R.string.subtype_korean)
        availableLanguages = buildList {
            if (settings.koreanEnabled) add(LayoutMode.KOREAN to getString(R.string.subtype_korean))
            if (settings.englishEnabled) add(LayoutMode.ENGLISH to getString(R.string.subtype_english))
        }
        view.languageList = availableLanguages.map { it.second }
        view.currentLanguageIndex =
            availableLanguages.indexOfFirst { it.first == mode }.coerceAtLeast(0)
    }

    /** 조합기로 보내야 하는 입력인지 — 호환/옛한글 자모, 천지인 ㆍ, 나랏글 변형 키. */
    private fun isComposerInput(c: Char): Boolean =
        HangulComposer.isHangulJamo(c) || c == ChunjiinComposer.KEY_ARAEA ||
            c == NaratgulComposer.KEY_ADD_STROKE || c == NaratgulComposer.KEY_DOUBLE

    private var kiekStreak = 0

    /** 유머 모드: ㅋ 3연타부터 레트로(40% ㄱ)·MZ(30% ㅎ) 치환. 비밀번호 필드 제외. */
    private fun applyMzMode(c: Char): Char {
        if (sensitiveField || !(settings.mzModeEnabled || settings.oldieModeEnabled)) return c
        if (c == 'ㅋ') {
            kiekStreak++
            if (kiekStreak >= 3) {
                if (settings.oldieModeEnabled && kotlin.random.Random.nextFloat() < 0.4f) return 'ㄱ'
                if (settings.mzModeEnabled && kotlin.random.Random.nextFloat() < 0.3f) return 'ㅎ'
            }
        } else {
            kiekStreak = 0
        }
        return c
    }

    private fun onChar(rawChar: Char) {
        lastCorrection = null
        val c = if (mode == LayoutMode.KOREAN) applyMzMode(rawChar) else rawChar
        val ic = currentInputConnection ?: return
        if (mode == LayoutMode.KOREAN && isComposerInput(c)) {
            val result = composer.input(c, System.currentTimeMillis())
            ic.beginBatchEdit()
            // 조합기 내부 치환이 없는 자판(나랏글 등)을 위한 커밋 시점 안전망
            val commit = if (settings.dwaetFixEnabled) result.commit.replace('됬', '됐') else result.commit
            if (commit.isNotEmpty()) ic.commitText(commit, 1)
            ic.setComposingText(result.composing, 1)
            ic.endBatchEdit()
        } else {
            finishComposition()
            ic.commitText(c.toString(), 1)
        }
    }

    private fun sendKeyWithMeta(keyCode: Int, meta: Int) {
        val ic = currentInputConnection ?: return
        val now = android.os.SystemClock.uptimeMillis()
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, meta))
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, meta))
    }

    /** 키코드 매핑이 있는 문자(영문·숫자)만 조합 이벤트로 보낸다. 한글이면 false. */
    private fun sendCharWithMeta(c: Char, meta: Int): Boolean {
        val keyCode = when (c.lowercaseChar()) {
            in 'a'..'z' -> KeyEvent.KEYCODE_A + (c.lowercaseChar() - 'a')
            in '0'..'9' -> KeyEvent.KEYCODE_0 + (c - '0')
            ' ' -> KeyEvent.KEYCODE_SPACE
            else -> return false
        }
        finishComposition()
        sendKeyWithMeta(keyCode, meta)
        return true
    }

    private var lastShiftTime = 0L
    private var lastSpaceTime = 0L
    private val doubleSpaceMs = 500L

    companion object {
        private const val CAPS_LOCK_TAP_MS = 350L
    }

    private fun onSpace() {
        val ic = currentInputConnection ?: return
        // 천지인 옵션: 조합 중 첫 스페이스바는 띄어쓰기 대신 조합만 끊는다 (통용 관습).
        if (settings.chunjiinSpaceCommits && mode == LayoutMode.KOREAN &&
            settings.koreanLayout == KoreanLayoutType.CHUNJIIN && composer.isComposing
        ) {
            finishComposition()
            lastSpaceTime = 0
            return
        }
        val now = System.currentTimeMillis()
        if (settings.doubleSpacePeriod && now - lastSpaceTime < doubleSpaceMs && canDoubleSpacePeriod(ic)) {
            ic.deleteSurroundingText(1, 0)
            ic.commitText(". ", 1)
            lastSpaceTime = 0
            return
        }
        finishComposition()
        maybeAutoCorrect(ic)
        ic.commitText(" ", 1)
        lastSpaceTime = now
    }

    /** AI 보정(노이지 채널): 스페이스바로 어절이 끝날 때 사전 밖 어절을 교정한다. */
    private fun maybeAutoCorrect(ic: android.view.inputmethod.InputConnection) {
        lastCorrection = null
        if (sensitiveField || noLearnField || noAutoTextHelp || mode != LayoutMode.KOREAN) return
        if (!(settings.touchCorrectionEnabled && settings.touchCorrectionAi)) return
        val before = ic.getTextBeforeCursor(16, 0) ?: return
        val word = before.takeLastWhile { it in '가'..'힣' }.toString()
        if (word.length < 2) return
        val fixed = wordCorrector.correct(word) ?: return
        if (fixed == word) return
        ic.deleteSurroundingText(word.length, 0)
        ic.commitText(fixed, 1)
        lastCorrection = word to fixed
    }

    /** 직전이 "글자 + 공백"일 때만 마침표 축약을 적용한다. */
    private fun canDoubleSpacePeriod(ic: android.view.inputmethod.InputConnection): Boolean {
        val before = ic.getTextBeforeCursor(2, 0) ?: return false
        return before.length == 2 && before[1] == ' ' && before[0].isLetterOrDigit()
    }

    /** 영문 모드에서 문장 시작이면 Shift를 자동으로 켠다. */
    private fun updateAutoCapitalize() {
        if (!settings.autoCapitalize || mode != LayoutMode.ENGLISH) return
        // 이메일·URL·자동완성 끈 필드는 첫 글자 대문자화를 하지 않는다.
        if (noAutoTextHelp) return
        val view = container?.keyboardView ?: return
        if (view.mode != LayoutMode.ENGLISH) return
        val ic = currentInputConnection ?: return
        val inputType = currentInputEditorInfo?.inputType ?: 0
        val caps = ic.getCursorCapsMode(inputType or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        view.shifted = caps != 0
    }

    private fun onDelete() {
        val ic = currentInputConnection ?: return
        // 자동 교정 직후 백스페이스는 삭제 대신 원래 어절로 되돌린다.
        lastCorrection?.let { (original, fixed) ->
            lastCorrection = null
            ic.deleteSurroundingText(fixed.length + 1, 0)
            ic.commitText("$original ", 1)
            return
        }
        val result = composer.backspace()
        if (result != null) {
            if (result.composing.isEmpty()) {
                ic.commitText("", 1)
            } else {
                ic.setComposingText(result.composing, 1)
            }
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        }
    }

    private fun onEnter() {
        finishComposition()
        val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
        if (action != null && action != EditorInfo.IME_ACTION_NONE &&
            currentInputEditorInfo.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION == 0
        ) {
            currentInputConnection?.performEditorAction(action)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
    }

    /** 조합 중인 글자를 확정 문자열로 굳히고 composing region을 닫는다. */
    private fun finishComposition() {
        if (composer.isComposing) {
            composer.flush()
            currentInputConnection?.finishComposingText()
        }
    }
}

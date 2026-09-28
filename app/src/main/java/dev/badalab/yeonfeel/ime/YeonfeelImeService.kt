package dev.badalab.yeonfeel.ime

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
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
import dev.badalab.yeonfeel.translate.GeminiBlockFlag
import dev.badalab.yeonfeel.translate.TranslationBackend
import dev.badalab.yeonfeel.translate.TranslationEngine
import dev.badalab.yeonfeel.translate.TranslationLanguage
import dev.badalab.yeonfeel.translate.TranslationResult

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

    /** 우리 편집과 사용자의 커서 이동을 가르기 위한 기대 커서 위치 추적. */
    private val selection = SelectionTracker()
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
        geminiBlockFlag = GeminiBlockFlag(this, settings)
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
        // 번역 패널이 열린 채 파괴되면 입력란의 번역을 확정해 앱에 조합 영역을 남기지 않는다.
        endTranslation()
        // close 뒤에는 번역 콜백이 오지 않는다.
        translationBackend?.close()
        translationBackend = null
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
        // 회전·테마 변경 등으로 뷰를 새로 만들 때는 onFinishInputView 없이 옛 뷰가 버려진다.
        // 번역 패널이 열려 있었다면 여기서 닫는다 — 안 그러면 보이지 않는 원문 버퍼로 키가 간다.
        endTranslation()
        val view = KeyboardContainerView(this, callbacks)
        view.keyboardView.mode = mode
        view.keyboardView.touchStatsProvider = { board -> touchModel.statsFor(board) }
        view.keyboardView.hasTextToDelete = {
            (translateOpen && !translateBuffer.isEmpty()) || composer.isComposing ||
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

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        // 키보드를 내렸다 다시 올릴 때(onStartInputView만 다시 불림)는 initialSel이 낡았으므로
        // 입력란이 새로 시작될 때만 기준 위치를 잡는다.
        selection.start(attribute?.initialSelStart ?: -1, attribute?.initialSelEnd ?: -1)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // 같은 입력란의 재시작(보내기 뒤 앱이 입력란을 비울 때 등)이면 번역 패널을 다시 연다.
        // 아래 applySettings가 열린 패널을 모두 닫으므로 먼저 기억해 둔다.
        val reopenTranslate = restarting && translateOpen && !settings.adjustModeRequested
        val fieldInputType = info?.inputType ?: 0
        sensitiveField = isPasswordInput(fieldInputType)
        noLearnField =
            (info?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0
        noAutoTextHelp = isNoAutoHelpField(fieldInputType)
        container?.keyboardView?.enterActionLabel = enterActionLabel(info)
        composer.reset()
        selection.abandonComposition(cursorKnown = true)
        composer = when (settings.koreanLayout) {
            KoreanLayoutType.CHUNJIIN -> chunjiinComposer
            KoreanLayoutType.NARATGUL, KoreanLayoutType.NARATGUL_CENTER -> naratgulComposer
            else -> dubeolComposer
        }
        configureComposer(dubeolComposer)
        configureComposer(chunjiinComposer)
        configureComposer(naratgulComposer)
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
        // applySettings가 패널을 닫았다 — 뷰가 없어 닫힘 콜백이 오지 않았어도 상태를 맞춘다.
        endTranslation()
        // 설정에서 번역 엔진이 바뀌었으면 기존 엔진을 닫는다 (다음 사용 때 새로 만든다).
        syncTranslationBackend()
        updateTranslateButton()
        if (reopenTranslate && translationAllowed()) container?.openTranslatePanel()
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
        // 키보드가 내려가 있는 동안 복사한 텍스트가 있으면 이제 툴바 자리에 보여준다.
        pendingCopiedText?.let { text ->
            pendingCopiedText = null
            if (!sensitiveField) container?.showCopiedText(text)
        }
    }

    /** 자판 설정(단모음 연타·됬 치환·연타 대기)을 조합기에 반영한다. */
    private fun configureComposer(c: KoreanComposer) {
        val multiTapDelay = settings.multiTapDelayMs.toLong()
        when (c) {
            is HangulComposer -> {
                c.doubleTapIotation = settings.koreanLayout == KoreanLayoutType.DANMOEUM
                c.doubleTapDoubling = settings.koreanLayout == KoreanLayoutType.DANMOEUM
                c.fixDwaet = settings.dwaetFixEnabled
                c.multiTapTimeoutMs = multiTapDelay
            }
            is ChunjiinComposer -> {
                c.fixDwaet = settings.dwaetFixEnabled
                // 천지인 자동 방식: 연타 대기가 사실상 무한 — 같은 키는 스페이스바로 끊기 전까지 계속 사이클.
                c.multiTapTimeoutMs =
                    if (settings.chunjiinSpaceCommits) Long.MAX_VALUE else multiTapDelay
            }
            is NaratgulComposer -> c.multiTapTimeoutMs = multiTapDelay
        }
    }

    /** 지금 자판용 새 조합기 — 앱 쪽 조합기와 상태를 나누지 않는 번역 패널용. */
    private fun newKoreanComposer(): KoreanComposer {
        val c = when (settings.koreanLayout) {
            KoreanLayoutType.CHUNJIIN -> ChunjiinComposer()
            KoreanLayoutType.NARATGUL, KoreanLayoutType.NARATGUL_CENTER -> NaratgulComposer()
            else -> HangulComposer()
        }
        configureComposer(c)
        return c
    }

    /**
     * 사용자가 커서를 직접 옮기면 조합 중이던 글자를 그 자리에서 확정한다.
     * 확정하지 않으면 다음 입력이 이전 조합 위치에서 일어나거나, 앱이 이미 닫은 조합을
     * 새 커서 자리에 다시 써서 글자가 복제된다. 우리 편집의 늦은 콜백(연타 조합이 끊기는
     * 원인이던 카카오톡의 일시적 -1 포함)과의 구분은 [SelectionTracker]가 한다.
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
        val verdict = selection.onUpdate(
            oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd,
        )
        if (verdict != SelectionTracker.Verdict.MOVED) return
        lastCorrection = null
        lastSpaceTime = 0
        symbolCycle = null
        // 번역 패널: 마지막 번역은 입력란에 그대로 두고 패널 상태만 비운다.
        if (translateOpen) resetTranslation()
        if (composer.isComposing) {
            // 보류 병합(찬ㅎ→찮)은 하지 않는다 — flush 결과를 쓰면 커서가 옛 자리로 끌려온다.
            composer.reset()
            currentInputConnection?.let { closeComposing(it) }
        }
    }

    /**
     * 조합을 이어 가기 전에 앱의 조합 영역이 아직 커서 바로 앞에 있는지 확인한다.
     * 커서를 옮기자마자 친 키가 선택 콜백보다 먼저 온 경우, 앱이 조합을 제자리에서 닫은
     * 경우를 잡는다. 어긋났으면 조합기를 비우고 false — 화면의 글자는 앱에 있는 그대로 둔다.
     */
    private fun verifyComposition(ic: InputConnection): Boolean {
        if (!composer.isComposing) return true
        val dropped = selection.compositionDropped
        val sent = selection.composing
        val moved = !dropped && sent.isNotEmpty() &&
            ic.getTextBeforeCursor(sent.length, 0)?.let { it.toString() != sent } == true
        if (!dropped && !moved) return true
        composer.reset()
        // 조합 영역이 남아 있다면(일시적 -1) 그 자리에서 확정해 다음 조합이 덮어쓰지 않게 한다.
        ic.finishComposingText()
        selection.abandonComposition(cursorKnown = !moved)
        return false
    }

    // 입력 연결 편집은 아래 함수로 보내 SelectionTracker의 기대 커서 위치를 맞춘다.

    private fun commit(ic: InputConnection, text: String) {
        ic.commitText(text, 1)
        selection.onCommit(text)
    }

    private fun setComposing(ic: InputConnection, text: String) {
        ic.setComposingText(text, 1)
        selection.onSetComposing(text)
    }

    private fun closeComposing(ic: InputConnection) {
        ic.finishComposingText()
        selection.onFinishComposing()
    }

    private fun deleteBefore(ic: InputConnection, length: Int) {
        ic.deleteSurroundingText(length, 0)
        selection.onDeleteBefore(length)
    }

    /** 키 이벤트는 앱이 무엇을 할지(몇 글자 지울지, 줄바꿈할지) 알 수 없다. */
    private fun sendDownUpKey(keyCode: Int) {
        sendDownUpKeyEvents(keyCode)
        selection.onUnpredictableEdit()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        // 키보드가 내려가면 번역 패널을 닫는다 — 마지막 번역은 입력란에 남는다.
        container?.closeTranslatePanel()
        endTranslation()
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
            currentInputConnection?.let { commit(it, text) }
            container?.showKeyboard()
        }

        override fun onEmoji(emoji: String) {
            finishComposition()
            currentInputConnection?.let { commit(it, emoji) }
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
            // 화살표 등은 커서를 옮긴다 — 번역은 입력란에 두고 패널 상태를 먼저 비운다.
            if (translateOpen) resetTranslation()
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

        override fun onTranslatePanelStateChanged(open: Boolean) {
            if (open) startTranslation() else endTranslation()
        }

        override fun onTranslateSwap() = swapTranslationLanguages()
    }

    /**
     * 클립보드 변경을 이력에 반영한다. 민감 표시(EXTRA_IS_SENSITIVE)가 있는 클립은
     * ClipboardHistory 정책에 따라 저장되지 않는다.
     */
    private fun captureClip() {
        val clip = clipboardManager.primaryClip ?: return
        if (clip.itemCount == 0) return
        // ClipDescription.getExtras()·EXTRA_IS_SENSITIVE 는 API 24+ 라 그 미만에선 건너뛴다.
        val isSensitive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val sensitiveKey = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ClipDescription.EXTRA_IS_SENSITIVE
            } else {
                "android.content.extra.IS_SENSITIVE"
            }
            clip.description.extras?.getBoolean(sensitiveKey, false) ?: false
        } else {
            false
        }
        val text = clip.getItemAt(0).coerceToText(this)?.toString() ?: return
        if (clipboardHistory.add(text, isSensitive, System.currentTimeMillis())) {
            persistClipboard()
        }
        // 민감 정보가 아니면 툴바 자리에 복사한 텍스트를 보여준다. 복사 순간 키보드가
        // 내려가 있으면 대기시켰다가 다음에 키보드가 뜰 때(onStartInputView) 표시한다.
        if (!isSensitive) {
            if (isInputViewShown()) {
                container?.showCopiedText(text)
            } else {
                pendingCopiedText = text
            }
        }
    }

    private var pendingCopiedText: String? = null

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
        if (translateOpen && handleTranslateKey(key)) return
        val view = container?.keyboardView ?: return
        when (key.type) {
            KeyType.CHAR, KeyType.GHOST -> {
                // ctrl/alt가 무장돼 있으면 문자 대신 조합 키 이벤트로 보낸다 (터미널용).
                val meta = container?.consumeModifierMeta() ?: 0
                if (meta != 0 && sendCharWithMeta(key.char, meta)) {
                    if (view.shifted && !view.capsLock) view.shifted = false
                    return
                }
                if (!rotateSymbolKey(key)) onChar(key.char)
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
        // 번역 원문의 조합 중인 음절도 확정한다 — 다른 언어 글자가 조합에 섞이지 않게.
        if (translateOpen) {
            val before = translateBuffer.text
            translateBuffer.flushComposer()
            // 보류 병합(찬ㅎ→찮)으로 원문이 바뀌었으면 화면과 번역에 반영한다.
            if (translateBuffer.text != before) onTranslateSourceChanged()
        }
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
            // 연타 판정 시각은 아래 동기 조회(verifyComposition)의 왕복 시간을 빼고 잰다.
            val now = System.currentTimeMillis()
            // 조합이 끊겼으면(커서 이동 등) 조합기가 비워져 새 음절로 시작한다.
            verifyComposition(ic)
            val result = composer.input(c, now)
            ic.beginBatchEdit()
            // 조합기 내부 치환이 없는 자판(나랏글 등)을 위한 커밋 시점 안전망
            val committed = if (settings.dwaetFixEnabled) result.commit.replace('됬', '됐') else result.commit
            if (committed.isNotEmpty()) commit(ic, committed)
            setComposing(ic, result.composing)
            ic.endBatchEdit()
        } else {
            finishComposition()
            commit(ic, c.toString())
        }
    }

    private fun sendKeyWithMeta(keyCode: Int, meta: Int) {
        val ic = currentInputConnection ?: return
        val now = android.os.SystemClock.uptimeMillis()
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, meta))
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, meta))
        selection.onUnpredictableEdit()
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

    // 기호 키 연타 로테이션 상태 (천지인·3x4의 ".,?!"/".,-/" 키)
    private var symbolCycle: String? = null
    private var symbolCycleIndex = 0
    private var symbolCycleTime = 0L

    /** 라벨 전체가 연타 사이클인 기호 키인지 — 맞으면 사이클 문자열을 돌려준다. */
    private fun symbolCycleOf(key: Key): String? =
        key.label.takeIf { label ->
            key.type == KeyType.CHAR && label.length >= 2 && label[0] == key.char &&
                label.none { it.isLetterOrDigit() || HangulComposer.isHangulJamo(it) }
        }

    /**
     * 기호 키 연타: 제한 시간 안에 같은 키를 다시 누르면 직전 기호를 라벨
     * 사이클의 다음 기호로 교체한다 (. → , → ? → !). 교체했으면 true,
     * 첫 탭이면 사이클만 무장하고 false를 돌려줘 평소처럼 커밋하게 한다.
     */
    private fun rotateSymbolKey(key: Key): Boolean {
        val cycle = symbolCycleOf(key) ?: run {
            symbolCycle = null
            return false
        }
        val now = System.currentTimeMillis()
        val ic = currentInputConnection
        if (ic != null && cycle == symbolCycle &&
            now - symbolCycleTime < settings.multiTapDelayMs
        ) {
            // 커서 앞 글자가 직전에 넣은 사이클 기호일 때만 교체한다 (커서 이동 방어).
            val before = ic.getTextBeforeCursor(1, 0)
            if (before?.length == 1 && before[0] == cycle[symbolCycleIndex]) {
                symbolCycleIndex = (symbolCycleIndex + 1) % cycle.length
                lastCorrection = null
                ic.beginBatchEdit()
                deleteBefore(ic, 1)
                commit(ic, cycle[symbolCycleIndex].toString())
                ic.endBatchEdit()
                symbolCycleTime = now
                return true
            }
        }
        symbolCycle = cycle
        symbolCycleIndex = 0
        symbolCycleTime = now
        return false
    }

    companion object {
        private const val CAPS_LOCK_TAP_MS = 350L

        /** 마지막 키 입력 뒤 번역을 요청하기까지의 대기. */
        private const val TRANSLATE_DEBOUNCE_MS = 350L

        /** 엔진이 모델을 내려받는 중이면 이만큼 뒤 다시 시도한다. */
        private const val TRANSLATE_RETRY_MS = 2_000L
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
            deleteBefore(ic, 1)
            commit(ic, ". ")
            lastSpaceTime = 0
            return
        }
        finishComposition()
        maybeAutoCorrect(ic)
        commit(ic, " ")
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
        deleteBefore(ic, word.length)
        commit(ic, fixed)
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
        // 시프트 더블탭 고정(Caps Lock) 중에는 자동 대문자화가 상태를 덮어쓰지 않는다.
        if (view.capsLock) return
        val ic = currentInputConnection ?: return
        val inputType = currentInputEditorInfo?.inputType ?: 0
        val caps = ic.getCursorCapsMode(inputType or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        view.shifted = caps != 0
    }

    private fun onDelete() {
        val ic = currentInputConnection ?: return
        // 자동 교정 직후 백스페이스는 삭제 대신 원래 어절로 되돌린다.
        // 그 사이 다른 편집(엔터·붙여넣기 등)이 있었으면 커서 앞이 달라져 있으므로 보통 삭제한다.
        lastCorrection?.let { (original, fixed) ->
            lastCorrection = null
            if (ic.getTextBeforeCursor(fixed.length + 1, 0)?.toString() == "$fixed ") {
                deleteBefore(ic, fixed.length + 1)
                commit(ic, "$original ")
                return
            }
        }
        // 조합이 끊겼으면 조합기가 비워져 커서 앞 글자를 보통 삭제한다.
        verifyComposition(ic)
        val result = composer.backspace()
        if (result != null) {
            if (result.composing.isEmpty()) {
                commit(ic, "")
            } else {
                setComposing(ic, result.composing)
            }
        } else {
            sendDownUpKey(KeyEvent.KEYCODE_DEL)
        }
    }

    private fun onEnter() {
        finishComposition()
        val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
        if (action != null && action != EditorInfo.IME_ACTION_NONE &&
            currentInputEditorInfo.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION == 0
        ) {
            currentInputConnection?.performEditorAction(action)
            selection.onUnpredictableEdit()
        } else {
            sendDownUpKey(KeyEvent.KEYCODE_ENTER)
        }
    }

    /** 조합 중인 글자를 확정 문자열로 굳히고 composing region을 닫는다. */
    private fun finishComposition() {
        if (!composer.isComposing) return
        val ic = currentInputConnection
        // 조합 영역이 커서 앞에서 사라졌으면 다시 쓰지 않는다 — 새 커서 자리에 옛 글자가 복제된다.
        if (ic != null && !verifyComposition(ic)) return
        // flush가 보류 병합(찬ㅎ→찮)을 수행할 수 있어 화면의 조합 텍스트와
        // 다를 수 있다 — 결과를 조합 영역에 반영한 뒤 닫는다.
        var text = composer.flush()
        if (settings.dwaetFixEnabled) text = text.replace('됬', '됐')
        if (ic != null) {
            setComposing(ic, text)
            closeComposing(ic)
        } else {
            selection.abandonComposition(cursorKnown = false)
        }
    }

    // ---- 번역 패널 ----
    // 원문은 패널 버퍼에 쌓고, 번역만 앱의 조합 영역에 쓴다. 앱 편집은 모두 commit/setComposing/
    // closeComposing을 거쳐 SelectionTracker가 우리 편집을 커서 이동으로 오인하지 않게 한다.

    private val translateBuffer = TranslateSourceBuffer()
    private val translateRequests = TranslateRequestState()
    private var translateOpen = false
    private lateinit var geminiBlockFlag: GeminiBlockFlag

    /** 처음 쓸 때 만들고, 설정에서 엔진이 바뀌면 닫고 다시 만든다. */
    private var translationBackend: TranslationBackend? = null
    private var translationBackendEngine: TranslationEngine? = null

    /** 이 키보드 세션에서 쓸 수 없다고 확인한 엔진과 그 상태 (엔진 없음·애드온 없음·막힘). */
    private var unusableEngine: Pair<TranslationEngine, TranslateStatus>? = null

    // 패널을 열 때 정한 엔진·언어 쌍 (언어 칩으로 바꾸면 갱신).
    private var translateEngine = TranslationEngine.DEFAULT
    private var translateSource = TranslationLanguage.DEFAULT_SOURCE
    private var translateTarget = TranslationLanguage.DEFAULT_TARGET

    /** 키보드 안에서 Gemini Nano가 막혀 있다 — 요청을 보내지 않고 이유만 보여준다. */
    private var translateGeminiBlocked = false

    private val translateDebounce = Runnable { requestTranslation(confirm = false) }
    private var translateRetrySource: String? = null
    private val translateRetry = Runnable {
        // 패널이 열려 있고 원문이 그대로일 때만 다시 시도한다.
        if (translateOpen && translateBuffer.text == translateRetrySource) {
            requestTranslation(confirm = translateRequests.confirmPending)
        }
    }

    /** 비밀번호 입력란과 개인화 학습을 거부한 입력란에서는 번역을 쓰지 않는다. */
    private fun translationAllowed(): Boolean = !sensitiveField && !noLearnField

    private fun syncTranslationBackend() {
        val engine = settings.translationEngine
        if (translationBackendEngine != null && translationBackendEngine != engine) {
            translationBackend?.close()
            translationBackend = null
            translationBackendEngine = null
        }
    }

    private fun backendFor(engine: TranslationEngine): TranslationBackend {
        if (translationBackendEngine != engine) {
            translationBackend?.close()
            translationBackend = null
        }
        return translationBackend ?: TranslationBackend.create(this, engine, allowMeteredDownload = false).also {
            translationBackend = it
            translationBackendEngine = engine
        }
    }

    /** 고른 엔진을 쓸 수 없다고 이미 알고 있는지 — Gemini 막힘 기록 또는 이번 세션의 실패. */
    private fun engineKnownUnusable(engine: TranslationEngine): Boolean =
        (engine == TranslationEngine.GEMINI_NANO && geminiBlockFlag.isBlocked()) ||
            unusableEngine?.first == engine

    private fun updateTranslateButton() {
        container?.setTranslateButtonState(
            visible = translationAllowed(),
            dimmed = engineKnownUnusable(settings.translationEngine),
        )
    }

    private fun startTranslation() {
        if (!translationAllowed()) {
            container?.closeTranslatePanel()
            return
        }
        // 이미 열려 있던 상태가 남아 있으면 입력란의 번역부터 확정해 새 번역이 덮어쓰지 않게 한다.
        endTranslation()
        // 앱에서 조합 중이던 글자는 먼저 확정한다 — 이후 앱 조합 영역은 번역만 쓴다.
        finishComposition()
        translateOpen = true
        syncTranslationBackend()
        translateEngine = settings.translationEngine
        translateSource = settings.translationSource
        translateTarget = settings.translationTarget
        translateGeminiBlocked =
            translateEngine == TranslationEngine.GEMINI_NANO && geminiBlockFlag.isBlocked()
        translateBuffer.composer = newKoreanComposer()
        translateBuffer.fixDwaet = settings.dwaetFixEnabled
        translateBuffer.clear()
        translateRequests.reset()
        symbolCycle = null
        showTranslateStatus(TranslateStatus.initial(translateEngine, translateGeminiBlocked, unusableEngine))
        refreshTranslateSource()
        refreshTranslatePair()
        updateTranslateButton()
    }

    /** 패널이 닫혔다. 마지막 번역은 입력란에 그대로 남긴다. */
    private fun endTranslation() {
        if (!translateOpen) return
        resetTranslation()
        translateOpen = false
        translateBuffer.composer = null
    }

    /** 입력란의 번역을 확정(조합 영역만 닫기)하고 패널 상태를 처음으로 되돌린다. */
    private fun resetTranslation() {
        cancelTranslateTimers()
        if (translateRequests.shownSource != null) currentInputConnection?.let { closeComposing(it) }
        translateBuffer.clear()
        translateRequests.reset()
        showTranslateStatus(idleTranslateStatus())
        refreshTranslateSource()
    }

    private fun cancelTranslateTimers() {
        mainHandler.removeCallbacks(translateDebounce)
        mainHandler.removeCallbacks(translateRetry)
        translateRetrySource = null
    }

    private fun idleTranslateStatus(): TranslateStatus =
        if (translateGeminiBlocked) TranslateStatus.BLOCKED else TranslateStatus.IDLE

    /** 번역 패널의 키 처리. 처리했으면 true — 아니면(시프트·기호·언어 키 등) 평소대로 처리한다. */
    private fun handleTranslateKey(key: Key): Boolean {
        val view = container?.keyboardView ?: return false
        when (key.type) {
            KeyType.CHAR, KeyType.GHOST -> {
                val c = key.char
                if (!rotateTranslateSymbol(key)) {
                    val accepted = if (mode == LayoutMode.KOREAN && isComposerInput(c)) {
                        translateBuffer.inputJamo(c, System.currentTimeMillis())
                    } else {
                        translateBuffer.inputChar(c)
                    }
                    if (!accepted) {
                        onTranslateSourceFull()
                        return true
                    }
                }
                if (view.shifted && !view.capsLock) view.shifted = false
                onTranslateSourceChanged()
            }
            KeyType.SPACE -> {
                symbolCycle = null
                // 천지인 옵션: 조합 중 첫 스페이스바는 조합만 끊는다 (앱 입력과 같게).
                if (settings.chunjiinSpaceCommits && mode == LayoutMode.KOREAN &&
                    settings.koreanLayout == KoreanLayoutType.CHUNJIIN && translateBuffer.isComposing
                ) {
                    translateBuffer.flushComposer()
                } else if (!translateBuffer.inputChar(' ')) {
                    onTranslateSourceFull()
                    return true
                }
                onTranslateSourceChanged()
            }
            KeyType.DELETE -> {
                symbolCycle = null
                // 원문이 비어 있으면 앱에서 평소처럼 지운다.
                if (!translateBuffer.backspace()) return false
                onTranslateSourceChanged()
            }
            KeyType.ENTER -> onTranslateEnter()
            else -> return false
        }
        return true
    }

    /** 기호 키 연타(. → , → ? → !)를 원문 버퍼에서 처리한다. 앱 입력의 [rotateSymbolKey]와 같은 규칙. */
    private fun rotateTranslateSymbol(key: Key): Boolean {
        val cycle = symbolCycleOf(key) ?: run {
            symbolCycle = null
            return false
        }
        val now = System.currentTimeMillis()
        if (cycle == symbolCycle && now - symbolCycleTime < settings.multiTapDelayMs &&
            translateBuffer.lastChar() == cycle[symbolCycleIndex]
        ) {
            symbolCycleIndex = (symbolCycleIndex + 1) % cycle.length
            translateBuffer.replaceLast(cycle[symbolCycleIndex])
            symbolCycleTime = now
            return true
        }
        symbolCycle = cycle
        symbolCycleIndex = 0
        symbolCycleTime = now
        return false
    }

    private fun onTranslateSourceChanged() {
        cancelTranslateTimers()
        translateRequests.confirmPending = false
        refreshTranslateSource()
        if (translateBuffer.isEmpty()) {
            // 원문을 다 지웠으면 입력란의 번역도 지운다.
            if (translateRequests.shownSource != null) currentInputConnection?.let { commit(it, "") }
            translateRequests.reset()
            showTranslateStatus(idleTranslateStatus())
            return
        }
        if (translateGeminiBlocked) return
        mainHandler.postDelayed(translateDebounce, TRANSLATE_DEBOUNCE_MS)
    }

    /** 원문이 [TranslateSourceBuffer.MAX_LENGTH]에 닿아 키를 버렸다 — 이유만 보여준다 (번역·요청은 그대로). */
    private fun onTranslateSourceFull() {
        symbolCycle = null
        showTranslateStatus(TranslateStatus.TEXT_TOO_LONG)
    }

    private fun onTranslateEnter() {
        val source = translateBuffer.text
        when (translateRequests.enterAction(source)) {
            TranslateRequestState.EnterAction.EDITOR_ACTION -> {
                onEnter()
                updateAutoCapitalize()
            }
            TranslateRequestState.EnterAction.CONFIRM -> resetTranslation()
            TranslateRequestState.EnterAction.CONFIRM_ON_RESULT -> translateRequests.confirmPending = true
            TranslateRequestState.EnterAction.TRANSLATE_THEN_CONFIRM -> requestTranslation(confirm = true)
        }
    }

    private fun requestTranslation(confirm: Boolean) {
        cancelTranslateTimers()
        if (!translateOpen) return
        val source = translateBuffer.text
        if (source.isEmpty()) return
        if (translateGeminiBlocked) {
            translateRequests.confirmPending = false
            showTranslateStatus(TranslateStatus.BLOCKED)
            return
        }
        val engine = translateEngine
        val backend = backendFor(engine)
        val id = translateRequests.issue(source)
        // 콜백이 translate 안에서 곧장 불릴 수 있으므로 상태를 먼저 정한다.
        translateRequests.confirmPending = confirm
        showTranslateStatus(TranslateStatus.WORKING)
        backend.translate(source, translateSource, translateTarget) { result ->
            onTranslationResult(id, engine, source, result)
        }
    }

    private fun onTranslationResult(
        id: Long,
        engine: TranslationEngine,
        source: String,
        result: TranslationResult,
    ) {
        if (!translateOpen || !translateRequests.complete(id)) return
        when (result) {
            is TranslationResult.Success -> {
                if (unusableEngine?.first == engine) {
                    unusableEngine = null
                    updateTranslateButton()
                }
                val ic = currentInputConnection ?: run {
                    translateRequests.confirmPending = false
                    showTranslateStatus(idleTranslateStatus())
                    return
                }
                if (!translationRegionIntact(ic)) return
                setComposing(ic, result.text)
                translateRequests.onShown(source)
                showTranslateStatus(TranslateStatus.IDLE)
                if (translateRequests.confirmPending && source == translateBuffer.text) resetTranslation()
            }
            is TranslationResult.Failure -> {
                // 실패 상세(detail)와 원문은 기록하지 않는다 — 입력한 글이 로그에 남을 수 있다.
                val status = TranslateStatus.of(result.reason)
                if (status == null) {
                    // 다른 요청이 대신했다 — 조용히 넘어간다.
                    translateRequests.confirmPending = false
                    showTranslateStatus(idleTranslateStatus())
                    return
                }
                if (status == TranslateStatus.BLOCKED && engine == TranslationEngine.GEMINI_NANO) {
                    geminiBlockFlag.markBlocked()
                    translateGeminiBlocked = true
                }
                if (status.remembersForSession) {
                    unusableEngine = engine to status
                    updateTranslateButton()
                }
                showTranslateStatus(status)
                if (status.retries) {
                    translateRetrySource = source
                    mainHandler.postDelayed(translateRetry, TRANSLATE_RETRY_MS)
                } else {
                    translateRequests.confirmPending = false
                }
            }
        }
    }

    /**
     * 입력란의 번역(앱 조합 영역)이 아직 커서 바로 앞에 있는지. 앱이 조합 영역을 스스로 닫았거나
     * 커서가 옮겨졌으면 새 번역을 쓰면 옛 번역 뒤에 하나 더 들어가므로, 패널 상태를 비우고 false.
     */
    private fun translationRegionIntact(ic: InputConnection): Boolean {
        if (translateRequests.shownSource == null) return true
        val sent = selection.composing
        val moved = !selection.compositionDropped && sent.isNotEmpty() &&
            ic.getTextBeforeCursor(sent.length, 0)?.let { it.toString() != sent } == true
        if (!selection.compositionDropped && !moved) return true
        ic.finishComposingText()
        selection.abandonComposition(cursorKnown = !moved)
        translateRequests.reset()
        translateBuffer.clear()
        cancelTranslateTimers()
        showTranslateStatus(idleTranslateStatus())
        refreshTranslateSource()
        return false
    }

    private fun swapTranslationLanguages() {
        if (!translateOpen) return
        val source = translateSource
        translateSource = translateTarget
        translateTarget = source
        settings.translationSource = translateSource
        settings.translationTarget = translateTarget
        refreshTranslatePair()
        refreshTranslateSource()
        if (!translateBuffer.isEmpty() && !translateGeminiBlocked) requestTranslation(confirm = false)
    }

    private fun refreshTranslateSource() {
        container?.updateTranslateSource(
            translateBuffer.text,
            getString(R.string.translate_hint, translateSource.displayName()),
        )
    }

    private fun refreshTranslatePair() {
        container?.updateTranslatePair("${translateSource.displayName()} → ${translateTarget.displayName()}")
    }

    private fun showTranslateStatus(status: TranslateStatus) {
        val text = when (status) {
            TranslateStatus.IDLE -> getString(R.string.translate_status_idle)
            TranslateStatus.WORKING -> getString(R.string.translate_status_working)
            TranslateStatus.DOWNLOADING -> getString(R.string.translate_status_downloading)
            TranslateStatus.NEEDS_DOWNLOAD -> getString(R.string.translate_status_needs_download)
            TranslateStatus.NEEDS_WIFI -> getString(R.string.translate_status_needs_wifi)
            TranslateStatus.ENGINE_UNAVAILABLE ->
                getString(R.string.translate_status_unavailable, engineName(translateEngine))
            TranslateStatus.LANGUAGE_UNSUPPORTED -> getString(R.string.translate_status_unsupported_pair)
            TranslateStatus.BLOCKED -> getString(R.string.translate_status_blocked)
            TranslateStatus.ADDON_MISSING -> getString(R.string.translate_status_addon_missing)
            TranslateStatus.BUSY -> getString(R.string.translate_status_busy)
            TranslateStatus.TEXT_TOO_LONG -> getString(R.string.translate_status_too_long)
            TranslateStatus.ERROR -> getString(R.string.translate_status_error)
        }
        val emphasized = status != TranslateStatus.IDLE && status != TranslateStatus.WORKING &&
            status != TranslateStatus.DOWNLOADING
        container?.updateTranslateStatus(text, emphasized)
    }

    private fun engineName(engine: TranslationEngine): String = getString(
        when (engine) {
            TranslationEngine.SYSTEM -> R.string.translate_engine_system
            TranslationEngine.MLKIT -> R.string.translate_engine_mlkit
            TranslationEngine.GEMINI_NANO -> R.string.translate_engine_gemini
        },
    )
}

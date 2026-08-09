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
import dev.badalab.yeonfeel.settings.SettingsActivity

class YeonfeelImeService : InputMethodService() {

    private val dubeolComposer = HangulComposer()
    private val chunjiinComposer = ChunjiinComposer()
    private val naratgulComposer = NaratgulComposer()
    private var composer: KoreanComposer = dubeolComposer
    private val clipboardHistory = ClipboardHistory()
    private lateinit var clipboardStore: SecureClipboardStore
    private lateinit var touchStats: dev.badalab.yeonfeel.debug.TouchStatsStore
    private var container: KeyboardContainerView? = null
    private var mode = LayoutMode.KOREAN

    /** 비밀번호류 입력란 여부. 타점 수집·키 미리보기·MZ 모드를 끈다. */
    private var sensitiveField = false
    private lateinit var settings: KeyboardSettings
    private lateinit var clipboardManager: ClipboardManager

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { captureClip() }
    private val ioExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /** 클립보드 복호화는 느리므로 백그라운드에서 읽고 메인에서 반영한다. */
    private fun reloadStoresAsync() {
        ioExecutor.execute {
            val entries = clipboardStore.load()
            mainHandler.post {
                clipboardHistory.restore(entries)
                touchStats.reload()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        settings = KeyboardSettings(this)
        clipboardStore = SecureClipboardStore(this)
        touchStats = dev.badalab.yeonfeel.debug.TouchStatsStore(this)
        reloadStoresAsync()
        clipboardManager = getSystemService(ClipboardManager::class.java)
        clipboardManager.addPrimaryClipChangedListener(clipListener)
    }

    override fun onDestroy() {
        clipboardManager.removePrimaryClipChangedListener(clipListener)
        ioExecutor.shutdown()
        super.onDestroy()
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
        view.keyboardView.onTapRecorded = { key, ax, ay, rx, ry ->
            if (settings.touchStatsEnabled && !sensitiveField && key.type != KeyType.SPACER) {
                val keyId = when (key.type) {
                    KeyType.CHAR, KeyType.GHOST -> key.char.toString()
                    else -> key.type.name
                }
                // 자판마다 키 위치가 달라 보드별로 분리 저장한다.
                val kv = view.keyboardView
                val board = when (kv.mode) {
                    LayoutMode.KOREAN -> "KO_" + settings.koreanLayout.name
                    LayoutMode.ENGLISH -> "EN_" + settings.englishLayout.name
                    LayoutMode.SYMBOLS ->
                        (if (kv.compactSymbols) "SYMC_" else "SYM_") + kv.symbolsPage
                }
                touchStats.add(
                    dev.badalab.yeonfeel.debug.TouchStatsStore.Sample(board, keyId, ax, ay, rx, ry),
                )
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
        sensitiveField = isPasswordInput(info?.inputType ?: 0)
        composer.reset()
        composer = when (settings.koreanLayout) {
            KoreanLayoutType.CHUNJIIN -> chunjiinComposer
            KoreanLayoutType.NARATGUL, KoreanLayoutType.NARATGUL_CENTER -> naratgulComposer
            else -> dubeolComposer
        }
        dubeolComposer.doubleTapIotation = settings.koreanLayout == KoreanLayoutType.DANMOEUM
        dubeolComposer.doubleTapDoubling = settings.koreanLayout == KoreanLayoutType.DANMOEUM
        val multiTapDelay = settings.multiTapDelayMs.toLong()
        dubeolComposer.multiTapTimeoutMs = multiTapDelay
        // 천지인 자동 방식: 연타 대기가 사실상 무한 — 같은 키는 스페이스바로 끊기 전까지 계속 사이클.
        chunjiinComposer.multiTapTimeoutMs =
            if (settings.chunjiinSpaceCommits) Long.MAX_VALUE else multiTapDelay
        naratgulComposer.multiTapTimeoutMs = multiTapDelay
        // 설정에서 꺼진 언어가 현재 모드면 켜진 언어로 강제 전환한다.
        if (mode == LayoutMode.ENGLISH && !settings.englishEnabled) mode = LayoutMode.KOREAN
        if (mode == LayoutMode.KOREAN && !settings.koreanEnabled) mode = LayoutMode.ENGLISH
        container?.let {
            it.applySettings(settings)
            // 비밀번호 입력란에서는 어깨너머·화면 녹화로 노출되는 키 미리보기를 끈다.
            if (sensitiveField) it.keyboardView.keyPreviewEnabled = false
            it.keyboardView.mode = mode
            it.keyboardView.shifted = false
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
        if (composer.isComposing && candidatesStart >= 0 &&
            (newSelStart < candidatesStart || newSelStart > candidatesEnd)
        ) {
            composer.reset()
            currentInputConnection?.finishComposingText()
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        finishComposition()
        touchStats.flush()
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
        clipboardStore.save(clipboardHistory.entries(System.currentTimeMillis()))
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
                onChar(key.char)
                if (view.shifted) view.shifted = false
                updateAutoCapitalize()
            }
            KeyType.SHIFT -> view.shifted = !view.shifted
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
                    // 3x4 나랏글 계열에서 들어온 기호 키보드는 컴팩트 배치를 쓴다.
                    view.compactSymbols = mode == LayoutMode.KOREAN &&
                        settings.koreanLayout in setOf(
                            KoreanLayoutType.NARATGUL,
                            KoreanLayoutType.NARATGUL_CENTER,
                            KoreanLayoutType.CHUNJIIN,
                        )
                }
                view.mode = if (view.mode == LayoutMode.SYMBOLS) mode else LayoutMode.SYMBOLS
                view.shifted = false
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

    /** MZ 모드가 켜져 있으면 ㅋ 3연타부터 30% 확률로 ㅎ을 대신 입력한다. */
    private fun applyMzMode(c: Char): Char {
        if (!settings.mzModeEnabled || sensitiveField) return c
        if (c == 'ㅋ') {
            kiekStreak++
            if (kiekStreak >= 3 && kotlin.random.Random.nextFloat() < 0.3f) return 'ㅎ'
        } else {
            kiekStreak = 0
        }
        return c
    }

    private fun onChar(rawChar: Char) {
        val c = if (mode == LayoutMode.KOREAN) applyMzMode(rawChar) else rawChar
        val ic = currentInputConnection ?: return
        if (mode == LayoutMode.KOREAN && isComposerInput(c)) {
            val result = composer.input(c, System.currentTimeMillis())
            ic.beginBatchEdit()
            if (result.commit.isNotEmpty()) ic.commitText(result.commit, 1)
            ic.setComposingText(result.composing, 1)
            ic.endBatchEdit()
        } else {
            finishComposition()
            ic.commitText(c.toString(), 1)
        }
    }

    private var lastSpaceTime = 0L
    private val doubleSpaceMs = 500L

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
        ic.commitText(" ", 1)
        lastSpaceTime = now
    }

    /** 직전이 "글자 + 공백"일 때만 마침표 축약을 적용한다. */
    private fun canDoubleSpacePeriod(ic: android.view.inputmethod.InputConnection): Boolean {
        val before = ic.getTextBeforeCursor(2, 0) ?: return false
        return before.length == 2 && before[1] == ' ' && before[0].isLetterOrDigit()
    }

    /** 영문 모드에서 문장 시작이면 Shift를 자동으로 켠다. */
    private fun updateAutoCapitalize() {
        if (!settings.autoCapitalize || mode != LayoutMode.ENGLISH) return
        val view = container?.keyboardView ?: return
        if (view.mode != LayoutMode.ENGLISH) return
        val ic = currentInputConnection ?: return
        val inputType = currentInputEditorInfo?.inputType ?: 0
        val caps = ic.getCursorCapsMode(inputType or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        view.shifted = caps != 0
    }

    private fun onDelete() {
        val ic = currentInputConnection ?: return
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

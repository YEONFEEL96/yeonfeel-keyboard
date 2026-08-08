package dev.badalab.yeonfeel.ime

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
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
    private var container: KeyboardContainerView? = null
    private var mode = LayoutMode.KOREAN
    private lateinit var settings: KeyboardSettings
    private lateinit var clipboardManager: ClipboardManager

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { captureClip() }

    override fun onCreate() {
        super.onCreate()
        settings = KeyboardSettings(this)
        clipboardStore = SecureClipboardStore(this)
        clipboardHistory.restore(clipboardStore.load())
        clipboardManager = getSystemService(ClipboardManager::class.java)
        clipboardManager.addPrimaryClipChangedListener(clipListener)
    }

    override fun onDestroy() {
        clipboardManager.removePrimaryClipChangedListener(clipListener)
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        val view = KeyboardContainerView(this, callbacks)
        view.keyboardView.mode = mode
        container = view
        view.applySettings(settings)
        return view
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        composer.reset()
        composer = when (settings.koreanLayout) {
            KoreanLayoutType.CHUNJIIN -> chunjiinComposer
            KoreanLayoutType.NARATGUL -> naratgulComposer
            else -> dubeolComposer
        }
        dubeolComposer.doubleTapIotation = settings.koreanLayout == KoreanLayoutType.DANMOEUM
        // 설정에서 꺼진 언어가 현재 모드면 켜진 언어로 강제 전환한다.
        if (mode == LayoutMode.ENGLISH && !settings.englishEnabled) mode = LayoutMode.KOREAN
        if (mode == LayoutMode.KOREAN && !settings.koreanEnabled) mode = LayoutMode.ENGLISH
        container?.let {
            it.applySettings(settings)
            it.keyboardView.mode = mode
            it.keyboardView.shifted = false
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        finishComposition()
        super.onFinishInputView(finishingInput)
    }

    private val callbacks = object : KeyboardContainerView.Callbacks {
        override fun onKey(key: Key) = handleKey(key)

        override fun onPaste(text: String) {
            finishComposition()
            currentInputConnection?.commitText(text, 1)
            container?.showKeyboard()
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

    private fun handleKey(key: Key) {
        val view = container?.keyboardView ?: return
        when (key.type) {
            KeyType.CHAR -> {
                onChar(key.char)
                if (view.shifted) view.shifted = false
            }
            KeyType.SHIFT -> view.shifted = !view.shifted
            KeyType.DELETE -> onDelete()
            KeyType.SPACE -> {
                finishComposition()
                currentInputConnection?.commitText(" ", 1)
            }
            KeyType.ENTER -> onEnter()
            KeyType.LANG -> switchLanguage()
            KeyType.SYMBOLS -> {
                finishComposition()
                view.symbolsPage = 0
                view.mode = if (view.mode == LayoutMode.SYMBOLS) mode else LayoutMode.SYMBOLS
                view.shifted = false
            }
            KeyType.PAGE -> view.symbolsPage = if (view.symbolsPage == 0) 1 else 0
            KeyType.SPACER -> Unit
        }
    }

    private fun switchLanguage() {
        if (!(settings.koreanEnabled && settings.englishEnabled)) return
        val view = container?.keyboardView ?: return
        finishComposition()
        mode = if (mode == LayoutMode.ENGLISH) LayoutMode.KOREAN else LayoutMode.ENGLISH
        view.mode = mode
        view.shifted = false
    }

    /** 조합기로 보내야 하는 입력인지 — 호환/옛한글 자모, 천지인 ㆍ, 나랏글 변형 키. */
    private fun isComposerInput(c: Char): Boolean =
        HangulComposer.isHangulJamo(c) || c == ChunjiinComposer.KEY_ARAEA ||
            c == NaratgulComposer.KEY_ADD_STROKE || c == NaratgulComposer.KEY_DOUBLE

    private fun onChar(c: Char) {
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

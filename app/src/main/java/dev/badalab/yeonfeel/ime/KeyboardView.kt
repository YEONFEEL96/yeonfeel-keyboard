package dev.badalab.yeonfeel.ime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.CornerPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityEvent
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper
import dev.badalab.yeonfeel.R
import dev.badalab.yeonfeel.settings.KeyboardSettings
import dev.badalab.yeonfeel.settings.KoreanLayoutType

/**
 * 키보드 판을 직접 그리는 커스텀 뷰.
 * 레이아웃은 [KeyboardLayouts]의 행 정의를 그대로 그리며,
 * 키 입력은 [onKeyListener] 콜백으로 서비스에 전달한다.
 */
@SuppressLint("ViewConstructor")
class KeyboardView(
    context: Context,
    var onKeyListener: (Key) -> Unit,
) : View(context) {

    // TalkBack: 직접 그린 키마다 가상 접근성 노드를 만들어 탐색·클릭을 지원한다.
    private val a11yHelper = KeyboardA11yHelper()

    init {
        ViewCompat.setAccessibilityDelegate(this, a11yHelper)
    }

    override fun dispatchHoverEvent(event: MotionEvent): Boolean =
        a11yHelper.dispatchHoverEvent(event) || super.dispatchHoverEvent(event)

    var mode: LayoutMode = LayoutMode.KOREAN
        set(value) {
            field = value
            relayoutKeys()
        }

    var shifted: Boolean = false
        set(value) {
            field = value
            relayoutKeys()
        }

    /** 시프트 더블탭 고정(Caps Lock): 글자를 입력해도 시프트가 풀리지 않는다. */
    var capsLock: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var theme: KeyboardTheme = KeyboardTheme.DARK
        set(value) {
            field = value
            applyTheme()
            invalidate()
        }

    /** 키 영역 높이(dp). 여백 조정의 상하 핸들로 조절된다. */
    var heightDp: Int = KeyboardSettings.HEIGHT_DEFAULT
        set(value) {
            field = value
            requestLayout()
        }

    var showNumberRow: Boolean = true
        set(value) {
            field = value
            relayoutKeys()
        }

    /** 특수문자 페이지 (0 = 1/2, 1 = 2/2). */
    var symbolsPage: Int = 0
        set(value) {
            field = value
            relayoutKeys()
        }

    /** 한/영 키 표시 여부. 숨기면 스페이스바가 넓어진다. */
    var showLangKey: Boolean = true
        set(value) {
            field = value
            relayoutKeys()
        }

    var languageSwipeEnabled: Boolean = false
    var onLanguageSwipe: (() -> Unit)? = null

    var languageName: String = "한국어"
    var nextLanguageName: String = "English"

    var languageList: List<String> = listOf("한국어", "English")
    var currentLanguageIndex: Int = 0
    var onLanguageSelected: ((Int) -> Unit)? = null

    /** 기억형 기호 키에서 변형 팝업으로 기호를 골랐을 때 (저장용). */
    var onVariantPicked: ((Char) -> Unit)? = null

    /** 스페이스바 왼쪽 기호 키 팝업의 클립보드·설정 단축 셀 선택. */
    var onShortcutSelected: ((Char) -> Unit)? = null

    private class LangListState(
        val pointerId: Int,
        val panel: RectF,
        val rows: List<RectF>,
        var selected: Int,
    )

    private var langKeyPointerId = -1
    private var langListState: LangListState? = null
    private var langListPopup: android.widget.PopupWindow? = null
    private val langListRunnable = Runnable { showLanguageListPopup() }

    private inner class LangListContent : View(context) {
        private val bgPath = Path()

        override fun onDraw(canvas: Canvas) {
            val state = langListState ?: return
            canvas.translate(-state.panel.left, -state.panel.top)
            val inset = previewBorderPaint.strokeWidth / 2f + 0.5f
            buildSmoothRoundRect(
                bgPath,
                state.panel.left + inset,
                state.panel.top + inset,
                state.panel.right - inset,
                state.panel.bottom - inset,
                dp(18f),
            )
            canvas.drawPath(bgPath, previewBgPaint)
            canvas.drawPath(bgPath, previewBorderPaint)
            state.rows.forEachIndexed { index, row ->
                if (index == state.selected) {
                    canvas.drawRoundRect(row, dp(10f), dp(10f), iconFillPaint)
                }
                langTextPaint.color = if (index == state.selected) 0xFFFFFFFF.toInt() else theme.text
                val y = row.centerY() - (langTextPaint.ascent() + langTextPaint.descent()) / 2
                canvas.drawText(languageList[index], row.centerX(), y, langTextPaint)
            }
            langTextPaint.color = theme.text
        }
    }

    private fun showLanguageListPopup() {
        if (langListPopup != null) return // 이미 떠 있으면 두 번째를 만들지 않는다 (누수 방지)
        if (langKeyPointerId == -1 || languageList.size < 2) return
        val key = pressedByPointer[langKeyPointerId]?.key ?: return
        val bound = keyBounds.firstOrNull { it.key == key } ?: return
        val rowHeight = dp(42f)
        val panelWidth = dp(136f)
        val pad = dp(6f)
        val panelHeight = rowHeight * languageList.size + pad * 2
        val left = (bound.rect.centerX() - panelWidth / 2)
            .coerceIn(dp(2f), maxOf(dp(2f), width - panelWidth - dp(2f)))
        val bottom = bound.rect.top - dp(6f)
        val panel = RectF(left, bottom - panelHeight, left + panelWidth, bottom)
        val rows = languageList.indices.map { index ->
            RectF(
                panel.left + pad,
                panel.top + pad + index * rowHeight + dp(2f),
                panel.right - pad,
                panel.top + pad + (index + 1) * rowHeight - dp(2f),
            )
        }
        langListState = LangListState(langKeyPointerId, panel, rows, currentLanguageIndex)
        val location = IntArray(2)
        getLocationInWindow(location)
        val window = android.widget.PopupWindow(
            LangListContent(),
            panelWidth.toInt(),
            panelHeight.toInt(),
        ).apply {
            isClippingEnabled = false
            isTouchable = false
            isFocusable = false
        }
        window.showAtLocation(
            this,
            Gravity.NO_GRAVITY,
            location[0] + panel.left.toInt(),
            location[1] + panel.top.toInt(),
        )
        langListPopup = window
        performKeyHaptic()
    }

    private fun dismissLanguageListPopup() {
        repeatHandler.removeCallbacks(langListRunnable)
        langListPopup?.dismiss()
        langListPopup = null
        langListState = null
        langKeyPointerId = -1
    }

    private var langPopupWindow: android.widget.PopupWindow? = null
    private var langDragOffset = 0f
    private var spaceLastDx = 0f
    private val langPopupRunnable = Runnable { showLanguagePopup() }

    /**
     * 스페이스바 홀드 언어 팝업 내용. 좌우 화살표와 G2 연속 곡률 모서리를 쓰고,
     * 임계값을 넘으면 다음 언어가 중앙에 온전히 표시된다 — 떼면 그 언어로 전환.
     */
    private inner class LangPopupContent : View(context) {
        private val bgPath = Path()

        override fun onDraw(canvas: Canvas) {
            // 테두리 선이 뷰 경계에서 잘리지 않도록 선 두께의 절반만큼 안쪽에 그린다.
            val inset = previewBorderPaint.strokeWidth / 2f + 0.5f
            buildSmoothRoundRect(bgPath, inset, inset, width - inset, height - inset, dp(24f))
            canvas.drawPath(bgPath, previewBgPaint)
            canvas.drawPath(bgPath, previewBorderPaint)

            val willSwitch = kotlin.math.abs(spaceLastDx) > dp(SPACE_SWIPE_THRESHOLD_DP)
            val label = if (willSwitch) nextLanguageName else languageName
            langTextPaint.color = theme.text
            val y = height / 2f - (langTextPaint.ascent() + langTextPaint.descent()) / 2
            canvas.drawText(label, width / 2f, y, langTextPaint)

            val cy = height / 2f
            val arrow = dp(5f)
            val leftX = dp(16f)
            val rightX = width - dp(16f)
            canvas.drawLine(leftX + arrow, cy - arrow, leftX, cy, spaceGlyphPaint)
            canvas.drawLine(leftX, cy, leftX + arrow, cy + arrow, spaceGlyphPaint)
            canvas.drawLine(rightX - arrow, cy - arrow, rightX, cy, spaceGlyphPaint)
            canvas.drawLine(rightX, cy, rightX - arrow, cy + arrow, spaceGlyphPaint)
        }
    }

    /**
     * G2 연속 곡률 라운드 사각형: 코너 곡선의 제어점을 직선 변 위에 두어
     * 접선·곡률이 연속으로 이어진다 (원호 라운드의 '뭉친' 모서리 방지).
     */
    private fun buildSmoothRoundRect(path: Path, l: Float, t: Float, r: Float, b: Float, radius: Float) {
        val len = minOf(radius, (r - l) / 2f - 1f, (b - t) / 2f - 1f)
        // 제어점을 변 위 len*k 지점에 둔다 (0.45 ≈ 원호와 같은 곡률).
        val k = 0.45f
        path.reset()
        path.moveTo(l + len, t)
        path.lineTo(r - len, t)
        path.cubicTo(r - len * k, t, r, t + len * k, r, t + len)
        path.lineTo(r, b - len)
        path.cubicTo(r, b - len * k, r - len * k, b, r - len, b)
        path.lineTo(l + len, b)
        path.cubicTo(l + len * k, b, l, b - len * k, l, b - len)
        path.lineTo(l, t + len)
        path.cubicTo(l, t + len * k, l + len * k, t, l + len, t)
        path.close()
    }

    private val langTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = sp(16f)
    }

    private fun showLanguagePopup() {
        if (langPopupWindow != null) return // 분할 스페이스 등으로 두 번 호출돼도 하나만 띄운다
        if (spacePointerId == -1 || variantPopup != null) return
        val spaceKey = pressedByPointer[spacePointerId]?.key ?: return
        val bound = keyBounds.firstOrNull { it.key == spaceKey } ?: return
        langTextPaint.color = theme.text
        langDragOffset = 0f
        val popupWidth = dp(150f).toInt()
        val popupHeight = dp(44f).toInt()
        val location = IntArray(2)
        getLocationInWindow(location)
        val x = (bound.rect.centerX() - popupWidth / 2f).toInt()
            .coerceIn(dp(2f).toInt(), maxOf(dp(2f).toInt(), width - popupWidth - dp(2f).toInt()))
        val y = (bound.rect.top - popupHeight - dp(8f)).toInt()
        val window = android.widget.PopupWindow(LangPopupContent(), popupWidth, popupHeight).apply {
            isClippingEnabled = false
            isTouchable = false
            isFocusable = false
        }
        window.showAtLocation(this, Gravity.NO_GRAVITY, location[0] + x, location[1] + y)
        langPopupWindow = window
        performKeyHaptic()
    }

    private fun dismissLanguagePopup() {
        repeatHandler.removeCallbacks(langPopupRunnable)
        langPopupWindow?.dismiss()
        langPopupWindow = null
        langDragOffset = 0f
    }

    /** 타점 수집 콜백: (키, 키보드 정규화 x·y, 키 중심 대비 상대 x·y). */
    var onTapRecorded: ((Key, Float, Float, Float, Float) -> Unit)? = null

    var koreanLayout: KoreanLayoutType = KoreanLayoutType.DUBEOLSIK
        set(value) {
            field = value
            relayoutKeys()
        }

    var englishLayout: dev.badalab.yeonfeel.settings.EnglishLayoutType =
        dev.badalab.yeonfeel.settings.EnglishLayoutType.QWERTY
        set(value) {
            field = value
            relayoutKeys()
        }

    /** 타점 개인화 보정 사용 여부 (경계 근처 CHAR 키만 재판정). */
    var touchModelEnabled: Boolean = false
    var touchStatsProvider: ((String) -> Map<String, TouchModel.KeyStat>)? = null

    /** 타점 저장·보정에 쓰는 현재 자판 보드 식별자. */
    fun currentBoardId(): String = when (mode) {
        LayoutMode.KOREAN -> "KO_" + koreanLayout.name
        LayoutMode.ENGLISH -> "EN_" + englishLayout.name
        LayoutMode.SYMBOLS -> (if (compactSymbols) "SYMC_" else "SYM_") + symbolsPage
        LayoutMode.NUMBER -> "NUM"
    }

    /** 키 라벨 글자 크기 배율 (작게/보통/크게 설정). */
    var fontScale: Float = 1f
        set(value) {
            field = value
            applyFontScale()
            invalidate()
        }

    private fun applyFontScale() {
        textPaint.textSize = sp(20f) * fontScale
        bigTextPaint.textSize = sp(25f) * fontScale
        smallTextPaint.textSize = sp(13f) * fontScale
        hintTextPaint.textSize = sp(9.5f) * fontScale
        previewTextPaint.textSize = sp(30f) * fontScale
        enterActionPaint.textSize = sp(14f) * fontScale
    }

    /** 길게 누르기 판정 시간(ms). 접근성 설정에서 조절한다 (변형 팝업·숫자·언어 목록 공통). */
    var longPressDelayMs: Long = 350L

    /** 분할 키보드: 행을 좌우로 나누고 중앙에 빈 공간을 둔다 (3x4 자판 제외). */
    var splitEnabled: Boolean = false
        set(value) {
            field = value
            relayoutKeys()
        }

    /** 실제로 분할 배치가 적용되는지 — 3x4 자판은 분할하지 않는다. */
    val splitActive: Boolean get() = splitEnabled && !is3x4Board()

    /** 분할 중앙 간격 비율 (행 전체 폭 가중치 대비). 레이아웃 조정 핸들로 바뀐다. */
    var splitGapRatio: Float = 0.45f
        set(value) {
            field = value
            relayoutKeys()
        }

    /** 3x4 자판(나랏글 계열)에서 기호 키보드를 컴팩트 배치로 보여줄지. */
    var compactSymbols: Boolean = false
        set(value) {
            field = value
            relayoutKeys()
        }

    var shiftNumberRowSymbols: Boolean = true
        set(value) {
            field = value
            relayoutKeys()
        }

    /** 키캡 배경 표시. 끄면 글자만 그리는 플랫 스타일 (눌린 키만 하이라이트). */
    var showKeyBackground: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    var hapticEnabled: Boolean = true

    /** 햅틱 세기 단계 (1~5). Composition 프리미티브의 scale 로 환산된다. */
    var hapticStrength: Int = 3

    var soundEnabled: Boolean = false

    private val audioManager: android.media.AudioManager? =
        context.getSystemService(android.media.AudioManager::class.java)

    private fun performKeySound(key: Key) {
        if (!soundEnabled) return
        val effect = when (key.type) {
            KeyType.DELETE -> android.media.AudioManager.FX_KEYPRESS_DELETE
            KeyType.SPACE -> android.media.AudioManager.FX_KEYPRESS_SPACEBAR
            KeyType.ENTER -> android.media.AudioManager.FX_KEYPRESS_RETURN
            else -> android.media.AudioManager.FX_KEYPRESS_STANDARD
        }
        runCatching { audioManager?.playSoundEffect(effect, KEY_SOUND_VOLUME) }
    }

    var keyPreviewEnabled: Boolean = true


    private val vibrator: android.os.Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(android.os.VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(android.os.Vibrator::class.java)
        }

    /**
     * 키 입력 햅틱. LRA 기기에서는 Composition 프리미티브 CLICK 에 [HAPTIC_SCALE]
     * 세기를 줘 OEM 튜닝을 유지하면서 조금 약하게 낸다. 미지원 기기는 약한 프리셋
     * EFFECT_TICK(CLICK보다 가벼움)으로, 더 구형은 짧은 진동으로 폴백한다.
     * performHapticFeedback(KEYBOARD_TAP)은 Vivo 등에서 무음이라 쓰지 않는다.
     * 켜고 끄기는 우리 토글(hapticEnabled)이 담당한다.
     */
    private fun performKeyHaptic() {
        if (!hapticEnabled) return
        val vib = vibrator ?: return
        runCatching {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                    vib.areAllPrimitivesSupported(
                        android.os.VibrationEffect.Composition.PRIMITIVE_CLICK,
                    ) ->
                    vib.vibrate(
                        android.os.VibrationEffect.startComposition()
                            .addPrimitive(
                                android.os.VibrationEffect.Composition.PRIMITIVE_CLICK,
                                hapticStrength.coerceIn(1, 5) * 0.2f,
                            )
                            .compose(),
                    )
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                    vib.vibrate(
                        android.os.VibrationEffect.createPredefined(
                            android.os.VibrationEffect.EFFECT_TICK,
                        ),
                    )
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ->
                    vib.vibrate(android.os.VibrationEffect.createOneShot(10L, 100))
                else -> {
                    @Suppress("DEPRECATION")
                    vib.vibrate(10L)
                }
            }
        }
    }

    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val specialKeyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pressedPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val spaceGlyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    // 기능 키 아이콘(시프트·삭제·엔터)은 선 두께·크기를 통일해 직접 그린다.
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val iconFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = sp(20f)
    }
    private val bigTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = sp(25f)
    }
    private val smallTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = sp(13f)
    }

    /** NUMBER 모드 숫자 키패드 변형 비트(전화·소수점·부호). 바뀌면 재배치. */
    var numberVariant: Int = 0
        set(value) {
            if (field == value) return
            field = value
            relayoutKeys()
        }

    /** 엔터 키에 표시할 동작 라벨(다음/검색/완료 등). null이면 ⏎ 아이콘을 그린다. */
    var enterActionLabel: CharSequence? = null
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }
    private val enterActionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = sp(14f)
        color = ACCENT
    }
    private val hintTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
        textSize = sp(9.5f)
    }
    private val previewBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val previewBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val previewTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = sp(30f)
    }
    private val previewSelectedTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = sp(20f)
        color = 0xFFFFFFFF.toInt()
    }

    init {
        applyTheme()
    }

    private fun applyTheme() {
        keyPaint.color = theme.key
        specialKeyPaint.color = theme.specialKey
        pressedPaint.color = theme.pressed
        textPaint.color = theme.text
        bigTextPaint.color = theme.text
        smallTextPaint.color = theme.text
        spaceGlyphPaint.color = theme.subText
        hintTextPaint.color = theme.subText
        iconPaint.color = theme.text
        iconPaint.strokeWidth = dp(1.8f)
        iconPaint.pathEffect = CornerPathEffect(dp(1.5f))
        iconFillPaint.color = ACCENT
        previewBgPaint.color = theme.key
        previewBorderPaint.color = theme.subText and 0x60FFFFFF.toInt()
        previewBorderPaint.strokeWidth = dp(1f)
        previewTextPaint.color = theme.text
        spaceGlyphPaint.strokeWidth = dp(2f)
        theme.keyBorder?.let {
            borderPaint.color = it
            borderPaint.strokeWidth = dp(1.5f)
        }
    }

    private data class KeyBounds(val key: Key, val rect: RectF)

    private var keyBounds: List<KeyBounds> = emptyList()
    private var boundsDirty = true

    /** 키 배치가 바뀌는 설정 변경 시에만 좌표를 다시 계산한다 (매 프레임 재계산 방지). */
    private fun relayoutKeys() {
        boundsDirty = true
        invalidate()
    }

    // 멀티터치: 포인터별로 눌린 키를 추적해야 빠른 타이핑(이전 키를 떼기 전에
    // 다음 키를 누르는 패턴)에서 글자가 씹히지 않는다.
    // 눌린 키를 위치(KeyBounds)로 저장한다 — 값이 같은 키가 둘(콤마 등)이어도 구분된다.
    private val pressedByPointer = HashMap<Int, KeyBounds>()
    private val downXByPointer = HashMap<Int, Float>()
    private var spacePointerId = -1
    private var spaceSwiped = false
    private var deletePointerId = -1

    private class VariantPopupState(
        val pointerId: Int,
        val options: List<String>,
        val panel: RectF,
        val cells: List<RectF>,
        val startX: Float,
        var selected: Int = 0,
    )

    private data class PendingVariant(val pointerId: Int, val key: Key, val rect: RectF)

    private var pendingVariant: PendingVariant? = null
    private var variantPopup: VariantPopupState? = null
    private var variantPopupWindow: android.widget.PopupWindow? = null
    private val longPressRunnable = Runnable { showVariantPopup() }
    private val digitLongPressRunnable = Runnable { commitPendingDigit() }

    /** 천지인·나랏글 등 3x4 자판(컴팩트 기호 포함)인지 — 키가 커서 누른 키 미리보기를 생략한다. */
    private fun is3x4Board(): Boolean = when (mode) {
        LayoutMode.SYMBOLS -> compactSymbols
        LayoutMode.KOREAN -> when (koreanLayout) {
            KoreanLayoutType.CHUNJIIN,
            KoreanLayoutType.NARATGUL,
            KoreanLayoutType.NARATGUL_CENTER,
            -> true
            else -> false
        }
        else -> false
    }

    /** 3x4 자판에서 이 키를 길게 눌렀을 때 입력될 숫자 (없으면 null). */
    private fun naratgulDigit(key: Key): Char? {
        if (mode != LayoutMode.KOREAN || key.type != KeyType.CHAR) return null
        return when (koreanLayout) {
            KoreanLayoutType.NARATGUL, KoreanLayoutType.NARATGUL_CENTER -> NARATGUL_DIGITS[key.char]
            KoreanLayoutType.CHUNJIIN -> CHUNJIIN_DIGITS[key.char]
            else -> null
        }
    }

    private fun isLandscape(): Boolean =
        resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    /** 가로모드 QWERTY류는 숫자 열을 없애고 맨 윗열 길게 누르기로 숫자를 입력한다. */
    private fun effectiveShowNumberRow(): Boolean =
        showNumberRow && !(isLandscape() && mode != LayoutMode.SYMBOLS && !is3x4Board())

    // 맨 윗열 문자 → 숫자 (가로모드 전용). 자판이 바뀔 때마다 윗열에서 다시 만든다.
    private var landscapeTopDigits: Map<Char, Char> = emptyMap()

    private fun landscapeTopDigit(key: Key): Char? =
        if (key.type == KeyType.CHAR) landscapeTopDigits[key.char] else null

    /** 각 키 우상단 보조문자 표시·롱프레스 입력 옵션 (삼성식 숫자·기호 힌트). */
    var keyHintsEnabled: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    /** 옵션이 켜졌을 때 이 키가 보여줄/롱프레스로 입력할 보조문자 (없으면 null). */
    private fun keyHint(key: Key): Char? =
        if (keyHintsEnabled && key.type == KeyType.CHAR && key.hint != ' ') key.hint else null

    /**
     * 기호 자판 롱프레스 변형 — 기호별 관련 문자(숫자 열은 기존 분수) 중, 이미 기호
     * 자판에 노출된 문자는 중복이라 뺀다. base 도 자판에 있으므로 넣지 않는다.
     * 남는 게 없으면 null (팝업 없음).
     */
    private fun symbolPopupChars(c: Char): String? {
        val related = SYMBOL_VARIANTS[c] ?: NUMBER_VARIANTS[c] ?: return null
        val filtered = related.filter { it !in SYMBOL_LAYOUT_CHARS }
        return filtered.ifEmpty { null }
    }

    /** 이 키에 롱프레스 팝업이 있는지 — 자판에 따라 참조하는 변형 맵이 다르다. */
    private fun hasVariantPopup(key: Key): Boolean {
        if (key.type != KeyType.CHAR) return false
        if (keyHint(key) != null) return true
        return if (mode == LayoutMode.SYMBOLS) {
            symbolPopupChars(key.char) != null
        } else {
            KEY_VARIANTS.containsKey(key.char)
        }
    }

    private fun commitPendingDigit() {
        val pending = pendingVariant ?: return
        val ch = naratgulDigit(pending.key) ?: landscapeTopDigit(pending.key)
            ?: keyHint(pending.key) ?: return
        performKeyHaptic()
        onKeyListener(Key(KeyType.CHAR, ch.toString(), ch))
        cancelPendingVariant()
    }

    /** 팝업 창에 그려지는 내용. 셀 좌표는 키보드 로컬 기준이므로 패널 원점만큼 이동해 그린다. */
    private inner class VariantPopupContent : View(context) {
        override fun onDraw(canvas: Canvas) {
            val popup = variantPopup ?: return
            canvas.translate(-popup.panel.left, -popup.panel.top)
            drawVariantPopup(canvas, popup)
        }
    }

    var deleteRepeatIntervalMs: Long = 50L

    /** 지울 내용이 남았는지 — 반복 삭제 진동을 빈 입력창에서 울리지 않게 한다. */
    var hasTextToDelete: (() -> Boolean)? = null

    private val repeatHandler = Handler(Looper.getMainLooper())
    private val repeatDelete = object : Runnable {
        override fun run() {
            val hadText = hasTextToDelete?.invoke() != false
            onKeyListener(Key(KeyType.DELETE, "⌫"))
            // 반복 삭제마다 짧은 진동을 줘 지워지는 리듬이 손에 전달되게 한다.
            if (hadText) performKeyHaptic()
            repeatHandler.postDelayed(this, deleteRepeatIntervalMs)
        }
    }

    private var repeatCharKey: Key? = null
    private var repeatCharPointerId = -1
    private val repeatCharRunnable = object : Runnable {
        override fun run() {
            repeatCharKey?.let { onKeyListener(it) }
            repeatHandler.postDelayed(this, CHAR_REPEAT_INTERVAL_MS)
        }
    }

    private fun dp(v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    private fun sp(v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(width, dp(heightDp.toFloat()).toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        boundsDirty = true
    }

    /** 숫자 열은 다른 열보다 살짝 낮게 그린다. 기호 페이지의 숫자 열도 포함. */
    private fun hasCompactNumberRow(): Boolean = when {
        // 컴팩트 기호(3x4)는 숫자 열 없이 4열이 전체 높이를 나눈다.
        mode == LayoutMode.SYMBOLS -> !compactSymbols
        // 3x4 자판(천지인/나랏글)은 숫자 열 자체를 얹지 않는다.
        mode == LayoutMode.KOREAN && is3x4Board() -> false
        // 숫자 키패드는 자체가 숫자라 상단 숫자 열을 얹지 않는다.
        mode == LayoutMode.NUMBER -> false
        else -> effectiveShowNumberRow()
    }

    private fun rebuildBounds() {
        val rows = KeyboardLayouts.rows(
            mode, shifted, effectiveShowNumberRow(), symbolsPage, showLangKey, koreanLayout,
            shiftNumberRowSymbols, englishLayout, compactSymbols, numberVariant,
        )
        landscapeTopDigits =
            if (isLandscape() && mode != LayoutMode.SYMBOLS && mode != LayoutMode.NUMBER && !is3x4Board()) {
                buildMap {
                    rows.firstOrNull()?.asSequence()
                        ?.filter { it.type == KeyType.CHAR }
                        ?.take(10)
                        ?.forEachIndexed { i, key -> put(key.char, "1234567890"[i]) }
                }
            } else {
                emptyMap()
            }
        val heightWeights = FloatArray(rows.size) { 1f }
        if (hasCompactNumberRow() && rows.isNotEmpty()) {
            heightWeights[0] = NUMBER_ROW_HEIGHT_WEIGHT
        }
        val unit = height.toFloat() / heightWeights.sum()
        // 수직 간격을 수평보다 넓게 — 키 높이가 낮아 보이는 인상을 준다.
        val gapX = dp(3f)
        // 가로는 키 높이가 낮아 수직 간격을 줄여 키캡 면적을 확보한다.
        val landscape =
            resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val gapY = if (landscape) dp(4f) else dp(6.5f)
        val bounds = mutableListOf<KeyBounds>()
        var top = 0f
        // 숫자 키패드는 3~4열이라 분할하면 어색하므로 대화면에서도 분할하지 않는다.
        val split = splitEnabled && !is3x4Board() && mode != LayoutMode.NUMBER
        val spaceLeftEdge = if (split) maxLeftBlockEnd(rows) else null
        rows.forEachIndexed { rowIdx, row ->
            val rowHeight = unit * heightWeights[rowIdx]
            val totalWeight = row.sumOf { it.widthWeight.toDouble() }.toFloat()
            if (split) {
                layoutSplitRow(row, totalWeight, top, rowHeight, gapX, gapY, bounds, spaceLeftEdge)
            } else {
                var x = 0f
                row.forEach { key ->
                    val keyWidth = width * (key.widthWeight / totalWeight)
                    bounds += KeyBounds(
                        key,
                        RectF(x + gapX, top + gapY, x + keyWidth - gapX, top + rowHeight - gapY),
                    )
                    x += keyWidth
                }
            }
            top += rowHeight
        }
        keyBounds = bounds
    }

    /**
     * 분할 배치: 행을 가중치 절반 지점에서 좌우로 나누고 중앙에 간격을 둔다.
     * 경계에 걸친 스페이스바는 반으로 갈라 양쪽에 배치하되, 왼쪽 조각의
     * 오른끝만 [spaceLeftEdge]까지 늘려 윗 행 키 끝(ㅍ)과 맞춘다.
     */
    private fun layoutSplitRow(
        row: List<Key>,
        totalWeight: Float,
        top: Float,
        rowHeight: Float,
        gapX: Float,
        gapY: Float,
        bounds: MutableList<KeyBounds>,
        spaceLeftEdge: Float?,
    ) {
        val sideMargin = width * SPLIT_SIDE_MARGIN_RATIO
        val usable = width - sideMargin * 2
        val unit = usable / (totalWeight * (1f + splitGapRatio))
        val gapWidth = usable - totalWeight * unit
        val half = totalWeight / 2f
        var acc = 0f
        var x = sideMargin
        var gapPlaced = false
        row.forEach { key ->
            val w = key.widthWeight
            val straddles = acc < half && acc + w > half
            if (straddles && key.type == KeyType.SPACE) {
                val leftWidth = (half - acc) * unit
                val leftEnd = maxOf(x + leftWidth, spaceLeftEdge ?: 0f)
                bounds += KeyBounds(
                    key,
                    RectF(x + gapX, top + gapY, leftEnd - gapX, top + rowHeight - gapY),
                )
                x += leftWidth + gapWidth
                val rightWidth = (acc + w - half) * unit
                bounds += KeyBounds(
                    key,
                    RectF(x + gapX, top + gapY, x + rightWidth - gapX, top + rowHeight - gapY),
                )
                x += rightWidth
                gapPlaced = true
            } else {
                val keyWidth = w * unit
                bounds += KeyBounds(
                    key,
                    RectF(x + gapX, top + gapY, x + keyWidth - gapX, top + rowHeight - gapY),
                )
                x += keyWidth
            }
            acc += w
            if (!gapPlaced && acc >= half) {
                x += gapWidth
                gapPlaced = true
            }
        }
    }

    /** 분할된 스페이스바가 없는 행들의 좌 블록 오른끝 최댓값 — 스페이스바 끝 정렬 기준. */
    private fun maxLeftBlockEnd(rows: List<List<Key>>): Float? {
        val sideMargin = width * SPLIT_SIDE_MARGIN_RATIO
        val usable = width - sideMargin * 2
        var best: Float? = null
        rows.forEach { row ->
            val totalWeight = row.sumOf { it.widthWeight.toDouble() }.toFloat()
            val half = totalWeight / 2f
            var acc = 0f
            var leftWeight = 0f
            var hasSplitSpace = false
            row.forEach { key ->
                val w = key.widthWeight
                if (acc < half && acc + w > half && key.type == KeyType.SPACE) {
                    hasSplitSpace = true
                } else if (acc < half) {
                    leftWeight += w
                }
                acc += w
            }
            if (!hasSplitSpace) {
                val unit = usable / (totalWeight * (1f + splitGapRatio))
                val end = sideMargin + leftWeight * unit
                if (best == null || end > best!!) best = end
            }
        }
        return best
    }

    private val backgroundPaint = Paint()

    override fun onDraw(canvas: Canvas) {
        // 부모 클리핑이 풀려 있으므로 drawColor(캔버스 전체)가 아니라 뷰 영역만 칠한다.
        backgroundPaint.color = theme.background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        if (boundsDirty) {
            rebuildBounds()
            boundsDirty = false
            // 키 배치가 바뀌었으니 접근성 노드 트리도 다시 만들게 한다.
            a11yHelper.invalidateRoot()
        }
        val radius = dp(8f)
        keyBounds.forEach { bound ->
            val (key, rect) = bound
            if (key.type == KeyType.SPACER || key.type == KeyType.GHOST) return@forEach
            val pressed = pressedByPointer.containsValue(bound)
            if (showKeyBackground || pressed) {
                val paint = when {
                    pressed -> pressedPaint
                    key.type == KeyType.CHAR -> keyPaint
                    key.type == KeyType.SPACE -> keyPaint
                    else -> specialKeyPaint
                }
                canvas.drawRoundRect(rect, radius, radius, paint)
                if (showKeyBackground && theme.keyBorder != null) {
                    canvas.drawRoundRect(rect, radius, radius, borderPaint)
                }
            }
            drawKeyContent(canvas, key, rect)
        }
    }

    private val clipboardShortcutIcon by lazy(LazyThreadSafetyMode.NONE) {
        context.getDrawable(dev.badalab.yeonfeel.R.drawable.ic_toolbar_clipboard)?.mutate()
    }
    private val settingsShortcutIcon by lazy(LazyThreadSafetyMode.NONE) {
        context.getDrawable(dev.badalab.yeonfeel.R.drawable.ic_toolbar_settings)?.mutate()
    }

    private fun drawVariantPopup(canvas: Canvas, popup: VariantPopupState) {
        val radius = dp(12f)
        // 테두리 선이 팝업 창 경계에서 잘려 모서리가 뭉쳐 보이지 않게 안쪽에 그린다.
        val inset = previewBorderPaint.strokeWidth / 2f + 0.5f
        val panelRect = RectF(popup.panel).apply { inset(inset, inset) }
        canvas.drawRoundRect(panelRect, radius, radius, previewBgPaint)
        canvas.drawRoundRect(panelRect, radius, radius, previewBorderPaint)
        popup.cells.forEachIndexed { index, cell ->
            if (index == popup.selected) {
                canvas.drawRoundRect(cell, dp(8f), dp(8f), iconFillPaint)
            } else if (index == 0) {
                canvas.drawRoundRect(cell, dp(8f), dp(8f), specialKeyPaint)
            }
            val paint = if (index == popup.selected) previewSelectedTextPaint else textPaint
            val option = popup.options[index]
            val icon = when (option.firstOrNull()) {
                SHORTCUT_CLIPBOARD -> clipboardShortcutIcon
                SHORTCUT_SETTINGS -> settingsShortcutIcon
                else -> null
            }
            if (icon != null) {
                val half = dp(10f).toInt()
                icon.setTint(paint.color)
                icon.setBounds(
                    cell.centerX().toInt() - half,
                    cell.centerY().toInt() - half,
                    cell.centerX().toInt() + half,
                    cell.centerY().toInt() + half,
                )
                icon.draw(canvas)
            } else {
                val y = cell.centerY() - (paint.ascent() + paint.descent()) / 2
                canvas.drawText(option, cell.centerX(), y, paint)
            }
        }
    }

    /**
     * 누른 키 위의 확대 키캡. 캔버스는 IME 창 안에만 그릴 수 있어 맨 윗열에서
     * 손가락 아래로 밀렸으므로, 창 밖(앱 영역)까지 올라가는 PopupWindow 로 띄운다.
     * 포인터별로 하나씩 — 멀티터치 동시 미리보기를 유지한다.
     */
    private val keyPreviewPopups = HashMap<Int, android.widget.PopupWindow>()

    private fun showKeyPreview(pointerId: Int, pressed: Key) {
        if (!keyPreviewEnabled || is3x4Board() || variantPopup != null) return
        if (pressed.type != KeyType.CHAR && pressed.type != KeyType.GHOST) return
        // 고스트(투명 보정 영역)는 실제 키 위치에서 미리보기를 띄운다.
        val bound = if (pressed.type == KeyType.GHOST) {
            keyBounds.firstOrNull { it.key.type == KeyType.CHAR && it.key.char == pressed.char }
        } else {
            keyBounds.firstOrNull { it.key == pressed }
        } ?: return
        dismissKeyPreview(pointerId)
        val previewWidth = (bound.rect.width() * 1.45f).toInt()
        val previewHeight = (bound.rect.height() * 1.6f).toInt()
        val window = android.widget.PopupWindow(
            KeyPreviewContent(bound.key.label),
            previewWidth,
            previewHeight,
        ).apply {
            isClippingEnabled = false
            isTouchable = false
            isFocusable = false
        }
        val location = IntArray(2)
        getLocationInWindow(location)
        window.showAtLocation(
            this,
            Gravity.NO_GRAVITY,
            (location[0] + bound.rect.centerX() - previewWidth / 2f).toInt(),
            (location[1] + bound.rect.top - dp(4f) - previewHeight).toInt(),
        )
        keyPreviewPopups[pointerId] = window
    }

    private fun dismissKeyPreview(pointerId: Int) {
        keyPreviewPopups.remove(pointerId)?.dismiss()
    }

    private fun dismissAllKeyPreviews() {
        keyPreviewPopups.values.forEach { it.dismiss() }
        keyPreviewPopups.clear()
    }

    private inner class KeyPreviewContent(private val label: String) : View(context) {
        override fun onDraw(canvas: Canvas) {
            val inset = previewBorderPaint.strokeWidth / 2f + 0.5f
            val r = RectF(0f, 0f, width.toFloat(), height.toFloat()).apply { inset(inset, inset) }
            val radius = dp(12f)
            canvas.drawRoundRect(r, radius, radius, previewBgPaint)
            canvas.drawRoundRect(r, radius, radius, previewBorderPaint)
            val y = r.centerY() - (previewTextPaint.ascent() + previewTextPaint.descent()) / 2
            canvas.drawText(label, r.centerX(), y, previewTextPaint)
        }
    }

    private fun drawKeyContent(canvas: Canvas, key: Key, rect: RectF) {
        run {
            when (key.type) {
                KeyType.SPACE -> {
                    // 스페이스바 중앙에 ⎵ 기호를 직접 그린다 (폰트 글리프 의존 없이).
                    val half = dp(22f)
                    val tick = dp(7f)
                    val cx = rect.centerX()
                    val baseY = rect.centerY() + tick / 2
                    canvas.drawLine(cx - half, baseY - tick, cx - half, baseY, spaceGlyphPaint)
                    canvas.drawLine(cx - half, baseY, cx + half, baseY, spaceGlyphPaint)
                    canvas.drawLine(cx + half, baseY - tick, cx + half, baseY, spaceGlyphPaint)
                }
                KeyType.SHIFT -> drawShiftIcon(canvas, rect)
                KeyType.DELETE -> drawDeleteIcon(canvas, rect)
                KeyType.ENTER -> {
                    val label = enterActionLabel
                    if (label.isNullOrEmpty()) {
                        drawEnterIcon(canvas, rect)
                    } else {
                        val y = rect.centerY() -
                            (enterActionPaint.ascent() + enterActionPaint.descent()) / 2
                        canvas.drawText(label, 0, label.length, rect.centerX(), y, enterActionPaint)
                    }
                }
                else -> if (key.label.isNotEmpty()) {
                    // 키가 큰 3x4 자판 글자(".,?!" 포함)는 크게, 한/영·그 외 긴 라벨은 작게 표시한다.
                    val paint = when {
                        key.type == KeyType.LANG -> smallTextPaint
                        is3x4Board() && key.type == KeyType.CHAR -> bigTextPaint
                        key.label.length >= 4 -> smallTextPaint
                        else -> textPaint
                    }
                    val digit = (naratgulDigit(key) ?: landscapeTopDigit(key) ?: keyHint(key))?.toString()
                    // 보조문자가 있으면 라벨은 가로 중앙을 유지한 채 살짝 아래로 내려 위쪽에 여백을 준다.
                    val labelDrop = if (digit != null) dp(3f) else 0f
                    val y = rect.centerY() + labelDrop - (paint.ascent() + paint.descent()) / 2
                    // 한 손 모드 등으로 키가 좁아 라벨(예: 한/영)이 키 폭을 넘으면 축소해 그린다.
                    val baseSize = paint.textSize
                    val avail = rect.width() - dp(8f)
                    val measured = paint.measureText(key.label)
                    if (measured > avail && avail > 0f) paint.textSize = baseSize * (avail / measured)
                    canvas.drawText(key.label, rect.centerX(), y, paint)
                    if (paint.textSize != baseSize) paint.textSize = baseSize
                    if (digit != null) {
                        canvas.drawText(
                            digit,
                            rect.right - dp(5f),
                            rect.top + dp(4f) - hintTextPaint.ascent(),
                            hintTextPaint,
                        )
                    }
                }
            }
        }
    }

    // 기능 키 아이콘 Path는 dp 고정이라 1회만 생성한다 (매 프레임 할당 방지).
    private val shiftIconPath by lazy(LazyThreadSafetyMode.NONE) {
        val u = dp(1f)
        Path().apply {
            moveTo(0f, -9f * u)
            lineTo(7.5f * u, -0.5f * u)
            lineTo(3.5f * u, -0.5f * u)
            lineTo(3.5f * u, 8f * u)
            lineTo(-3.5f * u, 8f * u)
            lineTo(-3.5f * u, -0.5f * u)
            lineTo(-7.5f * u, -0.5f * u)
            close()
        }
    }

    private val deleteIconBody by lazy(LazyThreadSafetyMode.NONE) {
        val u = dp(1f)
        Path().apply {
            moveTo(-10f * u, 0f)
            lineTo(-4f * u, -6.5f * u)
            lineTo(9.5f * u, -6.5f * u)
            lineTo(9.5f * u, 6.5f * u)
            lineTo(-4f * u, 6.5f * u)
            close()
        }
    }

    private fun drawShiftIcon(canvas: Canvas, rect: RectF) {
        val u = dp(1f)
        canvas.withTranslation(rect.centerX(), rect.centerY()) {
            if (shifted) {
                drawPath(shiftIconPath, iconFillPaint)
            } else {
                drawPath(shiftIconPath, iconPaint)
            }
            // 고정 상태 표시: 아이콘 아래 짧은 밑줄 (액센트 색)
            if (capsLock) {
                drawRect(-3.5f * u, 10.5f * u, 3.5f * u, 12.5f * u, iconFillPaint)
            }
        }
    }

    private fun drawDeleteIcon(canvas: Canvas, rect: RectF) {
        val u = dp(1f)
        canvas.withTranslation(rect.centerX(), rect.centerY()) {
            drawPath(deleteIconBody, iconPaint)
            val c = 2.4f * u
            val ox = 1.8f * u
            drawLine(ox - c, -c, ox + c, c, iconPaint)
            drawLine(ox - c, c, ox + c, -c, iconPaint)
        }
    }

    private fun drawEnterIcon(canvas: Canvas, rect: RectF) {
        val u = dp(1f)
        canvas.withTranslation(rect.centerX(), rect.centerY()) {
            drawLine(7f * u, -7.5f * u, 7f * u, 2.5f * u, iconPaint)
            drawLine(7f * u, 2.5f * u, -7f * u, 2.5f * u, iconPaint)
            drawLine(-7f * u, 2.5f * u, -2.5f * u, -2f * u, iconPaint)
            drawLine(-7f * u, 2.5f * u, -2.5f * u, 7f * u, iconPaint)
        }
    }

    private inline fun Canvas.withTranslation(x: Float, y: Float, block: Canvas.() -> Unit) {
        val save = save()
        translate(x, y)
        block()
        restoreToCount(save)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                val pointerId = event.getPointerId(index)
                val x = event.getX(index)
                val y = event.getY(index)
                val hit = boundsAt(x, y) ?: return true
                val key = hit.key
                if (width > 0 && height > 0) {
                    onTapRecorded?.invoke(
                        key,
                        x / width,
                        y / height,
                        (x - hit.rect.centerX()) / hit.rect.width(),
                        (y - hit.rect.centerY()) / hit.rect.height(),
                    )
                }
                // 롤오버: 스페이스바를 떼기 전에 다음 키가 눌리면 순서 보존을 위해
                // 대기 중인 스페이스바를 먼저 확정한다 (빠른 타이핑에서 어순 역전 방지).
                if (spacePointerId != -1 && !spaceSwiped && pointerId != spacePointerId) {
                    pressedByPointer[spacePointerId]?.key?.let { onKeyListener(it) }
                    spaceSwiped = true // UP에서 중복 입력 방지
                    dismissLanguagePopup()
                }
                // 변형 키 롤오버: 팝업이 뜨기 전에 다른 키가 눌리면 원래 문자를 먼저 확정
                pendingVariant?.let { pending ->
                    if (pending.pointerId != pointerId && variantPopup == null) {
                        onKeyListener(pending.key)
                        cancelPendingVariant()
                    }
                }
                pressedByPointer[pointerId] = hit
                downXByPointer[pointerId] = x
                showKeyPreview(pointerId, key)
                performKeyHaptic()
                performKeySound(key)
                when {
                    // 스페이스바는 좌우 스와이프(언어 변경)와 구분해야 하므로 UP에서 입력한다.
                    key.type == KeyType.SPACE -> {
                        spacePointerId = pointerId
                        spaceSwiped = false
                        spaceLastDx = 0f
                        if (languageSwipeEnabled) {
                            repeatHandler.postDelayed(langPopupRunnable, LANG_POPUP_DELAY_MS)
                        }
                    }
                    key.type == KeyType.DELETE -> {
                        onKeyListener(key)
                        deletePointerId = pointerId
                        repeatHandler.postDelayed(repeatDelete, 400L)
                    }
                    // 나랏글 우상단 숫자 등 3x4 자판: 길게 누르면 바로 숫자 입력.
                    naratgulDigit(key) != null ||
                        (landscapeTopDigit(key) != null && !KEY_VARIANTS.containsKey(key.char)) -> {
                        pendingVariant = PendingVariant(pointerId, key, RectF(hit.rect))
                        repeatHandler.postDelayed(digitLongPressRunnable, longPressDelayMs)
                    }
                    // 변형 문자·보조문자가 있는 키는 롱프레스 시 팝업을 띄우고 UP에서 입력한다.
                    hasVariantPopup(key) -> {
                        pendingVariant = PendingVariant(pointerId, key, RectF(hit.rect))
                        repeatHandler.postDelayed(longPressRunnable, longPressDelayMs)
                    }
                    key.type == KeyType.LANG -> {
                        langKeyPointerId = pointerId
                        repeatHandler.postDelayed(langListRunnable, longPressDelayMs)
                    }
                    (key.type == KeyType.CHAR || key.type == KeyType.GHOST) &&
                        key.char in REPEATABLE_CHARS -> {
                        onKeyListener(key)
                        repeatCharKey = key
                        repeatCharPointerId = pointerId
                        repeatHandler.postDelayed(repeatCharRunnable, CHAR_REPEAT_START_MS)
                    }
                    else -> onKeyListener(key)
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                langListState?.let { state ->
                    val idx = event.findPointerIndex(state.pointerId)
                    if (idx >= 0) {
                        val y = event.getY(idx)
                        // 아직 키 위(팝업 아래)에 있으면 유지, 팝업 쪽으로 올라오면 가장 가까운 행 선택
                        if (y <= state.panel.bottom + dp(10f)) {
                            var nearest = state.selected
                            var best = Float.MAX_VALUE
                            state.rows.forEachIndexed { index, row ->
                                val dy = kotlin.math.abs(y - row.centerY())
                                if (dy < best) {
                                    best = dy
                                    nearest = index
                                }
                            }
                            if (nearest != state.selected) {
                                state.selected = nearest
                                performKeyHaptic()
                                langListPopup?.contentView?.invalidate()
                            }
                        }
                    }
                }
                variantPopup?.let { popup ->
                    val idx = event.findPointerIndex(popup.pointerId)
                    if (idx >= 0) {
                        val selected = nearestVariantCell(popup, event.getX(idx), event.getY(idx))
                        if (selected != popup.selected) {
                            popup.selected = selected
                            variantPopupWindow?.contentView?.invalidate()
                        }
                    }
                }
                if (spacePointerId != -1 && languageSwipeEnabled) {
                    val index = event.findPointerIndex(spacePointerId)
                    val startX = downXByPointer[spacePointerId]
                    if (index >= 0 && startX != null) {
                        // 전환은 손을 뗄 때 1회만 — 드래그 중에는 미리보기만 갱신한다.
                        spaceLastDx = event.getX(index) - startX
                        langDragOffset = spaceLastDx.coerceIn(-dp(70f), dp(70f))
                        langPopupWindow?.contentView?.invalidate()
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val pointerId = event.getPointerId(event.actionIndex)
                val key = pressedByPointer.remove(pointerId)?.key
                downXByPointer.remove(pointerId)
                dismissKeyPreview(pointerId)
                pendingVariant?.let { pending ->
                    if (pending.pointerId == pointerId) {
                        val popup = variantPopup
                        if (popup != null && popup.pointerId == pointerId) {
                            val choice = popup.options[popup.selected]
                            val first = choice.first()
                            if (first == SHORTCUT_CLIPBOARD || first == SHORTCUT_SETTINGS) {
                                onShortcutSelected?.invoke(first)
                            } else {
                                onKeyListener(Key(KeyType.CHAR, choice, first))
                                if (pending.key.remember && choice.length == 1) {
                                    KeyboardLayouts.lastSymbol3x4 = first
                                    onVariantPicked?.invoke(first)
                                    relayoutKeys()
                                }
                            }
                        } else {
                            onKeyListener(pending.key)
                        }
                        cancelPendingVariant()
                    }
                }
                if (key?.type == KeyType.LANG && pointerId == langKeyPointerId) {
                    val state = langListState
                    if (state != null) {
                        onLanguageSelected?.invoke(state.selected)
                    } else {
                        onKeyListener(key) // 짧게 누름 → 기존 토글
                    }
                    dismissLanguageListPopup()
                }
                if (key?.type == KeyType.SPACE && pointerId == spacePointerId) {
                    val swiped = languageSwipeEnabled &&
                        kotlin.math.abs(spaceLastDx) > dp(SPACE_SWIPE_THRESHOLD_DP)
                    when {
                        swiped -> {
                            performKeyHaptic()
                            onLanguageSwipe?.invoke()
                        }
                        !spaceSwiped -> onKeyListener(key)
                    }
                    spacePointerId = -1
                    spaceLastDx = 0f
                    dismissLanguagePopup()
                }
                if (pointerId == deletePointerId) {
                    repeatHandler.removeCallbacks(repeatDelete)
                    deletePointerId = -1
                }
                if (pointerId == repeatCharPointerId) {
                    repeatHandler.removeCallbacks(repeatCharRunnable)
                    repeatCharKey = null
                    repeatCharPointerId = -1
                }
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    clearTouchState()
                }
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                clearTouchState()
                invalidate()
            }
        }
        return true
    }

    /**
     * 터치 지점의 키를 찾는다. 키 사이 틈에 떨어진 터치는 키 간격만큼 확장한
     * 영역 안에서만 가장 가까운 키로 스냅한다 — 빠른 타이핑 씹힘은 막되,
     * 스페이서 같은 진짜 빈 공간을 누르면 아무 키도 눌리지 않는다.
     * 터치마다 호출되므로 할당 없이 순회한다.
     */
    private fun boundsAt(x: Float, y: Float): KeyBounds? {
        val geometric = geometricBoundsAt(x, y) ?: return null
        val provider = touchStatsProvider
        if (!touchModelEnabled || provider == null) return geometric
        // 기능 키(스페이스·엔터 등)는 확률 판정으로 빼앗기지 않게 보호한다.
        if (geometric.key.type != KeyType.CHAR) return geometric
        val rect = geometric.rect
        val edgeX = rect.width() * 0.28f
        val edgeY = rect.height() * 0.28f
        val nearEdge = x < rect.left + edgeX || x > rect.right - edgeX ||
            y < rect.top + edgeY || y > rect.bottom - edgeY
        if (!nearEdge) return geometric

        val stats = provider(currentBoardId())
        var best = geometric
        var bestScore = Float.NEGATIVE_INFINITY
        for (bound in keyBounds) {
            if (bound.key.type != KeyType.CHAR) continue
            val r = bound.rect
            val marginX = r.width() * 0.6f
            val marginY = r.height() * 0.6f
            if (x < r.left - marginX || x > r.right + marginX ||
                y < r.top - marginY || y > r.bottom + marginY
            ) {
                continue
            }
            val rx = (x - r.centerX()) / r.width()
            val ry = (y - r.centerY()) / r.height()
            val score = TouchModel.logLikelihood(rx, ry, stats[bound.key.char.toString()])
            if (score > bestScore) {
                bestScore = score
                best = bound
            }
        }
        return best
    }

    private fun geometricBoundsAt(x: Float, y: Float): KeyBounds? {
        val slopX = dp(6f)
        val slopY = dp(9f)
        var nearest: KeyBounds? = null
        var nearestDistance = Float.MAX_VALUE
        for (bound in keyBounds) {
            if (bound.key.type == KeyType.SPACER) continue
            if (bound.rect.contains(x, y)) return bound
            val rect = bound.rect
            val inSlop = x >= rect.left - slopX && x <= rect.right + slopX &&
                y >= rect.top - slopY && y <= rect.bottom + slopY
            if (!inSlop) continue
            val dx = x - rect.centerX()
            val dy = y - rect.centerY()
            val distance = dx * dx + dy * dy
            if (distance < nearestDistance) {
                nearestDistance = distance
                nearest = bound
            }
        }
        return nearest
    }

    private fun clearTouchState() {
        dismissAllKeyPreviews()
        pressedByPointer.clear()
        downXByPointer.clear()
        spacePointerId = -1
        deletePointerId = -1
        repeatHandler.removeCallbacks(repeatDelete)
        repeatHandler.removeCallbacks(repeatCharRunnable)
        repeatCharKey = null
        repeatCharPointerId = -1
        cancelPendingVariant()
        dismissLanguagePopup()
        dismissLanguageListPopup()
    }

    private fun cancelPendingVariant() {
        repeatHandler.removeCallbacks(longPressRunnable)
        repeatHandler.removeCallbacks(digitLongPressRunnable)
        pendingVariant = null
        variantPopupWindow?.dismiss()
        variantPopupWindow = null
        if (variantPopup != null) {
            variantPopup = null
            invalidate()
        }
    }

    private fun showVariantPopup() {
        if (variantPopupWindow != null) return // 다른 손가락으로 이미 변형 팝업이 떠 있으면 무시
        dismissAllKeyPreviews() // 미리보기 위에 변형 팝업이 겹치지 않게 정리
        val pending = pendingVariant ?: return
        // 보조문자 옵션이 켜지면 그 키의 롱프레스 팝업은 보조문자 하나만 보여준다
        // (코너 글자 = 손 떼면 입력되는 결과).
        val hint = keyHint(pending.key)
        val options: List<String>
        val selectedIndex: Int
        if (hint != null) {
            options = listOf(hint.toString())
            selectedIndex = 0
        } else if (mode == LayoutMode.SYMBOLS) {
            // 기호 자판: 자판에 없는 관련 변형만 (base·이미 노출된 기호 제외).
            val variants = symbolPopupChars(pending.key.char) ?: return
            options = variants.map { it.toString() }
            selectedIndex = 0
        } else {
            val variants = (KEY_VARIANTS[pending.key.char] ?: "") +
                (landscapeTopDigit(pending.key)?.toString() ?: "")
            if (variants.isEmpty()) return
            // 한글 쌍자음 팝업은 원래 자음을 빼고 변형만 보여준다 (ㅂ 롱프레스 → ㅃ만).
            val includeOriginal = pending.key.char !in KOREAN_VARIANTS
            options = buildList {
                if (includeOriginal) add(pending.key.label)
                variants.forEach { add(it.toString()) }
                // 툴바 없이도 접근할 수 있게 그리드의 빈 두 칸을 단축키로 채운다.
                if (pending.key.char == ',' && !pending.key.remember) {
                    add(SHORTCUT_CLIPBOARD.toString())
                    add(SHORTCUT_SETTINGS.toString())
                }
            }
            selectedIndex = if (includeOriginal) 1 else 0
        }
        val anchor = pending.rect
        // 단일 문자 후보는 키가 큰 3x4 자판에서도 작은 고정 셀(6열)로 촘촘히 배치한다.
        val compact = options.all { it.length == 1 }
        val pad0 = dp(6f)
        // 한 손 모드·큰 여백으로 뷰가 좁으면 열 수·셀 폭을 줄여 패널이 화면을 넘지 않게 한다.
        val fitCols = ((width - pad0 * 2) / dp(40f)).toInt().coerceAtLeast(1)
        val columns = minOf(if (compact) 6 else 4, options.size, if (compact) fitCols else 4)
        val rows = (options.size + columns - 1) / columns
        val cellWidth = if (compact) {
            minOf(dp(40f), (width - pad0 * 2) / columns)
        } else {
            maxOf(anchor.width(), dp(44f)).coerceAtMost((width - pad0 * 2) / columns)
        }
        val cellHeight = if (compact) dp(42f) else maxOf(anchor.height(), dp(44f))
        val pad = dp(6f)
        val panelWidth = pad * 2 + columns * cellWidth
        val panelHeight = pad * 2 + rows * cellHeight
        val left = anchor.left.coerceIn(dp(2f), maxOf(dp(2f), width - panelWidth - dp(2f)))
        // 손가락(키) 수직 위. PopupWindow 오버레이라 키보드 창 밖(툴바·앱 영역)까지 올라간다.
        val bottom = anchor.top - dp(4f)
        val panel = RectF(left, bottom - panelHeight, left + panelWidth, bottom)
        // 셀은 아랫줄부터 채운다 (원래 키가 왼쪽 아래, 참고 디자인과 동일)
        val cells = options.indices.map { index ->
            val row = index / columns
            val col = index % columns
            val x = panel.left + pad + col * cellWidth
            val y = panel.bottom - pad - (row + 1) * cellHeight
            RectF(x + dp(2f), y + dp(2f), x + cellWidth - dp(2f), y + cellHeight - dp(2f))
        }
        // 첫 변형을 기본 선택으로 — 길게 눌렀다 그대로 떼면 첫 변형이 입력된다.
        variantPopup = VariantPopupState(
            pending.pointerId,
            options,
            panel,
            cells,
            startX = downXByPointer[pending.pointerId] ?: anchor.centerX(),
            selected = selectedIndex,
        )
        val location = IntArray(2)
        getLocationInWindow(location)
        val window = android.widget.PopupWindow(
            VariantPopupContent(),
            panelWidth.toInt(),
            panelHeight.toInt(),
        ).apply {
            isClippingEnabled = false
            isTouchable = false
            isFocusable = false
        }
        window.showAtLocation(
            this,
            Gravity.NO_GRAVITY,
            location[0] + panel.left.toInt(),
            location[1] + panel.top.toInt(),
        )
        variantPopupWindow = window
        performKeyHaptic()
        invalidate()
    }

    private fun nearestVariantCell(popup: VariantPopupState, x: Float, y: Float): Int {
        if (y > popup.panel.bottom + dp(6f)) {
            // 손가락이 아직 키 위(팝업 아래)에 있는 경우: 팝업으로 올리지 않아도
            // 좌우로 밀면 아랫줄 후보 사이에서 포커스가 움직인다.
            // 미세 떨림으로 기본 선택이 튀지 않게 시작점에서 12dp 이상 움직여야 반응한다.
            if (kotlin.math.abs(x - popup.startX) < dp(12f)) return popup.selected
            val bottomRow = popup.cells.maxOf { it.bottom }
            var nearest = popup.selected
            var best = Float.MAX_VALUE
            popup.cells.forEachIndexed { index, cell ->
                if (cell.bottom < bottomRow - 1f) return@forEachIndexed
                val dx = kotlin.math.abs(x - cell.centerX())
                if (dx < best) {
                    best = dx
                    nearest = index
                }
            }
            return nearest
        }
        var nearest = popup.selected
        var nearestDistance = Float.MAX_VALUE
        popup.cells.forEachIndexed { index, cell ->
            val dx = x - cell.centerX()
            val dy = y - cell.centerY()
            val distance = dx * dx + dy * dy
            if (distance < nearestDistance) {
                nearestDistance = distance
                nearest = index
            }
        }
        return nearest
    }

    override fun onDetachedFromWindow() {
        // 팝업·반복 핸들러·눌림 상태를 모두 정리한다. 하나라도 남으면 숨겨진 뒤에도
        // 문자가 계속 삽입되거나(반복 키), 뜬 팝업이 서비스를 붙잡아 누수된다.
        clearTouchState()
        super.onDetachedFromWindow()
    }

    /** onDraw 밖(접근성 질의)에서도 키 좌표가 필요하므로 필요 시 즉시 계산한다. */
    private fun ensureBounds() {
        if (boundsDirty && width > 0 && height > 0) {
            rebuildBounds()
            boundsDirty = false
        }
    }

    /** 기능 키는 아이콘만 그려 라벨이 없으므로 TalkBack용 이름을 따로 만든다. */
    private fun describeKey(key: Key): CharSequence = when (key.type) {
        KeyType.DELETE -> context.getString(R.string.a11y_key_delete)
        KeyType.SHIFT -> context.getString(R.string.a11y_key_shift)
        KeyType.ENTER ->
            enterActionLabel?.takeIf { it.isNotEmpty() } ?: context.getString(R.string.a11y_key_enter)
        KeyType.SPACE -> context.getString(R.string.a11y_key_space)
        KeyType.SYMBOLS -> key.label.ifBlank { context.getString(R.string.a11y_key_symbols) }
        KeyType.LANG -> key.label.ifBlank { context.getString(R.string.a11y_key_language) }
        else -> key.label
    }

    private inner class KeyboardA11yHelper : ExploreByTouchHelper(this@KeyboardView) {
        override fun getVirtualViewAt(x: Float, y: Float): Int {
            ensureBounds()
            keyBounds.forEachIndexed { i, b ->
                if (b.key.type != KeyType.SPACER && b.rect.contains(x, y)) return i
            }
            return HOST_ID
        }

        override fun getVisibleVirtualViews(ids: MutableList<Int>) {
            ensureBounds()
            keyBounds.forEachIndexed { i, b ->
                if (b.key.type != KeyType.SPACER) ids.add(i)
            }
        }

        override fun onPopulateNodeForVirtualView(id: Int, node: AccessibilityNodeInfoCompat) {
            val bound = keyBounds.getOrNull(id)
            if (bound == null) {
                node.contentDescription = ""
                node.setBoundsInParent(Rect(0, 0, 1, 1))
                return
            }
            node.contentDescription = describeKey(bound.key)
            node.className = "android.widget.Button"
            node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
            val r = bound.rect
            node.setBoundsInParent(
                Rect(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt()),
            )
        }

        override fun onPerformActionForVirtualView(id: Int, action: Int, args: Bundle?): Boolean {
            if (action == AccessibilityNodeInfoCompat.ACTION_CLICK) {
                keyBounds.getOrNull(id)?.let {
                    performKeyHaptic()
                    onKeyListener(it.key)
                    return true
                }
            }
            return false
        }

        override fun onPopulateEventForVirtualView(id: Int, event: AccessibilityEvent) {
            event.text.add(keyBounds.getOrNull(id)?.let { describeKey(it.key) } ?: "")
        }
    }

    companion object {
        const val SHORTCUT_CLIPBOARD = '\uE020'
        const val SHORTCUT_SETTINGS = '\uE021'
        private const val NUMBER_ROW_HEIGHT_WEIGHT = 0.85f
        private const val SPLIT_SIDE_MARGIN_RATIO = 0.03f
        private const val ACCENT = 0xFF3D8BFF.toInt()
        private const val KEY_SOUND_VOLUME = 0.5f
        private const val LANG_POPUP_DELAY_MS = 300L
        private const val SPACE_SWIPE_THRESHOLD_DP = 30f
        private const val CHAR_REPEAT_START_MS = 400L
        private const val CHAR_REPEAT_INTERVAL_MS = 60L

        private val REPEATABLE_CHARS = setOf('ㅋ')

        /** 3x4 자판 키 우상단 숫자: 길게 누르면 해당 숫자가 입력된다. */
        private val NARATGUL_DIGITS = mapOf(
            'ㄱ' to '1', 'ㄴ' to '2', 'ㅏ' to '3',
            'ㄹ' to '4', 'ㅁ' to '5', 'ㅗ' to '6',
            'ㅅ' to '7', 'ㅇ' to '8', 'ㅣ' to '9',
            'ㅡ' to '0',
        )
        private val CHUNJIIN_DIGITS = mapOf(
            'ㅣ' to '1', 'ㆍ' to '2', 'ㅡ' to '3',
            'ㄱ' to '4', 'ㄴ' to '5', 'ㄷ' to '6',
            'ㅂ' to '7', 'ㅅ' to '8', 'ㅈ' to '9',
            'ㅇ' to '0',
        )

        /** 숫자 키 롱프레스: 위첨자(첫 후보, 기본 선택) + 유니코드 분수. */
        private val NUMBER_VARIANTS = mapOf(
            '1' to "¹½⅓¼⅕⅙⅐⅛⅑",
            '2' to "²⅔⅖",
            '3' to "³¾⅗⅜",
            '4' to "⁴⅘",
            '5' to "⁵⅚⅝",
            '6' to "⁶",
            '7' to "⁷⅞",
            '8' to "⁸",
            '9' to "⁹",
            '0' to "⁰",
        )

        /** 영문 키 롱프레스: 악센트(다이어크리틱) 문자. 대문자는 자동 생성된다. */
        private val LETTER_VARIANTS = mapOf(
            'a' to "àáâäæãåā",
            'c' to "çćč",
            'e' to "èéêëēėę",
            'g' to "ğ",
            'i' to "îïíīįì",
            'l' to "ł",
            'n' to "ñń",
            'o' to "ôöòóœøōõ",
            's' to "ßśš",
            'u' to "ûüùúū",
            'y' to "ÿ",
            'z' to "žźż",
        )

        /** 한글 키 롱프레스: 쌍자음·이중모음. */
        private val KOREAN_VARIANTS = mapOf(
            'ㄱ' to "ㄲ",
            'ㄷ' to "ㄸ",
            'ㅂ' to "ㅃ",
            'ㅅ' to "ㅆ",
            'ㅈ' to "ㅉ",
            'ㅐ' to "ㅒ",
            'ㅔ' to "ㅖ",
        )

        /** 기호 키 롱프레스에 공통으로 띄우는 자주 쓰는 기호. */
        private const val QUICK_SYMBOLS = "@-/:#,?!'$"
        private const val QUICK_SYMBOL_TARGETS = "!?.,()@:;/-*_%~^#'\"$"

        /** 기호 자판(두 페이지·숫자 열)에 노출된 모든 문자 — 팝업에서 이 문자는 뺀다. */
        private val SYMBOL_LAYOUT_CHARS: Set<Char> = (
            "1234567890" +
                "+×÷=/_<>[]" + "!@#₩%^&*()" + "-'\":;,?" +
                "`~\\|{}€£¥$" + "°•○●□■♤♡◇♧" + "☆▪¤《》¡¿"
            ).toSet()

        /**
         * 기호 자판 전용 per-symbol 변형 후보 — 각 기호의 관련 문자. 이 중 자판에 이미
         * 노출된 문자는 [symbolPopupChars]에서 걸러진다.
         */
        private val SYMBOL_VARIANTS = mapOf(
            '$' to "€£¥₩₽¢₹",
            '%' to "‰",
            '+' to "±",
            '-' to "–—·•",
            '×' to "∙·",
            '÷' to "∕",
            '=' to "≈≠≡",
            '<' to "≤«",
            '>' to "≥»",
            '*' to "∗†‧",
            '/' to "⁄\\",
            '\\' to "/",
            '_' to "—–",
            '~' to "≈",
            '(' to "[{<",
            ')' to "]}>",
            '[' to "{(<",
            ']' to "})>",
            '\'' to "‘’‚′",
            '"' to "“”„″«»",
            '.' to "…·",
            '!' to "¡",
            '?' to "¿",
            '#' to "№♯",
            '&' to "§",
            '•' to "·○●",
            '°' to "ºª",
        )

        private val KEY_VARIANTS: Map<Char, String> = buildMap {
            putAll(NUMBER_VARIANTS)
            putAll(KOREAN_VARIANTS)
            LETTER_VARIANTS.forEach { (base, variants) ->
                put(base, variants)
                put(
                    base.uppercaseChar(),
                    variants.map { if (it == 'ß') 'ẞ' else it.uppercaseChar() }.joinToString(""),
                )
            }
            QUICK_SYMBOL_TARGETS.forEach { c ->
                if (c !in this) put(c, QUICK_SYMBOLS.filter { it != c })
            }
        }
    }
}

package dev.badalab.yeonfeel.ime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.CornerPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
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
        if (langKeyPointerId == -1 || languageList.size < 2) return
        val key = pressedByPointer[langKeyPointerId] ?: return
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
     * 스페이스 홀드 언어 팝업 내용. 좌우 화살표와 G2 연속 곡률 모서리를 쓰고,
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
        if (spacePointerId == -1 || variantPopup != null) return
        val spaceKey = pressedByPointer[spacePointerId] ?: return
        val bound = keyBounds.firstOrNull { it.key == spaceKey } ?: return
        langTextPaint.color = theme.text
        langDragOffset = 0f
        val popupWidth = dp(150f).toInt()
        val popupHeight = dp(44f).toInt()
        val location = IntArray(2)
        getLocationInWindow(location)
        val x = (bound.rect.centerX() - popupWidth / 2f).toInt()
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

    /** 길게 누르기 판정 시간(ms). 접근성 설정에서 조절한다 (변형 팝업·숫자·언어 목록 공통). */
    var longPressDelayMs: Long = 350L

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
    var hapticStrength: Int = 50

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


    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    /** 강도 조절이 되는 기기에선 진폭으로, 아니면 짧은 진동으로 피드백한다. */
    private fun performKeyHaptic() {
        if (!hapticEnabled || hapticStrength <= 0) return
        val vib = vibrator ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amplitude = (1 + hapticStrength * 2.54).toInt().coerceIn(1, 255)
                vib.vibrate(VibrationEffect.createOneShot(HAPTIC_DURATION_MS, amplitude))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(HAPTIC_DURATION_MS)
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
    private val smallTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = sp(13f)
    }
    private val hintTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
        textSize = sp(11f)
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
    private val pressedByPointer = HashMap<Int, Key>()
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
    private fun is3x4Board(): Boolean = when {
        mode == LayoutMode.SYMBOLS -> compactSymbols
        mode == LayoutMode.KOREAN -> koreanLayout in setOf(
            KoreanLayoutType.CHUNJIIN,
            KoreanLayoutType.NARATGUL,
            KoreanLayoutType.NARATGUL_CENTER,
        )
        else -> false
    }

    /** 나랏글 자판에서 이 키를 길게 눌렀을 때 입력될 숫자 (없으면 null). */
    private fun naratgulDigit(key: Key): Char? =
        if (mode == LayoutMode.KOREAN && key.type == KeyType.CHAR &&
            (koreanLayout == KoreanLayoutType.NARATGUL || koreanLayout == KoreanLayoutType.NARATGUL_CENTER)
        ) {
            NARATGUL_DIGITS[key.char]
        } else {
            null
        }

    private fun commitPendingDigit() {
        val pending = pendingVariant ?: return
        val digit = naratgulDigit(pending.key) ?: return
        performKeyHaptic()
        onKeyListener(Key(KeyType.CHAR, digit.toString(), digit))
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

    private val repeatHandler = Handler(Looper.getMainLooper())
    private val repeatDelete = object : Runnable {
        override fun run() {
            onKeyListener(Key(KeyType.DELETE, "⌫"))
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
        // 세벌식·3x4 자판(천지인/나랏글)은 숫자 열 자체를 얹지 않는다.
        mode == LayoutMode.KOREAN && koreanLayout in setOf(
            KoreanLayoutType.SEBEOLSIK_390,
            KoreanLayoutType.CHUNJIIN,
            KoreanLayoutType.NARATGUL,
            KoreanLayoutType.NARATGUL_CENTER,
        ) -> false
        else -> showNumberRow
    }

    private fun rebuildBounds() {
        val rows = KeyboardLayouts.rows(
            mode, shifted, showNumberRow, symbolsPage, showLangKey, koreanLayout,
            shiftNumberRowSymbols, englishLayout, compactSymbols,
        )
        val heightWeights = FloatArray(rows.size) { 1f }
        if (hasCompactNumberRow() && rows.isNotEmpty()) {
            heightWeights[0] = NUMBER_ROW_HEIGHT_WEIGHT
        }
        val unit = height.toFloat() / heightWeights.sum()
        // 수직 간격을 수평보다 넓게 — 키 높이가 낮아 보이는 인상을 준다.
        val gapX = dp(3f)
        val gapY = dp(6.5f)
        val bounds = mutableListOf<KeyBounds>()
        var top = 0f
        rows.forEachIndexed { rowIdx, row ->
            val rowHeight = unit * heightWeights[rowIdx]
            val totalWeight = row.sumOf { it.widthWeight.toDouble() }.toFloat()
            var x = 0f
            row.forEach { key ->
                val keyWidth = width * (key.widthWeight / totalWeight)
                bounds += KeyBounds(
                    key,
                    RectF(x + gapX, top + gapY, x + keyWidth - gapX, top + rowHeight - gapY),
                )
                x += keyWidth
            }
            top += rowHeight
        }
        keyBounds = bounds
    }

    private val backgroundPaint = Paint()

    override fun onDraw(canvas: Canvas) {
        // 부모 클리핑이 풀려 있으므로 drawColor(캔버스 전체)가 아니라 뷰 영역만 칠한다.
        backgroundPaint.color = theme.background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        if (boundsDirty) {
            rebuildBounds()
            boundsDirty = false
        }
        val radius = dp(8f)
        keyBounds.forEach { (key, rect) ->
            if (key.type == KeyType.SPACER || key.type == KeyType.GHOST) return@forEach
            val pressed = pressedByPointer.containsValue(key)
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
        if (keyPreviewEnabled && variantPopup == null && !is3x4Board()) {
            for (pressed in pressedByPointer.values) {
                if (pressed.type != KeyType.CHAR && pressed.type != KeyType.GHOST) continue
                // 고스트(투명 보정 영역)는 실제 키 위치에서 미리보기를 띄운다.
                val bound = if (pressed.type == KeyType.GHOST) {
                    keyBounds.firstOrNull { it.key.type == KeyType.CHAR && it.key.char == pressed.char }
                } else {
                    keyBounds.firstOrNull { it.key == pressed }
                } ?: continue
                drawKeyPreview(canvas, bound.key, bound.rect)
            }
        }
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
            val y = cell.centerY() - (paint.ascent() + paint.descent()) / 2
            canvas.drawText(popup.options[index], cell.centerX(), y, paint)
        }
    }

    /** 누른 키 위에 확대 키캡을 그린다 (뷰 안쪽으로 클램프). */
    private fun drawKeyPreview(canvas: Canvas, key: Key, rect: RectF) {
        val previewWidth = rect.width() * 1.45f
        val previewHeight = rect.height() * 1.6f
        val cx = rect.centerX().coerceIn(previewWidth / 2 + dp(2f), width - previewWidth / 2 - dp(2f))
        var top = rect.top - dp(4f) - previewHeight
        if (top < dp(2f)) top = dp(2f)
        val popup = RectF(cx - previewWidth / 2, top, cx + previewWidth / 2, top + previewHeight)
        val radius = dp(12f)
        canvas.drawRoundRect(popup, radius, radius, previewBgPaint)
        canvas.drawRoundRect(popup, radius, radius, previewBorderPaint)
        val y = popup.centerY() - (previewTextPaint.ascent() + previewTextPaint.descent()) / 2
        canvas.drawText(key.label, popup.centerX(), y, previewTextPaint)
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
                KeyType.ENTER -> drawEnterIcon(canvas, rect)
                else -> if (key.label.isNotEmpty()) {
                    // 한/영 키와 ".,?!" 같은 긴 라벨은 작은 글자로 표시한다.
                    val paint = if (key.type == KeyType.LANG || key.label.length >= 4) {
                        smallTextPaint
                    } else {
                        textPaint
                    }
                    val y = rect.centerY() - (paint.ascent() + paint.descent()) / 2
                    canvas.drawText(key.label, rect.centerX(), y, paint)
                    naratgulDigit(key)?.let { digit ->
                        canvas.drawText(
                            digit.toString(),
                            rect.right - dp(6f),
                            rect.top + dp(5f) - hintTextPaint.ascent(),
                            hintTextPaint,
                        )
                    }
                }
            }
        }
    }

    private fun drawShiftIcon(canvas: Canvas, rect: RectF) {
        val u = dp(1f)
        val path = Path().apply {
            moveTo(0f, -9f * u)
            lineTo(7.5f * u, -0.5f * u)
            lineTo(3.5f * u, -0.5f * u)
            lineTo(3.5f * u, 8f * u)
            lineTo(-3.5f * u, 8f * u)
            lineTo(-3.5f * u, -0.5f * u)
            lineTo(-7.5f * u, -0.5f * u)
            close()
        }
        canvas.withTranslation(rect.centerX(), rect.centerY()) {
            if (shifted) {
                drawPath(path, iconFillPaint)
            } else {
                drawPath(path, iconPaint)
            }
        }
    }

    private fun drawDeleteIcon(canvas: Canvas, rect: RectF) {
        val u = dp(1f)
        val body = Path().apply {
            moveTo(-10f * u, 0f)
            lineTo(-4f * u, -6.5f * u)
            lineTo(9.5f * u, -6.5f * u)
            lineTo(9.5f * u, 6.5f * u)
            lineTo(-4f * u, 6.5f * u)
            close()
        }
        canvas.withTranslation(rect.centerX(), rect.centerY()) {
            drawPath(body, iconPaint)
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
                // 롤오버: 스페이스를 떼기 전에 다음 키가 눌리면 순서 보존을 위해
                // 대기 중인 스페이스를 먼저 확정한다 (빠른 타이핑에서 어순 역전 방지).
                if (spacePointerId != -1 && !spaceSwiped && pointerId != spacePointerId) {
                    pressedByPointer[spacePointerId]?.let { onKeyListener(it) }
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
                pressedByPointer[pointerId] = key
                downXByPointer[pointerId] = x
                performKeyHaptic()
                performKeySound(key)
                when {
                    // 스페이스는 좌우 스와이프(언어 변경)와 구분해야 하므로 UP에서 입력한다.
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
                    // 나랏글: 길게 누르면 우상단 숫자 입력 (쌍자음 변형 팝업보다 우선).
                    naratgulDigit(key) != null -> {
                        pendingVariant = PendingVariant(pointerId, key, RectF(hit.rect))
                        repeatHandler.postDelayed(digitLongPressRunnable, longPressDelayMs)
                    }
                    // 변형 문자(분수 등)가 있는 키는 롱프레스와 구분하기 위해 UP에서 입력한다.
                    key.type == KeyType.CHAR && KEY_VARIANTS.containsKey(key.char) -> {
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
                val key = pressedByPointer.remove(pointerId)
                downXByPointer.remove(pointerId)
                pendingVariant?.let { pending ->
                    if (pending.pointerId == pointerId) {
                        val popup = variantPopup
                        if (popup != null && popup.pointerId == pointerId) {
                            val choice = popup.options[popup.selected]
                            onKeyListener(Key(KeyType.CHAR, choice, choice.first()))
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
        val pending = pendingVariant ?: return
        val variants = KEY_VARIANTS[pending.key.char] ?: return
        // 한글 쌍자음 팝업은 원래 자음을 빼고 변형만 보여준다 (ㅂ 롱프레스 → ㅃ만).
        val includeOriginal = pending.key.char !in KOREAN_VARIANTS
        val options = buildList {
            if (includeOriginal) add(pending.key.label)
            variants.forEach { add(it.toString()) }
        }
        val anchor = pending.rect
        val columns = minOf(4, options.size)
        val rows = (options.size + columns - 1) / columns
        val cellWidth = maxOf(anchor.width(), dp(44f))
        val cellHeight = maxOf(anchor.height(), dp(44f))
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
            selected = if (includeOriginal) 1 else 0,
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
        repeatHandler.removeCallbacks(repeatDelete)
        cancelPendingVariant()
        super.onDetachedFromWindow()
    }

    companion object {
        private const val NUMBER_ROW_HEIGHT_WEIGHT = 0.85f
        private const val ACCENT = 0xFF3D8BFF.toInt()
        private const val HAPTIC_DURATION_MS = 12L
        private const val KEY_SOUND_VOLUME = 0.5f
        private const val LANG_POPUP_DELAY_MS = 300L
        private const val SPACE_SWIPE_THRESHOLD_DP = 30f
        private const val CHAR_REPEAT_START_MS = 400L
        private const val CHAR_REPEAT_INTERVAL_MS = 60L

        private val REPEATABLE_CHARS = setOf('ㅋ')

        /** 나랏글 키 우상단 숫자: 길게 누르면 해당 숫자가 입력된다. */
        private val NARATGUL_DIGITS = mapOf(
            'ㄱ' to '1', 'ㄴ' to '2', 'ㅏ' to '3',
            'ㄹ' to '4', 'ㅁ' to '5', 'ㅗ' to '6',
            'ㅅ' to '7', 'ㅇ' to '8', 'ㅣ' to '9',
            'ㅡ' to '0',
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
        }
    }
}

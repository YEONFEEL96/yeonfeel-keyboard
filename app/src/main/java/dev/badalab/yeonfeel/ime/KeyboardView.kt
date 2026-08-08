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

    /** 스페이스바 좌우 스와이프로 언어를 바꿀 수 있는지. */
    var languageSwipeEnabled: Boolean = false
    var onLanguageSwipe: (() -> Unit)? = null

    /** 타점 수집 콜백: (키, 키보드 정규화 x·y, 키 중심 대비 상대 x·y). */
    var onTapRecorded: ((Key, Float, Float, Float, Float) -> Unit)? = null

    var koreanLayout: KoreanLayoutType = KoreanLayoutType.DUBEOLSIK
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

    /** 누른 키를 크게 보여주는 미리보기 팝업. */
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
        val amplitude = (1 + hapticStrength * 2.54).toInt().coerceIn(1, 255)
        runCatching {
            vib.vibrate(VibrationEffect.createOneShot(HAPTIC_DURATION_MS, amplitude))
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

    // 롱프레스 변형 문자(분수 등) 팝업 상태
    private class VariantPopupState(
        val pointerId: Int,
        val options: List<String>,
        val panel: RectF,
        val cells: List<RectF>,
        var selected: Int = 0,
    )

    private data class PendingVariant(val pointerId: Int, val key: Key, val rect: RectF)

    private var pendingVariant: PendingVariant? = null
    private var variantPopup: VariantPopupState? = null
    private var variantPopupWindow: android.widget.PopupWindow? = null
    private val longPressRunnable = Runnable { showVariantPopup() }

    /** 팝업 창에 그려지는 내용. 셀 좌표는 키보드 로컬 기준이므로 패널 원점만큼 이동해 그린다. */
    private inner class VariantPopupContent : View(context) {
        override fun onDraw(canvas: Canvas) {
            val popup = variantPopup ?: return
            canvas.translate(-popup.panel.left, -popup.panel.top)
            drawVariantPopup(canvas, popup)
        }
    }

    private val repeatHandler = Handler(Looper.getMainLooper())
    private val repeatDelete = object : Runnable {
        override fun run() {
            onKeyListener(Key(KeyType.DELETE, "⌫"))
            repeatHandler.postDelayed(this, 50L)
        }
    }

    // ㅋ 등 반복 입력 키: 꾹 누르면 연속 입력된다.
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
        mode == LayoutMode.SYMBOLS -> true
        mode == LayoutMode.KOREAN && koreanLayout == KoreanLayoutType.SEBEOLSIK_390 -> false
        else -> showNumberRow
    }

    private fun rebuildBounds() {
        val rows = KeyboardLayouts.rows(
            mode, shifted, showNumberRow, symbolsPage, showLangKey, koreanLayout, shiftNumberRowSymbols,
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
        if (keyPreviewEnabled && variantPopup == null) {
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
        canvas.drawRoundRect(popup.panel, radius, radius, previewBgPaint)
        canvas.drawRoundRect(popup.panel, radius, radius, previewBorderPaint)
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
                    // 한/영 키는 작은 글자로 표시한다.
                    val paint = if (key.type == KeyType.LANG) smallTextPaint else textPaint
                    val y = rect.centerY() - (paint.ascent() + paint.descent()) / 2
                    canvas.drawText(key.label, rect.centerX(), y, paint)
                }
            }
        }
    }

    /** 시프트: 집 모양 화살표 외곽선. 활성 상태면 액센트 색으로 채운다. */
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

    /** 삭제: 왼쪽이 뾰족한 오각형 + 가운데 ×. */
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

    /** 엔터: ↵ 모양 꺾인 화살표. */
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
                when {
                    // 스페이스는 좌우 스와이프(언어 변경)와 구분해야 하므로 UP에서 입력한다.
                    key.type == KeyType.SPACE -> {
                        spacePointerId = pointerId
                        spaceSwiped = false
                    }
                    key.type == KeyType.DELETE -> {
                        onKeyListener(key)
                        deletePointerId = pointerId
                        repeatHandler.postDelayed(repeatDelete, 400L)
                    }
                    // 변형 문자(분수 등)가 있는 키는 롱프레스와 구분하기 위해 UP에서 입력한다.
                    key.type == KeyType.CHAR && KEY_VARIANTS.containsKey(key.char) -> {
                        pendingVariant = PendingVariant(pointerId, key, RectF(hit.rect))
                        repeatHandler.postDelayed(longPressRunnable, LONG_PRESS_MS)
                    }
                    // ㅋ 등은 꾹 누르면 반복 입력된다.
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
                if (spacePointerId != -1 && languageSwipeEnabled && !spaceSwiped) {
                    val index = event.findPointerIndex(spacePointerId)
                    val startX = downXByPointer[spacePointerId]
                    if (index >= 0 && startX != null &&
                        kotlin.math.abs(event.getX(index) - startX) > dp(48f)
                    ) {
                        spaceSwiped = true
                        performKeyHaptic()
                        onLanguageSwipe?.invoke()
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
                if (key?.type == KeyType.SPACE && pointerId == spacePointerId) {
                    if (!spaceSwiped) onKeyListener(key)
                    spacePointerId = -1
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
    }

    private fun cancelPendingVariant() {
        repeatHandler.removeCallbacks(longPressRunnable)
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
        val options = listOf(pending.key.label) + variants.map { it.toString() }
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
        // 첫 변형(위첨자)을 기본 선택으로 — 길게 눌렀다 그대로 떼면 위첨자가 입력된다.
        variantPopup = VariantPopupState(pending.pointerId, options, panel, cells, selected = 1)
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
        // 손가락이 아직 팝업 아래(원래 키 위)에 있으면 선택을 바꾸지 않는다 —
        // 기본 선택(위첨자)이 미세 떨림으로 원래 숫자 셀로 넘어가는 것을 막는다.
        if (y > popup.panel.bottom + dp(6f)) return popup.selected
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
        private const val LONG_PRESS_MS = 350L
        private const val CHAR_REPEAT_START_MS = 400L
        private const val CHAR_REPEAT_INTERVAL_MS = 60L

        /** 꾹 누르면 반복 입력되는 문자. */
        private val REPEATABLE_CHARS = setOf('ㅋ')

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

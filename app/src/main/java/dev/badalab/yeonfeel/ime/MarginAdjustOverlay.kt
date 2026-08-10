package dev.badalab.yeonfeel.ime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import dev.badalab.yeonfeel.R
import dev.badalab.yeonfeel.settings.KeyboardSettings

/**
 * 키보드 여백 조정 오버레이. 키 영역의 상하좌우 가장자리에 화살표 핸들을 띄우고,
 * 드래그로 여백을 실시간 조정한다.
 *
 * - 좌우 핸들: 양쪽 여백을 함께 움직여 키보드가 항상 중앙 정렬을 유지한다.
 * - 상하 핸들: 잡은 가장자리만 움직인다. 반대쪽 가장자리는 화면상 고정되므로
 *   여백과 키 영역 높이가 함께 변한다 (가장자리 고정 방식).
 */
@SuppressLint("ViewConstructor")
class MarginAdjustOverlay(
    context: Context,
    private var topDp: Int,
    private var bottomDp: Int,
    private var sideDp: Int,
    private var heightDp: Int,
    private val theme: KeyboardTheme,
    private val splitActive: Boolean = false,
    private var splitGapPercent: Int = KeyboardSettings.SPLIT_GAP_DEFAULT,
    private val listener: Listener,
) : View(context) {

    interface Listener {
        /** 드래그 중 실시간 호출. 키 영역 패딩·높이를 즉시 반영한다. */
        fun onMarginsChanged(topDp: Int, bottomDp: Int, sideDp: Int, heightDp: Int)

        /** 드래그가 끝나거나 초기화됐을 때 — 설정에 저장한다. */
        fun onCommit(topDp: Int, bottomDp: Int, sideDp: Int, heightDp: Int)

        /** 분할 간격 드래그 중 실시간 호출. */
        fun onSplitGapChanged(percent: Int)

        /** 분할 간격 드래그 종료 — 설정에 저장한다. */
        fun onSplitGapCommitted(percent: Int)

        fun onDone()
    }

    private enum class Handle { TOP, BOTTOM, LEFT, RIGHT }

    private val density = resources.displayMetrics.density

    /**
     * 높이 상한은 화면에서 동적으로 계산한다 — 위 핸들을 끌어올리는 만큼 커지되
     * 앱 영역이 보이도록 상단 일부를 남긴다. 가로에서는 KeyboardContainerView가
     * 키 높이를 화면 40%로 제한하므로(effectiveHeightDp) 그 상한과 일치시킨다.
     */
    private val maxHeightDp: Int = run {
        val cfg = resources.configuration
        if (cfg.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            (cfg.screenHeightDp * 2 / 5).coerceAtLeast(120)
        } else {
            (resources.displayMetrics.heightPixels / density).toInt() - RESERVED_SCREEN_TOP_DP
        }
    }

    private val highlightPaint = Paint().apply { color = 0x80FFFFFF.toInt() }
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ACCENT
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT }
    private val gripPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        strokeWidth = dp(2f)
        strokeCap = Paint.Cap.ROUND
    }
    private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val buttonTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = sp(14f)
    }

    private var activeHandle: Handle? = null
    private var pendingButton: RectF? = null
    private var downRawX = 0f
    private var downRawY = 0f
    private var downSplitX = 0f
    private var startTop = 0
    private var startBottom = 0
    private var startSide = 0
    private var startHeight = 0

    private val resetLabel = context.getString(R.string.adjust_reset)
    private val doneLabel = context.getString(R.string.adjust_done)
    private var resetRect = RectF()
    private var doneRect = RectF()

    private fun dp(v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    private fun sp(v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    /**
     * 래퍼(키 영역 + 상하 여백)와 같은 높이로 스스로 측정한다.
     * wrap_content FrameLayout 안에서 MATCH_PARENT는 화면 전체로 풀리므로 쓸 수 없다.
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        // 가로에서 저장 높이가 화면 상한을 넘으면 키 영역은 상한으로 그려지므로
        // 오버레이도 같은 높이로 맞춰 핸들이 실제 가장자리에 오게 한다.
        val drawnHeight = minOf(heightDp, maxHeightDp)
        val height = ((drawnHeight + topDp + bottomDp) * density).toInt()
        setMeasuredDimension(width, height)
    }

    /** 분할 상태에서 우측 블록 영역 (KeyboardView.layoutSplitRow와 같은 수식). */
    private fun rightBlockArea(area: RectF): RectF {
        val g = splitGapPercent / 100f
        val margin = area.width() * 0.03f
        val usable = area.width() - margin * 2
        val innerLeft = area.left + margin + usable * (0.5f + g) / (1f + g)
        return RectF(innerLeft, area.top, area.right - margin, area.bottom)
    }

    /** 조정 UI를 얹는 영역 — 분할 상태에서는 우측 블록만 기준으로 잡는다. */
    private fun displayArea(): RectF =
        if (splitActive) rightBlockArea(keyArea()) else keyArea()

    private fun keyArea(): RectF = RectF(
        sideDp * density,
        topDp * density,
        width - sideDp * density,
        height - bottomDp * density,
    )

    override fun onDraw(canvas: Canvas) {
        val area = displayArea()

        // 여백 영역은 원래 배경 그대로 두고, 조정 영역만 반투명 흰색으로 살짝 밝힌다 —
        // 조정 대상이 어디인지 드러나고 초기화/완료 버튼도 잘 보인다.
        canvas.drawRect(area, highlightPaint)

        canvas.drawRect(area, edgePaint)

        handlePoints(area).forEach { (handle, point) ->
            val horizontal = handle == Handle.TOP || handle == Handle.BOTTOM
            drawHandle(canvas, point.first, point.second, horizontal)
        }

        // 중앙의 초기화/완료 버튼
        val pillW = dp(84f)
        val pillH = dp(40f)
        val gap = dp(8f)
        val cy = area.centerY()
        resetRect = RectF(area.centerX() - pillW - gap / 2, cy - pillH / 2, area.centerX() - gap / 2, cy + pillH / 2)
        doneRect = RectF(area.centerX() + gap / 2, cy - pillH / 2, area.centerX() + pillW + gap / 2, cy + pillH / 2)

        buttonPaint.color = theme.specialKey
        canvas.drawRoundRect(resetRect, pillH / 2, pillH / 2, buttonPaint)
        buttonTextPaint.color = theme.text
        drawCenteredText(canvas, resetLabel, resetRect, buttonTextPaint)

        buttonPaint.color = ACCENT
        canvas.drawRoundRect(doneRect, pillH / 2, pillH / 2, buttonPaint)
        buttonTextPaint.color = 0xFFFFFFFF.toInt()
        drawCenteredText(canvas, doneLabel, doneRect, buttonTextPaint)
    }

    /** 그립 핸들: 라운드 사각형 안에 짧은 선 3개. 상하는 가로, 좌우는 세로 방향. */
    private fun drawHandle(canvas: Canvas, cx: Float, cy: Float, horizontal: Boolean) {
        val longHalf = dp(28f)
        val shortHalf = dp(13f)
        val rect = if (horizontal) {
            RectF(cx - longHalf, cy - shortHalf, cx + longHalf, cy + shortHalf)
        } else {
            RectF(cx - shortHalf, cy - longHalf, cx + shortHalf, cy + longHalf)
        }
        canvas.drawRoundRect(rect, dp(8f), dp(8f), handlePaint)

        val lineHalf = dp(14f)
        val gap = dp(4.5f)
        for (offset in intArrayOf(-1, 0, 1)) {
            if (horizontal) {
                val y = cy + offset * gap
                canvas.drawLine(cx - lineHalf, y, cx + lineHalf, y, gripPaint)
            } else {
                val x = cx + offset * gap
                canvas.drawLine(x, cy - lineHalf, x, cy + lineHalf, gripPaint)
            }
        }
    }

    private fun drawCenteredText(canvas: Canvas, text: String, rect: RectF, paint: Paint) {
        val y = rect.centerY() - (paint.ascent() + paint.descent()) / 2
        canvas.drawText(text, rect.centerX(), y, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pendingButton = null
                activeHandle = null
                when {
                    doneRect.contains(event.x, event.y) -> pendingButton = doneRect
                    resetRect.contains(event.x, event.y) -> pendingButton = resetRect
                    else -> {
                        activeHandle = findHandle(event.x, event.y) ?: return true
                        downRawX = event.rawX
                        downRawY = event.rawY
                        downSplitX = rightBlockArea(keyArea()).left
                        startTop = topDp
                        startBottom = bottomDp
                        startSide = sideDp
                        startHeight = heightDp
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val handle = activeHandle ?: return true
                if (splitActive && handle == Handle.LEFT) {
                    // 핸들 위치(우측 블록 안쪽 가장자리)를 간격 비율로 역산 — 좌우가 대칭으로 움직인다.
                    val area = keyArea()
                    val margin = area.width() * 0.03f
                    val usable = area.width() - margin * 2
                    val x = downSplitX + (event.rawX - downRawX)
                    val t = ((x - area.left - margin) / usable).coerceIn(0.52f, 0.9f)
                    val g = (t - 0.5f) / (1f - t)
                    splitGapPercent = (g * 100).toInt()
                        .coerceIn(KeyboardSettings.SPLIT_GAP_MIN, KeyboardSettings.SPLIT_GAP_MAX)
                    listener.onSplitGapChanged(splitGapPercent)
                    invalidate()
                    return true
                }
                val dxDp = ((event.rawX - downRawX) / density).toInt()
                val dyDp = ((event.rawY - downRawY) / density).toInt()
                when (handle) {
                    // 위 가장자리 = 순수 높이 조절. 끌어올린 만큼 창이 위로 자라고 아래는 고정된다.
                    Handle.TOP -> {
                        heightDp = (startHeight - dyDp)
                            .coerceIn(KeyboardSettings.HEIGHT_MIN, maxHeightDp)
                    }
                    // 아래 가장자리만 이동: 위 가장자리 고정.
                    Handle.BOTTOM -> {
                        // 가로 등 startHeight가 maxHeightDp보다 큰 경우 lo>hi로 뒤집힐 수
                        // 있어 상한을 lo 이상으로 고정한다 (빈 범위 예외 방지).
                        val lo = maxOf(-startBottom, startHeight - maxHeightDp)
                        val hi = minOf(
                            KeyboardSettings.MARGIN_BOTTOM_MAX - startBottom,
                            startHeight - KeyboardSettings.HEIGHT_MIN,
                        )
                        val d = (-dyDp).coerceIn(lo, maxOf(lo, hi))
                        bottomDp = startBottom + d
                        heightDp = startHeight - d
                    }
                    Handle.LEFT -> sideDp = (startSide + dxDp).coerceIn(0, KeyboardSettings.MARGIN_SIDE_MAX)
                    Handle.RIGHT -> sideDp = (startSide - dxDp).coerceIn(0, KeyboardSettings.MARGIN_SIDE_MAX)
                }
                listener.onMarginsChanged(topDp, bottomDp, sideDp, heightDp)
                requestLayout()
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                val button = pendingButton
                when {
                    button === doneRect && doneRect.contains(event.x, event.y) -> {
                        listener.onCommit(topDp, bottomDp, sideDp, heightDp)
                        listener.onDone()
                    }
                    button === resetRect && resetRect.contains(event.x, event.y) -> {
                        topDp = 0
                        bottomDp = KeyboardSettings.MARGIN_BOTTOM_DEFAULT
                        sideDp = 0
                        heightDp = KeyboardSettings.HEIGHT_DEFAULT
                        if (splitActive) {
                            splitGapPercent = KeyboardSettings.SPLIT_GAP_DEFAULT
                            listener.onSplitGapChanged(splitGapPercent)
                            listener.onSplitGapCommitted(splitGapPercent)
                        }
                        listener.onMarginsChanged(topDp, bottomDp, sideDp, heightDp)
                        listener.onCommit(topDp, bottomDp, sideDp, heightDp)
                        requestLayout()
                        invalidate()
                    }
                    splitActive && activeHandle == Handle.LEFT ->
                        listener.onSplitGapCommitted(splitGapPercent)
                    activeHandle != null -> listener.onCommit(topDp, bottomDp, sideDp, heightDp)
                }
                pendingButton = null
                activeHandle = null
            }
            MotionEvent.ACTION_CANCEL -> {
                if (splitActive && activeHandle == Handle.LEFT) listener.onSplitGapCommitted(splitGapPercent)
                else if (activeHandle != null) listener.onCommit(topDp, bottomDp, sideDp, heightDp)
                pendingButton = null
                activeHandle = null
            }
        }
        return true
    }

    /**
     * 핸들 표시·터치 좌표. 화면 가장자리의 시스템 제스처(뒤로 가기·홈)와 겹치지 않도록
     * 가장자리에서 최소 28dp 안쪽으로 클램프한다.
     */
    private fun handlePoints(area: RectF): Map<Handle, Pair<Float, Float>> {
        val inset = dp(28f)
        val points = mutableMapOf(
            Handle.TOP to (area.centerX() to area.top),
            Handle.BOTTOM to (area.centerX() to minOf(area.bottom, height - inset)),
            Handle.LEFT to (maxOf(area.left, inset) to area.centerY()),
            Handle.RIGHT to (minOf(area.right, width - inset) to area.centerY()),
        )
        return points
    }

    private fun findHandle(x: Float, y: Float): Handle? {
        val slop = dp(36f)
        return handlePoints(displayArea()).entries.firstOrNull { (_, point) ->
            val dx = x - point.first
            val dy = y - point.second
            dx * dx + dy * dy <= slop * slop
        }?.key
    }

    companion object {
        private const val ACCENT = 0xFF3D8BFF.toInt()

        /** 위로 끌어올려도 남겨 두는 화면 상단 영역(dp). 앱 콘텐츠·툴바 가시성 확보용. */
        private const val RESERVED_SCREEN_TOP_DP = 160
    }
}

package dev.badalab.yeonfeel.ime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.CornerPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.TypedValue
import android.view.View

/**
 * 키보드 기능 키 아이콘과 같은 룩앤필(1.8dp 스트로크·라운드 캡)로 그리는 아이콘 뷰.
 * 클립보드 패널 헤더 등 키보드 밖 UI에서 쓴다.
 */
@SuppressLint("ViewConstructor")
class GlyphIconView(
    context: Context,
    private val type: Type,
    color: Int,
    private val sizeScale: Float = 1f,
) : View(context) {

    enum class Type { KEYBOARD, PIN, DELETE, CANCEL, SETTINGS, CLIPBOARD }

    var color: Int = color
        set(value) {
            field = value
            paint.color = value
            dotPaint.color = value
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.8f) * sizeScale
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        pathEffect = CornerPathEffect(dp(1.2f))
        this.color = color
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.2f) * sizeScale
        strokeCap = Paint.Cap.ROUND
        this.color = color
    }

    private fun dp(v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            (dp(40f) * sizeScale).toInt(),
            (dp(36f) * sizeScale).toInt(),
        )
    }

    override fun onDraw(canvas: Canvas) {
        val u = dp(1f) * sizeScale
        val save = canvas.save()
        canvas.translate(width / 2f, height / 2f)
        when (type) {
            Type.KEYBOARD -> drawKeyboard(canvas, u)
            Type.PIN -> drawPin(canvas, u)
            Type.DELETE -> drawTrash(canvas, u)
            Type.CANCEL -> drawCancel(canvas, u)
            Type.SETTINGS -> drawGear(canvas, u)
            Type.CLIPBOARD -> drawClipboard(canvas, u)
        }
        canvas.restoreToCount(save)
    }

    private fun drawKeyboard(canvas: Canvas, u: Float) {
        canvas.drawRoundRect(RectF(-9.5f * u, -7f * u, 9.5f * u, 7f * u), 3f * u, 3f * u, paint)
        floatArrayOf(-5f, 0f, 5f).forEach { x ->
            canvas.drawPoint(x * u, -2.5f * u, dotPaint)
        }
        canvas.drawLine(-4f * u, 3f * u, 4f * u, 3f * u, paint)
    }

    private fun drawPin(canvas: Canvas, u: Float) {
        canvas.rotate(40f)
        // 머리 막대
        canvas.drawLine(-4.5f * u, -8f * u, 4.5f * u, -8f * u, paint)
        // 몸통 (아래로 벌어지는 사다리꼴)
        val body = Path().apply {
            moveTo(-2.8f * u, -8f * u)
            lineTo(-2.8f * u, -3f * u)
            lineTo(-5.5f * u, 0f)
            lineTo(5.5f * u, 0f)
            lineTo(2.8f * u, -3f * u)
            lineTo(2.8f * u, -8f * u)
        }
        canvas.drawPath(body, paint)
        // 바늘
        canvas.drawLine(0f, 0f, 0f, 7f * u, paint)
    }

    private fun drawTrash(canvas: Canvas, u: Float) {
        // 뚜껑과 손잡이
        canvas.drawLine(-8f * u, -5f * u, 8f * u, -5f * u, paint)
        canvas.drawLine(-2.5f * u, -8f * u, 2.5f * u, -8f * u, paint)
        // 몸통
        val body = Path().apply {
            moveTo(-6.5f * u, -5f * u)
            lineTo(-5.5f * u, 8f * u)
            lineTo(5.5f * u, 8f * u)
            lineTo(6.5f * u, -5f * u)
        }
        canvas.drawPath(body, paint)
        // 세로 골
        canvas.drawLine(-2f * u, -2f * u, -2f * u, 5f * u, paint)
        canvas.drawLine(2f * u, -2f * u, 2f * u, 5f * u, paint)
    }

    private fun drawCancel(canvas: Canvas, u: Float) {
        canvas.drawLine(-6f * u, -6f * u, 6f * u, 6f * u, paint)
        canvas.drawLine(-6f * u, 6f * u, 6f * u, -6f * u, paint)
    }

    /** 설정: 원 + 중심 구멍 + 8개 톱니 기어. */
    private fun drawGear(canvas: Canvas, u: Float) {
        canvas.drawCircle(0f, 0f, 6f * u, paint)
        canvas.drawCircle(0f, 0f, 2.4f * u, paint)
        for (i in 0 until 8) {
            val angle = Math.toRadians(i * 45.0 + 22.5)
            val cos = kotlin.math.cos(angle).toFloat()
            val sin = kotlin.math.sin(angle).toFloat()
            canvas.drawLine(cos * 6.4f * u, sin * 6.4f * u, cos * 8.6f * u, sin * 8.6f * u, paint)
        }
    }

    /** 클립보드: 위에 클립 탭이 달린 라운드 사각형. */
    private fun drawClipboard(canvas: Canvas, u: Float) {
        canvas.drawRoundRect(
            RectF(-7f * u, -6f * u, 7f * u, 8.5f * u),
            3.5f * u,
            3.5f * u,
            paint,
        )
        canvas.drawRoundRect(
            RectF(-3.2f * u, -8.5f * u, 3.2f * u, -3.8f * u),
            2f * u,
            2f * u,
            paint,
        )
    }
}

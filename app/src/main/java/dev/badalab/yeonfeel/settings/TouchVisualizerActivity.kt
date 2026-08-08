package dev.badalab.yeonfeel.settings

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.View
import dev.badalab.yeonfeel.R
import dev.badalab.yeonfeel.debug.TouchStatsStore
import dev.badalab.yeonfeel.ime.KeyType
import dev.badalab.yeonfeel.ime.KeyboardLayouts
import dev.badalab.yeonfeel.ime.LayoutMode

/** 수집된 타점을 현재 한국어 자판 위에 흩뿌려 보여준다. */
class TouchVisualizerActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = KeyboardSettings(this)
        val store = TouchStatsStore(this)
        title = getString(R.string.debug_touch_visualizer)

        val ui = SettingComponents(this)
        ui.header(getString(R.string.debug_touch_visualizer))
        ui.caption(getString(R.string.debug_touch_count, store.totalCount()))
        // 카드 라운드 모서리 안쪽에 들어오도록 여백을 두고 그린다.
        val pad = ui.dp(14)
        ui.card(
            android.widget.LinearLayout(this).apply {
                setPadding(pad, pad, pad, pad)
                addView(
                    VisualizerView(this@TouchVisualizerActivity, settings, store.all()),
                    android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    ),
                )
            },
        )
        setContentView(ui.root())
    }

    @SuppressLint("ViewConstructor")
    private class VisualizerView(
        activity: Activity,
        private val settings: KeyboardSettings,
        private val samples: List<TouchStatsStore.Sample>,
    ) : View(activity) {

        private val rows = KeyboardLayouts.rows(
            LayoutMode.KOREAN,
            shifted = false,
            showNumberRow = settings.showNumberRow,
            symbolsPage = 0,
            showLangKey = true,
            koreanLayout = settings.koreanLayout,
            shiftNumberRowSymbols = settings.shiftNumberRowSymbols,
        )

        private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            color = 0xFFC6CAD2.toInt()
        }
        private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = 11 * resources.displayMetrics.scaledDensity
            color = 0xFF9AA0AC.toInt()
        }
        private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x553D8BFF
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val screenWidthDp = resources.configuration.screenWidthDp.toFloat()
            val height = (width * (settings.keyboardHeightDp / screenWidthDp)).toInt()
            setMeasuredDimension(width, height)
        }

        override fun onDraw(canvas: Canvas) {
            // 키 외곽선: KeyboardView와 같은 배치 규칙 (숫자 열 85% 높이)
            val heightWeights = FloatArray(rows.size) { 1f }
            if (settings.showNumberRow && rows.isNotEmpty()) heightWeights[0] = 0.85f
            val unit = height.toFloat() / heightWeights.sum()
            val gap = 3f * resources.displayMetrics.density
            var top = 0f
            rows.forEachIndexed { rowIdx, row ->
                val rowHeight = unit * heightWeights[rowIdx]
                val totalWeight = row.sumOf { it.widthWeight.toDouble() }.toFloat()
                var x = 0f
                row.forEach { key ->
                    val keyWidth = width * (key.widthWeight / totalWeight)
                    if (key.type != KeyType.SPACER && key.type != KeyType.GHOST) {
                        val rect = RectF(x + gap, top + gap, x + keyWidth - gap, top + rowHeight - gap)
                        canvas.drawRoundRect(rect, 8f, 8f, keyPaint)
                        if (key.label.isNotEmpty()) {
                            canvas.drawText(
                                key.label,
                                rect.centerX(),
                                rect.centerY() - (labelPaint.ascent() + labelPaint.descent()) / 2,
                                labelPaint,
                            )
                        }
                    }
                    x += keyWidth
                }
                top += rowHeight
            }

            // 타점
            val radius = 2.5f * resources.displayMetrics.density
            samples.forEach { sample ->
                canvas.drawCircle(sample.ax * width, sample.ay * height, radius, dotPaint)
            }
        }
    }
}

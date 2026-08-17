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
import dev.badalab.yeonfeel.ime.KeyGeometry
import dev.badalab.yeonfeel.ime.KeyType
import dev.badalab.yeonfeel.ime.KeyboardLayouts
import dev.badalab.yeonfeel.ime.LayoutMode
import dev.badalab.yeonfeel.ime.TouchModel

/**
 * 수집된 타점을 현재 한국어 자판 위에 흩뿌려 보여준다.
 * 배치가 달라지는 화면 상태(가로·분할)는 보드 서픽스로 구분 수집되므로,
 * 표본이 있는 상태마다 해당 배치를 재구성한 자판을 따로 그린다.
 */
class TouchVisualizerActivity : Activity() {

    private data class Variant(
        val suffix: String,
        val labelRes: Int,
        val landscape: Boolean,
        val split: Boolean,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = KeyboardSettings(this)
        val store = TouchStatsStore(this)
        title = getString(R.string.debug_touch_visualizer)

        val ui = SettingComponents(this)
        ui.header(getString(R.string.debug_touch_visualizer))
        ui.caption(getString(R.string.debug_touch_count, store.totalCount()))

        val base = "KO_" + settings.koreanLayout.name
        val model = TouchModel(store)
        val variants = listOf(
            Variant("", R.string.debug_board_portrait, landscape = false, split = false),
            Variant("|split", R.string.debug_board_portrait_split, landscape = false, split = true),
            Variant("|land", R.string.debug_board_landscape, landscape = true, split = false),
            Variant("|land|split", R.string.debug_board_landscape_split, landscape = true, split = true),
        )
        // 표본이 있는 화면 상태만 그리되, 아무것도 없으면 세로 자판을 빈 채로 보여준다.
        val shown = variants.filter { store.forBoard(base + it.suffix).isNotEmpty() }
            .ifEmpty { variants.take(1) }

        // 카드 라운드 모서리 안쪽에 들어오도록 여백을 두고 그린다.
        val pad = ui.dp(14)
        shown.forEach { variant ->
            val board = base + variant.suffix
            val samples = store.forBoard(board)
            ui.caption(
                getString(R.string.debug_board_samples, getString(variant.labelRes), samples.size),
            )
            ui.card(
                android.widget.LinearLayout(this).apply {
                    setPadding(pad, pad, pad, pad)
                    addView(
                        VisualizerView(
                            this@TouchVisualizerActivity,
                            settings,
                            variant.landscape,
                            variant.split,
                            samples,
                            model.statsFor(board),
                        ),
                        android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                        ),
                    )
                },
            )
        }
        ui.show()
    }

    @SuppressLint("ViewConstructor")
    private class VisualizerView(
        activity: Activity,
        private val settings: KeyboardSettings,
        private val landscape: Boolean,
        private val split: Boolean,
        private val samples: List<TouchStatsStore.Sample>,
        private val stats: Map<String, TouchModel.KeyStat>,
    ) : View(activity) {

        private val is3x4 = settings.koreanLayout in setOf(
            KoreanLayoutType.CHUNJIIN,
            KoreanLayoutType.NARATGUL,
            KoreanLayoutType.NARATGUL_CENTER,
        )

        // 실제 자판과 같은 규칙: 가로 모드의 QWERTY류는 숫자 열을 내린다.
        private val showNumberRow = settings.showNumberRow && !(landscape && !is3x4)

        private val rows = KeyboardLayouts.rows(
            LayoutMode.KOREAN,
            shifted = false,
            showNumberRow = showNumberRow,
            symbolsPage = 0,
            showLangKey = settings.koreanEnabled && settings.englishEnabled &&
                settings.languageSwitchMethod != LanguageSwitchMethod.SWIPE,
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
        private val ellipsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = 0xCCFF6D3D.toInt()
        }
        private val muPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFF6D3D.toInt()
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val config = resources.configuration
            // 자판 비율 근사: 가로 자판은 화면의 긴 변이 폭이 된다.
            val boardWidthDp = if (landscape) {
                maxOf(config.screenWidthDp, config.screenHeightDp).toFloat()
            } else {
                config.screenWidthDp.toFloat()
            }
            val height = (width * (settings.keyboardHeightDp / boardWidthDp)).toInt()
            setMeasuredDimension(width, height)
        }

        private val statRects = mutableListOf<Pair<String, RectF>>()

        override fun onDraw(canvas: Canvas) {
            statRects.clear()
            val density = resources.displayMetrics.density
            // 키 외곽선: KeyboardView와 같은 배치 계산기를 써서 실제 자판과 일치시킨다.
            val placed = KeyGeometry.place(
                rows,
                width.toFloat(),
                height.toFloat(),
                gapX = 3f * density,
                gapY = (if (landscape) 4f else 6.5f) * density,
                compactNumberRow = showNumberRow && !is3x4,
                split = split && !is3x4,
                splitGapRatio = settings.splitGapPercent / 100f,
            )
            placed.forEach { (key, rect) ->
                if (key.type == KeyType.SPACER || key.type == KeyType.GHOST) return@forEach
                canvas.drawRoundRect(rect, 8f, 8f, keyPaint)
                if (key.label.isNotEmpty()) {
                    canvas.drawText(
                        key.label,
                        rect.centerX(),
                        rect.centerY() - (labelPaint.ascent() + labelPaint.descent()) / 2,
                        labelPaint,
                    )
                }
                // 개인화 분포 표시는 타점 위에 얹기 위해 위치만 모아 둔다.
                if (key.type == KeyType.CHAR) {
                    statRects.add(key.char.toString() to RectF(rect))
                }
            }

            val radius = 2.5f * density
            samples.forEach { sample ->
                canvas.drawCircle(sample.ax * width, sample.ay * height, radius, dotPaint)
            }

            // 개인화 평균(점)과 1σ 타원 — 타점 위 오버레이로 그린다.
            statRects.forEach { (keyId, rect) ->
                val stat = stats[keyId] ?: return@forEach
                if (stat.n < TouchModel.MIN_SAMPLES) return@forEach
                val p = TouchModel.effective(stat)
                val cx = rect.centerX() + p[0] * rect.width()
                val cy = rect.centerY() + p[1] * rect.height()
                canvas.drawOval(
                    cx - p[2] * rect.width(),
                    cy - p[3] * rect.height(),
                    cx + p[2] * rect.width(),
                    cy + p[3] * rect.height(),
                    ellipsePaint,
                )
                canvas.drawCircle(cx, cy, 4f, muPaint)
            }
        }
    }
}

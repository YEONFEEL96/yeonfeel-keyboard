package dev.badalab.yeonfeel.settings

import android.annotation.SuppressLint
import android.app.Activity
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import dev.badalab.yeonfeel.R
import dev.badalab.yeonfeel.ime.KeyboardTheme

/** 고대비: 색상 스타일을 실제 키캡 미리보기로 선택한다. */
class HighContrastSettingsActivity : Activity() {

    private lateinit var settings: KeyboardSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = KeyboardSettings(this)
        title = getString(R.string.settings_high_contrast_short)
        buildUi()
    }

    private fun buildUi() {
        val ui = SettingComponents(this)
        ui.header(getString(R.string.settings_high_contrast_short))

        ui.card(
            ui.switchRow(getString(R.string.settings_high_contrast), settings.highContrast) { checked, _ ->
                settings.highContrast = checked
            },
        )

        ui.caption(getString(R.string.theme_hc_style_title))
        val styles = listOf(
            HighContrastStyle.DEFAULT to getString(R.string.hc_style_default),
            HighContrastStyle.YELLOW_BLACK to getString(R.string.hc_style_yellow_black),
            HighContrastStyle.BLACK_WHITE to getString(R.string.hc_style_black_white),
            HighContrastStyle.BLACK_YELLOW to getString(R.string.hc_style_black_yellow),
        )
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12))
        }
        styles.chunked(2).forEach { rowStyles ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowStyles.forEach { (style, label) ->
                row.addView(
                    buildStyleTile(ui, style, label),
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        setMargins(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(4))
                    },
                )
            }
            grid.addView(row)
        }
        ui.card(grid)

        setContentView(ui.root())
    }

    private fun buildStyleTile(ui: SettingComponents, style: HighContrastStyle, label: String): View {
        val theme = KeyboardTheme.of(resolveDark(), highContrast = true, style = style)
        val selected = settings.highContrastStyle == style
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            contentDescription = label
            setOnClickListener {
                settings.highContrastStyle = style
                settings.highContrast = true
                buildUi()
            }
        }
        tile.addView(
            PalettePreviewView(this, theme, selected),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ui.dp(64)),
        )
        tile.addView(TextView(this).apply {
            text = label
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(if (selected) SettingComponents.ACCENT else SettingComponents.SUB_TEXT)
            setPadding(0, ui.dp(4), 0, 0)
        })
        return tile
    }

    private fun resolveDark(): Boolean = when (settings.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM ->
            resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
    }

    /** 실제 키캡 3개를 해당 팔레트로 그리는 미리보기 타일. */
    @SuppressLint("ViewConstructor")
    private class PalettePreviewView(
        activity: Activity,
        private val theme: KeyboardTheme,
        private val selected: Boolean,
    ) : View(activity) {

        private val density = resources.displayMetrics.density
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.background }
        private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.key }
        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * density
            color = theme.keyBorder ?: 0
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = 14 * resources.displayMetrics.scaledDensity
            color = theme.text
        }
        private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * density
            color = SettingComponents.ACCENT
        }

        override fun onDraw(canvas: Canvas) {
            val radius = 10f * density
            canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), radius, radius, bgPaint)

            val labels = listOf("ㄱ", "ㄴ", "ㄷ")
            val gap = 6f * density
            val keyWidth = (width - gap * (labels.size + 1)) / labels.size
            labels.forEachIndexed { index, label ->
                val left = gap + index * (keyWidth + gap)
                val rect = RectF(left, height * 0.22f, left + keyWidth, height * 0.78f)
                canvas.drawRoundRect(rect, 6f * density, 6f * density, keyPaint)
                if (theme.keyBorder != null) {
                    canvas.drawRoundRect(rect, 6f * density, 6f * density, borderPaint)
                }
                val y = rect.centerY() - (textPaint.ascent() + textPaint.descent()) / 2
                canvas.drawText(label, rect.centerX(), y, textPaint)
            }

            if (selected) {
                val inset = 1.5f * density
                canvas.drawRoundRect(
                    RectF(inset, inset, width - inset, height - inset),
                    radius,
                    radius,
                    selectionPaint,
                )
            }
        }
    }
}

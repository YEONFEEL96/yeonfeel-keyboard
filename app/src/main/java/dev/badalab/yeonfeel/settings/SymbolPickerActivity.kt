package dev.badalab.yeonfeel.settings

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import dev.badalab.yeonfeel.R

/** 기호 선택 화면. 스페이스바 오른쪽(즐겨찾기)·왼쪽 기호를 EXTRA_SIDE로 구분한다. */
class SymbolPickerActivity : Activity() {

    private lateinit var settings: KeyboardSettings
    private val chips = LinkedHashMap<Char, TextView>()
    private var isLeft = false

    private var currentSymbol: String
        get() = if (isLeft) settings.leftSymbol else settings.favoriteSymbol
        set(value) {
            if (isLeft) settings.leftSymbol = value else settings.favoriteSymbol = value
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = KeyboardSettings(this)
        isLeft = intent.getStringExtra(EXTRA_SIDE) == SIDE_LEFT
        val screenTitle =
            getString(if (isLeft) R.string.symbol_left_title else R.string.symbol_favorite_title)
        title = screenTitle

        val ui = SettingComponents(this)
        ui.header(screenTitle)

        ui.caption(getString(R.string.symbol_pick_caption))
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(12))
        }
        SYMBOLS.chunked(6).forEach { rowSymbols ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowSymbols.forEach { symbol ->
                val chip = TextView(this).apply {
                    text = symbol.toString()
                    textSize = 20f
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(0, ui.dp(48), 1f).apply {
                        setMargins(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(4))
                    }
                    setOnClickListener {
                        currentSymbol = symbol.toString()
                        refreshChips()
                    }
                }
                chips[symbol] = chip
                row.addView(chip)
            }
            grid.addView(row)
        }
        ui.card(grid)

        ui.caption(getString(R.string.symbol_custom_title))
        ui.card(
            EditText(this).apply {
                hint = getString(R.string.symbol_custom_hint)
                background = null
                setText(if (currentSymbol.first() in SYMBOLS) "" else currentSymbol)
                setPadding(ui.dp(20), ui.dp(16), ui.dp(20), ui.dp(16))
                addTextChangedListener(object : TextWatcher {
                    override fun afterTextChanged(s: Editable?) {
                        val symbol = s?.toString()?.firstOrNull() ?: return
                        currentSymbol = symbol.toString()
                        refreshChips()
                    }

                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                })
            },
        )

        setContentView(ui.root())
        refreshChips()
    }

    private fun refreshChips() {
        val selected = currentSymbol.first()
        chips.forEach { (symbol, chip) ->
            val isSelected = symbol == selected
            chip.background = GradientDrawable().apply {
                cornerRadius = chip.resources.displayMetrics.density * 12
                setColor(if (isSelected) SettingComponents.ACCENT else SettingComponents.BG)
            }
            chip.setTextColor(if (isSelected) 0xFFFFFFFF.toInt() else SettingComponents.TEXT)
        }
    }

    companion object {
        const val EXTRA_SIDE = "side"
        const val SIDE_RIGHT = "right"
        const val SIDE_LEFT = "left"

        private val SYMBOLS = listOf(
            '.', ',', '!', '?', '~', '@',
            '#', '&', '*', '-', '_', ':',
            ';', '\'', '"', '/', '+', '=',
            '♥', '★', '…', '₩', '%', '^',
        )
    }
}

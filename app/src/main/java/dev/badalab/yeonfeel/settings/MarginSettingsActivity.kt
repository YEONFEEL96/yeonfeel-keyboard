package dev.badalab.yeonfeel.settings

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import dev.badalab.yeonfeel.R

/** 키보드 여백: 아래에서 키보드를 조정 핸들과 함께 띄워 바로 조절한다. */
class MarginSettingsActivity : Activity() {

    private lateinit var settings: KeyboardSettings
    private var previewInput: android.widget.EditText? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = KeyboardSettings(this)
        title = getString(R.string.settings_section_margins)
        buildUi()
        previewInput?.post { showAdjustKeyboard() }
    }

    /** 조정 모드 요청 플래그를 세우고 키보드를 띄운다. */
    private fun showAdjustKeyboard() {
        val input = previewInput ?: return
        settings.adjustModeRequested = true
        input.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.showSoftInput(input, 0)
    }

    private fun buildUi() {
        val ui = SettingComponents(this)
        ui.header(
            getString(R.string.settings_section_margins),
            actionIcon = R.drawable.ic_toolbar_keyboard,
            onAction = { showAdjustKeyboard() },
        )
        ui.card(ui.textRow(getString(R.string.settings_margin_hint)))

        val input = android.widget.EditText(this).apply {
            alpha = 0f
            background = null
            imeOptions = android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI
        }
        previewInput = input
        val root = android.widget.FrameLayout(this).apply {
            addView(ui.root())
            addView(input, android.widget.FrameLayout.LayoutParams(1, 1, Gravity.BOTTOM))
        }
        ui.show(root)
    }
}

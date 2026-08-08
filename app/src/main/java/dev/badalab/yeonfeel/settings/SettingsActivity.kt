package dev.badalab.yeonfeel.settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import dev.badalab.yeonfeel.R

/** 키보드 설정 첫 화면. 값은 변경 즉시 저장되며, 다음에 키보드가 열릴 때 반영된다. */
class SettingsActivity : Activity() {

    private lateinit var settings: KeyboardSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = KeyboardSettings(this)
        title = getString(R.string.settings_title)
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        // 하위 화면에서 값을 바꾸고 돌아오면 부제를 갱신한다.
        buildUi()
    }

    private fun buildUi() {
        val ui = SettingComponents(this)
        ui.header(getString(R.string.settings_title))

        ui.card(
            ui.textRow(getString(R.string.settings_language_section)) {
                startActivity(Intent(this, LanguageSettingsActivity::class.java))
            },
            ui.textRow(getString(R.string.settings_section_theme), currentThemeName()) {
                startActivity(Intent(this, ThemeSettingsActivity::class.java))
            },
            ui.textRow(getString(R.string.symbol_menu)) {
                startActivity(Intent(this, SymbolSettingsActivity::class.java))
            },
            ui.textRow(getString(R.string.gesture_feedback_menu)) {
                startActivity(Intent(this, GestureFeedbackActivity::class.java))
            },
        )

        ui.caption(getString(R.string.settings_section_input))
        ui.card(
            ui.switchRow(getString(R.string.settings_number_row), settings.showNumberRow) { checked, _ ->
                settings.showNumberRow = checked
            },
            ui.switchRow(getString(R.string.settings_show_toolbar), settings.showToolbar) { checked, _ ->
                settings.showToolbar = checked
            },
            ui.switchRow(getString(R.string.settings_mz_mode), settings.mzModeEnabled) { checked, _ ->
                settings.mzModeEnabled = checked
            },
            ui.textRow(getString(R.string.extra_input_menu)) {
                startActivity(Intent(this, ExtraInputSettingsActivity::class.java))
            },
        )

        ui.card(
            ui.textRow(
                getString(R.string.settings_section_margins),
                getString(R.string.settings_margin_hint),
            ),
        )

        ui.card(
            ui.textRow(getString(R.string.reset_menu)) {
                startActivity(Intent(this, ResetSettingsActivity::class.java))
            },
            ui.textRow(getString(R.string.debug_menu)) {
                startActivity(Intent(this, DebugSettingsActivity::class.java))
            },
        )

        setContentView(ui.root())
    }

    private fun currentThemeName(): String = buildString {
        append(
            getString(
                when (settings.themeMode) {
                    ThemeMode.SYSTEM -> R.string.settings_theme_system
                    ThemeMode.DARK -> R.string.settings_theme_dark
                    ThemeMode.LIGHT -> R.string.settings_theme_light
                },
            ),
        )
        if (settings.highContrast) {
            append(" · ")
            append(getString(R.string.settings_high_contrast_short))
        }
    }
}

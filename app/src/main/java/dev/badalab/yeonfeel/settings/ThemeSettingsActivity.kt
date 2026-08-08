package dev.badalab.yeonfeel.settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import dev.badalab.yeonfeel.R

/** 테마: 모드(시스템/다크/라이트), 고대비 하위 화면, 키캡 배경 표시. */
class ThemeSettingsActivity : Activity() {

    private lateinit var settings: KeyboardSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = KeyboardSettings(this)
        title = getString(R.string.settings_section_theme)
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        // 고대비 하위 화면에서 바뀐 상태(스위치·부제)를 갱신한다.
        buildUi()
    }

    private fun buildUi() {
        val ui = SettingComponents(this)
        ui.header(getString(R.string.settings_section_theme))

        val mode = settings.themeMode
        val radios = linkedMapOf(
            ThemeMode.SYSTEM to ui.radioRow(getString(R.string.settings_theme_system), mode == ThemeMode.SYSTEM),
            ThemeMode.DARK to ui.radioRow(getString(R.string.settings_theme_dark), mode == ThemeMode.DARK),
            ThemeMode.LIGHT to ui.radioRow(getString(R.string.settings_theme_light), mode == ThemeMode.LIGHT),
        )
        ui.bindRadioGroup(radios) { selected -> settings.themeMode = selected }
        ui.card(*radios.values.toTypedArray())

        ui.card(
            ui.switchNavRow(
                label = getString(R.string.settings_high_contrast),
                subLabel = currentStyleName(),
                checked = settings.highContrast,
                onToggle = { checked, _ -> settings.highContrast = checked },
                onOpen = { startActivity(Intent(this, HighContrastSettingsActivity::class.java)) },
            ),
            ui.switchRow(getString(R.string.theme_show_keycap), settings.showKeyBackground) { checked, _ ->
                settings.showKeyBackground = checked
            },
        )

        setContentView(ui.root())
    }

    private fun currentStyleName(): String = getString(
        when (settings.highContrastStyle) {
            HighContrastStyle.DEFAULT -> R.string.hc_style_default
            HighContrastStyle.YELLOW_BLACK -> R.string.hc_style_yellow_black
            HighContrastStyle.BLACK_WHITE -> R.string.hc_style_black_white
            HighContrastStyle.BLACK_YELLOW -> R.string.hc_style_black_yellow
        },
    )
}

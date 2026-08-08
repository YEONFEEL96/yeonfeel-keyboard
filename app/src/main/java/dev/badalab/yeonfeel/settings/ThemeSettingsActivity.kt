package dev.badalab.yeonfeel.settings

import android.app.Activity
import android.os.Bundle
import dev.badalab.yeonfeel.R

/** 테마: 다크/라이트, 고대비, 키캡 배경 표시. */
class ThemeSettingsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = KeyboardSettings(this)
        title = getString(R.string.settings_section_theme)

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
            ui.switchRow(getString(R.string.settings_high_contrast), settings.highContrast) { checked, _ ->
                settings.highContrast = checked
            },
            ui.switchRow(getString(R.string.theme_show_keycap), settings.showKeyBackground) { checked, _ ->
                settings.showKeyBackground = checked
            },
        )

        setContentView(ui.root())
    }
}

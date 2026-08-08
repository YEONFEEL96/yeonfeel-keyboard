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

        val darkRadio = ui.radioRow(getString(R.string.settings_theme_dark), settings.darkTheme)
        val lightRadio = ui.radioRow(getString(R.string.settings_theme_light), !settings.darkTheme)
        ui.bindRadioGroup(mapOf(true to darkRadio, false to lightRadio)) { dark ->
            settings.darkTheme = dark
        }
        ui.card(darkRadio, lightRadio)

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

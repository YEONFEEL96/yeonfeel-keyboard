package dev.badalab.yeonfeel.settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import dev.badalab.yeonfeel.R

/** 접근성: 고대비 등 접근성 관련 옵션 모음. */
class AccessibilitySettingsActivity : Activity() {

    private lateinit var settings: KeyboardSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = KeyboardSettings(this)
        title = getString(R.string.settings_accessibility)
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        buildUi()
    }

    private fun buildUi() {
        val ui = SettingComponents(this)
        ui.header(getString(R.string.settings_accessibility))

        ui.card(
            ui.switchNavRow(
                label = getString(R.string.settings_high_contrast),
                subLabel = currentStyleName(),
                checked = settings.highContrast,
                onToggle = { checked, _ -> settings.highContrast = checked },
                onOpen = { startActivity(Intent(this, HighContrastSettingsActivity::class.java)) },
            ),
        )

        var delaySlider: SettingComponents.SliderRowView? = null
        ui.caption(getString(R.string.accessibility_input_title), R.drawable.ic_icon_refresh) {
            settings.longPressDelayMs = KeyboardSettings.LONG_PRESS_DELAY_DEFAULT
            delaySlider?.setValue(KeyboardSettings.LONG_PRESS_DELAY_DEFAULT)
        }
        ui.card(
            ui.sliderRow(
                getString(R.string.long_press_delay_label),
                max = KeyboardSettings.LONG_PRESS_DELAY_MAX,
                initial = settings.longPressDelayMs,
                min = KeyboardSettings.LONG_PRESS_DELAY_MIN,
                valueFormatter = { getString(R.string.multi_tap_delay_value, it) },
            ) { value -> settings.longPressDelayMs = value }.also { delaySlider = it },
        )

        ui.show()
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

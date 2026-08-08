package dev.badalab.yeonfeel.settings

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dev.badalab.yeonfeel.R

/** 피드백: 햅틱 온오프 + 강도 슬라이더. 슬라이더를 움직이면 그 세기로 미리 진동한다. */
class FeedbackSettingsActivity : Activity() {

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = KeyboardSettings(this)
        title = getString(R.string.feedback_menu)

        val ui = SettingComponents(this)
        ui.header(getString(R.string.feedback_menu))

        ui.card(
            ui.switchRow(getString(R.string.feedback_key_preview), settings.keyPreviewEnabled) { checked, _ ->
                settings.keyPreviewEnabled = checked
            },
            ui.switchRow(getString(R.string.feedback_haptic), settings.hapticEnabled) { checked, _ ->
                settings.hapticEnabled = checked
            },
            ui.sliderRow(getString(R.string.feedback_haptic_strength), 100, settings.hapticStrength) { value ->
                settings.hapticStrength = value
                previewHaptic(value)
            },
        )

        setContentView(ui.root())
    }

    private fun previewHaptic(strength: Int) {
        if (strength <= 0) return
        val amplitude = (1 + strength * 2.54).toInt().coerceIn(1, 255)
        runCatching { vibrator?.vibrate(VibrationEffect.createOneShot(12L, amplitude)) }
    }
}

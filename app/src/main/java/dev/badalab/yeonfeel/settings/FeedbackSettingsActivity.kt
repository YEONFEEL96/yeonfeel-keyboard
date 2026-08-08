package dev.badalab.yeonfeel.settings

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dev.badalab.yeonfeel.R

/** 터치 피드백: 소리 / 진동(강도 포함) / 누른 키 보여주기. */
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
        title = getString(R.string.touch_feedback_menu)

        val ui = SettingComponents(this)
        ui.header(getString(R.string.touch_feedback_menu))

        ui.card(
            ui.switchRow(getString(R.string.feedback_sound), settings.soundEnabled) { checked, _ ->
                settings.soundEnabled = checked
            },
            ui.switchRow(getString(R.string.feedback_vibration), settings.hapticEnabled) { checked, _ ->
                settings.hapticEnabled = checked
            },
            ui.sliderRow(getString(R.string.feedback_haptic_strength), 100, settings.hapticStrength) { value ->
                settings.hapticStrength = value
                previewHaptic(value)
            },
            ui.switchRow(getString(R.string.feedback_key_preview), settings.keyPreviewEnabled) { checked, _ ->
                settings.keyPreviewEnabled = checked
            },
        )

        setContentView(ui.root())
    }

    private fun previewHaptic(strength: Int) {
        if (strength <= 0) return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amplitude = (1 + strength * 2.54).toInt().coerceIn(1, 255)
                vibrator?.vibrate(VibrationEffect.createOneShot(12L, amplitude))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(12L)
            }
        }
    }
}

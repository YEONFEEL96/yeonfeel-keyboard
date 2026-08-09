package dev.badalab.yeonfeel.settings

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import dev.badalab.yeonfeel.R

/** 터치 피드백: 소리 / 진동(켜져 있을 때만 강도 표시) / 누른 키 보여주기. */
class FeedbackSettingsActivity : Activity() {

    private lateinit var settings: KeyboardSettings

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
        settings = KeyboardSettings(this)
        title = getString(R.string.touch_feedback_menu)
        buildUi()
    }

    private fun buildUi() {
        val ui = SettingComponents(this)
        ui.header(getString(R.string.touch_feedback_menu))

        val rows = mutableListOf<View>()
        rows.add(
            ui.switchRow(getString(R.string.feedback_sound), settings.soundEnabled) { checked, _ ->
                settings.soundEnabled = checked
            },
        )
        rows.add(
            ui.switchRow(getString(R.string.feedback_vibration), settings.hapticEnabled) { checked, _ ->
                settings.hapticEnabled = checked
                buildUi() // 진동이 꺼지면 강도 슬라이더를 숨긴다
            },
        )
        if (settings.hapticEnabled) {
            rows.add(
                ui.sliderRow(getString(R.string.feedback_haptic_strength), 100, settings.hapticStrength) { value ->
                    settings.hapticStrength = value
                    previewHaptic(value)
                },
            )
        }
        // 3x4 자판(천지인/나랏글)은 키가 커서 미리보기가 불필요 — 메뉴에서 숨긴다.
        val is3x4 = settings.koreanLayout in setOf(
            KoreanLayoutType.CHUNJIIN,
            KoreanLayoutType.NARATGUL,
            KoreanLayoutType.NARATGUL_CENTER,
        )
        if (!is3x4) {
            rows.add(
                ui.switchRow(getString(R.string.feedback_key_preview), settings.keyPreviewEnabled) { checked, _ ->
                    settings.keyPreviewEnabled = checked
                },
            )
        }
        ui.card(*rows.toTypedArray())

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

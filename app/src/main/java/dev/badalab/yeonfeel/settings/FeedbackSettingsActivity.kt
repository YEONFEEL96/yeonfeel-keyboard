package dev.badalab.yeonfeel.settings

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import dev.badalab.yeonfeel.R

/** 터치 피드백: 소리 / 진동(세기 조절) / 누른 키 보여주기. */
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
                buildUi() // 진동을 끄면 세기 슬라이더를 숨긴다
            },
        )
        if (settings.hapticEnabled) {
            rows.add(
                ui.sliderRow(
                    getString(R.string.feedback_haptic_strength),
                    max = 5,
                    initial = settings.hapticStrength,
                    min = 1,
                ) { step ->
                    settings.hapticStrength = step
                    previewHaptic(step)
                },
            )
        }
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

        ui.show()
    }

    /** 슬라이더 조정 시 그 세기로 즉시 미리보기 진동 (키보드와 같은 프리미티브 경로). */
    private fun previewHaptic(step: Int) {
        val vib = vibrator ?: return
        runCatching {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                    vib.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK) ->
                    vib.vibrate(
                        VibrationEffect.startComposition()
                            .addPrimitive(
                                VibrationEffect.Composition.PRIMITIVE_CLICK,
                                step.coerceIn(1, 5) * 0.2f,
                            )
                            .compose(),
                    )
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                    vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ->
                    vib.vibrate(VibrationEffect.createOneShot(10L, step.coerceIn(1, 5) * 40))
                else -> {
                    @Suppress("DEPRECATION")
                    vib.vibrate(10L)
                }
            }
        }
    }
}

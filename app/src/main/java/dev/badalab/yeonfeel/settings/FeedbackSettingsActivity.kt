package dev.badalab.yeonfeel.settings

import android.app.Activity
import android.os.Bundle
import android.view.View
import dev.badalab.yeonfeel.R

/** 터치 피드백: 소리 / 진동(시스템 햅틱) / 누른 키 보여주기. */
class FeedbackSettingsActivity : Activity() {

    private lateinit var settings: KeyboardSettings

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
            },
        )
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

}

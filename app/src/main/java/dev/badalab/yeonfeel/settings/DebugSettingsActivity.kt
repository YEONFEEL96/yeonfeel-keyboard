package dev.badalab.yeonfeel.settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import dev.badalab.yeonfeel.R

/** 실험실: 실험 기능 목록. */
class DebugSettingsActivity : Activity() {

    private lateinit var settings: KeyboardSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = KeyboardSettings(this)
        title = getString(R.string.debug_menu)
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        buildUi()
    }

    private fun buildUi() {
        val ui = SettingComponents(this)
        ui.header(getString(R.string.debug_menu))

        ui.card(
            ui.switchNavRow(
                label = getString(R.string.debug_touch_correction),
                subLabel = buildList {
                    if (settings.touchCorrectionBasic) add(getString(R.string.touch_basic))
                    if (settings.touchCorrectionAi) add(getString(R.string.touch_ai))
                }.joinToString(" · ").ifEmpty { null },
                checked = settings.touchCorrectionEnabled,
                onToggle = { checked, view ->
                    if (checked && !settings.touchStatsEnabled) {
                        // 보정은 타점 수집이 재료 — 함께 켤지 확인받는다.
                        view.isChecked = false
                        ui.confirmBottom(
                            title = getString(R.string.debug_touch_correction),
                            message = getString(R.string.debug_touch_correction_dialog_message),
                            confirmLabel = getString(R.string.debug_touch_correction_dialog_confirm),
                            cancelLabel = getString(R.string.clipboard_cancel),
                        ) {
                            settings.touchCorrectionEnabled = true
                            settings.touchStatsEnabled = true
                            buildUi()
                        }
                    } else {
                        settings.touchCorrectionEnabled = checked
                    }
                },
                onOpen = { startActivity(Intent(this, TouchCorrectionActivity::class.java)) },
            ),
        )

        ui.show()
    }
}

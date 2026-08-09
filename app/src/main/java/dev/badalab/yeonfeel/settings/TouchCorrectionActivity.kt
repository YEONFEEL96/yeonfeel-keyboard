package dev.badalab.yeonfeel.settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import dev.badalab.yeonfeel.R
import dev.badalab.yeonfeel.debug.TouchStatsStore

/** 터치 보정: 기능 토글과 그 재료인 타점 데이터 관리. */
class TouchCorrectionActivity : Activity() {

    private lateinit var settings: KeyboardSettings
    private var sections: List<android.view.View> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = KeyboardSettings(this)
        title = getString(R.string.debug_touch_correction)
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        buildUi()
    }

    private fun buildUi() {
        val store = TouchStatsStore(this)
        val ui = SettingComponents(this)
        ui.header(getString(R.string.debug_touch_correction))

        val sections = mutableListOf<android.view.View>()
        this.sections = sections
        ui.masterSwitch(settings.touchCorrectionEnabled) { checked, view ->
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
                SettingComponents.setSectionsEnabled(sections, checked, animate = true)
            }
        }
        sections.add(ui.caption(getString(R.string.debug_touch_correction_sub)))

        sections.add(ui.card(
            ui.switchRow(
                getString(R.string.touch_basic),
                getString(R.string.touch_basic_sub),
                settings.touchCorrectionBasic,
            ) { checked, _ -> settings.touchCorrectionBasic = checked },
            ui.switchRow(
                getString(R.string.touch_ai),
                getString(R.string.touch_ai_sub),
                settings.touchCorrectionAi,
            ) { checked, _ -> settings.touchCorrectionAi = checked },
        ))

        sections.add(ui.card(
            ui.switchRow(getString(R.string.debug_touch_collect), settings.touchStatsEnabled) { checked, _ ->
                settings.touchStatsEnabled = checked
            },
            ui.textRow(
                getString(R.string.debug_touch_visualizer),
                getString(R.string.debug_touch_count, store.totalCount()),
            ) {
                startActivity(Intent(this, TouchVisualizerActivity::class.java))
            },
            ui.textRow(getString(R.string.debug_touch_clear)) {
                ui.confirmBottom(
                    title = getString(R.string.debug_touch_clear),
                    message = getString(R.string.reset_confirm_message),
                    confirmLabel = getString(R.string.reset_confirm_yes),
                    cancelLabel = getString(R.string.clipboard_cancel),
                ) {
                    store.clear()
                    android.widget.Toast
                        .makeText(this, R.string.reset_done, android.widget.Toast.LENGTH_SHORT)
                        .show()
                    buildUi()
                }
            },
        ))

        SettingComponents.setSectionsEnabled(sections, settings.touchCorrectionEnabled, animate = false)

        ui.show()
    }
}

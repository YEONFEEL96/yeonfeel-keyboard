package dev.badalab.yeonfeel.settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import dev.badalab.yeonfeel.R
import dev.badalab.yeonfeel.debug.TouchStatsStore

/** 디버그: 타점 수집·시각화·초기화. */
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
        val store = TouchStatsStore(this)
        val ui = SettingComponents(this)
        ui.header(getString(R.string.debug_menu))

        ui.card(
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
                // 실수로 지우지 않도록 하단 확인 모달을 거친다.
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
        )

        setContentView(ui.root())
    }
}

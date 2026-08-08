package dev.badalab.yeonfeel.settings

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.widget.Toast
import dev.badalab.yeonfeel.R
import dev.badalab.yeonfeel.clipboard.SecureClipboardStore
import dev.badalab.yeonfeel.debug.TouchStatsStore

/** 초기화: 개인 입력 데이터 / 터치 입력 데이터 / 캐시 / 설정 전체. */
class ResetSettingsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.reset_menu)

        val ui = SettingComponents(this)
        ui.header(getString(R.string.reset_menu))

        ui.card(
            ui.textRow(
                getString(R.string.reset_personal),
                getString(R.string.reset_personal_sub),
            ) {
                confirm(R.string.reset_personal) {
                    SecureClipboardStore(this).clear()
                }
            },
            ui.textRow(
                getString(R.string.reset_touch),
                getString(R.string.reset_touch_sub),
            ) {
                confirm(R.string.reset_touch) {
                    TouchStatsStore(this).clear()
                }
            },
            ui.textRow(getString(R.string.reset_cache)) {
                confirm(R.string.reset_cache) {
                    cacheDir.deleteRecursively()
                }
            },
            ui.textRow(
                getString(R.string.reset_settings),
                getString(R.string.reset_settings_sub),
            ) {
                confirm(R.string.reset_settings) {
                    KeyboardSettings(this).resetAll()
                }
            },
        )

        setContentView(ui.root())
    }

    private fun confirm(titleRes: Int, action: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(titleRes)
            .setMessage(R.string.reset_confirm_message)
            .setPositiveButton(R.string.reset_confirm_yes) { _, _ ->
                runCatching { action() }
                Toast.makeText(this, R.string.reset_done, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.clipboard_cancel, null)
            .show()
    }
}

package dev.badalab.yeonfeel.settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import dev.badalab.yeonfeel.R

/** 기호 메뉴: 즐겨찾기(스페이스바 오른쪽)·왼쪽 기호 하위 화면으로 이동한다. */
class SymbolSettingsActivity : Activity() {

    private lateinit var settings: KeyboardSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = KeyboardSettings(this)
        title = getString(R.string.symbol_menu)
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        buildUi()
    }

    private fun buildUi() {
        val ui = SettingComponents(this)
        ui.header(getString(R.string.symbol_menu))

        ui.card(
            ui.switchNavRow(
                label = getString(R.string.symbol_favorite_title),
                subLabel = null,
                checked = settings.favoriteSymbolEnabled,
                onToggle = { checked, _ -> settings.favoriteSymbolEnabled = checked },
                onOpen = { openPicker(SymbolPickerActivity.SIDE_RIGHT) },
            ),
            ui.switchNavRow(
                label = getString(R.string.symbol_left_title),
                subLabel = null,
                checked = settings.leftSymbolEnabled,
                onToggle = { checked, _ -> settings.leftSymbolEnabled = checked },
                onOpen = { openPicker(SymbolPickerActivity.SIDE_LEFT) },
            ),
        )

        setContentView(ui.root())
    }

    private fun openPicker(side: String) {
        startActivity(
            Intent(this, SymbolPickerActivity::class.java)
                .putExtra(SymbolPickerActivity.EXTRA_SIDE, side),
        )
    }
}

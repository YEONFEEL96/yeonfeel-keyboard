package dev.badalab.yeonfeel.settings

import android.app.Activity
import android.os.Bundle
import dev.badalab.yeonfeel.R

/** 기타 입력 옵션: 자동 대문자 전환, 스페이스바 두 번 → 마침표, MZ 모드. */
class ExtraInputSettingsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = KeyboardSettings(this)
        title = getString(R.string.extra_input_menu)

        val ui = SettingComponents(this)
        ui.header(getString(R.string.extra_input_menu))

        ui.card(
            ui.switchRow(getString(R.string.extra_auto_capitalize), settings.autoCapitalize) { checked, _ ->
                settings.autoCapitalize = checked
            },
            ui.switchRow(getString(R.string.extra_double_space_period), settings.doubleSpacePeriod) { checked, _ ->
                settings.doubleSpacePeriod = checked
            },
            ui.switchRow(getString(R.string.settings_mz_mode), settings.mzModeEnabled) { checked, _ ->
                settings.mzModeEnabled = checked
            },
        )

        ui.show()
    }
}

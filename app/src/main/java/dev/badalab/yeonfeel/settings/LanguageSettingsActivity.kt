package dev.badalab.yeonfeel.settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import dev.badalab.yeonfeel.R

/**
 * 언어 및 키보드 형식.
 * 언어 행을 누르면 해당 언어의 자판 종류 화면으로 들어가고, 스위치로 언어를 켜고 끈다.
 */
class LanguageSettingsActivity : Activity() {

    private lateinit var settings: KeyboardSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = KeyboardSettings(this)
        title = getString(R.string.settings_language_section)
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        // 하위 화면에서 자판을 바꾸고 돌아오면 부제(현재 자판)를 갱신한다.
        buildUi()
    }

    private fun buildUi() {
        val ui = SettingComponents(this)
        ui.header(getString(R.string.settings_language_section))

        ui.caption(getString(R.string.language_manage_title))
        ui.card(
            ui.switchNavRow(
                label = getString(R.string.subtype_korean),
                subLabel = koreanLayoutName(),
                checked = settings.koreanEnabled,
                onToggle = { checked, view ->
                    if (!checked && !settings.englishEnabled) {
                        view.isChecked = true
                        Toast.makeText(this, R.string.language_min_one, Toast.LENGTH_SHORT).show()
                    } else {
                        settings.koreanEnabled = checked
                    }
                },
                onOpen = { openLayoutSettings(LayoutSettingsActivity.LANGUAGE_KOREAN) },
            ),
            ui.switchNavRow(
                label = getString(R.string.subtype_english),
                subLabel = getString(R.string.english_layout_qwerty),
                checked = settings.englishEnabled,
                onToggle = { checked, view ->
                    if (!checked && !settings.koreanEnabled) {
                        view.isChecked = true
                        Toast.makeText(this, R.string.language_min_one, Toast.LENGTH_SHORT).show()
                    } else {
                        settings.englishEnabled = checked
                    }
                },
                onOpen = { openLayoutSettings(LayoutSettingsActivity.LANGUAGE_ENGLISH) },
            ),
        )

        ui.caption(getString(R.string.number_symbol_title))
        ui.card(
            ui.switchRow(
                getString(R.string.shift_number_symbols),
                settings.shiftNumberRowSymbols,
            ) { checked, _ -> settings.shiftNumberRowSymbols = checked },
        )

        ui.caption(getString(R.string.language_switch_method_title))
        val method = settings.languageSwitchMethod
        val radios = linkedMapOf(
            LanguageSwitchMethod.BUTTON to
                ui.radioRow(getString(R.string.switch_method_button), method == LanguageSwitchMethod.BUTTON),
            LanguageSwitchMethod.BUTTON_AND_SWIPE to
                ui.radioRow(getString(R.string.switch_method_button_swipe), method == LanguageSwitchMethod.BUTTON_AND_SWIPE),
            LanguageSwitchMethod.SWIPE to
                ui.radioRow(getString(R.string.switch_method_swipe), method == LanguageSwitchMethod.SWIPE),
        )
        ui.bindRadioGroup(radios) { selected -> settings.languageSwitchMethod = selected }
        ui.card(*radios.values.toTypedArray())

        ui.show()
    }

    private fun openLayoutSettings(language: String) {
        startActivity(
            Intent(this, LayoutSettingsActivity::class.java)
                .putExtra(LayoutSettingsActivity.EXTRA_LANGUAGE, language),
        )
    }

    private fun koreanLayoutName(): String = getString(
        when (settings.koreanLayout) {
            KoreanLayoutType.DUBEOLSIK -> R.string.korean_layout_name_dubeolsik
            KoreanLayoutType.DANMOEUM -> R.string.korean_layout_name_danmoeum
            KoreanLayoutType.CHUNJIIN -> R.string.korean_layout_name_chunjiin
            KoreanLayoutType.NARATGUL -> R.string.korean_layout_name_naratgul
            KoreanLayoutType.NARATGUL_CENTER -> R.string.korean_layout_name_naratgul_center
        },
    )
}

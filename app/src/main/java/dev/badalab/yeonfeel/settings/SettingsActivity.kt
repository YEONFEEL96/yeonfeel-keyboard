package dev.badalab.yeonfeel.settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import dev.badalab.yeonfeel.R

/** 키보드 설정 첫 화면. 값은 변경 즉시 저장되며, 다음에 키보드가 열릴 때 반영된다. */
class SettingsActivity : Activity() {

    private lateinit var settings: KeyboardSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = KeyboardSettings(this)
        title = getString(R.string.settings_title)
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        // 하위 화면에서 값을 바꾸고 돌아오면 부제를 갱신한다.
        buildUi()
    }

    private fun buildUi() {
        val ui = SettingComponents(this)
        ui.header(getString(R.string.settings_title))

        ui.card(
            ui.textRow(getString(R.string.settings_language_section)) {
                openDetail(LanguageSettingsActivity::class.java)
            },
            ui.textRow(getString(R.string.settings_section_theme), currentThemeName()) {
                openDetail(ThemeSettingsActivity::class.java)
            },
            ui.textRow(getString(R.string.symbol_menu)) {
                openDetail(SymbolSettingsActivity::class.java)
            },
            ui.textRow(getString(R.string.gesture_feedback_menu)) {
                openDetail(GestureFeedbackActivity::class.java)
            },
            ui.textRow(getString(R.string.settings_accessibility)) {
                openDetail(AccessibilitySettingsActivity::class.java)
            },
        )

        ui.caption(getString(R.string.settings_section_display))
        ui.card(
            ui.switchRow(getString(R.string.settings_show_toolbar), settings.showToolbar) { checked, _ ->
                settings.showToolbar = checked
            },
            ui.switchRow(getString(R.string.settings_key_hints), settings.keyHintsEnabled) { checked, _ ->
                settings.keyHintsEnabled = checked
            },
            ui.switchRow(getString(R.string.terminal_row_title), settings.terminalRowEnabled) { checked, _ ->
                settings.terminalRowEnabled = checked
            },
            ui.switchRow(
                getString(R.string.split_landscape_title),
                settings.splitLandscape,
            ) { checked, _ -> settings.splitLandscape = checked },
            *(
                if (resources.configuration.smallestScreenWidthDp >= KeyboardSettings.LARGE_SCREEN_SW_DP) {
                    arrayOf(
                        ui.switchRow(
                            getString(R.string.split_portrait_title),
                            settings.splitPortrait,
                        ) { checked, _ -> settings.splitPortrait = checked },
                    )
                } else {
                    emptyArray()
                }
                ),
            ui.textRow(getString(R.string.settings_section_margins)) {
                openDetail(MarginSettingsActivity::class.java)
            },
        )

        ui.caption(getString(R.string.settings_section_input))
        ui.card(
            ui.textRow(getString(R.string.extra_input_menu)) {
                openDetail(ExtraInputSettingsActivity::class.java)
            },
        )

        ui.caption(getString(R.string.settings_section_general))
        ui.card(
            ui.textRow(getString(R.string.reset_menu)) {
                openDetail(ResetSettingsActivity::class.java)
            },
            ui.textRow(getString(R.string.debug_menu)) {
                openDetail(DebugSettingsActivity::class.java)
            },
        )

        ui.linkButton(getString(R.string.licenses_menu)) {
            openDetail(LicensesActivity::class.java)
        }

        ui.show()
    }

    /**
     * 상위 메뉴 열기. 2단 구성에서 이미 오른쪽에 떠 있는 화면을 다시 누르면
     * 리로드하지 않고 그 패널에 촉각 펄스만 준다 (삼성 설정과 동일한 느낌).
     *
     * 임베딩된 액티비티의 Configuration 은 전체 화면이 아니라 자기 패널 크기를
     * 반영하므로 폭으로 분할 여부를 판정하면 안 된다. 대신 "그 상세가 지금 오른쪽에
     * 떠 있는가"(버스의 현재 클래스)로 판정한다 — 떠 있으면 곧 분할 상태다.
     */
    private fun openDetail(cls: Class<out android.app.Activity>) {
        if (DetailPulseBus.currentClass() == cls) {
            DetailPulseBus.pulse()
        } else {
            startActivity(Intent(this, cls))
        }
    }

    private fun currentThemeName(): String = buildString {
        append(
            getString(
                when (settings.themeMode) {
                    ThemeMode.SYSTEM -> R.string.settings_theme_system
                    ThemeMode.DARK -> R.string.settings_theme_dark
                    ThemeMode.LIGHT -> R.string.settings_theme_light
                },
            ),
        )
        if (settings.highContrast) {
            append(" · ")
            append(getString(R.string.settings_high_contrast))
        }
    }
}

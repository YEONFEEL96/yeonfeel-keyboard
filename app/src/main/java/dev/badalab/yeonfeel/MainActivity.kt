package dev.badalab.yeonfeel

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import dev.badalab.yeonfeel.settings.SettingComponents
import dev.badalab.yeonfeel.settings.SettingsActivity

/**
 * 키보드 활성화 도우미.
 * 1) 시스템 설정에서 연필키보드를 사용함으로 켜고
 * 2) 키보드 선택기에서 연필키보드로 전환한 뒤
 * 3) 아래 입력창에서 바로 테스트한다.
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val ui = SettingComponents(this)
        ui.header(getString(R.string.app_name), showBack = false)
        ui.caption(getString(R.string.setup_guide))

        ui.card(
            ui.textRow(getString(R.string.enable_keyboard)) {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            },
            ui.textRow(getString(R.string.switch_keyboard)) {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showInputMethodPicker()
            },
            ui.textRow(getString(R.string.open_settings)) {
                startActivity(Intent(this, SettingsActivity::class.java))
            },
        )

        ui.card(
            EditText(this).apply {
                hint = getString(R.string.test_field_hint)
                background = null
                // 테마 색을 명시하지 않으면 다크 카드 위에서 검은 글자가 되어 안 보인다.
                setTextColor(SettingComponents.TEXT)
                setHintTextColor(SettingComponents.SUB_TEXT)
                setPadding(ui.dp(20), ui.dp(18), ui.dp(20), ui.dp(18))
            },
        )

        setContentView(ui.root())
    }
}

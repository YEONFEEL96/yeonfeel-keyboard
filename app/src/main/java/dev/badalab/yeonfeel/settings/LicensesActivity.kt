package dev.badalab.yeonfeel.settings

import android.app.Activity
import android.os.Bundle
import android.util.TypedValue
import android.widget.TextView
import dev.badalab.yeonfeel.R

/** 오픈소스 라이선스 고지. 내용은 assets/licenses.txt (배포물 고지 의무 이행). */
class LicensesActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.licenses_menu)

        val ui = SettingComponents(this)
        ui.header(getString(R.string.licenses_menu))

        val text = assets.open("licenses.txt").bufferedReader().use { it.readText() }
        ui.card(
            TextView(this).apply {
                setText(text)
                setTextColor(SettingComponents.TEXT)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setLineSpacing(0f, 1.25f)
                setPadding(ui.dp(20), ui.dp(16), ui.dp(20), ui.dp(16))
                setTextIsSelectable(true)
            },
        )
        ui.show()
    }
}

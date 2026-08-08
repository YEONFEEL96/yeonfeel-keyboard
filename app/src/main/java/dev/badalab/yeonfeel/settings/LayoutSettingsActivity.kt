package dev.badalab.yeonfeel.settings

import android.app.Activity
import android.os.Bundle
import dev.badalab.yeonfeel.R

/** 언어별 자판 종류 선택 화면. EXTRA_LANGUAGE로 한국어/영어를 구분한다. */
class LayoutSettingsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = KeyboardSettings(this)
        val isKorean = intent.getStringExtra(EXTRA_LANGUAGE) != LANGUAGE_ENGLISH

        val ui = SettingComponents(this)
        if (isKorean) {
            title = getString(R.string.subtype_korean)
            ui.header(getString(R.string.subtype_korean))
            ui.caption(getString(R.string.korean_layout_title))
            val layout = settings.koreanLayout
            val radios = linkedMapOf(
                KoreanLayoutType.DUBEOLSIK to
                    ui.radioRow(getString(R.string.korean_layout_dubeolsik), layout == KoreanLayoutType.DUBEOLSIK),
                KoreanLayoutType.DANMOEUM to
                    ui.radioRow(getString(R.string.korean_layout_danmoeum), layout == KoreanLayoutType.DANMOEUM),
                KoreanLayoutType.SEBEOLSIK_390 to
                    ui.radioRow(getString(R.string.korean_layout_sebeolsik), layout == KoreanLayoutType.SEBEOLSIK_390),
                KoreanLayoutType.CHUNJIIN to
                    ui.radioRow(getString(R.string.korean_layout_chunjiin), layout == KoreanLayoutType.CHUNJIIN),
                KoreanLayoutType.NARATGUL to
                    ui.radioRow(getString(R.string.korean_layout_naratgul), layout == KoreanLayoutType.NARATGUL),
            )
            ui.bindRadioGroup(radios) { selected -> settings.koreanLayout = selected }
            ui.card(*radios.values.toTypedArray())
        } else {
            title = getString(R.string.subtype_english)
            ui.header(getString(R.string.subtype_english))
            ui.caption(getString(R.string.english_layout_title))
            ui.card(ui.radioRow(getString(R.string.english_layout_qwerty), true))
        }

        setContentView(ui.root())
    }

    companion object {
        const val EXTRA_LANGUAGE = "language"
        const val LANGUAGE_KOREAN = "ko"
        const val LANGUAGE_ENGLISH = "en"
    }
}

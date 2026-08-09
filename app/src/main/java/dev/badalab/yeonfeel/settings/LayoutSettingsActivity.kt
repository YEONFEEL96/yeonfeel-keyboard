package dev.badalab.yeonfeel.settings

import android.app.Activity
import android.os.Bundle
import dev.badalab.yeonfeel.R

/** 언어별 자판 종류 선택 화면. EXTRA_LANGUAGE로 한국어/영어를 구분한다. */
class LayoutSettingsActivity : Activity() {

    private var multiTapSection: List<android.view.View> = emptyList()
    private var chunjiinSection: List<android.view.View> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    private fun buildUi() {
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
                KoreanLayoutType.CHUNJIIN to
                    ui.radioRow(getString(R.string.korean_layout_chunjiin), layout == KoreanLayoutType.CHUNJIIN),
                KoreanLayoutType.NARATGUL to
                    ui.radioRow(getString(R.string.korean_layout_naratgul), layout == KoreanLayoutType.NARATGUL),
                KoreanLayoutType.NARATGUL_CENTER to
                    ui.radioRow(
                        getString(R.string.korean_layout_naratgul_center),
                        layout == KoreanLayoutType.NARATGUL_CENTER,
                    ),
            )
            ui.bindRadioGroup(radios) { selected ->
                settings.koreanLayout = selected
                updateMultiTapSection(settings)
            }
            ui.card(*radios.values.toTypedArray())

            val spaceRadios = linkedMapOf(
                true to ui.radioRow(getString(R.string.chunjiin_input_space), settings.chunjiinSpaceCommits),
                false to ui.radioRow(getString(R.string.chunjiin_input_auto), !settings.chunjiinSpaceCommits),
            )
            ui.bindRadioGroup(spaceRadios) { selected ->
                settings.chunjiinSpaceCommits = selected
                updateMultiTapSection(settings)
            }
            chunjiinSection = listOf(
                ui.caption(getString(R.string.chunjiin_space_title)),
                ui.card(*spaceRadios.values.toTypedArray()),
            )

            // 연타 판정을 쓰는 방식/자판일 때만 아래에 입력 지연시간 섹션이 나타난다.
            var delaySlider: SettingComponents.SliderRowView? = null
            multiTapSection = listOf(
                ui.caption(getString(R.string.multi_tap_delay_title), R.drawable.ic_icon_refresh) {
                    settings.multiTapDelayMs = KeyboardSettings.MULTI_TAP_DELAY_DEFAULT
                    delaySlider?.setValue(KeyboardSettings.MULTI_TAP_DELAY_DEFAULT)
                },
                ui.card(
                    ui.sliderRow(
                        getString(R.string.multi_tap_delay_label),
                        max = KeyboardSettings.MULTI_TAP_DELAY_MAX,
                        initial = settings.multiTapDelayMs,
                        min = KeyboardSettings.MULTI_TAP_DELAY_MIN,
                        valueFormatter = { getString(R.string.multi_tap_delay_value, it) },
                    ) { value -> settings.multiTapDelayMs = value }.also { delaySlider = it },
                ),
            )
            updateMultiTapSection(settings)
        } else {
            title = getString(R.string.subtype_english)
            ui.header(getString(R.string.subtype_english))
            ui.caption(getString(R.string.english_layout_title))
            val layout = settings.englishLayout
            val radios = linkedMapOf(
                EnglishLayoutType.QWERTY to
                    ui.radioRow(getString(R.string.english_layout_qwerty), layout == EnglishLayoutType.QWERTY),
                EnglishLayoutType.DVORAK to
                    ui.radioRow(getString(R.string.english_layout_dvorak), layout == EnglishLayoutType.DVORAK),
            )
            ui.bindRadioGroup(radios) { selected -> settings.englishLayout = selected }
            ui.card(*radios.values.toTypedArray())
        }

        ui.show()
    }

    /**
     * 연타 입력이 없는 두벌식·세벌식, 그리고 연타 대기가 무한인 자동 방식 천지인에서는
     * 연타 판정 시간 섹션을 숨긴다 (다른 콘텐츠는 유지).
     */
    private fun updateMultiTapSection(settings: KeyboardSettings) {
        val uses = when (settings.koreanLayout) {
            KoreanLayoutType.DANMOEUM,
            KoreanLayoutType.NARATGUL,
            KoreanLayoutType.NARATGUL_CENTER,
            -> true
            KoreanLayoutType.CHUNJIIN -> !settings.chunjiinSpaceCommits
            else -> false
        }
        multiTapSection.forEach { it.visibility = if (uses) android.view.View.VISIBLE else android.view.View.GONE }
        val chunjiin = settings.koreanLayout == KoreanLayoutType.CHUNJIIN
        chunjiinSection.forEach { it.visibility = if (chunjiin) android.view.View.VISIBLE else android.view.View.GONE }
    }

    companion object {
        const val EXTRA_LANGUAGE = "language"
        const val LANGUAGE_KOREAN = "ko"
        const val LANGUAGE_ENGLISH = "en"
    }
}

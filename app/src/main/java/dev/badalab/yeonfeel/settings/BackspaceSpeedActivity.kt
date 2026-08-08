package dev.badalab.yeonfeel.settings

import android.app.Activity
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import dev.badalab.yeonfeel.R

/** 백스페이스 반복 속도 선택. */
class BackspaceSpeedActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = KeyboardSettings(this)
        title = getString(R.string.gesture_backspace_speed)

        val ui = SettingComponents(this)
        ui.header(getString(R.string.gesture_backspace_speed))

        val speed = settings.backspaceSpeed
        val radios = linkedMapOf(
            BackspaceSpeed.SLOW to
                ui.radioRow(speedLabel(getString(R.string.speed_slow), BackspaceSpeed.SLOW), speed == BackspaceSpeed.SLOW),
            BackspaceSpeed.NORMAL to
                ui.radioRow(speedLabel(getString(R.string.speed_normal), BackspaceSpeed.NORMAL), speed == BackspaceSpeed.NORMAL),
            BackspaceSpeed.FAST to
                ui.radioRow(speedLabel(getString(R.string.speed_fast), BackspaceSpeed.FAST), speed == BackspaceSpeed.FAST),
        )
        ui.bindRadioGroup(radios) { selected -> settings.backspaceSpeed = selected }
        ui.card(*radios.values.toTypedArray())

        setContentView(ui.root())
    }

    /** "느리게  80ms" — ms 부분은 작은 글씨·보조색으로. */
    private fun speedLabel(name: String, speed: BackspaceSpeed): CharSequence {
        val ms = "${speed.intervalMs}ms"
        val text = SpannableString("$name  $ms")
        val start = text.length - ms.length
        text.setSpan(RelativeSizeSpan(0.72f), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        text.setSpan(ForegroundColorSpan(SettingComponents.SUB_TEXT), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return text
    }
}

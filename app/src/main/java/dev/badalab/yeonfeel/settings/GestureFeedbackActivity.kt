package dev.badalab.yeonfeel.settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import dev.badalab.yeonfeel.R

/** 제스처 및 피드백 메뉴. */
class GestureFeedbackActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.gesture_feedback_menu)

        val ui = SettingComponents(this)
        ui.header(getString(R.string.gesture_feedback_menu))

        ui.card(
            ui.textRow(getString(R.string.feedback_menu)) {
                startActivity(Intent(this, FeedbackSettingsActivity::class.java))
            },
        )

        ui.caption(getString(R.string.gesture_backspace_speed))
        val settings = KeyboardSettings(this)
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
        val text = android.text.SpannableString("$name  $ms")
        val start = text.length - ms.length
        text.setSpan(
            android.text.style.RelativeSizeSpan(0.72f),
            start,
            text.length,
            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        text.setSpan(
            android.text.style.ForegroundColorSpan(SettingComponents.SUB_TEXT),
            start,
            text.length,
            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        return text
    }
}

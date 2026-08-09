package dev.badalab.yeonfeel.settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import dev.badalab.yeonfeel.R

/** 제스처 및 피드백: 터치 피드백 / 백스페이스 속도. */
class GestureFeedbackActivity : Activity() {

    private lateinit var settings: KeyboardSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = KeyboardSettings(this)
        title = getString(R.string.gesture_feedback_menu)
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        buildUi()
    }

    private fun buildUi() {
        val ui = SettingComponents(this)
        ui.header(getString(R.string.gesture_feedback_menu))

        ui.card(
            ui.textRow(getString(R.string.touch_feedback_menu)) {
                startActivity(Intent(this, FeedbackSettingsActivity::class.java))
            },
            ui.textRow(getString(R.string.gesture_backspace_speed), currentSpeedName()) {
                startActivity(Intent(this, BackspaceSpeedActivity::class.java))
            },
        )

        ui.show()
    }

    private fun currentSpeedName(): String = getString(
        when (settings.backspaceSpeed) {
            BackspaceSpeed.SLOW -> R.string.speed_slow
            BackspaceSpeed.NORMAL -> R.string.speed_normal
            BackspaceSpeed.FAST -> R.string.speed_fast
        },
    )
}

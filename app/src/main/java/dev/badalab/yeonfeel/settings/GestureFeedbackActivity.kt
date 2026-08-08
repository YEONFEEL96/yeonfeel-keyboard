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

        setContentView(ui.root())
    }
}

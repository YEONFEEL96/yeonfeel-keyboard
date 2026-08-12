package dev.badalab.yeonfeel

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.window.embedding.RuleController
import dev.badalab.yeonfeel.settings.DetailPulseBus
import dev.badalab.yeonfeel.settings.SettingsActivity
import dev.badalab.yeonfeel.settings.ThemeChangeBus

/** 앱 시작 시 설정 2단 구성 규칙을 등록하고, 상세 화면의 재탭 펄스를 배선한다. */
class YeonfeelApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val rules = RuleController.parseRules(this, R.xml.main_split_config)
        RuleController.getInstance(this).setRules(rules)
        registerDetailPulseTracking()
    }

    /**
     * 오른쪽 상세 화면(= SettingsActivity·MainActivity 가 아닌 액티비티)이 보일 때
     * 자기 콘텐츠 루트에 펄스를 재생할 수 있도록 버스에 등록/해제한다.
     *
     * resume/pause 가 아니라 start/stop 을 쓴다 — 2단에서 왼쪽 목록을 누르면
     * 오른쪽 상세는 paused 되지만 여전히 started(보임) 상태라, 이 시점에도
     * "현재 떠 있는 상세"로 추적돼야 재탭을 리로드 없이 펄스로 처리할 수 있다.
     */
    private fun registerDetailPulseTracking() {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                // 테마 변경 시 재생성 대상 — 두 패널(목록·상세) 모두 포함해야 한다.
                if (activity !is MainActivity) ThemeChangeBus.register(activity)
                if (activity is SettingsActivity || activity is MainActivity) return
                DetailPulseBus.register(activity, activity.javaClass) {
                    pulse(activity.findViewById(android.R.id.content))
                }
            }

            override fun onActivityStopped(activity: Activity) {
                ThemeChangeBus.unregister(activity)
                DetailPulseBus.unregister(activity)
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    private fun pulse(root: View?) {
        root ?: return
        root.animate().cancel()
        root.pivotX = root.width / 2f
        root.pivotY = root.height / 2f
        root.animate().scaleX(0.97f).scaleY(0.97f).setDuration(100)
            .withEndAction {
                root.animate().scaleX(1f).scaleY(1f).setDuration(180)
                    .setInterpolator(OvershootInterpolator(2f))
                    .start()
            }
            .start()
    }
}

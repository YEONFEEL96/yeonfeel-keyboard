package dev.badalab.yeonfeel.settings

import android.app.Activity
import java.util.Collections
import java.util.WeakHashMap

/**
 * 현재 화면에 보이는 설정 액티비티들을 추적한다. 앱 내부 테마(다크/라이트)를 바꾸면
 * 폴드·태블릿 2단 구성에서 양쪽 패널이 모두 새 팔레트로 다시 그려지도록 recreate 한다.
 * (혼자 뜬 화면은 buildUi 로 충분하지만, 왼쪽 목록 패널은 재구성 신호를 못 받는다.)
 */
object ThemeChangeBus {
    private val live: MutableSet<Activity> =
        Collections.newSetFromMap(WeakHashMap<Activity, Boolean>())

    fun register(activity: Activity) {
        live.add(activity)
    }

    fun unregister(activity: Activity) {
        live.remove(activity)
    }

    fun recreateAll() {
        live.toList().forEach { it.recreate() }
    }
}

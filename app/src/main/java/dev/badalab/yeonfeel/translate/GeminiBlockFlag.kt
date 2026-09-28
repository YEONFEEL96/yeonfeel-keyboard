package dev.badalab.yeonfeel.translate

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import dev.badalab.yeonfeel.settings.KeyboardSettings

/**
 * 키보드 안에서 Gemini Nano가 막힌 적이 있는지 기억한다. AICore는 "화면 맨 앞 앱"에만 추론을
 * 허용해 키보드에서는 BACKGROUND_USE_BLOCKED가 날 수 있는데, 상태 확인(checkStatus)으로는 미리
 * 알 수 없고 실제 추론에서야 드러난다. 한 번 막히면 기록해 두고 키보드는 다시 시도하지 않는다.
 *
 * 기록은 막힌 시점의 AICore 버전과 함께 남긴다 — AICore가 업데이트되면 정책이 바뀌었을 수 있으므로
 * 기록이 저절로 무효가 된다. 설정 화면은 [clear]로 사용자가 다시 시도하게 할 수 있다.
 *
 * 키보드(#19)가 [markBlocked]로 기록하고, 설정 화면(#20)이 [isBlocked]로 표시·[clear]로 해제한다.
 */
class GeminiBlockFlag(context: Context, private val settings: KeyboardSettings) {

    private val packageManager = context.applicationContext.packageManager

    /** 현재 AICore 버전에서 키보드 안 Gemini Nano가 막혀 있는지. */
    fun isBlocked(): Boolean = isBlocked(settings.geminiBlockedAicoreVersion, aicoreVersion())

    /** 키보드 안에서 BACKGROUND_USE_BLOCKED를 받았을 때 부른다. */
    fun markBlocked() {
        settings.geminiBlockedAicoreVersion = aicoreVersion()
    }

    fun clear() {
        settings.geminiBlockedAicoreVersion = NOT_BLOCKED
    }

    /** AICore 앱 버전. 설치돼 있지 않거나 조회할 수 없으면 [NOT_BLOCKED]와 같은 -1. */
    private fun aicoreVersion(): Long = runCatching {
        PackageInfoCompat.getLongVersionCode(packageManager.getPackageInfo(AICORE_PACKAGE, 0))
    }.getOrDefault(NOT_BLOCKED)

    companion object {
        /** AICore 앱 패키지. Gemini Nano 라이브러리의 매니페스트가 <queries>에 이미 넣어 보인다. */
        const val AICORE_PACKAGE = "com.google.android.aicore"
        private const val NOT_BLOCKED = -1L

        /**
         * 기록된 버전이 지금 AICore 버전과 같을 때만 막힌 것으로 본다. 기록이 없거나(-1),
         * AICore를 알 수 없거나(-1), 그 사이 AICore가 바뀌었으면 막히지 않은 것.
         */
        fun isBlocked(recordedVersion: Long, currentVersion: Long): Boolean =
            recordedVersion != NOT_BLOCKED && currentVersion != NOT_BLOCKED && recordedVersion == currentVersion
    }
}

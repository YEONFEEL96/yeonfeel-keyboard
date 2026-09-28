package dev.badalab.yeonfeel.translate

import android.annotation.SuppressLint
import android.view.translation.TranslationCapability
import com.google.mlkit.genai.common.FeatureStatus

/**
 * 엔진을 지금 쓸 수 있는지. [TranslationBackend.checkAvailability]가 답한다 —
 * 상태만 조회하며 모델·언어 팩 다운로드는 절대 시작하지 않는다.
 */
enum class Availability {
    /** 바로 번역할 수 있다. */
    READY,

    /** 모델·언어 팩을 먼저 내려받아야 한다. */
    NEEDS_DOWNLOAD,

    /** 모델·언어 팩을 내려받는 중이다. */
    DOWNLOADING,

    /** 엔진은 있지만 이 언어 쌍을 지원하지 않는다. */
    LANGUAGE_UNSUPPORTED,

    /** 이 기기(또는 OS 버전)에서는 엔진을 쓸 수 없다. */
    UNAVAILABLE,

    /** 엔진을 제공하는 애드온 앱(ML Kit)이 설치돼 있지 않다. */
    ADDON_NOT_INSTALLED,

    /** 확인할 수 없다 (상태 조회를 지원하지 않는 엔진, 조회 실패·시간 초과). */
    UNKNOWN,
    ;

    companion object {
        /**
         * 시스템 번역의 언어 쌍 상태([TranslationCapability]의 STATE_*)를 옮긴다.
         * null은 지원 언어 목록이 비었다는 뜻 — 번역 서비스가 없거나 응답하지 않았다.
         * 상수는 컴파일 때 값으로 들어가 API 31 미만에서도 클래스를 로드하지 않는다.
         */
        @SuppressLint("InlinedApi")
        fun fromCapabilityState(state: Int?): Availability = when (state) {
            null -> UNAVAILABLE
            TranslationCapability.STATE_ON_DEVICE -> READY
            TranslationCapability.STATE_AVAILABLE_TO_DOWNLOAD -> NEEDS_DOWNLOAD
            TranslationCapability.STATE_DOWNLOADING -> DOWNLOADING
            else -> LANGUAGE_UNSUPPORTED
        }

        /** Gemini Nano checkStatus()의 [FeatureStatus]를 옮긴다. */
        fun fromFeatureStatus(status: Int): Availability = when (status) {
            FeatureStatus.AVAILABLE -> READY
            FeatureStatus.DOWNLOADABLE -> NEEDS_DOWNLOAD
            FeatureStatus.DOWNLOADING -> DOWNLOADING
            FeatureStatus.UNAVAILABLE -> UNAVAILABLE
            else -> UNKNOWN
        }

        /**
         * 번역 실패 이유를 상태로 옮긴다. 쓸 수 없음이 확실한 이유만 옮기고,
         * 일시적인 실패(한도·오류 등)는 [UNKNOWN] — 엔진 자체가 안 된다는 뜻이 아니므로.
         */
        fun fromFailure(reason: TranslationResult.Reason): Availability = when (reason) {
            TranslationResult.Reason.ENGINE_UNAVAILABLE -> UNAVAILABLE
            TranslationResult.Reason.ADDON_NOT_INSTALLED -> ADDON_NOT_INSTALLED
            TranslationResult.Reason.LANGUAGE_UNSUPPORTED -> LANGUAGE_UNSUPPORTED
            TranslationResult.Reason.NEEDS_DOWNLOAD,
            TranslationResult.Reason.NEEDS_WIFI,
            -> NEEDS_DOWNLOAD
            TranslationResult.Reason.DOWNLOADING -> DOWNLOADING
            else -> UNKNOWN
        }
    }
}

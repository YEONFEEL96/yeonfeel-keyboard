package dev.badalab.yeonfeel.ime

import dev.badalab.yeonfeel.translate.TranslationEngine
import dev.badalab.yeonfeel.translate.TranslationResult

/**
 * 번역 패널의 요청 상태. 엔진 응답은 순서가 보장되지 않으므로 요청 번호로 낡은 응답을 버리고,
 * 앱 조합 영역에 들어가 있는 번역이 어떤 원문의 번역인지 기억해 엔터 동작을 정한다.
 */
class TranslateRequestState {

    enum class EnterAction {
        /** 원문이 비어 있다 — 평소 엔터(보내기·검색 등). */
        EDITOR_ACTION,

        /** 입력란의 번역이 지금 원문의 번역이다 — 확정한다. */
        CONFIRM,

        /** 지금 원문을 번역하는 요청이 이미 가고 있다 — 응답이 오면 확정한다. */
        CONFIRM_ON_RESULT,

        /** 번역이 낡았다 — 바로 번역하고 응답이 오면 확정한다. */
        TRANSLATE_THEN_CONFIRM,
    }

    private var latestId = 0L

    /** 응답을 기다리는 최신 요청의 원문. 없으면 null. */
    var pendingSource: String? = null
        private set

    /** 앱 조합 영역에 들어가 있는 번역의 원문. 없으면 null. */
    var shownSource: String? = null
        private set

    /** 최신 요청의 응답이 성공하면 바로 확정한다 (엔터로 요청한 번역). */
    var confirmPending = false

    /** 새 요청을 시작하고 번호를 돌려준다. 이전 요청의 응답은 이제 낡은 것이다. */
    fun issue(source: String): Long {
        latestId++
        pendingSource = source
        return latestId
    }

    /** 요청 [id]의 응답이 왔다. 최신 요청이면 true, 낡았으면 false(버린다). */
    fun complete(id: Long): Boolean {
        if (id != latestId) return false
        pendingSource = null
        return true
    }

    /** [source]의 번역을 앱 조합 영역에 썼다. */
    fun onShown(source: String) {
        shownSource = source
    }

    /** 가고 있는 요청을 모두 낡은 것으로 만든다. 입력란의 번역 기록은 그대로 둔다. */
    fun invalidate() {
        latestId++
        pendingSource = null
        confirmPending = false
    }

    /** 패널 상태를 처음으로 — 요청은 낡은 것이 되고 입력란의 번역은 더 이상 우리 것이 아니다. */
    fun reset() {
        invalidate()
        shownSource = null
    }

    fun enterAction(current: String): EnterAction = when {
        current.isEmpty() -> EnterAction.EDITOR_ACTION
        pendingSource == null && shownSource == current -> EnterAction.CONFIRM
        pendingSource == current -> EnterAction.CONFIRM_ON_RESULT
        else -> EnterAction.TRANSLATE_THEN_CONFIRM
    }
}

/** 번역 패널 상태 줄에 보여줄 상태. */
enum class TranslateStatus {
    IDLE,
    WORKING,
    DOWNLOADING,
    NEEDS_DOWNLOAD,
    NEEDS_WIFI,
    ENGINE_UNAVAILABLE,
    LANGUAGE_UNSUPPORTED,
    BLOCKED,
    ADDON_MISSING,
    BUSY,
    TEXT_TOO_LONG,
    ERROR,
    ;

    /** 엔진 자체를 이 기기·키보드에서 쓸 수 없다 — 툴바 번역 버튼을 흐리게 한다. */
    val engineUnusable: Boolean
        get() = this == ENGINE_UNAVAILABLE || this == ADDON_MISSING || this == BLOCKED

    /** 잠시 뒤 같은 원문으로 다시 시도할 상태 (모델 내려받는 중). */
    val retries: Boolean get() = this == DOWNLOADING

    companion object {
        /** 실패 이유를 상태로. [TranslationResult.Reason.CANCELLED]는 보여줄 필요가 없어 null. */
        fun of(reason: TranslationResult.Reason): TranslateStatus? = when (reason) {
            TranslationResult.Reason.ENGINE_UNAVAILABLE -> ENGINE_UNAVAILABLE
            TranslationResult.Reason.LANGUAGE_UNSUPPORTED -> LANGUAGE_UNSUPPORTED
            TranslationResult.Reason.NEEDS_DOWNLOAD -> NEEDS_DOWNLOAD
            TranslationResult.Reason.NEEDS_WIFI -> NEEDS_WIFI
            TranslationResult.Reason.DOWNLOADING -> DOWNLOADING
            TranslationResult.Reason.BLOCKED_IN_BACKGROUND -> BLOCKED
            TranslationResult.Reason.ADDON_NOT_INSTALLED -> ADDON_MISSING
            TranslationResult.Reason.BUSY -> BUSY
            TranslationResult.Reason.TEXT_TOO_LONG -> TEXT_TOO_LONG
            TranslationResult.Reason.ERROR -> ERROR
            TranslationResult.Reason.CANCELLED -> null
        }

        /**
         * 패널을 열 때 요청 없이 바로 보여줄 상태. 키보드 안에서 막힌 Gemini Nano는 [BLOCKED]
         * (이때는 요청도 보내지 않는다), 이번 세션에 쓸 수 없다고 확인한 엔진은 그 이유, 아니면 [IDLE].
         */
        fun initial(
            engine: TranslationEngine,
            geminiBlocked: Boolean,
            knownUnusable: Pair<TranslationEngine, TranslateStatus>?,
        ): TranslateStatus = when {
            engine == TranslationEngine.GEMINI_NANO && geminiBlocked -> BLOCKED
            knownUnusable != null && knownUnusable.first == engine -> knownUnusable.second
            else -> IDLE
        }
    }
}

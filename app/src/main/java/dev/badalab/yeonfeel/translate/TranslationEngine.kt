package dev.badalab.yeonfeel.translate

import android.content.Context
import android.os.Build
import java.util.Locale

/**
 * 키보드 번역 엔진. 세 엔진 모두 번역은 기기 안에서 하며 입력 문장을 서버로 보내지 않는다.
 * 키보드 앱 자체는 INTERNET 권한이 없다 — 네트워크가 필요한 ML Kit은 별도 애드온 앱이 맡는다.
 */
enum class TranslationEngine {
    /** Android 12+ 시스템 번역 서비스(TranslationManager). 제조사가 서비스를 넣은 기기에서만 동작한다. */
    SYSTEM,

    /**
     * ML Kit 번역. 네이티브 엔진(ABI당 약 16MB)과 INTERNET 권한(언어 모델 다운로드)이 필요해
     * 키보드가 아니라 같은 키로 서명한 별도 애드온 앱이 제공한다 (#25).
     */
    MLKIT,

    /** Gemini Nano (AICore Prompt API). 지원 플래그십 기기 전용. */
    GEMINI_NANO,
    ;

    companion object {
        /** 기본 엔진 — 추가 다운로드·네트워크가 필요 없는 시스템 번역. */
        val DEFAULT = SYSTEM

        /** 저장된 이름을 엔진으로. 없거나 알 수 없는 값이면 [DEFAULT]. */
        fun fromName(name: String?): TranslationEngine = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/** 번역 언어. [code]는 BCP-47 기본 언어 태그로, 시스템 번역·ML Kit 모두 그대로 쓴다. */
enum class TranslationLanguage(val code: String, val englishName: String, val sample: String) {
    KOREAN("ko", "Korean", "안녕하세요, 오늘 날씨가 정말 좋네요."),
    ENGLISH("en", "English", "Hello, the weather is really nice today."),
    JAPANESE("ja", "Japanese", "こんにちは、今日は本当にいい天気ですね。"),
    CHINESE("zh", "Simplified Chinese", "你好，今天天气真好。"),
    SPANISH("es", "Spanish", "Hola, hoy hace un tiempo muy bueno."),
    FRENCH("fr", "French", "Bonjour, il fait vraiment beau aujourd'hui."),
    GERMAN("de", "German", "Hallo, das Wetter ist heute wirklich schön."),
    VIETNAMESE("vi", "Vietnamese", "Xin chào, hôm nay thời tiết thật đẹp."),
    ;

    /** 현재 시스템 언어로 표시한 이름 (예: 한국어 설정에서 "영어"). */
    fun displayName(): String =
        Locale.forLanguageTag(code).getDisplayLanguage(Locale.getDefault()).replaceFirstChar { it.titlecase() }

    companion object {
        val DEFAULT_SOURCE = KOREAN
        val DEFAULT_TARGET = ENGLISH

        fun fromCode(code: String?): TranslationLanguage? = entries.firstOrNull { it.code == code }

        /** 저장된 코드를 언어로. 없거나 알 수 없는 값이면 [fallback]. */
        fun fromCodeOrDefault(code: String?, fallback: TranslationLanguage): TranslationLanguage =
            fromCode(code) ?: fallback
    }
}

/** 번역 결과. */
sealed interface TranslationResult {
    data class Success(val text: String) : TranslationResult

    data class Failure(val reason: Reason, val detail: String? = null) : TranslationResult

    enum class Reason {
        /** 이 기기(또는 OS 버전)에서는 엔진을 쓸 수 없다. */
        ENGINE_UNAVAILABLE,

        /** 엔진은 있지만 이 언어 쌍을 지원하지 않는다. */
        LANGUAGE_UNSUPPORTED,

        /** 언어 팩·모델을 먼저 내려받아야 한다 (시스템 번역 언어 팩은 시스템 설정에서). */
        NEEDS_DOWNLOAD,

        /** 모델을 받아야 하지만 Wi-Fi가 아니라 받지 않았다(키보드). Wi-Fi에 연결하면 받는다. */
        NEEDS_WIFI,

        /** 모델을 내려받는 중이다. 잠시 뒤 다시 시도하면 된다. */
        DOWNLOADING,

        /** 앱이 화면 맨 앞에 있지 않아 차단됐다 (AICore의 포그라운드 전용 정책). */
        BLOCKED_IN_BACKGROUND,

        /** 엔진을 제공하는 애드온 앱(ML Kit)이 설치돼 있지 않다. */
        ADDON_NOT_INSTALLED,

        /** 사용량 한도·동시 요청 제한에 걸렸다. */
        BUSY,

        /** 원문이 엔진의 입력 한도보다 길다. 잘라서 번역하지 않는다 — 끝부분이 말없이 사라지므로. */
        TEXT_TOO_LONG,

        /** 다른 언어 쌍의 새 요청이 이 요청을 대신했다. 화면에 보여줄 필요가 없다. */
        CANCELLED,

        ERROR,
    }
}

/**
 * 번역 엔진 공통 인터페이스. 구현은 현재 언어 쌍의 번역기를 캐시한다.
 *
 * - [translate]와 [close]는 메인 스레드에서 부른다.
 * - 콜백은 요청마다 메인 스레드에서 정확히 한 번 불린다. 단, [close] 뒤에는 어떤 콜백도 불리지 않는다
 *   ([close] 뒤의 [translate]도 무시된다). 바로 알 수 있는 실패는 [translate] 안에서 곧장 불릴 수도 있다.
 * - 요청이 겹치면 결과가 요청 순서대로 온다는 보장은 없다 — 호출하는 쪽이 요청 번호로 낡은 결과를 버린다.
 */
interface TranslationBackend {
    fun translate(
        text: String,
        source: TranslationLanguage,
        target: TranslationLanguage,
        callback: (TranslationResult) -> Unit,
    )

    /**
     * 이 언어 쌍으로 지금 번역할 수 있는지 알려준다 (설정 화면의 엔진 상태 표시용).
     * 상태만 조회하며 모델·언어 팩 다운로드는 절대 시작하지 않는다. 콜백 약속은 [translate]와 같다 —
     * 메인 스레드에서 정확히 한 번, [close] 뒤에는 불리지 않는다.
     *
     * 기본 구현은 조회를 지원하지 않는 엔진용으로 곧장 [Availability.UNKNOWN]을 답한다.
     */
    fun checkAvailability(
        source: TranslationLanguage,
        target: TranslationLanguage,
        callback: (Availability) -> Unit,
    ) {
        callback(Availability.UNKNOWN)
    }

    fun close()

    companion object {
        /**
         * 엔진 구현을 만든다. OS 버전이 모자라면 엔진 클래스를 아예 로드하지 않는다 —
         * 시스템 번역은 API 31, Gemini Nano 라이브러리는 API 26 클래스를 참조한다.
         *
         * [allowMeteredDownload]가 false면(키보드) 애드온이 ML Kit 모델을 Wi-Fi에서만 받는다.
         *
         * Gemini Nano는 라이브러리 호출이 끝나지 않을 수 있어(서비스 멈춤) [TimeoutBackend]로
         * 감싸 "콜백은 정확히 한 번" 약속을 지킨다. 시스템 번역은 자체 제한 시간이 있다.
         */
        fun create(
            context: Context,
            engine: TranslationEngine,
            allowMeteredDownload: Boolean,
        ): TranslationBackend = when (engine) {
            TranslationEngine.SYSTEM ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SystemTranslationBackend(context)
                } else {
                    UnavailableBackend()
                }
            // 애드온 연결(#25)이 생기기 전까지는 늘 '애드온 없음'으로 답한다.
            TranslationEngine.MLKIT -> UnavailableBackend(TranslationResult.Reason.ADDON_NOT_INSTALLED)
            TranslationEngine.GEMINI_NANO ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    TimeoutBackend(GeminiNanoTranslationBackend(context), LIBRARY_TIMEOUT_MS)
                } else {
                    UnavailableBackend()
                }
        }
    }
}

/** 라이브러리 엔진의 요청당 제한 시간. 온디바이스 LLM의 첫 추론(워밍업 포함)도 들어오도록 넉넉히 잡는다. */
internal const val LIBRARY_TIMEOUT_MS = 30_000L

/**
 * 제한 시간 안에 결과가 없으면 [TranslationResult.Reason.ERROR]("timeout")로 끝낸다.
 * 늦게 온 실제 결과는 버린다. 메인 스레드에서만 쓰므로 동기화가 필요 없다.
 */
internal class TimeoutBackend(
    private val inner: TranslationBackend,
    private val timeoutMs: Long,
) : TranslationBackend {
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var closed = false

    override fun translate(
        text: String,
        source: TranslationLanguage,
        target: TranslationLanguage,
        callback: (TranslationResult) -> Unit,
    ) {
        if (closed) return
        var done = false
        val token = Any()
        val finish = { result: TranslationResult ->
            if (!done && !closed) {
                done = true
                handler.removeCallbacksAndMessages(token)
                callback(result)
            }
        }
        handler.postAtTime(
            { finish(TranslationResult.Failure(TranslationResult.Reason.ERROR, "timeout")) },
            token,
            android.os.SystemClock.uptimeMillis() + timeoutMs,
        )
        inner.translate(text, source, target, finish)
    }

    /** 상태 조회도 같은 제한 시간을 건다. 시간 안에 답이 없으면 [Availability.UNKNOWN]. */
    override fun checkAvailability(
        source: TranslationLanguage,
        target: TranslationLanguage,
        callback: (Availability) -> Unit,
    ) {
        if (closed) return
        var done = false
        val token = Any()
        val finish = { availability: Availability ->
            if (!done && !closed) {
                done = true
                handler.removeCallbacksAndMessages(token)
                callback(availability)
            }
        }
        handler.postAtTime(
            { finish(Availability.UNKNOWN) },
            token,
            android.os.SystemClock.uptimeMillis() + timeoutMs,
        )
        inner.checkAvailability(source, target, finish)
    }

    override fun close() {
        closed = true
        handler.removeCallbacksAndMessages(null)
        inner.close()
    }
}

/** 엔진을 쓸 수 없는 경우 (OS 버전 부족, 애드온 없음). 모든 요청에 [reason]으로 답한다. */
private class UnavailableBackend(
    private val reason: TranslationResult.Reason = TranslationResult.Reason.ENGINE_UNAVAILABLE,
) : TranslationBackend {
    private var closed = false

    override fun translate(
        text: String,
        source: TranslationLanguage,
        target: TranslationLanguage,
        callback: (TranslationResult) -> Unit,
    ) {
        if (closed) return
        callback(TranslationResult.Failure(reason))
    }

    override fun checkAvailability(
        source: TranslationLanguage,
        target: TranslationLanguage,
        callback: (Availability) -> Unit,
    ) {
        if (closed) return
        callback(Availability.fromFailure(reason))
    }

    override fun close() {
        closed = true
    }
}

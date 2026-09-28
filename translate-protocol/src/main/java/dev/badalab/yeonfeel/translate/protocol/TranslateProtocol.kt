package dev.badalab.yeonfeel.translate.protocol

/**
 * 키보드 ↔ ML Kit 애드온 번역 IPC 프로토콜 (Messenger + Bundle).
 *
 * - 키보드는 명시적 인텐트([ADDON_PACKAGE]/[SERVICE_CLASS])로 애드온의 서비스에 바인드한다.
 *   서비스는 서명 권한 [PERMISSION]으로 보호돼 같은 키로 서명한 앱(키보드)만 바인드할 수 있다.
 * - 요청: `what` = [MSG_TRANSLATE], `arg1` = 요청 번호, `replyTo` = 키보드의 응답 Messenger,
 *   `data` = [TranslateRequest.encode]로 채운 Bundle.
 * - 응답: `what` = [MSG_RESULT], `arg1` = 같은 요청 번호, `data` = [TranslateResponse.encode].
 *
 * 필드를 더하는 호환 변경은 버전을 올리지 않는다(모르는 키는 무시한다). 기존 필드의 뜻이 바뀌는
 * 호환되지 않는 변경만 [PROTOCOL_VERSION]을 올리고, 받는 쪽은 [isSupportedVersion]으로 거른다.
 */
object TranslateProtocol {
    /** 현재 프로토콜 버전. 호환되지 않는 변경일 때만 올린다. */
    const val PROTOCOL_VERSION = 1

    /** 이 빌드가 받아들이는 가장 낮은 버전. */
    const val MIN_SUPPORTED_VERSION = 1

    const val ADDON_PACKAGE = "dev.badalab.yeonfeel.translate.mlkit"
    const val SERVICE_CLASS = "dev.badalab.yeonfeel.translate.mlkit.TranslateService"

    /** 서비스 바인드 권한. 두 앱 매니페스트에 protectionLevel="signature"로 선언한다. */
    const val PERMISSION = "dev.badalab.yeonfeel.permission.TRANSLATE"

    const val MSG_TRANSLATE = 1
    const val MSG_RESULT = 2

    /**
     * 원문 최대 길이(UTF-16 단위). 키보드 입력으로는 넉넉하고, Binder 트랜잭션 한도(1MB)보다
     * 훨씬 작다. 넘으면 잘라 보내지 않고 [FailureCode.TEXT_TOO_LONG]으로 거절한다.
     */
    const val MAX_TEXT_LENGTH = 5_000

    /** 실패 상세 메시지 최대 길이. 원문·번역문은 절대 담지 않는다 (라이브러리 오류 메시지만). */
    const val MAX_DETAIL_LENGTH = 200

    /** 언어 코드 최대 길이 (BCP-47 기본 태그면 충분하다). */
    const val MAX_LANGUAGE_LENGTH = 35

    internal const val KEY_VERSION = "v"
    internal const val KEY_TEXT = "text"
    internal const val KEY_SOURCE = "source"
    internal const val KEY_TARGET = "target"
    internal const val KEY_ALLOW_METERED = "allowMetered"
    internal const val KEY_STATUS = "status"
    internal const val KEY_DETAIL = "detail"

    /** 응답 [KEY_STATUS]의 성공 값. 실패는 [FailureCode.code]. */
    internal const val STATUS_OK = 0

    fun isSupportedVersion(version: Int): Boolean = version in MIN_SUPPORTED_VERSION..PROTOCOL_VERSION

    private val LANGUAGE_PATTERN = Regex("[A-Za-z]{2,3}(-[A-Za-z0-9]{1,8})*")

    /** BCP-47 형태의 언어 코드인지 (예: "ko", "zh", "zh-Hant"). */
    fun isValidLanguageCode(code: String?): Boolean =
        code != null && code.length <= MAX_LANGUAGE_LENGTH && LANGUAGE_PATTERN.matches(code)

    internal fun clipDetail(detail: String?): String? = detail?.take(MAX_DETAIL_LENGTH)
}

/**
 * 실패 코드. 이름과 순서가 키보드의 `TranslationResult.Reason`과 1:1로 대응한다
 * (키보드 단위 테스트가 확인한다). [code]는 전송값이라 한번 정하면 바꾸지 않는다.
 */
enum class FailureCode(val code: Int) {
    ENGINE_UNAVAILABLE(1),
    LANGUAGE_UNSUPPORTED(2),
    NEEDS_DOWNLOAD(3),
    NEEDS_WIFI(4),
    DOWNLOADING(5),
    BLOCKED_IN_BACKGROUND(6),
    ADDON_NOT_INSTALLED(7),
    BUSY(8),
    TEXT_TOO_LONG(9),
    CANCELLED(10),
    ERROR(11),
    ;

    companion object {
        fun fromCode(code: Int): FailureCode? = entries.firstOrNull { it.code == code }
    }
}

/** Bundle 같은 키-값 저장소 읽기. 없거나 타입이 다르면 null. */
interface FieldReader {
    fun string(key: String): String?
    fun int(key: String): Int?
    fun boolean(key: String): Boolean?
}

/** Bundle 같은 키-값 저장소 쓰기. */
interface FieldWriter {
    fun putString(key: String, value: String)
    fun putInt(key: String, value: Int)
    fun putBoolean(key: String, value: Boolean)
}

/** 번역 요청. [source]·[target]은 BCP-47 언어 코드. */
data class TranslateRequest(
    val text: String,
    val source: String,
    val target: String,
    val allowMeteredDownload: Boolean,
) {
    fun encode(out: FieldWriter) {
        out.putInt(TranslateProtocol.KEY_VERSION, TranslateProtocol.PROTOCOL_VERSION)
        out.putString(TranslateProtocol.KEY_TEXT, text)
        out.putString(TranslateProtocol.KEY_SOURCE, source)
        out.putString(TranslateProtocol.KEY_TARGET, target)
        out.putBoolean(TranslateProtocol.KEY_ALLOW_METERED, allowMeteredDownload)
    }

    /** 디코드 결과: 올바른 요청이거나, 그대로 돌려보낼 실패 응답. */
    sealed interface Decoded {
        data class Valid(val request: TranslateRequest) : Decoded
        data class Invalid(val response: TranslateResponse.Failure) : Decoded
    }

    companion object {
        fun decode(fields: FieldReader): Decoded {
            val version = fields.int(TranslateProtocol.KEY_VERSION)
            if (version == null || !TranslateProtocol.isSupportedVersion(version)) {
                return invalid(FailureCode.ENGINE_UNAVAILABLE, PROTOCOL_MISMATCH)
            }
            val text = fields.string(TranslateProtocol.KEY_TEXT)
            val source = fields.string(TranslateProtocol.KEY_SOURCE)
            val target = fields.string(TranslateProtocol.KEY_TARGET)
            val allowMetered = fields.boolean(TranslateProtocol.KEY_ALLOW_METERED)
            if (text == null || allowMetered == null) return invalid(FailureCode.ERROR, MALFORMED)
            if (source == null || target == null ||
                !TranslateProtocol.isValidLanguageCode(source) || !TranslateProtocol.isValidLanguageCode(target)
            ) {
                return invalid(FailureCode.LANGUAGE_UNSUPPORTED, null)
            }
            if (text.length > TranslateProtocol.MAX_TEXT_LENGTH) return invalid(FailureCode.TEXT_TOO_LONG, null)
            return Decoded.Valid(TranslateRequest(text, source, target, allowMetered))
        }

        private fun invalid(code: FailureCode, detail: String?) =
            Decoded.Invalid(TranslateResponse.Failure(code, detail))
    }

    /** 원문이 로그·예외 메시지에 새지 않도록 문자열 표현에서 뺀다. */
    override fun toString(): String =
        "TranslateRequest(length=${text.length}, source=$source, target=$target, " +
            "allowMeteredDownload=$allowMeteredDownload)"
}

/** 번역 응답. */
sealed interface TranslateResponse {
    fun encode(out: FieldWriter)

    data class Success(val text: String) : TranslateResponse {
        override fun encode(out: FieldWriter) {
            out.putInt(TranslateProtocol.KEY_VERSION, TranslateProtocol.PROTOCOL_VERSION)
            out.putInt(TranslateProtocol.KEY_STATUS, TranslateProtocol.STATUS_OK)
            out.putString(TranslateProtocol.KEY_TEXT, text)
        }

        /** 번역문이 로그에 새지 않도록 문자열 표현에서 뺀다. */
        override fun toString(): String = "Success(length=${text.length})"
    }

    data class Failure(val code: FailureCode, val detail: String? = null) : TranslateResponse {
        override fun encode(out: FieldWriter) {
            out.putInt(TranslateProtocol.KEY_VERSION, TranslateProtocol.PROTOCOL_VERSION)
            out.putInt(TranslateProtocol.KEY_STATUS, code.code)
            TranslateProtocol.clipDetail(detail)?.let { out.putString(TranslateProtocol.KEY_DETAIL, it) }
        }
    }

    companion object {
        /**
         * 응답을 읽는다. 버전이 맞지 않으면 [FailureCode.ENGINE_UNAVAILABLE], 형식이 깨졌으면
         * [FailureCode.ERROR]. 모르는 실패 코드(더 새 애드온)도 [FailureCode.ERROR]로 읽는다.
         */
        fun decode(fields: FieldReader): TranslateResponse {
            val version = fields.int(TranslateProtocol.KEY_VERSION)
            if (version == null || !TranslateProtocol.isSupportedVersion(version)) {
                return Failure(FailureCode.ENGINE_UNAVAILABLE, PROTOCOL_MISMATCH)
            }
            val status = fields.int(TranslateProtocol.KEY_STATUS) ?: return Failure(FailureCode.ERROR, MALFORMED)
            if (status == TranslateProtocol.STATUS_OK) {
                val text = fields.string(TranslateProtocol.KEY_TEXT) ?: return Failure(FailureCode.ERROR, MALFORMED)
                return Success(text)
            }
            val detail = TranslateProtocol.clipDetail(fields.string(TranslateProtocol.KEY_DETAIL))
            val code = FailureCode.fromCode(status) ?: return Failure(FailureCode.ERROR, detail ?: "unknown status $status")
            return Failure(code, detail)
        }
    }
}

/** 프로토콜 버전이 맞지 않을 때의 상세 메시지 — 키보드나 애드온을 업데이트해야 한다. */
const val PROTOCOL_MISMATCH = "protocol version mismatch"

/** 요청·응답 형식이 깨졌을 때의 상세 메시지. */
const val MALFORMED = "malformed message"

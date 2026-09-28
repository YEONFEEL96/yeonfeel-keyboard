package dev.badalab.yeonfeel.translate

import com.google.mlkit.genai.common.GenAiException

/**
 * Gemini Nano 번역 프롬프트, 출력 정리, 오류 코드 분류. Android·AICore 런타임에 의존하지 않는 순수 함수라
 * JVM 테스트로 검증한다 (GenAiException 오류 코드는 컴파일 때 상수로 들어가 클래스를 로드하지 않는다).
 */
object GeminiPrompt {

    /** 온디바이스 모델 입력 한도(약 4,000토큰)보다 넉넉히 작게 자른다. 키보드 입력은 보통 훨씬 짧다. */
    const val MAX_INPUT_CHARS = 2_000

    fun build(text: String, source: TranslationLanguage, target: TranslationLanguage): String =
        "Translate the text between <text> tags from ${source.englishName} to ${target.englishName}. " +
            "Reply with only the ${target.englishName} translation: no explanations, notes, " +
            "quotes, or tags. Keep line breaks, emoji, names, and punctuation style.\n" +
            "<text>\n${truncate(text)}\n</text>"

    /** [MAX_INPUT_CHARS]로 자르되 서로게이트 쌍(이모지 등)을 반으로 가르지 않는다. */
    internal fun truncate(text: String): String {
        if (text.length <= MAX_INPUT_CHARS) return text
        val end = if (text[MAX_INPUT_CHARS - 1].isHighSurrogate()) MAX_INPUT_CHARS - 1 else MAX_INPUT_CHARS
        return text.substring(0, end)
    }

    /**
     * 모델이 지시를 어기고 덧붙인 흔한 꾸밈을 걷어낸다 — 앞뒤 공백, 되돌려준 태그,
     * "Translation:" 머리말, 전체를 감싼 따옴표. 따옴표는 원문이 따옴표로 감싸여 있지 않을 때만 벗긴다.
     */
    fun clean(output: String, original: String): String {
        var text = output.trim()
        text = text.removePrefix("<text>").removeSuffix("</text>").trim()
        LEAD_IN.find(text)?.let { text = text.substring(it.range.last + 1).trim() }
        if (quotePair(original.trim()) == null) {
            quotePair(text)?.let { (open, close) ->
                text = text.substring(open.length, text.length - close.length).trim()
            }
        }
        return text
    }

    /** GenAiException 오류 코드를 화면에 보여줄 실패 이유로. */
    fun failureReason(errorCode: Int): TranslationResult.Reason = when (errorCode) {
        GenAiException.ErrorCode.BACKGROUND_USE_BLOCKED -> TranslationResult.Reason.BLOCKED_IN_BACKGROUND
        GenAiException.ErrorCode.BUSY,
        GenAiException.ErrorCode.PER_APP_BATTERY_USE_QUOTA_EXCEEDED,
        -> TranslationResult.Reason.BUSY
        GenAiException.ErrorCode.NOT_AVAILABLE,
        GenAiException.ErrorCode.NOT_SUPPORTED,
        GenAiException.ErrorCode.AICORE_INCOMPATIBLE,
        GenAiException.ErrorCode.NEEDS_SYSTEM_UPDATE,
        -> TranslationResult.Reason.ENGINE_UNAVAILABLE
        else -> TranslationResult.Reason.ERROR
    }

    /** [text] 전체를 감싼 따옴표 쌍. 없으면 null. */
    private fun quotePair(text: String): Pair<String, String>? = QUOTES.firstOrNull { (open, close) ->
        text.length >= open.length + close.length && text.startsWith(open) && text.endsWith(close)
    }

    private val QUOTES = listOf("\"" to "\"", "“" to "”", "'" to "'", "‘" to "’", "「" to "」", "『" to "』")

    // "Translation:", "English translation:", "Here is the translation:", "번역:" 같은 머리말.
    private val LEAD_IN = Regex(
        "^(?:(?:here is|here's) (?:the )?)?(?:[a-z]+ )?(?:translation|translated text)\\s*[:：]\\s*|^번역\\s*[:：]\\s*",
        RegexOption.IGNORE_CASE,
    )
}

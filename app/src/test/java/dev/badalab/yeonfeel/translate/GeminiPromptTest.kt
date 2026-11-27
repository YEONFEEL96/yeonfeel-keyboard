package dev.badalab.yeonfeel.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiPromptTest {

    @Test
    fun `프롬프트에 원문·대상 언어 이름과 태그로 감싼 원문이 들어간다`() {
        val prompt = GeminiPrompt.build("안녕하세요", TranslationLanguage.KOREAN, TranslationLanguage.ENGLISH)

        assertTrue(prompt.contains("from Korean to English"))
        assertTrue(prompt.contains("only the English translation"))
        assertTrue(prompt.endsWith("<text>\n안녕하세요\n</text>"))
    }

    @Test
    fun `중국어는 간체로 지정한다`() {
        val prompt = GeminiPrompt.build("hi", TranslationLanguage.ENGLISH, TranslationLanguage.CHINESE)

        assertTrue(prompt.contains("from English to Simplified Chinese"))
    }

    @Test
    fun `입력은 최대 길이로 자른다`() {
        val long = "가".repeat(GeminiPrompt.MAX_INPUT_CHARS + 500)
        val prompt = GeminiPrompt.build(long, TranslationLanguage.KOREAN, TranslationLanguage.ENGLISH)

        assertTrue(prompt.contains("가".repeat(GeminiPrompt.MAX_INPUT_CHARS) + "\n</text>"))
        assertFalse(prompt.contains("가".repeat(GeminiPrompt.MAX_INPUT_CHARS + 1)))
    }

    @Test
    fun `최대 길이 안의 입력은 그대로 둔다`() {
        val exact = "a".repeat(GeminiPrompt.MAX_INPUT_CHARS)

        assertEquals(exact, GeminiPrompt.truncate(exact))
    }

    @Test
    fun `자르는 위치가 이모지 중간이면 이모지 앞에서 자른다`() {
        val text = "a".repeat(GeminiPrompt.MAX_INPUT_CHARS - 1) + "😀" + "b"
        val cut = GeminiPrompt.truncate(text)

        assertEquals("a".repeat(GeminiPrompt.MAX_INPUT_CHARS - 1), cut)
        assertFalse(cut.last().isHighSurrogate())
    }

    @Test
    fun `깨끗한 출력은 앞뒤 공백만 걷어낸다`() {
        assertEquals("Hello.", GeminiPrompt.clean("  Hello.\n", "안녕."))
    }

    @Test
    fun `되돌려준 태그를 걷어낸다`() {
        assertEquals("Hello.", GeminiPrompt.clean("<text>\nHello.\n</text>", "안녕."))
    }

    @Test
    fun `번역 머리말을 걷어낸다`() {
        assertEquals("Hello.", GeminiPrompt.clean("Translation: Hello.", "안녕."))
        assertEquals("Hello.", GeminiPrompt.clean("translated text：Hello.", "안녕."))
        assertEquals("Hello.", GeminiPrompt.clean("English translation: Hello.", "안녕."))
        assertEquals("Hello.", GeminiPrompt.clean("Here is the translation: Hello.", "안녕."))
        assertEquals("안녕.", GeminiPrompt.clean("번역: 안녕.", "Hello."))
    }

    @Test
    fun `머리말이 아닌 문장 속 콜론은 그대로 둔다`() {
        assertEquals("Note: bring an umbrella.", GeminiPrompt.clean("Note: bring an umbrella.", "참고: 우산 챙겨."))
    }

    @Test
    fun `원문에 없던 감싼 따옴표는 벗긴다`() {
        assertEquals("Hello.", GeminiPrompt.clean("\"Hello.\"", "안녕."))
        assertEquals("Hello.", GeminiPrompt.clean("“Hello.”", "안녕."))
        assertEquals("こんにちは", GeminiPrompt.clean("「こんにちは」", "안녕"))
    }

    @Test
    fun `원문이 따옴표로 감싸여 있으면 따옴표를 남긴다`() {
        assertEquals("\"Hello.\"", GeminiPrompt.clean("\"Hello.\"", "\"안녕.\""))
        assertEquals("“Hello.”", GeminiPrompt.clean("“Hello.”", "“안녕.”"))
    }

    @Test
    fun `안쪽 따옴표나 한쪽 따옴표는 건드리지 않는다`() {
        assertEquals("He said \"hi\" to me.", GeminiPrompt.clean("He said \"hi\" to me.", "그가 \"안녕\"이라고 했다."))
        assertEquals("\"Hello.", GeminiPrompt.clean("\"Hello.", "안녕."))
        assertEquals("\"", GeminiPrompt.clean("\"", "따옴표"))
    }

    @Test
    fun `태그·머리말·따옴표가 겹쳐도 모두 걷어낸다`() {
        assertEquals("Hello.", GeminiPrompt.clean("<text>Translation: \"Hello.\"</text>", "안녕."))
    }

    @Test
    fun `AICore 오류 코드를 실패 이유로 분류한다`() {
        assertEquals(TranslationResult.Reason.BLOCKED_IN_BACKGROUND, GeminiPrompt.failureReason(30))
        assertEquals(TranslationResult.Reason.BUSY, GeminiPrompt.failureReason(9))
        assertEquals(TranslationResult.Reason.BUSY, GeminiPrompt.failureReason(27))
        assertEquals(TranslationResult.Reason.ENGINE_UNAVAILABLE, GeminiPrompt.failureReason(8))
        assertEquals(TranslationResult.Reason.ENGINE_UNAVAILABLE, GeminiPrompt.failureReason(16))
        assertEquals(TranslationResult.Reason.ENGINE_UNAVAILABLE, GeminiPrompt.failureReason(-101))
        assertEquals(TranslationResult.Reason.ENGINE_UNAVAILABLE, GeminiPrompt.failureReason(604))
        assertEquals(TranslationResult.Reason.ERROR, GeminiPrompt.failureReason(0))
        assertEquals(TranslationResult.Reason.ERROR, GeminiPrompt.failureReason(501))
    }
}

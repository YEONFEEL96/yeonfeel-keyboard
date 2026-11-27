package dev.badalab.yeonfeel.translate

import dev.badalab.yeonfeel.translate.protocol.FailureCode
import dev.badalab.yeonfeel.translate.protocol.TranslateResponse
import org.junit.Assert.assertEquals
import org.junit.Test

class AddonProtocolMappingTest {

    @Test
    fun `실패 코드와 실패 이유는 이름이 같은 것끼리 1대1로 대응한다`() {
        // Reason에 값을 더하면 FailureCode에도 같은 이름으로 더해야 한다 (애드온이 보낼 수 있도록).
        assertEquals(
            TranslationResult.Reason.entries.map { it.name },
            FailureCode.entries.map { it.name },
        )
        FailureCode.entries.forEach { assertEquals(it.name, it.toReason().name) }
        assertEquals(FailureCode.entries.size, FailureCode.entries.map { it.toReason() }.toSet().size)
    }

    @Test
    fun `성공 응답은 번역문 그대로 옮긴다`() {
        assertEquals(TranslationResult.Success("Hello"), TranslateResponse.Success("Hello").toTranslationResult())
    }

    @Test
    fun `실패 응답은 이유와 상세 메시지를 옮긴다`() {
        assertEquals(
            TranslationResult.Failure(TranslationResult.Reason.NEEDS_WIFI, null),
            TranslateResponse.Failure(FailureCode.NEEDS_WIFI).toTranslationResult(),
        )
        assertEquals(
            TranslationResult.Failure(TranslationResult.Reason.ERROR, "download failed: x"),
            TranslateResponse.Failure(FailureCode.ERROR, "download failed: x").toTranslationResult(),
        )
    }
}

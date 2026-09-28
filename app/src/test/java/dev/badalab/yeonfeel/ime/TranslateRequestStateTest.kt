package dev.badalab.yeonfeel.ime

import dev.badalab.yeonfeel.ime.TranslateRequestState.EnterAction
import dev.badalab.yeonfeel.translate.TranslationEngine
import dev.badalab.yeonfeel.translate.TranslationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslateRequestStateTest {

    @Test
    fun `최신 요청의 응답만 받는다`() {
        val state = TranslateRequestState()
        val first = state.issue("안")
        val second = state.issue("안녕")
        assertFalse(state.complete(first))
        assertEquals("안녕", state.pendingSource)
        assertTrue(state.complete(second))
        assertNull(state.pendingSource)
    }

    @Test
    fun `invalidate 뒤에는 가고 있던 응답을 버린다`() {
        val state = TranslateRequestState()
        val id = state.issue("안녕")
        state.confirmPending = true
        state.invalidate()
        assertFalse(state.complete(id))
        assertFalse(state.confirmPending)
    }

    @Test
    fun `엔터 - 빈 원문은 평소 동작`() {
        assertEquals(EnterAction.EDITOR_ACTION, TranslateRequestState().enterAction(""))
    }

    @Test
    fun `엔터 - 입력란 번역이 지금 원문의 것이면 확정`() {
        val state = TranslateRequestState()
        val id = state.issue("안녕")
        assertTrue(state.complete(id))
        state.onShown("안녕")
        assertEquals(EnterAction.CONFIRM, state.enterAction("안녕"))
    }

    @Test
    fun `엔터 - 원문이 바뀌었으면 번역 후 확정`() {
        val state = TranslateRequestState()
        state.complete(state.issue("안녕"))
        state.onShown("안녕")
        assertEquals(EnterAction.TRANSLATE_THEN_CONFIRM, state.enterAction("안녕하"))
    }

    @Test
    fun `엔터 - 지금 원문의 요청이 가고 있으면 응답 뒤 확정`() {
        val state = TranslateRequestState()
        state.issue("안녕")
        assertEquals(EnterAction.CONFIRM_ON_RESULT, state.enterAction("안녕"))
        // 다른 원문의 요청이 가고 있으면 다시 번역해야 한다.
        assertEquals(EnterAction.TRANSLATE_THEN_CONFIRM, state.enterAction("안녕하"))
    }

    @Test
    fun `reset은 입력란 번역 기록도 지운다`() {
        val state = TranslateRequestState()
        state.complete(state.issue("안녕"))
        state.onShown("안녕")
        state.reset()
        assertNull(state.shownSource)
        assertEquals(EnterAction.TRANSLATE_THEN_CONFIRM, state.enterAction("안녕"))
    }

    @Test
    fun `모든 실패 이유가 상태로 옮겨지고 CANCELLED만 조용히 넘긴다`() {
        TranslationResult.Reason.entries.forEach { reason ->
            val status = TranslateStatus.of(reason)
            if (reason == TranslationResult.Reason.CANCELLED) assertNull(status) else assertTrue(status != null)
        }
        assertEquals(TranslateStatus.ADDON_MISSING, TranslateStatus.of(TranslationResult.Reason.ADDON_NOT_INSTALLED))
        assertEquals(TranslateStatus.BLOCKED, TranslateStatus.of(TranslationResult.Reason.BLOCKED_IN_BACKGROUND))
    }

    @Test
    fun `엔진을 못 쓰는 상태만 버튼을 흐리게 하고 내려받는 중만 재시도한다`() {
        val unusable = TranslateStatus.entries.filter { it.engineUnusable }.toSet()
        assertEquals(
            setOf(TranslateStatus.ENGINE_UNAVAILABLE, TranslateStatus.ADDON_MISSING, TranslateStatus.BLOCKED),
            unusable,
        )
        assertEquals(listOf(TranslateStatus.DOWNLOADING), TranslateStatus.entries.filter { it.retries })
    }

    @Test
    fun `패널을 열 때의 첫 상태`() {
        assertEquals(
            TranslateStatus.BLOCKED,
            TranslateStatus.initial(TranslationEngine.GEMINI_NANO, geminiBlocked = true, knownUnusable = null),
        )
        // 막힘 기록은 Gemini Nano에만 해당한다.
        assertEquals(
            TranslateStatus.IDLE,
            TranslateStatus.initial(TranslationEngine.SYSTEM, geminiBlocked = true, knownUnusable = null),
        )
        val addonMissing = TranslationEngine.MLKIT to TranslateStatus.ADDON_MISSING
        assertEquals(
            TranslateStatus.ADDON_MISSING,
            TranslateStatus.initial(TranslationEngine.MLKIT, geminiBlocked = false, knownUnusable = addonMissing),
        )
        // 다른 엔진에서 확인한 실패는 지금 엔진과 무관하다.
        assertEquals(
            TranslateStatus.IDLE,
            TranslateStatus.initial(TranslationEngine.SYSTEM, geminiBlocked = false, knownUnusable = addonMissing),
        )
    }
}

package dev.badalab.yeonfeel.settings

import dev.badalab.yeonfeel.translate.Availability
import dev.badalab.yeonfeel.translate.TranslationEngine
import dev.badalab.yeonfeel.translate.TranslationLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class TranslationSettingsLogicTest {

    private val ko = TranslationLanguage.KOREAN
    private val en = TranslationLanguage.ENGLISH
    private val ja = TranslationLanguage.JAPANESE

    @Test
    fun `다른 언어를 고르면 그쪽만 바뀐다`() {
        assertEquals(ja to en, pickLanguagePair(ko, en, pickSource = true, picked = ja))
        assertEquals(ko to ja, pickLanguagePair(ko, en, pickSource = false, picked = ja))
    }

    @Test
    fun `반대쪽과 같은 언어를 고르면 맞바꾼다`() {
        assertEquals(en to ko, pickLanguagePair(ko, en, pickSource = true, picked = en))
        assertEquals(en to ko, pickLanguagePair(ko, en, pickSource = false, picked = ko))
    }

    @Test
    fun `같은 언어를 다시 고르면 그대로다`() {
        assertEquals(ko to en, pickLanguagePair(ko, en, pickSource = true, picked = ko))
        assertEquals(ko to en, pickLanguagePair(ko, en, pickSource = false, picked = en))
    }

    @Test
    fun `확인 결과가 없으면 확인 중이다`() {
        assertEquals(EngineStatus.CHECKING, status(TranslationEngine.SYSTEM, null))
    }

    @Test
    fun `확인 결과를 그대로 보여준다`() {
        val expected = mapOf(
            Availability.READY to EngineStatus.READY,
            Availability.NEEDS_DOWNLOAD to EngineStatus.NEEDS_DOWNLOAD,
            Availability.DOWNLOADING to EngineStatus.DOWNLOADING,
            Availability.LANGUAGE_UNSUPPORTED to EngineStatus.LANGUAGE_UNSUPPORTED,
            Availability.UNAVAILABLE to EngineStatus.NOT_ON_THIS_PHONE,
            Availability.ADDON_NOT_INSTALLED to EngineStatus.ADDON_NOT_INSTALLED,
            Availability.UNKNOWN to EngineStatus.UNKNOWN,
        )
        assertEquals(Availability.entries.toSet(), expected.keys)
        expected.forEach { (availability, status) ->
            assertEquals(status, status(TranslationEngine.SYSTEM, availability))
        }
    }

    @Test
    fun `애드온이 없으면 ML Kit은 확인 결과와 상관없이 애드온 없음이다`() {
        assertEquals(EngineStatus.ADDON_NOT_INSTALLED, status(TranslationEngine.MLKIT, null, addonInstalled = false))
        assertEquals(
            EngineStatus.ADDON_NOT_INSTALLED,
            status(TranslationEngine.MLKIT, Availability.UNKNOWN, addonInstalled = false),
        )
        assertEquals(EngineStatus.UNKNOWN, status(TranslationEngine.MLKIT, Availability.UNKNOWN, addonInstalled = true))
        // 애드온 설치 여부는 ML Kit에만 쓰인다.
        assertEquals(EngineStatus.READY, status(TranslationEngine.SYSTEM, Availability.READY, addonInstalled = false))
    }

    @Test
    fun `Gemini Nano 차단 기록은 준비됨보다 앞선다`() {
        assertEquals(
            EngineStatus.BLOCKED_IN_KEYBOARD,
            status(TranslationEngine.GEMINI_NANO, Availability.READY, geminiBlocked = true),
        )
        assertEquals(
            EngineStatus.BLOCKED_IN_KEYBOARD,
            status(TranslationEngine.GEMINI_NANO, null, geminiBlocked = true),
        )
        // 기기에 없으면 차단 기록보다 그 사실이 중요하다.
        assertEquals(
            EngineStatus.NOT_ON_THIS_PHONE,
            status(TranslationEngine.GEMINI_NANO, Availability.UNAVAILABLE, geminiBlocked = true),
        )
        assertEquals(EngineStatus.READY, status(TranslationEngine.GEMINI_NANO, Availability.READY))
        // 차단 기록은 Gemini Nano에만 쓰인다.
        assertEquals(EngineStatus.READY, status(TranslationEngine.SYSTEM, Availability.READY, geminiBlocked = true))
    }

    private fun status(
        engine: TranslationEngine,
        availability: Availability?,
        addonInstalled: Boolean = true,
        geminiBlocked: Boolean = false,
    ) = engineStatus(engine, availability, addonInstalled, geminiBlocked)
}

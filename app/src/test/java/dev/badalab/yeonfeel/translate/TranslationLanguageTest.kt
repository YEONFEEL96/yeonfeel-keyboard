package dev.badalab.yeonfeel.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TranslationLanguageTest {

    @Test
    fun `언어 코드로 언어를 찾는다`() {
        TranslationLanguage.entries.forEach { assertEquals(it, TranslationLanguage.fromCode(it.code)) }
    }

    @Test
    fun `모르는 코드·빈 값·null은 찾지 못한다`() {
        assertNull(TranslationLanguage.fromCode(null))
        assertNull(TranslationLanguage.fromCode(""))
        assertNull(TranslationLanguage.fromCode("xx"))
        // 코드는 정확히 일치해야 한다 (대문자·지역 태그는 저장하지 않는다).
        assertNull(TranslationLanguage.fromCode("KO"))
        assertNull(TranslationLanguage.fromCode("en-US"))
    }

    @Test
    fun `언어 코드는 서로 겹치지 않는다`() {
        val codes = TranslationLanguage.entries.map { it.code }
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun `기본 언어는 한국어에서 영어로`() {
        assertEquals(TranslationLanguage.KOREAN, TranslationLanguage.DEFAULT_SOURCE)
        assertEquals(TranslationLanguage.ENGLISH, TranslationLanguage.DEFAULT_TARGET)
    }

    @Test
    fun `저장값이 없거나 잘못되면 기본 언어로 돌아간다`() {
        val source = TranslationLanguage.DEFAULT_SOURCE
        assertEquals(source, TranslationLanguage.fromCodeOrDefault(null, source))
        assertEquals(source, TranslationLanguage.fromCodeOrDefault("", source))
        assertEquals(source, TranslationLanguage.fromCodeOrDefault("klingon", source))
        assertEquals(TranslationLanguage.JAPANESE, TranslationLanguage.fromCodeOrDefault("ja", source))
    }

    @Test
    fun `기본 엔진은 시스템 번역`() {
        assertEquals(TranslationEngine.SYSTEM, TranslationEngine.DEFAULT)
    }

    @Test
    fun `저장된 엔진 이름이 없거나 잘못되면 기본 엔진으로 돌아간다`() {
        assertEquals(TranslationEngine.DEFAULT, TranslationEngine.fromName(null))
        assertEquals(TranslationEngine.DEFAULT, TranslationEngine.fromName(""))
        assertEquals(TranslationEngine.DEFAULT, TranslationEngine.fromName("mlkit"))
        assertEquals(TranslationEngine.DEFAULT, TranslationEngine.fromName("DEEPL"))
        TranslationEngine.entries.forEach { assertEquals(it, TranslationEngine.fromName(it.name)) }
    }
}

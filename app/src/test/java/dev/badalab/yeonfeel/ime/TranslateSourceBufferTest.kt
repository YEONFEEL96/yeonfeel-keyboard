package dev.badalab.yeonfeel.ime

import dev.badalab.yeonfeel.hangul.ChunjiinComposer
import dev.badalab.yeonfeel.hangul.HangulComposer
import dev.badalab.yeonfeel.hangul.NaratgulComposer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslateSourceBufferTest {

    /** 키 간격을 넉넉히 두고(연타 아님) 자모를 넣는다. */
    private fun TranslateSourceBuffer.typeSlow(keys: String) {
        keys.forEachIndexed { index, key -> inputJamo(key, index * 1000L) }
    }

    @Test
    fun `두벌식 - 조합 중인 음절까지 원문에 들어간다`() {
        val buffer = TranslateSourceBuffer().apply { composer = HangulComposer() }
        buffer.typeSlow("ㅇㅏㄴㄴㅕㅇ")
        assertEquals("안녕", buffer.text)
        assertTrue(buffer.isComposing)
    }

    @Test
    fun `천지인 자판의 키는 천지인 조합기로 조합된다`() {
        val buffer = TranslateSourceBuffer().apply { composer = ChunjiinComposer() }
        // ㅇ + ㅣ + ㆍ = 아
        buffer.typeSlow("ㅇㅣㆍ")
        assertEquals("아", buffer.text)
    }

    @Test
    fun `나랏글 자판의 키는 나랏글 조합기로 조합된다`() {
        val buffer = TranslateSourceBuffer().apply { composer = NaratgulComposer() }
        buffer.typeSlow("ㄱㅏ")
        assertEquals("가", buffer.text)
    }

    @Test
    fun `조합기가 없으면 자모를 그대로 붙인다`() {
        val buffer = TranslateSourceBuffer()
        buffer.typeSlow("ㅎㅎ")
        assertEquals("ㅎㅎ", buffer.text)
    }

    @Test
    fun `공백·기호는 조합을 확정한 뒤 붙는다`() {
        val buffer = TranslateSourceBuffer().apply { composer = HangulComposer() }
        buffer.typeSlow("ㄱㅏ")
        buffer.inputChar(' ')
        buffer.typeSlow("ㄴㅏ")
        assertEquals("가 나", buffer.text)
        buffer.inputChar('.')
        assertFalse(buffer.isComposing)
        assertEquals("가 나.", buffer.text)
    }

    @Test
    fun `백스페이스는 조합 단계부터 지우고 비면 false`() {
        val buffer = TranslateSourceBuffer().apply { composer = HangulComposer() }
        buffer.inputChar('a')
        buffer.typeSlow("ㄱㅏㄴ")
        assertTrue(buffer.backspace())
        assertEquals("a가", buffer.text)
        assertTrue(buffer.backspace())
        assertTrue(buffer.backspace())
        assertEquals("a", buffer.text)
        assertTrue(buffer.backspace())
        assertTrue(buffer.isEmpty())
        assertFalse(buffer.backspace())
    }

    @Test
    fun `백스페이스는 서로게이트 쌍을 한 글자로 지운다`() {
        val buffer = TranslateSourceBuffer()
        buffer.inputChar('a')
        "😀".forEach { buffer.inputChar(it) }
        assertTrue(buffer.backspace())
        assertEquals("a", buffer.text)
    }

    @Test
    fun `기호 연타는 마지막 글자를 바꾼다 - 조합 중이면 안 바꾼다`() {
        val buffer = TranslateSourceBuffer().apply { composer = HangulComposer() }
        buffer.inputChar('.')
        assertEquals('.', buffer.lastChar())
        assertTrue(buffer.replaceLast(','))
        assertEquals(",", buffer.text)
        buffer.typeSlow("ㄱ")
        assertNull(buffer.lastChar())
        assertFalse(buffer.replaceLast('?'))
    }

    @Test
    fun `됬 치환 옵션은 확정 글자에 적용된다`() {
        val buffer = TranslateSourceBuffer().apply {
            composer = HangulComposer()
            fixDwaet = true
        }
        buffer.typeSlow("ㄷㅗㅣㅆ")
        buffer.flushComposer()
        assertEquals("됐", buffer.text)
    }

    @Test
    fun `clear는 원문과 조합 상태를 모두 비운다`() {
        val composer = HangulComposer()
        val buffer = TranslateSourceBuffer().apply { this.composer = composer }
        buffer.typeSlow("ㄱㅏ")
        buffer.clear()
        assertTrue(buffer.isEmpty())
        assertFalse(composer.isComposing)
        buffer.typeSlow("ㄴㅏ")
        assertEquals("나", buffer.text)
    }
}

package dev.badalab.yeonfeel.hangul

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WordCorrectorTest {

    private fun corrector(vararg entries: Pair<String, Long>): WordCorrector {
        val corrector = WordCorrector()
        val text = entries.joinToString("\n") { "${it.first} ${it.second}" }
        corrector.load(text.byteInputStream())
        return corrector
    }

    @Test
    fun `사전에 있는 어절은 교정하지 않는다`() {
        val c = corrector("안녕" to 1000L)
        assertNull(c.correct("안녕"))
    }

    @Test
    fun `인접 키 오타는 교정된다`() {
        // ㅛ↔ㅕ 인접: 안뇽 → 안녕
        val c = corrector("안녕" to 1000L, "방법" to 500L)
        assertEquals("안녕", c.correct("안뇽"))
    }

    @Test
    fun `쌍자음 시프트 오타는 교정된다`() {
        // ㅅ↔ㅆ 같은 자리: 아시 → 아씨
        val c = corrector("아씨" to 800L)
        assertEquals("아씨", c.correct("아시"))
    }

    @Test
    fun `편집거리가 먼 어절은 교정하지 않는다`() {
        val c = corrector("안녕" to 1000L)
        assertNull(c.correct("만두국"))
    }

    @Test
    fun `한 음절 어절은 건드리지 않는다`() {
        val c = corrector("안녕" to 1000L)
        assertNull(c.correct("녕"))
    }

    @Test
    fun `블룸 필터 - 실제 에셋과 해시 호환`() {
        val file = java.io.File("src/main/assets/ko_known.bloom")
        org.junit.Assume.assumeTrue(file.exists())
        val bloom = WordCorrector.BloomFilter(file.readBytes())
        // 말뭉치에 있는 어절은 보호된다 (졸려요 → 돌려요 오교정 방지 회귀)
        assertEquals(true, bloom.contains("졸려요"))
        assertEquals(true, bloom.contains("안녕"))
        // 무작위 비단어는 대부분 통과하지 않는다
        assertEquals(false, bloom.contains("쀍꿻뛣휋"))
    }

    @Test
    fun `복모음 분해 - 두벌식 키 입력열`() {
        assertEquals("ㅇㅗㅏㄴㅈㅓㄴ", WordCorrector.decompose("완전"))
        assertEquals("ㅇㅏㄴㅎ", WordCorrector.decompose("않"))
    }
}

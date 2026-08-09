package dev.badalab.yeonfeel.hangul

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HangulComposerTest {

    /** 에디터를 흉내 내어 자모 열을 입력한 뒤 (확정 + 조합 중) 전체 문자열을 돌려준다. */
    private fun type(jamos: String): String {
        val composer = HangulComposer()
        val committed = StringBuilder()
        var composing = ""
        jamos.forEach { jamo ->
            val result = composer.input(jamo)
            committed.append(result.commit)
            composing = result.composing
        }
        return committed.toString() + composing
    }

    @Test
    fun `기본 음절 조합`() {
        assertEquals("안녕", type("ㅇㅏㄴㄴㅕㅇ"))
        assertEquals("한글", type("ㅎㅏㄴㄱㅡㄹ"))
    }

    @Test
    fun `도깨비불 - 받침이 다음 글자 초성으로 이동`() {
        assertEquals("가나", type("ㄱㅏㄴㅏ"))
        assertEquals("읽어", type("ㅇㅣㄹㄱㅇㅓ"))
    }

    @Test
    fun `겹받침에서 도깨비불 - 마지막 요소만 이동`() {
        assertEquals("일거", type("ㅇㅣㄹㄱㅓ"))
    }

    @Test
    fun `겹받침 조합`() {
        assertEquals("닭", type("ㄷㅏㄹㄱ"))
        assertEquals("값", type("ㄱㅏㅂㅅ"))
        assertEquals("않", type("ㅇㅏㄴㅎ"))
    }

    @Test
    fun `복합 모음 조합`() {
        assertEquals("과", type("ㄱㅗㅏ"))
        assertEquals("왜", type("ㅇㅗㅐ"))
        assertEquals("위", type("ㅇㅜㅣ"))
        assertEquals("의", type("ㅇㅡㅣ"))
        assertEquals("워", type("ㅇㅜㅓ"))
    }

    @Test
    fun `쌍자음 초성`() {
        assertEquals("까치", type("ㄲㅏㅊㅣ"))
        assertEquals("빵", type("ㅃㅏㅇ"))
    }

    @Test
    fun `종성이 될 수 없는 자음은 새 글자로 시작`() {
        assertEquals("가따", type("ㄱㅏㄸㅏ"))
    }

    @Test
    fun `자음 연타는 각각 확정`() {
        assertEquals("ㄱㄱ", type("ㄱㄱ"))
        assertEquals("ㅋㅋㅋ", type("ㅋㅋㅋ"))
    }

    @Test
    fun `모음 단독 입력`() {
        assertEquals("ㅏㅏ", type("ㅏㅏ"))
        assertEquals("ㅘ", type("ㅗㅏ"))
    }

    @Test
    fun `백스페이스 역순 분해`() {
        val composer = HangulComposer()
        "ㄷㅏㄹㄱ".forEach { composer.input(it) } // 닭

        assertEquals("달", composer.backspace()!!.composing)
        assertEquals("다", composer.backspace()!!.composing)
        assertEquals("ㄷ", composer.backspace()!!.composing)
        assertEquals("", composer.backspace()!!.composing)
        assertNull(composer.backspace())
    }

    @Test
    fun `복합 모음 백스페이스 분해`() {
        val composer = HangulComposer()
        "ㄱㅗㅏ".forEach { composer.input(it) } // 과

        assertEquals("고", composer.backspace()!!.composing)
        assertEquals("ㄱ", composer.backspace()!!.composing)
    }

    private fun typeDanmoeum(jamos: String): String {
        val composer = HangulComposer().apply { doubleTapIotation = true }
        val committed = StringBuilder()
        var composing = ""
        jamos.forEach { jamo ->
            val result = composer.input(jamo)
            committed.append(result.commit)
            composing = result.composing
        }
        return committed.toString() + composing
    }

    @Test
    fun `단모음 - 모음 연타로 이중모음 입력`() {
        assertEquals("야", typeDanmoeum("ㅇㅏㅏ"))
        assertEquals("교", typeDanmoeum("ㄱㅗㅗ"))
        assertEquals("예", typeDanmoeum("ㅇㅔㅔ"))
    }

    @Test
    fun `단모음 - 자음 연타는 쌍자음이 되지 않는다 (하꾜 문제 회피)`() {
        assertEquals("학교", typeDanmoeum("ㅎㅏㄱㄱㅗㅗ"))
    }

    @Test
    fun `단모음 꺼짐 - 모음 연타는 별개 글자`() {
        assertEquals("아ㅏ", type("ㅇㅏㅏ"))
    }

    @Test
    fun `단모음 - 자음 빠른 연타는 쌍자음 토글`() {
        val composer = HangulComposer().apply {
            doubleTapIotation = true
            doubleTapDoubling = true
            multiTapTimeoutMs = 300
        }
        composer.input('ㄱ', 0)
        assertEquals("ㄲ", composer.input('ㄱ', 100).composing) // 연타 → 쌍자음
        assertEquals("까", composer.input('ㅏ', 200).composing)
        // 받침 ㅅ 연타 → ㅆ받침
        composer.input('ㅅ', 300)
        assertEquals("깠", composer.input('ㅅ', 400).composing)
    }

    @Test
    fun `단모음 - 받침에서 승급 불가면 새 글자 쌍자음 초성으로`() {
        val composer = HangulComposer().apply {
            doubleTapIotation = true
            doubleTapDoubling = true
            multiTapTimeoutMs = 300
        }
        // 진짜: ㅈㅣㄴ + ㅈㅈ(→ㅉ) + ㅏ — 겹받침 ㄵ에서 ㅈ을 빼내 ㅉ 초성으로
        val committed = StringBuilder()
        var composing = ""
        "ㅈㅣㄴㅈㅈㅏ".forEachIndexed { index, jamo ->
            val result = composer.input(jamo, index * 100L)
            committed.append(result.commit)
            composing = result.composing
        }
        assertEquals("진짜", committed.toString() + composing)

        // 아찌: 홑받침 ㅈ에서도 동일하게 동작
        val composer2 = HangulComposer().apply {
            doubleTapIotation = true
            doubleTapDoubling = true
            multiTapTimeoutMs = 300
        }
        val committed2 = StringBuilder()
        var composing2 = ""
        "ㅇㅏㅈㅈㅣ".forEachIndexed { index, jamo ->
            val result = composer2.input(jamo, index * 100L)
            committed2.append(result.commit)
            composing2 = result.composing
        }
        assertEquals("아찌", committed2.toString() + composing2)
    }

    @Test
    fun `단모음 - 세 번째 연타는 새 자음 (ㄷㄷㄷ→ㄸㄷ)`() {
        val composer = HangulComposer().apply {
            doubleTapIotation = true
            doubleTapDoubling = true
            multiTapTimeoutMs = 300
        }
        composer.input('ㄷ', 0)
        assertEquals("ㄸ", composer.input('ㄷ', 100).composing)
        val third = composer.input('ㄷ', 200)
        assertEquals("ㄸ", third.commit)
        assertEquals("ㄷ", third.composing)
    }

    @Test
    fun `단모음 - 판정 시간 초과 연타는 별개 자음`() {
        val composer = HangulComposer().apply {
            doubleTapIotation = true
            doubleTapDoubling = true
            multiTapTimeoutMs = 300
        }
        composer.input('ㄱ', 0)
        val result = composer.input('ㄱ', 1000)
        assertEquals("ㄱ", result.commit)
        assertEquals("ㄱ", result.composing)
    }

    @Test
    fun `flush는 조합 중 글자를 확정하고 상태를 비운다`() {
        val composer = HangulComposer()
        "ㄱㅏ".forEach { composer.input(it) }

        assertEquals("가", composer.flush())
        assertEquals(false, composer.isComposing)
        assertEquals("", composer.flush())
    }
}

class DwaetFixTest {
    private fun type(composer: HangulComposer, keys: String): String {
        val out = StringBuilder()
        var composing = ""
        keys.forEachIndexed { i, k ->
            val r = composer.input(k, i * 1000L)
            out.append(r.commit)
            composing = r.composing
        }
        return out.toString() + composing
    }

    @org.junit.Test
    fun `됬 금지 모드 - 됬이 됐으로 바뀐다`() {
        val c = HangulComposer().apply { fixDwaet = true }
        org.junit.Assert.assertEquals("됐", type(c, "ㄷㅗㅣㅆ"))
    }

    @org.junit.Test
    fun `됬 금지 모드 - 이어지는 입력도 자연스럽다`() {
        val c = HangulComposer().apply { fixDwaet = true }
        org.junit.Assert.assertEquals("됐어", type(c, "ㄷㅗㅣㅆㅇㅓ"))
    }

    @org.junit.Test
    fun `기본값에서는 됬 그대로`() {
        org.junit.Assert.assertEquals("됬", type(HangulComposer(), "ㄷㅗㅣㅆ"))
    }
}

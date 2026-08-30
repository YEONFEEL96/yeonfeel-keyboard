package dev.badalab.yeonfeel.hangul

import org.junit.Assert.assertEquals
import org.junit.Test

class TenKeyComposerTest {

    /** 키 간 간격을 크게 두어(멀티탭 아님) 순서대로 입력한다. */
    private fun typeSlow(composer: KoreanComposer, keys: String): String {
        val committed = StringBuilder()
        var composing = ""
        keys.forEachIndexed { index, key ->
            val result = composer.input(key, index * 1000L)
            committed.append(result.commit)
            composing = result.composing
        }
        return committed.toString() + composing
    }

    /** 모든 키를 빠르게(같은 키 연타 판정 안으로) 입력한다. */
    private fun typeFast(composer: KoreanComposer, keys: String): String {
        val committed = StringBuilder()
        var composing = ""
        keys.forEachIndexed { index, key ->
            val result = composer.input(key, index * 100L)
            committed.append(result.commit)
            composing = result.composing
        }
        return committed.toString() + composing
    }

    // --- 천지인 ---

    @Test
    fun `천지인 - 연타 겹받침 보류 병합`() {
        // 않아: 안 + ㅅㅅ(ㅎ) → 다음 자음이 오면 ㄶ으로 병합
        assertEquals("않아", typeFast(ChunjiinComposer(), "ㅇㅣㆍㄴㅅㅅㅇㅣㆍ"))
        // 삶: 살 + ㅇㅇ(ㅁ) → 확정 시 ㄻ 병합
        val c = ChunjiinComposer()
        typeFast(c, "ㅅㅣㆍㄴㄴ")
        listOf('ㅇ', 'ㅇ').forEachIndexed { i, k -> c.input(k, 10_000L + i * 100L) }
        assertEquals("삶", c.flush())
    }

    @Test
    fun `천지인 - 보류 상태에서 모음이 오면 새 글자 초성`() {
        // 살 + ㅇ(보류) + ㅣ → 살이
        assertEquals("살이", typeFast(ChunjiinComposer(), "ㅅㅣㆍㄴㄴㅇㅣ"))
    }

    @Test
    fun `천지인 - 보류 상태 백스페이스는 앞 글자 복원`() {
        val c = ChunjiinComposer()
        typeFast(c, "ㅇㅣㆍㄴㅅ") // 안 + ㅅ(보류)
        val r = c.backspace()
        assertEquals("안", r?.composing)
    }

    @Test
    fun `천지인 - 백스페이스는 완성 모음을 통째로 지운다`() {
        // 요 → ㅇ (ㆍㆍㅡ 토큰이 ㅇㆍㆍ로 분해되지 않아야 한다)
        val c = ChunjiinComposer()
        typeFast(c, "ㅇㆍㆍㅡ")
        assertEquals("ㅇ", c.backspace()?.composing)
        // 가 → ㄱ
        val c2 = ChunjiinComposer()
        typeFast(c2, "ㄱㅣㆍ")
        assertEquals("ㄱ", c2.backspace()?.composing)
        // 받침이 있으면 받침 먼저: 강 → 가
        val c3 = ChunjiinComposer()
        typeFast(c3, "ㄱㅣㆍㅇ")
        assertEquals("가", c3.backspace()?.composing)
        // 아직 모음이 아닌 중간 상태(ㆍㆍ)는 토큰 단위로 지운다
        val c4 = ChunjiinComposer()
        typeFast(c4, "ㅇㆍㆍ")
        assertEquals("ㅇㆍ", c4.backspace()?.composing)
    }

    @Test
    fun `천지인 - 받침 연타로 다음 글자 쌍자음 시작`() {
        // ㅂ 3연타: 압→앞→(아+ㅃ), 이어서 모음이 오면 아빠
        assertEquals("아빠", typeFast(ChunjiinComposer(), "ㅇㅣㆍㅂㅂㅂㅣㆍ"))
        assertEquals("오빠", typeFast(ChunjiinComposer(), "ㅇㆍㅡㅂㅂㅂㅣㆍ"))
    }

    @Test
    fun `천지인 - 자음만 이어 칠 때 연타 사이클 유지`() {
        // ㅅㅅㅅ=ㅆ 확정 후에도 ㄷㄷ=ㅌ, ㅈㅈ=ㅊ 연타가 이어져야 한다 (확정 시 연타 상태 유실 회귀)
        val composer = ChunjiinComposer()
        val committed = StringBuilder()
        var composing = ""
        // 같은 키 연타만 빠르게, 키가 바뀔 때는 시간 간격을 둔다
        val taps = listOf('ㅅ' to 0L, 'ㅅ' to 100L, 'ㅅ' to 200L, 'ㄷ' to 1500L, 'ㄷ' to 1600L, 'ㅈ' to 3000L, 'ㅈ' to 3100L)
        taps.forEach { (key, t) ->
            val result = composer.input(key, t)
            committed.append(result.commit)
            composing = result.composing
        }
        assertEquals("ㅆㅌㅊ", committed.toString() + composing)
    }

    @Test
    fun `천지인 - 귀찮다 ㄴㅎ 겹받침`() {
        // 같은 키 연타만 빠르게, 키가 바뀔 때는 시간 간격을 둔다
        val composer = ChunjiinComposer()
        val committed = StringBuilder()
        var composing = ""
        val taps = listOf(
            'ㄱ' to 0L, 'ㅡ' to 1000L, 'ㆍ' to 2000L, 'ㅣ' to 3000L, // 귀
            'ㅈ' to 4000L, 'ㅈ' to 4100L, // ㅊ
            'ㅣ' to 5000L, 'ㆍ' to 6000L, // ㅏ
            'ㄴ' to 7000L,
            'ㅅ' to 8000L, 'ㅅ' to 8100L, // ㅎ
            'ㄷ' to 9000L, 'ㅣ' to 10_000L, 'ㆍ' to 11_000L, // 다
        )
        taps.forEach { (key, t) ->
            val result = composer.input(key, t)
            committed.append(result.commit)
            composing = result.composing
        }
        assertEquals("귀찮다", committed.toString() + composing)
    }

    @Test
    fun `천지인 - 겹받침 후보 연타는 병합형으로 표시`() {
        // 안 + ㅅㅅ(ㅎ): 내부는 보류지만 화면에는 곧장 '않'으로 보여준다
        val c = ChunjiinComposer()
        var composing = ""
        "ㅇㅣㆍㄴㅅㅅ".forEachIndexed { i, k -> composing = c.input(k, i * 100L).composing }
        assertEquals("않", composing)
    }

    @Test
    fun `천지인 - 병합 표시 상태에서 조합 종료는 병합으로 확정`() {
        // 귀찮 까지 치고 flush(스페이스·엔터 등) → 찬ㅎ이 아니라 찮
        val c = ChunjiinComposer()
        "ㄱㅡㆍㅣㅈㅈㅣㆍㄴㅅㅅ".forEachIndexed { i, k -> c.input(k, i * 100L) }
        assertEquals("찮", c.flush())
    }

    @Test
    fun `천지인 - 병합 표시 후 모음이 오면 갈라짐`() {
        // 않 표시 상태에서 ㅏ → 안하 (떠 있던 ㅎ이 새 글자 초성)
        assertEquals("안하", typeFast(ChunjiinComposer(), "ㅇㅣㆍㄴㅅㅅㅣㆍ"))
    }

    @Test
    fun `천지인 - 기본 모음 조합`() {
        assertEquals("나", typeSlow(ChunjiinComposer(), "ㄴㅣㆍ"))
        assertEquals("너", typeSlow(ChunjiinComposer(), "ㄴㆍㅣ"))
        assertEquals("노", typeSlow(ChunjiinComposer(), "ㄴㆍㅡ"))
        assertEquals("뉴", typeSlow(ChunjiinComposer(), "ㄴㅡㆍㆍ"))
    }

    @Test
    fun `천지인 - 자음 연타 사이클`() {
        assertEquals("카", typeFast(ChunjiinComposer(), "ㄱㄱㅣㆍ"))
        assertEquals("까", typeFast(ChunjiinComposer(), "ㄱㄱㄱㅣㆍ"))
        assertEquals("마", typeFast(ChunjiinComposer(), "ㅇㅇㅣㆍ"))
    }

    @Test
    fun `천지인 - 연타 시간 초과는 별개 자음`() {
        assertEquals("ㄱㄱ", typeSlow(ChunjiinComposer(), "ㄱㄱ"))
    }

    @Test
    fun `천지인 - 음절 완성과 도깨비불`() {
        // 안녕: ㅇ+ㅏ+ㄴ / ㄴ+ㅕ+ㅇ
        assertEquals("안녕", typeSlow(ChunjiinComposer(), "ㅇㅣㆍㄴㄴㆍㆍㅣㅇ"))
        // 간 + ㅏ → 가나 (받침 이동)
        assertEquals("가나", typeSlow(ChunjiinComposer(), "ㄱㅣㆍㄴㅣㆍ"))
    }

    @Test
    fun `천지인 - 복합 모음`() {
        assertEquals("왜", typeSlow(ChunjiinComposer(), "ㅇㆍㅡㅣㆍㅣ"))
        assertEquals("의", typeSlow(ChunjiinComposer(), "ㅇㅡㅣ"))
    }

    // --- 나랏글 ---

    private val stroke = NaratgulComposer.KEY_ADD_STROKE
    private val double = NaratgulComposer.KEY_DOUBLE

    @Test
    fun `나랏글 - 기본 입력`() {
        assertEquals("가", typeSlow(NaratgulComposer(), "ㄱㅏ"))
        assertEquals("모", typeSlow(NaratgulComposer(), "ㅁㅗ"))
    }

    @Test
    fun `나랏글 - 획추가와 쌍자음`() {
        assertEquals("카", typeSlow(NaratgulComposer(), "ㄱ${stroke}ㅏ"))
        assertEquals("타", typeSlow(NaratgulComposer(), "ㄴ${stroke}${stroke}ㅏ"))
        assertEquals("싸", typeSlow(NaratgulComposer(), "ㅅ${double}ㅏ"))
        assertEquals("하", typeSlow(NaratgulComposer(), "ㅇ${stroke}ㅏ"))
    }

    @Test
    fun `나랏글 - 모음 토글과 획추가`() {
        assertEquals("어", typeFast(NaratgulComposer(), "ㅇㅏㅏ"))
        assertEquals("우", typeFast(NaratgulComposer(), "ㅇㅗㅗ"))
        assertEquals("야", typeSlow(NaratgulComposer(), "ㅇㅏ$stroke"))
    }

    @Test
    fun `나랏글 - 모음 결합`() {
        assertEquals("개", typeSlow(NaratgulComposer(), "ㄱㅏㅣ"))
        assertEquals("과", typeSlow(NaratgulComposer(), "ㄱㅗㅏ"))
    }

    @Test
    fun `롱프레스 쌍자음 직접 입력`() {
        // 그룹 밖의 자음(ㄲ 등)이 들어와도 안전하게 조합돼야 한다
        assertEquals("까", typeSlow(ChunjiinComposer(), "ㄲㅣㆍ"))
        assertEquals("까", typeSlow(NaratgulComposer(), "ㄲㅏ"))
    }

    @Test
    fun `나랏글 - 받침과 도깨비불`() {
        assertEquals("간", typeSlow(NaratgulComposer(), "ㄱㅏㄴ"))
        assertEquals("가나", typeSlow(NaratgulComposer(), "ㄱㅏㄴㅏ"))
    }
}

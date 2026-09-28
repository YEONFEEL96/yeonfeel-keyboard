package dev.badalab.yeonfeel.ime

import dev.badalab.yeonfeel.ime.SelectionTracker.Verdict.MOVED
import dev.badalab.yeonfeel.ime.SelectionTracker.Verdict.OWN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionTrackerTest {

    /** "안" 확정 뒤 "녕"을 조합 중 — 조합 영역 [1,2], 커서 2, 앱이 조합 영역을 보고한 상태. */
    private fun composingNyeong(): SelectionTracker {
        val t = SelectionTracker()
        t.start(0, 0)
        t.onSetComposing("아")
        assertEquals(OWN, t.onUpdate(0, 0, 1, 1, 0, 1))
        t.onSetComposing("안")
        t.onSetComposing("안") // 같은 값이면 앱은 콜백을 보내지 않는다
        t.onCommit("안")
        t.onSetComposing("녕")
        assertEquals(OWN, t.onUpdate(1, 1, 2, 2, 1, 2))
        return t
    }

    @Test
    fun `우리 조합 편집의 메아리는 이동이 아니다`() {
        val t = composingNyeong()
        assertEquals("녕", t.composing)
        assertEquals(2, t.expectedSelStart)
        assertFalse(t.compositionDropped)
    }

    @Test
    fun `확정과 조합 사이 중간 상태의 -1은 조합을 끊지 않는다 (카카오톡)`() {
        val t = SelectionTracker()
        t.start(0, 0)
        t.onSetComposing("있")
        assertEquals(OWN, t.onUpdate(0, 0, 1, 1, 0, 1))
        // 있 + ㄷ → "있" 확정, "ㄷ" 조합. 앱이 두 편집 사이 상태를 따로 보고한다.
        t.onCommit("있")
        t.onSetComposing("ㄷ")
        assertEquals(OWN, t.onUpdate(1, 1, 1, 1, -1, -1))
        assertFalse(t.compositionDropped)
        assertEquals(OWN, t.onUpdate(1, 1, 2, 2, 1, 2))
        assertEquals("ㄷ", t.composing)
    }

    @Test
    fun `늦게 도착한 이전 편집의 메아리는 이동이 아니다`() {
        val t = SelectionTracker()
        t.start(5, 5)
        t.onSetComposing("가")
        t.onCommit("가")
        t.onSetComposing("나") // 기대 커서 7
        // "가" 조합 시점의 메아리가 이제서야 도착
        assertEquals(OWN, t.onUpdate(5, 5, 6, 6, 5, 6))
        assertEquals(7, t.expectedSelStart)
        assertEquals(OWN, t.onUpdate(6, 6, 7, 7, 6, 7))
        assertEquals("나", t.composing)
    }

    @Test
    fun `조합 영역 밖으로 커서를 옮기면 이동 (조합 영역을 남기는 앱)`() {
        val t = composingNyeong()
        assertEquals(MOVED, t.onUpdate(2, 2, 0, 0, 1, 2))
        assertEquals("", t.composing)
        assertEquals(0, t.expectedSelStart)
    }

    @Test
    fun `조합 글자 바로 앞으로 커서를 옮겨도 이동`() {
        val t = composingNyeong()
        assertEquals(MOVED, t.onUpdate(2, 2, 1, 1, 1, 2))
    }

    @Test
    fun `앱이 조합을 닫고 -1과 함께 커서를 옮기면 이동 (WebView·Compose)`() {
        val t = composingNyeong()
        assertEquals(MOVED, t.onUpdate(2, 2, 0, 0, -1, -1))
        assertEquals("", t.composing)
    }

    @Test
    fun `조합 중 범위 선택은 이동`() {
        val t = composingNyeong()
        assertEquals(MOVED, t.onUpdate(2, 2, 1, 2, 1, 2))
    }

    @Test
    fun `앱이 제자리에서 조합을 닫으면 소실로 표시한다`() {
        val t = composingNyeong()
        assertEquals(OWN, t.onUpdate(2, 2, 2, 2, -1, -1))
        assertTrue(t.compositionDropped)
    }

    @Test
    fun `일시적 -1 뒤에 조합 영역이 돌아오면 소실 표시를 거둔다`() {
        val t = composingNyeong()
        t.onUpdate(2, 2, 2, 2, -1, -1)
        assertEquals(OWN, t.onUpdate(2, 2, 2, 2, 1, 2))
        assertFalse(t.compositionDropped)
    }

    @Test
    fun `조합 영역을 보고하지 않는 앱에서는 -1을 소실로 보지 않는다`() {
        val t = SelectionTracker()
        t.start(0, 0)
        t.onSetComposing("ㄱ")
        assertEquals(OWN, t.onUpdate(0, 0, 1, 1, -1, -1))
        assertFalse(t.compositionDropped)
    }

    @Test
    fun `키 이벤트 뒤 조합 중에는 우리 조합과 맞는 콜백으로만 다시 맞춘다`() {
        val t = SelectionTracker()
        t.start(5, 5)
        t.onUnpredictableEdit() // DEL
        t.onSetComposing("ㄱ")
        // DEL의 메아리: 조합과 맞지 않으니 판단하지 않는다
        assertEquals(OWN, t.onUpdate(5, 5, 4, 4, -1, -1))
        assertEquals(-1, t.expectedSelStart)
        assertEquals(OWN, t.onUpdate(4, 4, 5, 5, 4, 5))
        assertEquals(5, t.expectedSelStart)
        // 다시 맞춘 뒤의 탭 이동은 잡는다
        assertEquals(MOVED, t.onUpdate(5, 5, 0, 0, 4, 5))
    }

    @Test
    fun `키 이벤트 뒤 조합 중이 아니면 첫 콜백으로 다시 맞춘다`() {
        val t = SelectionTracker()
        t.start(5, 5)
        t.onUnpredictableEdit() // 엔터
        assertEquals(OWN, t.onUpdate(5, 5, 6, 6, -1, -1))
        assertEquals(6, t.expectedSelStart)
        assertEquals(MOVED, t.onUpdate(6, 6, 2, 2, -1, -1))
    }

    @Test
    fun `기준 위치가 틀렸어도 우리 조합 그대로의 콜백이면 이동이 아니다`() {
        val t = SelectionTracker()
        t.start(0, 0) // 실제 커서는 10
        t.onSetComposing("ㄱ")
        assertEquals(OWN, t.onUpdate(0, 0, 11, 11, 10, 11))
        assertEquals(11, t.expectedSelStart)
        assertEquals("ㄱ", t.composing)
    }

    @Test
    fun `확정·조합·삭제에 따라 기대 커서가 움직인다`() {
        val t = SelectionTracker()
        t.start(10, 10)
        t.onDeleteBefore(3)
        assertEquals(7, t.expectedSelStart)
        t.onCommit("abc")
        assertEquals(10, t.expectedSelStart)
        t.onSetComposing("찬ㅎ") // 천지인 보류 표시는 두 글자
        assertEquals(12, t.expectedSelStart)
        t.onSetComposing("찮") // 보류 병합으로 조합 영역이 줄어든다
        assertEquals(11, t.expectedSelStart)
        t.onFinishComposing()
        t.onCommit(" ")
        assertEquals(12, t.expectedSelStart)
        t.onSetComposing("ㄱ")
        t.onCommit("") // 조합 중 마지막 자모를 지움
        assertEquals(12, t.expectedSelStart)
        assertEquals("", t.composing)
    }

    @Test
    fun `선택 영역을 확정 문자열로 바꾼다`() {
        val t = SelectionTracker()
        t.start(3, 8)
        t.onCommit("x")
        assertEquals(4, t.expectedSelStart)
        assertEquals(4, t.expectedSelEnd)
    }

    @Test
    fun `위치를 모르는 입력란은 첫 콜백으로 맞춘다`() {
        val t = SelectionTracker()
        t.start(-1, -1)
        assertEquals(-1, t.expectedSelStart)
        assertEquals(OWN, t.onUpdate(-1, -1, 3, 3, -1, -1))
        assertEquals(3, t.expectedSelStart)
    }

    @Test
    fun `조합을 버리면 커서를 모르는 경우 다시 맞출 때까지 판단을 미룬다`() {
        val t = composingNyeong()
        t.abandonComposition(cursorKnown = false)
        assertEquals("", t.composing)
        assertEquals(-1, t.expectedSelStart)
        t.abandonComposition(cursorKnown = true)
        assertEquals(-1, t.expectedSelStart)
    }
}

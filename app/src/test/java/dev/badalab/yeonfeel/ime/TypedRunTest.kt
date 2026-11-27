package dev.badalab.yeonfeel.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TypedRunTest {

    @Test
    fun `조합은 조합 영역을 바꿔 쓰므로 확정된 글자만 쌓인다`() {
        val run = TypedRun()
        run.onSetComposing("ㄹ")
        run.onSetComposing("레")
        run.onCommit("레")
        run.onSetComposing("ㅍ")
        run.onSetComposing("포")
        assertEquals(2, run.length)
        run.onFinishComposing()
        assertEquals(2, run.length)
    }

    @Test
    fun `보류 병합처럼 조합이 짧아지면 줄어든다`() {
        val run = TypedRun()
        run.onSetComposing("찬ㅎ")
        run.onSetComposing("찮")
        run.onFinishComposing()
        assertEquals(1, run.length)
    }

    @Test
    fun `이어 친 범위를 넘어 지우면 0에서 멈추고 새 글자부터 다시 센다`() {
        val run = TypedRun()
        run.onCommit("포")
        run.onDeleteBefore(1)
        run.onDeleteBefore(1) // 커서를 옮겨 둔 기존 글자 '레'를 지움
        assertEquals(0, run.length)
        run.onCommit("포")
        assertEquals(1, run.length)
    }

    @Test
    fun `reset은 남길 길이를 받는다`() {
        val run = TypedRun()
        run.onSetComposing("가")
        run.reset(keep = 3)
        run.onCommit("나")
        assertEquals(4, run.length)
    }
}

class CorrectionTest {

    private val made = Correction(original = "레포", fixed = "체포")

    @Test
    fun `교정 직후에는 교정 어절과 공백만 바꾼다`() {
        assertEquals(3, made.spanLength(0))
        assertEquals("레포 ", made.revertText("체포 ", 0))
    }

    @Test
    fun `다음 어절을 치고 있어도 꼬리를 살려 되돌린다`() {
        assertEquals("레포 주소", made.revertText("체포 주소", 2))
    }

    @Test
    fun `그 사이 글이 달라졌으면 되돌리지 않는다`() {
        assertNull(made.revertText("체표 주소", 2))
        assertNull(made.revertText("포 주소", 2))
        assertNull(made.revertText(null, 0))
    }
}

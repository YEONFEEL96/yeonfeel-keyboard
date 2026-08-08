package dev.badalab.yeonfeel.clipboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardHistoryTest {

    @Test
    fun `민감 클립은 수집하지 않는다`() {
        val history = ClipboardHistory()
        assertFalse(history.add("hunter2", isSensitive = true, now = 0))
        assertTrue(history.entries(0).isEmpty())
    }

    @Test
    fun `빈 텍스트와 초과 길이 텍스트는 걸러진다`() {
        val history = ClipboardHistory()
        assertFalse(history.add("   ", isSensitive = false, now = 0))
        assertFalse(history.add("a".repeat(ClipboardHistory.MAX_TEXT_LENGTH + 1), isSensitive = false, now = 0))
        assertTrue(history.entries(0).isEmpty())
    }

    @Test
    fun `최신 항목이 앞으로 오고 중복은 하나로 합쳐진다`() {
        val history = ClipboardHistory()
        history.add("첫째", false, 1)
        history.add("둘째", false, 2)
        history.add("첫째", false, 3)

        assertEquals(listOf("첫째", "둘째"), history.entries(3).map { it.text })
    }

    @Test
    fun `최대 개수를 넘으면 오래된 항목부터 버린다`() {
        val history = ClipboardHistory(maxItems = 3)
        (1..5).forEach { history.add("항목$it", false, it.toLong()) }

        assertEquals(listOf("항목5", "항목4", "항목3"), history.entries(5).map { it.text })
    }

    @Test
    fun `TTL이 지난 항목은 자동 만료된다`() {
        val history = ClipboardHistory(ttlMillis = 1000)
        history.add("옛날", false, 0)
        history.add("최근", false, 900)

        assertEquals(listOf("최근"), history.entries(1500).map { it.text })
    }

    @Test
    fun `개별 삭제와 전체 삭제`() {
        val history = ClipboardHistory()
        history.add("하나", false, 0)
        history.add("둘", false, 0)

        history.remove("하나")
        assertEquals(listOf("둘"), history.entries(0).map { it.text })

        history.clear()
        assertTrue(history.entries(0).isEmpty())
    }
}

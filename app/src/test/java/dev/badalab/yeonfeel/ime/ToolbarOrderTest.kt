package dev.badalab.yeonfeel.ime

import org.junit.Assert.assertEquals
import org.junit.Test

class ToolbarOrderTest {

    @Test
    fun `parse는 공백과 빈 항목을 버린다`() {
        assertEquals(listOf("settings", "emoji"), ToolbarOrder.parse(" settings, ,emoji,"))
    }

    @Test
    fun `숨긴 항목은 이전 자리로 돌아간다`() {
        val previous = listOf("settings", "emoji", "translate", "onehand")
        // 번역 버튼을 숨긴 채 onehand를 맨 앞으로 옮겼다.
        val visible = listOf("onehand", "settings", "emoji")
        assertEquals(
            listOf("onehand", "settings", "translate", "emoji"),
            ToolbarOrder.keepHidden(visible, previous, listOf("translate")),
        )
    }

    @Test
    fun `이전 순서에 없던 숨긴 항목은 넣지 않는다`() {
        val previous = listOf("settings", "emoji")
        assertEquals(
            listOf("emoji", "settings"),
            ToolbarOrder.keepHidden(listOf("emoji", "settings"), previous, listOf("translate")),
        )
    }

    @Test
    fun `숨긴 항목이 맨 끝이었으면 끝에 남는다`() {
        val previous = listOf("settings", "emoji", "translate")
        assertEquals(
            listOf("emoji", "settings", "translate"),
            ToolbarOrder.keepHidden(listOf("emoji", "settings"), previous, listOf("translate")),
        )
    }

    @Test
    fun `여러 항목이 숨겨져 있어도 각자 이전 자리로 돌아간다`() {
        val previous = listOf("a", "translate", "b", "onehand", "c")
        val visible = listOf("c", "a", "b")
        assertEquals(
            listOf("c", "translate", "a", "onehand", "b"),
            ToolbarOrder.keepHidden(visible, previous, listOf("translate", "onehand")),
        )
    }
}

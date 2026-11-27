package dev.badalab.yeonfeel.translate

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiBlockFlagTest {

    @Test
    fun `같은 AICore 버전에서 기록했으면 막힌 것이다`() {
        assertTrue(GeminiBlockFlag.isBlocked(recordedVersion = 1234L, currentVersion = 1234L))
    }

    @Test
    fun `기록이 없으면 막히지 않았다`() {
        assertFalse(GeminiBlockFlag.isBlocked(recordedVersion = -1L, currentVersion = 1234L))
    }

    @Test
    fun `AICore가 업데이트되면 기록이 무효가 된다`() {
        assertFalse(GeminiBlockFlag.isBlocked(recordedVersion = 1234L, currentVersion = 1300L))
    }

    @Test
    fun `AICore를 알 수 없으면 막히지 않은 것으로 본다`() {
        assertFalse(GeminiBlockFlag.isBlocked(recordedVersion = 1234L, currentVersion = -1L))
        assertFalse(GeminiBlockFlag.isBlocked(recordedVersion = -1L, currentVersion = -1L))
    }
}

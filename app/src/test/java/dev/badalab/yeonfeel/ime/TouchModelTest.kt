package dev.badalab.yeonfeel.ime

import dev.badalab.yeonfeel.debug.TouchStatsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchModelTest {

    private fun samples(key: String, rx: Float, ry: Float, n: Int) =
        List(n) { TouchStatsStore.Sample("KO_DUBEOLSIK", key, 0.5f, 0.5f, rx, ry) }

    @Test
    fun `표본 부족이면 기본 분포`() {
        val stat = TouchModel.fitSamples(samples("ㅔ", 0.2f, 0f, 5))
        val p = TouchModel.effective(stat)
        assertEquals(0f, p[0], 1e-6f)
        assertEquals(0f, p[1], 1e-6f)
    }

    @Test
    fun `표본이 쌓이면 평균이 사용자 쪽으로 이동하되 상한을 넘지 않는다`() {
        val stat = TouchModel.fitSamples(samples("ㅔ", 0.6f, 0f, 100))
        val p = TouchModel.effective(stat)
        assertTrue(p[0] > 0.1f)
        assertTrue(p[0] <= 0.25f)
    }

    @Test
    fun `이상치는 평균 추정에서 걸러진다`() {
        val base = samples("ㅔ", 0.05f, 0f, 60)
        val outliers = samples("ㅔ", 0.9f, 0.9f, 3)
        val stat = TouchModel.fitSamples(base + outliers)
        assertTrue(kotlin.math.abs(stat.muX - 0.05f) < 0.05f)
    }

    @Test
    fun `치우친 타점은 경계 지점에서 개인화된 키가 더 그럴듯하다`() {
        // 사용자가 ㅔ를 왼쪽으로 약간 흩어지게 치는 경향 (평균 -0.2, 자연스러운 산포)
        val biased = TouchModel.fitSamples(
            List(80) {
                TouchStatsStore.Sample("b", "ㅔ", 0f, 0f, -0.2f + (it % 5 - 2) * 0.03f, 0f)
            },
        )
        val neutral: TouchModel.KeyStat? = null
        // 키 왼쪽 경계 근처(-0.35): 개인화 분포가 기본 분포보다 그럴듯해야 한다
        val scoreBiased = TouchModel.logLikelihood(-0.35f, 0f, biased)
        val scoreNeutral = TouchModel.logLikelihood(-0.35f, 0f, neutral)
        assertTrue(scoreBiased > scoreNeutral)
    }
}

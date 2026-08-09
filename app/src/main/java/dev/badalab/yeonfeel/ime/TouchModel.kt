package dev.badalab.yeonfeel.ime

import dev.badalab.yeonfeel.debug.TouchStatsStore
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * 키별 타점 분포(가우시안) 기반 탭 보정 모델.
 * 표본이 적으면 기본 분포로 수렴해 기존 기하학 판정과 같아지고,
 * 개인화 이동량은 키 폭의 [MU_CLAMP]까지로 제한한다.
 */
class TouchModel(private val store: TouchStatsStore) {

    data class KeyStat(
        val n: Int,
        val muX: Float,
        val muY: Float,
        val sigmaX: Float,
        val sigmaY: Float,
    )

    private val cache = HashMap<String, Map<String, KeyStat>>()

    @Synchronized
    fun invalidate() = cache.clear()

    @Synchronized
    fun statsFor(board: String): Map<String, KeyStat> = cache.getOrPut(board) {
        store.forBoard(board).groupBy { it.key }.mapValues { (_, samples) -> fitSamples(samples) }
    }

    companion object {
        const val MIN_SAMPLES = 20
        private const val PRIOR_WEIGHT = 30f
        private const val SIGMA_DEFAULT = 0.18f
        private const val SIGMA_MIN = 0.08f
        private const val SIGMA_MAX = 0.35f
        private const val MU_CLAMP = 0.25f
        private const val OUTLIER_SIGMA = 2.5f

        fun fitSamples(samples: List<TouchStatsStore.Sample>): KeyStat {
            val first = estimate(samples)
            // 오타 탭이 분포를 오염시키지 않도록 이상치를 버리고 1회 재추정한다.
            val trimmed = samples.filter {
                abs(it.rx - first.muX) <= OUTLIER_SIGMA * first.sigmaX &&
                    abs(it.ry - first.muY) <= OUTLIER_SIGMA * first.sigmaY
            }
            return if (trimmed.size in 1 until samples.size) estimate(trimmed) else first
        }

        private fun estimate(samples: List<TouchStatsStore.Sample>): KeyStat {
            val n = samples.size
            if (n == 0) return KeyStat(0, 0f, 0f, SIGMA_DEFAULT, SIGMA_DEFAULT)
            val muX = samples.map { it.rx }.sum() / n
            val muY = samples.map { it.ry }.sum() / n
            val varX = samples.map { (it.rx - muX) * (it.rx - muX) }.sum() / n
            val varY = samples.map { (it.ry - muY) * (it.ry - muY) }.sum() / n
            return KeyStat(n, muX, muY, sqrt(varX), sqrt(varY))
        }

        /** 표본 수에 따라 기본 분포와 섞은 유효 파라미터: [mx, my, sx, sy]. */
        fun effective(stat: KeyStat?): FloatArray {
            if (stat == null || stat.n < MIN_SAMPLES) {
                return floatArrayOf(0f, 0f, SIGMA_DEFAULT, SIGMA_DEFAULT)
            }
            val w = stat.n / (stat.n + PRIOR_WEIGHT)
            val mx = (stat.muX * w).coerceIn(-MU_CLAMP, MU_CLAMP)
            val my = (stat.muY * w).coerceIn(-MU_CLAMP, MU_CLAMP)
            fun blendSigma(userSigma: Float): Float {
                val variance = (PRIOR_WEIGHT * SIGMA_DEFAULT * SIGMA_DEFAULT +
                    stat.n * userSigma * userSigma) / (PRIOR_WEIGHT + stat.n)
                return sqrt(variance).coerceIn(SIGMA_MIN, SIGMA_MAX)
            }
            return floatArrayOf(mx, my, blendSigma(stat.sigmaX), blendSigma(stat.sigmaY))
        }

        /** 키 크기 단위 상대좌표(rx, ry)에 대한 로그 가능도. */
        fun logLikelihood(rx: Float, ry: Float, stat: KeyStat?): Float {
            val p = effective(stat)
            fun term(v: Float, mu: Float, sigma: Float): Float {
                val d = (v - mu) / sigma
                return -(ln(sigma) + 0.5f * d * d)
            }
            return term(rx, p[0], p[2]) + term(ry, p[1], p[3])
        }
    }
}

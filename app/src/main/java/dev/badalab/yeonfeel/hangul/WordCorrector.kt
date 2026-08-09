package dev.badalab.yeonfeel.hangul

import java.io.InputStream
import kotlin.math.abs
import kotlin.math.ln

/**
 * 노이지 채널 어절 교정기: P(단어|입력) ∝ P(입력|단어) × P(단어).
 * P(입력|단어)는 두벌식 키 인접도를 반영한 자모 편집거리,
 * P(단어)는 빈도 사전(assets/ko_freq.txt)의 로그 빈도.
 */
class WordCorrector {

    private class Entry(val word: String, val keys: String, val logFreq: Float)

    /** 생성 스크립트(scripts/generate_lexicon.py)와 같은 FNV-1a 이중 해시 블룸 필터. */
    class BloomFilter(bytes: ByteArray) {
        private val hashes = ((bytes[4].toInt() and 0xFF) shl 24) or
            ((bytes[5].toInt() and 0xFF) shl 16) or
            ((bytes[6].toInt() and 0xFF) shl 8) or
            (bytes[7].toInt() and 0xFF)
        private val bitCount: Long = (8..15).fold(0L) { acc, i ->
            (acc shl 8) or (bytes[i].toLong() and 0xFF)
        }
        private val bits = bytes.copyOfRange(16, bytes.size)

        fun contains(word: String): Boolean {
            val data = word.toByteArray(Charsets.UTF_8)
            val h1 = fnv64(data, FNV_OFFSET)
            val h2 = fnv64(data, FNV_SEED2) or 1L
            for (i in 0 until hashes) {
                val idx = java.lang.Long.remainderUnsigned(h1 + i * h2, bitCount)
                val byteIndex = (idx ushr 3).toInt()
                val bit = 1 shl (idx and 7L).toInt()
                if (bits[byteIndex].toInt() and bit == 0) return false
            }
            return true
        }

        private fun fnv64(data: ByteArray, seed: Long): Long {
            var h = seed
            data.forEach { b ->
                h = h xor (b.toLong() and 0xFF)
                h *= FNV_PRIME
            }
            return h
        }

        companion object {
            private const val FNV_OFFSET = -0x340D631B7BDDDCDBL // 0xCBF29CE484222325
            private const val FNV_PRIME = 0x100000001B3L
            private const val FNV_SEED2 = -0x61C8864680B583EBL // 0x9E3779B97F4A7C15
        }
    }


    @Volatile
    private var loaded: Pair<Map<String, Float>, Map<Int, List<Entry>>>? = null

    @Volatile
    private var knownBloom: BloomFilter? = null

    /** 알려진 어절 보호용 블룸 필터 (오탐은 '교정 안 함' 방향이라 안전). */
    fun loadKnown(bytes: ByteArray) {
        knownBloom = BloomFilter(bytes)
    }

    /** "단어 빈도" 줄 형식의 교정 후보 사전을 읽는다. 백그라운드 스레드에서 호출할 것. */
    fun load(stream: InputStream) {
        val words = HashMap<String, Float>()
        val buckets = HashMap<Int, MutableList<Entry>>()
        stream.bufferedReader().forEachLine { line ->
            val space = line.indexOf(' ')
            if (space <= 0) return@forEachLine
            val word = line.substring(0, space)
            val count = line.substring(space + 1).toLongOrNull() ?: return@forEachLine
            val keys = decompose(word) ?: return@forEachLine
            val logFreq = ln(count.toFloat())
            words[word] = logFreq
            buckets.getOrPut(keys.length) { mutableListOf() }.add(Entry(word, keys, logFreq))
        }
        loaded = words to buckets
    }

    val isReady: Boolean get() = loaded != null

    /**
     * 사전에 없는 어절이면 가장 그럴듯한 교정 후보를 돌려준다.
     * 편집 비용이 [ACCEPT_COST]를 넘거나 사전에 이미 있으면 null.
     */
    fun correct(word: String): String? {
        val (words, buckets) = loaded ?: return null
        if (word.length !in 2..10 || !word.all { it in '가'..'힣' }) return null
        if (words.containsKey(word)) return null
        // 말뭉치에 한 번이라도 등장한 어절은 실제 단어로 보고 교정하지 않는다.
        if (knownBloom?.contains(word) == true) return null
        val keys = decompose(word) ?: return null

        var bestWord: String? = null
        var bestScore = Float.MAX_VALUE
        var bestDist = Float.MAX_VALUE
        for (len in keys.length - 1..keys.length + 1) {
            val bucket = buckets[len] ?: continue
            for (entry in bucket) {
                val dist = editDistance(keys, entry.keys, MAX_SEARCH_COST) ?: continue
                val score = dist - FREQ_WEIGHT * entry.logFreq
                if (score < bestScore) {
                    bestScore = score
                    bestDist = dist
                    bestWord = entry.word
                }
            }
        }
        return if (bestDist <= ACCEPT_COST) bestWord else null
    }

    companion object {
        private const val FREQ_WEIGHT = 0.12f
        private const val ACCEPT_COST = 1.25f
        private const val MAX_SEARCH_COST = 2.4f
        private const val COST_ADJACENT = 0.55f
        private const val COST_SHIFT_PAIR = 0.35f
        private const val COST_SUBSTITUTE = 1.3f
        private const val COST_INSERT_DELETE = 1.0f

        private val REVERSE_VOWEL = HangulTables.VOWEL_COMBINE.entries.associate { it.value to it.key }
        private val REVERSE_JONG = HangulTables.JONG_COMBINE.entries.associate { it.value to it.key }

        /** Shift 짝 (같은 키 자리): 편집 비용 계산에서 같은 위치로 취급한다. */
        private val SHIFT_BASE = mapOf(
            'ㅃ' to 'ㅂ', 'ㅉ' to 'ㅈ', 'ㄸ' to 'ㄷ', 'ㄲ' to 'ㄱ', 'ㅆ' to 'ㅅ',
            'ㅒ' to 'ㅐ', 'ㅖ' to 'ㅔ',
        )

        /** 두벌식 배열 좌표 (행 오프셋 포함) — 키 인접도 판정용. */
        private val KEY_POS: Map<Char, Pair<Float, Float>> = buildMap {
            "ㅂㅈㄷㄱㅅㅛㅕㅑㅐㅔ".forEachIndexed { i, c -> put(c, i.toFloat() to 0f) }
            "ㅁㄴㅇㄹㅎㅗㅓㅏㅣ".forEachIndexed { i, c -> put(c, i + 0.5f to 1f) }
            "ㅋㅌㅊㅍㅠㅜㅡ".forEachIndexed { i, c -> put(c, i + 1.5f to 2f) }
        }

        /** 어절 → 두벌식 키 입력열. 한글 음절이 아니면 null. */
        fun decompose(word: String): String? = buildString {
            word.forEach { ch ->
                if (ch !in '가'..'힣') return null
                val s = ch - '가'
                append(HangulTables.CHO_LIST[s / 588])
                val jung = HangulTables.JUNG_LIST[(s % 588) / 28]
                REVERSE_VOWEL[jung]?.let { (a, b) ->
                    append(a)
                    append(b)
                } ?: append(jung)
                val jongIndex = s % 28
                if (jongIndex > 0) {
                    val jong = HangulTables.JONG_LIST[jongIndex - 1]
                    REVERSE_JONG[jong]?.let { (a, b) ->
                        append(a)
                        append(b)
                    } ?: append(jong)
                }
            }
        }

        private fun substituteCost(a: Char, b: Char): Float {
            if (a == b) return 0f
            val baseA = SHIFT_BASE[a] ?: a
            val baseB = SHIFT_BASE[b] ?: b
            if (baseA == baseB) return COST_SHIFT_PAIR
            val pa = KEY_POS[baseA] ?: return COST_SUBSTITUTE
            val pb = KEY_POS[baseB] ?: return COST_SUBSTITUTE
            val dx = pa.first - pb.first
            val dy = pa.second - pb.second
            return if (dx * dx + dy * dy <= 1.3f) COST_ADJACENT else COST_SUBSTITUTE
        }

        // 후보마다 배열을 새로 만들지 않도록 재사용한다 (단일 스레드 호출 전제).
        private var dpPrev = FloatArray(64)
        private var dpCurr = FloatArray(64)

        /** 비용 상한 초과 시 조기 종료하는 편집거리. */
        fun editDistance(a: String, b: String, maxCost: Float): Float? {
            if (abs(a.length - b.length) * COST_INSERT_DELETE > maxCost) return null
            if (dpPrev.size <= b.length) {
                dpPrev = FloatArray(b.length + 1)
                dpCurr = FloatArray(b.length + 1)
            }
            var prev = dpPrev
            var curr = dpCurr
            for (j in 0..b.length) prev[j] = j * COST_INSERT_DELETE
            for (i in 1..a.length) {
                curr[0] = i * COST_INSERT_DELETE
                var rowMin = curr[0]
                for (j in 1..b.length) {
                    val sub = prev[j - 1] + substituteCost(a[i - 1], b[j - 1])
                    val del = prev[j] + COST_INSERT_DELETE
                    val ins = curr[j - 1] + COST_INSERT_DELETE
                    curr[j] = minOf(sub, del, ins)
                    if (curr[j] < rowMin) rowMin = curr[j]
                }
                if (rowMin > maxCost) return null
                val tmp = prev
                prev = curr
                curr = tmp
            }
            return if (prev[b.length] <= maxCost) prev[b.length] else null
        }
    }
}

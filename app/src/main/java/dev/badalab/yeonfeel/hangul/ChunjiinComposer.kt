package dev.badalab.yeonfeel.hangul

import dev.badalab.yeonfeel.hangul.HangulTables.JONG_COMBINE
import dev.badalab.yeonfeel.hangul.HangulTables.canBeJong

/**
 * 천지인 조합 오토마타 (2011년 국가표준·특허 개방 방식).
 *
 * - 모음은 천(ㆍ)·지(ㅡ)·인(ㅣ) 세 키의 순서 조합으로 만든다.
 * - 자음 키는 같은 키 연타로 사이클한다 (ㄱ→ㅋ→ㄲ). 연타 판정은
 *   [multiTapTimeoutMs] 안에 같은 키를 다시 눌렀는지로 한다.
 * - 받침 뒤 모음 입력 시 도깨비불(받침 이동)은 두벌식과 동일하다.
 */
class ChunjiinComposer(
    private val multiTapTimeoutMs: Long = 600L,
) : KoreanComposer {

    private var cho: Char? = null
    private var jungTokens: String = "" // ㅣㆍㅡ 토큰 열
    private var jong: String = "" // 호환 자모 1~2타

    private var lastKey: Char? = null
    private var lastTime: Long = 0
    private var lastInputWasActiveConsonant = false

    override val isComposing: Boolean
        get() = cho != null || jungTokens.isNotEmpty()

    override fun input(jamo: Char, now: Long): HangulComposer.Result {
        val cycling = jamo == lastKey && now - lastTime < multiTapTimeoutMs
        val result = if (jamo in VOWEL_TOKENS) {
            lastInputWasActiveConsonant = false
            inputVowelToken(jamo)
        } else {
            inputConsonantKey(jamo, cycling && lastInputWasActiveConsonant)
        }
        lastKey = jamo
        lastTime = now
        return result
    }

    private fun inputConsonantKey(key: Char, cycle: Boolean): HangulComposer.Result {
        val group = GROUPS.getValue(key)
        if (cycle) {
            // 같은 키 연타: 마지막 자음을 그룹 안에서 다음 후보로 교체
            if (jungTokens.isEmpty() && jong.isEmpty() && cho != null) {
                cho = nextInGroup(group, cho!!) { true }
                lastInputWasActiveConsonant = true
                return HangulComposer.Result("", composed())
            }
            if (jong.isNotEmpty()) {
                val replaced = nextInGroup(group, jong.last()) { candidate ->
                    if (jong.length == 1) canBeJong(candidate)
                    else JONG_COMBINE.containsKey(jong[0] to candidate)
                }
                jong = jong.dropLast(1) + replaced
                lastInputWasActiveConsonant = true
                return HangulComposer.Result("", composed())
            }
        }
        // 새 자음 입력 (두벌식과 같은 규칙)
        val c = group[0]
        lastInputWasActiveConsonant = true
        return when {
            cho == null && jungTokens.isEmpty() -> {
                cho = c
                HangulComposer.Result("", composed())
            }
            jungTokens.isEmpty() || cho == null -> {
                val committed = composed()
                reset()
                cho = c
                HangulComposer.Result(committed, composed())
            }
            jong.isEmpty() && currentVowel() != null -> {
                if (canBeJong(c)) {
                    jong = c.toString()
                    HangulComposer.Result("", composed())
                } else {
                    val committed = composed()
                    reset()
                    cho = c
                    HangulComposer.Result(committed, composed())
                }
            }
            jong.length == 1 && JONG_COMBINE.containsKey(jong[0] to c) -> {
                jong += c
                HangulComposer.Result("", composed())
            }
            else -> {
                val committed = composed()
                reset()
                cho = c
                HangulComposer.Result(committed, composed())
            }
        }
    }

    private fun inputVowelToken(token: Char): HangulComposer.Result {
        // 받침 있는 상태에서 모음 시작 → 도깨비불
        if (jong.isNotEmpty()) {
            val moving = jong.last()
            jong = jong.dropLast(1)
            val committed = composed()
            reset()
            cho = moving
            jungTokens = token.toString()
            return HangulComposer.Result(committed, composed())
        }
        if (jungTokens.isEmpty()) {
            jungTokens = token.toString()
            return HangulComposer.Result("", composed())
        }
        val candidate = jungTokens + token
        return when {
            // ㆍ 연타 사이클: ㆍ→ᆢ→ㆍ
            jungTokens == "ㆍㆍ" && token == 'ㆍ' -> {
                jungTokens = "ㆍ"
                HangulComposer.Result("", composed())
            }
            VOWEL_MAP.containsKey(candidate) || candidate in INTERMEDIATES -> {
                jungTokens = candidate
                HangulComposer.Result("", composed())
            }
            else -> {
                val committed = composed()
                reset()
                jungTokens = token.toString()
                HangulComposer.Result(committed, composed())
            }
        }
    }

    override fun backspace(): HangulComposer.Result? {
        when {
            jong.isNotEmpty() -> jong = jong.dropLast(1)
            jungTokens.isNotEmpty() -> jungTokens = jungTokens.dropLast(1)
            cho != null -> cho = null
            else -> return null
        }
        return HangulComposer.Result("", composed())
    }

    override fun flush(): String {
        val committed = composed()
        reset()
        return committed
    }

    override fun reset() {
        cho = null
        jungTokens = ""
        jong = ""
        lastKey = null
        lastInputWasActiveConsonant = false
    }

    private fun currentVowel(): Char? = VOWEL_MAP[jungTokens]

    private fun composed(): String {
        val c = cho
        if (c == null && jungTokens.isEmpty()) return ""
        if (jungTokens.isEmpty()) return c.toString()
        val vowel = currentVowel()
            ?: return (c?.toString() ?: "") + jungTokens // ㆍ 중간 상태는 그대로 보여준다
        if (c == null) return vowel.toString()
        return HangulTables.syllable(c, vowel, jongChar()).toString()
    }

    private fun jongChar(): Char? = when (jong.length) {
        0 -> null
        1 -> jong[0]
        else -> JONG_COMBINE.getValue(jong[0] to jong[1])
    }

    private fun nextInGroup(group: String, current: Char, valid: (Char) -> Boolean): Char {
        var index = group.indexOf(current)
        if (index < 0) return current
        repeat(group.length) {
            index = (index + 1) % group.length
            if (valid(group[index])) return group[index]
        }
        return current
    }

    companion object {
        const val KEY_I = 'ㅣ'
        const val KEY_ARAEA = 'ㆍ'
        const val KEY_EU = 'ㅡ'

        private const val VOWEL_TOKENS = "ㅣㆍㅡ"

        /** 자음 키 그룹: 연타 사이클 순서. 키 대표 문자 → 사이클. */
        private val GROUPS = mapOf(
            'ㄱ' to "ㄱㅋㄲ",
            'ㄴ' to "ㄴㄹ",
            'ㄷ' to "ㄷㅌㄸ",
            'ㅂ' to "ㅂㅍㅃ",
            'ㅅ' to "ㅅㅎㅆ",
            'ㅈ' to "ㅈㅊㅉ",
            'ㅇ' to "ㅇㅁ",
        )

        /** 천지인 토큰 열 → 완성 모음. */
        private val VOWEL_MAP = mapOf(
            "ㅣ" to 'ㅣ',
            "ㅡ" to 'ㅡ',
            "ㅣㆍ" to 'ㅏ',
            "ㅣㆍㆍ" to 'ㅑ',
            "ㆍㅣ" to 'ㅓ',
            "ㆍㆍㅣ" to 'ㅕ',
            "ㆍㅡ" to 'ㅗ',
            "ㆍㆍㅡ" to 'ㅛ',
            "ㅡㆍ" to 'ㅜ',
            "ㅡㆍㆍ" to 'ㅠ',
            "ㅡㅣ" to 'ㅢ',
            "ㅣㆍㅣ" to 'ㅐ',
            "ㅣㆍㆍㅣ" to 'ㅒ',
            "ㆍㅣㅣ" to 'ㅔ',
            "ㆍㆍㅣㅣ" to 'ㅖ',
            "ㆍㅡㅣ" to 'ㅚ',
            "ㆍㅡㅣㆍ" to 'ㅘ',
            "ㆍㅡㅣㆍㅣ" to 'ㅙ',
            "ㅡㆍㅣ" to 'ㅟ',
            "ㅡㆍㆍㅣ" to 'ㅝ',
            "ㅡㆍㆍㅣㅣ" to 'ㅞ',
        )

        /** 아직 모음이 아니지만 이어질 수 있는 중간 상태. */
        private val INTERMEDIATES = setOf("ㆍ", "ㆍㆍ")

        fun isChunjiinKey(ch: Char): Boolean =
            ch == KEY_ARAEA || ch == KEY_I || ch == KEY_EU || GROUPS.containsKey(ch)
    }
}

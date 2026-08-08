package dev.badalab.yeonfeel.hangul

import dev.badalab.yeonfeel.hangul.HangulTables.JONG_COMBINE
import dev.badalab.yeonfeel.hangul.HangulTables.JUNG_LIST
import dev.badalab.yeonfeel.hangul.HangulTables.VOWEL_COMBINE
import dev.badalab.yeonfeel.hangul.HangulTables.canBeJong

/**
 * 두벌식 한글 조합 오토마타. 단모음 자판(모음 연타)과
 * 세벌식(역할 명시 자모 블록 입력)도 이 클래스가 처리한다.
 *
 * 호환 자모(U+3131~U+3163) 단위로 입력을 받아 완성형 음절(U+AC00~)을 조합한다.
 * 조합 중인 글자(composing)와 확정된 글자(commit)를 분리해 돌려주므로,
 * IME 쪽에서는 InputConnection의 composing region으로 그대로 매핑하면 된다.
 */
class HangulComposer : KoreanComposer {

    /** 한 번의 입력이 만들어 낸 결과. [commit]은 확정 문자열, [composing]은 조합 중 문자열. */
    data class Result(val commit: String, val composing: String)

    private var cho: Char? = null // 초성 (호환 자모)
    private var jung: String = "" // 중성 키 입력 1~2타
    private var jong: String = "" // 종성 키 입력 1~2타

    /** 단모음 자판용: 같은 모음 연타로 이중모음을 만든다 (ㅏㅏ→ㅑ). */
    var doubleTapIotation: Boolean = false

    /**
     * 단모음 자판용: 같은 자음을 [multiTapTimeoutMs] 안에 연타하면 쌍자음으로 토글한다
     * (ㄱㄱ→ㄲ). 판정 시간이 짧을수록 '학교→하꾜'류 오조합이 줄어든다.
     */
    var doubleTapDoubling: Boolean = false
    var multiTapTimeoutMs: Long = 300L

    private var lastKey: Char? = null
    private var lastKeyTime: Long = 0L

    override val isComposing: Boolean
        get() = cho != null || jung.isNotEmpty()

    override fun input(jamo: Char, now: Long): Result {
        val result =
            if (doubleTapDoubling && jamo == lastKey && now - lastKeyTime < multiTapTimeoutMs) {
                tryToggleDouble(jamo) ?: input(jamo)
            } else {
                input(jamo)
            }
        lastKey = jamo
        lastKeyTime = now
        return result
    }

    /**
     * 연타된 자음을 제자리에서 쌍자음으로 승급한다 (ㄷㄷ→ㄸ).
     * 이미 쌍자음이면 null을 돌려 세 번째 연타가 새 자음으로 시작되게 한다 (ㄷㄷㄷ→ㄸㄷ).
     * 받침 자리에서 승급이 불가능하면(ㄵ의 ㅈ→ㅉ 등) 그 자음을 받침에서 빼내
     * 새 글자의 쌍자음 초성으로 만든다 (짅+ㅈ→진ㅉ → "진짜" 입력).
     */
    private fun tryToggleDouble(base: Char): Result? {
        val doubled = DOUBLE_CONSONANT[base] ?: return null
        if (jung.isEmpty() && jong.isEmpty() && cho == base) {
            cho = doubled
            return Result("", composed())
        }
        if (jong.isNotEmpty() && jong.last() == base) {
            val canUpgradeInPlace =
                if (jong.length == 1) canBeJong(doubled)
                else JONG_COMBINE.containsKey(jong[0] to doubled)
            if (canUpgradeInPlace) {
                jong = jong.dropLast(1) + doubled
                return Result("", composed())
            }
            // 받침에서 승급 불가 → 마지막 받침을 빼내 새 글자 초성 쌍자음으로
            jong = jong.dropLast(1)
            val committed = composed()
            reset()
            cho = doubled
            return Result(committed, composed())
        }
        return null
    }

    /**
     * 호환 자모(ㄱ~ㅣ)는 두벌식 규칙(도깨비불 포함)으로,
     * 옛한글 자모 블록(초성 U+1100~/중성 U+1161~/종성 U+11A8~)은
     * 세벌식 규칙(역할 명시, 도깨비불 없음)으로 처리한다.
     */
    fun input(jamo: Char): Result = when (jamo) {
        in 'ᄀ'..'ᄒ' -> inputChoExplicit(HangulTables.CHO_LIST[jamo - 'ᄀ'])
        in 'ᅡ'..'ᅵ' -> inputJungExplicit(JUNG_LIST[jamo - 'ᅡ'])
        in 'ᆨ'..'ᇂ' -> inputJongExplicit(HangulTables.JONG_LIST[jamo - 'ᆨ'])
        else -> if (HangulTables.isVowel(jamo)) inputVowel(jamo) else inputConsonant(jamo)
    }

    /** 세벌식 초성: 조합 중인 글자가 있으면 확정하고 새 글자를 시작한다. */
    private fun inputChoExplicit(c: Char): Result {
        val committed = if (isComposing) composed() else ""
        reset()
        cho = c
        return Result(committed, composed())
    }

    /** 세벌식 중성: 받침 뒤의 모음은 도깨비불 없이 새 글자가 된다. */
    private fun inputJungExplicit(v: Char): Result {
        return when {
            jung.isEmpty() -> {
                jung = v.toString()
                Result("", composed())
            }
            jung.length == 1 && jong.isEmpty() && combineVowel(jung[0], v) != null -> {
                jung += v
                Result("", composed())
            }
            else -> {
                val committed = composed()
                reset()
                jung = v.toString()
                Result(committed, composed())
            }
        }
    }

    /** 세벌식 종성: 붙을 자리가 없으면 홑자모로 바로 확정한다. */
    private fun inputJongExplicit(j: Char): Result {
        return when {
            cho != null && jung.isNotEmpty() && jong.isEmpty() -> {
                jong = j.toString()
                Result("", composed())
            }
            jong.length == 1 && JONG_COMBINE.containsKey(jong[0] to j) -> {
                jong += j
                Result("", composed())
            }
            else -> {
                val committed = composed()
                reset()
                Result(committed + j, "")
            }
        }
    }

    private fun inputConsonant(c: Char): Result {
        return when {
            // 빈 상태 → 초성으로 시작
            cho == null && jung.isEmpty() -> {
                cho = c
                Result("", composed())
            }
            // 자음만 있는 상태: 두벌식은 자음 연타 조합이 없으므로 앞 자음을 확정
            jung.isEmpty() -> {
                val committed = composed()
                reset()
                cho = c
                Result(committed, composed())
            }
            // 모음만 있는 상태: 모음을 확정하고 새 초성 시작
            cho == null -> {
                val committed = composed()
                reset()
                cho = c
                Result(committed, composed())
            }
            // 초성+중성: 종성이 될 수 있으면 받침으로
            jong.isEmpty() -> {
                if (canBeJong(c)) {
                    jong = c.toString()
                    Result("", composed())
                } else {
                    val committed = composed()
                    reset()
                    cho = c
                    Result(committed, composed())
                }
            }
            // 홑받침 상태: 겹받침 조합 시도
            jong.length == 1 -> {
                if (JONG_COMBINE.containsKey(jong[0] to c)) {
                    jong += c
                    Result("", composed())
                } else {
                    val committed = composed()
                    reset()
                    cho = c
                    Result(committed, composed())
                }
            }
            // 겹받침까지 찬 상태: 확정 후 새 글자
            else -> {
                val committed = composed()
                reset()
                cho = c
                Result(committed, composed())
            }
        }
    }

    private fun inputVowel(v: Char): Result {
        return when {
            // 빈 상태 → 모음 단독 조합
            cho == null && jung.isEmpty() -> {
                jung = v.toString()
                Result("", composed())
            }
            // 자음만 있는 상태 → 초성+중성
            jung.isEmpty() -> {
                jung = v.toString()
                Result("", composed())
            }
            // 받침 없는 상태: 복합 모음 조합 시도
            jong.isEmpty() -> {
                if (jung.length == 1 && combineVowel(jung[0], v) != null) {
                    jung += v
                    Result("", composed())
                } else {
                    val committed = composed()
                    reset()
                    jung = v.toString()
                    Result(committed, composed())
                }
            }
            // 받침 있는 상태: 도깨비불 — 마지막 받침이 다음 글자 초성으로 이동
            else -> {
                val moving = jong.last()
                jong = jong.dropLast(1)
                val committed = composed()
                reset()
                cho = moving
                jung = v.toString()
                Result(committed, composed())
            }
        }
    }

    /**
     * 조합 중인 글자를 역순으로 한 단계 분해한다.
     * 조합 중이 아니면 null을 돌려주며, 이때 호출자는 일반 삭제(글자 지우기)를 수행해야 한다.
     */
    override fun backspace(): Result? {
        when {
            jong.length == 2 -> jong = jong.take(1)
            jong.length == 1 -> jong = ""
            jung.length == 2 -> jung = jung.take(1)
            jung.length == 1 -> jung = ""
            cho != null -> cho = null
            else -> return null
        }
        return Result("", composed())
    }

    /** 조합 중인 글자를 확정하고 상태를 비운다. 공백·문장부호·포커스 이동 등에 사용. */
    override fun flush(): String {
        val committed = composed()
        reset()
        return committed
    }

    override fun reset() {
        cho = null
        jung = ""
        jong = ""
        lastKey = null
    }

    private fun composed(): String {
        val c = cho
        return when {
            c == null && jung.isEmpty() -> ""
            jung.isEmpty() -> c.toString()
            c == null -> jungChar().toString()
            else -> HangulTables.syllable(c, jungChar(), jongChar()).toString()
        }
    }

    private fun combineVowel(a: Char, b: Char): Char? =
        VOWEL_COMBINE[a to b] ?: if (doubleTapIotation) HangulTables.DOUBLE_TAP_VOWEL[a to b] else null

    private fun jungChar(): Char =
        if (jung.length == 2) combineVowel(jung[0], jung[1])!! else jung[0]

    private fun jongChar(): Char? = when (jong.length) {
        0 -> null
        1 -> jong[0]
        else -> JONG_COMBINE.getValue(jong[0] to jong[1])
    }

    companion object {
        private val DOUBLE_CONSONANT = mapOf(
            'ㄱ' to 'ㄲ',
            'ㄷ' to 'ㄸ',
            'ㅂ' to 'ㅃ',
            'ㅅ' to 'ㅆ',
            'ㅈ' to 'ㅉ',
        )

        fun isVowel(jamo: Char): Boolean = HangulTables.isVowel(jamo)

        fun isHangulJamo(ch: Char): Boolean =
            ch in 'ㄱ'..'ㅣ' || ch in 'ᄀ'..'ᇂ'
    }
}

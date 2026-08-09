package dev.badalab.yeonfeel.hangul

import dev.badalab.yeonfeel.hangul.HangulTables.JONG_COMBINE
import dev.badalab.yeonfeel.hangul.HangulTables.JUNG_LIST
import dev.badalab.yeonfeel.hangul.HangulTables.VOWEL_COMBINE
import dev.badalab.yeonfeel.hangul.HangulTables.canBeJong

/**
 * 두벌식 한글 조합 오토마타. 단모음 자판(모음 연타 이오테이션·쌍자음)도 처리한다.
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

    /** '됬' 금지 모드: 받침 ㅆ이 붙는 순간 ㄷ+ㅚ를 ㄷ+ㅙ(됐)로 고친다. */
    var fixDwaet: Boolean = false

    private fun applyDwaetFix() {
        if (!fixDwaet || cho != 'ㄷ' || jong != "ㅆ") return
        if (jung.isNotEmpty() && jungChar() == 'ㅚ') jung = "ㅗㅐ"
    }
    var multiTapTimeoutMs: Long = 300L

    private var lastKey: Char? = null
    private var lastKeyTime: Long = 0L

    override val isComposing: Boolean
        get() = cho != null || jung.isNotEmpty()

    /** 같은 키를 연타 판정 시간 안에 다시 눌렀는지 — 모음 이오테이션(ㅏㅏ→ㅑ)도 이 시간을 따른다. */
    private var withinTap = false

    override fun input(jamo: Char, now: Long): Result {
        withinTap = jamo == lastKey && now - lastKeyTime < multiTapTimeoutMs
        val result =
            if (doubleTapDoubling && withinTap) {
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
                applyDwaetFix()
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

    fun input(jamo: Char): Result =
        if (HangulTables.isVowel(jamo)) inputVowel(jamo) else inputConsonant(jamo)

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
                    applyDwaetFix()
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
                if (jung.length == 1 && combineVowelOnInput(jung[0], v) != null) {
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

    /** 새 모음 입력 시의 결합 판정: 연타 이오테이션은 연타 판정 시간 안에서만 허용한다. */
    private fun combineVowelOnInput(a: Char, b: Char): Char? =
        VOWEL_COMBINE[a to b]
            ?: if (doubleTapIotation && withinTap) HangulTables.DOUBLE_TAP_VOWEL[a to b] else null

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

        fun isHangulJamo(ch: Char): Boolean = ch in 'ㄱ'..'ㅣ'
    }
}

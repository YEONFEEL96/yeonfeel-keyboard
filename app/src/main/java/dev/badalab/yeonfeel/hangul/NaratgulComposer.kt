package dev.badalab.yeonfeel.hangul

import dev.badalab.yeonfeel.hangul.HangulTables.JONG_COMBINE
import dev.badalab.yeonfeel.hangul.HangulTables.canBeJong

/**
 * 나랏글 조합 오토마타 (2011년 국가표준·특허 개방 방식, KT).
 *
 * - 기본 자음 ㄱㄴㄹㅁㅅㅇ에 '획추가'로 거센소리(ㄱ→ㅋ, ㄴ→ㄷ→ㅌ …),
 *   '쌍자음'으로 된소리(ㄱ→ㄲ …)를 만든다. 모음도 획추가로 이중모음(ㅏ→ㅑ).
 * - ㅏㅓ·ㅗㅜ 키는 같은 키 연타로 토글한다.
 * - 받침 뒤 모음 입력 시 도깨비불은 두벌식과 동일하다.
 */
class NaratgulComposer(
    private val multiTapTimeoutMs: Long = 600L,
) : KoreanComposer {

    private var cho: Char? = null
    private var jung: Char? = null // 표시 모음 하나로 관리 (조합 결과 포함)
    private var jong: String = ""

    private var lastKey: Char? = null
    private var lastTime: Long = 0
    private var lastInputSlot = Slot.NONE

    private enum class Slot { NONE, CHO, JUNG, JONG }

    override val isComposing: Boolean
        get() = cho != null || jung != null

    override fun input(jamo: Char, now: Long): HangulComposer.Result {
        val withinTap = jamo == lastKey && now - lastTime < multiTapTimeoutMs
        val result = when (jamo) {
            KEY_ADD_STROKE -> applyTransform(STROKE_MAP)
            KEY_DOUBLE -> applyTransform(DOUBLE_MAP)
            in CONSONANT_KEYS -> inputConsonant(jamo, withinTap)
            else -> inputVowelKey(jamo, withinTap)
        }
        lastKey = jamo
        lastTime = now
        return result
    }

    /** 획추가·쌍자음: 마지막으로 입력한 자모를 제자리에서 변형한다. */
    private fun applyTransform(map: Map<Char, Char>): HangulComposer.Result {
        when (lastInputSlot) {
            Slot.CHO -> cho?.let { map[it] }?.let { cho = it }
            Slot.JUNG -> jung?.let { map[it] }?.let { jung = it }
            Slot.JONG -> if (jong.isNotEmpty()) {
                val replaced = map[jong.last()]
                if (replaced != null) {
                    val valid =
                        if (jong.length == 1) canBeJong(replaced)
                        else JONG_COMBINE.containsKey(jong[0] to replaced)
                    if (valid) jong = jong.dropLast(1) + replaced
                }
            }
            Slot.NONE -> Unit
        }
        return HangulComposer.Result("", composed())
    }

    private fun inputConsonant(c: Char, withinTap: Boolean): HangulComposer.Result {
        // 같은 자음 키 연타는 새 자모 입력으로 취급하지 않도록 변형 키만 예외 처리하면 되지만,
        // 나랏글 자음 키에는 연타 동작이 없으므로 withinTap은 쓰지 않는다.
        return when {
            cho == null && jung == null -> {
                cho = c
                lastInputSlot = Slot.CHO
                HangulComposer.Result("", composed())
            }
            jung == null || cho == null -> {
                val committed = composed()
                reset()
                cho = c
                lastInputSlot = Slot.CHO
                HangulComposer.Result(committed, composed())
            }
            jong.isEmpty() -> {
                if (canBeJong(c)) {
                    jong = c.toString()
                    lastInputSlot = Slot.JONG
                    HangulComposer.Result("", composed())
                } else {
                    val committed = composed()
                    reset()
                    cho = c
                    lastInputSlot = Slot.CHO
                    HangulComposer.Result(committed, composed())
                }
            }
            jong.length == 1 && JONG_COMBINE.containsKey(jong[0] to c) -> {
                jong += c
                lastInputSlot = Slot.JONG
                HangulComposer.Result("", composed())
            }
            else -> {
                val committed = composed()
                reset()
                cho = c
                lastInputSlot = Slot.CHO
                HangulComposer.Result(committed, composed())
            }
        }
    }

    private fun inputVowelKey(key: Char, withinTap: Boolean): HangulComposer.Result {
        // ㅏㅓ·ㅗㅜ 키 연타 토글: 마지막 입력이 그 키의 모음이면 짝으로 교체
        if (withinTap && lastInputSlot == Slot.JUNG) {
            val toggled = TOGGLE_MAP[key]?.get(jung)
            if (toggled != null) {
                jung = toggled
                return HangulComposer.Result("", composed())
            }
        }
        val v = key
        // 받침 있는 상태 → 도깨비불
        if (jong.isNotEmpty()) {
            val moving = jong.last()
            jong = jong.dropLast(1)
            val committed = composed()
            reset()
            cho = moving
            jung = v
            lastInputSlot = Slot.JUNG
            return HangulComposer.Result(committed, composed())
        }
        return when {
            jung == null -> {
                jung = v
                lastInputSlot = Slot.JUNG
                HangulComposer.Result("", composed())
            }
            VOWEL_COMBINE[jung!! to v] != null -> {
                jung = VOWEL_COMBINE.getValue(jung!! to v)
                lastInputSlot = Slot.JUNG
                HangulComposer.Result("", composed())
            }
            else -> {
                val committed = composed()
                reset()
                jung = v
                lastInputSlot = Slot.JUNG
                HangulComposer.Result(committed, composed())
            }
        }
    }

    override fun backspace(): HangulComposer.Result? {
        when {
            jong.isNotEmpty() -> jong = jong.dropLast(1)
            jung != null -> jung = null
            cho != null -> cho = null
            else -> return null
        }
        lastInputSlot = Slot.NONE
        return HangulComposer.Result("", composed())
    }

    override fun flush(): String {
        val committed = composed()
        reset()
        return committed
    }

    override fun reset() {
        cho = null
        jung = null
        jong = ""
        lastKey = null
        lastInputSlot = Slot.NONE
    }

    private fun composed(): String {
        val c = cho
        val v = jung
        return when {
            c == null && v == null -> ""
            v == null -> c.toString()
            c == null -> v.toString()
            else -> HangulTables.syllable(c, v, jongChar()).toString()
        }
    }

    private fun jongChar(): Char? = when (jong.length) {
        0 -> null
        1 -> jong[0]
        else -> JONG_COMBINE.getValue(jong[0] to jong[1])
    }

    companion object {
        /** 획추가 키 (레이아웃에서 사용, 사설 영역 문자). */
        const val KEY_ADD_STROKE = '\uE000'

        /** 쌍자음 키. */
        const val KEY_DOUBLE = '\uE001'

        private const val CONSONANT_KEYS = "ㄱㄴㄹㅁㅅㅇ"

        /** 획추가: 자음 거센소리 사이클 + 모음 이중모음 토글. */
        private val STROKE_MAP = mapOf(
            'ㄱ' to 'ㅋ', 'ㅋ' to 'ㄱ',
            'ㄴ' to 'ㄷ', 'ㄷ' to 'ㅌ', 'ㅌ' to 'ㄴ',
            'ㅁ' to 'ㅂ', 'ㅂ' to 'ㅍ', 'ㅍ' to 'ㅁ',
            'ㅅ' to 'ㅈ', 'ㅈ' to 'ㅊ', 'ㅊ' to 'ㅅ',
            'ㅇ' to 'ㅎ', 'ㅎ' to 'ㅇ',
            'ㅏ' to 'ㅑ', 'ㅑ' to 'ㅏ',
            'ㅓ' to 'ㅕ', 'ㅕ' to 'ㅓ',
            'ㅗ' to 'ㅛ', 'ㅛ' to 'ㅗ',
            'ㅜ' to 'ㅠ', 'ㅠ' to 'ㅜ',
        )

        /** 쌍자음 토글. */
        private val DOUBLE_MAP = mapOf(
            'ㄱ' to 'ㄲ', 'ㄲ' to 'ㄱ',
            'ㄷ' to 'ㄸ', 'ㄸ' to 'ㄷ',
            'ㅂ' to 'ㅃ', 'ㅃ' to 'ㅂ',
            'ㅅ' to 'ㅆ', 'ㅆ' to 'ㅅ',
            'ㅈ' to 'ㅉ', 'ㅉ' to 'ㅈ',
        )

        /** ㅏㅓ·ㅗㅜ 키 연타 토글 (획추가 상태 포함). */
        private val TOGGLE_MAP = mapOf(
            'ㅏ' to mapOf('ㅏ' to 'ㅓ', 'ㅓ' to 'ㅏ', 'ㅑ' to 'ㅕ', 'ㅕ' to 'ㅑ'),
            'ㅗ' to mapOf('ㅗ' to 'ㅜ', 'ㅜ' to 'ㅗ', 'ㅛ' to 'ㅠ', 'ㅠ' to 'ㅛ'),
        )

        /** 나랏글 확장 모음 조합: 기본 조합에 ㅐㅔㅒㅖ·ㅙㅞ 계열을 더한다. */
        private val VOWEL_COMBINE: Map<Pair<Char, Char>, Char> =
            HangulTables.VOWEL_COMBINE + mapOf(
                ('ㅏ' to 'ㅣ') to 'ㅐ',
                ('ㅓ' to 'ㅣ') to 'ㅔ',
                ('ㅑ' to 'ㅣ') to 'ㅒ',
                ('ㅕ' to 'ㅣ') to 'ㅖ',
                ('ㅘ' to 'ㅣ') to 'ㅙ',
                ('ㅝ' to 'ㅣ') to 'ㅞ',
            )

        fun isNaratgulKey(ch: Char): Boolean =
            ch == KEY_ADD_STROKE || ch == KEY_DOUBLE ||
                ch in CONSONANT_KEYS || ch == 'ㅏ' || ch == 'ㅗ' || ch == 'ㅣ' || ch == 'ㅡ'
    }
}

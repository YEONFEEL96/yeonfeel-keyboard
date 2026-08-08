package dev.badalab.yeonfeel.hangul

/** 모든 한글 조합기가 공유하는 자모 테이블과 음절 조립 규칙. */
internal object HangulTables {
    const val CHO_LIST = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
    const val JUNG_LIST = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ"
    const val JONG_LIST = "ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ"

    val VOWEL_COMBINE = mapOf(
        ('ㅗ' to 'ㅏ') to 'ㅘ',
        ('ㅗ' to 'ㅐ') to 'ㅙ',
        ('ㅗ' to 'ㅣ') to 'ㅚ',
        ('ㅜ' to 'ㅓ') to 'ㅝ',
        ('ㅜ' to 'ㅔ') to 'ㅞ',
        ('ㅜ' to 'ㅣ') to 'ㅟ',
        ('ㅡ' to 'ㅣ') to 'ㅢ',
    )

    val DOUBLE_TAP_VOWEL = mapOf(
        ('ㅏ' to 'ㅏ') to 'ㅑ',
        ('ㅓ' to 'ㅓ') to 'ㅕ',
        ('ㅗ' to 'ㅗ') to 'ㅛ',
        ('ㅜ' to 'ㅜ') to 'ㅠ',
        ('ㅐ' to 'ㅐ') to 'ㅒ',
        ('ㅔ' to 'ㅔ') to 'ㅖ',
    )

    val JONG_COMBINE = mapOf(
        ('ㄱ' to 'ㅅ') to 'ㄳ',
        ('ㄴ' to 'ㅈ') to 'ㄵ',
        ('ㄴ' to 'ㅎ') to 'ㄶ',
        ('ㄹ' to 'ㄱ') to 'ㄺ',
        ('ㄹ' to 'ㅁ') to 'ㄻ',
        ('ㄹ' to 'ㅂ') to 'ㄼ',
        ('ㄹ' to 'ㅅ') to 'ㄽ',
        ('ㄹ' to 'ㅌ') to 'ㄾ',
        ('ㄹ' to 'ㅍ') to 'ㄿ',
        ('ㄹ' to 'ㅎ') to 'ㅀ',
        ('ㅂ' to 'ㅅ') to 'ㅄ',
    )

    fun isVowel(jamo: Char): Boolean = jamo in 'ㅏ'..'ㅣ'

    fun canBeJong(c: Char): Boolean = JONG_LIST.indexOf(c) >= 0

    fun syllable(cho: Char, jung: Char, jong: Char?): Char {
        val choIdx = CHO_LIST.indexOf(cho)
        val jungIdx = JUNG_LIST.indexOf(jung)
        val jongIdx = if (jong == null) 0 else JONG_LIST.indexOf(jong) + 1
        return ('가' + (choIdx * 21 + jungIdx) * 28 + jongIdx)
    }
}

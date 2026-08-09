package dev.badalab.yeonfeel.hangul

/**
 * 한국어 조합기 공통 인터페이스. 자판(두벌식·단모음·천지인·나랏글)마다
 * 조합 규칙이 다르므로 각자 구현하고, IME 서비스는 이 인터페이스로만 다룬다.
 */
interface KoreanComposer {
    val isComposing: Boolean

    /** [now]는 같은 키 연타(멀티탭) 판정용. 연타 개념이 없는 조합기는 무시한다. */
    fun input(jamo: Char, now: Long): HangulComposer.Result

    fun backspace(): HangulComposer.Result?

    fun flush(): String

    fun reset()
}

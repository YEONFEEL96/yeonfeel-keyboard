package dev.badalab.yeonfeel.ime

import dev.badalab.yeonfeel.hangul.KoreanComposer

/**
 * 번역 패널의 원문 버퍼. 키 입력은 앱이 아니라 여기로 온다.
 *
 * 한글은 지금 자판의 조합기(두벌식·단모음·천지인·나랏글)를 따로 하나 만들어 [composer]로 넘긴다 —
 * 앱 쪽 조합기와 상태를 나누지 않으므로 앱의 조합 확정(finishComposition)이 원문을 앱에 쓰는 일이 없다.
 */
class TranslateSourceBuffer {

    /** 패널을 열 때 지금 자판에 맞는 새 조합기를 넣는다. null이면 자모도 그대로 붙인다. */
    var composer: KoreanComposer? = null
        set(value) {
            field?.reset()
            field = value
            composing = ""
        }

    /** 확정 시 '됬'→'됐' 치환 (앱 입력과 같은 안전망). */
    var fixDwaet = false

    private val committed = StringBuilder()
    private var composing = ""

    /** 번역할 원문 — 조합 중인 음절까지 포함한다. */
    val text: String get() = committed.toString() + composing

    val isComposing: Boolean get() = composing.isNotEmpty()

    fun isEmpty(): Boolean = committed.isEmpty() && composing.isEmpty()

    /** 조합기로 보낼 자모 입력. [now]는 연타 판정용. */
    fun inputJamo(jamo: Char, now: Long) {
        val c = composer
        if (c == null) {
            committed.append(jamo)
            return
        }
        val result = c.input(jamo, now)
        appendCommitted(result.commit)
        composing = result.composing
    }

    /** 조합기를 거치지 않는 글자(영문·숫자·기호·공백). 조합 중인 음절을 먼저 확정한다. */
    fun inputChar(ch: Char) {
        flushComposer()
        committed.append(ch)
    }

    /** 조합 중인 음절을 확정한다 (스페이스·언어 전환 등). 원문 [text]는 바뀌지 않는다(보류 병합 제외). */
    fun flushComposer() {
        val c = composer
        if (c != null && c.isComposing) appendCommitted(c.flush())
        composing = ""
    }

    /** 한 글자(또는 조합 한 단계)를 지운다. 지울 것이 없으면 false — 앱에서 지우게 한다. */
    fun backspace(): Boolean {
        composer?.backspace()?.let { result ->
            composing = result.composing
            return true
        }
        if (composing.isNotEmpty()) {
            composing = ""
            return true
        }
        if (committed.isEmpty()) return false
        val count = Character.charCount(Character.codePointBefore(committed, committed.length))
        committed.setLength(committed.length - count)
        return true
    }

    /** 조합 중이 아닐 때의 마지막 글자 (기호 키 연타 판정용). */
    fun lastChar(): Char? = if (composing.isEmpty()) committed.lastOrNull() else null

    /** 마지막 글자를 바꾼다 (기호 키 연타 . → , → ?). 조합 중이거나 비어 있으면 false. */
    fun replaceLast(ch: Char): Boolean {
        if (composing.isNotEmpty() || committed.isEmpty()) return false
        committed.setCharAt(committed.length - 1, ch)
        return true
    }

    fun clear() {
        committed.setLength(0)
        composing = ""
        composer?.reset()
    }

    private fun appendCommitted(text: String) {
        committed.append(if (fixDwaet) text.replace('됬', '됐') else text)
    }
}

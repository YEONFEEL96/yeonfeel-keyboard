package dev.badalab.yeonfeel.ime

/**
 * 커서 바로 앞에 이 키보드가 이어서 써 넣은 글자 수 (UTF-16 단위).
 *
 * 자동 교정은 이 범위 안에 통째로 들어 있는 어절만 고친다. 커서를 옮겨 둔 기존 글이나
 * 붙여넣은 글을 방금 친 어절로 착각하지 않기 위해서다. 교정 뒤로는 그 뒤에 이어 친
 * 글자 수가 되어, 되돌릴 때 커서 앞에서 읽을 길이를 정한다.
 *
 * 입력 연결에 편집을 보낼 때마다 [SelectionTracker]와 함께 해당 on* 메서드를 불러야 한다.
 */
class TypedRun {

    var length = 0
        private set

    /** 지금 조합 영역의 길이 — commitText·setComposingText는 이 영역을 바꿔 쓴다. */
    private var composingLength = 0

    fun onCommit(text: CharSequence) {
        length = maxOf(0, length - composingLength) + text.length
        composingLength = 0
    }

    fun onSetComposing(text: CharSequence) {
        length = maxOf(0, length - composingLength) + text.length
        composingLength = text.length
    }

    fun onFinishComposing() {
        composingLength = 0
    }

    /** 커서 앞 [count]글자를 지운다 (조합 중이 아닐 때). 이어 친 범위를 넘으면 0에서 멈춘다. */
    fun onDeleteBefore(count: Int) {
        length = maxOf(0, length - count)
    }

    /** 흐름을 끊는다. [keep]은 그 자리에서 다시 이어 친 것으로 볼 글자 수다. */
    fun reset(keep: Int = 0) {
        length = keep
        composingLength = 0
    }
}

/**
 * 스페이스바 자동 교정 하나 ([original] → [fixed]). 다음 어절을 이어 치는 동안에도
 * 되돌릴 수 있도록, 교정 뒤에 쓴 글자 수(꼬리 길이)를 받아 바꿀 구간을 계산한다.
 */
data class Correction(val original: String, val fixed: String) {

    /** 되돌릴 때 커서 앞에서 바꿀 길이: 교정 어절 + 공백 + 꼬리. */
    fun spanLength(tailLength: Int): Int = fixed.length + 1 + tailLength

    /**
     * 커서 앞 [spanLength]글자([before])가 아직 "교정 어절 + 공백 + 꼬리"이면 그 구간을 대신할
     * "원래 어절 + 공백 + 꼬리"를, 그 사이 글이 달라졌으면 null을 돌려준다.
     */
    fun revertText(before: CharSequence?, tailLength: Int): String? {
        if (before == null || before.length != spanLength(tailLength)) return null
        val text = before.toString()
        if (!text.startsWith("$fixed ")) return null
        return "$original " + text.substring(fixed.length + 1)
    }
}

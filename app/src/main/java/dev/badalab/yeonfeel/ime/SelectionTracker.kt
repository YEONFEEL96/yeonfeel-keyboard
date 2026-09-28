package dev.badalab.yeonfeel.ime

/**
 * 앱의 선택 콜백(onUpdateSelection)이 우리 편집의 결과인지, 사용자가 커서를 옮긴 것인지 가른다.
 *
 * 콜백은 비동기라 우리 편집의 늦은 메아리와 사용자의 커서 이동이 섞여 온다. 조합 영역
 * (candidatesStart)만으로는 둘을 가를 수 없다 — 카카오톡은 우리 편집 도중의 중간 상태를
 * -1로 보내고, WebView·Compose 계열은 탭으로 커서를 옮기며 조합 영역을 스스로 닫고 -1을
 * 보낸다. 그래서 우리 편집으로 커서가 어디에 있어야 하는지 직접 계산해 두고, 콜백을 그
 * 기대 위치와 비교한다 (AOSP LatinIME RichInputConnection.isBelatedExpectedUpdate 방식).
 *
 * 입력 연결에 편집을 보낼 때마다 해당 on* 메서드를 불러 기대 위치를 갱신해야 한다.
 */
class SelectionTracker {

    enum class Verdict {
        /** 우리 편집의 결과(늦게 도착한 것 포함)이거나 판단할 수 없는 콜백. */
        OWN,

        /** 사용자나 앱이 커서를 옮겼다 — 조합을 끝내야 한다. */
        MOVED,
    }

    /** 우리 편집 뒤 기대하는 선택 영역. -1이면 모름 — 키 이벤트처럼 결과를 예측할 수 없는 편집 뒤. */
    var expectedSelStart = -1
        private set
    var expectedSelEnd = -1
        private set

    /** 앱에 마지막으로 보낸 조합 문자열. 조합 영역은 늘 커서 바로 앞에 있다. */
    var composing = ""
        private set

    /**
     * 기대 위치 그대로인데 조합 영역이 사라졌다는 콜백을 받았다 — 앱이 제자리에서 조합을
     * 닫았을 수 있다. 일시적인 -1일 수도 있어 곧바로 끊지 않고 다음 편집 직전에 확정한다.
     */
    var compositionDropped = false
        private set

    /** 이 입력란이 조합 영역을 보고한 적이 있는지 — 없는 앱에서는 -1을 조합 소실로 보지 않는다. */
    private var appReportsComposing = false

    /** 새 입력란에서 시작한다. 선택 위치를 모르면 -1. */
    fun start(selStart: Int, selEnd: Int) {
        if (selStart < 0 || selEnd < 0) setExpected(-1, -1) else setExpected(selStart, selEnd)
        composing = ""
        compositionDropped = false
        appReportsComposing = false
    }

    fun onCommit(text: CharSequence) {
        moveCursorAfterReplace(text.length)
        composing = ""
    }

    fun onSetComposing(text: CharSequence) {
        moveCursorAfterReplace(text.length)
        composing = text.toString()
    }

    fun onFinishComposing() {
        composing = ""
        compositionDropped = false
    }

    /** 커서 앞 [length]글자를 지운다 (조합 중이 아닐 때만 쓴다). */
    fun onDeleteBefore(length: Int) {
        if (expectedSelStart >= 0 && expectedSelStart == expectedSelEnd) {
            val pos = maxOf(0, expectedSelStart - length)
            setExpected(pos, pos)
        } else {
            setExpected(-1, -1)
        }
    }

    /** 키 이벤트·에디터 동작처럼 앱이 무엇을 할지 모르는 편집. */
    fun onUnpredictableEdit() {
        setExpected(-1, -1)
    }

    /**
     * 조합이 끊겼다고 판단해 버린다. [cursorKnown]이 false면 커서가 어디 있는지 몰라
     * 다음 콜백으로 다시 맞춘다.
     */
    fun abandonComposition(cursorKnown: Boolean) {
        composing = ""
        compositionDropped = false
        if (!cursorKnown) setExpected(-1, -1)
    }

    fun onUpdate(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ): Verdict {
        if (candidatesStart >= 0) appReportsComposing = true
        val isComposing = composing.isNotEmpty()
        // 지금 조합 중인 글자 그대로의 영역을 보고하는 콜백 — 기대 위치가 틀렸어도 우리 것이다.
        val matchesComposition = isComposing && candidatesStart >= 0 &&
            newSelStart == newSelEnd && newSelEnd == candidatesEnd &&
            candidatesEnd - candidatesStart == composing.length
        return when {
            expectedSelStart < 0 -> {
                // 기대 위치를 모를 때: 조합 중이 아니거나 우리 조합과 맞는 콜백으로 다시 맞춘다.
                // 조합 중에 맞지 않는 콜백은 판단하지 않는다 — 다음 키 입력 전 확인에 맡긴다.
                if (!isComposing || matchesComposition) setExpected(newSelStart, newSelEnd)
                Verdict.OWN
            }
            newSelStart == expectedSelStart && newSelEnd == expectedSelEnd -> {
                if (isComposing) compositionDropped = candidatesStart < 0 && appReportsComposing
                Verdict.OWN
            }
            isBelated(oldSelStart, oldSelEnd, newSelStart, newSelEnd) -> Verdict.OWN
            matchesComposition -> {
                // 기준 위치가 틀렸던 것(초기 위치 오보고 등) — 조합 그대로이니 여기에 다시 맞춘다.
                setExpected(newSelStart, newSelEnd)
                compositionDropped = false
                Verdict.OWN
            }
            else -> {
                setExpected(newSelStart, newSelEnd)
                composing = ""
                compositionDropped = false
                Verdict.MOVED
            }
        }
    }

    /** 커서가 이전 위치에서 기대 위치로 가는 길목에 있으면 우리 편집이 늦게 도착한 것이다. */
    private fun isBelated(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int): Boolean {
        // 앱이 이미 기대 위치를 보고했는데 거기서 움직였다면 우리가 한 일이 아니다.
        if (oldSelStart == expectedSelStart && oldSelEnd == expectedSelEnd) return false
        return newSelStart == newSelEnd &&
            (newSelStart - oldSelStart).toLong() * (expectedSelStart - newSelStart) >= 0 &&
            (newSelEnd - oldSelEnd).toLong() * (expectedSelEnd - newSelEnd) >= 0
    }

    /** commitText·setComposingText는 조합 영역(없으면 선택 영역)을 바꾸고 커서를 그 끝에 둔다. */
    private fun moveCursorAfterReplace(length: Int) {
        if (expectedSelStart < 0) return
        val start = if (composing.isNotEmpty()) expectedSelEnd - composing.length else expectedSelStart
        setExpected(start + length, start + length)
    }

    private fun setExpected(selStart: Int, selEnd: Int) {
        expectedSelStart = selStart
        expectedSelEnd = selEnd
    }
}

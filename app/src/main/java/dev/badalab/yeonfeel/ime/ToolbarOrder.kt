package dev.badalab.yeonfeel.ime

/** 툴바 순서 CSV 도우미. */
object ToolbarOrder {

    fun parse(csv: String): List<String> = csv.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    /**
     * 화면에 보이는 아이콘만으로 새 순서를 만들면 지금 숨겨 둔 항목(비밀번호 입력란의 번역 버튼 등)이
     * 저장값에서 사라진다. 숨긴 항목을 이전 순서의 자리에 다시 넣는다.
     */
    fun keepHidden(visibleOrder: List<String>, previous: List<String>, hidden: Collection<String>): List<String> {
        val result = visibleOrder.filter { it !in hidden }.toMutableList()
        previous.forEachIndexed { index, id ->
            if (id in hidden && id !in result) result.add(minOf(index, result.size), id)
        }
        return result
    }
}

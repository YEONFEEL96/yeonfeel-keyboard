package dev.badalab.yeonfeel.clipboard

/**
 * 클립보드 이력 저장소. 보안 원칙:
 *
 * - 디스크 보관은 [SecureClipboardStore]를 통해 Keystore 키로 암호화해서만 한다.
 *   네트워크 전송은 없다.
 * - 비밀번호 관리자 등이 민감([android.content.ClipDescription.EXTRA_IS_SENSITIVE])으로
 *   표시한 클립은 아예 수집하지 않는다.
 * - [ttlMillis]를 주면 그 시간 뒤 자동 만료된다. null이면 만료 없이 보관한다.
 * - 과도하게 긴 텍스트([MAX_TEXT_LENGTH] 초과)는 이력에 남기지 않는다.
 */
class ClipboardHistory(
    private val ttlMillis: Long? = null,
    private val maxItems: Int = DEFAULT_MAX_ITEMS,
) {
    data class Entry(val text: String, val addedAt: Long, val pinned: Boolean = false)

    private val items = ArrayDeque<Entry>()

    /** 새 클립을 추가한다. 보안 정책상 걸러진 경우 false를 돌려준다. */
    @Synchronized
    fun add(text: String, isSensitive: Boolean, now: Long): Boolean {
        if (isSensitive || text.isBlank() || text.length > MAX_TEXT_LENGTH) return false
        val wasPinned = items.firstOrNull { it.text == text }?.pinned ?: false
        items.removeAll { it.text == text }
        items.addFirst(Entry(text, now, wasPinned))
        // 넘치면 고정 안 된 오래된 항목부터 버린다.
        while (items.size > maxItems) {
            val victim = items.lastOrNull { !it.pinned } ?: items.last()
            items.remove(victim)
        }
        return true
    }

    /** 만료 항목을 정리한 뒤 고정 항목 우선, 최신순 목록을 돌려준다. */
    @Synchronized
    fun entries(now: Long): List<Entry> {
        ttlMillis?.let { ttl -> items.removeAll { now - it.addedAt > ttl && !it.pinned } }
        return items.sortedByDescending { it.pinned }
    }

    /** 상단 고정 상태를 바꾼다. */
    @Synchronized
    fun setPinned(text: String, pinned: Boolean) {
        val index = items.indexOfFirst { it.text == text }
        if (index >= 0) items[index] = items[index].copy(pinned = pinned)
    }

    /** 암호화 저장소에서 복원할 때 사용한다. 기존 내용은 대체된다. */
    @Synchronized
    fun restore(entries: List<Entry>) {
        items.clear()
        items.addAll(entries.take(maxItems))
    }

    @Synchronized
    fun remove(text: String) {
        items.removeAll { it.text == text }
    }

    @Synchronized
    fun clear() = items.clear()

    companion object {
        const val DEFAULT_MAX_ITEMS = 30
        const val MAX_TEXT_LENGTH = 5000
    }
}

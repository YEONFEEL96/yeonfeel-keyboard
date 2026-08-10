package dev.badalab.yeonfeel.settings

/**
 * 2단 설정에서 현재 오른쪽에 떠 있는 상세 화면을 추적하고, 같은 메뉴를 다시
 * 눌렀을 때 리로드 대신 촉각 펄스(살짝 축소 후 복귀)를 재생하기 위한 버스.
 *
 * 상세 화면은 별도 액티비티라 좌측 목록이 직접 애니메이션할 수 없으므로,
 * Application의 라이프사이클 콜백이 resume 시 자기 루트 펄스를 등록하고
 * pause 시 해제한다. clearTop 규칙상 상세는 항상 하나뿐이라 소유자는 1개다.
 */
object DetailPulseBus {
    private var owner: Any? = null
    private var shownClass: Class<*>? = null
    private var pulse: (() -> Unit)? = null

    fun register(owner: Any, cls: Class<*>, pulse: () -> Unit) {
        this.owner = owner
        this.shownClass = cls
        this.pulse = pulse
    }

    fun unregister(owner: Any) {
        if (this.owner === owner) {
            this.owner = null
            this.shownClass = null
            this.pulse = null
        }
    }

    /** 현재 오른쪽에 떠 있는 상세 화면의 클래스 (없으면 null). */
    fun currentClass(): Class<*>? = shownClass

    fun pulse() {
        pulse?.invoke()
    }
}

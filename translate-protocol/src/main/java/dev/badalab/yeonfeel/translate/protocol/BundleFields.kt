package dev.badalab.yeonfeel.translate.protocol

import android.os.Bundle

/**
 * [Bundle]을 [FieldReader]/[FieldWriter]로 감싼다. 코덱은 이 인터페이스만 알아서 JVM 단위
 * 테스트에서 Map으로 검증할 수 있다.
 *
 * 다른 프로세스에서 온 Bundle은 처음 읽을 때 풀리는데(unparcel), 깨졌거나 모르는 클래스가 들어 있으면
 * 예외가 난다 — 그런 필드는 없는 것(null)으로 읽어 디코더가 형식 오류로 처리하게 한다.
 */
class BundleFields(private val bundle: Bundle) : FieldReader, FieldWriter {

    override fun string(key: String): String? = read(key) as? String

    override fun int(key: String): Int? = read(key) as? Int

    override fun boolean(key: String): Boolean? = read(key) as? Boolean

    @Suppress("DEPRECATION") // 타입을 확인하며 읽으려면 get()이 필요하다 (원시 타입용 대체 API가 없다).
    private fun read(key: String): Any? = try {
        bundle.get(key)
    } catch (_: RuntimeException) {
        null
    }

    override fun putString(key: String, value: String) = bundle.putString(key, value)

    override fun putInt(key: String, value: Int) = bundle.putInt(key, value)

    override fun putBoolean(key: String, value: Boolean) = bundle.putBoolean(key, value)
}

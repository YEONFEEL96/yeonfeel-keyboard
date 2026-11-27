package dev.badalab.yeonfeel.translate.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslateProtocolTest {

    /** Bundle 대신 쓰는 Map 저장소. Bundle처럼 타입이 다르면 null로 읽는다. */
    private class MapFields(val map: MutableMap<String, Any?> = mutableMapOf()) : FieldReader, FieldWriter {
        override fun string(key: String) = map[key] as? String
        override fun int(key: String) = map[key] as? Int
        override fun boolean(key: String) = map[key] as? Boolean
        override fun putString(key: String, value: String) { map[key] = value }
        override fun putInt(key: String, value: Int) { map[key] = value }
        override fun putBoolean(key: String, value: Boolean) { map[key] = value }
    }

    private fun encoded(request: TranslateRequest) = MapFields().also { request.encode(it) }

    private fun encoded(response: TranslateResponse) = MapFields().also { response.encode(it) }

    private val request = TranslateRequest("안녕하세요", "ko", "en", allowMeteredDownload = false)

    @Test
    fun `요청은 인코딩한 그대로 디코딩된다`() {
        assertEquals(TranslateRequest.Decoded.Valid(request), TranslateRequest.decode(encoded(request)))
        val metered = request.copy(allowMeteredDownload = true, source = "zh-Hant")
        assertEquals(TranslateRequest.Decoded.Valid(metered), TranslateRequest.decode(encoded(metered)))
    }

    @Test
    fun `응답은 인코딩한 그대로 디코딩된다`() {
        val success = TranslateResponse.Success("Hello")
        assertEquals(success, TranslateResponse.decode(encoded(success)))
        FailureCode.entries.forEach { code ->
            val failure = TranslateResponse.Failure(code, "detail")
            assertEquals(failure, TranslateResponse.decode(encoded(failure)))
        }
        val noDetail = TranslateResponse.Failure(FailureCode.DOWNLOADING)
        assertEquals(noDetail, TranslateResponse.decode(encoded(noDetail)))
    }

    @Test
    fun `빈 번역문도 성공으로 읽는다`() {
        assertEquals(TranslateResponse.Success(""), TranslateResponse.decode(encoded(TranslateResponse.Success(""))))
    }

    @Test
    fun `버전이 없거나 지원 범위 밖이면 프로토콜 불일치로 거절한다`() {
        listOf(null, 0, TranslateProtocol.PROTOCOL_VERSION + 1).forEach { version ->
            val fields = encoded(request).apply { map[TranslateProtocol.KEY_VERSION] = version }
            assertEquals(
                TranslateRequest.Decoded.Invalid(TranslateResponse.Failure(FailureCode.ENGINE_UNAVAILABLE, PROTOCOL_MISMATCH)),
                TranslateRequest.decode(fields),
            )
            val response = encoded(TranslateResponse.Success("x")).apply { map[TranslateProtocol.KEY_VERSION] = version }
            assertEquals(
                TranslateResponse.Failure(FailureCode.ENGINE_UNAVAILABLE, PROTOCOL_MISMATCH),
                TranslateResponse.decode(response),
            )
        }
    }

    @Test
    fun `지원 버전 범위는 현재 버전을 포함한다`() {
        assertTrue(TranslateProtocol.isSupportedVersion(TranslateProtocol.PROTOCOL_VERSION))
        assertTrue(TranslateProtocol.MIN_SUPPORTED_VERSION <= TranslateProtocol.PROTOCOL_VERSION)
        assertFalse(TranslateProtocol.isSupportedVersion(TranslateProtocol.MIN_SUPPORTED_VERSION - 1))
    }

    @Test
    fun `필드가 빠지거나 타입이 다르면 형식 오류다`() {
        val malformed = TranslateRequest.Decoded.Invalid(TranslateResponse.Failure(FailureCode.ERROR, MALFORMED))
        assertEquals(malformed, TranslateRequest.decode(encoded(request).apply { map.remove(TranslateProtocol.KEY_TEXT) }))
        assertEquals(malformed, TranslateRequest.decode(encoded(request).apply { map[TranslateProtocol.KEY_ALLOW_METERED] = "true" }))

        val badResponse = TranslateResponse.Failure(FailureCode.ERROR, MALFORMED)
        assertEquals(badResponse, TranslateResponse.decode(encoded(TranslateResponse.Success("x")).apply { map.remove(TranslateProtocol.KEY_TEXT) }))
        assertEquals(badResponse, TranslateResponse.decode(encoded(TranslateResponse.Success("x")).apply { map.remove(TranslateProtocol.KEY_STATUS) }))
    }

    @Test
    fun `언어 코드가 없거나 형식이 틀리면 지원하지 않는 언어다`() {
        val unsupported = TranslateRequest.Decoded.Invalid(TranslateResponse.Failure(FailureCode.LANGUAGE_UNSUPPORTED))
        listOf(null, "", "k", "korean", "ko_KR", "ko-", "../x", "a".repeat(40)).forEach { code ->
            assertEquals(code.toString(), unsupported, TranslateRequest.decode(encoded(request).apply { map[TranslateProtocol.KEY_SOURCE] = code }))
            assertEquals(code.toString(), unsupported, TranslateRequest.decode(encoded(request).apply { map[TranslateProtocol.KEY_TARGET] = code }))
        }
    }

    @Test
    fun `올바른 언어 코드`() {
        listOf("ko", "en", "zh", "zh-Hant", "pt-BR", "fil").forEach { assertTrue(it, TranslateProtocol.isValidLanguageCode(it)) }
    }

    @Test
    fun `원문이 최대 길이를 넘으면 잘라 번역하지 않고 거절한다`() {
        val max = request.copy(text = "가".repeat(TranslateProtocol.MAX_TEXT_LENGTH))
        assertEquals(TranslateRequest.Decoded.Valid(max), TranslateRequest.decode(encoded(max)))
        val over = request.copy(text = "가".repeat(TranslateProtocol.MAX_TEXT_LENGTH + 1))
        assertEquals(
            TranslateRequest.Decoded.Invalid(TranslateResponse.Failure(FailureCode.TEXT_TOO_LONG)),
            TranslateRequest.decode(encoded(over)),
        )
    }

    @Test
    fun `모르는 실패 코드는 오류로 읽는다`() {
        val fields = encoded(TranslateResponse.Failure(FailureCode.BUSY)).apply { map[TranslateProtocol.KEY_STATUS] = 999 }
        assertEquals(TranslateResponse.Failure(FailureCode.ERROR, "unknown status 999"), TranslateResponse.decode(fields))
    }

    @Test
    fun `실패 코드 전송값은 고유하고 성공 값과 겹치지 않는다`() {
        val codes = FailureCode.entries.map { it.code }
        assertEquals(codes.size, codes.toSet().size)
        assertFalse(TranslateProtocol.STATUS_OK in codes)
        FailureCode.entries.forEach { assertEquals(it, FailureCode.fromCode(it.code)) }
        assertNull(FailureCode.fromCode(TranslateProtocol.STATUS_OK))
    }

    @Test
    fun `실패 코드 전송값은 고정돼 있다`() {
        // 이미 배포된 키보드·애드온과 호환되려면 값이 바뀌면 안 된다.
        assertEquals(1, FailureCode.ENGINE_UNAVAILABLE.code)
        assertEquals(4, FailureCode.NEEDS_WIFI.code)
        assertEquals(5, FailureCode.DOWNLOADING.code)
        assertEquals(11, FailureCode.ERROR.code)
    }

    @Test
    fun `상세 메시지는 최대 길이로 자른다`() {
        val long = "e".repeat(TranslateProtocol.MAX_DETAIL_LENGTH * 2)
        val decoded = TranslateResponse.decode(encoded(TranslateResponse.Failure(FailureCode.ERROR, long)))
        assertEquals(TranslateProtocol.MAX_DETAIL_LENGTH, (decoded as TranslateResponse.Failure).detail!!.length)
    }

    @Test
    fun `문자열 표현에 원문과 번역문이 들어가지 않는다`() {
        val secret = "비밀번호는 1234"
        assertFalse(request.copy(text = secret).toString().contains(secret))
        assertFalse(TranslateResponse.Success(secret).toString().contains(secret))
    }
}

package dev.badalab.yeonfeel.translate

import android.view.translation.TranslationCapability
import com.google.mlkit.genai.common.FeatureStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class AvailabilityTest {

    @Test
    fun `시스템 번역 언어 쌍 상태를 옮긴다`() {
        assertEquals(Availability.READY, Availability.fromCapabilityState(TranslationCapability.STATE_ON_DEVICE))
        assertEquals(
            Availability.NEEDS_DOWNLOAD,
            Availability.fromCapabilityState(TranslationCapability.STATE_AVAILABLE_TO_DOWNLOAD),
        )
        assertEquals(Availability.DOWNLOADING, Availability.fromCapabilityState(TranslationCapability.STATE_DOWNLOADING))
        assertEquals(
            Availability.LANGUAGE_UNSUPPORTED,
            Availability.fromCapabilityState(TranslationCapability.STATE_NOT_AVAILABLE),
        )
        assertEquals(
            Availability.LANGUAGE_UNSUPPORTED,
            Availability.fromCapabilityState(TranslationCapability.STATE_REMOVED_AND_AVAILABLE),
        )
    }

    @Test
    fun `지원 언어 목록이 비면 이 기기에서 쓸 수 없다`() {
        assertEquals(Availability.UNAVAILABLE, Availability.fromCapabilityState(null))
    }

    @Test
    fun `Gemini Nano 상태를 옮긴다`() {
        assertEquals(Availability.READY, Availability.fromFeatureStatus(FeatureStatus.AVAILABLE))
        assertEquals(Availability.NEEDS_DOWNLOAD, Availability.fromFeatureStatus(FeatureStatus.DOWNLOADABLE))
        assertEquals(Availability.DOWNLOADING, Availability.fromFeatureStatus(FeatureStatus.DOWNLOADING))
        assertEquals(Availability.UNAVAILABLE, Availability.fromFeatureStatus(FeatureStatus.UNAVAILABLE))
        assertEquals(Availability.UNKNOWN, Availability.fromFeatureStatus(-42))
    }

    @Test
    fun `쓸 수 없음이 확실한 실패만 상태로 옮긴다`() {
        assertEquals(Availability.UNAVAILABLE, Availability.fromFailure(TranslationResult.Reason.ENGINE_UNAVAILABLE))
        assertEquals(
            Availability.ADDON_NOT_INSTALLED,
            Availability.fromFailure(TranslationResult.Reason.ADDON_NOT_INSTALLED),
        )
        assertEquals(
            Availability.LANGUAGE_UNSUPPORTED,
            Availability.fromFailure(TranslationResult.Reason.LANGUAGE_UNSUPPORTED),
        )
        assertEquals(Availability.NEEDS_DOWNLOAD, Availability.fromFailure(TranslationResult.Reason.NEEDS_DOWNLOAD))
        assertEquals(Availability.NEEDS_DOWNLOAD, Availability.fromFailure(TranslationResult.Reason.NEEDS_WIFI))
        assertEquals(Availability.DOWNLOADING, Availability.fromFailure(TranslationResult.Reason.DOWNLOADING))
        listOf(
            TranslationResult.Reason.BUSY,
            TranslationResult.Reason.ERROR,
            TranslationResult.Reason.BLOCKED_IN_BACKGROUND,
            TranslationResult.Reason.CANCELLED,
            TranslationResult.Reason.TEXT_TOO_LONG,
        ).forEach { assertEquals(it.name, Availability.UNKNOWN, Availability.fromFailure(it)) }
    }
}

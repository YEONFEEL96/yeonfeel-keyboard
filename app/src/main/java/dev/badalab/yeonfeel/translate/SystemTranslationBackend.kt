package dev.badalab.yeonfeel.translate

import android.content.Context
import android.icu.util.ULocale
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.translation.TranslationCapability
import android.view.translation.TranslationContext
import android.view.translation.TranslationManager
import android.view.translation.TranslationRequest
import android.view.translation.TranslationRequestValue
import android.view.translation.TranslationResponse
import android.view.translation.TranslationSpec
import android.view.translation.Translator
import androidx.annotation.RequiresApi
import androidx.core.util.isNotEmpty
import java.util.concurrent.Executors

/**
 * Android 12+ 시스템 번역(TranslationManager). 권한이 필요 없는 공개 API지만, 실제 번역은
 * 제조사가 등록한 기본 번역 서비스(Pixel은 Android System Intelligence)가 한다 —
 * 서비스가 없는 기기에서는 지원 언어 목록이 비어 [TranslationResult.Reason.ENGINE_UNAVAILABLE].
 *
 * 상태는 모두 메인 스레드에서만 읽고 쓴다. 작업 스레드는 지원 언어 조회만 한다.
 */
@RequiresApi(Build.VERSION_CODES.S)
internal class SystemTranslationBackend(context: Context) : TranslationBackend {

    private val manager: TranslationManager? = context.getSystemService(TranslationManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mainExecutor = context.mainExecutor

    // 지원 언어 조회는 바인더 동기 호출(최대 수 초)이라 작업 스레드에서 한다.
    private val worker = Executors.newSingleThreadExecutor()

    private var translator: Translator? = null
    private var translatorPair: Pair<TranslationLanguage, TranslationLanguage>? = null

    /** 번역기를 준비(지원 조회 → 생성) 중인 언어 쌍. 같은 쌍의 요청은 이 준비를 함께 기다린다. */
    private var preparing: Preparation? = null
    private var closed = false

    override fun translate(
        text: String,
        source: TranslationLanguage,
        target: TranslationLanguage,
        callback: (TranslationResult) -> Unit,
    ) {
        if (closed) return
        if (source == target) return callback(TranslationResult.Success(text))
        val manager = manager ?: return callback(failure(TranslationResult.Reason.ENGINE_UNAVAILABLE))
        // 제한 시간은 조회·생성·번역 전체에 건다.
        val once = OnceCallback(callback)
        val pair = source to target
        val current = translator
        if (current != null && !current.isDestroyed && translatorPair == pair) {
            request(current, text, once)
            return
        }
        val pending = preparing
        if (pending != null && pending.pair == pair && !pending.isStale()) {
            pending.waiters += text to once
            return
        }
        // 다른 쌍을 준비하던 요청은 더는 쓸모가 없다. 멈춘 준비는 버리고 새로 시작한다.
        pending?.finish(failure(TranslationResult.Reason.CANCELLED))
        releaseTranslator()
        val prep = Preparation(pair)
        prep.waiters += text to once
        preparing = prep
        worker.execute {
            val state = capabilityState(manager, source, target)
            mainHandler.post { onCapability(manager, prep, state) }
        }
    }

    /**
     * 지원 언어 조회만 작업 스레드에서 하고 결과를 메인 스레드로 옮긴다. 번역기를 만들거나 언어 팩을
     * 받지 않는다 — 언어 팩은 시스템 설정에서만 받는다.
     *
     * 언어 팩 설치를 지켜보는 리스너(addOnDeviceTranslationCapabilityUpdateListener)는 두지 않는다.
     * [translate]는 이 쌍의 번역기가 없을 때마다 지원 상태를 새로 조회하고 실패 상태를 캐시하지 않으므로,
     * 시스템 설정에서 받은 언어 팩은 다음 요청부터 바로 쓰인다. 설정 화면은 돌아올 때마다 이 조회를 다시 한다.
     */
    override fun checkAvailability(
        source: TranslationLanguage,
        target: TranslationLanguage,
        callback: (Availability) -> Unit,
    ) {
        if (closed) return
        if (source == target) return callback(Availability.READY)
        val manager = manager ?: return callback(Availability.UNAVAILABLE)
        // 서비스가 느려도 번역과 같은 제한 시간 안에 답한다 — 늦게 온 조회 결과는 버린다.
        var done = false
        val token = Any()
        val finish = { availability: Availability ->
            if (!done && !closed) {
                done = true
                mainHandler.removeCallbacksAndMessages(token)
                callback(availability)
            }
        }
        mainHandler.postAtTime({ finish(Availability.UNKNOWN) }, token, SystemClock.uptimeMillis() + TIMEOUT_MS)
        worker.execute {
            val state = capabilityState(manager, source, target)
            mainHandler.post { finish(Availability.fromCapabilityState(state)) }
        }
    }

    private fun onCapability(manager: TranslationManager, prep: Preparation, state: Int?) {
        if (closed || preparing !== prep) return
        when (state) {
            null -> prep.finish(failure(TranslationResult.Reason.ENGINE_UNAVAILABLE))
            TranslationCapability.STATE_ON_DEVICE -> create(manager, prep)
            TranslationCapability.STATE_AVAILABLE_TO_DOWNLOAD ->
                prep.finish(failure(TranslationResult.Reason.NEEDS_DOWNLOAD))
            TranslationCapability.STATE_DOWNLOADING ->
                prep.finish(failure(TranslationResult.Reason.DOWNLOADING))
            else -> prep.finish(failure(TranslationResult.Reason.LANGUAGE_UNSUPPORTED))
        }
    }

    /**
     * 언어 쌍의 지원 상태. 서비스가 아무 언어도 알려주지 않으면 null(서비스 없음), 목록에 그 쌍이 없으면
     * [TranslationCapability.STATE_NOT_AVAILABLE]. 작업 스레드에서 부른다.
     *
     * 프레임워크는 서비스 응답이 제한 시간 안에 오지 않아도 빈 목록을 돌려준다 — 그래서 빈 목록은
     * "서비스 없음 또는 응답 없음"이다. 둘 다 지금은 쓸 수 없다는 뜻이라 구분하지 않는다.
     * 조회 자체가 예외를 던져도 같은 이유로 null.
     */
    private fun capabilityState(
        manager: TranslationManager,
        source: TranslationLanguage,
        target: TranslationLanguage,
    ): Int? {
        val caps = runCatching {
            manager.getOnDeviceTranslationCapabilities(
                TranslationSpec.DATA_FORMAT_TEXT,
                TranslationSpec.DATA_FORMAT_TEXT,
            )
        }.getOrNull()
        if (caps.isNullOrEmpty()) return null
        // 같은 언어에 스크립트·지역 변형이 여럿일 수 있다 (zh-Hans/zh-Hant) — 가장 쓸 만한 상태를 고른다.
        return caps.filter {
            it.sourceSpec.locale.language == source.code && it.targetSpec.locale.language == target.code
        }.map { it.state }.maxByOrNull(::stateRank) ?: TranslationCapability.STATE_NOT_AVAILABLE
    }

    /** 상태의 쓸모 순위: 기기에 있음 > 받는 중 > 받을 수 있음 > 그 밖. */
    private fun stateRank(state: Int): Int = when (state) {
        TranslationCapability.STATE_ON_DEVICE -> 3
        TranslationCapability.STATE_DOWNLOADING -> 2
        TranslationCapability.STATE_AVAILABLE_TO_DOWNLOAD -> 1
        else -> 0
    }

    private fun create(manager: TranslationManager, prep: Preparation) {
        val (source, target) = prep.pair
        val context = TranslationContext.Builder(
            TranslationSpec(ULocale(source.code), TranslationSpec.DATA_FORMAT_TEXT),
            TranslationSpec(ULocale(target.code), TranslationSpec.DATA_FORMAT_TEXT),
        ).build()
        runCatching {
            manager.createOnDeviceTranslator(context, mainExecutor) { created ->
                // 그사이 닫혔거나 다른 쌍으로 바뀌었으면 이 번역기는 쓸 데가 없다.
                if (closed || preparing !== prep) {
                    created?.let { runCatching { it.destroy() } }
                    return@createOnDeviceTranslator
                }
                if (created == null) {
                    prep.finish(failure(TranslationResult.Reason.ERROR, "translator not created"))
                    return@createOnDeviceTranslator
                }
                releaseTranslator()
                translator = created
                translatorPair = prep.pair
                preparing = null
                prep.waiters.forEach { (text, once) -> request(created, text, once) }
                prep.waiters.clear()
            }
        }.onFailure { prep.finish(failure(TranslationResult.Reason.ERROR, it.message)) }
    }

    private fun request(translator: Translator, text: String, once: OnceCallback) {
        val request = TranslationRequest.Builder()
            .setTranslationRequestValues(listOf(TranslationRequestValue.forText(text)))
            .build()
        // 서비스 프로세스가 죽으면 프레임워크는 바인더 오류를 로그만 남기고 삼켜 콜백이 오지 않는다 —
        // 시간 초과나 실패 응답이면 이 번역기를 버려 다음 요청에서 새로 만든다.
        once.onTimeout = { releaseIfCurrent(translator) }
        runCatching {
            // 취소는 하지 않는다 — 낡은 결과는 호출하는 쪽이 버리고, close()는 번역기를 파기한다.
            translator.translate(request, CancellationSignal(), mainExecutor) { response ->
                val values = response.translationResponseValues
                val translated = if (values.isNotEmpty()) values.valueAt(0).text?.toString() else null
                if (response.translationStatus == TranslationResponse.TRANSLATION_STATUS_SUCCESS &&
                    translated != null
                ) {
                    once(TranslationResult.Success(translated))
                } else {
                    if (response.translationStatus != TranslationResponse.TRANSLATION_STATUS_SUCCESS) {
                        releaseIfCurrent(translator)
                    }
                    once(failure(TranslationResult.Reason.ERROR, "status ${response.translationStatus}"))
                }
            }
        }.onFailure {
            // 세션이 서비스 쪽에서 파기된 경우 — 다음 요청에서 번역기를 새로 만든다.
            releaseIfCurrent(translator)
            once(failure(TranslationResult.Reason.ERROR, it.message))
        }
    }

    private fun releaseIfCurrent(translator: Translator) {
        if (!closed && this.translator === translator) releaseTranslator()
    }

    private fun releaseTranslator() {
        translator?.let { runCatching { it.destroy() } }
        translator = null
        translatorPair = null
    }

    override fun close() {
        if (closed) return
        closed = true
        preparing = null
        releaseTranslator()
        mainHandler.removeCallbacksAndMessages(null)
        worker.shutdown()
    }

    /** 번역기 준비와 그 결과를 기다리는 요청들. */
    private inner class Preparation(val pair: Pair<TranslationLanguage, TranslationLanguage>) {
        private val startedAt = SystemClock.uptimeMillis()
        val waiters = mutableListOf<Pair<String, OnceCallback>>()

        /** 제한 시간이 지나도록 끝나지 않은 준비. 기다리던 요청은 이미 시간 초과로 끝났다. */
        fun isStale() = SystemClock.uptimeMillis() - startedAt > TIMEOUT_MS

        fun finish(result: TranslationResult) {
            if (preparing === this) preparing = null
            waiters.forEach { (_, once) -> once(result) }
            waiters.clear()
        }
    }

    /**
     * 결과·제한 시간 중 먼저 온 것 하나만 메인 스레드에서 전달하고, close() 뒤에는 아무것도 전달하지 않는다.
     * 자신을 제한 시간 콜백의 토큰으로 써서 결과가 오면 제한 시간 콜백을 지운다.
     */
    private inner class OnceCallback(private val callback: (TranslationResult) -> Unit) :
        (TranslationResult) -> Unit {
        private var done = false

        /** 결과보다 제한 시간이 먼저 왔을 때 할 정리. */
        var onTimeout: (() -> Unit)? = null

        init {
            mainHandler.postDelayed(
                {
                    if (!done && !closed) onTimeout?.invoke()
                    invoke(failure(TranslationResult.Reason.ERROR, "timeout"))
                },
                this,
                TIMEOUT_MS,
            )
        }

        override fun invoke(result: TranslationResult) {
            if (done || closed) return
            done = true
            mainHandler.removeCallbacksAndMessages(this)
            callback(result)
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L

        fun failure(reason: TranslationResult.Reason, detail: String? = null) =
            TranslationResult.Failure(reason, detail)
    }
}

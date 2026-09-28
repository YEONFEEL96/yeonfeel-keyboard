package dev.badalab.yeonfeel.translate.mlkit

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.os.SystemClock
import dev.badalab.yeonfeel.translate.protocol.BundleFields
import dev.badalab.yeonfeel.translate.protocol.FailureCode
import dev.badalab.yeonfeel.translate.protocol.TranslateProtocol
import dev.badalab.yeonfeel.translate.protocol.TranslateRequest
import dev.badalab.yeonfeel.translate.protocol.TranslateResponse

/**
 * 키보드 전용 번역 서비스 ([TranslateProtocol] 참고). 매니페스트에서 서명 권한
 * [TranslateProtocol.PERMISSION]으로 보호돼, 같은 키로 서명한 앱만 바인드해 Messenger를 얻는다.
 *
 * 메시지는 메인 스레드에서 처리한다 — ML Kit Task 리스너도 메인 스레드에서 불린다.
 * 요청마다 응답은 정확히 한 번 보낸다(서비스가 끝나면 보내지 않는다). 원문·번역문은 로그에 남기지 않는다.
 */
class TranslateService : Service() {

    private val handler = Handler(Looper.getMainLooper()) { message ->
        handle(message)
        true
    }
    private val messenger = Messenger(handler)
    private var engine: MlKitEngine? = null
    private var inFlight = 0
    private var destroyed = false
    private val onModelsChanged: () -> Unit = { engine?.invalidateModels() }

    override fun onCreate() {
        super.onCreate()
        MlKitSetup.addModelListener(onModelsChanged)
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onDestroy() {
        destroyed = true
        handler.removeCallbacksAndMessages(null)
        MlKitSetup.removeModelListener(onModelsChanged)
        engine?.close()
        engine = null
        super.onDestroy()
    }

    private fun handle(message: Message) {
        if (destroyed || message.what != TranslateProtocol.MSG_TRANSLATE) return
        val replyTo = message.replyTo ?: return
        val requestId = message.arg1
        val request = when (val decoded = TranslateRequest.decode(BundleFields(message.data))) {
            is TranslateRequest.Decoded.Invalid -> return reply(replyTo, requestId, decoded.response)
            is TranslateRequest.Decoded.Valid -> decoded.request
        }
        // 키보드는 입력할 때마다 요청하지만 결과를 기다리는 요청은 몇 개뿐이다 — 멈춘 라이브러리 호출이
        // 쌓여 메모리를 잡아먹지 않도록 동시 요청 수를 제한한다.
        if (inFlight >= MAX_IN_FLIGHT) {
            return reply(replyTo, requestId, TranslateResponse.Failure(FailureCode.BUSY))
        }
        inFlight++
        val translator = engine ?: MlKitEngine(this).also { engine = it }
        var answered = false
        val token = Any()
        val finish = { response: TranslateResponse ->
            if (!answered) {
                answered = true
                inFlight--
                handler.removeCallbacksAndMessages(token)
                reply(replyTo, requestId, response)
            }
        }
        // 라이브러리 호출이 끝나지 않아도 자리를 비우고 한 번은 답한다.
        handler.postAtTime(
            { finish(TranslateResponse.Failure(FailureCode.ERROR, "timeout")) },
            token,
            SystemClock.uptimeMillis() + REQUEST_TIMEOUT_MS,
        )
        translator.translate(request.text, request.source, request.target, request.allowMeteredDownload, finish)
    }

    private fun reply(replyTo: Messenger, requestId: Int, response: TranslateResponse) {
        if (destroyed) return
        val message = Message.obtain(null, TranslateProtocol.MSG_RESULT, requestId, 0).apply {
            data = Bundle().also { response.encode(BundleFields(it)) }
        }
        try {
            replyTo.send(message)
        } catch (_: RemoteException) {
            // 키보드 프로세스가 사라졌다 — 받을 쪽이 없으니 버린다.
        }
    }

    private companion object {
        const val MAX_IN_FLIGHT = 16

        /** 키보드의 요청 제한 시간(30초)과 같게 잡는다. */
        const val REQUEST_TIMEOUT_MS = 30_000L
    }
}

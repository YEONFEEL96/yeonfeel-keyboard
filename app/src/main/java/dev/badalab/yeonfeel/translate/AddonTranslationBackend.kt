package dev.badalab.yeonfeel.translate

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import dev.badalab.yeonfeel.translate.protocol.BundleFields
import dev.badalab.yeonfeel.translate.protocol.FailureCode
import dev.badalab.yeonfeel.translate.protocol.TranslateProtocol
import dev.badalab.yeonfeel.translate.protocol.TranslateRequest
import dev.badalab.yeonfeel.translate.protocol.TranslateResponse

/**
 * ML Kit 애드온 앱에 번역을 맡긴다 (#25). 키보드는 INTERNET 권한이 없고, 네트워크(언어 모델 다운로드)와
 * ML Kit 네이티브 엔진은 같은 키로 서명한 애드온([TranslateProtocol.ADDON_PACKAGE])이 가진다.
 * 애드온 서비스는 서명 권한으로 보호돼, 입력 문장은 우리가 서명한 애드온에만 건너간다.
 *
 * - 첫 요청 때 명시적 인텐트로 바인드하고 [close]에서 푼다. 연결되기 전 요청은 모아 두었다가 보낸다.
 * - 애드온이 없거나 바인드가 거절되면 [TranslationResult.Reason.ADDON_NOT_INSTALLED].
 * - 애드온 프로세스가 죽으면 답을 기다리던 요청은 한 번씩 [TranslationResult.Reason.ERROR]로 끝난다.
 *   시스템이 다시 연결해 주면 이후 요청은 그대로 이어진다.
 * - 응답이 오지 않는 경우는 [TimeoutBackend]가 끝낸다.
 *
 * 메인 스레드에서만 쓰므로 동기화가 필요 없다 (ServiceConnection 콜백과 응답 Handler도 메인 스레드).
 */
internal class AddonTranslationBackend(
    context: Context,
    private val allowMeteredDownload: Boolean,
) : TranslationBackend {

    private class Outgoing(val id: Int, val request: TranslateRequest, val callback: (TranslationResult) -> Unit)

    private val appContext = context.applicationContext
    private val replies = Messenger(
        Handler(Looper.getMainLooper()) { message ->
            onReply(message)
            true
        },
    )
    private var connection: Connection? = null
    private var service: Messenger? = null

    /** 애드온에 보내고 답을 기다리는 요청. */
    private val pending = LinkedHashMap<Int, (TranslationResult) -> Unit>()

    /** 연결되기를 기다리는 요청. */
    private val waiting = mutableListOf<Outgoing>()
    private var lastId = 0
    private var closed = false

    override fun translate(
        text: String,
        source: TranslationLanguage,
        target: TranslationLanguage,
        callback: (TranslationResult) -> Unit,
    ) {
        if (closed) return
        if (source == target) return callback(TranslationResult.Success(text))
        // 잘라 보내지 않는다 — 끝부분이 말없이 사라지므로. 애드온도 같은 한도로 거절한다.
        if (text.length > TranslateProtocol.MAX_TEXT_LENGTH) {
            return callback(TranslationResult.Failure(TranslationResult.Reason.TEXT_TOO_LONG))
        }
        lastId = if (lastId == Int.MAX_VALUE) 1 else lastId + 1
        val outgoing = Outgoing(lastId, TranslateRequest(text, source.code, target.code, allowMeteredDownload), callback)
        service?.let { return send(it, outgoing) }
        if (connection == null && !bind()) {
            return callback(TranslationResult.Failure(TranslationResult.Reason.ADDON_NOT_INSTALLED))
        }
        waiting += outgoing
    }

    /** 애드온 서비스에 바인드한다. 패키지가 없거나(바인드 false) 권한이 없으면(SecurityException) false. */
    private fun bind(): Boolean {
        val intent = Intent().setComponent(ComponentName(TranslateProtocol.ADDON_PACKAGE, TranslateProtocol.SERVICE_CLASS))
        val created = Connection()
        val bound = try {
            appContext.bindService(intent, created, Context.BIND_AUTO_CREATE)
        } catch (_: SecurityException) {
            false
        }
        if (!bound) {
            // bindService가 false여도 연결 기록이 남을 수 있어 풀어 둔다.
            runCatching { appContext.unbindService(created) }
            return false
        }
        connection = created
        return true
    }

    private fun send(target: Messenger, outgoing: Outgoing) {
        val message = Message.obtain(null, TranslateProtocol.MSG_TRANSLATE, outgoing.id, 0).apply {
            data = Bundle().also { outgoing.request.encode(BundleFields(it)) }
            replyTo = replies
        }
        pending[outgoing.id] = outgoing.callback
        try {
            target.send(message)
        } catch (_: RemoteException) {
            // 애드온 프로세스가 막 죽었다. 연결 끊김 콜백이 뒤따른다.
            pending.remove(outgoing.id)
            deliver(outgoing.callback, TranslationResult.Failure(TranslationResult.Reason.ERROR, "add-on unreachable"))
        }
    }

    private fun onReply(message: Message) {
        if (closed || message.what != TranslateProtocol.MSG_RESULT) return
        val callback = pending.remove(message.arg1) ?: return
        deliver(callback, TranslateResponse.decode(BundleFields(message.data)).toTranslationResult())
    }

    /** [close] 뒤에는 부르지 않는다 — 앞선 콜백 안에서 닫혔을 수 있다. */
    private fun deliver(callback: (TranslationResult) -> Unit, result: TranslationResult) {
        if (!closed) callback(result)
    }

    /** 답을 기다리던 요청을 한 번씩 실패로 끝낸다. [andWaiting]이면 연결을 기다리던 요청도. */
    private fun failPending(result: TranslationResult, andWaiting: Boolean) {
        val callbacks = pending.values.toList()
        pending.clear()
        val queued = if (andWaiting) waiting.map { it.callback }.also { waiting.clear() } else emptyList()
        (callbacks + queued).forEach { deliver(it, result) }
    }

    /** 연결을 버린다. 다음 요청이 다시 바인드한다. */
    private fun dropConnection(dead: Connection) {
        runCatching { appContext.unbindService(dead) }
        connection = null
        service = null
    }

    override fun close() {
        closed = true
        pending.clear()
        waiting.clear()
        connection?.let { runCatching { appContext.unbindService(it) } }
        connection = null
        service = null
    }

    /** 바인드마다 새로 만든다 — 버린 연결의 늦은 콜백은 무시한다. */
    private inner class Connection : ServiceConnection {
        private val current get() = !closed && connection === this

        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (!current || binder == null) return
            val connected = Messenger(binder)
            service = connected
            val queued = waiting.toList()
            waiting.clear()
            queued.forEach { if (!closed) send(connected, it) }
        }

        /** 애드온 프로세스가 죽었다. 바인드는 남아 있어 시스템이 다시 띄우면 onServiceConnected가 온다. */
        override fun onServiceDisconnected(name: ComponentName?) {
            if (!current) return
            service = null
            failPending(TranslationResult.Failure(TranslationResult.Reason.ERROR, "add-on disconnected"), andWaiting = false)
        }

        /** 애드온이 업데이트·삭제돼 이 바인드는 다시 이어지지 않는다. */
        override fun onBindingDied(name: ComponentName?) {
            if (!current) return
            dropConnection(this)
            failPending(TranslationResult.Failure(TranslationResult.Reason.ERROR, "add-on binding died"), andWaiting = true)
        }

        /** 서비스가 바인더를 주지 않았다 — 번역을 제공하지 않는 패키지다. */
        override fun onNullBinding(name: ComponentName?) {
            if (!current) return
            dropConnection(this)
            failPending(TranslationResult.Failure(TranslationResult.Reason.ADDON_NOT_INSTALLED), andWaiting = true)
        }
    }
}

/** 애드온 응답을 키보드 결과로. */
internal fun TranslateResponse.toTranslationResult(): TranslationResult = when (this) {
    is TranslateResponse.Success -> TranslationResult.Success(text)
    is TranslateResponse.Failure -> TranslationResult.Failure(code.toReason(), detail)
}

/** 실패 코드는 [TranslationResult.Reason]과 1:1로 대응한다 (같은 이름). */
internal fun FailureCode.toReason(): TranslationResult.Reason = when (this) {
    FailureCode.ENGINE_UNAVAILABLE -> TranslationResult.Reason.ENGINE_UNAVAILABLE
    FailureCode.LANGUAGE_UNSUPPORTED -> TranslationResult.Reason.LANGUAGE_UNSUPPORTED
    FailureCode.NEEDS_DOWNLOAD -> TranslationResult.Reason.NEEDS_DOWNLOAD
    FailureCode.NEEDS_WIFI -> TranslationResult.Reason.NEEDS_WIFI
    FailureCode.DOWNLOADING -> TranslationResult.Reason.DOWNLOADING
    FailureCode.BLOCKED_IN_BACKGROUND -> TranslationResult.Reason.BLOCKED_IN_BACKGROUND
    FailureCode.ADDON_NOT_INSTALLED -> TranslationResult.Reason.ADDON_NOT_INSTALLED
    FailureCode.BUSY -> TranslationResult.Reason.BUSY
    FailureCode.TEXT_TOO_LONG -> TranslationResult.Reason.TEXT_TOO_LONG
    FailureCode.CANCELLED -> TranslationResult.Reason.CANCELLED
    FailureCode.ERROR -> TranslationResult.Reason.ERROR
}

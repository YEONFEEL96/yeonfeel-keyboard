package dev.badalab.yeonfeel.translate

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.genai.common.DownloadCallback
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.java.GenerativeModelFutures
import java.util.concurrent.Executor

/**
 * Gemini Nano (AICore) Prompt API 번역. AICore는 추론을 "화면 맨 앞 앱"에만 허용하는데,
 * 키보드는 다른 앱 위에 뜬 서비스라 기기에 따라 [GenAiException.ErrorCode.BACKGROUND_USE_BLOCKED]로
 * 막힐 수 있다 — 그 경우 [TranslationResult.Reason.BLOCKED_IN_BACKGROUND]로 알린다.
 *
 * 코루틴 대신 ListenableFuture API를 쓰고, 결과는 모두 메인 스레드에서 푼다.
 */
@RequiresApi(Build.VERSION_CODES.O)
internal class GeminiNanoTranslationBackend(context: Context) : TranslationBackend {

    private val mainExecutor: Executor = ContextCompat.getMainExecutor(context)
    private var model: GenerativeModel? = null
    private var futures: GenerativeModelFutures? = null

    /** checkStatus가 AVAILABLE이었다. 모델이 사라졌다는 오류가 오면 다시 확인한다. */
    private var ready = false
    private var downloading = false

    /** 실패한 다운로드. 다음 요청에 한 번 알리고 지운다 — 그다음 요청은 다시 받는다. */
    private var downloadError: Throwable? = null
    private var closed = false

    override fun translate(
        text: String,
        source: TranslationLanguage,
        target: TranslationLanguage,
        callback: (TranslationResult) -> Unit,
    ) {
        if (closed) return
        if (source == target) return callback(TranslationResult.Success(text))
        val client = client() ?: return callback(failure(TranslationResult.Reason.ENGINE_UNAVAILABLE))
        if (ready) {
            generate(client, text, source, target, callback)
            return
        }
        downloadError?.let {
            downloadError = null
            return callback(classify(it))
        }
        if (downloading) return callback(failure(TranslationResult.Reason.DOWNLOADING))
        onDone({ client.checkStatus() }, callback) { status ->
            when (status) {
                FeatureStatus.AVAILABLE -> {
                    ready = true
                    generate(client, text, source, target, callback)
                }
                // 시스템이 이미 받는 중(DOWNLOADING)이어도 download()로 끝을 알아낸다.
                FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING -> {
                    startDownload(client)
                    callback(failure(TranslationResult.Reason.DOWNLOADING))
                }
                else -> callback(failure(TranslationResult.Reason.ENGINE_UNAVAILABLE))
            }
        }
    }

    private fun client(): GenerativeModelFutures? {
        futures?.let { return it }
        return runCatching {
            val created = Generation.getClient()
            model = created
            GenerativeModelFutures.from(created)
        }.getOrNull()?.also { futures = it }
    }

    private fun startDownload(client: GenerativeModelFutures) {
        if (downloading) return
        downloading = true
        val future = try {
            client.download(object : DownloadCallback {})
        } catch (e: Exception) {
            downloading = false
            downloadError = e
            return
        }
        future.addListener(
            {
                if (closed) return@addListener
                downloading = false
                runCatching { future.get() }
                    .onSuccess { ready = true }
                    .onFailure { downloadError = it }
            },
            mainExecutor,
        )
    }

    private fun generate(
        client: GenerativeModelFutures,
        text: String,
        source: TranslationLanguage,
        target: TranslationLanguage,
        callback: (TranslationResult) -> Unit,
    ) {
        onDone(
            {
                val request = GenerateContentRequest.builder(TextPart(GeminiPrompt.build(text, source, target)))
                    .apply {
                        // 번역은 창의성이 필요 없다 — 같은 입력에 같은 결과가 나오게 한다.
                        temperature = 0f
                        topK = 1
                        maxOutputTokens = MAX_OUTPUT_TOKENS
                    }
                    .build()
                client.generateContent(request)
            },
            callback,
        ) { response ->
            val output = response.candidates.firstOrNull()?.text
                ?.let { GeminiPrompt.clean(it, text) }
            if (output.isNullOrBlank()) {
                callback(failure(TranslationResult.Reason.ERROR, "empty response"))
            } else {
                callback(TranslationResult.Success(output))
            }
        }
    }

    /**
     * [start]가 만든 future를 메인 스레드에서 풀어 성공이면 [onSuccess], 실패면 이유를 분류해 [callback].
     * 요청을 만들거나 보내다 바로 던진 예외도 같은 실패로 다룬다. close() 뒤에는 아무것도 부르지 않는다.
     */
    private fun <T> onDone(
        start: () -> ListenableFuture<T>,
        callback: (TranslationResult) -> Unit,
        onSuccess: (T) -> Unit,
    ) {
        val future = try {
            start()
        } catch (e: Exception) {
            return fail(e, callback)
        }
        future.addListener(
            {
                if (closed) return@addListener
                // get()은 실패를 ExecutionException으로 감싸 던진다 — 분류할 때 원인을 찾아 푼다.
                runCatching { future.get() }.fold(onSuccess) { fail(it, callback) }
            },
            mainExecutor,
        )
    }

    private fun fail(error: Throwable, callback: (TranslationResult) -> Unit) {
        val result = classify(error)
        // 모델이 내려갔거나 AICore가 바뀌었다 — 다음 요청은 상태부터 다시 확인한다.
        if (result.reason == TranslationResult.Reason.ENGINE_UNAVAILABLE) ready = false
        callback(result)
    }

    private fun classify(error: Throwable): TranslationResult.Failure {
        val genAi = generateSequence(error) { it.cause }.filterIsInstance<GenAiException>().firstOrNull()
            ?: return failure(TranslationResult.Reason.ERROR, error.message)
        return failure(GeminiPrompt.failureReason(genAi.errorCode), "code ${genAi.errorCode}")
    }

    override fun close() {
        closed = true
        runCatching { model?.close() }
        model = null
        futures = null
    }

    private companion object {
        const val MAX_OUTPUT_TOKENS = 1024

        fun failure(reason: TranslationResult.Reason, detail: String? = null) =
            TranslationResult.Failure(reason, detail)
    }
}

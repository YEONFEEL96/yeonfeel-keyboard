package dev.badalab.yeonfeel.translate.mlkit

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import dev.badalab.yeonfeel.translate.protocol.FailureCode
import dev.badalab.yeonfeel.translate.protocol.TranslateResponse

/**
 * ML Kit 번역 (키보드에서 옮겨 온 로직). 번역은 기기 안에서 하고, 네트워크는 언어 모델(약 30MB)을
 * 처음 받을 때만 쓴다. 모델이 없으면 다운로드를 시작하고 곧바로 [FailureCode.DOWNLOADING]을 돌려준다 —
 * 키보드가 잠시 뒤 다시 요청한다. 요청의 allowMeteredDownload가 false면 Wi-Fi에서만 받고, 모델이 없는
 * 동안 Wi-Fi가 아니면 [FailureCode.NEEDS_WIFI]를 돌려준다 — 다운로드 조건(requireWifi)과 같은
 * 기준이라 Wi-Fi를 기다리며 DOWNLOADING만 되풀이하는 일이 없다.
 *
 * 다운로드는 번역기가 아니라 [RemoteModelManager]로 언어별로 한다 — 언어 쌍이 바뀌어 번역기를
 * 닫아도 받던 모델은 계속 받는다. Task 리스너는 따로 지정하지 않으면 메인 스레드에서 불린다.
 *
 * 메인 스레드에서만 쓴다. 콜백은 요청마다 정확히 한 번, [close] 뒤에는 불리지 않는다.
 * 원문·번역문은 로그에 남기지 않는다.
 */
internal class MlKitEngine(context: Context) {

    init {
        // 매니페스트에서 ML Kit 시작 프로바이더를 뺐다 — 처음 쓸 때 초기화한다 (RemoteModelManager보다 먼저).
        MlKitSetup.ensureInitialized(context)
    }

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val models = RemoteModelManager.getInstance()
    private var translator: Translator? = null
    private var translatorPair: Pair<String, String>? = null
    private val readyPairs = mutableSetOf<Pair<String, String>>()

    /** 내려받는 중인 언어(ML Kit 언어 코드). */
    private val downloading = mutableSetOf<String>()

    /** 실패한 다운로드와 그 이유. 한 번 알리고 지워 다음 요청에서 다시 받게 한다. */
    private val failedDownloads = mutableMapOf<String, String?>()
    private var closed = false

    fun translate(
        text: String,
        sourceCode: String,
        targetCode: String,
        allowMeteredDownload: Boolean,
        callback: (TranslateResponse) -> Unit,
    ) {
        if (closed) return
        val sourceTag = TranslateLanguage.fromLanguageTag(sourceCode)
        val targetTag = TranslateLanguage.fromLanguageTag(targetCode)
        if (sourceTag == null || targetTag == null) {
            callback(TranslateResponse.Failure(FailureCode.LANGUAGE_UNSUPPORTED))
            return
        }
        if (sourceTag == targetTag) return callback(TranslateResponse.Success(text))
        val pair = sourceTag to targetTag
        if (pair in readyPairs) {
            run(pair, text, callback)
            return
        }
        missingModels(listOf(sourceTag, targetTag)).addOnCompleteListener { check ->
            if (closed) return@addOnCompleteListener
            if (!check.isSuccessful) {
                callback(TranslateResponse.Failure(FailureCode.ERROR, check.exception?.message))
                return@addOnCompleteListener
            }
            val missing = check.result
            if (missing.isEmpty()) {
                readyPairs += pair
                run(pair, text, callback)
                return@addOnCompleteListener
            }
            val failed = missing.filter { it in failedDownloads }
            if (failed.isNotEmpty()) {
                val detail = failed.firstNotNullOfOrNull { failedDownloads[it] }
                failed.forEach { failedDownloads -= it }
                callback(TranslateResponse.Failure(FailureCode.ERROR, "download failed: ${detail ?: "unknown"}"))
                return@addOnCompleteListener
            }
            // 이미 받는 중이어도 Wi-Fi가 끊겼으면 다운로드는 Wi-Fi를 기다린다 — 그대로 알린다.
            if (!allowMeteredDownload && !isOnWifi()) {
                callback(TranslateResponse.Failure(FailureCode.NEEDS_WIFI))
                return@addOnCompleteListener
            }
            missing.filter { it !in downloading }.forEach { startDownload(it, allowMeteredDownload) }
            callback(TranslateResponse.Failure(FailureCode.DOWNLOADING))
        }
    }

    /** 기기에 없는 언어 모델. 영어가 내장이면 영어는 목록에 오르지 않는다. */
    private fun missingModels(tags: List<String>): Task<List<String>> {
        val distinct = tags.distinct()
        val checks = distinct.map { models.isModelDownloaded(TranslateRemoteModel.Builder(it).build()) }
        // whenAllSuccess의 결과는 입력 순서를 따른다. 하나라도 실패하면 이어지는 Task도 실패한다.
        return Tasks.whenAllSuccess<Boolean>(checks).continueWith { task ->
            val downloaded = task.result
            distinct.filterIndexed { i, _ -> downloaded[i] != true }
        }
    }

    /** 현재 네트워크가 Wi-Fi인지 — [DownloadConditions.Builder.requireWifi]와 같은 기준. 알 수 없으면 false. */
    private fun isOnWifi(): Boolean = runCatching {
        val network = connectivity?.activeNetwork ?: return@runCatching false
        connectivity.getNetworkCapabilities(network)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }.getOrDefault(false)

    private fun startDownload(tag: String, allowMeteredDownload: Boolean) {
        downloading += tag
        val conditions = DownloadConditions.Builder()
            // 받는 도중 셀룰러로 바뀌어도 키보드가 Wi-Fi 전용을 요청했으면 다른 네트워크를 쓰지 않는다.
            .apply { if (!allowMeteredDownload) requireWifi() }
            .build()
        models.download(TranslateRemoteModel.Builder(tag).build(), conditions)
            .addOnCompleteListener { download ->
                downloading -= tag
                if (!download.isSuccessful) failedDownloads[tag] = download.exception?.message
            }
    }

    private fun run(pair: Pair<String, String>, text: String, callback: (TranslateResponse) -> Unit) {
        translatorFor(pair).translate(text).addOnCompleteListener {
            if (closed) return@addOnCompleteListener
            if (it.isSuccessful) {
                callback(TranslateResponse.Success(it.result.orEmpty()))
            } else {
                // 모델이 지워졌을 수 있다 — 다음 요청에서 다시 확인한다.
                readyPairs -= pair
                callback(TranslateResponse.Failure(FailureCode.ERROR, it.exception?.message))
            }
        }
    }

    /** 현재 언어 쌍의 번역기. 쌍이 바뀌면 이전 번역기를 닫는다 (그 번역기의 진행 중 요청은 실패로 끝난다). */
    private fun translatorFor(pair: Pair<String, String>): Translator {
        translator?.takeIf { translatorPair == pair }?.let { return it }
        translator?.close()
        val created = Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(pair.first)
                .setTargetLanguage(pair.second)
                .build(),
        )
        translator = created
        translatorPair = pair
        return created
    }

    /** 모델 관리 화면에서 모델을 지웠다 — 준비된 쌍 캐시와 번역기를 비워 다음 요청에서 다시 확인한다. */
    fun invalidateModels() {
        readyPairs.clear()
        translator?.close()
        translator = null
        translatorPair = null
    }

    fun close() {
        closed = true
        translator?.close()
        translator = null
        translatorPair = null
    }
}

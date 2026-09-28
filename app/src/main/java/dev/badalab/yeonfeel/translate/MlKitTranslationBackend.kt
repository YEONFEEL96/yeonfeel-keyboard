package dev.badalab.yeonfeel.translate

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.common.sdkinternal.MlKitContext
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions

/**
 * ML Kit 번역. 번역은 기기 안에서 하고, 네트워크는 언어 모델(약 30MB)을 처음 받을 때만 쓴다.
 * 모델이 없으면 다운로드를 시작하고 곧바로 [TranslationResult.Reason.DOWNLOADING]을 돌려준다 —
 * 호출하는 쪽이 잠시 뒤 다시 요청한다. [allowMeteredDownload]가 false면 Wi-Fi에서만 받고,
 * 모델이 없는 동안 Wi-Fi가 아니면 [TranslationResult.Reason.NEEDS_WIFI]를 돌려준다 — 다운로드 조건
 * (requireWifi)과 같은 기준이라 Wi-Fi를 기다리며 DOWNLOADING만 되풀이하는 일이 없다.
 *
 * 다운로드는 번역기가 아니라 [RemoteModelManager]로 언어별로 한다 — 언어 쌍이 바뀌어 번역기를
 * 닫아도 받던 모델은 계속 받는다. Task 리스너는 따로 지정하지 않으면 메인 스레드에서 불린다.
 */
internal class MlKitTranslationBackend(
    context: Context,
    private val allowMeteredDownload: Boolean,
) : TranslationBackend {

    init {
        // 매니페스트에서 ML Kit 시작 프로바이더를 뺐다 — 기본 엔진(시스템 번역)만 쓰는 사용자는
        // 키보드 프로세스가 뜰 때마다 ML Kit·전송 라이브러리를 초기화할 이유가 없다. 이 엔진을 처음
        // 만들 때 초기화한다 (아래 RemoteModelManager보다 먼저).
        MlKitContext.initializeIfNeeded(context.applicationContext)
    }

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val models = RemoteModelManager.getInstance()
    private var translator: Translator? = null
    private var translatorPair: Pair<TranslationLanguage, TranslationLanguage>? = null
    private val readyPairs = mutableSetOf<Pair<TranslationLanguage, TranslationLanguage>>()

    /** 내려받는 중인 언어(ML Kit 언어 코드). */
    private val downloading = mutableSetOf<String>()

    /** 실패한 다운로드와 그 이유. 한 번 알리고 지워 다음 요청에서 다시 받게 한다. */
    private val failedDownloads = mutableMapOf<String, String?>()
    private var closed = false

    override fun translate(
        text: String,
        source: TranslationLanguage,
        target: TranslationLanguage,
        callback: (TranslationResult) -> Unit,
    ) {
        if (closed) return
        if (source == target) return callback(TranslationResult.Success(text))
        val sourceTag = TranslateLanguage.fromLanguageTag(source.code)
        val targetTag = TranslateLanguage.fromLanguageTag(target.code)
        if (sourceTag == null || targetTag == null) {
            callback(failure(TranslationResult.Reason.LANGUAGE_UNSUPPORTED))
            return
        }
        val pair = source to target
        if (pair in readyPairs) {
            run(pair, sourceTag, targetTag, text, callback)
            return
        }
        missingModels(listOf(sourceTag, targetTag)).addOnCompleteListener { check ->
            if (closed) return@addOnCompleteListener
            if (!check.isSuccessful) {
                callback(failure(TranslationResult.Reason.ERROR, check.exception?.message))
                return@addOnCompleteListener
            }
            val missing = check.result
            if (missing.isEmpty()) {
                readyPairs += pair
                run(pair, sourceTag, targetTag, text, callback)
                return@addOnCompleteListener
            }
            val failed = missing.filter { it in failedDownloads }
            if (failed.isNotEmpty()) {
                val detail = failed.firstNotNullOfOrNull { failedDownloads[it] }
                failed.forEach { failedDownloads -= it }
                callback(failure(TranslationResult.Reason.ERROR, "download failed: ${detail ?: "unknown"}"))
                return@addOnCompleteListener
            }
            // 이미 받는 중이어도 Wi-Fi가 끊겼으면 다운로드는 Wi-Fi를 기다린다 — 그대로 알린다.
            if (!allowMeteredDownload && !isOnWifi()) {
                callback(failure(TranslationResult.Reason.NEEDS_WIFI))
                return@addOnCompleteListener
            }
            missing.filter { it !in downloading }.forEach(::startDownload)
            callback(failure(TranslationResult.Reason.DOWNLOADING))
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

    private fun startDownload(tag: String) {
        downloading += tag
        val conditions = DownloadConditions.Builder()
            // 받는 도중 셀룰러로 바뀌어도 키보드에서는 Wi-Fi가 아닌 네트워크를 쓰지 않는다.
            .apply { if (!allowMeteredDownload) requireWifi() }
            .build()
        models.download(TranslateRemoteModel.Builder(tag).build(), conditions)
            .addOnCompleteListener { download ->
                downloading -= tag
                if (!download.isSuccessful) failedDownloads[tag] = download.exception?.message
            }
    }

    private fun run(
        pair: Pair<TranslationLanguage, TranslationLanguage>,
        sourceTag: String,
        targetTag: String,
        text: String,
        callback: (TranslationResult) -> Unit,
    ) {
        translatorFor(pair, sourceTag, targetTag).translate(text).addOnCompleteListener {
            if (closed) return@addOnCompleteListener
            if (it.isSuccessful) {
                callback(TranslationResult.Success(it.result.orEmpty()))
            } else {
                // 모델이 지워졌을 수 있다 — 다음 요청에서 다시 확인한다.
                readyPairs -= pair
                callback(failure(TranslationResult.Reason.ERROR, it.exception?.message))
            }
        }
    }

    /** 현재 언어 쌍의 번역기. 쌍이 바뀌면 이전 번역기를 닫는다 (그 번역기의 진행 중 요청은 실패로 끝난다). */
    private fun translatorFor(
        pair: Pair<TranslationLanguage, TranslationLanguage>,
        sourceTag: String,
        targetTag: String,
    ): Translator {
        translator?.takeIf { translatorPair == pair }?.let { return it }
        translator?.close()
        val created = Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(sourceTag)
                .setTargetLanguage(targetTag)
                .build(),
        )
        translator = created
        translatorPair = pair
        return created
    }

    override fun close() {
        closed = true
        translator?.close()
        translator = null
        translatorPair = null
    }

    private companion object {
        fun failure(reason: TranslationResult.Reason, detail: String? = null) =
            TranslationResult.Failure(reason, detail)
    }
}

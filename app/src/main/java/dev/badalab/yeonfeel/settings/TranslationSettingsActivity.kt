package dev.badalab.yeonfeel.settings

import android.app.Activity
import android.app.ActivityOptions
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.View
import android.view.translation.TranslationManager
import androidx.annotation.RequiresApi
import dev.badalab.yeonfeel.R
import dev.badalab.yeonfeel.translate.Availability
import dev.badalab.yeonfeel.translate.GeminiBlockFlag
import dev.badalab.yeonfeel.translate.TranslationBackend
import dev.badalab.yeonfeel.translate.TranslationEngine
import dev.badalab.yeonfeel.translate.TranslationLanguage
import dev.badalab.yeonfeel.translate.TranslationResult

/**
 * 번역 설정: 엔진·언어 선택, 엔진별 사용 가능 상태, 번역 테스트(모델 다운로드 겸), 시스템 번역 언어 팩.
 *
 * 설정 화면은 화면 맨 앞 앱이라 여기서만 ML Kit·Gemini Nano 모델을 받는다 (어떤 네트워크든 허용).
 * 엔진 상태 확인은 조회만 하고 다운로드는 시작하지 않는다 — 다운로드는 사용자가 테스트를 눌렀을 때만.
 */
class TranslationSettingsActivity : Activity() {

    private lateinit var settings: KeyboardSettings
    private lateinit var geminiBlock: GeminiBlockFlag
    private val handler = Handler(Looper.getMainLooper())

    /** 엔진별 상태 확인 결과. 확인 중이면 없다. */
    private val availability = mutableMapOf<TranslationEngine, Availability>()

    /** 상태 확인에 쓰는 엔진들. 확인 결과가 오면(또는 화면을 떠나면) 닫는다. */
    private val checkBackends = mutableListOf<TranslationBackend>()

    /** 결과를 전한 뒤 닫기로 한 엔진들. 닫기 전에 화면이 없어져도 [onDestroy]에서 닫는다. */
    private val closing = mutableListOf<TranslationBackend>()

    /** 한 번 그리는 동안 쓰는 애드온 설치 여부·Gemini 차단 기록 (둘 다 PackageManager 조회). */
    private var addonInstalled = false
    private var geminiBlocked = false

    /** 낡은 확인 결과를 버리기 위한 세대 번호 — 엔진·언어가 바뀌면 새로 확인한다. */
    private var checkGeneration = 0

    /** 진행 중인 번역 테스트의 엔진. 없으면 테스트 중이 아니다. */
    private var testBackend: TranslationBackend? = null
    private var testDeadline = 0L

    /** 테스트 행 아래에 보여줄 결과·진행 상태. null이면 안내 문구. */
    private var testMessage: String? = null

    /** 화면이 앞에 있는지. 떠나 있는 동안에는 상태 확인을 하지 않는다 (돌아오면 다시 확인). */
    private var resumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = KeyboardSettings(this)
        geminiBlock = GeminiBlockFlag(this, settings)
        title = getString(R.string.translate_menu)
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        // 시스템 설정에서 언어 팩을 받았거나 애드온을 설치하고 돌아왔을 수 있다 — 다시 확인한다.
        checkAvailability()
    }

    override fun onPause() {
        super.onPause()
        resumed = false
        cancelChecks()
        // Gemini Nano는 화면 맨 앞 앱에서만 돌므로 떠난 뒤의 재시도는 막힐 뿐이다. 모델은 AICore가 계속 받는다.
        if (testBackend != null) {
            cancelTest()
            testMessage = null
            buildUi()
        }
    }

    override fun onDestroy() {
        cancelTest()
        cancelChecks()
        handler.removeCallbacksAndMessages(null)
        closing.forEach { it.close() }
        closing.clear()
        super.onDestroy()
    }

    private fun buildUi() {
        val ui = SettingComponents(this)
        ui.header(getString(R.string.translate_menu))

        val selected = settings.translationEngine
        addonInstalled = isAddonInstalled()
        geminiBlocked = geminiBlock.isBlocked()
        val addonMissing = engineStatus(TranslationEngine.MLKIT) == EngineStatus.ADDON_NOT_INSTALLED

        ui.caption(getString(R.string.translate_engine_caption))
        val radios = linkedMapOf<TranslationEngine, View>()
        TranslationEngine.entries.forEach { engine ->
            radios[engine] = ui.radioRow(engineLabel(engine), engine == selected)
        }
        ui.bindRadioGroup(radios) { engine ->
            if (settings.translationEngine == engine) return@bindRadioGroup
            settings.translationEngine = engine
            cancelTest()
            testMessage = null
            buildUi()
        }
        val engineRows = radios.values.toMutableList()
        if (addonMissing) {
            engineRows += ui.textRow(
                getString(R.string.translate_addon_download),
                getString(R.string.translate_addon_download_desc),
            ) { openUrl(getString(R.string.github_releases_url)) }
        }
        if (geminiBlocked) {
            engineRows += ui.textRow(
                getString(R.string.translate_gemini_retry),
                getString(R.string.translate_gemini_retry_desc),
            ) {
                geminiBlock.clear()
                buildUi()
            }
        }
        ui.card(*engineRows.toTypedArray())

        ui.caption(getString(R.string.translate_languages_caption))
        ui.card(
            ui.textRow(getString(R.string.translate_source), settings.translationSource.displayName()) {
                pickLanguage(pickSource = true)
            },
            ui.textRow(getString(R.string.translate_target), settings.translationTarget.displayName()) {
                pickLanguage(pickSource = false)
            },
        )

        val testRows = mutableListOf(
            ui.textRow(getString(R.string.translate_test), testMessage ?: getString(R.string.translate_test_hint)) {
                startTest()
            },
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            testRows += ui.textRow(getString(R.string.translate_system_settings)) { openSystemLanguagePacks() }
        }
        ui.card(*testRows.toTypedArray())

        if (selected == TranslationEngine.GEMINI_NANO) ui.caption(getString(R.string.translate_test_gemini_note))
        ui.caption(getString(R.string.translate_privacy_note))

        ui.show()
    }

    /** "엔진 이름 / 한 줄 설명 / 상태" — 설명·상태는 작은 글씨·보조색으로. */
    private fun engineLabel(engine: TranslationEngine): CharSequence {
        val text = SpannableStringBuilder(engineName(this, engine))
        val start = text.length
        text.append('\n').append(getString(engineDescRes(engine)))
        text.append('\n').append(getString(statusRes(engineStatus(engine))))
        text.setSpan(RelativeSizeSpan(0.76f), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        text.setSpan(ForegroundColorSpan(SettingComponents.SUB_TEXT), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return text
    }

    private fun engineStatus(engine: TranslationEngine): EngineStatus = engineStatus(
        engine = engine,
        availability = availability[engine],
        addonInstalled = addonInstalled,
        geminiBlocked = geminiBlocked,
    )

    private fun isAddonInstalled(): Boolean =
        runCatching { packageManager.getPackageInfo(MLKIT_ADDON_PACKAGE, 0) }.isSuccess

    /** 모든 엔진의 상태를 새로 확인한다. 조회만 하고 다운로드는 시작하지 않는다. */
    private fun checkAvailability() {
        cancelChecks()
        availability.clear()
        val generation = ++checkGeneration
        val source = settings.translationSource
        val target = settings.translationTarget
        TranslationEngine.entries.forEach { engine ->
            val backend = TranslationBackend.create(applicationContext, engine, allowMeteredDownload = false)
            checkBackends += backend
            backend.checkAvailability(source, target) { result ->
                checkBackends.remove(backend)
                closeLater(backend)
                if (generation != checkGeneration) return@checkAvailability
                availability[engine] = result
                buildUi()
            }
        }
        buildUi()
    }

    /** 콜백 안에서 바로 닫지 않는다 — 엔진이 콜백을 부른 뒤 할 정리가 남아 있을 수 있다. */
    private fun closeLater(backend: TranslationBackend) {
        closing += backend
        handler.post {
            if (closing.remove(backend)) backend.close()
        }
    }

    private fun cancelChecks() {
        checkGeneration++
        checkBackends.forEach { it.close() }
        checkBackends.clear()
    }

    private fun pickLanguage(pickSource: Boolean) {
        val languages = TranslationLanguage.entries
        val current = if (pickSource) settings.translationSource else settings.translationTarget
        AlertDialog.Builder(this)
            .setTitle(if (pickSource) R.string.translate_source else R.string.translate_target)
            .setSingleChoiceItems(
                languages.map { it.displayName() }.toTypedArray(),
                languages.indexOf(current),
            ) { dialog, which ->
                dialog.dismiss()
                val (source, target) = pickLanguagePair(
                    settings.translationSource,
                    settings.translationTarget,
                    pickSource,
                    languages[which],
                )
                if (source == settings.translationSource && target == settings.translationTarget) {
                    return@setSingleChoiceItems
                }
                settings.translationSource = source
                settings.translationTarget = target
                cancelTest()
                testMessage = null
                // 시스템 번역의 상태는 언어 쌍마다 다르다.
                checkAvailability()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * 선택한 엔진으로 원문 언어의 예문을 번역한다. 엔진은 어떤 네트워크에서든 모델을 받을 수 있게 만들고,
     * 결과가 "내려받는 중"이면 [TEST_DOWNLOAD_WAIT_MS]까지 [TEST_RETRY_MS] 간격으로 다시 시도한다.
     */
    private fun startTest() {
        cancelTest()
        val engine = settings.translationEngine
        val source = settings.translationSource
        val target = settings.translationTarget
        val backend = TranslationBackend.create(applicationContext, engine, allowMeteredDownload = true)
        testBackend = backend
        testDeadline = SystemClock.uptimeMillis() + TEST_DOWNLOAD_WAIT_MS
        testMessage = getString(R.string.translate_status_working)
        buildUi()
        attemptTest(backend, engine, source, target)
    }

    private fun attemptTest(
        backend: TranslationBackend,
        engine: TranslationEngine,
        source: TranslationLanguage,
        target: TranslationLanguage,
    ) {
        backend.translate(source.sample, source, target) { result ->
            if (testBackend !== backend) return@translate
            val downloading = result is TranslationResult.Failure &&
                result.reason == TranslationResult.Reason.DOWNLOADING
            if (downloading && SystemClock.uptimeMillis() < testDeadline) {
                testMessage = getString(R.string.translate_status_downloading)
                buildUi()
                handler.postDelayed(
                    { if (testBackend === backend) attemptTest(backend, engine, source, target) },
                    TEST_RETRY_MS,
                )
                return@translate
            }
            testBackend = null
            closeLater(backend)
            testMessage = testResultText(engine, result)
            // 테스트가 모델을 받았을 수 있다 — 상태를 다시 확인한다.
            if (resumed) checkAvailability() else buildUi()
        }
    }

    private fun cancelTest() {
        testBackend?.close()
        testBackend = null
    }

    private fun testResultText(engine: TranslationEngine, result: TranslationResult): String {
        val reason = when (result) {
            is TranslationResult.Success -> return result.text
            is TranslationResult.Failure -> result.reason
        }
        if (reason == TranslationResult.Reason.ENGINE_UNAVAILABLE) {
            return getString(R.string.translate_status_unavailable, engineName(this, engine))
        }
        return getString(
            when (reason) {
                TranslationResult.Reason.LANGUAGE_UNSUPPORTED -> R.string.translate_status_unsupported_pair
                TranslationResult.Reason.NEEDS_DOWNLOAD -> R.string.translate_test_needs_download
                // 시간이 다 되도록 받는 중 — 설정 화면에서는 어떤 네트워크든 허용하므로 Wi-Fi 대기도 같은 뜻.
                TranslationResult.Reason.NEEDS_WIFI,
                TranslationResult.Reason.DOWNLOADING,
                -> R.string.translate_test_still_downloading
                TranslationResult.Reason.BLOCKED_IN_BACKGROUND -> R.string.translate_status_blocked
                TranslationResult.Reason.ADDON_NOT_INSTALLED -> R.string.translate_status_addon_missing
                TranslationResult.Reason.BUSY -> R.string.translate_status_busy
                TranslationResult.Reason.TEXT_TOO_LONG -> R.string.translate_status_too_long
                else -> R.string.translate_status_error
            },
        )
    }

    /** 시스템 번역 언어 팩 화면. 시스템이 만든 PendingIntent라 화면 맨 앞인 이 앱이 대신 띄운다. */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun openSystemLanguagePacks() {
        runCatching {
            val manager = getSystemService(TranslationManager::class.java) ?: return
            val pending = manager.onDeviceTranslationSettingsActivityIntent ?: return
            val options = ActivityOptions.makeBasic()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                options.setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE,
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                @Suppress("DEPRECATION")
                options.setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                )
            }
            pending.send(this, 0, null, null, null, null, options.toBundle())
        }
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
        // 브라우저가 하나도 없는 기기에서도 크래시하지 않도록 방어한다.
        runCatching { startActivity(intent) }
    }

    companion object {
        /** ML Kit 애드온 앱 패키지 (#25). 매니페스트 <queries>에 넣어야 설치 여부가 보인다. */
        const val MLKIT_ADDON_PACKAGE = "dev.badalab.yeonfeel.translate.mlkit"

        /** 테스트가 모델 다운로드를 기다리는 최대 시간. */
        private const val TEST_DOWNLOAD_WAIT_MS = 120_000L
        private const val TEST_RETRY_MS = 3_000L

        /** 설정 목록 등에 보여줄 엔진 이름. */
        fun engineName(context: Context, engine: TranslationEngine): String = context.getString(
            when (engine) {
                TranslationEngine.SYSTEM -> R.string.translate_engine_system
                TranslationEngine.MLKIT -> R.string.translate_engine_mlkit
                TranslationEngine.GEMINI_NANO -> R.string.translate_engine_gemini
            },
        )

        private fun engineDescRes(engine: TranslationEngine): Int = when (engine) {
            TranslationEngine.SYSTEM -> R.string.translate_engine_system_desc
            TranslationEngine.MLKIT -> R.string.translate_engine_mlkit_desc
            TranslationEngine.GEMINI_NANO -> R.string.translate_engine_gemini_desc
        }

        private fun statusRes(status: EngineStatus): Int = when (status) {
            EngineStatus.CHECKING -> R.string.translate_availability_checking
            EngineStatus.READY -> R.string.translate_availability_ready
            EngineStatus.NEEDS_DOWNLOAD -> R.string.translate_availability_needs_download
            EngineStatus.DOWNLOADING -> R.string.translate_availability_downloading
            EngineStatus.LANGUAGE_UNSUPPORTED -> R.string.translate_availability_unsupported
            EngineStatus.NOT_ON_THIS_PHONE -> R.string.translate_availability_unavailable
            EngineStatus.ADDON_NOT_INSTALLED -> R.string.translate_availability_addon_missing
            EngineStatus.BLOCKED_IN_KEYBOARD -> R.string.translate_availability_blocked
            EngineStatus.UNKNOWN -> R.string.translate_availability_unknown
        }
    }
}

/** 설정 화면에 보여주는 엔진 상태. 엔진의 [Availability]에 애드온 설치 여부·키보드 안 차단 기록을 더한다. */
internal enum class EngineStatus {
    CHECKING,
    READY,
    NEEDS_DOWNLOAD,
    DOWNLOADING,
    LANGUAGE_UNSUPPORTED,
    NOT_ON_THIS_PHONE,
    ADDON_NOT_INSTALLED,
    BLOCKED_IN_KEYBOARD,
    UNKNOWN,
}

/**
 * 엔진 상태를 정한다. [availability]가 null이면 아직 확인 중.
 *
 * - ML Kit은 애드온 앱이 없으면 확인 결과와 상관없이 [EngineStatus.ADDON_NOT_INSTALLED].
 * - Gemini Nano는 checkStatus()가 AVAILABLE이어도 키보드 안에서는 막힐 수 있다 — 차단 기록이 있으면
 *   기기에 없다는 경우만 빼고 [EngineStatus.BLOCKED_IN_KEYBOARD].
 */
internal fun engineStatus(
    engine: TranslationEngine,
    availability: Availability?,
    addonInstalled: Boolean,
    geminiBlocked: Boolean,
): EngineStatus {
    if (engine == TranslationEngine.MLKIT && !addonInstalled) return EngineStatus.ADDON_NOT_INSTALLED
    if (engine == TranslationEngine.GEMINI_NANO && geminiBlocked && availability != Availability.UNAVAILABLE) {
        return EngineStatus.BLOCKED_IN_KEYBOARD
    }
    return when (availability) {
        null -> EngineStatus.CHECKING
        Availability.READY -> EngineStatus.READY
        Availability.NEEDS_DOWNLOAD -> EngineStatus.NEEDS_DOWNLOAD
        Availability.DOWNLOADING -> EngineStatus.DOWNLOADING
        Availability.LANGUAGE_UNSUPPORTED -> EngineStatus.LANGUAGE_UNSUPPORTED
        Availability.UNAVAILABLE -> EngineStatus.NOT_ON_THIS_PHONE
        Availability.ADDON_NOT_INSTALLED -> EngineStatus.ADDON_NOT_INSTALLED
        Availability.UNKNOWN -> EngineStatus.UNKNOWN
    }
}

/**
 * 한쪽 언어를 [picked]로 바꾼 새 (원문, 번역) 쌍. 반대쪽과 같은 언어를 고르면 두 언어를 맞바꾼다 —
 * 같은 언어끼리는 번역할 게 없으므로.
 */
internal fun pickLanguagePair(
    source: TranslationLanguage,
    target: TranslationLanguage,
    pickSource: Boolean,
    picked: TranslationLanguage,
): Pair<TranslationLanguage, TranslationLanguage> = if (pickSource) {
    if (picked == target) picked to source else picked to target
} else {
    if (picked == source) target to picked else source to picked
}

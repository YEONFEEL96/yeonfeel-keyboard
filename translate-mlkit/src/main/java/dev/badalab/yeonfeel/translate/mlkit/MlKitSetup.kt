package dev.badalab.yeonfeel.translate.mlkit

import android.content.Context
import com.google.mlkit.common.sdkinternal.MlKitContext

/** 프로세스 전역 ML Kit 준비와, 모델 관리 화면 → 번역 서비스 알림. 메인 스레드에서만 쓴다. */
internal object MlKitSetup {
    private val modelListeners = mutableSetOf<() -> Unit>()

    /**
     * 매니페스트에서 ML Kit 시작 프로바이더를 뺐으므로 처음 쓰기 전에 초기화한다 — 번역 서비스나 모델
     * 화면을 열지 않으면 ML Kit·사용 통계 라이브러리를 띄우지 않는다. 여러 번 불러도 된다.
     */
    fun ensureInitialized(context: Context) {
        MlKitContext.initializeIfNeeded(context.applicationContext)
    }

    fun addModelListener(listener: () -> Unit) {
        modelListeners += listener
    }

    fun removeModelListener(listener: () -> Unit) {
        modelListeners -= listener
    }

    /** 언어 모델이 지워졌다. */
    fun notifyModelsChanged() {
        modelListeners.toList().forEach { it() }
    }
}

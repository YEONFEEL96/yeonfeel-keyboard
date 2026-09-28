package dev.badalab.yeonfeel.translate.mlkit

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateRemoteModel
import java.util.Locale

/**
 * 애드온 안내 화면. 연필키보드용 ML Kit 애드온이라는 설명과, 내려받은 언어 모델 목록·삭제를 보여 준다.
 * 모델은 번역할 때 키보드 요청으로 내려받으므로 여기서는 받지 않는다.
 */
class ModelsActivity : Activity() {

    private lateinit var modelList: LinearLayout
    private val models by lazy {
        MlKitSetup.ensureInitialized(this)
        RemoteModelManager.getInstance()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }
        column.addView(text(getString(R.string.addon_app_name), android.R.style.TextAppearance_Material_Headline))
        column.addView(text(getString(R.string.addon_intro)).spaced(top = 16))
        column.addView(text(getString(R.string.addon_privacy)).spaced(top = 12))
        column.addView(
            text(getString(R.string.addon_models_title), android.R.style.TextAppearance_Material_Title)
                .spaced(top = 28),
        )
        modelList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(modelList.spaced(top = 8))
        setContentView(
            ScrollView(this).apply {
                // targetSdk 35+ 전체 화면에서 상태·내비게이션 바 아래로 내용이 들어가지 않게 한다.
                fitsSystemWindows = true
                addView(column)
            },
        )
    }

    override fun onResume() {
        super.onResume()
        loadModels()
    }

    private fun loadModels() {
        showMessage(getString(R.string.addon_models_loading))
        // Activity를 넘기면 화면이 멈출 때 리스너가 풀린다.
        models.getDownloadedModels(TranslateRemoteModel::class.java).addOnCompleteListener(this) { task ->
            if (!task.isSuccessful) {
                showMessage(getString(R.string.addon_models_error))
                return@addOnCompleteListener
            }
            val downloaded = task.result.orEmpty().sortedBy { displayName(it) }
            if (downloaded.isEmpty()) {
                showMessage(getString(R.string.addon_models_empty))
                return@addOnCompleteListener
            }
            modelList.removeAllViews()
            downloaded.forEach { modelList.addView(modelRow(it)) }
        }
    }

    private fun showMessage(message: String) {
        modelList.removeAllViews()
        modelList.addView(text(message).spaced(top = 8))
    }

    private fun modelRow(model: TranslateRemoteModel): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(56)
        addView(
            text(displayName(model), android.R.style.TextAppearance_Material_Subhead),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        addView(
            Button(context, null, android.R.attr.borderlessButtonStyle).apply {
                text = getString(R.string.addon_model_delete)
                contentDescription = "${getString(R.string.addon_model_delete)} ${displayName(model)}"
                setOnClickListener { confirmDelete(model) }
            },
        )
    }

    private fun confirmDelete(model: TranslateRemoteModel) {
        val name = displayName(model)
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.addon_model_delete_confirm, name))
            .setNegativeButton(R.string.addon_cancel, null)
            .setPositiveButton(R.string.addon_model_delete) { _, _ ->
                models.deleteDownloadedModel(model).addOnCompleteListener(this) { task ->
                    if (!task.isSuccessful) {
                        Toast.makeText(this, getString(R.string.addon_model_delete_failed, name), Toast.LENGTH_SHORT).show()
                    }
                    // 번역 서비스가 같은 프로세스에서 돌고 있으면 준비된 언어 쌍 캐시를 비운다.
                    MlKitSetup.notifyModelsChanged()
                    loadModels()
                }
            }
            .show()
    }

    private fun displayName(model: TranslateRemoteModel): String =
        Locale.forLanguageTag(model.language).getDisplayName(Locale.getDefault())
            .replaceFirstChar { it.titlecase(Locale.getDefault()) }
            .ifEmpty { model.language }

    private fun text(value: String, appearance: Int = android.R.style.TextAppearance_Material_Body1) =
        TextView(this).apply {
            setTextAppearance(appearance)
            text = value
        }

    private fun <T : View> T.spaced(top: Int): T = apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(top) }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

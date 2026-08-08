package dev.badalab.yeonfeel.debug

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 키 타점 수집 저장소. 오타 보정 모델의 기초 데이터.
 *
 * 프라이버시 설계: 타점은 키 단위로만 묶어 보관하고 순서·시각을 기록하지 않는다 —
 * 데이터를 통째로 읽어도 입력한 문장을 복원할 수 없다. 키별 상한을 두어
 * 오래된 타점부터 밀려난다. 기기 밖 전송은 없다.
 */
class TouchStatsStore(context: Context) {

    /**
     * [ax],[ay]는 키보드 전체 기준 정규화 좌표(0~1, 히트맵용),
     * [rx],[ry]는 눌린 키 중심 대비 상대 오프셋(키 크기 단위, 보정 모델용).
     */
    data class Sample(val key: String, val ax: Float, val ay: Float, val rx: Float, val ry: Float)

    private val file = File(context.filesDir, FILE_NAME)
    private val samples = HashMap<String, MutableList<Sample>>()
    private var unsavedCount = 0

    init {
        runCatching {
            if (file.exists()) {
                val array = JSONArray(file.readText())
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    insert(
                        Sample(
                            obj.getString("k"),
                            obj.getDouble("ax").toFloat(),
                            obj.getDouble("ay").toFloat(),
                            obj.getDouble("rx").toFloat(),
                            obj.getDouble("ry").toFloat(),
                        ),
                    )
                }
            }
        }
    }

    @Synchronized
    fun add(sample: Sample) {
        insert(sample)
        if (++unsavedCount >= SAVE_INTERVAL) flush()
    }

    @Synchronized
    fun flush() {
        if (unsavedCount == 0) return
        save()
        unsavedCount = 0
    }

    @Synchronized
    fun all(): List<Sample> = samples.values.flatten()

    @Synchronized
    fun totalCount(): Int = samples.values.sumOf { it.size }

    @Synchronized
    fun clear() {
        samples.clear()
        unsavedCount = 0
        file.delete()
    }

    private fun insert(sample: Sample) {
        val list = samples.getOrPut(sample.key) { mutableListOf() }
        list.add(sample)
        if (list.size > PER_KEY_LIMIT) list.removeAt(0)
    }

    private fun save() {
        runCatching {
            val array = JSONArray()
            samples.forEach { (key, list) ->
                list.forEach { s ->
                    array.put(
                        JSONObject()
                            .put("k", key)
                            .put("ax", s.ax.toDouble())
                            .put("ay", s.ay.toDouble())
                            .put("rx", s.rx.toDouble())
                            .put("ry", s.ry.toDouble()),
                    )
                }
            }
            file.writeText(array.toString())
        }
    }

    companion object {
        private const val FILE_NAME = "touch_stats.json"
        private const val PER_KEY_LIMIT = 150
        private const val SAVE_INTERVAL = 20
    }
}

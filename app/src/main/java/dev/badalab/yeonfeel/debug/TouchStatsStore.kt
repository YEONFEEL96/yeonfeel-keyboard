package dev.badalab.yeonfeel.debug

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 키 타점 수집 저장소. 오타 보정 모델의 기초 데이터.
 *
 * 프라이버시 설계: 타점은 키 단위로만 묶고 순서·시각을 기록하지 않아
 * 데이터를 통째로 읽어도 입력한 문장을 복원할 수 없다. 만료 관리를 위해
 * 일(day) 단위 날짜만 남기며, [RETENTION_DAYS]가 지난 표본은 버린다.
 *
 * 저장은 키별로 묶어 파일 전체를 다시 쓰는 방식이다 — 입력 순서가 디스크에
 * 남지 않도록, append가 아니라 [persist]에서 키 그룹 순서를 섞어 재작성한다.
 * 임시 파일에 쓴 뒤 rename 하므로 쓰기 도중 프로세스가 죽어도 기존 파일이
 * 온전히 남는다. 기기 밖 전송은 없다.
 */
class TouchStatsStore(context: Context) {

    /**
     * [ax],[ay]는 키보드 전체 기준 정규화 좌표(0~1, 히트맵용),
     * [rx],[ry]는 눌린 키 중심 대비 상대 오프셋(키 크기 단위, 보정 모델용).
     * [day]는 만료 판정용 일 단위 날짜 (0이면 저장 시점에 찍힌다).
     */
    data class Sample(
        val board: String,
        val key: String,
        val ax: Float,
        val ay: Float,
        val rx: Float,
        val ry: Float,
        val day: Long = 0L,
    )

    private val file = File(context.filesDir, FILE_NAME)
    private val tmpFile = File(context.filesDir, "$FILE_NAME.tmp")
    private val legacyFile = File(context.filesDir, LEGACY_FILE_NAME)
    private val samples = HashMap<String, MutableList<Sample>>()
    private var unsavedCount = 0
    private var loadedMtime = 0L

    init {
        load()
    }

    @Synchronized
    fun add(sample: Sample) {
        val stamped = if (sample.day == 0L) sample.copy(day = today()) else sample
        insert(stamped)
        if (++unsavedCount >= SAVE_INTERVAL) persist()
    }

    @Synchronized
    fun flush() {
        if (unsavedCount > 0) persist()
    }

    @Synchronized
    fun all(): List<Sample> = samples.values.flatten()

    /** 특정 자판(보드)의 타점만 돌려준다 — 자판별로 키 위치가 달라 섞으면 안 된다. */
    @Synchronized
    fun forBoard(board: String): List<Sample> =
        samples.values.flatten().filter { it.board == board }

    @Synchronized
    fun totalCount(): Int = samples.values.sumOf { it.size }

    @Synchronized
    fun clear() {
        samples.clear()
        unsavedCount = 0
        file.delete()
        tmpFile.delete()
        legacyFile.delete()
        loadedMtime = 0L
    }

    /** 외부(설정 화면)에서 파일이 바뀐 경우만 다시 읽는다. 읽었으면 true. */
    @Synchronized
    fun reload(): Boolean {
        val mtime = if (file.exists()) file.lastModified() else 0L
        if (mtime == loadedMtime && !legacyFile.exists()) return false
        // 아직 디스크에 없는 표본이 있으면 먼저 반영해 잃지 않는다.
        if (unsavedCount > 0) persist()
        samples.clear()
        unsavedCount = 0
        load()
        return true
    }

    private fun load() {
        val cutoff = today() - RETENTION_DAYS
        var migrated = false

        // 구버전(JSON 배열) 마이그레이션: 오늘 날짜로 흡수 후 새 형식으로 재작성
        runCatching {
            if (legacyFile.exists()) {
                val array = JSONArray(legacyFile.readText())
                for (i in 0 until array.length()) {
                    insert(parse(array.getJSONObject(i)))
                }
                legacyFile.delete()
                migrated = true
            }
        }

        runCatching {
            if (file.exists()) {
                file.forEachLine { line ->
                    if (line.isBlank()) return@forEachLine
                    runCatching {
                        val sample = parse(JSONObject(line))
                        if (sample.day >= cutoff) insert(sample)
                    }
                }
            }
        }
        loadedMtime = if (file.exists()) file.lastModified() else 0L

        // 만료된 표본을 버렸다면 파일을 지금 상태로 다시 써 정리한다.
        if (migrated) persist()
    }

    private fun parse(obj: JSONObject): Sample = Sample(
        obj.optString("b", "KO_DUBEOLSIK"),
        obj.getString("k"),
        obj.getDouble("ax").toFloat(),
        obj.getDouble("ay").toFloat(),
        obj.getDouble("rx").toFloat(),
        obj.getDouble("ry").toFloat(),
        obj.optLong("d", today()),
    )

    private fun lineOf(s: Sample): String = JSONObject()
        .put("b", s.board)
        .put("k", s.key)
        .put("ax", s.ax.toDouble())
        .put("ay", s.ay.toDouble())
        .put("rx", s.rx.toDouble())
        .put("ry", s.ry.toDouble())
        .put("d", s.day)
        .toString()

    private fun insert(sample: Sample) {
        val list = samples.getOrPut(sample.board + "|" + sample.key) { mutableListOf() }
        list.add(sample)
        if (list.size > PER_KEY_LIMIT) list.removeAt(0)
    }

    /**
     * 파일 전체를 키 그룹 단위로 재작성한다. 그룹 순서를 섞어 입력 순서가
     * 디스크에 드러나지 않게 하고, 임시 파일 rename 으로 원자적으로 교체한다.
     */
    private fun persist() {
        runCatching {
            tmpFile.bufferedWriter().use { writer ->
                samples.values.shuffled().forEach { list ->
                    list.forEach { writer.appendLine(lineOf(it)) }
                }
            }
            if (tmpFile.renameTo(file) || (file.delete() && tmpFile.renameTo(file))) {
                loadedMtime = file.lastModified()
            }
        }
        unsavedCount = 0
    }

    private fun today(): Long = System.currentTimeMillis() / DAY_MS

    companion object {
        private const val FILE_NAME = "touch_stats.jsonl"
        private const val LEGACY_FILE_NAME = "touch_stats.json"
        private const val PER_KEY_LIMIT = 150
        private const val SAVE_INTERVAL = 20
        private const val RETENTION_DAYS = 7L
        private const val DAY_MS = 86_400_000L
    }
}

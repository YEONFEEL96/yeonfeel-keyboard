package dev.badalab.yeonfeel.clipboard

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 클립보드 이력의 암호화 영속 저장소.
 *
 * - 키는 Android Keystore에 생성한 AES-256 키 — 하드웨어 보관이 가능한 기기에서는
 *   하드웨어에 격리되며, 어떤 경우에도 키 자체를 추출할 수 없다.
 * - 데이터는 AES-GCM(무결성 인증 포함)으로 암호화해 앱 전용 내부 저장소 파일에 쓴다.
 *   파일을 통째로 가져가도 이 기기의 Keystore 없이는 복호화할 수 없다.
 * - 쓰기는 임시 파일 rename 으로 원자적이며, 복호화 실패 시에는 파일을 지우지 않고
 *   빈 이력으로 시작한다 — 일시적 실패(동시 접근 등)를 데이터 손실로 바꾸지 않는다.
 * - 메서드는 @Synchronized 로 직렬화한다. IME 메인 스레드의 save 와 io 스레드의
 *   load 가 같은 인스턴스를 공유하므로, 잘린 파일을 읽는 경쟁을 이 락으로 막는다.
 */
class SecureClipboardStore(context: Context) {

    private val file = File(context.filesDir, FILE_NAME)
    private val tmpFile = File(context.filesDir, "$FILE_NAME.tmp")

    private var loadedMtime = -1L

    /** 파일이 마지막 로드 이후 바뀌었을 때만 복호화해 돌려준다 (변경 없으면 null). */
    @Synchronized
    fun loadIfChanged(): List<ClipboardHistory.Entry>? {
        val mtime = if (file.exists()) file.lastModified() else 0L
        if (mtime == loadedMtime) return null
        loadedMtime = mtime
        return load()
    }

    /** 저장 성공 여부를 돌려준다 — 마이그레이션이 성공했을 때만 구키를 지우기 위해. */
    @Synchronized
    fun save(entries: List<ClipboardHistory.Entry>): Boolean {
        return runCatching {
            val json = JSONArray()
            entries.forEach { entry ->
                json.put(
                    JSONObject()
                        .put(KEY_TEXT, entry.text)
                        .put(KEY_ADDED_AT, entry.addedAt)
                        .put(KEY_PINNED, entry.pinned),
                )
            }
            val plain = json.toString().toByteArray(Charsets.UTF_8)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val encrypted = cipher.doFinal(plain)
            tmpFile.writeBytes(cipher.iv + encrypted)
            if (!tmpFile.renameTo(file)) {
                file.delete()
                if (!tmpFile.renameTo(file)) error("rename failed")
            }
            // 자기 자신이 쓴 변경을 loadIfChanged 가 되읽지 않도록 mtime 을 맞춘다.
            loadedMtime = file.lastModified()
            true
        }.onFailure { Log.w(TAG, "클립보드 암호화 저장 실패", it) }.getOrDefault(false)
    }

    @Synchronized
    fun load(): List<ClipboardHistory.Entry> {
        if (!file.exists()) return emptyList()
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return emptyList()

        decrypt(bytes, existingKey(KEY_ALIAS))?.let { return parse(it) }

        // 구버전(비-StrongBox) 키로 저장된 파일이면 복호화 후 새 키로 재암호화한다.
        decrypt(bytes, existingKey(KEY_ALIAS_LEGACY))?.let { json ->
            val entries = parse(json)
            // 재암호화가 성공했을 때만 구키를 폐기한다 — 실패 시 원본을 읽을 유일한 키를 지키기 위해.
            if (save(entries)) {
                runCatching { keyStore().deleteEntry(KEY_ALIAS_LEGACY) }
            }
            return entries
        }

        // 복호화 실패는 일시적(잘린 읽기)일 수 있어 파일을 지우지 않는다. 빈 이력으로 시작한다.
        Log.w(TAG, "클립보드 복호화 실패 — 파일 보존")
        return emptyList()
    }

    @Synchronized
    fun clear() {
        file.delete()
        tmpFile.delete()
        loadedMtime = -1L
    }

    private fun decrypt(bytes: ByteArray, key: SecretKey?): String? {
        if (key == null || bytes.size <= IV_LENGTH) return null
        return runCatching {
            val iv = bytes.copyOfRange(0, IV_LENGTH)
            val encrypted = bytes.copyOfRange(IV_LENGTH, bytes.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        }.getOrNull()
    }

    private fun parse(json: String): List<ClipboardHistory.Entry> = runCatching {
        val array = JSONArray(json)
        buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                add(
                    ClipboardHistory.Entry(
                        obj.getString(KEY_TEXT),
                        obj.getLong(KEY_ADDED_AT),
                        obj.optBoolean(KEY_PINNED, false),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun keyStore(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private fun existingKey(alias: String): SecretKey? =
        runCatching { keyStore().getKey(alias, null) as? SecretKey }.getOrNull()

    /**
     * 새 키는 StrongBox(전용 보안 칩)에 우선 생성하고, 미지원 기기는 TEE로 폴백한다.
     * 어느 쪽이든 키는 하드웨어 밖으로 추출할 수 없다.
     */
    private fun getOrCreateKey(): SecretKey {
        // alias 가 이미 있으면 그 키로만 기존 파일을 복호화할 수 있다. 조회가 실패하면
        // 재생성(=키 교체=데이터 소실)하지 않고 예외를 올려 save 가 이번 저장을 포기하게 한다.
        val store = keyStore()
        if (store.containsAlias(KEY_ALIAS)) {
            return store.getKey(KEY_ALIAS, null) as? SecretKey
                ?: error("clipboard key alias present but not a SecretKey")
        }

        fun spec(strongBox: Boolean): KeyGenParameterSpec {
            val builder = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
            if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                builder.setIsStrongBoxBacked(true)
            }
            return builder.build()
        }

        fun generate(spec: KeyGenParameterSpec): SecretKey {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
            generator.init(spec)
            return generator.generateKey()
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                generate(spec(strongBox = true))
            } catch (e: android.security.keystore.StrongBoxUnavailableException) {
                generate(spec(strongBox = false))
            } catch (e: java.security.ProviderException) {
                // 일부 기기는 StrongBox 부재를 ProviderException 으로 던진다.
                generate(spec(strongBox = false))
            }
        } else {
            generate(spec(strongBox = false))
        }
    }

    companion object {
        private const val TAG = "SecureClipboard"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS_LEGACY = "clipboard_history_key"
        private const val KEY_ALIAS = "clipboard_history_key_v2"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val FILE_NAME = "clipboard.bin"
        private const val IV_LENGTH = 12
        private const val GCM_TAG_BITS = 128
        private const val KEY_TEXT = "t"
        private const val KEY_ADDED_AT = "a"
        private const val KEY_PINNED = "p"
    }
}

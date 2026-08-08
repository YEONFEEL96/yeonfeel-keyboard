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
 * - 복호화 실패(키 소실·파일 손상 등) 시에는 조용히 빈 이력으로 시작하고 파일을 지운다.
 */
class SecureClipboardStore(context: Context) {

    private val file = File(context.filesDir, FILE_NAME)

    fun save(entries: List<ClipboardHistory.Entry>) {
        runCatching {
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
            file.writeBytes(cipher.iv + encrypted)
        }.onFailure { Log.w(TAG, "클립보드 암호화 저장 실패", it) }
    }

    fun load(): List<ClipboardHistory.Entry> {
        if (!file.exists()) return emptyList()
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return emptyList()

        decrypt(bytes, existingKey(KEY_ALIAS))?.let { return parse(it) }

        // 구버전(비-StrongBox) 키로 저장된 파일이면 복호화 후 새 키로 재암호화한다.
        decrypt(bytes, existingKey(KEY_ALIAS_LEGACY))?.let { json ->
            val entries = parse(json)
            runCatching { save(entries) }
            runCatching { keyStore().deleteEntry(KEY_ALIAS_LEGACY) }
            return entries
        }

        Log.w(TAG, "클립보드 복호화 실패 — 이력을 비운다")
        file.delete()
        return emptyList()
    }

    fun clear() {
        file.delete()
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
        existingKey(KEY_ALIAS)?.let { return it }

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
            runCatching { generate(spec(strongBox = true)) }
                .getOrElse { generate(spec(strongBox = false)) }
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

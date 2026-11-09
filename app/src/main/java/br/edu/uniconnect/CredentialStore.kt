package br.edu.uniconnect

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

data class Credentials(val user: String, val password: String)

/** Only encrypted bytes are persisted. The key remains in Android Keystore. */
class CredentialStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("credentials", Context.MODE_PRIVATE)
    private val storage = AtomicFile(File(context.filesDir, "credentials-sealed.bin"))
    private val alias = "uniconnect-login-v1"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
        }.generateKey()
    }

    fun read(): Credentials? {
        val packed = try { DiskLock.run(context) { storage.readFully() } } catch (_: FileNotFoundException) {
            // Migrate existing encrypted credentials without changing the Keystore key.
            val saved = prefs.getString("sealed", null) ?: return null
            Base64.decode(saved, Base64.NO_WRAP).also { persist(it); check(prefs.edit().clear().commit()) }
        }
        if (packed.isEmpty()) return null
        require(packed.size > 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, packed.copyOfRange(0, 12)))
        val bytes = cipher.doFinal(packed.copyOfRange(12, packed.size))
        return try {
            val json = JSONObject(bytes.toString(Charsets.UTF_8))
            Credentials(json.getString("user"), json.getString("password"))
        } finally { bytes.fill(0) }
    }

    fun save(credentials: Credentials) {
        val bytes = JSONObject().put("user", credentials.user).put("password", credentials.password)
            .toString().toByteArray(Charsets.UTF_8)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val packed = cipher.iv + cipher.doFinal(bytes)
            persist(packed)
            check(prefs.edit().clear().commit())
        } finally { bytes.fill(0) }
    }

    private fun persist(bytes: ByteArray) {
        DiskLock.run(context) {
        val output = storage.startWrite()
        try { output.write(bytes); storage.finishWrite(output) }
        catch (error: Exception) { storage.failWrite(output); throw error }
        }
    }

    fun clear() { persist(ByteArray(0)); check(prefs.edit().clear().commit()) }
}

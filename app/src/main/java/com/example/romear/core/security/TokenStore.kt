package com.example.romear.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

@Singleton
class TokenStore @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = context.getSharedPreferences("secure_session", Context.MODE_PRIVATE)
    private val alias = "hello_session_v1"

    @Synchronized
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
    }

    @Synchronized
    private fun read(): JSONObject {
        val stored = preferences.getString("session", null) ?: return JSONObject()
        return try {
            val pieces = stored.split(":", limit = 2)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(pieces[0], Base64.NO_WRAP)))
            JSONObject(String(cipher.doFinal(Base64.decode(pieces[1], Base64.NO_WRAP)), Charsets.UTF_8))
        } catch (_: Exception) {
            preferences.edit().remove("session").commit()
            JSONObject()
        }
    }

    @Synchronized
    private fun write(value: JSONObject) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(value.toString().toByteArray(Charsets.UTF_8))
        check(preferences.edit().putString("session", Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit())
    }

    @Synchronized
    fun deviceId(): String {
        val value = read()
        return value.optString("deviceId").takeIf(String::isNotBlank) ?: UUID.randomUUID().toString().also { write(value.put("deviceId", it)) }
    }
    @Synchronized fun accessToken(): String? = read().optString("accessToken").takeIf(String::isNotBlank)
    @Synchronized fun refreshToken(): String? = read().optString("refreshToken").takeIf(String::isNotBlank)
    @Synchronized fun save(access: String, refresh: String) { write(read().put("accessToken", access).put("refreshToken", refresh)) }
    @Synchronized fun clear() { write(JSONObject().put("deviceId", deviceId())) }
    @Synchronized
    fun currentUserId(): String? {
        val token = accessToken() ?: return null
        return try {
            val parts = token.split(".")
            if (parts.size >= 2) {
                val decoded = String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING), Charsets.UTF_8)
                JSONObject(decoded).optString("sub").takeIf(String::isNotBlank)
            } else null
        } catch (_: Exception) { null }
    }
}

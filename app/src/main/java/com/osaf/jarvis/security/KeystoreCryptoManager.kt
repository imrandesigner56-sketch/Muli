package com.osaf.jarvis.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class KeystoreCryptoManager {

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "osaf_jarvis_api_key_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val SEPARATOR = ":"
    }

    private fun isKeystoreAvailable(): Boolean {
        return try {
            KeyStore.getInstance(KEYSTORE_PROVIDER) != null
        } catch (_: Exception) {
            false
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    fun encrypt(plainText: String): String {
        if (plainText.isBlank()) return ""
        if (!isKeystoreAvailable()) {
            // Only reachable in pure JVM unit tests without AndroidKeyStore provider
            return "test:" + Base64.getEncoder().encodeToString(plainText.toByteArray(StandardCharsets.UTF_8))
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val cipherBytes = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        val ivB64 = Base64.getEncoder().encodeToString(iv)
        val dataB64 = Base64.getEncoder().encodeToString(cipherBytes)
        return "$ivB64$SEPARATOR$dataB64"
    }

    fun decrypt(encryptedPayload: String): String {
        if (encryptedPayload.isBlank()) return ""
        if (encryptedPayload.startsWith("test:") && !isKeystoreAvailable()) {
            return String(
                Base64.getDecoder().decode(encryptedPayload.removePrefix("test:")),
                StandardCharsets.UTF_8
            )
        }
        val parts = encryptedPayload.split(SEPARATOR)
        if (parts.size != 2) return ""
        return try {
            val iv = Base64.getDecoder().decode(parts[0])
            val cipherBytes = Base64.getDecoder().decode(parts[1])
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    fun maskKey(plainKey: String): String {
        val trimmed = plainKey.trim()
        if (trimmed.isEmpty()) return "Not configured"
        if (trimmed.length <= 8) return "••••••••"
        return "${trimmed.take(3)}••••••••${trimmed.takeLast(4)}"
    }
}

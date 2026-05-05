package com.example.proyecto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.util.Base64
import javax.crypto.spec.SecretKeySpec

object cifrado_256 {
    private const val AES_MODE = "AES/GCM/NoPadding"
    private const val IV_SIZE = 12 // recomendado para GCM
    private const val TAG_SIZE = 128

    fun generateKey(): SecretKey {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256) // AES-256
        return keyGen.generateKey()
    }
    // 🔹 2. Convertir SecretKey a String (para guardar)
    fun keyToString(key: SecretKey): String {
        return Base64.encodeToString(key.encoded, Base64.NO_WRAP)
    }
    // 🔹 3. Convertir String a SecretKey (para recuperar)
    fun stringToKey(keyStr: String): SecretKey {
        val decoded = Base64.decode(keyStr, Base64.NO_WRAP)
        return SecretKeySpec(decoded, "AES")
    }
    fun encrypt(data: String, key: SecretKey): String {
        val cipher = Cipher.getInstance(AES_MODE)

        val iv = ByteArray(IV_SIZE)
        SecureRandom().nextBytes(iv)

        val spec = GCMParameterSpec(TAG_SIZE, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)

        val encrypted = cipher.doFinal(data.toByteArray(Charsets.UTF_8))

        // IV + ciphertext
        val combined = iv + encrypted

        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decrypt(data: String, key: SecretKey): String {
        val decoded = Base64.decode(data, Base64.NO_WRAP)

        val iv = decoded.copyOfRange(0, IV_SIZE)
        val encrypted = decoded.copyOfRange(IV_SIZE, decoded.size)

        val cipher = Cipher.getInstance(AES_MODE)
        val spec = GCMParameterSpec(TAG_SIZE, iv)

        cipher.init(Cipher.DECRYPT_MODE, key, spec)

        val decrypted = cipher.doFinal(encrypted)

        return String(decrypted, Charsets.UTF_8)
    }
}
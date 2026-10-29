package com.zomet.zxcarwash

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Email & password yang diingat, disandikan AES-GCM dengan kunci di
 * AndroidKeyStore — polanya sama dengan AcuMedis. Tidak butuh dependency
 * tambahan (security-crypto), cukup API bawaan.
 */
object Kredensial {

    private const val PREFS = "zxcarwash_creds"
    private const val KEY_EMAIL = "cred_email"
    private const val KEY_PASS = "cred_pass"
    private const val KEY_ALIAS = "zxcarwash_creds_v1"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val IV_LEN = 12

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }

    private fun enkripsi(teks: String): String? = runCatching {
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = c.iv
        val isi = c.doFinal(teks.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(iv + isi, Base64.NO_WRAP)
    }.getOrNull()

    private fun dekripsi(b64: String): String? = runCatching {
        val semua = Base64.decode(b64, Base64.NO_WRAP)
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, semua, 0, IV_LEN))
        String(c.doFinal(semua, IV_LEN, semua.size - IV_LEN), Charsets.UTF_8)
    }.getOrNull()

    fun simpan(ctx: Context, email: String, password: String) {
        prefs(ctx).edit()
            .putString(KEY_EMAIL, email)
            .putString(KEY_PASS, enkripsi(password))
            .apply()
    }

    fun email(ctx: Context): String = prefs(ctx).getString(KEY_EMAIL, "") ?: ""

    fun password(ctx: Context): String =
        prefs(ctx).getString(KEY_PASS, null)?.let { dekripsi(it) } ?: ""

    fun ada(ctx: Context): Boolean = email(ctx).isNotBlank()

    fun hapus(ctx: Context) = prefs(ctx).edit().remove(KEY_EMAIL).remove(KEY_PASS).apply()
}

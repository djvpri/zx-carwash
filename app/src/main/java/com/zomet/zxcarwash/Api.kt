package com.zomet.zxcarwash

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Transport ke zx-carwash-api. Memakai HttpURLConnection bawaan Android —
 * tanpa Retrofit/OkHttp supaya APK kasir tetap kecil.
 *
 * Token sesi disimpan di tabel pengaturan (kunci/nilai) lewat Db, bukan
 * SharedPreferences, supaya ikut aturan "semua state lokal di satu tempat".
 */
object Api {

    /** Diisi dari gradle.properties → buildConfigField API_BASE. */
    val BASE: String get() = BuildConfig.API_BASE.trimEnd('/')

    private const val TIMEOUT = 20_000
    private const val SYNC_TIMEOUT = 40_000

    var lastError: String? = null
        private set

    private data class Hasil(val kode: Int, val isi: String)

    private fun kirim(
        path: String,
        metode: String,
        body: String?,
        token: String?,
        timeoutMs: Int = TIMEOUT,
    ): Hasil {
        val hasil = try {
            val conn = URL(BASE + path).openConnection() as HttpURLConnection
            conn.requestMethod = metode
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.setRequestProperty("Accept", "application/json")
            if (token != null) conn.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val kode = conn.responseCode
            val stream = if (kode in 200..299) conn.inputStream else conn.errorStream
            val teks = stream?.bufferedReader()?.use(BufferedReader::readText) ?: ""
            conn.disconnect()
            hasilOk(kode, teks)
        } catch (e: Exception) {
            // Exception ditangkap supaya pemanggil selalu dapat pesan yang bisa
            // ditampilkan ke kasir, bukan crash di area basah tanpa sinyal.
            hasilGagal(e)
        }
        lastError = if (hasil.kode in 200..299) null else pesanDari(hasil.isi)
        return hasil
    }

    private fun hasilOk(kode: Int, isi: String) = Hasil(kode, isi)
    private fun hasilGagal(e: Exception) = Hasil(0, """{"ok":false,"error":"${e.javaClass.simpleName}: ${e.message ?: "gagal terhubung"}"}""")

    /** Ambil field `error` dari balasan JSON; kalau bukan JSON, potong teksnya. */
    private fun pesanDari(isi: String): String = runCatching {
        val o = JSONObject(isi)
        val pesan = o.optString("error", "")
        if (pesan.isNotBlank()) pesan else "kesalahan server"
    }.getOrElse { if (isi.isBlank()) "tidak ada balasan dari server" else isi.take(160) }

    private fun sukses(h: Hasil): JSONObject? {
        if (h.kode !in 200..299) return null
        return runCatching { JSONObject(h.isi) }.getOrNull()
    }

    // ---------- SSO Z One (device code) ----------

    /** POST /v1/auth/sso/start → (code, verifyUrl) */
    fun ssoStart(deviceId: String): Pair<String, String>? {
        val body = JSONObject().put("deviceId", deviceId).toString()
        val o = sukses(kirim("/v1/auth/sso/start", "POST", body, null)) ?: return null
        val code = o.optString("code", "")
        val url = o.optString("verifyUrl", "")
        return if (code.isBlank() || url.isBlank()) null else code to url
    }

    /** GET /v1/auth/sso/poll → 202 berarti masih menunggu persetujuan. */
    fun ssoPoll(code: String): JSONObject? {
        val h = kirim("/v1/auth/sso/poll?code=${android.net.Uri.encode(code)}", "GET", null, null)
        if (h.kode == 202) return JSONObject().put("pending", true)
        return sukses(h)
    }

    /** POST /v1/auth/sso/exchange {token} — jalur deep link zxcarwash://sso?token= */
    fun ssoExchange(token: String, deviceId: String): JSONObject? {
        val body = JSONObject().put("token", token).put("deviceId", deviceId).toString()
        return sukses(kirim("/v1/auth/sso/exchange", "POST", body, null))
    }

    // ---------- Data ----------

    fun master(token: String, outletId: String?, sinceIso: String? = null): JSONObject? {
        val q = buildString {
            append("/v1/master")
            if (!outletId.isNullOrBlank()) append("?outletId=").append(outletId)
            if (!sinceIso.isNullOrBlank()) append(if (outletId.isNullOrBlank()) "?since=" else "&since=").append(sinceIso)
        }
        return sukses(kirim(q, "GET", null, token))
    }

    /** POST /v1/sync/batch — kirim outbox. Balasan memuat hasil per clientId. */
    fun syncBatch(token: String, outletId: String?, deviceId: String, items: JSONArray): JSONObject? {
        val body = JSONObject()
            .put("deviceId", deviceId)
            .apply { if (!outletId.isNullOrBlank()) put("outletId", outletId) }
            .put("items", items)
            .toString()
        return sukses(kirim("/v1/sync/batch", "POST", body, token, SYNC_TIMEOUT))
    }

    fun patchPengaturan(token: String, outletId: String?, p: Pengaturan): Boolean {
        val body = JSONObject()
            .put("namaUsaha", p.namaUsaha)
            .put("alamat", p.alamat)
            .put("noHp", p.noHp)
            .put("catatanNota", p.catatanNota)
            .put("autoUpdate", p.autoUpdate)
            .apply { if (!outletId.isNullOrBlank()) put("outletId", outletId) }
            .toString()
        return sukses(kirim("/v1/pengaturan", "PATCH", body, token)) != null
    }

    /** GET /v1/sync/status — untuk badge di beranda. */
    fun syncStatus(token: String): JSONObject? = sukses(kirim("/v1/sync/status", "GET", null, token))

    /** Model perangkat untuk deviceId — cukup untuk membedakan HP kasir. */
    fun deviceId(ctx: Context): String =
        (android.provider.Settings.Secure.getString(ctx.contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: "hp")
            .take(16)
}

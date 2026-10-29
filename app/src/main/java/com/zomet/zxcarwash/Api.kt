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

    /** Alamat server: bawaan dari BuildConfig, bisa ditimpa kasir (tekan lama). */
    const val BASE_BAWAAN: String = "" // diisi di init karena BuildConfig tidak const
    private var baseOverride: String? = null

    /** Dipasang sekali saat app start dari nilai tersimpan. */
    fun pasangBase(url: String?) {
        baseOverride = url?.trim()?.trimEnd('/')?.takeIf { it.startsWith("http") }
    }

    val BASE: String
        get() = baseOverride ?: BuildConfig.API_BASE.trimEnd('/')

    private const val TIMEOUT = 20_000
    private const val SYNC_TIMEOUT = 40_000

    var lastError: String? = null
        private set

    private data class Hasil(val kode: Int, val isi: String)

    /**
     * Dipasang sekali saat app start (MainActivity). Mengembalikan access token
     * baru, atau null kalau refresh ditolak.
     *
     * Tanpa ini, access token 60 menit habis dan setiap layar yang sedang
     * terbuka langsung menampilkan "token expired or invalid" — kasir harus
     * keluar-masuk akun hanya karena aplikasi dibiarkan terbuka.
     */
    var onRefresh: (() -> String?)? = null

    private fun kirim(
        path: String,
        metode: String,
        body: String?,
        token: String?,
        timeoutMs: Int = TIMEOUT,
    ): Hasil {
        val pertama = kirimSekali(path, metode, body, token, timeoutMs)

        // 401 = access token kedaluwarsa. Coba segarkan sekali, lalu ulangi
        // permintaan yang sama. Kalau refresh ditolak, biarkan 401 naik ke UI.
        if (pertama.kode == 401 && token != null) {
            val baru = onRefresh?.invoke()
            if (!baru.isNullOrBlank() && baru != token) {
                return kirimSekali(path, metode, body, baru, timeoutMs)
            }
        }
        return pertama
    }

    private fun kirimSekali(
        path: String,
        metode: String,
        body: String?,
        token: String?,
        timeoutMs: Int,
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

    // ---------- Login email + password (pola sama dengan AcuMedis) ----------

    /** POST /v1/auth/login {email, password, deviceId} → sesi + refreshToken */
    fun login(email: String, password: String, deviceId: String): JSONObject? {
        val body = JSONObject()
            .put("email", email)
            .put("password", password)
            .put("deviceId", deviceId)
            .toString()
        return sukses(kirim("/v1/auth/login", "POST", body, null))
    }

    /** POST /v1/auth/refresh {refreshToken} — token lama dicabut, dapat yang baru. */
    fun refresh(refreshToken: String, deviceId: String): JSONObject? {
        val body = JSONObject().put("refreshToken", refreshToken).put("deviceId", deviceId).toString()
        return sukses(kirim("/v1/auth/refresh", "POST", body, null))
    }

    fun logout(refreshToken: String): Boolean {
        val body = JSONObject().put("refreshToken", refreshToken).toString()
        return sukses(kirim("/v1/auth/logout", "POST", body, null)) != null
    }

    /** POST /v1/auth/password {passwordLama, passwordBaru} */
    fun gantiPassword(token: String, lama: String, baru: String): String? {
        val body = JSONObject().put("passwordLama", lama).put("passwordBaru", baru).toString()
        val h = kirim("/v1/auth/password", "POST", body, token)
        if (h.kode in 200..299) return null
        return pesanDari(h.isi)
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

    // ---------- Antrian & laporan ----------

    /** GET /v1/antrian?status= — papan bay. `status=null` berarti semua. */
    fun antrian(token: String, status: String? = null): JSONObject? {
        val q = if (status.isNullOrBlank()) "" else "?status=$status"
        return sukses(kirim("/v1/antrian$q", "GET", null, token))
    }

    /** PATCH /v1/antrian/:id {status} */
    fun ubahAntrian(token: String, id: String, status: String): JSONObject? {
        val body = JSONObject().put("status", status).toString()
        return sukses(kirim("/v1/antrian/$id", "PATCH", body, token))
    }

    /** GET /v1/laporan/harian — tanpa tanggal berarti hari ini (WIB). */
    fun laporanHarian(token: String, tanggal: String? = null): JSONObject? {
        val q = if (tanggal.isNullOrBlank()) "" else "?tanggal=$tanggal"
        return sukses(kirim("/v1/laporan/harian$q", "GET", null, token))
    }

    /** GET /v1/laporan/periode — `hari` = 7 berarti 7 hari terakhir termasuk hari ini. */
    fun laporanPeriode(token: String, hari: Int): JSONObject? {
        val sampai = java.time.LocalDate.now()
        val dari = sampai.minusDays((hari - 1).toLong())
        return sukses(
            kirim("/v1/laporan/periode?dari=$dari&sampai=$sampai", "GET", null, token)
        )
    }

    /** GET /v1/license — status langganan dari server. */
    fun lisensi(token: String): JSONObject? =
        sukses(kirim("/v1/license", "GET", null, token))?.optJSONObject("lisensi")

    /** POST /v1/shift/buka {kasAwal} */
    fun bukaShift(token: String, kasAwal: Int): JSONObject? {
        val body = JSONObject().put("kasAwal", kasAwal).toString()
        return sukses(kirim("/v1/shift/buka", "POST", body, token))
    }

    /** POST /v1/shift/tutup {kasFisik} */
    fun tutupShift(token: String, kasFisik: Int, catatan: String? = null): JSONObject? {
        val body = JSONObject().put("kasFisik", kasFisik)
        if (!catatan.isNullOrBlank()) body.put("catatan", catatan)
        return sukses(kirim("/v1/shift/tutup", "POST", body.toString(), token))
    }

    /** GET /v1/shift — shift aktif + riwayat. */
    fun shift(token: String): JSONObject? = sukses(kirim("/v1/shift", "GET", null, token))

    /** POST /v1/transaksi/:id/bayar {metode, dibayar} — melunasi bon yang masih open. */
    fun bayarBon(token: String, id: String, metode: String, dibayar: Int): JSONObject? {
        val body = JSONObject().put("metode", metode).put("dibayar", dibayar).toString()
        return sukses(kirim("/v1/transaksi/$id/bayar", "POST", body, token))
    }

    /** GET /v1/transaksi?status=open — daftar piutang. */
    fun transaksi(token: String, status: String? = null, limit: Int = 50): JSONObject? {
        val q = buildString {
            append("/v1/transaksi?limit=").append(limit)
            if (!status.isNullOrBlank()) append("&status=").append(status)
        }
        return sukses(kirim(q, "GET", null, token))
    }

    // ---------- Master data: produk, stok, member ----------

    /** GET /v1/produk?kritis=1 — daftar menu kantin beserta stok. */
    fun produk(token: String, kritisSaja: Boolean = false): JSONObject? =
        sukses(kirim("/v1/produk" + if (kritisSaja) "?kritis=1" else "", "GET", null, token))

    /** POST /v1/produk {id?, nama, kategori, harga, stok, trackStok, aktif} */
    fun simpanProduk(token: String, o: JSONObject): JSONObject? =
        sukses(kirim("/v1/produk", "POST", o.toString(), token))

    /** POST /v1/stok/mutasi {produkId, tipe, qty} */
    fun mutasiStok(token: String, produkId: String, tipe: String, qty: Int): JSONObject? {
        val body = JSONObject().put("produkId", produkId).put("tipe", tipe).put("qty", qty).toString()
        return sukses(kirim("/v1/stok/mutasi", "POST", body, token))
    }

    /** GET /v1/member — daftar member + poin + saldo. */
    fun member(token: String): JSONObject? = sukses(kirim("/v1/member", "GET", null, token))

    /** POST /v1/member {id?, nama, telepon, level} */
    fun simpanMember(token: String, o: JSONObject): JSONObject? =
        sukses(kirim("/v1/member", "POST", o.toString(), token))

    /** POST /v1/member/:id/saldo {poin, saldo} — nilai negatif boleh. */
    fun ubahSaldo(token: String, id: String, poin: Int, saldo: Int): JSONObject? {
        val body = JSONObject().put("poin", poin).put("saldo", saldo).toString()
        return sukses(kirim("/v1/member/$id/saldo", "POST", body, token))
    }

    // ---------- Paket layanan & add-on ----------

    fun paket(token: String): JSONObject? = sukses(kirim("/v1/paket", "GET", null, token))

    fun simpanPaket(token: String, o: JSONObject): JSONObject? =
        sukses(kirim("/v1/paket", "POST", o.toString(), token))

    fun addon(token: String): JSONObject? = sukses(kirim("/v1/addon", "GET", null, token))

    fun simpanAddon(token: String, o: JSONObject): JSONObject? =
        sukses(kirim("/v1/addon", "POST", o.toString(), token))

    /** GET /v1/shift/:id/rekap — omzet, per metode, kas sistem, selisih. */
    fun rekapShift(token: String, id: String): JSONObject? =
        sukses(kirim("/v1/shift/$id/rekap", "GET", null, token))?.optJSONObject("rekap")

    /** Model perangkat untuk deviceId — cukup untuk membedakan HP kasir. */
    fun deviceId(ctx: Context): String =
        (android.provider.Settings.Secure.getString(ctx.contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: "hp")
            .take(16)
}

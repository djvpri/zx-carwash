package com.zomet.zxcarwash

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Sesi login (token ZX Carwash) + antrean sync.
 *
 * Token disimpan di tabel `pengaturan` yang sudah ada, jadi tidak perlu tabel
 * baru dan tetap bisa dibaca saat offline. Sengaja TIDAK memakai
 * EncryptedSharedPreferences: butuh dependency tambahan (security-crypto)
 * sementara yang disimpan bukan password melainkan token 60 menit.
 */
object Sesi {

    private const val K_TOKEN = "auth_token"
    private const val K_EMAIL = "auth_email"
    private const val K_NAMA = "auth_nama"
    private const val K_OUTLET = "auth_outlet"
    private const val K_OUTLET_NAMA = "auth_outlet_nama"
    private const val K_USAHA = "auth_usaha"
    private const val K_LOGIN_AT = "auth_login_at"
    private const val K_MASTER_VERSI = "master_versi"

    data class Info(
        val token: String,
        val email: String,
        val nama: String,
        val outletId: String?,
        val outletNama: String?,
        val namaUsaha: String?,
        val loginAt: Long,
    )

    /** null = belum login. */
    fun baca(db: Db): Info? {
        val m = db.bacaPengaturanMentah()
        val token = m[K_TOKEN] ?: return null
        if (token.isBlank()) return null
        return Info(
            token = token,
            email = m[K_EMAIL] ?: "",
            nama = m[K_NAMA] ?: "",
            outletId = m[K_OUTLET]?.takeIf { it.isNotBlank() },
            outletNama = m[K_OUTLET_NAMA]?.takeIf { it.isNotBlank() },
            namaUsaha = m[K_USAHA]?.takeIf { it.isNotBlank() },
            loginAt = (m[K_LOGIN_AT] ?: "0").toLongOrNull() ?: 0L,
        )
    }

    fun simpan(db: Db, o: JSONObject) {
        val user = o.optJSONObject("user") ?: JSONObject()
        val tenant = o.optJSONObject("tenant") ?: JSONObject()
        val outletId = if (o.isNull("outletId")) null else o.optString("outletId").takeIf { it.isNotBlank() }
        db.tulisPengaturanMentah(
            mapOf(
                K_TOKEN to o.optString("accessToken", ""),
                K_EMAIL to user.optString("email", ""),
                K_NAMA to user.optString("nama", ""),
                K_OUTLET to (outletId ?: ""),
                K_OUTLET_NAMA to (o.optString("outletNama", "")),
                K_USAHA to tenant.optString("nama", ""),
                K_LOGIN_AT to System.currentTimeMillis().toString(),
            )
        )
    }

    fun keluar(db: Db) = db.hapusPengaturanMentah(
        listOf(K_TOKEN, K_EMAIL, K_NAMA, K_OUTLET, K_OUTLET_NAMA, K_USAHA, K_LOGIN_AT, K_MASTER_VERSI)
    )

    // ---------- tarik master ----------

    /**
     * Ambil paket/addon/produk dari server, simpan peta nama → id supaya item
     * bon bisa membawa `refId`. Pengaturan usaha ikut disegarkan dari server.
     *
     * @return true kalau berhasil.
     */
    fun tarikMaster(db: Db, sesi: Info): Boolean {
        val o = Api.master(sesi.token, sesi.outletId) ?: return false

        fun peta(kunci: String): Map<String, String> {
            val arr = o.optJSONArray(kunci) ?: return emptyMap()
            val m = mutableMapOf<String, String>()
            for (i in 0 until arr.length()) {
                val it = arr.optJSONObject(i) ?: continue
                val nama = it.optString("nama", "")
                val id = it.optString("id", "")
                if (nama.isNotBlank() && id.isNotBlank()) m[nama] = id
            }
            return m
        }

        db.simpanPetaMaster(peta("paket"), peta("addon"), peta("produk"))

        // nama usaha dari server menang atas nilai bawaan app
        val p = o.optJSONObject("pengaturan")
        if (p != null && p.optString("namaUsaha", "").isNotBlank()) {
            val lama = db.bacaPengaturan()
            db.simpanPengaturan(
                lama.copy(
                    namaUsaha = p.optString("namaUsaha", lama.namaUsaha),
                    alamat = p.optString("alamat", lama.alamat),
                    noHp = p.optString("noHp", lama.noHp),
                    catatanNota = p.optString("catatanNota", lama.catatanNota),
                    autoUpdate = if (p.has("autoUpdate")) p.optBoolean("autoUpdate") else lama.autoUpdate,
                )
            )
        }
        db.tulisPengaturanMentah(mapOf(K_MASTER_VERSI to o.optString("versi", "")))
        return true
    }

    // ---------- antrean sync ----------

    /**
     * Kirim semua bon yang belum terkirim. Aman diulang: server memakai
     * `clientId` sebagai kunci idempoten, jadi kiriman ganda tidak membuat bon
     * kedua di server.
     *
     * @return jumlah bon yang berhasil ditandai terkirim, atau -1 kalau gagal
     *         menghubungi server (pesan ada di Api.lastError).
     */
    fun kirimOutbox(ctx: Context, db: Db, sesi: Info): Int {
        val baris = db.outbox()
        if (baris.isEmpty()) return 0

        val items = JSONArray()
        for ((_, clientId, payload) in baris) {
            val isi = runCatching { JSONObject(payload) }.getOrNull() ?: continue
            // payload lokal memakai snake_case; server memakai camelCase.
            // refId item diisi dari peta master (nama → id server).
            val itemKirim = JSONArray()
            val itemLokal = isi.optJSONArray("items") ?: JSONArray()
            for (i in 0 until itemLokal.length()) {
                val it = itemLokal.optJSONObject(i) ?: continue
                val tipe = it.optString("tipe", "cuci")
                val nama = it.optString("nama", "")
                val refId = it.optString("refId", "").ifBlank { db.refIdUntuk(tipe, nama) }
                itemKirim.put(
                    JSONObject()
                        .put("tipe", tipe)
                        .put("nama", nama)
                        .put("harga", it.optInt("harga", 0))
                        .put("qty", it.optInt("qty", 1))
                        .apply { if (refId.isNotBlank()) put("refId", refId) }
                )
            }

            val kirim = JSONObject()
                .put("clientId", clientId)
                .put("status", "bayar")
                .put("plat", isi.optString("plat", ""))
                .put("jenis", isi.optString("jenis", ""))
                .put("metode", normalMetode(isi.optString("metode", "tunai")))
                .put("dibayar", isi.optInt("dibayar", isi.optInt("total", 0)))
                .put("diskon", isi.optInt("diskon", 0))
                .put("createdAt", isoDariMillis(isi.optLong("created_at", System.currentTimeMillis())))
                .put("items", itemKirim)
            items.put(JSONObject().put("entitas", "bon").put("clientId", clientId).put("payload", kirim))
        }
        if (items.length() == 0) return 0

        val hasil = Api.syncBatch(sesi.token, sesi.outletId, Api.deviceId(ctx), items) ?: return -1
        val arr = hasil.optJSONArray("hasil") ?: return -1

        var sukses = 0
        for (i in 0 until arr.length()) {
            val h = arr.optJSONObject(i) ?: continue
            val clientId = h.optString("clientId", "")
            val status = h.optString("status", "")
            val idLokal = db.outboxIdUntuk(clientId) ?: continue
            if (status == "ok" || status == "duplikat") {
                db.tandaiTerkirim(idLokal)
                db.tandaiBonTersinkron(clientId, h.optString("nomorBon", ""))
                sukses++
            } else {
                db.tandaiGagal(idLokal, h.optString("error", "ditolak server"))
            }
        }
        return sukses
    }

    private fun normalMetode(m: String): String = when (m.lowercase()) {
        "qris" -> "qris"
        "kartu" -> "kartu"
        "transfer" -> "transfer"
        else -> "tunai"
    }

    private fun isoDariMillis(ms: Long): String =
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
            .format(java.util.Date(ms))
}

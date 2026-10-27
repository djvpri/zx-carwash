package com.zomet.zxcarwash

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Satu baris item pada bon (jasa cuci, add-on, atau menu kantin). */
data class ItemBon(
    val tipe: String,          // cuci | addon | kantin
    val nama: String,
    val harga: Int,
    val qty: Int = 1,
) {
    val subtotal: Int get() = harga * qty
}

/** Bon gabungan: cuci + kantin dalam satu transaksi. */
data class Bon(
    val id: Long = 0,
    val clientId: String = UUID.randomUUID().toString(),
    val nomor: String,
    val plat: String,
    val jenis: String,
    val items: List<ItemBon>,
    val diskon: Int,
    val metode: String,
    val dibayar: Int,
    val createdAt: Long,
    val synced: Boolean = false,
) {
    val subtotalCuci: Int get() = items.filter { it.tipe != "kantin" }.sumOf { it.subtotal }
    val subtotalKantin: Int get() = items.filter { it.tipe == "kantin" }.sumOf { it.subtotal }
    val total: Int get() = (subtotalCuci + subtotalKantin - diskon).coerceAtLeast(0)
    val kembali: Int get() = (dibayar - total).coerceAtLeast(0)
}

/** Identitas usaha — dipakai header/footer struk, tetap tersedia offline. */
data class Pengaturan(
    val namaUsaha: String = "Cuci Kantin Zomet",
    val alamat: String = "Jl. Melati 12, Pontianak",
    val noHp: String = "0812-3456-789",
    val catatanNota: String = "Terima kasih. Barang tertinggal di luar tanggung jawab kami.",
    val autoUpdate: Boolean = true,
    val paket: String = "Pro",
    val berlakuSampai: String = "28 Feb 2027",
    val idLisensi: String = "ZXC-MLT-0A4F",
)

object Layanan {
    val cuci = listOf(
        Triple("Reguler", 35000, 20),
        Triple("Salon", 65000, 45),
        Triple("Premium", 95000, 70),
        Triple("Mesin", 25000, 15),
        Triple("Interior", 120000, 90),
    )
    val addon = listOf("Jamur kaca" to 15000, "Parfum" to 10000, "Poles" to 120000, "Wax" to 40000)
    val kantin = listOf(
        Triple("Nasi Goreng Spesial", 12000, 20),
        Triple("Mie Goreng", 11000, 15),
        Triple("Es Teh Manis", 4000, 50),
        Triple("Kopi", 5000, 40),
        Triple("Air Mineral", 3000, 60),
    )
}

/** SQLite mentah (tanpa Room) — tabel lokal + outbox sync. */
class Db(ctx: Context) : SQLiteOpenHelper(ctx, "zxcarwash.db", null, 1) {

    override fun onCreate(d: SQLiteDatabase) {
        d.execSQL(
            """CREATE TABLE bon(
                id INTEGER PRIMARY KEY AUTOINCREMENT, client_id TEXT UNIQUE, nomor TEXT,
                plat TEXT, jenis TEXT, subtotal_cuci INTEGER, subtotal_kantin INTEGER,
                diskon INTEGER, total INTEGER, metode TEXT, dibayar INTEGER, kembali INTEGER,
                created_at INTEGER, synced INTEGER DEFAULT 0)"""
        )
        d.execSQL(
            """CREATE TABLE item(
                id INTEGER PRIMARY KEY AUTOINCREMENT, bon_id INTEGER, tipe TEXT,
                nama TEXT, harga INTEGER, qty INTEGER)"""
        )
        d.execSQL("CREATE TABLE pengaturan(kunci TEXT PRIMARY KEY, nilai TEXT)")
        d.execSQL(
            """CREATE TABLE outbox(
                id INTEGER PRIMARY KEY AUTOINCREMENT, entitas TEXT, client_id TEXT,
                payload TEXT, status TEXT DEFAULT 'pending', err TEXT, created_at INTEGER)"""
        )
    }

    override fun onUpgrade(d: SQLiteDatabase, old: Int, new: Int) = Unit

    fun simpanBon(b: Bon): Long {
        val cv = ContentValues().apply {
            put("client_id", b.clientId); put("nomor", b.nomor)
            put("plat", b.plat); put("jenis", b.jenis)
            put("subtotal_cuci", b.subtotalCuci); put("subtotal_kantin", b.subtotalKantin)
            put("diskon", b.diskon); put("total", b.total)
            put("metode", b.metode); put("dibayar", b.dibayar); put("kembali", b.kembali)
            put("created_at", b.createdAt); put("synced", 0)
        }
        val id = writableDatabase.insert("bon", null, cv)
        b.items.forEach { it ->
            writableDatabase.insert("item", null, ContentValues().apply {
                put("bon_id", id); put("tipe", it.tipe); put("nama", it.nama)
                put("harga", it.harga); put("qty", it.qty)
            })
        }
        antre(b, id)
        return id
    }

    /** Baris outbox = bukti transaksi belum terkirim. Dibuang setelah server balas 2xx. */
    private fun antre(b: Bon, id: Long) {
        val js = JSONObject().apply {
            put("client_id", b.clientId); put("nomor", b.nomor); put("plat", b.plat)
            put("jenis", b.jenis); put("total", b.total); put("metode", b.metode)
            put("created_at", b.createdAt)
            put("items", JSONArray().apply {
                b.items.forEach { i ->
                    put(JSONObject().apply {
                        put("tipe", i.tipe); put("nama", i.nama); put("harga", i.harga); put("qty", i.qty)
                    })
                }
            })
        }
        writableDatabase.insert("outbox", null, ContentValues().apply {
            put("entitas", "bon"); put("client_id", b.clientId); put("payload", js.toString())
            put("created_at", System.currentTimeMillis())
        })
    }

    fun bonHariIni(): List<Pair<String, Int>> {
        val awal = awalHari()
        val out = mutableListOf<Pair<String, Int>>()
        readableDatabase.rawQuery(
            "SELECT nomor, total FROM bon WHERE created_at >= ? ORDER BY id DESC", arrayOf(awal.toString())
        ).use { c -> while (c.moveToNext()) out += c.getString(0) to c.getInt(1) }
        return out
    }

    fun pendingSync(): Int = readableDatabase.rawQuery(
        "SELECT COUNT(*) FROM outbox WHERE status='pending'", null
    ).use { if (it.moveToNext()) it.getInt(0) else 0 }

    fun outbox(): List<Triple<Long, String, String>> {
        val out = mutableListOf<Triple<Long, String, String>>()
        readableDatabase.rawQuery(
            "SELECT id, client_id, payload FROM outbox WHERE status='pending' ORDER BY id LIMIT 50", null
        ).use { c -> while (c.moveToNext()) out += Triple(c.getLong(0), c.getString(1), c.getString(2)) }
        return out
    }

    fun tandaiTerkirim(id: Long) {
        writableDatabase.execSQL("UPDATE outbox SET status='sent' WHERE id=?", arrayOf(id))
    }

    fun tandaiGagal(id: Long, err: String) {
        writableDatabase.execSQL(
            "UPDATE outbox SET err=?, status='pending' WHERE id=?", arrayOf(err, id)
        )
    }

    fun bacaPengaturan(): Pengaturan {
        val m = mutableMapOf<String, String>()
        readableDatabase.rawQuery("SELECT kunci, nilai FROM pengaturan", null).use { c ->
            while (c.moveToNext()) m[c.getString(0)] = c.getString(1)
        }
        val d = Pengaturan()
        return Pengaturan(
            namaUsaha = m["nama_usaha"] ?: d.namaUsaha,
            alamat = m["alamat"] ?: d.alamat,
            noHp = m["no_hp"] ?: d.noHp,
            catatanNota = m["catatan_nota"] ?: d.catatanNota,
            autoUpdate = (m["auto_update"] ?: "1") == "1",
            paket = m["paket"] ?: d.paket,
            berlakuSampai = m["berlaku_sampai"] ?: d.berlakuSampai,
            idLisensi = m["id_lisensi"] ?: d.idLisensi,
        )
    }

    fun simpanPengaturan(p: Pengaturan) {
        val w = writableDatabase
        mapOf(
            "nama_usaha" to p.namaUsaha, "alamat" to p.alamat, "no_hp" to p.noHp,
            "catatan_nota" to p.catatanNota, "auto_update" to if (p.autoUpdate) "1" else "0",
            "paket" to p.paket, "berlaku_sampai" to p.berlakuSampai, "id_lisensi" to p.idLisensi,
        ).forEach { (k, v) -> w.insertWithOnConflict("pengaturan", null, ContentValues().apply {
            put("kunci", k); put("nilai", v)
        }, SQLiteDatabase.CONFLICT_REPLACE) }
    }

    companion object {
        @SuppressLint("ConstantLocale")
        private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale("in", "ID"))
        fun jam(ms: Long = System.currentTimeMillis()) = fmt.format(Date(ms))
        fun awalHari(): Long {
            val c = java.util.Calendar.getInstance()
            c.set(java.util.Calendar.HOUR_OF_DAY, 0); c.set(java.util.Calendar.MINUTE, 0)
            c.set(java.util.Calendar.SECOND, 0); c.set(java.util.Calendar.MILLISECOND, 0)
            return c.timeInMillis
        }
    }
}

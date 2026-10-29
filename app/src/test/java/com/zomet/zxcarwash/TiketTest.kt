package com.zomet.zxcarwash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tiket antrian: isi teks & bentuk byte ESC/POS. */
class TiketTest {

    private fun contoh(jam: Long = 1793269320000L) = Tiket.teks(
        namaUsaha = "Cuci Kantin Zomet",
        nomorBon = "CC-0007",
        bay = "Bay 2",
        plat = "KB 1234 XYZ",
        jenis = "mpv",
        paket = "Salon",
        durasiMenit = 45,
        jam = jam,
    )

    @Test
    fun memuatBayDanPlat() {
        val t = contoh()
        assertTrue(t.contains("BAY 2"))
        assertTrue(t.contains("KB 1234 XYZ"))
        assertTrue(t.contains("CC-0007"))
        assertTrue(t.contains("45 menit"))
    }

    @Test
    fun tanpaHargaSamaSekali() {
        // tiket antrian bukan bukti bayar — jangan ada angka uang
        val t = contoh()
        assertTrue(!t.contains("Rp"))
        assertTrue(!t.contains("TOTAL"))
    }

    @Test
    fun semuaBarisMaksimal32Kolom() {
        contoh().lines().filter { it.isNotEmpty() }.forEach { baris ->
            assertTrue("baris ${baris.length} kolom: $baris", baris.length <= Tiket.KOLOM)
        }
    }

    @Test
    fun byteMemuatPerintahBesarDanPotong() {
        val b = Tiket.bytes(
            namaUsaha = "Cuci Kantin Zomet",
            nomorBon = "CC-0007",
            bay = "Bay 2",
            plat = "KB 1234 XYZ",
            jenis = "mpv",
            paket = "Salon",
            durasiMenit = 45,
        )
        val teks = String(b, Charsets.ISO_8859_1)
        assertTrue(teks.contains("TIKET ANTRIAN"))
        assertTrue(teks.contains("BAY 2"))
        // perintah double width+height untuk nomor bay
        assertTrue(b.toList().containsAll(listOf<Byte>(0x1B, '!'.code.toByte(), 0x38)))
        // perintah potong kertas
        assertTrue(b.toList().windowed(3).any { it[0] == 0x1D.toByte() && it[1] == 'V'.code.toByte() })
    }

    @Test
    fun namaUsahaPanjangTetapTerbungkus() {
        val t = Tiket.teks(
            namaUsaha = "CUCI MOBIL DAN KANTIN ZOMET CABANG PONTIANAK",
            nomorBon = "CC-0001",
            bay = "Bay 1",
            plat = "KB 1 A",
            jenis = "suv",
            paket = "Premium",
            durasiMenit = 60,
        )
        assertEquals(1, t.lines().count { it.contains("PONTIANAK") || it.contains("CABANG") })
        t.lines().filter { it.isNotEmpty() }.forEach {
            assertTrue("baris terlalu panjang: $it", it.length <= Tiket.KOLOM)
        }
    }
}

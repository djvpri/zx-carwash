package com.zomet.zxcarwash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrukTest {

    private val pengaturan = Pengaturan()
    private val bon = Bon(
        nomor = "A-018", plat = "B 1234 XYZ", jenis = "MPV",
        items = listOf(
            ItemBon("cuci", "Salon MPV", 65000),
            ItemBon("addon", "Jamur kaca", 15000),
            ItemBon("kantin", "Nasi Goreng Spesial", 12000, 2),
            ItemBon("kantin", "Es Teh Manis", 4000, 2),
        ),
        diskon = 5600, metode = "Tunai", dibayar = 120000, createdAt = 1_700_000_000_000
    )

    @Test
    fun totalGabungan() {
        assertEquals(80000, bon.subtotalCuci)
        assertEquals(32000, bon.subtotalKantin)
        assertEquals(106400, bon.total)
        assertEquals(13600, bon.kembali)
    }

    @Test
    fun footerBrandingSelaluAda() {
        val nota = Struk.nota(bon, pengaturan)
        assertTrue(nota.contains("T E R I M A   K A S I H"))
        assertTrue(nota.contains("Powered by ZX Carwash App"))
    }

    @Test
    fun footerTetapAdaWalauCatatanKosong() {
        val nota = Struk.nota(bon, pengaturan.copy(catatanNota = ""))
        assertTrue(nota.contains("Powered by ZX Carwash App"))
        assertTrue(nota.contains("T E R I M A   K A S I H"))
    }

    @Test
    fun semuaBarisMuat32Kolom() {
        val nota = Struk.nota(bon, pengaturan.copy(
            namaUsaha = "CUCI KANTIN ZOMET PONTIANAK KALIMANTAN BARAT",
            alamat = "Jl. Melati 12, Kelurahan Sungai Jawi, Pontianak Kota"
        ))
        for (baris in nota.lines()) {
            assertTrue("baris > 32 kolom: '$baris' (${baris.length})", baris.length <= Struk.KOLOM)
        }
    }

    @Test
    fun bytesEscapePosBerisiPotongDanFooter() {
        val b = Struk.bytes(bon, pengaturan)
        val teks = String(b, Charsets.ISO_8859_1)
        assertTrue("harus ada init ESC @", b[0] == 0x1B.toByte() && b[1] == '@'.code.toByte())
        assertTrue(teks.contains("Powered by ZX Carwash App"))
        assertTrue(teks.contains("T E R I M A   K A S I H"))
        // GS V a n = perintah potong kertas
        val potong = byteArrayOf(0x1D, 'V'.code.toByte()).toList()
        assertTrue("harus ada perintah potong", b.toList().windowed(2).contains(potong))
    }

    @Test
    fun rupiahFormatRibuan() {
        assertEquals("Rp 1.234.567", Struk.rupiah(1234567))
        assertEquals("0", Struk.angka(0))
    }
}

package com.zomet.zxcarwash

/**
 * Tiket antrian — kertas kecil untuk ditempel di kaca mobil atau diberikan ke
 * pelanggan saat bayar, supaya tukang tahu mobil mana yang harus dicuci.
 *
 * Format 32 kolom (58 mm), nomor bay besar, tanpa harga sama sekali: tiket
 * bukan bukti bayar, jadi jangan mencantumkan uang di situ.
 */
object Tiket {

    const val KOLOM = Struk.KOLOM
    private const val ESC = 0x1B
    private const val GS = 0x1D

    /**
     * @param namaUsaha   dari pengaturan
     * @param nomorBon    nomor sementara/akhir yang terlihat kasir
     * @param bay         nama bay, mis. "Bay 2"
     * @param plat        plat kendaraan
     * @param jenis       MPV / SUV / motor / pickup
     * @param paket       nama paket cuci, mis. "Salon"
     * @param durasiMenit perkiraan lama cuci
     * @param jam         waktu masuk (ms)
     */
    fun teks(
        namaUsaha: String,
        nomorBon: String,
        bay: String,
        plat: String,
        jenis: String,
        paket: String,
        durasiMenit: Int,
        jam: Long = System.currentTimeMillis(),
    ): String {
        val sb = StringBuilder()
        Struk.bungkus(namaUsaha.uppercase()).forEach { sb.append(Struk.tengah(it)).append('\n') }
        sb.append(Struk.tengah("TIKET ANTRIAN")).append('\n')
        sb.append("-".repeat(KOLOM)).append('\n')
        // nomor bay paling besar — dibaca dari jauh
        sb.append(Struk.tengah(bay.uppercase())).append('\n')
        sb.append(Struk.tengah(plat)).append('\n')
        sb.append("-".repeat(KOLOM)).append('\n')
        sb.append(Struk.duaKolom("Bon", nomorBon)).append('\n')
        sb.append(Struk.duaKolom("Kendaraan", jenis)).append('\n')
        sb.append(Struk.duaKolom("Layanan", paket)).append('\n')
        sb.append(Struk.duaKolom("Perkiraan", "$durasiMenit menit")).append('\n')
        sb.append(Struk.duaKolom("Masuk", Db.jam(jam).takeLast(5))).append('\n')
        sb.append("-".repeat(KOLOM)).append('\n')
        sb.append(Struk.tengah("Simpan tiket ini")).append('\n')
        sb.append(Struk.tengah("Powered by ZX Carwash App")).append('\n')
        return sb.toString()
    }

    private fun MutableList<Byte>.cmd(vararg v: Int) { v.forEach { add(it.toByte()) } }
    private fun MutableList<Byte>.teks(s: String) { s.toByteArray(Charsets.ISO_8859_1).forEach { add(it) } }

    /** Byte ESC/POS: nama bay dicetak double width + double height. */
    fun bytes(
        namaUsaha: String,
        nomorBon: String,
        bay: String,
        plat: String,
        jenis: String,
        paket: String,
        durasiMenit: Int,
        jam: Long = System.currentTimeMillis(),
    ): ByteArray {
        val o = mutableListOf<Byte>()
        o.cmd(ESC, '@'.code)
        o.cmd(ESC, 'a'.code, 1)               // center
        Struk.bungkus(namaUsaha.uppercase()).forEach { o.teks(it); o.cmd('\n'.code) }
        o.teks("TIKET ANTRIAN"); o.cmd('\n'.code)
        o.cmd(ESC, 'a'.code, 0)               // left
        o.teks("-".repeat(KOLOM)); o.cmd('\n'.code)

        // nomor bay: besar (0x38 = double width + double height)
        o.cmd(ESC, 'a'.code, 1)
        o.cmd(ESC, '!'.code, 0x38)
        o.cmd(ESC, 'E'.code, 1)
        o.teks(bay.uppercase()); o.cmd('\n'.code)
        o.cmd(ESC, '!'.code, 0x08)            // double height saja untuk plat
        o.teks(plat); o.cmd('\n'.code)
        o.cmd(ESC, '!'.code, 0x00)
        o.cmd(ESC, 'E'.code, 0)
        o.cmd(ESC, 'a'.code, 0)
        o.teks("-".repeat(KOLOM)); o.cmd('\n'.code)
        o.teks(Struk.duaKolom("Bon", nomorBon)); o.cmd('\n'.code)
        o.teks(Struk.duaKolom("Kendaraan", jenis)); o.cmd('\n'.code)
        o.teks(Struk.duaKolom("Layanan", paket)); o.cmd('\n'.code)
        o.teks(Struk.duaKolom("Perkiraan", "$durasiMenit menit")); o.cmd('\n'.code)
        o.teks(Struk.duaKolom("Masuk", Db.jam(jam).takeLast(5))); o.cmd('\n'.code)
        o.teks("-".repeat(KOLOM)); o.cmd('\n'.code)
        o.cmd(ESC, 'a'.code, 1)
        o.teks("Simpan tiket ini"); o.cmd('\n'.code)
        o.teks("Powered by ZX Carwash App"); o.cmd('\n'.code)
        o.cmd('\n'.code, '\n'.code)
        o.cmd(GS, 'V'.code, 66, 3)            // feed + potong
        return o.toByteArray()
    }
}

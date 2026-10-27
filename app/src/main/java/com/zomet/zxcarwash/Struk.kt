package com.zomet.zxcarwash

/**
 * Penyusun struk ESC/POS untuk printer thermal 58mm (32 kolom, font A).
 *
 * Murni fungsi byte -> bisa diuji unit test tanpa Android. Baris bawah
 * "TERIMA KASIH" + "Powered by ZX Carwash App" adalah konstanta branding,
 * tidak diambil dari Pengaturan.
 */
object Struk {

    const val KOLOM = 32
    private const val ESC = 0x1B
    private const val GS = 0x1D

    fun rupiah(n: Int): String {
        val s = n.toString()
        val b = StringBuilder()
        for ((i, c) in s.withIndex()) {
            if (i > 0 && (s.length - i) % 3 == 0) b.append('.')
            b.append(c)
        }
        return "Rp $b"
    }

    /** "12.000" tanpa "Rp" — dipakai kolom harga di struk. */
    fun angka(n: Int): String = rupiah(n).removePrefix("Rp ")

    fun duaKolom(kiri: String, kanan: String, lebar: Int = KOLOM): String {
        val maxKiri = (lebar - kanan.length - 1).coerceAtLeast(1)
        val k = if (kiri.length > maxKiri) kiri.take(maxKiri) else kiri
        return k + " ".repeat(lebar - k.length - kanan.length) + kanan
    }

    fun tengah(s: String, lebar: Int = KOLOM): String {
        if (s.length >= lebar) return s
        val pad = (lebar - s.length) / 2
        return " ".repeat(pad) + s
    }

    /** Bungkus teks panjang jadi beberapa baris <= lebar kolom. */
    fun bungkus(s: String, lebar: Int = KOLOM): List<String> {
        if (s.isEmpty()) return listOf("")
        val out = mutableListOf<String>()
        var rest = s
        while (rest.length > lebar) {
            var cut = rest.lastIndexOf(' ', lebar)
            if (cut <= 0) cut = lebar
            out += rest.substring(0, cut).trim()
            rest = rest.substring(cut).trim()
        }
        out += rest
        return out
    }

    /** Teks nota lengkap (dipakai juga untuk pratinjau di layar). */
    fun nota(b: Bon, p: Pengaturan): String {
        val sb = StringBuilder()
        bungkus(p.namaUsaha.uppercase()).forEach { sb.append(tengah(it)).append('\n') }
        bungkus(p.alamat).forEach { sb.append(tengah(it)).append('\n') }
        bungkus(p.noHp).forEach { sb.append(tengah(it)).append('\n') }
        sb.append("-".repeat(KOLOM)).append('\n')
        sb.append(duaKolom("Bon ${b.nomor} · ${Db.jam(b.createdAt)}", "")).append('\n')
        if (b.plat.isNotBlank()) sb.append(duaKolom(b.plat, b.jenis)).append('\n')
        sb.append("-".repeat(KOLOM)).append('\n')
        b.items.forEach { i ->
            val nama = if (i.qty > 1) "${i.qty}x ${i.nama}" else i.nama
            bungkus(nama, KOLOM - 9).forEachIndexed { idx, line ->
                sb.append(if (idx == 0) duaKolom(line, angka(i.subtotal)) else line).append('\n')
            }
        }
        sb.append("-".repeat(KOLOM)).append('\n')
        if (b.diskon > 0) sb.append(duaKolom("Diskon", "-" + angka(b.diskon))).append('\n')
        sb.append(duaKolom("TOTAL", angka(b.total))).append('\n')
        sb.append(duaKolom(b.metode, angka(b.dibayar))).append('\n')
        if (b.metode.equals("Tunai", true))
            sb.append(duaKolom("Kembali", angka(b.kembali))).append('\n')
        sb.append("-".repeat(KOLOM)).append('\n')
        if (p.catatanNota.isNotBlank())
            bungkus(p.catatanNota).take(3).forEach { sb.append(tengah(it)).append('\n') }
        sb.append("-".repeat(KOLOM)).append('\n')
        sb.append(tengah(spasiHuruf("TERIMA KASIH"))).append('\n')
        sb.append(tengah("Powered by ZX Carwash App")).append('\n')
        sb.append(tengah("${b.nomor} · ${Db.jam(b.createdAt).takeLast(5)}")).append('\n')
        return sb.toString()
    }

    fun spasiHuruf(s: String): String = s.toCharArray().joinToString(" ")

    private fun MutableList<Byte>.cmd(vararg v: Int) { v.forEach { add(it.toByte()) } }
    private fun MutableList<Byte>.teks(s: String) { s.toByteArray(Charsets.ISO_8859_1).forEach { add(it) } }

    /** Byte ESC/POS siap kirim ke printer. */
    fun bytes(b: Bon, p: Pengaturan): ByteArray {
        val o = mutableListOf<Byte>()
        o.cmd(ESC, '@'.code)                  // init
        o.cmd(ESC, 'a'.code, 1)               // center
        o.cmd(ESC, '!'.code, 0x08)            // double height (nama usaha)
        bungkus(p.namaUsaha.uppercase()).forEach { o.teks(it); o.cmd('\n'.code) }
        o.cmd(ESC, '!'.code, 0x00)
        bungkus(p.alamat).forEach { o.teks(it); o.cmd('\n'.code) }
        bungkus(p.noHp).forEach { o.teks(it); o.cmd('\n'.code) }
        o.cmd(ESC, 'a'.code, 0)               // left
        o.teks("-".repeat(KOLOM)); o.cmd('\n'.code)
        o.teks(duaKolom("Bon ${b.nomor} · ${Db.jam(b.createdAt)}", "")); o.cmd('\n'.code)
        if (b.plat.isNotBlank()) { o.teks(duaKolom(b.plat, b.jenis)); o.cmd('\n'.code) }
        o.teks("-".repeat(KOLOM)); o.cmd('\n'.code)
        b.items.forEach { i ->
            val nama = if (i.qty > 1) "${i.qty}x ${i.nama}" else i.nama
            bungkus(nama, KOLOM - 9).forEachIndexed { idx, line ->
                o.teks(if (idx == 0) duaKolom(line, angka(i.subtotal)) else line); o.cmd('\n'.code)
            }
        }
        o.teks("-".repeat(KOLOM)); o.cmd('\n'.code)
        if (b.diskon > 0) { o.teks(duaKolom("Diskon", "-" + angka(b.diskon))); o.cmd('\n'.code) }
        o.cmd(ESC, '!'.code, 0x08)            // total ditebalkan lewat ukuran
        o.teks(duaKolom("TOTAL", angka(b.total))); o.cmd('\n'.code)
        o.cmd(ESC, '!'.code, 0x00)
        o.teks(duaKolom(b.metode, angka(b.dibayar))); o.cmd('\n'.code)
        if (b.metode.equals("Tunai", true)) {
            o.teks(duaKolom("Kembali", angka(b.kembali))); o.cmd('\n'.code)
        }
        o.teks("-".repeat(KOLOM)); o.cmd('\n'.code)
        if (p.catatanNota.isNotBlank()) {
            o.cmd(ESC, 'a'.code, 1)
            bungkus(p.catatanNota).take(3).forEach { o.teks(it); o.cmd('\n'.code) }
        }
        o.teks("-".repeat(KOLOM)); o.cmd('\n'.code)
        o.cmd(ESC, 'a'.code, 1)
        o.cmd(ESC, 'E'.code, 1)               // bold
        o.teks(spasiHuruf("TERIMA KASIH")); o.cmd('\n'.code)
        o.cmd(ESC, 'E'.code, 0)
        o.teks("Powered by ZX Carwash App"); o.cmd('\n'.code)
        o.teks("${b.nomor} · ${Db.jam(b.createdAt).takeLast(5)}"); o.cmd('\n'.code)
        o.cmd('\n'.code, '\n'.code)
        o.cmd(GS, 'V'.code, 66, 3)            // feed + potong
        return o.toByteArray()
    }
}

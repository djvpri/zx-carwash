package com.zomet.zxcarwash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.TimeZone

/** Format tanggal & jam dipakai layar Antrian dan Laporan. */
class FmtTest {

    @Test
    fun jamMenurutZonaPerangkat() {
        val iso = "2026-10-29T02:00:00.000Z"
        val ms = Fmt.millis(iso)
        assertNotNull(ms)
        val c = java.util.Calendar.getInstance().apply { timeInMillis = ms!! }
        val harap = String.format(
            "%02d:%02d",
            c.get(java.util.Calendar.HOUR_OF_DAY),
            c.get(java.util.Calendar.MINUTE)
        )
        assertEquals(harap, Fmt.jam(iso))
    }

    @Test
    fun jamTidakValidJadiStrip() {
        assertEquals("-", Fmt.jam(null))
        assertEquals("-", Fmt.jam(""))
        assertEquals("-", Fmt.jam("bukan tanggal"))
    }

    @Test
    fun tanggalPanjangPakaiNamaBulan() {
        val tzAsli = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Jakarta"))
            // 28 Feb 2027 00:00 WIB = 27 Feb 17:00 UTC
            assertEquals("28 Feb 2027", Fmt.tanggalPanjang("2027-02-27T17:00:00.000Z"))
        } finally {
            TimeZone.setDefault(tzAsli)
        }
    }

    @Test
    fun tanggalPendekTidakError() {
        assertEquals("-", Fmt.tanggal(null))
        assertEquals("-", Fmt.tanggal("sampah"))
    }
}

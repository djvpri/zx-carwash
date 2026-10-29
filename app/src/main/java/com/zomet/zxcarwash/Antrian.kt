package com.zomet.zxcarwash

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tab Antrian: papan bay dari server.
 *
 * Data diambil dari `GET /v1/antrian` — bukan dari SQLite lokal, karena antrian
 * adalah keadaan bersama (bay dipakai bersama antar kasir). Kalau offline,
 * layar menampilkan pesan apa adanya, bukan daftar kosong yang menyesatkan.
 */
@Composable
fun LayarAntrian(
    sesi: Sesi.Info,
    onUbahStatus: (String, String, () -> Unit) -> Unit,
    onCetakTiket: (JSONObject) -> Unit,
) {
    var daftar by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var memuat by remember { mutableStateOf(true) }
    var galat by remember { mutableStateOf<String?>(null) }
    var saring by remember { mutableStateOf("aktif") }
    var versi by remember { mutableStateOf(0) }

    suspend fun tarik() {
        memuat = true
        galat = null
        val o = Api.antrian(sesi.token, if (saring == "aktif") null else saring)
        if (o == null) {
            galat = Api.lastError ?: "Gagal memuat antrian"
            memuat = false
            return
        }
        val arr = o.optJSONArray("antrian") ?: JSONArray()
        val list = mutableListOf<JSONObject>()
        for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { list += it }
        daftar = if (saring == "aktif") {
            list.filter { it.optString("status") in listOf("menunggu", "dikerjakan") }
        } else list
        memuat = false
    }

    LaunchedEffect(saring, versi) { tarik() }

    Column(Modifier.fillMaxSize().background(Latar)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf("aktif" to "Aktif", "selesai" to "Selesai", "semua" to "Semua").forEach { (k, label) ->
                Chip(label, saring == k) { saring = k }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { versi++ }) {
                Icon(Icons.Default.Refresh, "Muat ulang", Modifier.size(20.dp), tint = Biru)
            }
        }

        when {
            memuat -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Biru)
            }

            galat != null -> Kartu(Modifier.padding(14.dp)) {
                Text(galat!!, fontSize = 12.sp, color = OranyeTeks)
                Text(
                    "Antrian butuh koneksi — data ini dipakai bersama antar kasir.",
                    fontSize = 10.sp, color = TeksRedup, modifier = Modifier.padding(top = 6.dp)
                )
                OutlinedButton(
                    onClick = { versi++ },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(44.dp),
                    shape = RoundedCornerShape(10.dp)
                ) { Text("Coba lagi", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Teks) }
            }

            daftar.isEmpty() -> Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (saring == "aktif") "Tidak ada mobil di bay" else "Belum ada antrian",
                    fontSize = 13.sp, color = TeksRedup, textAlign = TextAlign.Center
                )
            }

            else -> LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(daftar) { a ->
                    val status = a.optString("status")
                    val bay = a.optJSONObject("bay")?.optString("nama") ?: "-"
                    val trx = a.optJSONObject("transaksi") ?: JSONObject()
                    val warna = when (status) {
                        "dikerjakan" -> OranyeTeks
                        "selesai" -> HijauTeks
                        "diambil" -> TeksRedup
                        else -> Biru
                    }
                    Column(
                        Modifier.fillMaxWidth()
                            .border(1.dp, Garis, RoundedCornerShape(14.dp))
                            .background(Kartu, RoundedCornerShape(14.dp))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(bay, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Teks)
                                Text(
                                    "${trx.optString("nomorBon", "-")} · ${trx.optString("plat", "-")} · ${trx.optString("jenis", "-")}",
                                    fontSize = 11.sp, color = TeksRedup
                                )
                            }
                            Text(
                                status.replaceFirstChar { it.uppercase() },
                                fontSize = 10.sp, fontWeight = FontWeight.Bold, color = warna,
                                modifier = Modifier.border(1.dp, warna, RoundedCornerShape(50))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        val mulai = if (a.isNull("mulaiAt")) null else a.optString("mulaiAt")
                        val selesai = if (a.isNull("selesaiAt")) null else a.optString("selesaiAt")
                        if (mulai != null) {
                            Text(
                                "Mulai ${Fmt.jam(mulai)}" + (selesai?.let { " · selesai ${Fmt.jam(it)}" } ?: ""),
                                fontSize = 10.sp, color = TeksRedup, modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                        val komisi = a.optInt("komisi", 0)
                        if (komisi > 0) {
                            Text("Komisi ${Rp(komisi)}", fontSize = 10.sp, color = HijauTeks,
                                modifier = Modifier.padding(top = 2.dp))
                        }

                        // aksi cepat sesuai status
                        val id = a.optString("id")
                        Row(
                            Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (status == "menunggu") TombolKecil("Mulai cuci", Biru) {
                                onUbahStatus(id, "dikerjakan") { versi++ }
                            }
                            if (status == "dikerjakan") {
                                TombolKecil("Selesai", HijauTeks) { onUbahStatus(id, "selesai") { versi++ } }
                            }
                            if (status == "selesai") {
                                TombolKecil("Sudah diambil", TeksRedup) { onUbahStatus(id, "diambil") { versi++ } }
                            }
                            TombolKecil("Tiket", TeksRedup) { onCetakTiket(a) }
                            if (status == "diambil") {
                                Text("Selesai & sudah diserahkan", fontSize = 10.sp, color = TeksRedup,
                                    modifier = Modifier.align(Alignment.CenterVertically))
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(14.dp)) }
            }
        }
    }
}

@Composable
private fun TombolKecil(label: String, warna: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Box(
        Modifier.border(1.dp, warna, RoundedCornerShape(10.dp))
            .background(Kartu, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = warna)
    }
}

/** Formatter tanggal/jam — dipakai layar antrian & laporan. */
object Fmt {
    /** ISO 8601 → "14:22" menurut zona perangkat. */
    fun jam(iso: String?): String {
        val ms = millis(iso) ?: return "-"
        val c = java.util.Calendar.getInstance().apply { timeInMillis = ms }
        return String.format("%02d:%02d", c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE))
    }

    /** ISO 8601 → "29 Okt". */
    fun tanggal(iso: String?): String {
        val ms = millis(iso) ?: return "-"
        val c = java.util.Calendar.getInstance().apply { timeInMillis = ms }
        val bulan = listOf("Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des")
        return "${c.get(java.util.Calendar.DAY_OF_MONTH)} ${bulan[c.get(java.util.Calendar.MONTH)]}"
    }

    /** ISO / tanggal penuh → "28 Feb 2027". */
    fun tanggalPanjang(iso: String?): String {
        val ms = millis(iso) ?: return "-"
        val c = java.util.Calendar.getInstance().apply { timeInMillis = ms }
        val bulan = listOf("Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des")
        return "${c.get(java.util.Calendar.DAY_OF_MONTH)} ${bulan[c.get(java.util.Calendar.MONTH)]} ${c.get(java.util.Calendar.YEAR)}"
    }

    fun millis(iso: String?): Long? {
        if (iso.isNullOrBlank()) return null
        return runCatching {
            // "2026-10-29T04:52:48.575Z" → Instant
            java.time.Instant.parse(iso).toEpochMilli()
        }.getOrNull() ?: runCatching {
            java.time.OffsetDateTime.parse(iso).toInstant().toEpochMilli()
        }.getOrNull()
    }
}

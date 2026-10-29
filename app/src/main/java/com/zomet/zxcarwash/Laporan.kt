package com.zomet.zxcarwash

import androidx.compose.foundation.background
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
 * Tab Laporan: hari ini + periode 7/30 hari dari server.
 *
 * Angka datang dari endpoint laporan di server (dihitung dengan offset WIB),
 * jadi tidak ada penjumlahan di app yang bisa berbeda dengan server.
 */
@Composable
fun LayarLaporan(
    sesi: Sesi.Info,
    onCetakUlang: (JSONObject) -> Unit,
) {
    var periode by remember { mutableStateOf(7) }
    var hari by remember { mutableStateOf<JSONObject?>(null) }
    var bonTerakhir by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var rentang by remember { mutableStateOf<JSONObject?>(null) }
    var memuat by remember { mutableStateOf(true) }
    var galat by remember { mutableStateOf<String?>(null) }
    var versi by remember { mutableStateOf(0) }

    LaunchedEffect(periode, versi) {
        memuat = true
        galat = null
        val (h, p, trx) = withContext(Dispatchers.IO) {
            Triple(
                Api.laporanHarian(sesi.token),
                Api.laporanPeriode(sesi.token, periode),
                Api.transaksi(sesi.token, null, 8)
            )
        }
        if (h == null && p == null) {
            galat = Api.lastError ?: "Gagal memuat laporan"
        } else {
            hari = h
            rentang = p
            val arr = trx?.optJSONArray("transaksi")
            val list = mutableListOf<JSONObject>()
            if (arr != null) for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { list += it }
            bonTerakhir = list
        }
        memuat = false
    }

    Column(Modifier.fillMaxSize().background(Latar)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(1 to "Hari ini", 7 to "7 hari", 30 to "30 hari").forEach { (n, label) ->
                Chip(label, periode == n) { periode = n }
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
                Text("Laporan dihitung di server — butuh koneksi.",
                    fontSize = 10.sp, color = TeksRedup, modifier = Modifier.padding(top = 6.dp))
                OutlinedButton(
                    onClick = { versi++ },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(44.dp),
                    shape = RoundedCornerShape(10.dp)
                ) { Text("Coba lagi", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Teks) }
            }

            else -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(14.dp)) {
                val h = hari ?: JSONObject()
                val omzetHari = h.optInt("omzet", 0)
                val bonHari = h.optInt("bon", 0)
                val mobilHari = h.optInt("mobil", 0)

                Kartu {
                    Text("HARI INI", fontSize = 10.sp, letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold, color = TeksRedup)
                    Text(Rp(omzetHari), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Teks,
                        modifier = Modifier.padding(top = 2.dp))
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KotakAngka("Bon", bonHari.toString(), Modifier.weight(1f))
                        KotakAngka("Mobil", mobilHari.toString(), Modifier.weight(1f))
                        KotakAngka("Komisi", Rp(h.optInt("komisi", 0)), Modifier.weight(1.4f))
                    }
                    HorizontalDivider(Modifier.padding(vertical = 10.dp), color = Garis)
                    BarisInfo("Cuci", Rp(h.optInt("cuci", 0)))
                    BarisInfo("Kantin", Rp(h.optInt("kantin", 0)))
                    BarisInfo("Diskon", Rp(h.optInt("diskon", 0)))
                }

                if (bonTerakhir.isNotEmpty()) {
                    JudulKecil("Bon terakhir")
                    Kartu {
                        bonTerakhir.forEach { t ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        t.optString("nomorBon", "-") + " · " + t.optString("plat", "-"),
                                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Teks
                                    )
                                    Text(
                                        Fmt.jam(t.optString("createdAt")) + " · " +
                                            if (t.optString("status") == "bayar") "lunas" else "belum bayar",
                                        fontSize = 10.sp,
                                        color = if (t.optString("status") == "bayar") TeksRedup else OranyeTeks
                                    )
                                }
                                Text(
                                    Rp(t.optInt("total", 0)), fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold, color = Teks
                                )
                                Text(
                                    "cetak",
                                    fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Biru,
                                    modifier = Modifier
                                        .clickable { onCetakUlang(t) }
                                        .padding(start = 10.dp)
                                )
                            }
                        }
                    }
                }

                val metode = h.optJSONObject("byMetode")
                if (metode != null && metode.length() > 0) {
                    JudulKecil("Per metode bayar")
                    Kartu {
                        metode.keys().forEach { k ->
                            BarisInfo(k.replaceFirstChar { it.uppercase() }, Rp(metode.optInt(k, 0)))
                        }
                    }
                }

                val jam = h.optJSONArray("jamSibuk")
                if (jam != null && jam.length() > 0) {
                    JudulKecil("Jam ramai hari ini")
                    Kartu {
                        var puncak = 1
                        for (i in 0 until jam.length()) {
                            val v = jam.optJSONObject(i)?.optInt("omzet", 0) ?: 0
                            if (v > puncak) puncak = v
                        }
                        for (i in 0 until jam.length()) {
                            val o = jam.optJSONObject(i) ?: continue
                            val j = o.optInt("jam", 0)
                            val v = o.optInt("omzet", 0)
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text("${j.toString().padStart(2, '0')}:00", fontSize = 11.sp, color = TeksRedup,
                                    modifier = Modifier.width(46.dp))
                                Box(
                                    Modifier.height(10.dp).weight(1f).background(Garis, RoundedCornerShape(50))
                                ) {
                                    Box(
                                        Modifier.fillMaxWidth((v.toFloat() / puncak).coerceIn(0.06f, 1f))
                                            .fillMaxHeight().background(Biru, RoundedCornerShape(50))
                                    )
                                }
                                Text(Rp(v), fontSize = 10.sp, color = TeksRedup,
                                    modifier = Modifier.width(78.dp).padding(start = 6.dp),
                                    textAlign = TextAlign.End)
                            }
                        }
                    }
                }

                val pr = rentang ?: JSONObject()
                if (periode > 1) {
                    JudulKecil("$periode hari terakhir")
                    Kartu {
                        Text(Rp(pr.optInt("omzet", 0)), fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold, color = Teks)
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            KotakAngka("Bon", pr.optInt("bon", 0).toString(), Modifier.weight(1f))
                            KotakAngka("Cuci", Rp(pr.optInt("cuci", 0)), Modifier.weight(1.4f))
                            KotakAngka("Kantin", Rp(pr.optInt("kantin", 0)), Modifier.weight(1.4f))
                        }
                        val hariArr = pr.optJSONArray("hari")
                        if (hariArr != null && hariArr.length() > 1) {
                            HorizontalDivider(Modifier.padding(vertical = 10.dp), color = Garis)
                            GrafikBatang(hariArr)
                        }
                    }

                    val terlaris = pr.optJSONArray("terlaris")
                    if (terlaris != null && terlaris.length() > 0) {
                        JudulKecil("Terlaris")
                        Kartu {
                            for (i in 0 until minOf(terlaris.length(), 6)) {
                                val o = terlaris.optJSONObject(i) ?: continue
                                val tipe = o.optString("tipe")
                                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                    Column(Modifier.weight(1f)) {
                                        Text(o.optString("nama", "-"), fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold, color = Teks)
                                        Text("$tipe · ${o.optInt("qty", 0)}x", fontSize = 10.sp, color = TeksRedup)
                                    }
                                    Text(Rp(o.optInt("omzet", 0)), fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold, color = Teks)
                                }
                            }
                        }
                    }

                    val komisi = pr.optJSONArray("komisi")
                    if (komisi != null && komisi.length() > 0) {
                        JudulKecil("Komisi tukang")
                        Kartu {
                            for (i in 0 until komisi.length()) {
                                val o = komisi.optJSONObject(i) ?: continue
                                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                    Column(Modifier.weight(1f)) {
                                        Text(o.optString("nama", "-"), fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold, color = Teks)
                                        Text("${o.optInt("mobil", 0)} mobil", fontSize = 10.sp, color = TeksRedup)
                                    }
                                    Text(Rp(o.optInt("komisi", 0)), fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold, color = HijauTeks)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun KotakAngka(label: String, nilai: String, modifier: Modifier = Modifier) {
    Column(
        modifier.border(1.dp, Garis, RoundedCornerShape(10.dp))
            .background(Latar, RoundedCornerShape(10.dp)).padding(8.dp)
    ) {
        Text(label.uppercase(), fontSize = 9.sp, letterSpacing = 0.5.sp,
            fontWeight = FontWeight.Bold, color = TeksRedup)
        Text(nilai, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Teks)
    }
}

/** Grafik batang harian digambar sendiri — tanpa library chart. */
@Composable
private fun GrafikBatang(hariArr: JSONArray) {
    val nilai = mutableListOf<Int>()
    for (i in 0 until hariArr.length()) nilai += hariArr.optJSONObject(i)?.optInt("omzet", 0) ?: 0
    val puncak = (nilai.maxOrNull() ?: 0).coerceAtLeast(1)

    Row(
        Modifier.fillMaxWidth().height(78.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        nilai.forEach { v ->
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .background(Garis.copy(alpha = 0.35f), RoundedCornerShape(3.dp)),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    Modifier.fillMaxWidth()
                        .fillMaxHeight((v.toFloat() / puncak).coerceIn(0.04f, 1f))
                        .background(Biru, RoundedCornerShape(3.dp))
                )
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("terlama", fontSize = 9.sp, color = TeksRedup)
        Text("terbaru", fontSize = 9.sp, color = TeksRedup)
    }
}

package com.zomet.zxcarwash

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

/**
 * Layar Shift: buka kas, tutup kas (opname), dan lunasi bon piutang.
 *
 * Tiga hal yang dijaga:
 *  - tutup shift butuh kas fisik; sistem menghitung sendiri kas seharusnya
 *  - piutang (`status=open`) dilunasi di sini, bukan di layar kasir
 *  - angka selalu diambil ulang dari server setelah aksi, bukan ditebak
 */
@Composable
fun LayarShift(
    sesi: Sesi.Info,
    onBuka: (Int, () -> Unit) -> Unit,
    onTutup: (Int, String, () -> Unit) -> Unit,
    onBayarBon: (String, String, Int, () -> Unit) -> Unit,
    onRekap: (String, (JSONObject?) -> Unit) -> Unit,
) {
    var shiftAktif by remember { mutableStateOf<JSONObject?>(null) }
    var riwayat by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var piutang by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var memuat by remember { mutableStateOf(true) }
    var galat by remember { mutableStateOf<String?>(null) }
    var versi by remember { mutableStateOf(0) }
    var dialogBuka by remember { mutableStateOf(false) }
    var dialogTutup by remember { mutableStateOf(false) }
    var bayar by remember { mutableStateOf<JSONObject?>(null) }
    var rekap by remember { mutableStateOf<JSONObject?>(null) }
    var rekapMemuat by remember { mutableStateOf(false) }

    LaunchedEffect(versi) {
        memuat = true
        galat = null
        val s = Api.shift(sesi.token)
        val p = Api.transaksi(sesi.token, "open")
        if (s == null && p == null) {
            galat = Api.lastError ?: "Gagal memuat shift"
        } else {
            shiftAktif = if (s?.isNull("aktif") == false) s.optJSONObject("aktif") else null
            val arr = s?.optJSONArray("shift") ?: JSONArray()
            val list = mutableListOf<JSONObject>()
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { list += it }
            riwayat = list
            val arr2 = p?.optJSONArray("transaksi") ?: JSONArray()
            val list2 = mutableListOf<JSONObject>()
            for (i in 0 until arr2.length()) arr2.optJSONObject(i)?.let { list2 += it }
            piutang = list2
        }
        memuat = false
    }

    Column(Modifier.fillMaxSize().background(Latar)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Shift & kas", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Teks)
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
                OutlinedButton(
                    onClick = { versi++ },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(44.dp),
                    shape = RoundedCornerShape(10.dp)
                ) { Text("Coba lagi", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Teks) }
            }

            else -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(14.dp)) {
                val aktif = shiftAktif
                if (aktif == null) {
                    Kartu {
                        Text("Belum ada shift terbuka", fontSize = 13.sp,
                            fontWeight = FontWeight.Bold, color = Teks)
                        Text(
                            "Buka shift dengan mencatat kas awal di laci. Semua transaksi setelah ini menempel ke shift tersebut.",
                            fontSize = 11.sp, color = TeksRedup, modifier = Modifier.padding(top = 4.dp)
                        )
                        Button(
                            onClick = { dialogBuka = true },
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(46.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Biru)
                        ) { Text("Buka shift", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                    }
                } else {
                    Kartu {
                        Text("SHIFT BERJALAN", fontSize = 10.sp, letterSpacing = 1.sp,
                            fontWeight = FontWeight.Bold, color = TeksRedup)
                        Text("Kas awal ${Rp(aktif.optInt("kasAwal", 0))}", fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold, color = Teks,
                            modifier = Modifier.padding(top = 2.dp))
                        Text(
                            "Dibuka ${Fmt.jam(aktif.optString("bukaAt"))} · " +
                                (aktif.optJSONObject("kasir")?.optString("nama") ?: "kasir"),
                            fontSize = 11.sp, color = TeksRedup, modifier = Modifier.padding(top = 2.dp)
                        )
                        Button(
                            onClick = { dialogTutup = true },
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(46.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Oranye)
                        ) { Text("Tutup shift", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                    }
                }

                JudulKecil("Piutang belum dibayar (${piutang.size})")
                if (piutang.isEmpty()) {
                    Kartu { Text("Tidak ada bon menunggak", fontSize = 12.sp, color = TeksRedup) }
                } else {
                    piutang.forEach { t ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                .border(1.dp, Garis, RoundedCornerShape(12.dp))
                                .background(Kartu, RoundedCornerShape(12.dp))
                                .clickable { bayar = t }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${t.optString("nomorBon", "-")} · ${t.optString("plat", "-")}",
                                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Teks
                                )
                                Text(
                                    "${Fmt.tanggal(t.optString("createdAt"))} ${Fmt.jam(t.optString("createdAt"))} · " +
                                        "${t.optJSONArray("items")?.length() ?: 0} item",
                                    fontSize = 10.sp, color = TeksRedup
                                )
                            }
                            Text(Rp(t.optInt("total", 0)), fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold, color = OranyeTeks)
                        }
                    }
                }

                if (riwayat.isNotEmpty()) {
                    JudulKecil("Riwayat shift")
                    Kartu {
                        riwayat.take(6).forEach { s ->
                            val tutup = !s.isNull("tutupAt")
                            val selisih = s.optInt("selisih", 0)
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "${Fmt.tanggal(s.optString("bukaAt"))} ${Fmt.jam(s.optString("bukaAt"))}" +
                                            if (tutup) " – ${Fmt.jam(s.optString("tutupAt"))}" else " (terbuka)",
                                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Teks
                                    )
                                    Text(
                                        "kas awal ${Rp(s.optInt("kasAwal", 0))}" +
                                            if (tutup) " · sistem ${Rp(s.optInt("kasSistem", 0))}" else "",
                                        fontSize = 10.sp, color = TeksRedup
                                    )
                                }
                                if (tutup) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            if (selisih == 0) "pas" else (if (selisih > 0) "+" else "") + Rp(selisih),
                                            fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                            color = if (selisih == 0) HijauTeks else OranyeTeks
                                        )
                                        Text(
                                            "rekap",
                                            fontSize = 10.sp, color = Biru, fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .clickable {
                                                    rekapMemuat = true
                                                    rekap = null
                                                    onRekap(s.optString("id")) { r ->
                                                        rekap = r
                                                        rekapMemuat = false
                                                    }
                                                }
                                                .padding(top = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (dialogBuka) {
        DialogKas(
            judul = "Buka shift",
            label = "Kas awal di laci",
            onBatal = { dialogBuka = false },
            onKirim = { nilai, _ ->
                onBuka(nilai) { dialogBuka = false; versi++ }
            }
        )
    }

    if (dialogTutup) {
        DialogKas(
            judul = "Tutup shift",
            label = "Kas fisik di laci (hitung uang)",
            catatan = true,
            onBatal = { dialogTutup = false },
            onKirim = { nilai, note ->
                onTutup(nilai, note) { dialogTutup = false; versi++ }
            }
        )
    }

    if (rekapMemuat) {
        Box(Modifier.fillMaxSize().background(Teks.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
            Column(
                Modifier.background(Kartu, RoundedCornerShape(14.dp)).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Biru)
                Text("Memuat rekap…", fontSize = 11.sp, color = TeksRedup, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }

    val rk = rekap
    if (rk != null) DialogRekap(rk) { rekap = null }

    val b = bayar
    if (b != null) {
        DialogBayarPiutang(
            bon = b,
            onBatal = { bayar = null },
            onBayar = { metode, jumlah ->
                onBayarBon(b.optString("id"), metode, jumlah) { bayar = null; versi++ }
            }
        )
    }
}

/** Rekap satu shift: omzet per metode, kas seharusnya, selisih. */
@Composable
private fun DialogRekap(rekap: JSONObject, onTutup: () -> Unit) {
    val shift = rekap.optJSONObject("shift") ?: JSONObject()
    val byMetode = rekap.optJSONObject("byMetode") ?: JSONObject()
    val adaFisik = !shift.isNull("kasFisik")
    val selisih = rekap.optInt("selisih", 0)

    AlertDialog(
        onDismissRequest = onTutup,
        title = { Text("Rekap shift", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                Text(
                    Fmt.tanggal(shift.optString("bukaAt")) + " " + Fmt.jam(shift.optString("bukaAt")) + " - " +
                        Fmt.jam(shift.optString("tutupAt")),
                    fontSize = 11.sp, color = TeksRedup
                )
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Garis)
                BarisInfo("Bon", rekap.optInt("bon", 0).toString())
                BarisInfo("Omzet", Rp(rekap.optInt("omzet", 0)))
                BarisInfo("Cuci", Rp(rekap.optInt("cuci", 0)))
                BarisInfo("Kantin", Rp(rekap.optInt("kantin", 0)))
                BarisInfo("Diskon", Rp(rekap.optInt("diskon", 0)))
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Garis)
                byMetode.keys().forEach { k ->
                    BarisInfo(k.replaceFirstChar { it.uppercase() }, Rp(byMetode.optInt(k, 0)))
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Garis)
                BarisInfo("Kas awal", Rp(shift.optInt("kasAwal", 0)))
                BarisInfo("Kas seharusnya", Rp(rekap.optInt("kasSistem", 0)))
                if (adaFisik) {
                    BarisInfo("Kas fisik", Rp(shift.optInt("kasFisik", 0)))
                    Row(
                        Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Selisih", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Teks)
                        Text(
                            if (selisih == 0) "pas" else (if (selisih > 0) "+" else "-") + Rp(Math.abs(selisih)),
                            fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                            color = if (selisih == 0) HijauTeks else OranyeTeks
                        )
                    }
                }
                val note = shift.optString("catatan", "").removePrefix("[demo] ")
                if (note.isNotBlank()) {
                    Text(note, fontSize = 10.sp, color = TeksRedup, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onTutup,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Biru)
            ) { Text("Tutup", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        }
    )
}

@Composable
private fun DialogKas(
    judul: String,
    label: String,
    catatan: Boolean = false,
    onBatal: () -> Unit,
    onKirim: (Int, String) -> Unit,
) {
    var nilai by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onBatal,
        title = { Text(judul, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = nilai, onValueChange = { nilai = it.filter(Char::isDigit) },
                    label = { Text(label, fontSize = 11.sp) }, singleLine = true,
                    textStyle = TextStyle(fontSize = 15.sp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                if (nilai.isNotBlank()) {
                    Text(Rp(nilai.toIntOrNull() ?: 0), fontSize = 11.sp, color = TeksRedup,
                        modifier = Modifier.padding(top = 4.dp))
                }
                if (catatan) {
                    OutlinedTextField(
                        value = note, onValueChange = { note = it },
                        label = { Text("Catatan (opsional)", fontSize = 11.sp) },
                        textStyle = TextStyle(fontSize = 13.sp),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onKirim(nilai.toIntOrNull() ?: 0, note) },
                enabled = nilai.isNotBlank(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Biru)
            ) { Text("Simpan", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onBatal) { Text("Batal", fontSize = 13.sp) } }
    )
}

@Composable
private fun DialogBayarPiutang(
    bon: JSONObject,
    onBatal: () -> Unit,
    onBayar: (String, Int) -> Unit,
) {
    val total = bon.optInt("total", 0)
    var metode by remember { mutableStateOf("tunai") }
    var dibayar by remember { mutableStateOf(total.toString()) }
    val nilai = dibayar.toIntOrNull() ?: 0

    AlertDialog(
        onDismissRequest = onBatal,
        title = { Text("Lunasi ${bon.optString("nomorBon")}", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Total ${Rp(total)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Teks)
                Row(Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("tunai" to "Tunai", "qris" to "QRIS", "transfer" to "Transfer").forEach { (k, l) ->
                        Chip(l, metode == k) { metode = k }
                    }
                }
                OutlinedTextField(
                    value = dibayar, onValueChange = { dibayar = it.filter(Char::isDigit) },
                    label = { Text("Uang diterima", fontSize = 11.sp) }, singleLine = true,
                    textStyle = TextStyle(fontSize = 15.sp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                )
                val kembali = nilai - total
                Text(
                    if (kembali >= 0) "Kembali ${Rp(kembali)}" else "Kurang ${Rp(-kembali)}",
                    fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    color = if (kembali >= 0) HijauTeks else OranyeTeks,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onBayar(metode, nilai) },
                enabled = nilai >= total,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = HijauTeks)
            ) { Text("Tandai lunas", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onBatal) { Text("Batal", fontSize = 13.sp) } }
    )
}

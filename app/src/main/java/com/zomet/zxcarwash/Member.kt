package com.zomet.zxcarwash

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
 * Layar Member: daftar member, poin, saldo deposit, tambah member.
 *
 * Kasir bisa menambah poin/saldo langsung dari sini. Server yang menyimpan,
 * app menarik ulang supaya angka selalu angka server.
 */
@Composable
fun LayarMember(
    sesi: Sesi.Info,
    onSimpan: (JSONObject, () -> Unit) -> Unit,
    onSaldo: (String, Int, Int, () -> Unit) -> Unit,
) {
    var daftar by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var memuat by remember { mutableStateOf(true) }
    var galat by remember { mutableStateOf<String?>(null) }
    var versi by remember { mutableStateOf(0) }
    var cari by remember { mutableStateOf("") }
    var tambah by remember { mutableStateOf(false) }
    var ubah by remember { mutableStateOf<JSONObject?>(null) }
    var saldo by remember { mutableStateOf<JSONObject?>(null) }

    LaunchedEffect(versi) {
        memuat = true
        galat = null
        val o = Api.member(sesi.token)
        if (o == null) {
            galat = Api.lastError ?: "Gagal memuat member"
        } else {
            val arr = o.optJSONArray("member") ?: JSONArray()
            val list = mutableListOf<JSONObject>()
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { list += it }
            daftar = list
        }
        memuat = false
    }

    val tampil = remember(daftar, cari) {
        if (cari.isBlank()) daftar
        else daftar.filter {
            it.optString("nama").contains(cari, ignoreCase = true) ||
                it.optString("telepon").contains(cari, ignoreCase = true)
        }
    }

    Column(Modifier.fillMaxSize().background(Latar)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = cari, onValueChange = { cari = it },
                placeholder = { Text("Cari nama atau nomor HP", fontSize = 12.sp) },
                singleLine = true,
                textStyle = TextStyle(fontSize = 13.sp),
                modifier = Modifier.weight(1f).height(52.dp)
            )
            IconButton(onClick = { versi++ }) {
                Icon(Icons.Default.Refresh, "Muat ulang", Modifier.size(20.dp), tint = Biru)
            }
            IconButton(onClick = { tambah = true }) {
                Icon(Icons.Default.Add, "Tambah member", Modifier.size(22.dp), tint = Biru)
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

            tampil.isEmpty() -> Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (cari.isBlank()) "Belum ada member" else "Tidak ada yang cocok",
                    fontSize = 13.sp, color = TeksRedup, textAlign = TextAlign.Center
                )
            }

            else -> LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tampil) { m ->
                    val poin = m.optInt("poin", 0)
                    val saldoV = m.optInt("saldo", 0)
                    Column(
                        Modifier.fillMaxWidth()
                            .border(1.dp, Garis, RoundedCornerShape(14.dp))
                            .background(Kartu, RoundedCornerShape(14.dp))
                            .clickable { ubah = m }
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(m.optString("nama", "-"), fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold, color = Teks)
                                Text(
                                    listOfNotNull(
                                        m.optString("telepon", "").takeIf { it.isNotBlank() },
                                        m.optString("level", "").takeIf { it.isNotBlank() }
                                    ).joinToString(" · "),
                                    fontSize = 11.sp, color = TeksRedup
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("$poin poin", fontSize = 11.sp, color = Biru,
                                    fontWeight = FontWeight.Bold)
                                Text("saldo ${Rp(saldoV)}", fontSize = 11.sp, color = HijauTeks,
                                    fontWeight = FontWeight.Bold)
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TombolKecil("Isi saldo", HijauTeks) { saldo = m }
                            TombolKecil("Ubah", TeksRedup) { ubah = m }
                        }
                    }
                }
                item { Spacer(Modifier.height(14.dp)) }
            }
        }
    }

    val u = ubah
    if (tambah || u != null) {
        FormMember(
            awal = u,
            onBatal = { tambah = false; ubah = null },
            onSimpan = { o -> onSimpan(o) { tambah = false; ubah = null; versi++ } }
        )
    }

    val s = saldo
    if (s != null) {
        DialogSaldo(
            member = s,
            onBatal = { saldo = null },
            onSimpan = { poin, nominal -> onSaldo(s.optString("id"), poin, nominal) { saldo = null; versi++ } }
        )
    }
}

@Composable
private fun TombolKecil(label: String, warna: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Box(
        Modifier.border(1.dp, warna, RoundedCornerShape(8.dp))
            .background(Latar, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = warna) }
}

@Composable
private fun FormMember(awal: JSONObject?, onBatal: () -> Unit, onSimpan: (JSONObject) -> Unit) {
    var nama by remember { mutableStateOf(awal?.optString("nama") ?: "") }
    var telepon by remember { mutableStateOf(awal?.optString("telepon") ?: "") }
    var level by remember { mutableStateOf(awal?.optString("level")?.ifBlank { "reguler" } ?: "reguler") }

    AlertDialog(
        onDismissRequest = onBatal,
        title = {
            Text(if (awal == null) "Member baru" else "Ubah member",
                fontSize = 16.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                OutlinedTextField(
                    value = nama, onValueChange = { nama = it },
                    label = { Text("Nama", fontSize = 11.sp) }, singleLine = true,
                    textStyle = TextStyle(fontSize = 13.sp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = telepon, onValueChange = { telepon = it },
                    label = { Text("Nomor HP", fontSize = 11.sp) }, singleLine = true,
                    textStyle = TextStyle(fontSize = 13.sp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                Text("Level", fontSize = 11.sp, color = TeksRedup, modifier = Modifier.padding(top = 10.dp))
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("reguler", "silver", "gold").forEach { l ->
                        Chip(l.replaceFirstChar { it.uppercase() }, level == l) { level = l }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val o = JSONObject()
                    awal?.optString("id")?.takeIf { it.isNotBlank() }?.let { o.put("id", it) }
                    o.put("nama", nama.trim())
                    o.put("telepon", telepon.trim())
                    o.put("level", level)
                    onSimpan(o)
                },
                enabled = nama.isNotBlank(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Biru)
            ) { Text("Simpan", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onBatal) { Text("Batal", fontSize = 13.sp) } }
    )
}

@Composable
private fun DialogSaldo(member: JSONObject, onBatal: () -> Unit, onSimpan: (Int, Int) -> Unit) {
    var nominal by remember { mutableStateOf("") }
    var poin by remember { mutableStateOf("") }
    val saldoAwal = member.optInt("saldo", 0)
    // nilai negatif = tarik saldo
    var tarik by remember { mutableStateOf(false) }
    val n = nominal.toIntOrNull() ?: 0

    AlertDialog(
        onDismissRequest = onBatal,
        title = {
            Text("Saldo ${member.optString("nama")}", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text("Saldo sekarang ${Rp(saldoAwal)} · ${member.optInt("poin", 0)} poin",
                    fontSize = 12.sp, color = TeksRedup)
                Row(Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip("Isi saldo", !tarik) { tarik = false }
                    Chip("Tarik saldo", tarik) { tarik = true }
                }
                OutlinedTextField(
                    value = nominal, onValueChange = { nominal = it.filter(Char::isDigit) },
                    label = { Text(if (tarik) "Nominal ditarik" else "Nominal diisi", fontSize = 11.sp) },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 15.sp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                OutlinedTextField(
                    value = poin, onValueChange = { poin = it.filter(Char::isDigit) },
                    label = { Text("Tambah poin (opsional)", fontSize = 11.sp) }, singleLine = true,
                    textStyle = TextStyle(fontSize = 13.sp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                if (n > 0) {
                    val hasil = if (tarik) (saldoAwal - n).coerceAtLeast(0) else saldoAwal + n
                    Text("Saldo jadi ${Rp(hasil)}", fontSize = 11.sp, color = TeksRedup,
                        modifier = Modifier.padding(top = 6.dp))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val nominalAkhir = if (tarik) -n else n
                    onSimpan(poin.toIntOrNull() ?: 0, nominalAkhir)
                },
                enabled = n > 0 || poin.isNotBlank(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Biru)
            ) { Text("Simpan", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onBatal) { Text("Batal", fontSize = 13.sp) } }
    )
}

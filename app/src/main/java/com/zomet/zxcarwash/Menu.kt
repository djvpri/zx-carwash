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
 * Tab Menu: kelola produk kantin (harga, stok, tambah/kurangi stok).
 *
 * Server pemegang stok sebenarnya — app hanya mengirim mutasi, lalu menarik
 * ulang daftar supaya angka yang tampil selalu angka server.
 */
@Composable
fun LayarMenu(
    sesi: Sesi.Info,
    onSimpan: (JSONObject, () -> Unit) -> Unit,
    onMutasi: (String, String, Int, () -> Unit) -> Unit,
) {
    var daftar by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var memuat by remember { mutableStateOf(true) }
    var galat by remember { mutableStateOf<String?>(null) }
    var versi by remember { mutableStateOf(0) }
    var kritis by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<JSONObject?>(null) }
    var tambah by remember { mutableStateOf(false) }
    var mutasi by remember { mutableStateOf<JSONObject?>(null) }

    LaunchedEffect(kritis, versi) {
        memuat = true
        galat = null
        val o = Api.produk(sesi.token, kritis)
        if (o == null) {
            galat = Api.lastError ?: "Gagal memuat menu"
        } else {
            val arr = o.optJSONArray("produk") ?: JSONArray()
            val list = mutableListOf<JSONObject>()
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { list += it }
            daftar = list
        }
        memuat = false
    }

    Column(Modifier.fillMaxSize().background(Latar)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Chip("Semua", !kritis) { kritis = false }
            Chip("Stok kritis", kritis) { kritis = true }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { versi++ }) {
                Icon(Icons.Default.Refresh, "Muat ulang", Modifier.size(20.dp), tint = Biru)
            }
            IconButton(onClick = { tambah = true }) {
                Icon(Icons.Default.Add, "Tambah produk", Modifier.size(22.dp), tint = Biru)
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

            daftar.isEmpty() -> Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (kritis) "Tidak ada stok kritis" else "Belum ada produk kantin",
                    fontSize = 13.sp, color = TeksRedup, textAlign = TextAlign.Center
                )
            }

            else -> LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(daftar) { p ->
                    val stok = p.optInt("stok", 0)
                    val lacak = p.optBoolean("trackStok", true)
                    val habis = p.optBoolean("aktif", true).not()
                    Column(
                        Modifier.fillMaxWidth()
                            .border(1.dp, Garis, RoundedCornerShape(14.dp))
                            .background(Kartu, RoundedCornerShape(14.dp))
                            .clickable { edit = p }
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.optString("nama", "-"), fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold, color = if (habis) TeksRedup else Teks)
                                Text(
                                    listOfNotNull(
                                        p.optString("kategori", "").takeIf { it.isNotBlank() },
                                        Rp(p.optInt("harga", 0))
                                    ).joinToString(" · "),
                                    fontSize = 11.sp, color = TeksRedup
                                )
                            }
                            if (lacak) {
                                Text(
                                    "$stok",
                                    fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
                                    color = when {
                                        stok <= 0 -> OranyeTeks
                                        stok <= 5 -> OranyeTeks
                                        else -> HijauTeks
                                    },
                                    modifier = Modifier
                                        .border(1.dp, Garis, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            } else {
                                Text("tanpa stok", fontSize = 10.sp, color = TeksRedup)
                            }
                        }
                        if (lacak) {
                            Row(
                                Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TombolMenu("− 1") { onMutasi(p.optString("id"), "keluar", 1) { versi++ } }
                                TombolMenu("+ 1") { onMutasi(p.optString("id"), "masuk", 1) { versi++ } }
                                TombolMenu("+ 10") { onMutasi(p.optString("id"), "masuk", 10) { versi++ } }
                                Spacer(Modifier.weight(1f))
                                TombolMenu("Ubah") { edit = p }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(14.dp)) }
            }
        }
    }

    // dialog ubah / tambah
    val target = edit
    if (tambah || target != null) {
        FormProduk(
            awal = target,
            onBatal = { tambah = false; edit = null },
            onSimpan = { o ->
                onSimpan(o) {
                    tambah = false; edit = null; versi++
                }
            }
        )
    }
}

@Composable
private fun TombolMenu(label: String, onClick: () -> Unit) {
    Box(
        Modifier.border(1.dp, Garis, RoundedCornerShape(8.dp))
            .background(Latar, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Teks)
    }
}

@Composable
private fun FormProduk(awal: JSONObject?, onBatal: () -> Unit, onSimpan: (JSONObject) -> Unit) {
    var nama by remember { mutableStateOf(awal?.optString("nama") ?: "") }
    var kategori by remember { mutableStateOf(awal?.optString("kategori") ?: "") }
    var harga by remember { mutableStateOf((awal?.optInt("harga") ?: 0).toString()) }
    var stok by remember { mutableStateOf((awal?.optInt("stok") ?: 0).toString()) }
    var lacak by remember { mutableStateOf(awal?.optBoolean("trackStok") ?: true) }
    var aktif by remember { mutableStateOf(awal?.optBoolean("aktif") ?: true) }

    AlertDialog(
        onDismissRequest = onBatal,
        title = {
            Text(
                if (awal == null) "Produk baru" else "Ubah produk",
                fontSize = 16.sp, fontWeight = FontWeight.Bold
            )
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
                    value = kategori, onValueChange = { kategori = it },
                    label = { Text("Kategori", fontSize = 11.sp) }, singleLine = true,
                    textStyle = TextStyle(fontSize = 13.sp),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                OutlinedTextField(
                    value = harga, onValueChange = { harga = it.filter(Char::isDigit) },
                    label = { Text("Harga", fontSize = 11.sp) }, singleLine = true,
                    textStyle = TextStyle(fontSize = 13.sp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = stok, onValueChange = { stok = it.filter(Char::isDigit) },
                        label = { Text("Stok", fontSize = 11.sp) }, singleLine = true,
                        enabled = lacak,
                        textStyle = TextStyle(fontSize = 13.sp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Lacak", fontSize = 10.sp, color = TeksRedup)
                        Switch(checked = lacak, onCheckedChange = { lacak = it })
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Dijual (aktif)", fontSize = 12.sp, color = Teks)
                    Spacer(Modifier.weight(1f))
                    Switch(checked = aktif, onCheckedChange = { aktif = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val o = JSONObject()
                    awal?.optString("id")?.takeIf { it.isNotBlank() }?.let { o.put("id", it) }
                    o.put("nama", nama.trim())
                    o.put("kategori", kategori.trim())
                    o.put("harga", harga.toIntOrNull() ?: 0)
                    o.put("stok", stok.toIntOrNull() ?: 0)
                    o.put("trackStok", lacak)
                    o.put("aktif", aktif)
                    onSimpan(o)
                },
                enabled = nama.isNotBlank(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Biru)
            ) { Text("Simpan", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onBatal) { Text("Batal", fontSize = 13.sp) }
        }
    )
}

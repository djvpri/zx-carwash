package com.zomet.zxcarwash

import androidx.compose.foundation.background
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
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
 * Layar Layanan: paket cuci (harga per jenis kendaraan + komisi) dan add-on.
 *
 * Harga paket disimpan sebagai JSON `{motor, mpv, suv, pickup}` di server, jadi
 * satu paket punya empat harga. Setelah simpan, `Sesi.tarikMaster` dipanggil
 * ulang supaya peta nama→id di app ikut segar dan bon berikutnya memakai id
 * yang benar.
 */
@Composable
fun LayarLayanan(
    sesi: Sesi.Info,
    onSimpanPaket: (JSONObject, () -> Unit) -> Unit,
    onSimpanAddon: (JSONObject, () -> Unit) -> Unit,
) {
    var tab by remember { mutableStateOf(0) }
    var paket by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var addon by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var memuat by remember { mutableStateOf(true) }
    var galat by remember { mutableStateOf<String?>(null) }
    var versi by remember { mutableStateOf(0) }
    var editPaket by remember { mutableStateOf<JSONObject?>(null) }
    var editAddon by remember { mutableStateOf<JSONObject?>(null) }
    var baru by remember { mutableStateOf(false) }

    LaunchedEffect(versi) {
        memuat = true
        galat = null
        val (p, a) = withContext(Dispatchers.IO) {
            Api.paket(sesi.token) to Api.addon(sesi.token)
        }
        if (p == null && a == null) {
            galat = Api.lastError ?: "Gagal memuat layanan"
        } else {
            paket = jsonKeList(p?.optJSONArray("paket"))
            addon = jsonKeList(a?.optJSONArray("addon"))
        }
        memuat = false
    }

    Column(Modifier.fillMaxSize().background(Latar)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Chip("Paket cuci", tab == 0) { tab = 0 }
            Chip("Add-on", tab == 1) { tab = 1 }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { versi++ }) {
                Icon(Icons.Default.Refresh, "Muat ulang", Modifier.size(20.dp), tint = Biru)
            }
            IconButton(onClick = { baru = true }) {
                Icon(Icons.Default.Add, "Tambah", Modifier.size(22.dp), tint = Biru)
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

            tab == 0 -> LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (paket.isEmpty()) {
                    item { Kosong("Belum ada paket cuci") }
                }
                items(paket) { p ->
                    val harga = p.optJSONObject("harga") ?: JSONObject()
                    val aktif = p.optBoolean("aktif", true)
                    Column(
                        Modifier.fillMaxWidth()
                            .border(1.dp, Garis, RoundedCornerShape(14.dp))
                            .background(Kartu, RoundedCornerShape(14.dp))
                            .clickable { editPaket = p }
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(p.optString("nama", "-"), fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (aktif) Teks else TeksRedup)
                            Spacer(Modifier.width(8.dp))
                            Text("${p.optInt("durasi", 0)} mnt", fontSize = 10.sp, color = TeksRedup)
                            Spacer(Modifier.weight(1f))
                            if (p.optInt("komisi", 0) > 0) {
                                Text("komisi ${Rp(p.optInt("komisi", 0))}", fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold, color = HijauTeks)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        // empat harga sejajar — motor/MPV/SUV/pickup
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("motor" to "Motor", "mpv" to "MPV", "suv" to "SUV", "pickup" to "Pikap")
                                .forEach { (k, label) ->
                                    Column(
                                        Modifier.weight(1f)
                                            .background(Latar, RoundedCornerShape(8.dp))
                                            .padding(6.dp)
                                    ) {
                                        Text(label.uppercase(), fontSize = 8.sp, letterSpacing = 0.4.sp,
                                            fontWeight = FontWeight.Bold, color = TeksRedup)
                                        Text(
                                            Struk.angka(harga.optInt(k, 0)),
                                            fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Teks
                                        )
                                    }
                                }
                        }
                    }
                }
                item { Spacer(Modifier.height(14.dp)) }
            }

            else -> LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (addon.isEmpty()) {
                    item { Kosong("Belum ada add-on") }
                }
                items(addon) { a ->
                    Row(
                        Modifier.fillMaxWidth()
                            .border(1.dp, Garis, RoundedCornerShape(14.dp))
                            .background(Kartu, RoundedCornerShape(14.dp))
                            .clickable { editAddon = a }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(a.optString("nama", "-"), fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (a.optBoolean("aktif", true)) Teks else TeksRedup)
                            if (!a.optBoolean("aktif", true)) {
                                Text("tidak dijual", fontSize = 10.sp, color = TeksRedup)
                            }
                        }
                        Text(Rp(a.optInt("harga", 0)), fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold, color = Teks)
                    }
                }
                item { Spacer(Modifier.height(14.dp)) }
            }
        }
    }

    if (baru || editPaket != null) {
        val target = editPaket
        if (tab == 0) {
            FormPaket(
                awal = target,
                onBatal = { baru = false; editPaket = null },
                onSimpan = { o -> onSimpanPaket(o) { baru = false; editPaket = null; versi++ } }
            )
        } else {
            FormAddon(
                awal = editAddon,
                onBatal = { baru = false; editAddon = null },
                onSimpan = { o -> onSimpanAddon(o) { baru = false; editAddon = null; versi++ } }
            )
        }
    } else if (editAddon != null) {
        FormAddon(
            awal = editAddon,
            onBatal = { editAddon = null },
            onSimpan = { o -> onSimpanAddon(o) { editAddon = null; versi++ } }
        )
    }
}

/** JSONArray → List<JSONObject>; null jadi list kosong. */
private fun jsonKeList(arr: JSONArray?): List<JSONObject> {
    if (arr == null) return emptyList()
    val out = mutableListOf<JSONObject>()
    for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { out += it }
    return out
}

@Composable
private fun Kosong(teks: String) {
    Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
        Text(teks, fontSize = 13.sp, color = TeksRedup, textAlign = TextAlign.Center)
    }
}

@Composable
private fun FormPaket(awal: JSONObject?, onBatal: () -> Unit, onSimpan: (JSONObject) -> Unit) {
    var nama by remember { mutableStateOf(awal?.optString("nama") ?: "") }
    var durasi by remember { mutableStateOf((awal?.optInt("durasi") ?: 30).toString()) }
    var komisi by remember { mutableStateOf((awal?.optInt("komisi") ?: 0).toString()) }
    var aktif by remember { mutableStateOf(awal?.optBoolean("aktif") ?: true) }
    val hargaAwal = awal?.optJSONObject("harga")
    var motor by remember { mutableStateOf((hargaAwal?.optInt("motor") ?: 0).toString()) }
    var mpv by remember { mutableStateOf((hargaAwal?.optInt("mpv") ?: 0).toString()) }
    var suv by remember { mutableStateOf((hargaAwal?.optInt("suv") ?: 0).toString()) }
    var pickup by remember { mutableStateOf((hargaAwal?.optInt("pickup") ?: 0).toString()) }

    AlertDialog(
        onDismissRequest = onBatal,
        title = {
            Text(if (awal == null) "Paket cuci baru" else "Ubah paket",
                fontSize = 16.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                Modifier.heightIn(max = 460.dp)
                    .verticalScroll(androidx.compose.foundation.rememberScrollState())
            ) {
                OutlinedTextField(
                    value = nama, onValueChange = { nama = it },
                    label = { Text("Nama paket", fontSize = 11.sp) }, singleLine = true,
                    textStyle = TextStyle(fontSize = 13.sp),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AngkaField("Durasi (menit)", durasi, Modifier.weight(1f)) { durasi = it }
                    AngkaField("Komisi", komisi, Modifier.weight(1f)) { komisi = it }
                }
                Text("Harga per jenis kendaraan", fontSize = 11.sp, color = TeksRedup,
                    modifier = Modifier.padding(top = 10.dp))
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AngkaField("Motor", motor, Modifier.weight(1f)) { motor = it }
                    AngkaField("MPV", mpv, Modifier.weight(1f)) { mpv = it }
                }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AngkaField("SUV", suv, Modifier.weight(1f)) { suv = it }
                    AngkaField("Pikap", pickup, Modifier.weight(1f)) { pickup = it }
                }
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
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
                    o.put("durasi", durasi.toIntOrNull() ?: 30)
                    o.put("komisi", komisi.toIntOrNull() ?: 0)
                    o.put("aktif", aktif)
                    o.put(
                        "harga",
                        JSONObject()
                            .put("motor", motor.toIntOrNull() ?: 0)
                            .put("mpv", mpv.toIntOrNull() ?: 0)
                            .put("suv", suv.toIntOrNull() ?: 0)
                            .put("pickup", pickup.toIntOrNull() ?: 0)
                    )
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
private fun FormAddon(awal: JSONObject?, onBatal: () -> Unit, onSimpan: (JSONObject) -> Unit) {
    var nama by remember { mutableStateOf(awal?.optString("nama") ?: "") }
    var harga by remember { mutableStateOf((awal?.optInt("harga") ?: 0).toString()) }
    var aktif by remember { mutableStateOf(awal?.optBoolean("aktif") ?: true) }

    AlertDialog(
        onDismissRequest = onBatal,
        title = { Text(if (awal == null) "Add-on baru" else "Ubah add-on",
            fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = nama, onValueChange = { nama = it },
                    label = { Text("Nama add-on", fontSize = 11.sp) }, singleLine = true,
                    textStyle = TextStyle(fontSize = 13.sp),
                    modifier = Modifier.fillMaxWidth()
                )
                AngkaField("Harga", harga, Modifier.fillMaxWidth().padding(top = 8.dp)) { harga = it }
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
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
                    o.put("harga", harga.toIntOrNull() ?: 0)
                    o.put("aktif", aktif)
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
private fun AngkaField(
    label: String,
    nilai: String,
    modifier: Modifier = Modifier,
    onUbah: (String) -> Unit,
) {
    OutlinedTextField(
        value = nilai, onValueChange = { onUbah(it.filter(Char::isDigit)) },
        label = { Text(label, fontSize = 10.sp) }, singleLine = true,
        textStyle = TextStyle(fontSize = 13.sp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}


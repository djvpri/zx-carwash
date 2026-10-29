package com.zomet.zxcarwash

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Layar pembayaran + struk (pratinjau teks 32 kolom). */
@Composable
fun LayarBayar(
    bon: Bon,
    pengaturan: Pengaturan,
    onBatal: () -> Unit,
    onSelesai: (metode: String, dibayar: Int, cetak: Boolean) -> Unit,
) {
    var metode by remember { mutableStateOf("Tunai") }
    var dibayarTeks by remember { mutableStateOf(bon.total.toString()) }
    var cetak by remember { mutableStateOf(true) }
    val dibayar = dibayarTeks.toIntOrNull() ?: 0
    val final = bon.copy(metode = metode, dibayar = dibayar)

    Column(Modifier.fillMaxSize().background(Latar)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(14.dp)) {
            Kartu {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom) {
                    Text("TOTAL TAGIHAN", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TeksRedup)
                    Text(Rp(final.total), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Teks)
                }
                Spacer(Modifier.height(6.dp))
                BarisUang("Cuci (${final.items.count { it.tipe != "kantin" }} item)", final.subtotalCuci)
                BarisUang("Kantin (${final.items.filter { it.tipe == "kantin" }.sumOf { it.qty }} item)", final.subtotalKantin)
                if (final.diskon > 0) BarisUang("Diskon", -final.diskon)
            }

            JudulKecil("Metode")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Tunai", "QRIS", "Kartu").forEach {
                    Chip(it, metode == it) { metode = it }
                }
            }

            JudulKecil("Pembayaran")
            Kartu {
                OutlinedTextField(
                    value = dibayarTeks,
                    onValueChange = { dibayarTeks = it.filter { c -> c.isDigit() } },
                    label = { Text("Uang diterima") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                BarisUang("Kembalian", (dibayar - final.total).coerceAtLeast(0), tebal = true)
                if (dibayar > 0 && dibayar < final.total) {
                    Text("Uang kurang ${Rp(final.total - dibayar)}",
                        fontSize = 11.sp, color = OranyeTeks, fontWeight = FontWeight.SemiBold)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Checkbox(checked = cetak, onCheckedChange = { cetak = it })
                    Text("Cetak struk thermal", fontSize = 12.sp, color = Teks)
                }
            }

            JudulKecil("Pratinjau struk 58mm")
            Kartu {
                Text(
                    Struk.nota(final, pengaturan),
                    fontFamily = FontFamily.Monospace, fontSize = 9.sp,
                    lineHeight = 13.sp, color = Teks
                )
            }
            Spacer(Modifier.height(80.dp))
        }

        Surface(shadowElevation = 8.dp, color = Kartu) {
            Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onBatal,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("Batal", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Teks) }
                Button(
                    onClick = { onSelesai(metode, dibayar, cetak) },
                    enabled = metode != "Tunai" || dibayar >= final.total,
                    modifier = Modifier.weight(1.4f).height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Biru)
                ) {
                    Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (cetak) "Bayar & Cetak" else "Bayar", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun BarisUang(label: String, nilai: Int, tebal: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp, color = TeksRedup)
        Text(
            (if (nilai < 0) "-" else "") + Rp(kotlin.math.abs(nilai)),
            fontSize = 12.sp, color = if (tebal) OranyeTeks else Teks,
            fontWeight = if (tebal) FontWeight.Bold else FontWeight.Normal
        )
    }
}

/** Pengaturan usaha + lisensi + update. */
@Composable
fun LayarPengaturan(
    p: Pengaturan,
    versi: String,
    pendingSync: Int,
    statusUpdate: String?,
    sesi: Sesi.Info,
    onSimpan: (Pengaturan) -> Unit,
    onCekUpdate: () -> Unit,
    onPasang: () -> Unit,
    onScanPrinter: () -> Unit,
    lisensiPlan: String,
    lisensiAktif: Boolean,
    lisensiSampai: String,
    lisensiSisa: String,
    lisensiDariServer: Boolean,
    onKirimSekarang: () -> Unit,
    onTarikMaster: () -> Unit,
    onKeluar: () -> Unit,
    printerTersambung: Boolean,
) {
    var nama by remember(p) { mutableStateOf(p.namaUsaha) }
    var alamat by remember(p) { mutableStateOf(p.alamat) }
    var hp by remember(p) { mutableStateOf(p.noHp) }
    var catatan by remember(p) { mutableStateOf(p.catatanNota) }
    var auto by remember(p) { mutableStateOf(p.autoUpdate) }

    Column(Modifier.fillMaxSize().background(Latar)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(14.dp)) {
            JudulKecil("Identitas usaha")
            Kartu {
                FieldRingkas("Nama usaha", nama) { nama = it }
                FieldRingkas("Alamat usaha", alamat) { alamat = it }
                FieldRingkas("Nomor HP", hp) { hp = it }
                FieldRingkas("Catatan nota", catatan, banyakBaris = true) { catatan = it }
                Text("Catatan tampil di bawah struk, maks 3 baris.",
                    fontSize = 10.sp, color = TeksRedup)
            }

            JudulKecil("Langganan")
            Kartu {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(lisensiPlan, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Teks)
                        Text(
                            if (lisensiAktif) "langganan aktif" else "langganan tidak aktif",
                            fontSize = 11.sp,
                            color = if (lisensiAktif) HijauTeks else OranyeTeks
                        )
                    }
                    Text(
                        if (lisensiAktif) "Aktif" else "Habis",
                        fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        color = if (lisensiAktif) HijauTeks else OranyeTeks,
                        modifier = Modifier.border(
                            1.dp, if (lisensiAktif) HijauTeks else Oranye, RoundedCornerShape(50)
                        )
                            .background(TampilanChip(), RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                BarisInfo("Berlaku sampai", lisensiSampai)
                BarisInfo("Sisa", lisensiSisa)
                if (lisensiDariServer) {
                    BarisInfo("Sumber", "server Z One")
                } else {
                    Text(
                        "Status lisensi belum terbaca dari server — tekan Tarik master.",
                        fontSize = 10.sp, color = TeksRedup, modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }

            JudulKecil("Akun & sinkronisasi")
            Kartu {
                Text(sesi.nama.ifBlank { sesi.email }, fontSize = 13.sp,
                    fontWeight = FontWeight.Bold, color = Teks)
                Text(sesi.email, fontSize = 11.sp, color = TeksRedup)
                if (sesi.outletNama != null) BarisInfo("Outlet", sesi.outletNama)
                BarisInfo("Usaha", sesi.namaUsaha ?: "-")
                BarisInfo("Antrean sync", "$pendingSync transaksi")
                Row(Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onKirimSekarang,
                        modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(10.dp)) {
                        Icon(Icons.Default.CloudUpload, null, Modifier.size(15.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Kirim", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Teks)
                    }
                    OutlinedButton(onClick = onTarikMaster,
                        modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(10.dp)) {
                        Icon(Icons.Default.CloudDownload, null, Modifier.size(15.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Tarik master", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Teks)
                    }
                }
                TextButton(onClick = onKeluar, modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
                    Icon(Icons.Default.Logout, null, Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Keluar dari akun", fontSize = 12.sp, color = OranyeTeks)
                }
            }

            JudulKecil("Printer & aplikasi")
            Kartu {
                BarisInfo("Printer", if (printerTersambung) "tersambung" else "belum tersambung")
                OutlinedButton(onClick = onScanPrinter, modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    shape = RoundedCornerShape(10.dp)) {
                    Icon(Icons.Default.Bluetooth, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Pilih printer Bluetooth", fontSize = 12.sp)
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Garis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Auto update", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Teks)
                        Text("Cek otomatis saat app dibuka", fontSize = 11.sp, color = TeksRedup)
                    }
                    Switch(checked = auto, onCheckedChange = { auto = it })
                }
                BarisInfo("Versi aplikasi", versi)
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = onCekUpdate, modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(10.dp)) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Cek update terbaru", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Teks)
                }
                if (statusUpdate != null) {
                    Text(statusUpdate, fontSize = 11.sp, color = TeksRedup, modifier = Modifier.padding(top = 6.dp))
                    if (statusUpdate.startsWith("Versi")) {
                        Button(onClick = onPasang, modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Biru)) {
                            Text("Update sekarang", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Text(
                "ZX Carwash v$versi · ${pendingSync} antre sync",
                fontSize = 10.sp, color = TeksRedup, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            )
            Spacer(Modifier.height(80.dp))
        }

        Surface(shadowElevation = 8.dp, color = Kartu) {
            Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    nama = p.namaUsaha; alamat = p.alamat; hp = p.noHp; catatan = p.catatanNota
                }, modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(14.dp)) {
                    Text("Reset", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Teks)
                }
                Button(onClick = {
                    onSimpan(p.copy(namaUsaha = nama, alamat = alamat, noHp = hp,
                        catatanNota = catatan, autoUpdate = auto))
                }, modifier = Modifier.weight(1.4f).height(50.dp), shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Biru)) {
                    Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Simpan", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun TampilanChip() = Color(0xFFFFF1E7)

@Composable
private fun FieldRingkas(label: String, nilai: String, banyakBaris: Boolean = false, onUbah: (String) -> Unit) {
    OutlinedTextField(
        value = nilai, onValueChange = onUbah, label = { Text(label, fontSize = 11.sp) },
        singleLine = !banyakBaris, minLines = if (banyakBaris) 2 else 1,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    )
}

@Composable
fun BarisInfo(label: String, nilai: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp, color = TeksRedup)
        Text(nilai, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Teks)
    }
}

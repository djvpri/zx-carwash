package com.zomet.zxcarwash

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Token arah desain A — Biru Bersih (sama dengan mockup).
val Biru = Color(0xFF2563EB)
val Oranye = Color(0xFFEA580C)
val OranyeTeks = Color(0xFFB45309)
val Latar = Color(0xFFF8FAFC)
val Kartu = Color(0xFFFFFFFF)
val Teks = Color(0xFF1E293B)
val TeksRedup = Color(0xFF475569)
val Garis = Color(0xFFE2E8F0)
val HijauTeks = Color(0xFF15803D)
val HijauLatar = Color(0xFFE7F7EE)

fun Rp(n: Int) = Struk.rupiah(n)

@Composable
fun Chip(teks: String, aktif: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .border(1.dp, if (aktif) Oranye else Garis, RoundedCornerShape(50))
            .background(if (aktif) Color(0xFFFFF1E7) else Kartu, RoundedCornerShape(50))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            teks, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            color = if (aktif) OranyeTeks else TeksRedup
        )
    }
}

@Composable
fun Kartu(modifier: Modifier = Modifier, isi: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .border(1.dp, Garis, RoundedCornerShape(16.dp))
            .background(Kartu, RoundedCornerShape(16.dp))
            .padding(12.dp),
        content = isi
    )
}

@Composable
fun JudulKecil(teks: String) = Text(
    teks.uppercase(), fontSize = 10.sp, letterSpacing = 1.sp,
    fontWeight = FontWeight.Bold, color = TeksRedup,
    modifier = Modifier.padding(top = 4.dp)
)

/** Satu baris item dengan stepper qty. */
@Composable
fun BarisItem(nama: String, ket: String, harga: Int, qty: Int, onUbahQty: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 7.dp)) {
        Column(Modifier.weight(1f)) {
            Text(nama, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Teks)
            if (ket.isNotBlank()) Text(ket, fontSize = 11.sp, color = TeksRedup)
        }
        if (qty <= 0) {
            Text(Rp(harga), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Teks)
            Spacer(Modifier.width(10.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            KotakQty("-") { onUbahQty(qty - 1) }
            Text(
                "$qty", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Teks,
                modifier = Modifier.width(24.dp), textAlign = TextAlign.Center
            )
            KotakQty("+") { onUbahQty(qty + 1) }
        }
    }
}

@Composable
private fun KotakQty(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(28.dp)
            .border(1.dp, Garis, RoundedCornerShape(8.dp))
            .background(Kartu, RoundedCornerShape(8.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Teks) }
}

/** Layar Kasir: bon gabungan cuci + kantin. */
@Composable
fun LayarKasir(
    items: List<ItemBon>,
    plat: String,
    jenis: String,
    diskon: Int,
    onPlat: (String) -> Unit,
    onJenis: (String) -> Unit,
    onQty: (ItemBon, Int) -> Unit,
    onBayar: () -> Unit,
) {
    val bon = Bon(nomor = "A-000", plat = plat, jenis = jenis, items = items,
        diskon = diskon, metode = "Tunai", dibayar = 0, createdAt = System.currentTimeMillis())
    Column(Modifier.fillMaxSize().background(Latar)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(14.dp)) {
            Kartu {
                OutlinedTextField(
                    value = plat, onValueChange = onPlat,
                    label = { Text("Plat nomor") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("MPV", "SUV", "Motor", "Pickup").forEach {
                        Chip(it, jenis == it) { onJenis(it) }
                    }
                }
            }

            JudulKecil("Cuci")
            Kartu {
                Layanan.cuci.forEach { (nama, harga, menit) ->
                    val q = items.firstOrNull { it.tipe == "cuci" && it.nama == nama }?.qty ?: 0
                    BarisItem(nama, "±$menit menit", harga, q) { onQty(ItemBon("cuci", nama, harga), it) }
                }
            }

            JudulKecil("Add-on")
            Kartu {
                FlowChips(Layanan.addon.map { it.first }) { nama ->
                    val harga = Layanan.addon.first { it.first == nama }.second
                    onQty(ItemBon("addon", nama, harga), 1)
                }
            }

            JudulKecil("Kantin")
            Kartu {
                Layanan.kantin.forEach { (nama, harga, _) ->
                    val q = items.firstOrNull { it.tipe == "kantin" && it.nama == nama }?.qty ?: 0
                    BarisItem(nama, "", harga, q) { onQty(ItemBon("kantin", nama, harga), it) }
                }
            }
            Spacer(Modifier.height(80.dp))
        }

        Surface(shadowElevation = 8.dp, color = Kartu) {
            Column(Modifier.padding(14.dp)) {
                BarisRingkas("Cuci", bon.subtotalCuci)
                BarisRingkas("Kantin", bon.subtotalKantin)
                Row(Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom) {
                    Text("TOTAL", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TeksRedup)
                    Text(Rp(bon.total), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Teks)
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onBayar, enabled = items.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Biru)
                ) {
                    Icon(Icons.Default.Payments, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Bayar", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun BarisRingkas(label: String, nilai: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp, color = TeksRedup)
        Text(Rp(nilai), fontSize = 12.sp, color = TeksRedup)
    }
}

@Composable
fun FlowChips(daftar: List<String>, onPick: (String) -> Unit) {
    Column {
        daftar.chunked(2).forEach { baris ->
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                baris.forEach { Chip(it, false) { onPick(it) } }
            }
        }
    }
}

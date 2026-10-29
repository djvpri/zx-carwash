package com.zomet.zxcarwash

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Layar masuk lewat Z One (device code).
 *
 * Alur: minta kode → kasir membuka halaman izin di browser → menyetujui →
 * app menanyakan status tiap 3 detik sampai dapat token. Kode 8 karakter juga
 * ditampilkan supaya bisa diketik manual kalau tautan tidak terbuka.
 */
@Composable
fun LayarMasuk(db: Db, onMasuk: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var kode by remember { mutableStateOf<String?>(null) }
    var tautan by remember { mutableStateOf<String?>(null) }
    var pesan by remember { mutableStateOf<String?>(null) }
    var sibuk by remember { mutableStateOf(false) }
    var menunggu by remember { mutableStateOf(false) }

    suspend fun tungguPersetujuan(k: String) {
        menunggu = true
        for (i in 1..100) { // ~5 menit
            delay(3000)
            val o = withContext(Dispatchers.IO) { Api.ssoPoll(k) }
            if (o == null) {
                pesan = Api.lastError ?: "Gagal menanyakan status"
                continue
            }
            if (o.optBoolean("pending")) continue
            if (o.optBoolean("ok")) {
                Sesi.simpan(db, o)
                val sesi = Sesi.baca(db)
                if (sesi != null) withContext(Dispatchers.IO) { Sesi.tarikMaster(db, sesi) }
                menunggu = false
                onMasuk()
                return
            }
            pesan = o.optString("error", "Persetujuan gagal")
            menunggu = false
            return
        }
        menunggu = false
        pesan = "Kode kedaluwarsa. Minta kode baru."
    }

    fun mintaKode() {
        sibuk = true
        pesan = null
        scope.launch {
            val hasil = withContext(Dispatchers.IO) { Api.ssoStart(Api.deviceId(ctx)) }
            sibuk = false
            if (hasil == null) {
                pesan = Api.lastError ?: "Tidak dapat menghubungi server"
                return@launch
            }
            kode = hasil.first
            tautan = hasil.second
            scope.launch { tungguPersetujuan(hasil.first) }
        }
    }

    Column(
        Modifier.fillMaxSize().background(Latar).verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(36.dp))
        Text("ZX Carwash", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Teks)
        Text(
            "Kasir cuci + kantin. Masuk dengan akun Z One.",
            fontSize = 12.sp, color = TeksRedup, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp, bottom = 24.dp)
        )

        Kartu {
            Text("Masuk dengan Z One", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Teks)
            Text(
                "Satu akun untuk seluruh aplikasi Zomet. Data usaha & outlet mengikuti akun.",
                fontSize = 11.sp, color = TeksRedup,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )

            Button(
                onClick = { mintaKode() },
                enabled = !sibuk,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Biru)
            ) {
                Icon(Icons.Default.Login, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (sibuk) "Menyiapkan kode…" else "Masuk dengan Z One",
                    fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            val k = kode
            if (k != null) {
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Garis)
                Text("Kode perangkat", fontSize = 11.sp, color = TeksRedup)
                Text(
                    k, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Biru,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Text(
                    "Buka halaman izin, login Z One, lalu setujui kode ini.",
                    fontSize = 11.sp, color = TeksRedup, modifier = Modifier.padding(top = 6.dp)
                )
                OutlinedButton(
                    onClick = {
                        val u = tautan ?: return@OutlinedButton
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))) }
                            .onFailure { pesan = "Tidak ada browser. Buka tautan manual." }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(46.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.OpenInBrowser, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Buka halaman izin", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Teks)
                }
                if (menunggu) {
                    Row(
                        Modifier.padding(top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Biru)
                        Spacer(Modifier.width(8.dp))
                        Text("Menunggu persetujuan…", fontSize = 11.sp, color = TeksRedup)
                    }
                }
            }

            val p = pesan
            if (p != null) {
                Text(p, fontSize = 11.sp, color = OranyeTeks, modifier = Modifier.padding(top = 10.dp))
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "Belum punya akun? Minta admin mendaftarkan email kamu di Z One.",
            fontSize = 10.sp, color = TeksRedup, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Text("Server: ${Api.BASE}", fontSize = 9.sp, color = TeksRedup, textAlign = TextAlign.Center)
    }
}

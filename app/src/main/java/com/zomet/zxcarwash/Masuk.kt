package com.zomet.zxcarwash

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Login
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Layar masuk — konsep sama dengan AcuMedis:
 *  alamat server · email · password · "Ingat email & password" · tombol Masuk.
 *
 * Email & password diingat lewat Kredensial (AES-GCM di AndroidKeyStore).
 * Jalur kedua "Masuk dengan Z One" (device code) tetap tersedia di bawah.
 */
@Composable
fun LayarMasuk(db: Db, onMasuk: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var alamat by remember { mutableStateOf(Api.BASE) }
    var email by remember { mutableStateOf(Kredensial.email(ctx)) }
    var sandi by remember { mutableStateOf(Kredensial.password(ctx)) }
    var ingat by remember { mutableStateOf(Kredensial.ada(ctx)) }
    var galat by remember { mutableStateOf<String?>(null) }
    var sibuk by remember { mutableStateOf(false) }
    var modeZOne by remember { mutableStateOf(false) }

    var kode by remember { mutableStateOf<String?>(null) }
    var tautan by remember { mutableStateOf<String?>(null) }
    var menunggu by remember { mutableStateOf(false) }

    fun selesaiMasuk(o: JSONObject) {
        Sesi.simpan(db, o)
        if (ingat) Kredensial.simpan(ctx, email.trim(), sandi) else Kredensial.hapus(ctx)
        Sesi.baca(db)?.let { Sesi.tarikMaster(db, it) }
        onMasuk()
    }

    fun masuk() {
        val e = email.trim()
        galat = null
        when {
            !alamat.startsWith("http") -> { galat = "Alamat server harus diawali https://"; return }
            e.isBlank() -> { galat = "Email wajib diisi"; return }
            sandi.isBlank() -> { galat = "Password wajib diisi"; return }
        }
        Api.pasangBase(alamat)
        db.tulisPengaturanMentah(mapOf("server_url" to alamat.trim().trimEnd('/')))
        sibuk = true
        scope.launch {
            val o = withContext(Dispatchers.IO) { Api.login(e, sandi, Api.deviceId(ctx)) }
            if (o == null) {
                sibuk = false
                galat = Api.lastError ?: "Server tidak merespons"
            } else {
                withContext(Dispatchers.IO) { selesaiMasuk(o) }
                sibuk = false
            }
        }
    }

    suspend fun tungguPersetujuan(k: String) {
        menunggu = true
        for (i in 1..100) {
            delay(3000)
            val o = withContext(Dispatchers.IO) { Api.ssoPoll(k) }
            if (o == null) {
                galat = Api.lastError ?: "Gagal menanyakan status"
                continue
            }
            if (o.optBoolean("pending")) continue
            if (o.optBoolean("ok")) {
                withContext(Dispatchers.IO) { selesaiMasuk(o) }
                menunggu = false
                return
            }
            galat = o.optString("error", "Persetujuan gagal")
            menunggu = false
            return
        }
        menunggu = false
        galat = "Kode kedaluwarsa. Minta kode baru."
    }

    fun mintaKodeZOne() {
        sibuk = true
        galat = null
        scope.launch {
            val hasil = withContext(Dispatchers.IO) { Api.ssoStart(Api.deviceId(ctx)) }
            sibuk = false
            if (hasil == null) {
                galat = Api.lastError ?: "Tidak dapat menghubungi server"
                return@launch
            }
            kode = hasil.first
            tautan = hasil.second
            scope.launch { tungguPersetujuan(hasil.first) }
        }
    }

    Column(
        Modifier.fillMaxSize().background(Latar).verticalScroll(rememberScrollState()).padding(20.dp)
    ) {
        Spacer(Modifier.height(28.dp))
        Text("Masuk ke ZX Carwash", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Teks)
        Text(
            "Kasir cuci + kantin dalam satu bon",
            fontSize = 12.sp, color = TeksRedup,
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
        )

        if (!modeZOne) {
            Kartu {
                JudulKecil("Alamat server")
                OutlinedTextField(
                    value = alamat, onValueChange = { alamat = it },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(fontSize = 13.sp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )

                Spacer(Modifier.height(10.dp))
                JudulKecil("Email")
                OutlinedTextField(
                    value = email, onValueChange = { email = it },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(fontSize = 13.sp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )

                Spacer(Modifier.height(10.dp))
                JudulKecil("Password")
                OutlinedTextField(
                    value = sandi, onValueChange = { sandi = it },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    textStyle = TextStyle(fontSize = 13.sp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )

                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp).clickable { ingat = !ingat },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (ingat) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                        null, Modifier.size(18.dp), tint = if (ingat) HijauTeks else TeksRedup
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (ingat) "Ingat email & password" else "Jangan ingat password",
                        fontSize = 12.sp, color = if (ingat) HijauTeks else TeksRedup
                    )
                }

                galat?.let {
                    Text(it, fontSize = 11.sp, color = OranyeTeks, modifier = Modifier.padding(top = 10.dp))
                }

                Button(
                    onClick = { masuk() },
                    enabled = !sibuk,
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Biru)
                ) {
                    Icon(Icons.Default.Login, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (sibuk) "Memeriksa…" else "Masuk",
                        fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }

            TextButton(
                onClick = { modeZOne = true; galat = null },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Text("Masuk dengan Z One", fontSize = 12.sp, color = TeksRedup)
            }
        } else {
            Kartu {
                Text("Masuk dengan Z One", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Teks)
                Text(
                    "Satu akun untuk seluruh aplikasi Zomet.",
                    fontSize = 11.sp, color = TeksRedup,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )
                Button(
                    onClick = { mintaKodeZOne() },
                    enabled = !sibuk,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Biru)
                ) {
                    Icon(Icons.Default.Login, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (sibuk) "Menyiapkan kode…" else "Minta kode perangkat",
                        fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }

                val k = kode
                if (k != null) {
                    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Garis)
                    Text("Kode perangkat", fontSize = 11.sp, color = TeksRedup)
                    Text(k, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Biru)
                    Text(
                        "Buka halaman izin, login Z One, lalu setujui kode ini.",
                        fontSize = 11.sp, color = TeksRedup, modifier = Modifier.padding(top = 6.dp)
                    )
                    OutlinedButton(
                        onClick = {
                            val u = tautan ?: return@OutlinedButton
                            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))) }
                                .onFailure { galat = "Tidak ada browser. Buka tautan manual." }
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(46.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Buka halaman izin", fontSize = 13.sp,
                            fontWeight = FontWeight.Bold, color = Teks)
                    }
                    if (menunggu) {
                        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Biru)
                            Spacer(Modifier.width(8.dp))
                            Text("Menunggu persetujuan…", fontSize = 11.sp, color = TeksRedup)
                        }
                    }
                }
                galat?.let {
                    Text(it, fontSize = 11.sp, color = OranyeTeks, modifier = Modifier.padding(top = 10.dp))
                }
            }

            TextButton(
                onClick = { modeZOne = false; galat = null },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Text("Kembali ke email & password", fontSize = 12.sp, color = TeksRedup)
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            "Akun dibuat admin lewat Z One. Lupa password? Minta reset ke admin.",
            fontSize = 10.sp, color = TeksRedup, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(18.dp))
        Text(
            "v${BuildConfig.VERSION_NAME}", fontSize = 9.sp, color = TeksRedup,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
        )
    }
}

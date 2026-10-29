package com.zomet.zxcarwash

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.zomet.zxcarwash.bt.BluetoothPrinter
import com.zomet.zxcarwash.update.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    /** Token dari halaman /link Z One lewat `zxcarwash://sso?token=...`. */
    private var tokenDariLink: String? = null

    private var izin: (() -> Unit)? = null
    private val mintaIzin = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) izin?.invoke() else toast("Izin Bluetooth ditolak")
    }
    private val mintaIzinSemua = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { h ->
        if (h.values.all { it }) izin?.invoke() else toast("Izin Bluetooth ditolak")
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    /** `zxcarwash://sso?token=...` → token; null kalau bukan tautan SSO. */
    private fun ambilToken(i: Intent?): String? {
        val u = i?.data ?: return null
        if (u.scheme != "zxcarwash" || u.host != "sso") return null
        return u.getQueryParameter("token")?.takeIf { it.isNotBlank() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val t = ambilToken(intent) ?: return
        Thread {
            val db = Db(this)
            val o = Api.ssoExchange(t, Api.deviceId(this))
            if (o == null) {
                runOnUiThread { toast(Api.lastError ?: "Gagal masuk lewat Z One") }
                return@Thread
            }
            Sesi.simpan(db, o)
            Sesi.baca(db)?.let { Sesi.tarikMaster(db, it) }
            runOnUiThread {
                toast("Masuk sebagai ${Sesi.baca(db)?.nama ?: "pengguna"}")
                recreate()
            }
        }.start()
    }

    /** Android 12+ wajib BLUETOOTH_CONNECT sebelum akses perangkat. */
    private fun pastikanIzinBluetooth(lalu: () -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return lalu()
        val perlu = arrayOf(Manifest.permission.BLUETOOTH_CONNECT)
            .filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (perlu.isEmpty()) lalu() else {
            izin = lalu
            mintaIzinSemua.launch(perlu.toTypedArray())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tokenDariLink = ambilToken(intent)
        val db = Db(this)
        Api.pasangBase(db.bacaPengaturanMentah()["server_url"])
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Biru)) {
                App(db) { cb -> pastikanIzinBluetooth(cb) }
            }
        }
    }
}

private sealed class Layar {
    data object Kasir : Layar()
    data object Antrian : Layar()
    data object Laporan : Layar()
    data class Bayar(val bon: Bon) : Layar()
    data object Pengaturan : Layar()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(db: Db, izinBluetooth: (() -> Unit) -> Unit) {
    val ctx = LocalContext.current
    var sesi by remember { mutableStateOf(Sesi.baca(db)) }
    var layar by remember { mutableStateOf<Layar>(Layar.Kasir) }
    var items by remember { mutableStateOf(listOf<ItemBon>()) }
    var plat by remember { mutableStateOf("") }
    var jenis by remember { mutableStateOf("MPV") }
    var pengaturan by remember { mutableStateOf(db.bacaPengaturan()) }
    var pending by remember { mutableStateOf(db.pendingSync()) }
    var statusUpdate by remember { mutableStateOf<String?>(null) }
    var lisensi by remember { mutableStateOf(Sesi.lisensi(db)) }
    var printerOn by remember { mutableStateOf(BluetoothPrinter.connected()) }
    var dialogPrinter by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val aktivitas = ctx as? MainActivity

    DisposableEffect(Unit) {
        BluetoothPrinter.onStateChange = { printerOn = it }
        onDispose { BluetoothPrinter.onStateChange = null }
    }

    // Tarik master & kirim sisa antrean sekali saat app dibuka. Kalau offline,
    // dua-duanya gagal diam-diam — kasir tetap bisa transaksi.
    LaunchedEffect(sesi) {
        val s0 = sesi ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            if (!Sesi.tarikMaster(db, s0)) {
                // token 60 menit bisa kedaluwarsa; refresh dulu sebelum menyerah
                Sesi.perbaruiToken(ctx, db)?.let { Sesi.baca(db) }?.let { Sesi.tarikMaster(db, it) }
            }
            Sesi.baca(db)?.let { Sesi.kirimOutbox(ctx, db, it) }
        }
        pending = db.pendingSync()
        pengaturan = db.bacaPengaturan()
        lisensi = Sesi.lisensi(db)
    }

    // Auto-cek update sekali saat app dibuka (kalau diaktifkan).
    LaunchedEffect(pengaturan.autoUpdate) {
        if (!pengaturan.autoUpdate) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val r = Updater.cekTerbaru()
            if (r != null && Updater.lebihBaru(r.versi, BuildConfig.VERSION_NAME)) {
                statusUpdate = "Versi ${r.versi.trimStart('v')} tersedia"
            }
        }
    }

    fun ubahQty(item: ItemBon, qty: Int) {
        items = items.toMutableList().also { l ->
            l.removeAll { it.tipe == item.tipe && it.nama == item.nama }
            if (qty > 0) l += item.copy(qty = qty)
        }
    }

    // Belum login → hanya layar masuk. Transaksi offline tetap butuh akun sekali
    // di awal supaya data ter-scope ke tenant yang benar.
    val sesiAktif = sesi
    if (sesiAktif == null) {
        LayarMasuk(db) { sesi = Sesi.baca(db) }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(pengaturan.namaUsaha.take(22), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                        Text(
                            "Outlet Melati · ${if (pending == 0) "tersinkron" else "$pending antre sync"}",
                            fontSize = 10.sp, color = TeksRedup
                        )
                    }
                },
                actions = {
                    AssistChip(
                        onClick = {},
                        label = { Text(if (printerOn) "Printer OK" else "Printer off", fontSize = 10.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Print, null, Modifier.size(13.dp))
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Kartu)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Kartu) {
                NavItem("Kasir", Icons.Default.Payments, layar is Layar.Kasir) { layar = Layar.Kasir }
                NavItem("Antrian", Icons.Default.FormatListNumbered, layar is Layar.Antrian) {
                    layar = Layar.Antrian
                }
                NavItem("Laporan", Icons.Default.BarChart, layar is Layar.Laporan) {
                    layar = Layar.Laporan
                }
                NavItem("Lainnya", Icons.Default.Settings, layar is Layar.Pengaturan) {
                    layar = Layar.Pengaturan
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (val l = layar) {
                is Layar.Kasir -> LayarKasir(
                    items = items, plat = plat, jenis = jenis, diskon = 0, db = db,
                    onPlat = { plat = it.uppercase() }, onJenis = { jenis = it },
                    onQty = ::ubahQty,
                    onBayar = {
                        val bon = Bon(
                            nomor = "A-" + (db.bonHariIni().size + 1).toString().padStart(3, '0'),
                            plat = plat.ifBlank { "TANPA PLAT" }, jenis = jenis, items = items,
                            diskon = 0, metode = "Tunai", dibayar = 0,
                            createdAt = System.currentTimeMillis()
                        )
                        layar = Layar.Bayar(bon)
                    }
                )

                is Layar.Antrian -> LayarAntrian(sesi = sesiAktif) { id, status, selesai ->
                    scope.launch {
                        val o = withContext(Dispatchers.IO) { Api.ubahAntrian(sesiAktif.token, id, status) }
                        if (o == null) {
                            Toast.makeText(ctx, Api.lastError ?: "Gagal ubah antrian", Toast.LENGTH_LONG).show()
                        } else {
                            selesai()
                        }
                    }
                }

                is Layar.Laporan -> LayarLaporan(sesi = sesiAktif)

                is Layar.Bayar -> LayarBayar(
                    bon = l.bon, pengaturan = pengaturan,
                    onBatal = { layar = Layar.Kasir },
                    onSelesai = { metode, dibayar, cetak ->
                        val bon = l.bon.copy(metode = metode, dibayar = dibayar)
                        db.simpanBon(bon)
                        pending = db.pendingSync()
                        // kirim ke server tanpa menunggu: struk sudah tercetak,
                        // kalau sinyal hilang bon tetap ada di antrean lokal
                        scope.launch {
                            val n = withContext(Dispatchers.IO) {
                                Sesi.kirimOutbox(ctx, db, sesiAktif)
                            }
                            pending = db.pendingSync()
                            if (n > 0) Toast.makeText(ctx, "$n bon terkirim ke server",
                                Toast.LENGTH_SHORT).show()
                        }
                        if (cetak) {
                            izinBluetooth {
                                val err = BluetoothPrinter.write(Struk.bytes(bon, pengaturan))
                                Toast.makeText(ctx, err ?: "Struk dikirim ke printer",
                                    Toast.LENGTH_SHORT).show()
                            }
                        }
                        items = emptyList(); plat = ""
                        layar = Layar.Kasir
                    }
                )

                is Layar.Pengaturan -> LayarPengaturan(
                    p = pengaturan, versi = BuildConfig.VERSION_NAME,
                    pendingSync = pending, statusUpdate = statusUpdate,
                    printerTersambung = printerOn,
                    lisensiPlan = lisensi?.optString("plan")?.replaceFirstChar { it.uppercase() }
                        ?: pengaturan.paket,
                    lisensiAktif = lisensi?.optBoolean("aktif") ?: true,
                    lisensiSampai = lisensi?.let { l ->
                        if (l.isNull("sampai")) "-" else Fmt.tanggalPanjang(l.optString("sampai"))
                    } ?: pengaturan.berlakuSampai,
                    lisensiSisa = lisensi?.let { l ->
                        val s = if (l.isNull("sisaHari")) null else l.optInt("sisaHari")
                        if (s == null) "-" else "$s hari lagi"
                    } ?: "-",
                    lisensiDariServer = lisensi != null,
                    onSimpan = {
                        db.simpanPengaturan(it); pengaturan = it
                        Toast.makeText(ctx, "Pengaturan tersimpan", Toast.LENGTH_SHORT).show()
                    },
                    onCekUpdate = {
                        statusUpdate = "Memeriksa..."
                        scope.launch {
                            val r = withContext(Dispatchers.IO) { Updater.cekTerbaru() }
                            statusUpdate = when {
                                r == null -> "Gagal cek: ${Updater.lastErr ?: "butuh koneksi"}"
                                Updater.lebihBaru(r.versi, BuildConfig.VERSION_NAME) ->
                                    "Versi ${r.versi.trimStart('v')} tersedia"
                                else -> "Sudah versi terbaru (${BuildConfig.VERSION_NAME})"
                            }
                        }
                    },
                    onPasang = {
                        scope.launch {
                            val r = withContext(Dispatchers.IO) { Updater.cekTerbaru() }
                            val url = r?.apkUrl
                            if (url == null) {
                                Toast.makeText(ctx, "Asset APK tidak ditemukan", Toast.LENGTH_LONG).show()
                            } else {
                                val ok = aktivitas?.let { Updater.unduhDanInstall(it, url) } ?: false
                                if (!ok) Toast.makeText(ctx, Updater.lastErr ?: "Gagal unduh",
                                    Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    onScanPrinter = { dialogPrinter = true },
                    sesi = sesiAktif,
                    onKirimSekarang = {
                        scope.launch {
                            val n = withContext(Dispatchers.IO) { Sesi.kirimOutbox(ctx, db, sesiAktif) }
                            pending = db.pendingSync()
                            Toast.makeText(
                                ctx,
                                if (n < 0) Api.lastError ?: "Gagal terhubung"
                                else if (n == 0) "Tidak ada bon menunggu"
                                else "$n bon terkirim",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    onTarikMaster = {
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) { Sesi.tarikMaster(db, sesiAktif) }
                            pengaturan = db.bacaPengaturan()
                            Toast.makeText(
                                ctx,
                                if (ok) "Master diperbarui dari server" else Api.lastError ?: "Gagal tarik master",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    onKeluar = {
                        scope.launch {
                            val r = Sesi.refreshToken(db)
                            if (r != null) withContext(Dispatchers.IO) { Api.logout(r) }
                            Sesi.keluar(db)
                            sesi = null
                        }
                    }
                )
            }
        }
    }

    if (dialogPrinter) {
        val daftar = remember { mutableStateListOf<BluetoothDevice>() }
        LaunchedEffect(Unit) {
            izinBluetooth {
                daftar.clear(); daftar.addAll(BluetoothPrinter.pairedDevices())
            }
        }
        AlertDialog(
            onDismissRequest = { dialogPrinter = false },
            title = { Text("Pilih printer", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                if (daftar.isEmpty()) {
                    Text("Tidak ada printer dipasangkan. Pasangkan printer di Setelan Bluetooth HP dulu.",
                        fontSize = 12.sp)
                } else {
                    Column {
                        daftar.forEach { d ->
                            TextButton(onClick = {
                                val addr = d.address ?: return@TextButton
                                val err = BluetoothPrinter.connect(addr)
                                dialogPrinter = false
                                Toast.makeText(ctx, err ?: "Tersambung ke ${d.name}", Toast.LENGTH_SHORT).show()
                            }) { Text(d.name ?: d.address, fontSize = 14.sp) }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { dialogPrinter = false }) { Text("Tutup") }
            }
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NavItem(
    label: String, ikon: ImageVector, aktif: Boolean, onClick: () -> Unit,
) {
    NavigationBarItem(
        selected = aktif, onClick = onClick,
        icon = { Icon(ikon, label, Modifier.size(20.dp)) },
        label = { Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
    )
}

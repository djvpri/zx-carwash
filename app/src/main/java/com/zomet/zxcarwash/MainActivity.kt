package com.zomet.zxcarwash

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
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

    private var izin: (() -> Unit)? = null
    private val mintaIzin = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) izin?.invoke() else toast("Izin Bluetooth ditolak")
    }
    private val mintaIzinSemua = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { h ->
        if (h.values.all { it }) izin?.invoke() else toast("Izin Bluetooth ditolak")
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

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
        val db = Db(this)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Biru)) {
                App(db) { cb -> pastikanIzinBluetooth(cb) }
            }
        }
    }
}

private sealed class Layar {
    data object Kasir : Layar()
    data class Bayar(val bon: Bon) : Layar()
    data object Pengaturan : Layar()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(db: Db, izinBluetooth: (() -> Unit) -> Unit) {
    val ctx = LocalContext.current
    var layar by remember { mutableStateOf<Layar>(Layar.Kasir) }
    var items by remember { mutableStateOf(listOf<ItemBon>()) }
    var plat by remember { mutableStateOf("") }
    var jenis by remember { mutableStateOf("MPV") }
    var pengaturan by remember { mutableStateOf(db.bacaPengaturan()) }
    var pending by remember { mutableStateOf(db.pendingSync()) }
    var statusUpdate by remember { mutableStateOf<String?>(null) }
    var printerOn by remember { mutableStateOf(BluetoothPrinter.connected()) }
    var dialogPrinter by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val aktivitas = ctx as? MainActivity

    DisposableEffect(Unit) {
        BluetoothPrinter.onStateChange = { printerOn = it }
        onDispose { BluetoothPrinter.onStateChange = null }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Cuci Kantin", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
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
                NavItem("Antrian", Icons.Default.FormatListNumbered, false) {
                    Toast.makeText(ctx, "Antrian: fase berikutnya", Toast.LENGTH_SHORT).show()
                }
                NavItem("Menu", Icons.Default.Restaurant, false) {
                    Toast.makeText(ctx, "Kelola menu: fase berikutnya", Toast.LENGTH_SHORT).show()
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
                    items = items, plat = plat, jenis = jenis, diskon = 0,
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

                is Layar.Bayar -> LayarBayar(
                    bon = l.bon, pengaturan = pengaturan,
                    onBatal = { layar = Layar.Kasir },
                    onSelesai = { metode, dibayar, cetak ->
                        val bon = l.bon.copy(metode = metode, dibayar = dibayar)
                        db.simpanBon(bon)
                        pending = db.pendingSync()
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
                    onScanPrinter = { dialogPrinter = true }
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

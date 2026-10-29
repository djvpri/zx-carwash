# ZX Carwash (Android)

Kasir Android untuk usaha **cuci mobil + kantin**: satu bon gabungan, offline-first, struk thermal Bluetooth.

- Paket: `com.zomet.zxcarwash`, minSdk 26, targetSdk 36.
- Stack: Kotlin + Jetpack Compose (Material 3), SQLite mentah + outbox sync (tanpa Room/Retrofit), ESC/POS Bluetooth SPP.
- Arah desain: **A — Biru Bersih** (`#2563EB` / aksen `#EA580C`).

## Isi fase ini

| Fitur | Status |
|---|---|
| Kasir bon gabungan (cuci + add-on + kantin) | ada |
| Hitung subtotal per modul, diskon bon, kembalian | ada |
| Login email+password (pola AcuMedis) + SSO Z One | ada |
| Tarik master dari server (paket, add-on, produk, pengaturan) | ada |
| Simpan ke SQLite lokal + kirim outbox ke server (idempoten) | ada |
| Tab Antrian per bay (mulai / selesai / diambil) | ada |
| Tab Laporan (hari ini, 7/30 hari, grafik harian, komisi) | ada |
| Lisensi dibaca dari server | ada |
| Tab Menu: produk kantin + stok (tambah/ubah/mutasi) | ada |
| Buka & tutup shift (kas fisik vs sistem) | ada |
| Pelunasan bon piutang dari app | ada |
| Member: daftar, tambah/ubah, isi & tarik saldo, poin | ada |
| Struk thermal ESC/POS 58mm + pratinjau 32 kolom | ada |
| Printer Bluetooth SPP (pairing, monitor putus, reconnect) | ada |
| Pengaturan usaha: nama, alamat, no HP, catatan nota | ada |
| Lisensi (paket, berlaku sampai, sisa, ID) — tampil saja | ada |
| Cek update manual + auto-cek saat app dibuka (GitHub Releases) | ada |
| Kelola layanan (paket cuci) dari app, laporan per shift, cetak tiket antrian | belum |

## Build

```bash
export JAVA_HOME="C:/Users/<user>/AppData/Local/Temp/jdk-17.0.20.1+1"
printf 'sdk.dir=C:/Users/<user>/AppData/Local/Android/Sdk\n' > local.properties
./gradlew.bat testDebugUnitTest assembleDebug
./gradlew.bat assembleRelease      # butuh keystore/keystore.properties
```

`keystore/keystore.properties` (tidak di-commit):

```properties
storeFile=keystore/zxcarwash-release.jks
storePassword=…
keyAlias=zxcarwash
keyPassword=…
```

## Struk

`Struk.kt` murni fungsi (bisa diuji unit test):

```
        NAMA USAHA
      alamat usaha
         no HP
--------------------------------
Bon A-018 · 2026-10-27 14:22
B 1234 XYZ                    MPV
--------------------------------
Salon MPV                  65.000
Jamur kaca                 15.000
2x Nasi Goreng Spesial     24.000
2x Es Teh Manis             8.000
--------------------------------
Diskon                     -5.600
TOTAL                     106.400
Tunai                     120.000
Kembali                    13.600
--------------------------------
      (catatan nota, maks 3 baris)
--------------------------------
          T E R I M A   K A S I H
       Powered by ZX Carwash App
            A-018 · 14:22
```

Dua baris terakhir adalah konstanta branding — tidak diambil dari Pengaturan.

## Update aplikasi

Auto-cek memakai GitHub Releases (`djvpri/zx-carwash`, bisa diganti lewat
`-PCARWASH_REPO=owner/repo`). Bump `versionCode`/`versionName` → `assembleRelease` →
upload APK ke release. Instalasi pertama harus dari APK bertanda tangan rilis (keystore sama)
supaya update berikutnya tidak bentrok.

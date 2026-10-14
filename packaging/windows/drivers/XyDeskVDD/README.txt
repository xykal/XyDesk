XyDeskVDD — XyDesk Virtual Display Adapter
==========================================

Driver display virtual IddCx UMDF2 milik XyDesk sendiri (HWID Root\XyDeskVDD).
Source: packaging/windows/vdd-src/XyDeskVDD (fork IddSampleDriver, MIT).

Isi paket (biner dihasilkan workflow "Build XyDeskVDD driver" dan diletakkan
di folder ini saat rilis):
  XyDeskVDD.inf       — descriptor instalasi (Root\XyDeskVDD)
  XyDeskVDD.dll       — driver UMDF2 (EDID kustom XYD 256-byte)
  XyDeskVDD.cat       — katalog (self-signed sampai sertifikat code signing
                        Microsoft/EV tersedia; SmartScreen/Defender dapat
                        menampilkan peringatan — dianggap oke untuk sekarang)
  XyDeskVDD.cer       — sertifikat self-signed untuk Root & TrustedPublisher
  option.txt          — matriks resolusi x refresh (disalin ke C:\XyDeskVDD)
  vdd_settings.xml    — preset untuk driver MTT (kompatibilitas)
  install.bat         — instalasi (jalankan sebagai Administrator)
  uninstall.bat       — pembersihan device node + DriverStore + sertifikat

Instalasi:
  1. Klik kanan install.bat → Run as administrator
     (atau jalankan dari install-all-drivers.bat)
  2. Jalur utama memakai xydesk-vdd-ctl.exe bila ada di sebelah (..\..);
     tanpa itu jatuh ke fallback certutil + pnputil + SetupAPI P/Invoke.
  3. Setelah terpasang, XyDesk host otomatis mendeteksi adapter dan menawarkan
     mode display virtual (pesan 0x10 / meta.displays).

Driver membaca konfigurasi mode dari:
  1. C:\XyDeskVDD\option.txt        (utama)
  2. C:\IddSampleDriver\option.txt  (warisan — juga dipakai driver MTT/ge9)
Host XyDesk (sync_host_driver_configs / xydesk-vdd-ctl) menulis ketiganya.

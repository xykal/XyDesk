XyDesk sudah terpasang. Langkah selanjutnya:

1. Buka **XyDesk Control Panel** dari Start Menu atau shortcut Desktop.
2. Panel menjalankan `xydesk-host.exe` sebagai proses internal tanpa terminal.
3. Device ID dan kode pairing tampil di panel; gunakan pada aplikasi client
   (HP/Windows/Web) untuk konek.
4. Gunakan **Buka XyDesk Web** untuk kontrol sesi, display, audio, input, dan
   privacy. Gunakan **Buka log host** bila host berhenti dengan exit code.

Log diagnostik tersimpan di `%LOCALAPPDATA%\XyDesk\host.log` dan dipertahankan
saat uninstall. Driver VDD/VB-CABLE, bila tersedia di payload, tetap opsional
dan mengikuti kebijakan Windows; installer tidak memasangnya diam-diam.

Cara uninstall: Control Panel > Programs > Uninstall XyDesk.

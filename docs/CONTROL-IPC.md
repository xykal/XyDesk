# Bootstrap control API — Windows

Control API tetap bind `127.0.0.1`; endpoint `/status` dan `/action` memerlukan
header `x-xydesk-token`. Bearer acak per proses **tidak lagi ditulis ke stdout**.
`runtime-<pid>.json` adalah status publik dan tidak dapat dipakai sebagai
sumber bearer. `/health` bukan bukti otorisasi control.

## Launcher yang membutuhkan control API

1. Buat pipe privat dengan kapasitas setidaknya 4096 byte. Pegang ujung baca di
   launcher; hanya ujung tulis yang dibuat inheritable.
2. Jalankan engine dengan `--control-info-handle <nilai-handle-desimal>`.
   Batasi handle inheritance memakai `PROC_THREAD_ATTRIBUTE_HANDLE_LIST`
   (atau `subprocess.STARTUPINFO.lpAttributeList['handle_list']` pada Python).
   Jangan mewariskan ujung baca atau seluruh handle proses secara global.
3. Tutup salinan ujung tulis milik parent segera setelah child dibuat.
   Baca satu frame JSON + newline, maksimal 1024 byte, dengan deadline parent.
   Child memakai write nonblocking; kapasitas tidak cukup/handle salah berarti
   startup gagal, bukan menulis credential ke log.
4. Periksa `protocol == 1`, `pid` sesuai child yang masih hidup, URL tepat
   `http://127.0.0.1:<port>`, dan token 32 hex. Jangan mengikuti redirect atau
   memakai proxy untuk request control lokal.
5. Simpan bearer hanya di memori launcher. Jangan mencetak frame, respons
   `/status` (memuat password), atau memasukkannya ke bundle diagnostik.
   Token lama dibuang saat engine mati/restart; bootstrap ulang untuk proses baru.

Kontrak frame (hanya skema, bukan credential sungguhan):

```text
protocol: 1
pid: PID proses engine
url: http://127.0.0.1:PORT
token: bearer acak proses, 32 digit hex
```

Handle stdio, null/invalid dan berkas disk tidak diterima. Opsi hanya didukung
Windows. Launcher merupakan bagian dari trusted computing base: pemegang
pipe memang mendapat bearer dan harus melindunginya. Ini tidak melindungi
terhadap proses jahat dengan hak user yang sama atau administrator.

Panel native saat ini mengawasi proses dan membaca heartbeat publik; ia tidak
perlu meminta token. Integrasi stdout lama **harus dimigrasikan**, bukan
memasang opsi agar bearer kembali tercatat dalam `host.log`.

## Bukti yang disiapkan untuk CI

`tool/check_control_ipc.py --engine <engine-kandidat>` menggunakan Python
Windows >=3.12, profil sementara dan signaling fixture loopback. Ia hanya
memeriksa bootstrap dan GET `/status` (bearer salah → 401, valid → 200),
tidak mengubah password atau menghubungi layanan produksi. Ia tidak mencetak
payload/response. Workflow `Validate Web Host` menjalankannya setelah build
engine; file ini hanya panduan, **probe belum dijalankan pada sesi lokal**.

Unit test Windows juga disiapkan untuk roundtrip/EOF, penolakan logfile dan
backpressure pipe. Perubahan ini bukan klaim hasil test atau izin dispatch.

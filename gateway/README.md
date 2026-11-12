# Gateway RDP darurat — koneksi langsung dari browser

Mode **darurat** untuk membuka sesi RDP **tanpa PC perantara**: browser
berbicara Guacamole protocol ke gateway ini, gateway meneruskannya ke
`guacd`, dan `guacd` yang berbicara RDP ke target. Berbeda dengan fitur
"RDP" bawaan aplikasi (tab RDP / tombol RDP di APK) yang memakai PC yang
sedang dipasangkan untuk membuka `mstsc` — mode itu tetap yang utama.

## Kapan memakai yang mana?

| | RDP via PC tersambung (utama) | Gateway ini (darurat) |
|---|---|---|
| Butuh PC perantara | Ya (PC yang dipasangkan) | Tidak |
| Kredensial RDP melewati | Hanya PC kamu | Proses gateway (memori, sesaat) |
| IP target terekspos ke | Hanya PC kamu | Gateway (operator VPS) |
| Instalasi | Tidak ada (sudah bawaan) | VPS + docker compose |

## Arsitektur

```
browser (public/index.html + guacamole-common-js)
   └─ wss://gateway/?token=…        ← token HMAC: host/port target terkunci
        └─ bridge/server.js (guacamole-lite)
             └─ guacd (docker) ── RDP ──► target:3389
```

## Deploy (VPS, ±5 menit)

```bash
cd gateway
export GATEWAY_SECRET=$(openssl rand -hex 32)   # simpan di password manager!
docker compose up -d
```

Taruh di belakang TLS (wajib — token & kredensial jangan lewat HTTP polos),
contoh Caddy:

```
gateway.xydesk.my.id {
    reverse_proxy 127.0.0.1:8080
}
```

Buka `https://gateway.xydesk.my.id`, tempel token dari operator, isi
username/password RDP, sambung.

## Token

Operator mencetak token per target (bukan token global), masa berlaku
pendek — target host/port ikut ditandatangani, jadi token tidak bisa
dipakai untuk mengarahkan gateway ke host lain (anti-SSRF):

```bash
docker compose exec bridge node mint.js --host 192.168.1.5 --port 3389 --ttl 300
```

## Keamanan & batas jujur

- **Kredensial RDP** diproses di memori `bridge` lalu diteruskan ke guacd —
  tidak pernah ditulis ke log/berkas, tapi **melewati proses gateway**.
  Bandingkan dengan mode via PC: kredensial hanya hidup di PC kamu.
- **IP target** hanya diketahui gateway (bukan publik), tetap **bukan**
  nol-ekspos seperti mode P2P.
- Token HMAC pendek + tujuan tunggal; kedaluwarsa default 5 menit.
- Batas sesi bersamaan (`MAX_SESSIONS`, default 8) untuk VPS kecil.
- Jalankan hanya untuk keperluan darurat; matikan (`docker compose down`)
  bila tidak dipakai.

## Uji

```bash
cd bridge && npm install && npm test   # unit token (tanda tangan, kedaluwarsa, anti-SSRF)
node server.js                          # smoke: /healthz + halaman klien
```

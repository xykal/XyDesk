import { lazy, Suspense } from 'react';
import { LICENSE_TOTAL } from './license-total';

// Inventaris lisensi berisi 490 entri dan besarnya puluhan kilobyte. Halaman
// depan tidak butuh itu, jadi berkasnya baru diunduh saat halaman Legal
// dibuka.
const LicenseInventory = lazy(() => import('./LicenseInventory'));

export function LegalPage() {
  return (
    <main className="content-page prose-page">
      <p className="eyebrow">LEGAL & PRIVASI</p>
      <h1>Kendali tetap milik kamu.</h1>
      <p className="lede">
        Berlaku sejak 1 September 2026. XyDesk dibuat oleh XySpace Tech, Indonesia.
        Versi lengkap Syarat &amp; Ketentuan dan Kebijakan Privasi juga ada di dalam
        aplikasi, lewat Akun → Tentang → Legal.
      </p>

      <section>
        <h2>Status layanan</h2>
        <p>
          XyDesk masih pra-beta. Unduhan publik ditutup, sebagian fitur belum jalan,
          dan sesi bisa berubah perilakunya tanpa pemberitahuan. Jangan jadikan XyDesk
          satu-satunya jalan masuk ke komputer yang kamu butuhkan untuk kerja penting.
        </p>
      </section>

      <section>
        <h2>Privasi sesi</h2>
        <p>
          Signaling hanya mempertemukan perangkat. Media WebRTC memakai DTLS-SRTP dan
          tidak disimpan oleh layanan XyDesk. Password pairing diverifikasi di sisi Host
          dan tidak pernah dikirim ke server kami.
        </p>
      </section>

      <section>
        <h2>Data yang kami simpan</h2>
        <ul>
          <li>Email, dan nama serta foto profil kalau kamu masuk lewat Google.</li>
          <li>ID perangkat, nama perangkat, dan sistem operasinya.</li>
          <li>Metadata sesi: waktu, durasi, jalur langsung atau relay, bitrate, ping.</li>
          <li>Token notifikasi, kalau kamu mengizinkan notifikasi.</li>
          <li>Sidik jari acak browser untuk suka dan komentar berita.</li>
          <li>Log server standar: alamat IP, waktu akses, jenis permintaan.</li>
        </ul>
        <p>
          Yang tidak pernah kami simpan: isi layar, ketikan, gerakan mouse, audio,
          berkas yang kamu transfer, dan isi papan klip.
        </p>
      </section>

      <section>
        <h2>Data akun</h2>
        <p>
          Login email memakai kode sekali pakai. Kodenya disimpan sebagai hash dan punya
          batas waktu serta batas percobaan. Token koneksi tamu diperbarui otomatis; izin browser dapat disimpan terpisah tanpa menyimpan
          identitas pengguna.
        </p>
      </section>

      <section>
        <h2>Layanan pihak ketiga</h2>
        <p>
          Supabase untuk akun dan basis data, Cloudflare untuk jaringan dan situs,
          OneSignal untuk notifikasi, Google Sign-In untuk pilihan masuk, dan GitHub
          sebagai tempat berkas pemasangan diunduh. Karena penyedia ini beroperasi lintas
          negara, datamu bisa diproses di luar Indonesia. Kami tidak menjual data dan
          tidak memasang pelacak iklan.
        </p>
      </section>

      <section>
        <h2>Berapa lama disimpan</h2>
        <ul>
          <li>Metadata sesi: 90 hari.</li>
          <li>Log server: 30 hari.</li>
          <li>Catatan audit keamanan: 1 tahun.</li>
          <li>Data akun: selama akunmu aktif, lalu hilang 30 hari setelah dihapus.</li>
        </ul>
      </section>

      <section>
        <h2>Hak kamu</h2>
        <p>
          Sesuai UU Nomor 27 Tahun 2022 tentang Pelindungan Data Pribadi, kamu berhak
          melihat, memperbaiki, mengunduh, membatasi, dan menghapus datamu, serta menarik
          persetujuan. Kirim ke <strong>privacy@xydesk.app</strong>, dijawab paling lama
          14 hari kerja.
        </p>
      </section>

      <section>
        <h2>Tanggung jawab penggunaan</h2>
        <p>
          Gunakan XyDesk hanya pada perangkat yang kamu miliki atau yang jelas memberi
          izin. Jangan memakai layanan untuk mengambil alih, mengganggu, atau mengakses
          data pihak lain tanpa hak. Layanan resmi tidak pernah meminta kamu memasang
          aplikasi remote desktop lalu menyebutkan ID dan password — kalau ada yang
          meminta begitu, itu penipuan.
        </p>
      </section>

      <section>
        <h2>Hukum dan sengketa</h2>
        <p>
          Ketentuan ini tunduk pada hukum Republik Indonesia. Perselisihan diselesaikan
          lebih dulu secara musyawarah lewat <strong>legal@xydesk.app</strong>. Bila tidak
          selesai dalam 30 hari, perkara dibawa ke pengadilan yang berwenang di Indonesia.
        </p>
      </section>

      <section>
        <h2>Lisensi proyek</h2>
        <p>
          XyDesk adalah perangkat lunak <strong>proprietary (bukan sumber terbuka)</strong>.
          Kamu bebas memakai aplikasinya, tetapi dilarang meng-clone, menyalin,
          merekayasa balik, atau mendistribusikan ulang kode sumbernya tanpa izin
          tertulis dari XySpace Tech. Teks lengkap Perjanjian Lisensi ada di dokumen
          lisensi proyek dan di Pengaturan → Legal di aplikasi Android/Desktop.
        </p>
      </section>

      <section>
        <h2>Lisensi pihak ketiga</h2>
        <p>
          Seluruh UI/UX XyDesk dirancang sendiri oleh tim. Di bawah ini adalah
          inventaris <strong>lengkap</strong> komponen pihak ketiga yang ikut
          terkirim bersama aplikasi — {LICENSE_TOTAL} komponen, dihasilkan
          otomatis dari lockfile dan teks lisensi paket yang benar-benar
          terpasang, bukan daftar yang diketik tangan.
        </p>
        <Suspense fallback={<p className="muted">Memuat daftar lisensi…</p>}>
          <LicenseInventory />
        </Suspense>
      </section>
    </main>
  );
}

/// Nama penulis dengan badge resmi XyDesk.
///
/// Badge ini melekat pada identitas, jadi ia harus punya arti tunggal:
/// "tulisan ini datang dari tim". Karena itu ia HANYA dirender saat server
/// menandai `official` — nilai yang berasal dari ADMIN_TOKEN, bukan dari
/// nama yang diketik. Nama tim juga dikunci di sisi worker, sehingga tidak
/// ada komentar publik yang bisa tampil sebagai "Haekal Saputra" tanpa badge
/// dan menipu pembaca yang sekilas.

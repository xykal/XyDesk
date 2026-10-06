import { RELEASE_BASE, Route, WHATSAPP_CHANNEL, downloads } from './app_routes';
import { NewsCard } from './news_pages';
import { useEffect, useId, useState } from 'react';
import type { FormEvent } from 'react';
import { detectDevicePackage, initialDevicePackage } from './device';
import {
  fetchNewsList,
  NewsPost,
  subscribeNews,
  } from './news';

import {
  APP_VERSION,
  DOWNLOAD_DISABLED_REASON,
  DOWNLOAD_ENABLED,
  RELEASE_STAGE,
  STAGE_LABEL,
} from './version';
import { WhatsAppIcon, TelegramIcon } from './brand-icons';

export function LandingPage({ navigate }: { navigate: (r: Route) => void }) {
  // Tombol hero saat unduhan ditutup membuka popup "Ingatkan saya"
  // (email / saluran resmi), bukan sekadar membawa ke halaman unduhan.
  const [remindOpen, setRemindOpen] = useState(false);
  const [latest, setLatest] = useState<NewsPost[]>([]);

  useEffect(() => {
    fetchNewsList('semua', 3)
      .then((r) => setLatest(r.posts))
      .catch(() => {});
  }, []);

  return (
    <main className="landing">
      {/* ── Hero ── */}
      <section className="hero">
        <div className="hero-copy">
          <span className="hero-eyebrow">
            <span className="dot" /> Remote desktop low-latency
          </span>
          <h1>
            PC kamu, di
            <span className="grad"> genggaman</span>.
          </h1>
          <p>
            Akses layar PC dari HP atau browser, nyaris tanpa jeda di jaringan
            lokal. Buat kerja atau main game — koneksinya langsung antar
            perangkatmu, tanpa server perantara yang ikut melihat sesimu.
          </p>
          <div className="hero-cta">
            <button
              className="btn primary big"
              onClick={() => (DOWNLOAD_ENABLED ? navigate('/download') : setRemindOpen(true))}
            >
              {DOWNLOAD_ENABLED ? 'Unduh sekarang' : 'Ingatkan saya'}
            </button>
            <button className="btn ghost big" onClick={() => navigate('/connect')}>
              Coba dari browser
            </button>
          </div>
          <p className="hero-note">
            {DOWNLOAD_ENABLED
              ? 'Gratis. Media sesi peer-to-peer, tidak lewat server kami.'
              : `v${APP_VERSION} · ${STAGE_LABEL[RELEASE_STAGE]} — belum bisa diunduh.`}
          </p>
        </div>
        <div className="hero-art" aria-hidden="true">
          <img className="hero-deco deco-cloud" src="/float-cloud.webp" alt="" width="124" height="124" loading="lazy" decoding="async" />
          <img className="hero-deco deco-sparkle" src="/float-sparkle.webp" alt="" width="76" height="76" loading="lazy" decoding="async" />
          <img className="hero-deco deco-globe" src="/float-globe.webp" alt="" width="96" height="96" loading="lazy" decoding="async" />
          <img className="hero-cartoon" src="/hero-cartoon.webp" alt="Ilustrasi remote desktop XyDesk — kontrol PC dari HP" width="640" height="360" loading="eager" decoding="async" />
        </div>
      </section>

      {/* ── Stats ── */}
      <section className="stats-strip">
        <div className="stat">
          <strong>&lt; 40 ms</strong>
          <span>target latency LAN</span>
        </div>
        <div className="stat">
          <strong>P2P</strong>
          <span>media tidak lewat server</span>
        </div>
        <div className="stat">
          <strong>NVENC</strong>
          <span>encode hardware GPU</span>
        </div>
        <div className="stat">
          <strong>Rp 0</strong>
          <span>tanpa langganan</span>
        </div>
      </section>

      {/* ── Fitur ── */}
      <section className="features">
        <h2>Dibangun untuk terasa lokal</h2>
        <div className="feature-grid">
          <FeatureCard index="01" title="Main game langsung dari HP">
            WASD, tombol yang bisa ditahan, keyboard lengkap, dan trackpad —
            semua kontrol PC ada di layar HP tanpa menutupi jalannya game.
          </FeatureCard>
          <FeatureCard index="02" title="Koneksi pribadi dan aman">
            Masuk pakai ID dan password dari PC-mu sendiri. Gambar dan suara
            mengalir langsung antar perangkat, tidak mampir ke server kami.
          </FeatureCard>
          <FeatureCard index="03" title="Gambar mulus, jaringan hemat">
            Encoding pakai hardware GPU bila ada, dan bitrate menyesuaikan
            supaya Wi-Fi rumah tetap lega untuk perangkat lain.
          </FeatureCard>
          <FeatureCard index="04" title="Langsung dari browser">
            Tidak sempat pasang aplikasi? Buka halaman Connect, masukkan ID,
            dan PC-mu tampil di tab browser — HP maupun laptop.
          </FeatureCard>
        </div>
      </section>

      {/* ── Berita ── */}
      {latest.length > 0 && (
        <section className="home-news">
          <div className="section-head">
            <h2>Berita terbaru</h2>
            <button className="text-link" onClick={() => navigate('/news')}>
              Lihat semua →
            </button>
          </div>
          <div className="news-grid">
            {latest.map((p) => (
              <NewsCard
                key={p.slug}
                post={p}
                onOpen={() => navigate({ page: 'news-detail', slug: p.slug })}
              />
            ))}
          </div>
        </section>
      )}

      {/* ── Unduh ── */}
      <section className="download-cta">
        <h2>{DOWNLOAD_ENABLED ? 'Mulai dari perangkatmu' : 'Segera di perangkatmu'}</h2>
        <PlatformTables compact />
        {DOWNLOAD_ENABLED ? (
          <a className="btn primary big" href={`${RELEASE_BASE}/XyDesk-Android-arm64-v8a.apk`}>
            Unduh untuk Android
          </a>
        ) : (
          <NotifyMeForm />
        )}
      </section>
      {remindOpen && <RemindMeModal onClose={() => setRemindOpen(false)} />}
    </main>
  );
}

export function FeatureCard({
  index,
  title,
  children,
}: {
  index: string;
  title: string;
  children: string;
}) {
  return (
    <div className="feature-card">
      <span className="feature-index">{index}</span>
      <h3>{title}</h3>
      <p>{children}</p>
    </div>
  );
}

export function PlatformIcon({ platform }: { platform: 'android' | 'windows' }) {
  return (
    <img
      src={platform === 'android' ? '/platform-android.svg' : '/platform-windows.svg'}
      alt=""
      width="20"
      height="20"
    />
  );
}

export function useRecommendedDownload() {
  const [recommended, setRecommended] = useState(initialDevicePackage);
  useEffect(() => {
    void detectDevicePackage().then(setRecommended);
  }, []);
  return recommended;
}

export function PlatformTables({ compact = false }: { compact?: boolean }) {
  const android = downloads.filter((item) => item.platform === 'Android');
  const windows = downloads.filter((item) => item.platform !== 'Android');
  const table = (
    title: string,
    icon: 'android' | 'windows',
    items: readonly (typeof downloads)[number][],
  ) => (
    <section className="platform-table">
      <div className="platform-table-head">
        <PlatformIcon platform={icon} />
        <h2>{title}</h2>
      </div>
      <div className="platform-table-body">
        {items.map((item) => (
          <div className="platform-table-row" key={item.file}>
            <div>
              <strong>{item.architecture}</strong>
              {!compact && <span>{item.note}</span>}
            </div>
            {DOWNLOAD_ENABLED ? (
              <a href={`${RELEASE_BASE}/${item.file}`}>Download</a>
            ) : (
              <span className="soon-chip" title={DOWNLOAD_DISABLED_REASON}>
                Segera
              </span>
            )}
          </div>
        ))}
      </div>
    </section>
  );
  return (
    <div className={compact ? 'platform-tables compact' : 'platform-tables'}>
      {table('Android', 'android', android)}
      {table('Windows', 'windows', windows)}
    </div>
  );
}

/**
 * "Ingatkan saya" — pengganti tombol unduh selama unduhan ditahan.
 *
 * Kenapa bukan tombol unduh yang dinonaktifkan saja: tombol mati hanya
 * menghentikan orang, dan orang yang berhenti tidak pernah kembali. Form ini
 * menangkap niatnya — alamat email disimpan berlabel 'unduhan', sehingga
 * kelak ia bisa dikabari satu kali saat unduhan benar-benar dibuka.
 *
 * Email tidak dikirim ke mana pun hari ini. Ia disimpan, dan hanya dipakai
 * untuk satu kabar itu (lihat news/migrations/0003).
 */
export function NotifyMeForm() {
  const inputId = useId();
  const [email, setEmail] = useState('');
  const [status, setStatus] = useState<'idle' | 'sending' | 'done' | 'error'>('idle');
  const [note, setNote] = useState('');

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (status === 'sending') return;
    const value = email.trim();
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(value)) {
      setStatus('error');
      setNote('Alamat emailnya belum benar. Periksa lagi ya.');
      return;
    }
    setStatus('sending');
    setNote('');
    try {
      const r = await subscribeNews(value, 'unduhan');
      if (!r.ok) throw new Error('ditolak server');
      setStatus('done');
      if (r.subscribed === false) setNote('Email kamu sudah pernah terdaftar — kami tetap ingat.');
    } catch {
      setStatus('error');
      setNote('Gagal menyimpan email kamu. Coba lagi sebentar.');
    }
  };

  if (status === 'done') {
    return (
      <div className="notify-me done">
        <p className="notify-title">
          <strong>Siap.</strong> Kami kabari {email} begitu unduhan dibuka.
        </p>
        {note && <p className="notify-note">{note}</p>}
        <a className="notify-alt" href={WHATSAPP_CHANNEL} target="_blank" rel="noreferrer">
          Mau lebih cepat? Ikuti saluran WhatsApp kami
        </a>
      </div>
    );
  }

  return (
    <form className={`notify-me${status === 'error' ? ' invalid' : ''}`} onSubmit={submit} noValidate>
      <label htmlFor={inputId}>Ingatkan saya saat unduhan dibuka</label>
      <div className="notify-row">
        <input
          id={inputId}
          type="email"
          inputMode="email"
          autoComplete="email"
          placeholder="nama@email.com"
          value={email}
          onChange={(e) => {
            setEmail(e.target.value);
            if (status === 'error') {
              setStatus('idle');
              setNote('');
            }
          }}
          aria-invalid={status === 'error'}
          aria-describedby={note ? `${inputId}-note` : undefined}
        />
        <button className="btn primary" type="submit" disabled={status === 'sending'}>
          {status === 'sending' ? 'Menyimpan…' : 'Ingatkan saya'}
        </button>
      </div>
      {note && (
        <p className="notify-note error" id={`${inputId}-note`} role="alert">
          {note}
        </p>
      )}
      <a className="notify-alt" href={WHATSAPP_CHANNEL} target="_blank" rel="noreferrer">
        Atau pantau lewat saluran WhatsApp kami
      </a>
    </form>
  );
}

/// Popup "Ingatkan saya" dari tombol hero beranda — menangkap niat
/// pengunjung sebelum mereka pergi: kabar rilis ke email (disimpan
/// berlabel 'unduhan', sama dengan form di halaman unduhan) atau ikut
/// saluran resmi. Status rilis dijelaskan terbuka, bukan disembunyikan.
export function RemindMeModal({ onClose }: { onClose: () => void }) {
  const inputId = useId();
  const [email, setEmail] = useState('');
  const [status, setStatus] = useState<'idle' | 'sending' | 'done' | 'error'>('idle');
  const [note, setNote] = useState('');

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (status === 'sending') return;
    const value = email.trim();
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(value)) {
      setStatus('error');
      setNote('Alamat emailnya belum benar. Periksa lagi ya.');
      return;
    }
    setStatus('sending');
    setNote('');
    try {
      const r = await subscribeNews(value, 'unduhan');
      if (!r.ok) throw new Error('ditolak server');
      setStatus('done');
      if (r.subscribed === false) setNote('Email kamu sudah pernah terdaftar — kami tetap ingat.');
    } catch {
      setStatus('error');
      setNote('Gagal menyimpan email kamu. Coba lagi sebentar.');
    }
  };

  return (
    <div
      className="remind-modal"
      role="dialog"
      aria-modal="true"
      aria-label="Ingatkan saya saat rilis"
      onClick={onClose}
    >
      <div className="remind-box" onClick={(e) => e.stopPropagation()}>
        <header className="remind-head">
          <strong>Ingatkan saya saat rilis</strong>
          <button type="button" className="remind-close" onClick={onClose} aria-label="Tutup">
            ✕
          </button>
        </header>
        <p className="remind-sub">
          Unduhan belum dibuka — {STAGE_LABEL[RELEASE_STAGE]}. Mau kabar rilisnya dikirim ke mana?
        </p>
        {status === 'done' ? (
          <div className="remind-done">
            <p>
              <strong>Siap.</strong> Kami kabari {email} begitu unduhan dibuka.
            </p>
            {note && <p className="remind-note">{note}</p>}
          </div>
        ) : (
          <form className={`remind-form${status === 'error' ? ' invalid' : ''}`} onSubmit={submit} noValidate>
            <input
              id={inputId}
              type="email"
              inputMode="email"
              autoComplete="email"
              placeholder="nama@email.com"
              value={email}
              onChange={(e) => {
                setEmail(e.target.value);
                if (status === 'error') {
                  setStatus('idle');
                  setNote('');
                }
              }}
              aria-invalid={status === 'error'}
              aria-describedby={note ? `${inputId}-note` : undefined}
            />
            <button className="btn primary" type="submit" disabled={status === 'sending'}>
              {status === 'sending' ? 'Menyimpan…' : 'Kirim ke email'}
            </button>
            {note && (
              <p className="remind-note error" id={`${inputId}-note`} role="alert">
                {note}
              </p>
            )}
          </form>
        )}
        <div className="remind-sep"><span>atau ikuti saluran resmi</span></div>
        <div className="remind-channels">
          <a href={WHATSAPP_CHANNEL} target="_blank" rel="noreferrer">
            <WhatsAppIcon size={18} /> Saluran WhatsApp
          </a>
          <a href="https://t.me/xydesk" target="_blank" rel="noreferrer">
            <TelegramIcon size={18} /> Telegram
          </a>
        </div>
        <p className="remind-foot">Email hanya dipakai untuk kabar rilis — tidak untuk yang lain.</p>
      </div>
    </div>
  );
}

export function DownloadPage() {
  const recommended = useRecommendedDownload();

  // Pra-beta: halaman ini berhenti menjual dan mulai menjelaskan. Tidak ada
  // tombol yang menjanjikan file yang belum layak dipasang siapa pun.
  if (!DOWNLOAD_ENABLED) {
    return (
      <main className="content-page download-page">
        <p className="eyebrow">STATUS RILIS</p>
        <h1>Belum bisa diunduh dulu.</h1>
        <p className="page-lead">{DOWNLOAD_DISABLED_REASON}</p>
        <div className="stage-card">
          <div className="stage-row">
            <span>Versi saat ini</span>
            <strong>v{APP_VERSION}</strong>
          </div>
          <div className="stage-row">
            <span>Tahap</span>
            <strong>{STAGE_LABEL[RELEASE_STAGE]}</strong>
          </div>
          <div className="stage-row">
            <span>File unduhan</span>
            <strong>Belum dibuka</strong>
          </div>
        </div>
        <h2 className="stage-heading">Yang harus beres dulu</h2>
        <ul className="stage-list">
          <li>Suara PC benar-benar terdengar di HP, dan mikrofon HP terdengar di PC.</li>
          <li>Ganti monitor saat sesi berjalan, diuji di komputer sungguhan.</li>
          <li>Tombol kontrol, mouse, dan keyboard dari HP terbukti menggerakkan PC.</li>
          <li>Jeda gambar diukur di internet biasa, bukan cuma di jaringan lokal.</li>
          <li>Pemberitahuan benar-benar sampai dan bisa dibuka di HP.</li>
        </ul>
        <p className="page-lead">
          Setiap poin di atas kami tulis kemajuannya di halaman berita. Kamu bisa
          ikuti tanpa perlu memasang apa pun.
        </p>
        <button
          className="btn primary big centered"
          onClick={() => (window.location.href = '/news')}
        >
          Lihat kabar terbaru
        </button>
        <NotifyMeForm />
        <a className="channel-link" href={WHATSAPP_CHANNEL} target="_blank" rel="noreferrer">
          Mau dikabari saat sudah bisa diunduh? Ikuti saluran WhatsApp kami
        </a>
      </main>
    );
  }

  return (
    <main className="content-page download-page">
      <p className="eyebrow">DOWNLOAD CENTER</p>
      <h1>Pilih paket yang tepat.</h1>
      <p className="page-lead">
        Deteksi otomatis merekomendasikan {recommended.label}. Semua file di bawah
        terhubung langsung ke GitHub Release terbaru dan dilindungi SHA-256.
      </p>
      {recommended.file ? (
        <a className="btn primary big centered" href={`${RELEASE_BASE}/${recommended.file}`}>
          <PlatformIcon platform={recommended.platform === 'windows' ? 'windows' : 'android'} />
          Download For {recommended.platform === 'windows' ? 'Windows' : 'Android'}
        </a>
      ) : (
        <a className="btn primary big centered" href="/connect">
          Buka XyDesk Web
        </a>
      )}
      <div className="abi-switcher" aria-label="Pilih arsitektur Android manual">
        <span>Android ABI:</span>
        <button className={typeof localStorage !== 'undefined' && localStorage.getItem('xydesk.download.arch') === 'android-arm64' ? 'active' : ''} onClick={() => {
          localStorage.setItem('xydesk.download.arch', 'android-arm64');
          window.location.reload();
        }}>ARM64</button>
        <button className={typeof localStorage !== 'undefined' && localStorage.getItem('xydesk.download.arch') === 'android-armv7' ? 'active' : ''} onClick={() => {
          localStorage.setItem('xydesk.download.arch', 'android-armv7');
          window.location.reload();
        }}>ARMv7 32-bit</button>
        <button className={typeof localStorage !== 'undefined' && !localStorage.getItem('xydesk.download.arch') ? 'active' : ''} onClick={() => {
          localStorage.removeItem('xydesk.download.arch');
          window.location.reload();
        }}>Deteksi ulang</button>
      </div>
      <PlatformTables />
      <a className="channel-link" href={WHATSAPP_CHANNEL} target="_blank" rel="noreferrer">
        Ikuti info rilis di saluran WhatsApp
      </a>
    </main>
  );
}


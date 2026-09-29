import { RELEASE_BASE, Route, WHATSAPP_CHANNEL } from './app_routes';
import {PUBLIC_ORIGIN} from './site_routes';
import { useEffect, useRef, useState } from 'react';
import { BRAND_POWERED_BY } from './config/brand';

import {
  APP_VERSION,
  CHANGELOG_SLUG,
  DOWNLOAD_DISABLED_REASON,
  DOWNLOAD_ENABLED,
  RELEASE_STAGE,
  STAGE_LABEL,
} from './version';

export function Logo({ size = 30 }: { size?: number }) {
  return (
    <img src="/logo.png" alt="XyDesk" width={size} height={size} aria-hidden="true" />
  );
}

export function SiteHeader({
  route,
  navigate,
  bare = false,
}: {
  route: Route;
  navigate: (r: Route) => void;
  bare?: boolean;
}) {
  const current = typeof route === 'string' ? route : '/news';
  // Di layar sempit menu atas disembunyikan (lihat style.css). Dulu tidak ada
  // penggantinya, jadi pengunjung HP tidak bisa mencapai Berita dan Unduh
  // sama sekali. Panel ini menggantikannya.
  const [menuOpen, setMenuOpen] = useState(false);

  // Tutup menu setiap halaman berganti, atau pengguna menekan Escape.
  useEffect(() => {
    setMenuOpen(false);
  }, [route]);

  useEffect(() => {
    if (!menuOpen) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setMenuOpen(false);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [menuOpen]);

  const go = (r: Route) => {
    setMenuOpen(false);
    navigate(r);
  };

  return (
    <header className={bare ? 'site-header bare' : 'site-header'}>
      <button className="brand" onClick={() => navigate('/')} aria-label="Beranda XyDesk">
        <Logo />
        <strong>XyDesk</strong>
      </button>
      {!bare && (
        <nav className="top-nav">
          <button className={current === '/history' ? 'active' : ''} onClick={() => navigate('/history')}>Riwayat</button>
          <button className={current === '/' ? 'active' : ''} onClick={() => navigate('/')}>
            Beranda
          </button>
          <button className={current === '/news' ? 'active' : ''} onClick={() => navigate('/news')}>
            Berita
          </button>
          <button className={current === '/billing' ? 'active' : ''} onClick={() => navigate('/billing')}>
            Sewa PC
          </button>
          <button className={current === '/controls' ? 'active' : ''} onClick={() => navigate('/controls')}>
            Kontrol
          </button>
          <button className={current === '/download' ? 'active' : ''} onClick={() => navigate('/download')}>
            Unduh
          </button>
        </nav>
      )}
      <div className="header-actions">
        {DOWNLOAD_ENABLED ? (
          <>
            {!bare && (
              <button className="btn ghost desktop-only" onClick={() => navigate('/connect')}>
                Connect Web
              </button>
            )}
            <a className="btn primary" href={`${RELEASE_BASE}/XyDesk-x64.exe`}>
              Unduh Windows
            </a>
          </>
        ) : (
          <>
            <span className="stage-chip desktop-only" title={DOWNLOAD_DISABLED_REASON}>
              {STAGE_LABEL[RELEASE_STAGE]}
            </span>
            <button className="btn primary" onClick={() => navigate('/connect')}>
              Connect Web
            </button>
          </>
        )}
        {!bare && (
          <button
            className="nav-toggle"
            aria-label={menuOpen ? 'Tutup menu' : 'Buka menu'}
            aria-expanded={menuOpen}
            aria-controls="mobile-nav"
            onClick={() => setMenuOpen((o) => !o)}
          >
            <MenuIcon open={menuOpen} />
          </button>
        )}
      </div>

      {menuOpen && !bare && <NavigationOverlay onClose={()=>setMenuOpen(false)} navigate={go} current={current}/>}

    </header>
  );
}

export function NavigationOverlay({onClose,navigate,current}:{onClose:()=>void;navigate:(route:Route)=>void;current:string}) {
  const dialog=useRef<HTMLDialogElement>(null);
  useEffect(()=>{const el=dialog.current;if(!el)return;const previous=document.body.style.overflow;document.body.style.overflow='hidden';el.showModal();return()=>{el.close();document.body.style.overflow=previous;};},[]);
  const links=[['/devices','Perangkat'],['/history','Riwayat sesi'],['/connect','Koneksi baru'],['/controls','Control Studio'],['/','Beranda'],['/news','Berita'],['/download','Unduh'],['/billing','Sewa PC'],['/legal','Legal']] as const;
  return <dialog ref={dialog} className="mobile-navigation-screen" aria-label="Navigasi XyDesk" onCancel={e=>{e.preventDefault();onClose();}}>
    <header><strong>XyDesk <small>Menu</small></strong><button type="button" aria-label="Tutup menu" onClick={onClose}>Tutup <span aria-hidden="true">×</span></button></header>
    <nav id="mobile-nav" aria-label="Menu utama">{links.map(([path,label])=><button type="button" key={path} aria-current={current===path?'page':undefined} onClick={()=>{onClose();navigate(path);}}><span>{label}</span><span aria-hidden="true">↗</span></button>)}</nav>
  </dialog>;
}

const REMOTE_LINKS = [['/devices', 'Perangkat'], ['/history', 'Riwayat'], ['/controls', 'Kontrol']] as const;

// Header area remote memakai kerangka & gaya yang sama dengan header situs.
export function RemoteHeader({ route, navigate }: { route: Route; navigate: (r: Route) => void }) {
  const [open, setOpen] = useState(false);
  useEffect(() => setOpen(false), [route]);
  const current = typeof route === 'string' ? route : '/devices';
  return (
    <header className="site-header remote">
      <a className="brand" href={PUBLIC_ORIGIN}>
        <Logo />
        <strong>XyDesk <small>Remote</small></strong>
      </a>
      <nav className="top-nav" aria-label="Aplikasi remote">
        {REMOTE_LINKS.map(([path, label]) => (
          <button key={path} className={current === path ? 'active' : ''} aria-current={current === path ? 'page' : undefined} onClick={() => navigate(path)}>
            {label}
          </button>
        ))}
      </nav>
      <div className="header-actions">
        <button className="btn primary" onClick={() => navigate('/connect')}>Koneksi baru</button>
        <button className="nav-toggle" type="button" aria-label={open ? 'Tutup menu' : 'Buka menu'} aria-expanded={open} aria-controls="mobile-nav" onClick={() => setOpen((v) => !v)}>
          <MenuIcon open={open} />
        </button>
      </div>
      {open && <NavigationOverlay onClose={() => setOpen(false)} navigate={navigate} current={current} />}
    </header>
  );
}

function MenuIcon({ open }: { open: boolean }) {
  return (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true">
      {open ? <path d="M6 6l12 12M18 6L6 18" /> : <path d="M4 7h16M4 12h16M4 17h16" />}
    </svg>
  );
}

export function SiteFooter({ navigate }: { navigate: (r: Route) => void }) {
  return (
    <footer className="site-footer">
      <div className="footer-inner">
        <div className="footer-brand">
          <img src="/logo-white.png" alt="XyDesk" />
          <strong>XyDesk</strong>
          <p>Remote desktop ringan untuk kerja, bermain, dan mengakses PC dari mana saja.</p>
        </div>
        <div className="footer-columns">
          <div className="footer-column">
            <strong>Produk</strong>
            <button onClick={() => navigate('/connect')}>Connect Web</button>
            <button onClick={() => navigate('/download')}>Download</button>
            <button onClick={() => navigate('/news')}>Berita</button>
          </div>
          <div className="footer-column">
            <strong>Platform</strong>
            {DOWNLOAD_ENABLED ? (
              <>
                <a href={`${RELEASE_BASE}/XyDesk-Android-arm64-v8a.apk`}>Android</a>
                <a href={`${RELEASE_BASE}/XyDesk-x64.exe`}>Windows</a>
              </>
            ) : (
              <>
                <button onClick={() => navigate('/download')}>Android — segera</button>
                <button onClick={() => navigate('/download')}>Windows — segera</button>
              </>
            )}
            <button onClick={() => navigate('/connect')}>iPhone & iPad</button>
          </div>
          <div className="footer-column">
            <strong>Dukungan</strong>
            <button onClick={() => navigate('/legal')}>Legal & Privasi</button>
            <button onClick={() => navigate('/legal')}>Lisensi pihak ketiga</button>
            <a href={WHATSAPP_CHANNEL} target="_blank" rel="noreferrer">Saluran WhatsApp</a>
          </div>
        </div>
        <div className="footer-bottom">
          {/* Versi dibaca dari pubspec saat build; tautannya ke changelog,
              bukan ke GitHub Releases — pengguna butuh penjelasan, bukan
              artefak build. */}
          <span>
            © 2026 XyVerse Technology Global ·{' '}
            <button
              className="footer-version"
              onClick={() => navigate({ page: 'news-detail', slug: CHANGELOG_SLUG })}
            >
              XyDesk v{APP_VERSION} · {STAGE_LABEL[RELEASE_STAGE]}
            </button>
          </span>
          <span>Media sesi tidak disimpan oleh server.</span>
          <span className="footer-powered">{BRAND_POWERED_BY}</span>
        </div>
      </div>
    </footer>
  );
}


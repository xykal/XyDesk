import { DownloadPage, LandingPage } from './landing';
import { AuthPanel, ConnectScreen } from './connect_screen';
import { RemoteHeader, SiteFooter, SiteHeader } from './site_chrome';
import { ControlMappingPage } from './control_mapping_page';
import { AuthStep, GUEST_TOKEN_KEY, TOKEN_KEY, useRoute } from './app_routes';
import { NewsDetailPage, NewsPage } from './news_pages';
import { LegalPage } from './legal_page';
import {confirmAction,showNotice} from './app_dialog';
import {REMOTE_ORIGIN} from './site_routes';
import {ensureGuestAccess} from './guest_access';
import { SessionHistoryPage } from './session_history';
import { isSessionFragment } from './session_runtime';
import { useCallback, useEffect, useState } from 'react';
import {
  ApiError,
  createGuestSession,
  deleteAccount,
  me,
  requestOtp,
  signInWithGoogle,
  updateProfileName,
  UserProfile,
  verifyOtp,
} from './api';
import {
  consumeGoogleRedirect,
  consumeGoogleReturn,
  storeGoogleIdToken,
  clearStoredGoogleIdToken,
  } from './google';

import BillingPage from './Billing';

export default function App() {
  const [route, navigate] = useRoute();
  const sessionRoute=typeof route==='object'&&route.page==='session'?route:null;
  const deviceRoute=typeof route==='object'&&route.page==='device'?route:null;
  const remote=!!sessionRoute||!!deviceRoute||['/connect','/devices','/history','/controls','/auth/callback'].includes(String(route));
  if(remote) return <div className="remote-workspace">
    <RemoteHeader route={route} navigate={navigate} />
    {route==='/devices'||route==='/history'||deviceRoute?<SessionHistoryPage view={deviceRoute?'detail':route==='/history'?'history':'devices'} deviceId={deviceRoute?.deviceId}/>:route==='/controls'?<ControlMappingPage navigate={navigate}/>:<RemoteApp key={sessionRoute?.deviceId??'new'} reconnectDevice={sessionRoute?{deviceId:sessionRoute.deviceId,name:`PC ${sessionRoute.deviceId}`} :undefined} restoreScreen={!!sessionRoute}/>}
  </div>;

  let page: React.ReactNode;
  if (route === '/download') page = <DownloadPage />;
  else if (route === '/legal') page = <LegalPage />;
  else if (route === '/billing') page = <BillingPage />;
  else if (route === '/controls') page = <ControlMappingPage navigate={navigate} />;
  else if (route === '/news') page = <NewsPage navigate={navigate} />;
  else if (typeof route === 'object' && route.page === 'news-detail')
    page = <NewsDetailPage slug={route.slug} navigate={navigate} />;
  else page = <LandingPage navigate={navigate} />;

  return (
    <div className="site">
      <SiteHeader route={route} navigate={navigate} />
      {page}
      <SiteFooter navigate={navigate} />
    </div>
  );
}


function RemoteApp({reconnectDevice,restoreScreen=false}:{reconnectDevice?:{deviceId:string;name:string};restoreScreen?:boolean}={}) {
  const [jwt, setJwt] = useState<string | null>(() =>
    localStorage.getItem(TOKEN_KEY) || sessionStorage.getItem(GUEST_TOKEN_KEY),
  );
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [authStep, setAuthStep] = useState<AuthStep>('closed');
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [otp, setOtp] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [editingName, setEditingName] = useState<string | null>(null);
  const [accountWarning, setAccountWarning] = useState('');

  useEffect(() => {
    setAccountWarning('');
    setProfile(null);
    if (!jwt || sessionStorage.getItem(GUEST_TOKEN_KEY) === jwt) return;
    const request = new AbortController();
    const current = () => !request.signal.aborted && localStorage.getItem(TOKEN_KEY) === jwt;
    me(jwt, request.signal).then(r => {
      if (current()) setProfile(r.user);
    }).catch(error => {
      if (!current()) return;
      if (error instanceof ApiError && error.status === 401) {
        localStorage.removeItem(TOKEN_KEY);
        clearStoredGoogleIdToken();
        window.dispatchEvent(new Event('xydesk-account-changed'));
        setJwt(null);
      } else {
        setAccountWarning('Profil belum dapat dimuat. Sesi akun tetap disimpan; periksa koneksi dan coba lagi.');
      }
    });
    return () => request.abort();
  }, [jwt]);

  // Kembali dari halaman login Google (redirect flow): tukar id_token
  // menjadi sesi XyDesk lalu bersihkan URL. id_token ikut disimpan agar
  // mode founder berita bisa dipakai tanpa menempel ADMIN_TOKEN. Bila login
  // ini dipicu dari halaman berita (returnPath), kembalikan ke sana.
  useEffect(() => {
    const idToken = consumeGoogleRedirect();
    if (idToken) {
      storeGoogleIdToken(idToken);
      void doGoogle(idToken).then(ok => {
        if(!ok)return;
        const kembali = consumeGoogleReturn() || (window.location.origin===REMOTE_ORIGIN?'/devices':'/');
        if (kembali && kembali !== window.location.pathname) {
          window.history.pushState({}, '', kembali);
          window.dispatchEvent(new PopStateEvent('popstate'));
        }
      });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const finishAuth = (token: string, user?: UserProfile) => {
    sessionStorage.removeItem(GUEST_TOKEN_KEY);
    localStorage.setItem(TOKEN_KEY, token);
    window.dispatchEvent(new Event('xydesk-account-changed'));
    setJwt(token);
    if (user) setProfile(user);
    setAuthStep('closed');
  };

  const ensureToken = useCallback(async (signal?: AbortSignal) => {
    if (signal?.aborted) throw signal.reason;
    const member=localStorage.getItem(TOKEN_KEY);
    if (member) return member;
    const token=await ensureGuestAccess(refresh => createGuestSession(refresh, signal), signal);
    setJwt(token);
    return token;
  }, [jwt]);

  const doRequestOtp = async () => {
    setBusy(true);
    setError('');
    try {
      await requestOtp(email.trim().toLowerCase(), name.trim());
      setAuthStep('otp');
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Gagal mengirim OTP.');
    } finally {
      setBusy(false);
    }
  };

  const doVerify = async () => {
    setBusy(true);
    setError('');
    try {
      const session = await verifyOtp(email.trim().toLowerCase(), otp.trim());
      finishAuth(session.token, session.user);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'OTP salah.');
    } finally {
      setBusy(false);
    }
  };

  const doGoogle = async (idToken: string) => {
    setBusy(true);
    setError('');
    try {
      const session = await signInWithGoogle(idToken);
      finishAuth(session.token, session.user);
      return true;
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Login Google gagal.');
      setAuthStep('login');
      return false;
    } finally {
      setBusy(false);
    }
  };

  const signOut = () => {
    clearStoredGoogleIdToken();
    localStorage.removeItem(TOKEN_KEY);
    window.dispatchEvent(new Event('xydesk-account-changed'));
    sessionStorage.removeItem(GUEST_TOKEN_KEY);
    setJwt(null);
    setProfile(null);
  };

  if (authStep !== 'closed') {
    return (
      <AuthPanel
        step={authStep}
        setStep={setAuthStep}
        name={name}
        setName={setName}
        email={email}
        setEmail={setEmail}
        otp={otp}
        setOtp={setOtp}
        busy={busy}
        error={error}
        requestOtp={doRequestOtp}
        verify={doVerify}
      />
    );
  }

  return (
    <main className="connect-page">
      {accountWarning && <p role="status">{accountWarning}</p>}
      <div className="connect-account-bar">
        {profile ? (
          <div className="account-chip">
            {profile.picture ? (
              <img src={profile.picture} alt="" referrerPolicy="no-referrer" />
            ) : (
              <span className="avatar-fallback">
                {(profile.name || profile.email)[0]?.toUpperCase()}
              </span>
            )}
            <button
              type="button"
              className="account-name"
              title="Klik untuk ganti nama"
              onClick={() => setEditingName(profile.name ?? '')}
            >
              {profile.name || profile.email.split('@')[0]}
            </button>
          </div>
        ) : (
          <span className="muted">Mode tamu</span>
        )}
        {profile ? (
          <span className="account-actions">
            <button
              className="text-action danger-text"
              onClick={async () => {
                if (!jwt) return;
                const ok = await confirmAction(
                  'Hapus akun XyDesk secara permanen? Tindakan ini tidak bisa dibatalkan.',
                );
                if (!ok) return;
                try {
                  await deleteAccount(jwt);
                  signOut();
                } catch {
                  await showNotice('Akun belum berhasil dihapus. Periksa koneksi lalu coba lagi.', 'Gagal menghapus akun');
                }
              }}
            >
              Hapus akun
            </button>
            <button className="text-action" onClick={signOut}>Keluar</button>
          </span>
        ) : (
          <button className="text-action" onClick={() => setAuthStep('login')}>Masuk akun</button>
        )}
      </div>
      <ConnectScreen
        initialHostId={reconnectDevice?.deviceId}
        restoreScreen={restoreScreen||window.location.pathname.startsWith('/session')||isSessionFragment(window.location.hash)}
        onLogin={()=>setAuthStep('login')}
        ensureToken={ensureToken}
        accountName={(profile?.name || profile?.email || '').trim()}
      />
      {editingName !== null && (
        <div className="modal-backdrop" onClick={() => setEditingName(null)}>
          <div className="modal-card" onClick={(e) => e.stopPropagation()}>
            <h2>Ganti nama tampilan</h2>
            <input
              autoFocus
              maxLength={60}
              placeholder="Nama baru"
              value={editingName}
              onChange={(e) => setEditingName(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Escape') setEditingName(null);
                if (e.key === 'Enter' && editingName.trim().length >= 2) {
                  void saveName();
                }
              }}
            />
            <div className="modal-actions">
              <button className="ghost-btn" onClick={() => setEditingName(null)}>
                Batal
              </button>
              <button
                disabled={busy || editingName.trim().length < 2}
                onClick={() => void saveName()}
              >
                {busy ? 'Menyimpan…' : 'Simpan'}
              </button>
            </div>
          </div>
        </div>
      )}
    </main>
  );

  async function saveName() {
    if (!jwt || editingName === null) return;
    setBusy(true);
    try {
      const r = await updateProfileName(jwt, editingName.trim());
      setProfile(r.user);
      setEditingName(null);
    } catch {
      // Biarkan modal terbuka; pengguna bisa coba lagi.
    } finally {
      setBusy(false);
    }
  }
}

import { AuthStep, LAST_HOST_KEY } from './app_routes';
import {controllerDisplayLabel} from "./device_label";
import {rememberDestination, forgetDestination, sessionPath} from './session_restore';
import {ConnectionAttempt} from './connection_attempt';
import {browserAccessScope,loadHostAccess,saveHostAccess,forgetHostAccess,mayRetrySession,retryDelay} from './guest_access';
import {AdaptiveVideo} from './adaptive_video';
import {AutoPreset, type AutoDecision, type AutoInput} from './auto_preset';
import { flushSync } from 'react-dom';
import { saveSessionHistory, accountHistoryToken } from './session_history';
import type { HistoryItem, HistoryState } from './session_history';
import { CustomControlMapping } from './control_mapping';
import { cursorLayout, playRemoteAudio, newSessionFragment, isSessionFragment, SESSION_UI_REVISION } from './session_runtime';
import { desktopRect, desktopToCanvas, RemotePointer, KeyOwnership } from './remote_pointer';
import { enterSessionFullscreen, leaveSessionFullscreen, enterSessionLandscape } from './session_fullscreen';
import { videoOnlyStream, startRemoteVideoPlayback } from './video_playback';
import { useCallback, useEffect, useRef, useState } from 'react';
import {
  ApiError,
  } from './api';
import {
  beginGoogleLogin,
  GOOGLE_CLIENT_ID,
} from './google';

import { HostMeta, InputCodec, RtcPhase, RtcSession } from './rtc';
import { frameGuidance } from './session_guidance';
import type { SessionStats } from './rtc';
import { LatencyProbe, attachLatencyProbe, estimateGlassToGlass } from './latency_probe';
import { vkFromCode } from './vk';
import { ConnectionMorph } from './connection_morph';
import { VirtualKeyboard, transportLabel, SessionPanel, SessionRail, DEFAULT_PREFS, QUALITY_META, fmtDurasi, normalizeResolution, useElapsedSec } from './session_ui';
import type { SessionPrefs, StreamQuality, BitrateMbps } from './session_ui';
import { QrScanModal, ConnectGuide, SupportLinks } from './connect_extras';


export interface AuthPanelProps {
  step: AuthStep;
  setStep: (step: AuthStep) => void;
  name: string;
  setName: (value: string) => void;
  email: string;
  setEmail: (value: string) => void;
  otp: string;
  setOtp: (value: string) => void;
  busy: boolean;
  error: string;
  requestOtp: () => void;
  verify: () => void;
}

export function AuthPanel(props: AuthPanelProps) {
  return (
    <main className="auth-panel surface-card">
      <button className="back-action" onClick={() => props.setStep('closed')}>Kembali ke mode tamu</button>
      <img src="/logo.png" alt="XyDesk" className="auth-logo" />
      <h1>{props.step === 'otp' ? 'Kode verifikasi' : 'Masuk ke XyDesk'}</h1>
      {props.step === 'login' ? (
        <>
          <GoogleButton />
          <p className="or-label">atau dengan email</p>
          <label className="auth-field">Nama lengkap<input autoComplete="name" placeholder="Nama lu" value={props.name} onChange={(e) => props.setName(e.target.value)} /></label>
          <label className="auth-field">Alamat email<input type="email" autoComplete="email" autoCapitalize="none" spellCheck={false} placeholder="email@contoh.com" value={props.email} onChange={(e) => props.setEmail(e.target.value)} /></label>
          {props.error && <p className="error" role="alert">{props.error}</p>}
          <button disabled={props.busy || props.name.trim().length < 2 || !props.email.includes('@')} onClick={props.requestOtp}>
            {props.busy ? 'Mengirim…' : 'Kirim kode OTP'}
          </button>
        </>
      ) : (
        <>
          <p className="muted">Enam digit dikirim ke {props.email}.</p>
          <input aria-label="Kode verifikasi enam digit" autoComplete="one-time-code" className="otp-input" inputMode="numeric" maxLength={6} placeholder="000000" value={props.otp} autoFocus onChange={(e) => props.setOtp(e.target.value.replace(/\D/g, ''))} />
          {props.error && <p className="error" role="alert">{props.error}</p>}
          <button disabled={props.busy || props.otp.length !== 6} onClick={props.verify}>
            {props.busy ? 'Memeriksa…' : 'Masuk'}
          </button>
          <button className="text-action" onClick={() => props.setStep('login')}>Ganti email</button>
        </>
      )}
    </main>
  );
}

export function GoogleButton() {
  if (!GOOGLE_CLIENT_ID) return null;
  // Redirect flow: tanpa popup/iframe — aman untuk Safari iOS dan in-app
  // browser yang memblokir popup GIS (gejala "mentok di about:blank").
  return (
    <button type="button" className="google-btn" onClick={() => beginGoogleLogin(window.location.pathname+(isSessionFragment(window.location.hash)?window.location.hash:''))}>
      <svg viewBox="0 0 48 48" width="18" height="18" aria-hidden>
        <path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z" />
        <path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z" />
        <path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z" />
        <path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z" />
      </svg>
      Lanjutkan dengan Google
    </button>
  );
}

export function formatHostId(raw: string): string {
  return raw.replace(/\D/g, '').slice(0, 9).replace(/(\d{3})(?=\d)/g, '$1 ');
}

// ── Riwayat koneksi (maks 5, terbaru di atas) ──
// Hanya ID host yang disimpan, TANPA password. Password pairing adalah
// kunci masuk ke PC seseorang; menyimpannya di localStorage — meski
// di-encode — berarti satu XSS atau satu peramban bersama (urusan nyata
// untuk PC sewaan) cukup untuk membukanya. Klik riwayat mengisi ID dan
// langsung memfokus kolom password.
export interface RecentEntry {
  id: string;
  at: number;
}

export const RECENTS_KEY = 'xydesk.web.recents';

export function loadRecents(): RecentEntry[] {
  try {
    const raw = JSON.parse(localStorage.getItem(RECENTS_KEY) ?? '[]') as Array<
      RecentEntry & { pw?: string }
    >;
    const cleaned = raw
      .map(({ id, at }) => ({ id: String(id ?? '').replace(/\s/g, ''), at: Number(at) || Date.now() }))
      .filter((r) => r.id.length > 0)
      .slice(0, 5);
    // Migrasi satu arah: entri lama yang masih membawa password ditulis
    // ulang tanpanya, jadi sisa masa lalu ikut hilang saat dibaca.
    if (raw.some((r) => typeof r.pw === 'string')) {
      localStorage.setItem(RECENTS_KEY, JSON.stringify(cleaned));
    }
    return cleaned;
  } catch {
    return [];
  }
}

export function saveRecent(id: string) {
  const cleaned = id.replace(/\s/g, '');
  if (!cleaned) return;
  const next: RecentEntry[] = [
    { id: cleaned, at: Date.now() },
    ...loadRecents().filter((r) => r.id !== cleaned),
  ].slice(0, 5);
  localStorage.setItem(RECENTS_KEY, JSON.stringify(next));
}

export function clearRecents() {
  localStorage.removeItem(RECENTS_KEY);
}

export function HistoryIcon() {
  return (
    <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
      <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" />
      <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z" />
    </svg>
  );
}

export function QrIcon() {
  return (
    <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
      <rect x="3" y="3" width="7" height="7" rx="1" />
      <rect x="14" y="3" width="7" height="7" rx="1" />
      <rect x="3" y="14" width="7" height="7" rx="1" />
      <path d="M14 14h3v3h-3zM21 14v.01M14 21v.01M21 21v.01M17.5 17.5H21" />
    </svg>
  );
}

export function EyeIcon() {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
      <path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7z" />
      <circle cx="12" cy="12" r="3" />
    </svg>
  );
}

export function EyeOffIcon() {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
      <path d="M17.94 17.94A10.5 10.5 0 0 1 12 19c-6.5 0-10-7-10-7a19.8 19.8 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c6.5 0 10 7 10 7a19.8 19.8 0 0 1-3.22 4.31" />
      <path d="M14.12 14.12a3 3 0 1 1-4.24-4.24" />
      <path d="M2 2l20 20" />
    </svg>
  );
}

export function ConnectScreen({
  ensureToken,
  accountName,
  onLogin,
  initialHostId,
  restoreScreen=false,
}: {
  ensureToken: (signal?: AbortSignal) => Promise<string>;
  accountName: string;
  onLogin: ()=>void;
  initialHostId?:string;
  restoreScreen?:boolean;
}) {
  const [hostId, setHostId] = useState(() => initialHostId ?? localStorage.getItem(LAST_HOST_KEY) ?? '');
  const [pin, setPin] = useState('');
  const [phase, setPhase] = useState<RtcPhase | ''>('');
  const sessionFragmentRef = useRef(isSessionFragment(window.location.hash)?window.location.hash:'');
  const [sessionOpen, setSessionOpen] = useState(restoreScreen);
  const historyAttempt = useRef<{item:HistoryItem;token:string|null;done:boolean}|null>(null);
  const [recents, setRecents] = useState<RecentEntry[]>(loadRecents);
  const [recentsOpen, setRecentsOpen] = useState(false);
  const [qrOpen, setQrOpen] = useState(false);
  // Pindai QR butuh getUserMedia — sembunyikan tombolnya di browser tanpa
  // API kamera (konteks non-HTTPS / browser tua) daripada memamerkan
  // tombol yang pasti gagal.
  const canScanQr =
    typeof navigator !== 'undefined' && !!navigator.mediaDevices?.getUserMedia;
  const [kbOpen, setKbOpen] = useState(false);
  const [padOpen, setPadOpen] = useState(true);
  const [trackpad, setTrackpad] = useState(() => window.matchMedia?.('(pointer: coarse)').matches ?? false);
  const [panelOpen, setPanelOpen] = useState(false);
  const [panelTab,setPanelTab]=useState<'gambar'|'kontrol'|'suara'|'statistik'|'sesi'>('gambar');
  // Password pairing bisa diperlihatkan — sengaja huruf besar semua di sisi
  // host (tanpa I/O/0/1 yang mudah tertukar), jadi lihat-langsung adalah
  // cara tercepat memastikan ketikan sama dengan layar PC.
  const [showPw, setShowPw] = useState(false);
  // Rail kontrol bisa disembunyikan agar tidak menutupi game — pilihan
  // pengguna disimpan supaya konsisten antar sesi (ala aplikasi).
  const [railHidden, setRailHidden] = useState(() => localStorage.getItem('xydesk.session.railHidden') === '1');
  useEffect(() => {
    localStorage.setItem('xydesk.session.railHidden', railHidden ? '1' : '0');
  }, [railHidden]);
  const adaptive=useRef(new AdaptiveVideo());
  const autoPreset=useRef(new AutoPreset());
  const [autoDecision,setAutoDecision]=useState<AutoDecision|null>(null);
  // Sisi terpanjang layar dalam piksel fisik — dasar plafon resolusi otomatis.
  const clientLongEdgePx=()=>Math.round(Math.max(window.screen?.width??0,window.screen?.height??0)*(window.devicePixelRatio||1))||1280;
  const autoInputFromMeta=(meta:HostMeta|null):AutoInput=>({clientLongEdgePx:clientLongEdgePx(),hostLevel:meta?.video?.level,fpsLimit:meta?.video?.fpsLimit,encoder:meta?.encoder});
  const applyAutoDecision=(d:AutoDecision)=>{
    setAutoDecision(d);
    sessionRef.current?.setResolution(d.resolution);
    sessionRef.current?.setFps(d.fps);
  };
  const [stats, setStats] = useState<SessionStats | null>(null);
  const [connectedAt, setConnectedAt] = useState<number | null>(null);
  const [hudToast, setHudToast] = useState('');
  const totalSesiDetik:number|null = null;
  const durasiDetik = useElapsedSec(phase === 'connected' ? connectedAt : null);
  const sisaDetik:number|null = null;
  const pairingSecretRef=useRef(pin); pairingSecretRef.current=pin;
  const rememberBrowser=true;
  const [,updateAccess]=useState(0);
  const savedAccess=loadHostAccess(hostId.replace(/[\s-]/g,''));
  // Preferensi sesi — bertahan antar sesi di perangkat ini. Migrasi: entri lama tanpa quality/bitrate tetap jalan.
  const [prefs, setPrefs] = useState<SessionPrefs>(() => {
    try {
      const raw = JSON.parse(localStorage.getItem('xydesk.session.prefs') ?? '{}');
      return { ...DEFAULT_PREFS, ...raw, resolution:normalizeResolution(raw.resolution), sens:Math.max(.2,Math.min(4,Number(raw.sens)||DEFAULT_PREFS.sens)), cursorSize:Math.max(24,Math.min(96,Number(raw.cursorSize)||36)), cursorInVideo:raw.cursorInVideo===true } as SessionPrefs;
    } catch {
      return DEFAULT_PREFS;
    }
  });
  useEffect(() => {
    prefsRef.current = prefs;
    localStorage.setItem('xydesk.session.prefs', JSON.stringify(prefs));
    if (audioRef.current) audioRef.current.volume = prefs.volume;
  }, [prefs]);
  const [retryInfo, setRetryInfo] = useState('');
  /// Pesan kegagalan dari `RtcSession.lastError` — hanya diisi saat fase
  /// `error`. Dipisah dari `labels` karena isinya dinamis (beda sebab, beda
  /// pesan), sedangkan `labels` adalah peta statis.
  const [fasePesan, setFasePesan] = useState<string | null>(null);
  // Ref cermin prefs agar handler yang dibuat di closure lama (mis.
  // onAudioTrack) selalu membaca nilai terbaru.
  const prefsRef = useRef(DEFAULT_PREFS as SessionPrefs);
  const sessionRef = useRef<RtcSession | null>(null);
  useEffect(()=>()=>{sessionRef.current?.cancelWallpaper();},[accountName]);
  const attempts = useRef(new ConnectionAttempt());
  const retryRef = useRef({ tries: 0, wasConnected: false });
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const [remoteVideoStream, setRemoteVideoStream] = useState<MediaStream | null>(null);
  const videoPlaybackStop = useRef<(() => void) | null>(null);
  const [videoPlaybackBlocked, setVideoPlaybackBlocked] = useState(false);
  // Probe latensi rVFC: hidup selama video sesi terpasang; reset tiap sesi
  // baru supaya p95 tidak tercemar sesi sebelumnya.
  const latencyProbe = useRef(new LatencyProbe());
  useEffect(() => {
    const video = videoRef.current;
    if (phase !== 'connected' || !remoteVideoStream || !video) return;
    latencyProbe.current.reset();
    return attachLatencyProbe(video, latencyProbe.current);
  }, [phase, remoteVideoStream]);
  const resumeVideo = useCallback(() => {
    videoPlaybackStop.current?.();
    videoPlaybackStop.current = null;
    const video = videoRef.current;
    if (!video || !remoteVideoStream) return;
    videoPlaybackStop.current = startRemoteVideoPlayback(video, remoteVideoStream,
      () => videoRef.current === video, setVideoPlaybackBlocked);
  }, [remoteVideoStream]);
  useEffect(() => {
    if (phase === 'connected' && remoteVideoStream) resumeVideo();
    return () => {
      videoPlaybackStop.current?.();
      videoPlaybackStop.current = null;
    };
  }, [phase, remoteVideoStream, resumeVideo]);
  const [audioOn, setAudioOn] = useState(true);
  const audioOnRef = useRef(true);
  const [audioMessage, setAudioMessage] = useState('');
  const resumeAudio = () => {
    const audio = audioRef.current;
    if (!audio) return;
    audio.muted = !audioOnRef.current;
    const expectedStream = audio.srcObject;
    void playRemoteAudio(audio).then((started) => {
      if (audioRef.current === audio && audio.srcObject === expectedStream) setAudioMessage(started ? '' : 'Belum ada track suara dari host.');
    }).catch(() => { if (audio.srcObject === expectedStream) setAudioMessage('Pemutaran suara tertahan. Ketuk Aktifkan suara.'); });
  };
  const [micOn, setMicOn] = useState(false);
  const [hostMeta, setHostMeta] = useState<HostMeta | null>(null);
  const hostMetaRef = useRef<HostMeta | null>(null);
  const surfaceRef = useRef<HTMLDivElement | null>(null);
  const pinRef = useRef<HTMLInputElement | null>(null);

  const connected = phase === 'connected';
  const cursorRef = useRef<HTMLDivElement | null>(null);
  const pointerRef = useRef<RemotePointer | null>(null);
  const keyOwners=useRef<KeyOwnership|null>(null);
  if(!keyOwners.current)keyOwners.current=new KeyOwnership((vk,down)=>sessionRef.current?.sendInput(InputCodec.key(vk,down)));
  const getImageRect = () => {
    const video = videoRef.current, surface = surfaceRef.current;
    return video && surface ? desktopRect(video.getBoundingClientRect(), video.videoWidth, video.videoHeight, sessionRef.current?.meta?.video) : null;
  };
  const hostCursorRef=useRef<{x:number;y:number;visible:boolean}|null>(null);
  const cursorRaf = useRef(0);
  const paintCursor = () => {
    if (cursorRef.current?.hidden || cursorRaf.current) return;
    cursorRaf.current = requestAnimationFrame(() => {
      cursorRaf.current = 0;
      const el = cursorRef.current, surface = surfaceRef.current;
      if (!el || !surface) return;
      const box = surface.getBoundingClientRect();
      const layout = cursorLayout(box, getImageRect(), hostCursorRef.current||pointerRef.current!.cursor, prefsRef.current.cursorSize);
      el.style.transform = `translate3d(${layout.left}px, ${layout.top}px, 0)`;
      el.style.visibility=hostCursorRef.current?.visible===false?'hidden':'visible';
      el.dataset.ready = layout.ready ? 'image' : 'waiting-video';
      const svg = el.querySelector('svg');
      if (svg) svg.style.transform = `scale(${layout.flipX ? -1 : 1}, ${layout.flipY ? -1 : 1})`;
    });
  };
  if (!pointerRef.current) pointerRef.current = new RemotePointer(getImageRect, (event) => {
    if (event.type === 'move') {
      const video=videoRef.current;
      const p=desktopToCanvas(event.x,event.y,video?.videoWidth||0,video?.videoHeight||0,sessionRef.current?.meta?.video);
      sessionRef.current?.sendInput(InputCodec.mouseMoveAbs(p.x, p.y));
      paintCursor();
    } else if (event.type === 'button') {
      sessionRef.current?.sendInput(InputCodec.mouseButton(event.button, event.down));
    } else sessionRef.current?.sendInput(InputCodec.scroll(0, event.dy));
  });
  useEffect(() => {
    const reset = () => {pointerRef.current?.reset();keyOwners.current?.reset();};
    const visibility = () => { if (document.hidden) reset(); };
    window.addEventListener('blur', reset);
    document.addEventListener('visibilitychange', visibility);
    const observer = new ResizeObserver(paintCursor);
    // Cadangan untuk metadata/resize video yang tidak memberi event pada browser.
    // Tidak dipanggil per decoded frame, dan DOM writes tetap digabung per RAF.
    const refresh = connected ? window.setInterval(paintCursor, 1000) : 0;
    paintCursor();
    if (surfaceRef.current) observer.observe(surfaceRef.current);
    if (connected) setHudToast('Ketuk = klik kiri • tahan diam = klik kanan • geser = gerak. Tahan tombol Klik kiri pada dock untuk drag.');
    return () => {
      reset(); observer.disconnect();
      clearInterval(refresh); cancelAnimationFrame(cursorRaf.current); cursorRaf.current = 0;
      window.removeEventListener('blur', reset);
      document.removeEventListener('visibilitychange', visibility);
    };
  }, [connected]);

  useEffect(()=>{paintCursor();},[prefs.cursorSize,prefs.cursorInVideo]);
  const canConnect = /^\d{9}$/.test(hostId.replace(/[\s-]/g, '')) && (pin.length >= 6 || !!savedAccess) && !['pairing', 'negotiating'].includes(phase);

  // Layout sesi selalu memenuhi viewport; fullscreen browser hanya dari gesture.
  const [fullscreenOn, setFullscreenOn] = useState(false);
  useEffect(() => {
    if (!sessionOpen) return;
    const overflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    const background=Array.from(document.querySelectorAll<HTMLElement>('.remote-header,.connect-account-bar')).map(el=>({el,inert:el.inert}));
    background.forEach(({el})=>{el.inert=true;});
    return () => { document.body.style.overflow = overflow;background.forEach(({el,inert})=>{el.inert=inert;}); };
  }, [sessionOpen]);
  useEffect(() => {
    const onFsChange = () => setFullscreenOn(document.fullscreenElement === surfaceRef.current && !!surfaceRef.current);
    document.addEventListener('fullscreenchange', onFsChange);
    onFsChange();
    return () => document.removeEventListener('fullscreenchange', onFsChange);
  }, []);

  const toggleFullscreen = useCallback(async () => {
    const surface = surfaceRef.current;
    if (!surface) return;
    if (document.fullscreenElement === surface) {
      await leaveSessionFullscreen(surface);
      return;
    }
    const entered = await enterSessionFullscreen(surface);
    if (!entered) {
      setHudToast('Fullscreen browser tidak tersedia atau ditolak. Sesi tetap memenuhi area halaman.');
      return;
    }
    try {
      const orientation = screen.orientation as ScreenOrientation & { lock?: (value: string) => Promise<void> };
      await orientation.lock?.('landscape');
    } catch { /* Rotasi mengikuti dukungan dan pengaturan perangkat. */ }
  }, []);

  const finishHistory = (state:HistoryState) => {
    const attempt = historyAttempt.current;
    if(!attempt || attempt.done) return;
    attempt.done = true;
    attempt.item.endedAt = Date.now(); attempt.item.state = state;
    // Wallpaper datang dari transfer host, tidak pernah dari frame aplikasi.
    attempt.item.previewConsent = !!attempt.item.preview;
    void saveSessionHistory(attempt.item, attempt.token).then(()=>setHudToast('Riwayat sesi tersimpan.')).catch(()=>setHudToast('Riwayat belum tersimpan di server. Periksa koneksi akun.'));
  };
  const capturePreview = async () => {
    const attempt=historyAttempt.current;
    if(!connected || !attempt || attempt.done) return;
    if(attempt.token!==accountHistoryToken()){setHudToast('Akun berubah. Sambungkan ulang sebelum menyimpan preview.');return;}
    const session=sessionRef.current;
    if(!session)return;
    let preview:string;
    try{preview=await session.requestWallpaper();}catch(e){if(sessionRef.current===session && historyAttempt.current===attempt && !attempt.done)setHudToast(e instanceof Error?e.message:'Wallpaper belum tersedia.');return;}
    if(sessionRef.current!==session || historyAttempt.current!==attempt || attempt.done || attempt.token!==accountHistoryToken())return;
    attempt.item.preview=preview;attempt.item.previewConsent=true;
    void saveSessionHistory({...attempt.item,endedAt:Date.now(),state:'interrupted'},attempt.token)
      .then(()=>setHudToast('Preview wallpaper disimpan untuk ID ini.'))
      .catch(()=>setHudToast('Preview belum tersimpan. Periksa koneksi atau penyimpanan.'));
  };

  const automaticPreviewAttempt=useRef<string|null>(null);
  useEffect(()=>{
    const attempt=historyAttempt.current;
    if(!connected||!hostMeta||!attempt||attempt.done||automaticPreviewAttempt.current===attempt.item.id)return;
    automaticPreviewAttempt.current=attempt.item.id;
    void capturePreview();
  },[connected,hostMeta]);

  const connect = async (isRetry = false, automatic = false) => {
    if(!/^\d{9}$/.test(hostId.replace(/[\s-]/g,''))||(!savedAccess&&pin.length<6)){setSessionOpen(true);return;}

    keyOwners.current?.reset();
    pointerRef.current?.reset();
    finishHistory('interrupted');
    const previous = sessionRef.current;
    sessionRef.current = null;
    const signal = attempts.current.begin();
    previous?.stop();
    setRemoteVideoStream(null);
    if (videoRef.current) videoRef.current.srcObject = null;
    if (audioRef.current) audioRef.current.srcObject = null;
    setStats(null);
    setConnectedAt(null);
    setHostMeta(null); hostMetaRef.current=null; setAutoDecision(null);
    flushSync(()=>setSessionOpen(true));
    if(!/^#session\/[0-9a-f]{64}$/.test(sessionFragmentRef.current))sessionFragmentRef.current=newSessionFragment();
    const target=hostId.replace(/[\s-]/g,'');
    window.history.replaceState({},'',sessionPath(target,sessionFragmentRef.current));
    if(!rememberDestination(localStorage,target,sessionFragmentRef.current,browserAccessScope()))setHudToast('Browser tidak dapat menyimpan tujuan sesi. Simpan alamat halaman ini untuk kembali.');

    historyAttempt.current={item:{id:crypto.randomUUID(),deviceId:hostId.replace(/[\s-]/g,''),name:`PC ${hostId}`,startedAt:Date.now(),endedAt:Date.now(),state:'failed',specs:{},preview:null},token:accountHistoryToken(),done:false};
    const attemptId = historyAttempt.current.item.id;
    if(!isRetry && !automatic && surfaceRef.current){
      const current=()=>historyAttempt.current?.item.id===attemptId && !historyAttempt.current.done;
      void enterSessionLandscape(surfaceRef.current,current).then(result=>{
        if(!current())return;
        if(result==='unavailable')setHudToast('Browser menolak fullscreen otomatis. Gunakan tombol layar penuh dan putar HP ke landscape.');
        else if(result==='fullscreen')setHudToast('Fullscreen aktif. Kunci landscape tidak tersedia; putar HP secara manual.');
      });
    }
    try { localStorage.setItem(LAST_HOST_KEY, hostId); } catch { /* preferensi opsional; koneksi tetap berjalan */ }
    setPhase('pairing');
    setFasePesan(null);
    if (!isRetry) {
      retryRef.current.tries = 0;
      retryRef.current.wasConnected = false;
      setRetryInfo('');
    }
    // Klik Konek adalah gesture untuk fullscreen; retry otomatis tidak memaksanya.
    try {
      const jwt = await ensureToken(signal);
      if(!attempts.current.isCurrent(signal) || historyAttempt.current?.item.id!==attemptId || historyAttempt.current.done) return;
      const session = new RtcSession();
      // Label perangkat untuk pesan `pair`: nama akun bila login, kalau
      // tidak kosongkan supaya rtc.ts memakai tebakan browser + OS.
      session.selfName = controllerDisplayLabel(accountName);
      sessionRef.current = session;
      signal.addEventListener('abort', () => session.stop(), {once: true});
      const accessScope=browserAccessScope();
      session.onRememberedAccess=token=>{
        if(sessionRef.current!==session||browserAccessScope()!==accessScope||!rememberBrowser)return;
        if(saveHostAccess(hostId.replace(/[\s-]/g,''),token,accessScope)){pairingSecretRef.current='';setPin('');updateAccess(x=>x+1);}
        else setHudToast('Sesi aktif, tetapi browser tidak dapat menyimpan izin reconnect.');
      };
      session.onRememberedRejected=()=>{forgetHostAccess(hostId.replace(/[\s-]/g,''),accessScope);updateAccess(x=>x+1);};
      hostCursorRef.current=null;
      session.onCursor=cursor=>{
        if(sessionRef.current!==session)return;
        hostCursorRef.current=cursor;
        if(cursor.visible)pointerRef.current?.applyHostPosition(cursor.x,cursor.y);
        paintCursor();
      };
      session.onPhase = (next) => {
        if(sessionRef.current!==session) return;
        setPhase(next);
        if(['ended','error','rejected','peer-offline','host-busy'].includes(next)) finishHistory(next==='ended'||retryRef.current.wasConnected?'interrupted':'failed');
        setFasePesan(session.lastError);
        if (next === 'connected') {
          retryRef.current.tries = 0;
          retryRef.current.wasConnected = true;
          setRetryInfo('');
          setConnectedAt(Date.now());
          saveRecent(hostId);
          setRecents(loadRecents());
          if (!sessionFragmentRef.current) sessionFragmentRef.current = newSessionFragment();
          window.history.replaceState({}, '', sessionPath(hostId.replace(/[\s-]/g,''),sessionFragmentRef.current));
          rememberDestination(localStorage,hostId.replace(/[\s-]/g,''),sessionFragmentRef.current,browserAccessScope());
          setHudToast(document.fullscreenElement===surfaceRef.current?'Sesi layar penuh aktif. Jika masih tegak, putar HP ke landscape.':'Sesi aktif. Gunakan tombol layar penuh jika browser menolak permintaan otomatis.');
        }
        // Reconnect otomatis HANYA bila sesi pernah live lalu putus
        // (jaringan goyah) — bukan untuk pairing gagal/password salah.
        if (
          mayRetrySession(next,session.reconnectAllowed,retryRef.current.wasConnected,retryRef.current.tries) &&
          sessionRef.current === session
        ) {
          retryRef.current.tries += 1;
          const wait = retryDelay(retryRef.current.tries);
          setRetryInfo(
            `Koneksi terputus — mencoba ulang (${retryRef.current.tries}/10)…`,
          );
          attempts.current.schedule(signal, () => void connect(true), wait);
        } else if (['ended','error','peer-offline'].includes(next) && retryRef.current.tries >= 10) {
          setRetryInfo('Gagal menyambung ulang. Coba konek manual.');
        }
      };
      session.onTrack = (stream) => {
        if (sessionRef.current !== session) return;
        setRemoteVideoStream(videoOnlyStream(stream));
      };
      // Audio sistem host — diputar lewat elemen audio terpisah.
      session.onAudioTrack = (stream) => {
        if (sessionRef.current !== session || !audioRef.current) return;
        const audio = audioRef.current;
        audio.srcObject = stream;
        audio.volume = prefsRef.current.volume;
        audio.muted = !audioOnRef.current;
        void session.setAudioEnabled(audioOnRef.current);
        resumeAudio();
      };
      // Meta host (daftar layar + status audio) untuk pemilih monitor.
      let initialPrefsSent = false;
      session.onMeta = (meta) => {
        if (sessionRef.current !== session) return;
        setHostMeta(meta);
        if(prefsRef.current.preset==='manual'&&meta.video?.fpsControl&&meta.video.fpsRequested!==(prefsRef.current.fps===60?60:30))session.setFps(prefsRef.current.fps===60?60:30);
        const attempt=historyAttempt.current;
        if(attempt && !attempt.done && meta.hardware) {
          const specs:Record<string,string>={};
          for(const key of ['hostname','os','cpu','gpu','ram','storage','motherboard']) {const value=meta.hardware[key];if(typeof value==='string')specs[key]=value.slice(0,180);}
          attempt.item.specs=specs; attempt.item.name=specs.hostname||attempt.item.name;
        }
        // Meta tiba lewat channel input yang sudah terbuka. Jangan kirim
        // preferensi sebelum channel siap atau mengirim ulang tiap ganti monitor.
        if (!initialPrefsSent) {
          initialPrefsSent = true;
          session.setQuality(QUALITY_META[prefsRef.current.quality]?.num ?? 0);
          session.setBitrate(prefsRef.current.bitrateMbps||1);
          adaptive.current.reset(prefsRef.current.bitrateMbps||1);
          if (prefsRef.current.preset === 'manual') {
            session.setResolution(prefsRef.current.resolution||'1080p');
            session.setFps(prefsRef.current.fps===60?60:30);
          } else {
            applyAutoDecision(autoPreset.current.initial(autoInputFromMeta(meta), performance.now()));
          }
        } else if (prefsRef.current.preset !== 'manual' && meta.encoder && meta.encoder !== hostMetaRef.current?.encoder) {
          // Encoder host baru ketahuan di frame pertama (NVENC malas) — plafon bisa naik/turun.
          const d = autoPreset.current.update({ ...autoInputFromMeta(meta) }, performance.now());
          if (d) applyAutoDecision(d);
        }
        hostMetaRef.current = meta;
      };
      // Balasan "ambil dari papan klip PC": salin ke papan klip perangkat
      // ini; kalau izin ditolak, tampilkan isinya biar tetap bisa disalin.
      session.onClipboard = async (text) => {
        try {
          await navigator.clipboard.writeText(text);
          setHudToast(`Papan klip PC tersalin ke perangkat ini (${text.length} karakter)`);
        } catch {
          setHudToast(`Papan klip PC: ${text.slice(0, 80)}${text.length > 80 ? '…' : ''}`);
        }
      };
      const access=rememberBrowser?loadHostAccess(hostId.replace(/[\s-]/g,'')):null;
      await session.start(jwt, hostId, pairingSecretRef.current, {remember:rememberBrowser,resumeToken:access||undefined});
    } catch (err) {
      // `ensureToken()` atau `signalToken()` gagal = server tidak terjangkau.
      // Sebelumnya ini jatuh ke `ended` ("Sesi berakhir") — terdengar seperti
      // akhir normal, padahal tidak ada sesi yang pernah dimulai, dan tombol
      // Konek tidak memberi tahu apa yang harus diperbaiki.
      if(!attempts.current.isCurrent(signal) || historyAttempt.current?.item.id!==attemptId || historyAttempt.current.done) return;
      const failed = sessionRef.current;
      sessionRef.current = null;
      failed?.stop();
      setPhase('error');
      setFasePesan(
        'Tidak dapat menghubungi server XyDesk. Periksa koneksi internet, ' +
          'lalu coba lagi.',
      );
      finishHistory('failed');
      if(retryRef.current.wasConnected && retryRef.current.tries<10 && !(err instanceof ApiError && [401,403].includes(err.status))) {
        retryRef.current.tries+=1;
        setRetryInfo(`Server belum dapat dijangkau — mencoba ulang (${retryRef.current.tries}/10)…`);
        attempts.current.schedule(signal, ()=>void connect(true), retryDelay(retryRef.current.tries));
      }
      console.warn('[xydesk] connect gagal:', err);
    }
  };

  const restoreStarted=useRef(false);
  useEffect(()=>{
    if(!restoreScreen||!savedAccess||restoreStarted.current)return;
    const timer=setTimeout(()=>{restoreStarted.current=true;void connect(false,true);},0);
    return()=>clearTimeout(timer);
  },[restoreScreen,savedAccess]);
  const teardown = useCallback((preserveDestination:boolean) => {
    keyOwners.current?.reset();
    pointerRef.current?.reset();
    finishHistory(historyAttempt.current && retryRef.current.wasConnected ? 'ended' : 'cancelled');
    retryRef.current.tries = 10; // blok retry sebelum callback abort
    const stopping=sessionRef.current;
    sessionRef.current=null;
    attempts.current.cancel();
    stopping?.stop();
    if (videoRef.current) videoRef.current.srcObject = null;
    if (audioRef.current) audioRef.current.srcObject = null;
    if(!preserveDestination){
      setSessionOpen(false);
      setRemoteVideoStream(null);
      setAudioMessage('');
      setKbOpen(false);
      setPadOpen(false);
      setPanelOpen(false);
      setStats(null);
      setConnectedAt(null);
      setHudToast('');
      setPhase('');
      setFasePesan(null);

      forgetDestination(localStorage,sessionFragmentRef.current);
      sessionFragmentRef.current = '';
      window.history.replaceState({}, '', '/devices');
      window.dispatchEvent(new PopStateEvent('popstate'));
    }
    void leaveSessionFullscreen(surfaceRef.current);
  }, []);
  const disconnect=useCallback(()=>teardown(false),[teardown]);
  useEffect(()=>()=>teardown(true),[teardown]);
  useEffect(()=>{
    const initialScope=browserAccessScope();
    const changed=()=>{if(browserAccessScope()!==initialScope){
      teardown(true);forgetDestination(localStorage,sessionFragmentRef.current);restoreStarted.current=true;
      if(window.location.pathname.startsWith('/session')||isSessionFragment(window.location.hash)) {
        setSessionOpen(true);setPhase('ended');setRemoteVideoStream(null);setFasePesan('Akun berubah. Periksa akses lalu sambungkan ulang.');
      }
    }};
    window.addEventListener('storage',changed);window.addEventListener('xydesk-account-changed',changed);
    return()=>{window.removeEventListener('storage',changed);window.removeEventListener('xydesk-account-changed',changed);};
  },[teardown]);
  const latestConnect=useRef(connect);latestConnect.current=connect;
  useEffect(()=>{
    const scope=browserAccessScope();
    const isSession=()=>window.location.pathname.startsWith('/session')||isSessionFragment(window.location.hash);
    const hide=()=>{teardown(true);if(isSession()){setPhase('ended');setRemoteVideoStream(null);}};
    const show=(event:PageTransitionEvent)=>{
      if(!event.persisted||!isSession())return;
      setSessionOpen(true);setPhase('ended');
      if(browserAccessScope()===scope&&loadHostAccess(hostId.replace(/[\s-]/g,'')))void latestConnect.current(false,true);
    };
    window.addEventListener('pagehide',hide);window.addEventListener('pageshow',show);
    return()=>{window.removeEventListener('pagehide',hide);window.removeEventListener('pageshow',show);};
  },[teardown,hostId]);

  const send = (bytes:Uint8Array,owner='virtual',repeat=false) => {
    if(bytes[0]===5&&bytes.length>=4)keyOwners.current!.set(bytes[1]|bytes[2]<<8,bytes[3]===1,owner,repeat);
    else sessionRef.current?.sendInput(bytes);
  };

  // Statistik live (tab Gambar di panel): baca getStats tiap detik hanya
  // saat panel terbuka supaya tidak bikin rame saat main.
  useEffect(() => {
    if (!connected) return;
    let alive = true;
    const poll = async () => {
      const s = await sessionRef.current?.readStats();
      if (alive && s) {
        const video = videoRef.current;
        if (video) {
          s.playerState = `${video.paused ? 'paused' : 'playing'}; readyState=${video.readyState}; error=${video.error?.code ?? 'none'}`;
          s.playerSize = `${video.videoWidth}×${video.videoHeight}`;
          s.playerFrames = video.getVideoPlaybackQuality?.().totalVideoFrames;
          if ((s.playerFrames ?? 0) > 0) s.noFrameWarning = false;
        }
        s.latency = latencyProbe.current.summary();
        s.latencyEstimate = estimateGlassToGlass({ rttMs: s.rttMs, summary: s.latency });
        const cursor = cursorRef.current;
        const audio = audioRef.current;
        s.cursorState = `${SESSION_UI_REVISION}; ${cursor?.dataset.ready ?? 'not-mounted'}; ${Math.round(pointerRef.current!.cursor.x * 100)}%,${Math.round(pointerRef.current!.cursor.y * 100)}%`;
        s.audioPlayerState = audio ? `${audio.paused ? 'paused' : 'playing'}; muted=${audio.muted}; volume=${Math.round(audio.volume * 100)}%; readyState=${audio.readyState}; error=${audio.error?.code ?? 'none'}` : 'Belum ada pemutar';
        const p=prefsRef.current;
        const ceiling=p.bitrateMbps||((s.width??1280)*(s.height??720)>1280*720?20:12);
        const next=p.bitrateMbps===0?adaptive.current.update(s,ceiling,performance.now()):null;
        if(next!==null)sessionRef.current?.setBitrate(next);
        if(p.preset!=='manual'){
          const d=autoPreset.current.update({...autoInputFromMeta(hostMetaRef.current),rttMs:s.rttMs,recentLossPct:s.recentLossPct,jitterBufferMs:s.jitterBufferMs,decodeMs:s.decodeMs,deliveredFps:s.fps,glassMs:s.latencyEstimate?.totalMs},performance.now());
          if(d)applyAutoDecision(d);
        }
        setStats(s);
      }
    };
    void poll();
    const t = setInterval(() => void poll(), 1000);
    return () => {
      alive = false;
      clearInterval(t);
    };
  }, [connected]);

  // Toast HUD hilang sendiri setelah 4 detik.
  useEffect(() => {
    if (!hudToast) return;
    const t = setTimeout(() => setHudToast(''), 4000);
    return () => clearTimeout(t);
  }, [hudToast]);

  const toggleTrackpad = useCallback(() => {
    pointerRef.current?.reset();
    setTrackpad((v) => !v);
  }, []);

  // Kirim papan klip perangkat ini ke PC (0x08 CLIPBOARD_SET, bukan
  // diketik per karakter — sama seperti aplikasi).
  const clipboardPush = async () => {
    try {
      const text = await navigator.clipboard.readText();
      if (!text) {
        setHudToast('Papan klip perangkat ini kosong.');
        return;
      }
      sessionRef.current?.sendClipboard(text.slice(0, 32768));
      setHudToast(`Terkirim ke papan klip PC (${text.length} karakter)`);
    } catch {
      setHudToast('Izin baca papan klip ditolak browser — buka keyboard virtual lalu tempel di sana.');
    }
  };

  // Minta isi papan klip PC (0x09 CLIPBOARD_REQ); balasannya lewat
  // onClipboard di atas.
  const clipboardPull = () => {
    sessionRef.current?.requestClipboard();
    setHudToast('Meminta isi papan klip PC…');
  };

  // Tombol fisik yang sudah turun harus dilepas walau fokus pindah ke editor.
  useEffect(() => {
    if (!connected) return;
    const keys = new Set<number>();
    const release = () => { for(const vk of keys) send(InputCodec.key(vk,false),'physical'); keys.clear(); };
    const down = (e:KeyboardEvent) => {
      if((e.key==='Enter'||e.key===' ')&&e.target instanceof HTMLElement&&e.target.closest('button'))return;
      if(document.querySelector('.mapping-tools[data-editing="true"]') || (e.target instanceof HTMLElement && e.target.closest('input,textarea,select,[contenteditable="true"]'))) return;
      const vk=vkFromCode(e.code); if(vk===null) return;
      keys.add(vk); e.preventDefault(); send(InputCodec.key(vk,true),'physical',e.repeat);
    };
    const up = (e:KeyboardEvent) => {
      const vk=vkFromCode(e.code); if(vk===null || !keys.delete(vk)) return;
      e.preventDefault(); send(InputCodec.key(vk,false),'physical');
    };
    const visibility=()=>{if(document.hidden)release();};
    window.addEventListener('keydown',down);window.addEventListener('keyup',up);window.addEventListener('blur',release);document.addEventListener('visibilitychange',visibility);
    return()=>{release();window.removeEventListener('keydown',down);window.removeEventListener('keyup',up);window.removeEventListener('blur',release);document.removeEventListener('visibilitychange',visibility);};
  },[connected]);

  const isImageTarget = (e: { target: EventTarget }) => !surfaceRef.current?.classList.contains('mapping-studio-active') && (e.target === videoRef.current || e.target === surfaceRef.current || (e.target instanceof HTMLElement && e.target.classList.contains('remote-input-area')));
  const pointerMode = (e: React.PointerEvent) => trackpad && e.pointerType !== 'mouse';
  const onPointerDown = (e: React.PointerEvent<HTMLDivElement>) => {
    if (!connected || !isImageTarget(e)) return;
    const accepted = pointerRef.current!.down(e.pointerId, e.clientX, e.clientY, e.button === 2 ? 1 : e.button === 1 ? 2 : 0, pointerMode(e), performance.now(), e.pointerType === 'touch');
    if (accepted) { e.preventDefault(); e.currentTarget.setPointerCapture(e.pointerId); }
  };
  const onPointerMove = (e: React.PointerEvent<HTMLDivElement>) => {
    if (!connected || surfaceRef.current?.classList.contains('mapping-studio-active')) return;
    if (!isImageTarget(e) && !e.currentTarget.hasPointerCapture(e.pointerId)) return;
    pointerRef.current!.move(e.pointerId, e.clientX, e.clientY, pointerMode(e), prefs.sens, prefs.reverseScroll);
  };
  const onPointerEnd = (e: React.PointerEvent<HTMLDivElement>, cancel: boolean) => {
    pointerRef.current!.up(e.pointerId, cancel, prefs.tapClick, performance.now());
  };


  const labels: Record<string, string> = {
    pairing: 'Optimasi Koneksi',
    negotiating: 'Optimasi Koneksi',
    connected: 'Tersambung',
    rejected:
      'ID atau password salah. Periksa keduanya lalu coba lagi — huruf besar dan kecil ikut dihitung.',
    'peer-offline':
      'ID tidak ditemukan. Pastikan ID benar dan XyDesk Host sedang berjalan di PC.',
    'host-busy':
      'Perangkat sedang dipakai sesi lain. Koneksi ini ditolak — tunggu sesi selesai lalu coba lagi.',
    ended: 'Sesi berakhir',
    /// Cadangan bila `fasePesan` kosong. Isi aslinya dinamis dan datang dari
    /// `RtcSession.lastError`.
    error: 'Koneksi gagal. Coba lagi.',
  };

  const toggleSessionAudio=()=>{
            const next = !audioOn;
            setAudioOn(next);
            audioOnRef.current = next;
            if (audioRef.current) audioRef.current.muted = !next;
            void sessionRef.current?.setAudioEnabled(next);
            if (next) resumeAudio();
  };
  const toggleSessionMic=async()=>{
            if (micOn) {
              await sessionRef.current?.disableMic();
              setMicOn(false);
              return;
            }
            const err = await sessionRef.current?.enableMic();
            if (err) {
              setHudToast(err);
              return;
            }
            setMicOn(true);
  };

  return (
    <>
    <section className={sessionOpen ? 'remote-session' : 'connect-card surface-card'}>
      {!sessionOpen && (
        <div className="connect-form">
          <h1>Kendalikan PC dari browser.</h1>
          <p className="muted">Tidak perlu akun. Ambil ID dan password dari XyDesk Host di PC.</p>
          <div className="field-head">
            <span className="field-label">ID perangkat</span>
            <span className="field-tools">
            {canScanQr && (
            <button
              type="button"
              className="recents-toggle"
              onClick={() => setQrOpen(true)}
              title="Pindai QR dari XyDesk Host"
            >
              <QrIcon />
              Pindai QR
            </button>
            )}
            {recents.length > 0 && (
              <button
                type="button"
                className={recentsOpen ? 'recents-toggle open' : 'recents-toggle'}
                onClick={() => setRecentsOpen((v) => !v)}
                aria-expanded={recentsOpen}
              >
                <HistoryIcon />
                Riwayat
              </button>
            )}
            </span>
          </div>
          {recentsOpen && (
            <div className="recents-list">
              {recents.map((r) => (
                <button
                  key={r.id}
                  type="button"
                  className="recent-item"
                  onClick={() => {
                    setHostId(formatHostId(r.id));
                    setPin('');
                    setRecentsOpen(false);
                    pinRef.current?.focus();
                  }}
                >
                  <span className="recent-id">{formatHostId(r.id)}</span>
                  <span className="recent-pw">ketik password</span>
                </button>
              ))}
              <button
                type="button"
                className="recent-clear"
                onClick={() => {
                  clearRecents();
                  setRecents([]);
                  setRecentsOpen(false);
                }}
              >
                Hapus riwayat
              </button>
            </div>
          )}
          <input className="host-id" inputMode="numeric" placeholder="123 456 789" value={hostId} autoFocus={!hostId} onChange={(e) => {
            const value = formatHostId(e.target.value);
            setHostId(value);
            if (value.replace(/\s/g, '').length === 9) pinRef.current?.focus();
          }} />
          {savedAccess&&<p>Izin PC ini tersimpan. <button type="button" className="text-action" onClick={()=>{forgetHostAccess(hostId.replace(/[\s-]/g,''));updateAccess(x=>x+1);}}>Lupakan akses browser</button></p>}
          {!savedAccess&&<><span className="field-label">Password pairing</span>
          <div className="pw-field">
            {/* autoCapitalize "none", bukan "characters" seperti dulu: host
                membandingkan password secara peka-kasus, jadi peramban mobile
                yang mengkapital huruf pertama akan MENGUBAH password yang
                diketik dan mengunci penggunanya sendiri. */}
            <input
              ref={pinRef}
              type={showPw ? 'text' : 'password'}
              placeholder={savedAccess?"Izin browser tersimpan — password tidak diperlukan":"Password pairing"}
              value={pin}
              autoCapitalize="none"
              autoCorrect="off"
              spellCheck={false}
              autoComplete="off"
              onChange={(e) => setPin(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && canConnect && connect()}
            />
            <button
              type="button"
              className="pw-toggle"
              onClick={() => setShowPw((v) => !v)}
              title={showPw ? 'Sembunyikan password' : 'Tampilkan password'}
              aria-label={showPw ? 'Sembunyikan password' : 'Tampilkan password'}
              aria-pressed={showPw}
            >
              {showPw ? <EyeOffIcon /> : <EyeIcon />}
            </button>
          </div></>}
          {phase && (
            <p className="status-text">
              {['pairing', 'negotiating'].includes(phase) ? labels[phase] : fasePesan || labels[phase] || phase}
            </p>
          )}
          {retryInfo && <p className="status-text">{retryInfo}</p>}
          <a className="text-action" href="/history">Buka halaman riwayat</a>
          <button className="connect-cta" disabled={!canConnect} onClick={() => void connect()}>{['pairing', 'negotiating'].includes(phase) ? labels[phase] : 'Konek sekarang'}</button>
          <p className="microcopy">Sesi tamu tanpa batas durasi. Izin dan riwayat tersimpan di browser ini; pemilik PC tetap dapat mencabut akses.</p>
        </div>
      )}
      <div
        ref={surfaceRef}
        className="video-surface"
        hidden={!sessionOpen}
        onPointerMove={onPointerMove}
        onPointerDown={onPointerDown}
        onPointerUp={(e) => onPointerEnd(e, false)}
        onPointerCancel={(e) => onPointerEnd(e, true)}
        onLostPointerCapture={(e) => onPointerEnd(e, true)}
        onWheel={(e) => { if (isImageTarget(e)) send(InputCodec.scroll(-e.deltaX, -e.deltaY)); }}
        onContextMenu={(e) => e.preventDefault()}
      >
        <video ref={videoRef} autoPlay playsInline muted onLoadedMetadata={paintCursor} onResize={paintCursor} />
        {connected && videoPlaybackBlocked && <button className="video-playback-recovery" type="button" onPointerDown={e=>e.stopPropagation()} onClick={resumeVideo}>Browser menahan pemutaran. Ketuk untuk tampilkan video.</button>}
        {sessionOpen && !connected && <div className="session-connecting" role="region" aria-label="Pemulihan sesi">
          <img src="/logo.png" alt="XyDesk" width="64" height="64"/>
          {['pairing','negotiating'].includes(phase)&&<ConnectionMorph/>}
          <h2 aria-live="polite">{['pairing','negotiating'].includes(phase)?'Menghubungkan perangkat…':fasePesan||labels[phase]||'Lanjutkan sesi perangkat'}</h2>
          <p>{['pairing','negotiating'].includes(phase)?'Menyiapkan layar jarak jauh.':'Siap melanjutkan koneksi lu.'}</p>
          {!['pairing','negotiating'].includes(phase)&&<div className="session-resume-form">
            {!/^\d{9}$/.test(hostId.replace(/[\s-]/g,''))&&<label>ID perangkat<input inputMode="numeric" value={hostId} onChange={e=>setHostId(e.target.value)} autoComplete="off"/></label>}
            {!savedAccess&&<label>Password pairing<input ref={pinRef} type="password" value={pin} onChange={e=>setPin(e.target.value)} autoComplete="off" onKeyDown={e=>{if(e.key==='Enter'&&canConnect)void connect();}}/><small>Akses tersimpan tidak tersedia atau sudah dicabut. Password tidak disimpan.</small></label>}
            <button className="btn primary" disabled={!canConnect} onClick={()=>void connect()}>Sambungkan ulang</button>
            {!savedAccess&&<button className="btn ghost" onClick={onLogin}>Masuk akun untuk memakai akses tersimpan</button>}
          </div>}
          <button className="btn ghost" onClick={disconnect}>Kembali / batalkan</button>
        </div>}
        <div className="remote-input-area" aria-hidden="true" hidden={!connected} />
        <div ref={cursorRef} className="remote-control-cursor" hidden={true} style={{display:"none",width:prefs.cursorSize,height:prefs.cursorSize*4/3}} aria-hidden="true" data-revision={SESSION_UI_REVISION}>
          <svg viewBox="0 0 24 32">
            <path d="M2 2 L2 25 L8 20 L13 30 L18 27 L13 18 L22 17 Z" fill="white" stroke="#111" strokeWidth="2" strokeLinejoin="round" />
          </svg>
        </div>
        {/* Audio sistem host (track Opus) — elemen terpisah, tidak di-mute. */}
        <audio ref={audioRef} autoPlay muted={!audioOn} />
        {connected && audioMessage && <div className="session-audio-notice" role="status">
          <span>{audioMessage}</span>
          <button type="button" onClick={() => { audioOnRef.current = true; setAudioOn(true); void sessionRef.current?.setAudioEnabled(true); resumeAudio(); }}>Aktifkan suara</button>
        </div>}

        {connected && (
          <div
            className={`sesi-waktu${sisaDetik !== null && sisaDetik <= 300 ? ' kritis' : ''}`}
            title={
              'Durasi sesi berjalan — tanpa batas durasi tamu'
            }
          >
            <span>{fmtDurasi(durasiDetik ?? 0)}</span>
            {sisaDetik !== null && (
              <em>{sisaDetik > 0 ? `tersisa ${fmtDurasi(sisaDetik)}` : 'batas tercapai'}</em>
            )}
          </div>
        )}
        {connected && stats && frameGuidance(stats) && (() => {
          const g = frameGuidance(stats)!;
          return (
            <div className={`session-frame-guidance${g.relayRelated ? ' relay' : ''}`} role="status" aria-live="polite">
              <strong>{g.title}</strong>
              <p>{g.detail}</p>
              <div className="session-frame-actions">
                <button type="button" onClick={() => {setPanelTab('gambar');setPanelOpen(true);setKbOpen(false);}}>{g.action}</button>
              </div>
            </div>
          );
        })()}
        {connected && stats && !(panelOpen && panelTab==='statistik') && !railHidden && <div className={`session-connection-status${stats.noFrameWarning ? ' warning' : ''}`} role="status" aria-live="polite" title="Status koneksi live dari WebRTC">
          <span className="session-connection-dot" aria-hidden="true" />
          <strong>{stats.fps > 0 ? `${Math.round(stats.fps)} FPS` : 'FPS —'}</strong>
          <span>{stats.mbps > 0 ? `${Math.round(stats.mbps * 1000)} kbps` : 'kbps —'}</span>
          <span>{transportLabel(stats)}</span>
          {stats.noFrameWarning && <span className="session-freeze-label">Freeze terdeteksi</span>}
        </div>}
        {connected && <>
        <SessionRail
          collapsed={railHidden}
          onToggleCollapsed={() => setRailHidden(v=>!v)}
          audioOn={audioOn}
          onAudio={toggleSessionAudio}
          micOn={micOn}
          onMic={toggleSessionMic}
          kbOpen={kbOpen}
          onKeyboard={() => {setKbOpen(v=>!v);setPanelOpen(false);}}
          padOpen={padOpen}
          onPad={() => {setPadOpen(v=>!v);setPanelOpen(false);}}
          trackpad={trackpad}
          onTrackpad={toggleTrackpad}
          onClipboardPush={() => void clipboardPush()}
          onClipboardPull={clipboardPull}
          onFullscreen={toggleFullscreen}
          fullscreenOn={fullscreenOn}
          statisticsOpen={panelOpen&&panelTab==='statistik'}
          onStatistics={()=>{setPanelTab('statistik');setPanelOpen(true);}}
          panelOpen={panelOpen}
          onPanel={() => {setPanelOpen(v=>!v);}}
          onDisconnect={disconnect}
        />

        {retryInfo && <p className="session-retry">{retryInfo}</p>}
        {hudToast && <p className="hud-toast" role="status">{hudToast}</p>}
        {panelOpen && (
          <SessionPanel
            controlsVisible={padOpen}
            onControlsVisibilityChange={setPadOpen}
            audioOn={audioOn} onAudio={toggleSessionAudio}
            micOn={micOn} onMic={()=>void toggleSessionMic()}
            activeTab={panelTab}
            onTabChange={setPanelTab}
            prefs={prefs}
            onChange={setPrefs}
            onFps={fps=>sessionRef.current?.setFps(fps)}
            fpsLimit={hostMeta?.video?.fpsLimit}
            encoder={hostMeta?.encoder}
            videoApplied={hostMeta?.video?.applied}
            capture={hostMeta?.capture}
            onClose={() => setPanelOpen(false)}
            hostId={hostId}
            onDisconnect={disconnect}
            stats={stats}
            displays={hostMeta?.displays ?? []}
            wantedDisplay={hostMeta?.wanted ?? 0}
            desktopMode={hostMeta?.desktopMode}
            onSelectDisplay={(i) => sessionRef.current?.selectDisplay(i)}
            connectedAt={connectedAt}
            railCollapsed={railHidden}
            totalSesiDetik={totalSesiDetik}
            trackpad={trackpad}
            onTrackpadMode={(on) => {
              if (on !== trackpad) toggleTrackpad();
            }}
            onResolution={resolution=>sessionRef.current?.setResolution(resolution)}
            autoDecision={autoDecision}
            onPreset={preset=>{
              if(preset==='manual'){
                sessionRef.current?.setResolution(prefsRef.current.resolution||'720p');
                sessionRef.current?.setFps(prefsRef.current.fps===60?60:30);
              } else {
                applyAutoDecision(autoPreset.current.initial(autoInputFromMeta(hostMetaRef.current),performance.now()));
              }
            }}
            onQuality={(q: StreamQuality) => {
              const num = QUALITY_META[q].num;
              sessionRef.current?.setQuality(num);
            }}
            onBitrate={(mbps: BitrateMbps) => {
              sessionRef.current?.setBitrate(mbps||1);
              adaptive.current.reset(mbps||1);
            }}
          />
        )}
        {padOpen && <CustomControlMapping onEditStart={()=>{pointerRef.current?.reset();keyOwners.current?.reset();}} onToggleMode={toggleTrackpad} send={bytes=>{
          if(bytes[0]===3){if(bytes[2])pointerRef.current!.sync();pointerRef.current!.button(bytes[1],bytes[2]===1,'mapping');}
          else send(bytes,'mapping');
        }} />}
        {kbOpen && <VirtualKeyboard send={send} onClose={()=>setKbOpen(false)} />}
        </>}
      </div>
    </section>
    {qrOpen && (
      <QrScanModal
        onClose={() => setQrOpen(false)}
        onResult={(id) => {
          setHostId(formatHostId(id));
          setQrOpen(false);
          pinRef.current?.focus();
        }}
      />
    )}
    {!sessionOpen && (
      <>
        {hudToast && <p role="status">{hudToast}</p>}
        <SupportLinks />
        <ConnectGuide />
      </>
    )}
    </>
  );
}


// Kontrol sesi lanjutan versi web: rail kontrol ikon di tepi kanan,
// panel pengaturan ber-tab (Gambar/Suara/Kontrol/Sesi), keyboard virtual
// penuh, dan panel gaming. Cermin konsep dari lib/features/session/
// session_page.dart (rail kanan + sembunyikan kontrol) dan
// session_panels.dart (empat tab panel) di aplikasi — protokol inputnya
// sama persis (host/src/input.rs), hanya medianya yang beda.
import { useEffect, useState, useRef } from 'react';
import type { CSSProperties } from 'react';
import { InputCodec } from './rtc';
import { relayReasonText } from './session_guidance';
import type { SessionStats, HostMeta } from './rtc';

type Send = (bytes: Uint8Array) => void;

// ── Ikon garis (stroke) 24px, ala set ikon aplikasi ────────────
// Digambar inline supaya overlay sesi tidak menunggu aset eksternal.
function Svg({ children }: { children: React.ReactNode }) {
  return (
    <svg
      width="20"
      height="20"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {children}
    </svg>
  );
}
const IcChevronLeft = () => <Svg><path d="M15 18l-6-6 6-6" /></Svg>;
const IcChevronRight = () => <Svg><path d="M9 18l6-6-6-6" /></Svg>;
const IcVolume = () => (
  <Svg>
    <path d="M11 5L6 9H2v6h4l5 4V5z" />
    <path d="M15.5 8.5a5 5 0 0 1 0 7" />
    <path d="M18.3 5.7a9 9 0 0 1 0 12.6" />
  </Svg>
);
const IcMic = () => (
  <Svg>
    <rect x="9" y="2" width="6" height="12" rx="3" />
    <path d="M5 10a7 7 0 0 0 14 0" />
    <path d="M12 17v4" />
  </Svg>
);
const IcKeyboard = () => (
  <Svg>
    <rect x="2" y="6" width="20" height="12" rx="2" />
    <path d="M6 10h.01M10 10h.01M14 10h.01M18 10h.01M8 14h8" />
  </Svg>
);
const IcGamepad = () => (
  <Svg>
    <path d="M6 11h4M8 9v4" />
    <path d="M15 12h.01M18 10h.01" />
    <path d="M17.3 5H6.7a4 4 0 0 0-4 3.6C2.6 9.4 2 14.5 2 16a3 3 0 0 0 3 3c1 0 1.5-.5 2-1l1.4-1.4a2 2 0 0 1 1.4-.6h4.4a2 2 0 0 1 1.4.6L17 18c.5.5 1 1 2 1a3 3 0 0 0 3-3c0-1.5-.6-6.6-.7-7.4a4 4 0 0 0-4-3.6z" />
  </Svg>
);
const IcMove = () => (
  <Svg>
    <path d="M5 9l-3 3 3 3M9 5l3-3 3 3M15 19l-3 3-3-3M19 9l3 3-3 3" />
    <path d="M2 12h20M12 2v20" />
  </Svg>
);
const IcClipboardUp = () => (
  <Svg>
    <rect x="8" y="2" width="8" height="4" rx="1" />
    <path d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2" />
    <path d="M12 16V8M9 11l3-3 3 3" />
  </Svg>
);
const IcClipboardDown = () => (
  <Svg>
    <rect x="8" y="2" width="8" height="4" rx="1" />
    <path d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2" />
    <path d="M12 8v8M9 13l3 3 3-3" />
  </Svg>
);
const IcFullscreen = () => (
  <Svg>
    <path d="M8 3H5a2 2 0 0 0-2 2v3M21 8V5a2 2 0 0 0-2-2h-3M3 16v3a2 2 0 0 0 2 2h3M16 21h3a2 2 0 0 0 2-2v-3" />
  </Svg>
);
const IcSliders = () => (
  <Svg>
    <path d="M21 4h-7M10 4H3M21 12h-9M8 12H3M21 20h-5M12 20H3" />
    <path d="M14 2v4M8 10v4M16 18v4" />
  </Svg>
);
const IcPower = () => (
  <Svg>
    <path d="M18.4 6.6a9 9 0 1 1-12.8 0" />
    <path d="M12 2v10" />
  </Svg>
);

// ── Rail kontrol kanan, ala aplikasi ───────────────────────────
export function SessionRail({
  collapsed,
  statisticsOpen=false,
  onStatistics,
  onToggleCollapsed,
  audioOn,
  onAudio,
  micOn,
  onMic,
  kbOpen,
  onKeyboard,
  padOpen,
  onPad,
  trackpad,
  onTrackpad,
  onClipboardPush,
  onClipboardPull,
  onFullscreen,
  fullscreenOn,
  panelOpen,
  onPanel,
  onDisconnect,
}: {
  collapsed: boolean;
  statisticsOpen?:boolean;
  onStatistics?:()=>void;
  onToggleCollapsed: () => void;
  audioOn: boolean;
  onAudio: () => void;
  micOn: boolean;
  onMic: () => void;
  kbOpen: boolean;
  onKeyboard: () => void;
  padOpen: boolean;
  onPad: () => void;
  trackpad: boolean;
  onTrackpad: () => void;
  onClipboardPush: () => void;
  onClipboardPull: () => void;
  onFullscreen: () => void;
  fullscreenOn: boolean;
  panelOpen: boolean;
  onPanel: () => void;
  onDisconnect: () => void;
}) {
  if(collapsed)return <button type="button" className="srail-reveal" title="Tampilkan rail" aria-label="Tampilkan rail" onPointerDown={e=>e.stopPropagation()} onClick={onToggleCollapsed}><IcChevronLeft/></button>;
  return (<>
      <button type="button" className={`srail-keyboard${kbOpen ? ' on' : ''}`} title="Keyboard" aria-label="Keyboard" aria-pressed={kbOpen} onClick={onKeyboard}>
        <IcKeyboard />
      </button>
    <div className={collapsed?"srail compact":"srail"} role="toolbar" aria-label="Kontrol sesi" onPointerDown={(e) => e.stopPropagation()} onPointerUp={(e) => e.stopPropagation()}>
      <button type="button" className="srail-btn" title={collapsed?"Kontrol":"Sembunyikan rail"} aria-label={collapsed?"Kontrol":"Sembunyikan rail"} onClick={onToggleCollapsed}>
        {collapsed?<IcChevronLeft />:<IcChevronRight />}
      </button>
      <span className="srail-sep" />
      <button type="button" className={`srail-btn${audioOn ? ' on' : ''}`} title="Suara PC" aria-label="Suara PC" aria-pressed={audioOn} onClick={onAudio}>
        <IcVolume />
      </button>
      <button type="button" className={`srail-btn${micOn ? ' on' : ''}`} title="Mik ke PC" aria-label="Mik ke PC" aria-pressed={micOn} onClick={onMic}>
        <IcMic />
      </button>

      <button type="button" className={`srail-btn${padOpen ? ' on' : ''}`} title="Tombol mapping" aria-label="Kontrol layar" aria-pressed={padOpen} onClick={onPad}>
        <IcGamepad />
      </button>
      <button type="button" className={`srail-btn${trackpad ? ' on' : ''}`} title={trackpad?'Trackpad':'Direct'} aria-label="Mode trackpad" aria-pressed={trackpad} onClick={onTrackpad}>
        <IcMove />
      </button>
      <button type="button" className="srail-btn" title="Kirim ke papan klip PC" aria-label="Kirim ke papan klip PC" onClick={onClipboardPush}>
        <IcClipboardUp />
      </button>
      <button type="button" className="srail-btn" title="Ambil dari papan klip PC" aria-label="Ambil dari papan klip PC" onClick={onClipboardPull}>
        <IcClipboardDown />
      </button>
      <button type="button" className={`srail-btn${fullscreenOn ? ' on' : ''}`} title={fullscreenOn ? 'Keluar layar penuh' : 'Layar penuh'} aria-label={fullscreenOn ? 'Keluar layar penuh' : 'Layar penuh'} aria-pressed={fullscreenOn} onClick={onFullscreen}>
        <IcFullscreen />
      </button>
      {onStatistics&&<button type="button" className={`srail-btn${statisticsOpen?' on':''}`} aria-label="Statistik koneksi" title="Statistik koneksi" aria-pressed={statisticsOpen} onClick={onStatistics}><Svg><path d="M5 20V10M12 20V4M19 20v-7"/></Svg></button>}
      <button type="button" className={`srail-btn${panelOpen ? ' on' : ''}`} title="Pengaturan sesi" aria-label="Pengaturan sesi" aria-pressed={panelOpen} onClick={onPanel}>
        <IcSliders />
      </button>
      <span className="srail-sep" />
      <button type="button" className="srail-btn danger" title="Putuskan" aria-label="Putuskan" onClick={onDisconnect}>
        <IcPower />
      </button>
    </div></>
  );
}

// ── Keyboard virtual penuh ─────────────────────────────────────
type KeySpec = [string, number, number?, boolean?];

const VKB_ROWS: KeySpec[][] = [
  [
    ['Esc', 0x1b], ['F1', 0x70], ['F2', 0x71], ['F3', 0x72], ['F4', 0x73],
    ['F5', 0x74], ['F6', 0x75], ['F7', 0x76], ['F8', 0x77], ['F9', 0x78],
    ['F10', 0x79], ['F11', 0x7a], ['F12', 0x7b], ['\u232b', 0x08, 1.6],
  ],
  [
    ['`', 0xc0], ['1', 0x31], ['2', 0x32], ['3', 0x33], ['4', 0x34],
    ['5', 0x35], ['6', 0x36], ['7', 0x37], ['8', 0x38], ['9', 0x39],
    ['0', 0x30], ['-', 0xbd], ['=', 0xbb], ['Del', 0x2e, 1.6],
  ],
  [
    ['Tab', 0x09, 1.5], ['Q', 0x51], ['W', 0x57], ['E', 0x45], ['R', 0x52],
    ['T', 0x54], ['Y', 0x59], ['U', 0x55], ['I', 0x49], ['O', 0x4f],
    ['P', 0x50], ['[', 0xdb], [']', 0xdd], ['\\', 0xdc, 1.4],
  ],
  [
    ['Caps', 0x14, 1.8], ['A', 0x41], ['S', 0x53], ['D', 0x44], ['F', 0x46],
    ['G', 0x47], ['H', 0x48], ['J', 0x4a], ['K', 0x4b], ['L', 0x4c],
    [';', 0xba], ["'", 0xde], ['Enter', 0x0d, 2.1],
  ],
  [
    ['Shift', 0xa0, 2.2, true], ['Z', 0x5a], ['X', 0x58], ['C', 0x43],
    ['V', 0x56], ['B', 0x42], ['N', 0x4e], ['M', 0x4d], [',', 0xbc],
    ['.', 0xbe], ['/', 0xbf], ['Shift', 0xa1, 2.2, true],
  ],
  [
    ['Ctrl', 0xa2, 1.5, true], ['Win', 0x5b, 1.2, true],
    ['Alt', 0xa4, 1.2, true], ['Spasi', 0x20, 5.4],
    ['Alt', 0xa5, 1.1, true], ['\u2190', 0x25], ['\u2191', 0x26],
    ['\u2193', 0x28], ['\u2192', 0x27],
  ],
];

function keyboardRows(layer:'abc'|'numbers'|'fn'|'full'):KeySpec[][] {
 const letters=(text:string):KeySpec[]=>[...text].map(c=>[c,c.charCodeAt(0)]);
 const bottom:KeySpec[]=[['Ctrl',0xa2,1,true],['Alt',0xa4,1,true],['Spasi',0x20,4],['Enter',0x0d,1.6]];
 if(layer==='full')return VKB_ROWS;
 if(layer==='numbers')return [VKB_ROWS[1].slice(1,11),[['-',0xbd],['=',0xbb],['[',0xdb],[']',0xdd],[';',0xba],["'",0xde],[',',0xbc],['.',0xbe],['/',0xbf],['⌫',0x08]], [['Esc',0x1b],['Tab',0x09],['Shift',0xa0,1,true],['←',0x25],['↑',0x26],['↓',0x28],['→',0x27]],bottom];
 if(layer==='fn')return [0,1,2,3].map(row=>Array.from({length:6},(_,column):KeySpec=>{const n=row*6+column;return [`F${n+1}`,0x70+n];}));
 return [letters('QWERTYUIOP'),[['Caps',0x14,1.3],...letters('ASDFGHJKL')],[['Shift',0xa0,1.3,true],...letters('ZXCVBNM'),['⌫',0x08,1.3]],bottom];
}

export function VirtualKeyboard({send,onClose}:{send:Send;onClose?:()=>void}) {
  const [mode,setMode]=useState<'virtual'|'native'>(()=>{try{return localStorage.getItem('xydesk.keyboard.mode')==='native'?'native':'virtual';}catch{return 'virtual';}});
  const [settings,setSettings]=useState(false);
  const [transparency,setTransparency]=useState(()=>{try{const value=Number(localStorage.getItem('xydesk.keyboard.transparency'));return Number.isFinite(value)?Math.min(90,Math.max(0,value)):0;}catch{return 0;}});
  const keyboardStyle={'--keyboard-fill':String(1-transparency/100)} as CSSProperties;
  const [draft,setDraft]=useState('');
  const composing=useRef(false);
  const commit=()=>{if(!composing.current&&draft){send(InputCodec.text(draft));setDraft('');}};
  const key=(vk:number)=>{send(InputCodec.key(vk,true));send(InputCodec.key(vk,false));};
  return <section className="keyboard-shell" style={keyboardStyle} aria-label="Keyboard remote" onPointerDown={e=>e.stopPropagation()}>
    <div className="keyboard-toolbar"><span>{mode==='virtual'?'Keyboard virtual':'Keyboard HP · ketik lalu kirim'}</span>
      <button type="button" aria-label="Pengaturan keyboard" aria-expanded={settings} onClick={()=>setSettings(v=>!v)}><IcSliders/></button>
      <button type="button" aria-label="Tutup keyboard" onClick={onClose}><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true"><path d="m5 9 7 7 7-7"/></svg></button>
    </div>
    {settings&&<div onKeyDown={e=>e.stopPropagation()}><label className="keyboard-settings keyboard-transparency">Transparansi background <output>{transparency}%</output><input aria-label="Transparansi background keyboard" type="range" min="0" max="90" step="5" value={transparency} onChange={e=>{const value=Number(e.target.value);setTransparency(value);try{localStorage.setItem('xydesk.keyboard.transparency',String(value));}catch{}}}/></label><label className="keyboard-settings">Jenis keyboard <select value={mode} onChange={e=>{const next=e.target.value==='native'?'native':'virtual';setMode(next);try{localStorage.setItem('xydesk.keyboard.mode',next);}catch{}}}><option value="virtual">Virtual · tombol lengkap</option><option value="native">Keyboard HP · teks / IME</option></select></label></div>}
    {mode==='virtual'?<VirtualKeyGrid send={send}/>:<div className="native-keyboard"><textarea autoFocus aria-label="Teks untuk PC" placeholder="Ketik di keyboard HP, lalu Kirim teks" value={draft} maxLength={4000} onChange={e=>setDraft(e.target.value)} onCompositionStart={()=>{composing.current=true;}} onCompositionEnd={()=>{composing.current=false;}} onKeyDown={e=>{e.stopPropagation();if(e.key==='Enter'&&!e.shiftKey&&!e.nativeEvent.isComposing){e.preventDefault();commit();}}}/><div><button type="button" disabled={!draft} onClick={commit}>Kirim teks</button><button type="button" onClick={()=>key(0x08)}>Backspace PC</button><button type="button" onClick={()=>key(0x0d)}>Enter PC</button></div></div>}
  </section>;
}

function VirtualKeyGrid({ send }: { send: Send }) {
  const [layer,setLayer]=useState<'abc'|'numbers'|'fn'|'full'>('abc');
  const [held, updateHeld] = useState<ReadonlySet<number>>(new Set());
  const [caps, setCaps] = useState(false);
  const [physicalShift,setPhysicalShift]=useState(false);
  useEffect(()=>{const down=(e:KeyboardEvent)=>{setPhysicalShift(e.shiftKey);if(e.code==='CapsLock'&&!e.repeat)setCaps(v=>!v);};const up=(e:KeyboardEvent)=>setPhysicalShift(e.shiftKey);const blur=()=>setPhysicalShift(false);window.addEventListener('keydown',down);window.addEventListener('keyup',up);window.addEventListener('blur',blur);return()=>{window.removeEventListener('keydown',down);window.removeEventListener('keyup',up);window.removeEventListener('blur',blur);};},[]);
  const heldRef=useRef<ReadonlySet<number>>(new Set());const sendRef=useRef(send);sendRef.current=send;
  const setHeld=(value:ReadonlySet<number>)=>{heldRef.current=value;updateHeld(value);};
  const release=()=>{for(const vk of heldRef.current)sendRef.current(InputCodec.key(vk,false));heldRef.current=new Set();};
  useEffect(()=>{const clear=()=>{release();updateHeld(new Set());};const hidden=()=>{if(document.hidden)clear();};window.addEventListener('blur',clear);document.addEventListener('visibilitychange',hidden);return()=>{release();window.removeEventListener('blur',clear);document.removeEventListener('visibilitychange',hidden);};},[]);

  const tap = (vk: number, modifier: boolean) => {
    // Caps Lock adalah toggle keyboard, bukan modifier yang perlu ditahan.
    // Simpan state lokal agar UI virtual selalu jujur terhadap mode huruf.
    if (vk === 0x14) {
      send(InputCodec.key(vk, true));
      send(InputCodec.key(vk, false));
      setCaps(value => !value);
      return;
    }
    if (modifier) {
      if (heldRef.current.has(vk)) {
        send(InputCodec.key(vk, false));
        const next = new Set(heldRef.current);
        next.delete(vk);
        setHeld(next);
      } else {
        send(InputCodec.key(vk, true));
        setHeld(new Set(heldRef.current).add(vk));
      }
      return;
    }
    send(InputCodec.key(vk, true));
    send(InputCodec.key(vk, false));
    if (heldRef.current.size) {
      for (const m of heldRef.current) send(InputCodec.key(m, false));
      setHeld(new Set());
    }
  };

  return (
    <div className="vkb" onPointerDown={(e) => e.stopPropagation()}>
      <div className="vkb-layers" aria-label="Lapisan keyboard">{([['abc','ABC'],['numbers','123 / simbol'],['fn','F1–F24'],['full','Lengkap']] as const).map(([value,label])=><button type="button" key={value} aria-pressed={layer===value} onClick={()=>setLayer(value)}>{label}</button>)}</div>
      <div className={`vkb-keys ${layer==='full'?'full-layout':'touch-layout'}`}>
      {keyboardRows(layer).map((row, i) => {
        // Huruf mengikuti mode seperti keyboard fisik: Caps XOR Shift =
        // huruf besar; selain itu kecil. Simbol tidak berubah.
        const shiftHeld = physicalShift || held.has(0xa0) || held.has(0xa1);
        const upper = caps !== shiftHeld;
        return (
        <div className="vkb-row" key={i}>
          {row.map(([label, vk, flex = 1, modifier = false], j) => (
            <button
              key={`${label}-${j}`}
              type="button"
              className={`vkb-key${modifier ? ' mod' : ''}${held.has(vk) || (vk === 0x14 && caps) ? ' on' : ''}`}
              style={{ flexGrow: flex, flexBasis: 0 }}
              onPointerDown={e=>{if(e.button!==0)return;e.preventDefault();e.stopPropagation();tap(vk,modifier);}}
              onClick={e=>{if(e.detail===0)tap(vk,modifier);}}
              onContextMenu={e=>e.preventDefault()}
            >
              {vk === 0x14 ? (caps ? 'CAPS' : 'Caps')
                : vk >= 0x41 && vk <= 0x5a ? (upper ? String.fromCharCode(vk) : String.fromCharCode(vk + 32))
                : label}
            </button>
          ))}
        </div>
        );
      })}
      </div>
    </div>
  );
}

// ── Panel gaming dua sisi ──────────────────────────────────────
function HoldKey({
  vk,
  label,
  send,
  wide = false,
}: {
  vk: number;
  label: string;
  send: Send;
  wide?: boolean;
}) {
  const [down, setDown] = useState(false);
  const press = (isDown: boolean) => {
    if (isDown === down) return;
    setDown(isDown);
    send(InputCodec.key(vk, isDown));
  };
  return (
    <button
      type="button"
      className={`gp-key${wide ? ' wide' : ''}${down ? ' down' : ''}`}
      onPointerDown={(e) => {
        e.stopPropagation();
        e.currentTarget.setPointerCapture?.(e.pointerId);
        press(true);
      }}
      onPointerUp={() => press(false)}
      onPointerCancel={() => press(false)}
      onContextMenu={(e) => e.preventDefault()}
    >
      {label}
    </button>
  );
}

export function GamingPad({ send }: { send: Send }) {
  return (
    <>
      <div className="gp-cluster gp-left" onPointerDown={(e) => e.stopPropagation()}>
        <div className="gp-row"><HoldKey vk={0x57} label="W" send={send} /></div>
        <div className="gp-row">
          <HoldKey vk={0x41} label="A" send={send} />
          <HoldKey vk={0x53} label="S" send={send} />
          <HoldKey vk={0x44} label="D" send={send} />
        </div>
        <div className="gp-row">
          <HoldKey vk={0xa0} label="Shift" send={send} wide />
          <HoldKey vk={0xa2} label="Ctrl" send={send} wide />
        </div>
      </div>
      <div className="gp-cluster gp-right" onPointerDown={(e) => e.stopPropagation()}>
        <div className="gp-row">
          <HoldKey vk={0x1b} label="Esc" send={send} wide />
          <HoldKey vk={0x0d} label="Enter" send={send} wide />
        </div>
        <div className="gp-row">
          <HoldKey vk={0x51} label="Q" send={send} />
          <HoldKey vk={0x45} label="E" send={send} />
          <HoldKey vk={0x52} label="R" send={send} />
          <HoldKey vk={0x46} label="F" send={send} />
        </div>
        <div className="gp-row">
          <HoldKey vk={0x20} label="Spasi" send={send} wide />
        </div>
      </div>
    </>
  );
}

// ── Panel pengaturan sesi: empat tab ala aplikasi ──────────────
export type StreamQuality = 'auto' | 'medium' | 'high' | 'ultra';
export type BitrateMbps = 0 | 1 | 2 | 4 | 8 | 15 | 25 | 50;

export type ResolutionMode = '720p'|'1080p';
export const RESOLUTION_OPTIONS: ReadonlyArray<{value: ResolutionMode; label: string; hint: string}> = [
  {value:'720p', label:'720p', hint:'HD minimum — tidak di bawah 720p'},
  {value:'1080p', label:'1080p', hint:'Full HD — detail lebih tajam bila host/browser mendukung'},
];
export function normalizeResolution(value: unknown): ResolutionMode {
  // Migrasi native/480p dari preferensi lama ke Full HD; UI tidak menawarkan
  // target di bawah HD. Mode 720p tetap mengirim canvas HD 1280×720.
  return value === '1080p' ? '1080p' : '720p';
}

export type SessionPrefs = {
  volume: number;
  sens: number;
  cursorSize: number;
  cursorInVideo: boolean;
  tapClick: boolean;
  reverseScroll: boolean;
  resolution: ResolutionMode;
  quality: StreamQuality;
  bitrateMbps: BitrateMbps;
  fps:30|60;
};

export const QUALITY_META: Record<StreamQuality, { label: string; desc: string; bitrate: BitrateMbps; num: number }> = {
  auto:   { label: 'Otomatis', desc: 'Bitrate menyesuaikan kehilangan paket dan antrean jaringan', bitrate: 0,  num: 0 },
  medium: { label: 'Sedang',   desc: 'Target 8 Mbps • seimbang',    bitrate: 8,  num: 1 },
  high:   { label: 'Tinggi',   desc: 'Target 15 Mbps • detail lebih tinggi',      bitrate: 15, num: 2 },
  ultra:  { label: 'Sangat tinggi',    desc: 'Target 25 Mbps • bandwidth tinggi',    bitrate: 25, num: 3 },
};

export const BITRATE_OPTIONS: { value: BitrateMbps; label: string; hint: string }[] = [
  { value: 0,  label: 'Otomatis', hint: 'Adaptif berdasarkan kondisi jaringan' },
  { value: 1, label: '1 Mbps', hint: 'Koneksi terbatas; detail lebih rendah' },
  { value: 2, label: '2 Mbps', hint: 'Butuh ruang tambahan untuk audio dan transport' },
  { value: 4, label: '4 Mbps', hint: 'Seimbang untuk HD' },
  { value: 8, label: '8 Mbps', hint: 'Detail tinggi' },
  { value: 15, label: '15 Mbps',  hint: 'Seimbang' },
  { value: 25, label: '25 Mbps',  hint: 'Tajam' },
  { value: 50, label: '50 Mbps',  hint: 'Maksimal' },
];

export const DEFAULT_PREFS: SessionPrefs = {
  volume: 0.8,
  sens: 1.7,
  cursorSize: 36,
  cursorInVideo: false,
  tapClick: true,
  reverseScroll: false,
  resolution: '720p',
  quality: 'auto',
  bitrateMbps: 0,
  fps:30,
};

function ToggleRow({
  label,
  hint,
  on,
  onToggle,
}: {
  label: string;
  hint?: string;
  on: boolean;
  onToggle: () => void;
}) {
  return (
    <div className="spanel-row">
      <div className="spanel-copy">
        <span>{label}</span>
        {hint && <small>{hint}</small>}
      </div>
      <button
        type="button"
        role="switch"
        aria-label={label}
        aria-checked={on}
        className={`spanel-switch${on ? ' on' : ''}`}
        onClick={onToggle}
      >
        <i />
      </button>
    </div>
  );
}

function StatRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="stat-line">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

export function transportLabel(stats:Pick<SessionStats,'transportPath'|'transportProtocol'>):string {
 const protocol=stats.transportProtocol?` · ${stats.transportProtocol}`:'';
 return stats.transportPath==='turn-relay'?`TURN relay${protocol}`:stats.transportPath==='direct-p2p'?`Langsung (P2P)${protocol}`:'Jalur belum terukur';
}
export function StatisticsPanel({stats,onClose}:{stats:SessionStats|null;onClose?:()=>void}){
 return <aside className={`statistics-panel${onClose?'':' statistics-inline'}`} aria-label="Statistik koneksi" onPointerDown={e=>e.stopPropagation()} onWheel={e=>e.stopPropagation()}>
  <header><strong>Statistik koneksi</strong>{onClose&&<button type="button" aria-label="Tutup statistik" onClick={onClose}>×</button>}</header>
  {stats?<><div className="statistics-grid"><StatRow label="FPS" value={stats.fps?String(Math.round(stats.fps)):'—'}/><StatRow label="RTT" value={stats.rttMs?`${Math.round(stats.rttMs)} ms`:'—'}/><StatRow label="Video" value={`${stats.mbps.toFixed(1)} Mbps`}/><StatRow label="Resolusi" value={stats.width?`${stats.width}×${stats.height}`:'—'}/></div>
   <p className="statistics-path">{transportLabel(stats)}</p>
   {stats.noFrameWarning&&<p role="status">Frame video sedang tersendat.</p>}
   <details><summary>Detail jaringan</summary><StatRow label="Loss interval" value={stats.recentLossPct===undefined?'—':`${stats.recentLossPct.toFixed(1)}%`}/><StatRow label="Buffer video" value={stats.jitterBufferMs===undefined?'—':`${Math.round(stats.jitterBufferMs)} ms`}/><p>Langsung berarti ICE memilih koneksi P2P. TURN relay hanya tampil saat salah satu kandidat terpilih bertipe relay. Beda jaringan tetap bisa langsung. RTT bukan latensi layar-ke-layar.</p></details>
  </>:<p>Menunggu statistik koneksi.</p>}
 </aside>;
}

export type PanelTab = 'gambar' | 'suara' | 'kontrol' | 'statistik' | 'sesi';

const PANEL_TABS: [PanelTab, string, React.ReactNode][] = [
  ['gambar', 'Video', <Svg key="g"><rect x="2" y="3" width="20" height="14" rx="2" /><path d="M8 21h8M12 17v4" /></Svg>],
  ['kontrol', 'Kontrol', <IcGamepad key="k" />],
  ['suara', 'Audio', <IcVolume key="v" />],
  ['statistik', 'Statistik', <Svg key="stats"><path d="M5 20V10M12 20V4M19 20v-7"/></Svg>],
  ['sesi', 'Sesi', <Svg key="s"><circle cx="12" cy="12" r="9" /><path d="M12 8h.01M11 12h1v4h1" /></Svg>],
];

export function useElapsedSec(from: number | null) {
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    if (from === null) return;
    const t = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(t);
  }, [from]);
  if (from === null) return null;
  return Math.max(0, Math.floor((now - from) / 1000));
}

export function fmtDurasi(totalDetik: number): string {
  const s = Math.max(0, Math.floor(totalDetik));
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const ss = s % 60;
  const pad = (n: number) => String(n).padStart(2, '0');
  return h > 0 ? `${h}:${pad(m)}:${pad(ss)}` : `${m}:${pad(ss)}`;
}

export function SessionPanel({
  activeTab, onTabChange, controlsVisible, onControlsVisibilityChange, audioOn, onAudio, micOn, onMic,
  prefs,
  onChange,
  onClose,
  hostId,
  onDisconnect,
  stats,
  displays,
  wantedDisplay,
  onSelectDisplay,
  connectedAt,
  railCollapsed,
  totalSesiDetik,
  trackpad,
  onTrackpadMode,
  onQuality,
  onResolution,
  onBitrate,
  onFps,
  fpsLimit,
  encoder,
}: {
  controlsVisible?:boolean;
  onControlsVisibilityChange?:(on:boolean)=>void;
  audioOn?:boolean; onAudio?:()=>void; micOn?:boolean; onMic?:()=>void;
  activeTab?:PanelTab;
  onTabChange?:(tab:PanelTab)=>void;
  onFps?:(fps:30|60)=>void;
  fpsLimit?:number;
  encoder?:string;
  videoApplied?: [number, number] | null;
  capture?: HostMeta['capture'];
  prefs: SessionPrefs;
  onChange: (next: SessionPrefs) => void;
  onClose: () => void;
  hostId: string;
  onDisconnect: () => void;
  stats: SessionStats | null;
  displays: { index: number; name?: string; width: number; height: number }[];
  wantedDisplay: number;
  desktopMode?: HostMeta['desktopMode'];
  onSelectDisplay: (index: number) => void;
  connectedAt: number | null;
  railCollapsed: boolean;
  totalSesiDetik: number | null;
  trackpad: boolean;
  onTrackpadMode: (on: boolean) => void;
  onQuality?: (q: StreamQuality) => void;
  onResolution?: (resolution: SessionPrefs['resolution']) => void;
  onBitrate?: (mbps: BitrateMbps) => void;
}) {
  const [localTab, setLocalTab] = useState<PanelTab>('gambar');
  const tab=activeTab??localTab;
  const setTab=(next:PanelTab)=>{setLocalTab(next);onTabChange?.(next);};
  const tabsRef=useRef<HTMLDivElement>(null);
  const drawerRef=useRef<HTMLElement>(null);
  useEffect(()=>{const previous=document.activeElement;drawerRef.current?.querySelector<HTMLButtonElement>('.spanel-close')?.focus({preventScroll:true});return()=>{if(previous instanceof HTMLElement&&previous.isConnected)previous.focus({preventScroll:true});};},[]);
  const elapsed = useElapsedSec(connectedAt);

  return (
    <aside
      className={`spanel session-settings${railCollapsed ? ' no-rail' : ''}`}
      ref={drawerRef}
      aria-label="Pengaturan sesi"
      onKeyDown={e=>{e.stopPropagation();if(e.key==='Escape'){e.preventDefault();onClose();}}}
      onPointerDown={(e) => e.stopPropagation()}
      onWheel={(e) => e.stopPropagation()}
    >
      <header className="spanel-head">
        <strong>Pengaturan sesi</strong>
        <button type="button" className="spanel-close" onClick={onClose} title="Tutup" aria-label="Tutup pengaturan sesi">
          ✕
        </button>
      </header>

      <div ref={tabsRef} className="spanel-tabs" role="tablist" aria-label="Kategori pengaturan" onKeyDown={e=>{
        const index=PANEL_TABS.findIndex(([id])=>id===tab);
        const next=e.key==='ArrowRight'?(index+1)%PANEL_TABS.length:e.key==='ArrowLeft'?(index+PANEL_TABS.length-1)%PANEL_TABS.length:e.key==='Home'?0:e.key==='End'?PANEL_TABS.length-1:-1;
        if(next>=0){e.preventDefault();e.stopPropagation();setTab(PANEL_TABS[next][0]);tabsRef.current?.querySelectorAll<HTMLButtonElement>('[role=tab]')[next]?.focus();}
      }}>
        {PANEL_TABS.map(([id, label, icon]) => (
          <button
            key={id}
            type="button"
            role="tab"
            id={`session-tab-${id}`}
            aria-controls="session-settings-body"
            tabIndex={tab===id?0:-1}
            aria-selected={tab === id}
            className={`spanel-tab${tab === id ? ' on' : ''}`}
            onClick={() => setTab(id)}
          >
            {icon}
            <span>{label}</span>
          </button>
        ))}
      </div>

      <div key={tab} id="session-settings-body" className="spanel-body" role="tabpanel" aria-labelledby={`session-tab-${tab}`} tabIndex={0}>
      {tab === 'statistik' && <StatisticsPanel stats={stats}/>}
      {tab === 'gambar' && (
        <>
          <p className="spanel-section">Kualitas gambar</p>
          <div className="display-chips quality-chips">
            {(Object.keys(QUALITY_META) as StreamQuality[]).map((q) => {
              const meta = QUALITY_META[q];
              return (
                <button
                  key={q}
                  type="button"
                  className={prefs.quality === q ? 'active' : ''}
                  title={meta.desc}
                  onClick={() => {
                    const next = { ...prefs, quality: q, bitrateMbps: meta.bitrate } as SessionPrefs;
                    onChange(next);
                    onQuality?.(q);
                    // quality preset also sets bitrate to its default, but user can override afterwards
                    if (meta.bitrate !== prefs.bitrateMbps) {
                      onBitrate?.(meta.bitrate);
                    }
                  }}
                >
                  {meta.label}
                </button>
              );
            })}
          </div>
          <p className="spanel-note">{QUALITY_META[prefs.quality].desc}</p>

          <p className="spanel-section">Frame per detik</p>
          <div className="display-chips">{([30,60] as const).map(fps=><button type="button" key={fps} className={prefs.fps===fps?'active':''} onClick={()=>{onChange({...prefs,fps});onFps?.(fps);}}>{fps} FPS</button>)}</div>
          <p className="spanel-note">Encoder host: {encoder??'belum diketahui'} • batas host {fpsLimit??'belum tersedia'} FPS. FPS nyata terlihat pada statistik; mengikuti encoder, negosiasi H264, dan jaringan. Resolusi desktop tidak diubah oleh pilihan FPS.</p>
          <p className="spanel-section">Resolusi maksimal</p>
          <div className="display-chips">{RESOLUTION_OPTIONS.map(option=><button key={option.value} type="button" title={option.hint} className={(prefs.resolution||'1080p')===option.value?'active':''} onClick={()=>{onChange({...prefs,resolution:option.value});onResolution?.(option.value);}}>{option.label}</button>)}</div>
          <p className="spanel-note">Mode 720p menjaga output HD 1280×720, termasuk saat desktop RDP lebih kecil; sumber akan diskalakan halus agar tidak berhenti di tinggi 529. Mode 1080p memakai ukuran yang tersedia tanpa mengarang detail.</p>
          <p className="spanel-section">Bitrate</p>
          <div className="display-chips bitrate-chips">
            {BITRATE_OPTIONS.map((opt) => (
              <button
                key={opt.value}
                type="button"
                className={prefs.bitrateMbps === opt.value ? 'active' : ''}
                title={opt.hint}
                onClick={() => {
                  const next = { ...prefs, bitrateMbps: opt.value } as SessionPrefs;
                  // If user picks manual bitrate, keep quality as is but if they pick auto bitrate, also set quality auto? Keep independent.
                  onChange(next);
                  onBitrate?.(opt.value);
                }}
              >
                {opt.label}
              </button>
            ))}
          </div>
          <p className="spanel-note">Bitrate adalah target, bukan pemakaian tetap. Resolusi, fps, dan bitrate efektif mengikuti batas encoder host. Mode 720p mengirim kanvas HD; mode 1080p dipakai bila capture dan encoder mendukung. RTT bukan latensi layar-ke-layar.</p>
          {stats?.relayState === 'unavailable' && (
            <p className="spanel-note" role="status">
              Relay TURN tidak tersedia — {relayReasonText(stats.relayReason)}.
              {' '}Sesi tetap bisa tersambung lewat jalur langsung; kalau koneksi tidak pernah jadi, inilah sebab pertama yang perlu diperiksa.
              {stats.relayHint ? ` ${stats.relayHint}` : ''}
            </p>
          )}

          <p className="spanel-note">Statistik aktual tersedia melalui ikon grafik di rail, terpisah dari pengaturan ini.</p>
          {displays.length > 1 && (
            <>
              <p className="spanel-section">Layar PC</p>
              <div className="display-chips">
                {displays.map((d) => (
                  <button
                    key={d.index}
                    type="button"
                    className={d.index === wantedDisplay ? 'active' : ''}
                    onClick={() => onSelectDisplay(d.index)}
                  >
                    {d.name || `Layar ${d.index + 1}`} · {d.width}×{d.height}
                  </button>
                ))}
              </div>
            </>
          )}
        </>
      )}

      {tab === 'suara' && (
        <>
          {onAudio&&<ToggleRow label="Suara PC" hint="Dengarkan audio dari host di perangkat ini." on={!!audioOn} onToggle={onAudio}/>}
          {onMic&&<ToggleRow label="Mikrofon ke PC" hint="Izin mikrofon mengikuti pengaturan browser." on={!!micOn} onToggle={onMic}/>}

          <p className="spanel-section">Status suara</p>
          <div className="spanel-card">
            <StatRow label="Pemutar suara" value={stats?.audioPlayerState || 'Menunggu statistik'} />
            <StatRow label="Byte audio diterima" value={stats?.audioBytesReceived?.toLocaleString('id-ID') ?? '—'} />
            <StatRow label="Energi audio decode" value={stats?.audioEnergy === undefined ? 'Tidak dilaporkan browser' : String(stats.audioEnergy)} />
          </div>
          <p className="spanel-note">Byte audio bisa berupa keheningan. Putar suara di PC dan dengarkan di HP untuk menguji keluaran sebenarnya.</p>
          <p className="spanel-section">Volume</p>
          <div className="spanel-row">
            <div className="spanel-copy">
              <span>Volume audio PC</span>
              <small>{Math.round(prefs.volume * 100)}%</small>
            </div>
          </div>
          <input
            type="range"
            min={0}
            max={100}
            value={Math.round(prefs.volume * 100)}
            onChange={(e) => onChange({ ...prefs, volume: Number(e.target.value) / 100 })}
          />
          <p className="spanel-note">Volume berlaku di perangkat ini; pembicara di sekitar kamu tetap tidak terdengar PC.</p>
        </>
      )}

      {tab === 'kontrol' && (
        <>
          {onControlsVisibilityChange&&<ToggleRow label="Tampilkan kontrol layar" hint="Tombol mouse, keyboard dan stick; terpisah dari rail pengaturan." on={!!controlsVisible} onToggle={()=>onControlsVisibilityChange(!controlsVisible)}/>}

          <p className="spanel-section">Penunjuk mouse</p>
          <p className="spanel-note">Kursor Windows asli dikirim dalam video oleh host terbaru. Tidak ada panah lokal pengganti. Bentuk dan geraknya mengikuti desktop host.</p>
          <p className="spanel-section">Gerak kursor</p>
          <div className="spanel-seg">
            <button
              type="button"
              className={!trackpad ? 'on' : ''}
              onClick={() => onTrackpadMode(false)}
            >
              Langsung
            </button>
            <button
              type="button"
              className={trackpad ? 'on' : ''}
              onClick={() => onTrackpadMode(true)}
            >
              Trackpad
            </button>
          </div>
          <p className="spanel-note">
            Trackpad: geser satu jari untuk menggerakkan kursor, ketuk untuk klik kiri, tahan diam 0,5 detik untuk klik kanan, dua jari untuk scroll. Tahan tombol klik kiri sambil geser untuk drag. Langsung: sentuh tepat pada gambar. Kursor yang terlihat adalah kursor Windows dalam video, termasuk saat mouse digerakkan di PC.
          </p>
          <div className="spanel-row">
            <div className="spanel-copy">
              <span>Sensitivitas trackpad</span>
              <small>{prefs.sens.toFixed(1).replace('.', ',')}×</small>
            </div>
          </div>
          <input
            type="range"
            min={2}
            max={40}
            value={Math.round(prefs.sens * 10)}
            onChange={(e) => onChange({ ...prefs, sens: Number(e.target.value) / 10 })}
          />
          <ToggleRow
            label="Ketuk untuk klik"
            hint="Ketukan singkat tanpa geser = klik kiri"
            on={prefs.tapClick}
            onToggle={() => onChange({ ...prefs, tapClick: !prefs.tapClick })}
          />
          <ToggleRow
            label="Scroll terbalik"
            hint="Balik arah scroll dua jari"
            on={prefs.reverseScroll}
            onToggle={() => onChange({ ...prefs, reverseScroll: !prefs.reverseScroll })}
          />
        </>
      )}

      {tab === 'sesi' && (
        <>
          <p className="spanel-section">Sesi</p>
          <div className="spanel-card">
            <StatRow label="Terhubung ke" value={hostId} />
            <StatRow label="Durasi" value={elapsed !== null ? fmtDurasi(elapsed) : '—'} />
            <StatRow label="Total" value={totalSesiDetik !== null ? fmtDurasi(totalSesiDetik) : 'Bebas'} />
            <StatRow
              label="Sisa waktu"
              value={
                totalSesiDetik !== null && elapsed !== null
                  ? fmtDurasi(Math.max(0, totalSesiDetik - elapsed))
                  : '—'
              }
            />
          </div>
          <button type="button" className="spanel-disconnect" onClick={onDisconnect}>
            Putuskan sesi
          </button>
        </>
      )}
      </div>
    </aside>
  );
}

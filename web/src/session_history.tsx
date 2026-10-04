import {useEffect, useState, useRef} from 'react';
import {API_BASE} from './api';
import {MAX_PREVIEW_URL} from './wallpaper_transfer';
export type HistoryState = 'ended'|'interrupted'|'failed'|'cancelled';
export type HistoryItem = {id:string;deviceId:string;name:string;startedAt:number;endedAt:number;state:HistoryState;specs:Record<string,string>;preview:string|null;previewConsent?:boolean};
const LOCAL_KEY='xydesk.guest.history.v1';
const TOKEN_KEY='xydesk.web.jwt';
export function accountHistoryToken(){return localStorage.getItem(TOKEN_KEY);}
export function loadGuestHistory():HistoryItem[]{
 try{const rows=JSON.parse(localStorage.getItem(LOCAL_KEY)||'[]');return Array.isArray(rows)?rows.filter(x=>x&&typeof x.id==='string'&&/^\d{9}$/.test(x.deviceId)).slice(0,20).map(x=>({...x,preview:typeof x.preview==='string'&&x.preview.length<=MAX_PREVIEW_URL&&/^data:image\/jpeg;base64,[A-Za-z0-9+/]+=*$/.test(x.preview)?x.preview:null})):[];}catch{return [];}
}
async function serverHistory(token:string, body?:object){
 const r=await fetch(`${API_BASE}/auth/session-history`,{method:body?'POST':'GET',headers:{Authorization:`Bearer ${token}`,...(body?{'Content-Type':'application/json'}:{})},body:body?JSON.stringify(body):undefined,keepalive:!!body && JSON.stringify(body).length<48000,cache:'no-store'});
 if(!r.ok)throw Error(r.status===401?'Sesi akun berakhir. Masuk lagi untuk membuka riwayat.':'Riwayat server belum dapat diakses. Coba lagi.');
 return r.json();
}
export function deviceCards(rows:HistoryItem[]):HistoryItem[]{
 const cards=new Map<string,HistoryItem>();
 for(const row of [...rows].sort((a,b)=>b.endedAt-a.endedAt)){
  const old=cards.get(row.deviceId);
  if(!old)cards.set(row.deviceId,{...row});else if(!old.preview&&row.preview)old.preview=row.preview;
 }
 return [...cards.values()];
}
export async function saveSessionHistory(item:HistoryItem, accountToken:string|null){
 if(accountToken){await serverHistory(accountToken,{action:'save',record:item});return;}
 const previous=loadGuestHistory();
 const preview=item.preview || deviceCards(previous).find(x=>x.deviceId===item.deviceId)?.preview || null;
 const rows=[{...item,preview},...previous.filter(x=>x.id!==item.id)].slice(0,20);
 const previews=new Set<string>();for(const row of rows){if(previews.has(row.deviceId))row.preview=null;else if(row.preview)previews.add(row.deviceId);}
 for(let i=rows.length-1;;i--){try{localStorage.setItem(LOCAL_KEY,JSON.stringify(rows));return;}catch{if(i<=0)throw Error('Penyimpanan browser penuh; preview baru belum tersimpan.');rows[i].preview=null;}}
}
const status:Record<HistoryState,string>={ended:'Sesi selesai',interrupted:'Koneksi terputus',failed:'Gagal terhubung',cancelled:'Dibatalkan'};
export function SessionHistoryPage({view='history',deviceId}:{view?:'devices'|'history'|'detail';deviceId?:string}={}) {
 const [token,setToken]=useState(accountHistoryToken),[items,setItems]=useState<HistoryItem[]>([]),[error,setError]=useState(''),[loading,setLoading]=useState(true),[refresh,setRefresh]=useState(0);
 const [deleteTarget,setDeleteTarget]=useState<string|null|undefined>(undefined);
 const dialog=useRef<HTMLDialogElement|null>(null);
 useEffect(()=>{if(deleteTarget!==undefined)dialog.current?.showModal();},[deleteTarget]);
 useEffect(()=>{const changed=(e:Event)=>{if(e instanceof StorageEvent&&e.key!==TOKEN_KEY&&e.key!==null)return;setItems([]);setToken(accountHistoryToken());};window.addEventListener('storage',changed);window.addEventListener('xydesk-account-changed',changed);return()=>{window.removeEventListener('storage',changed);window.removeEventListener('xydesk-account-changed',changed);};},[]);
 useEffect(()=>{let active=true;setItems([]);setLoading(true);setError('');const task=token?serverHistory(token).then(x=>x.items):Promise.resolve(loadGuestHistory());task.then(rows=>{if(active)setItems(Array.isArray(rows)?rows:[]);}).catch(e=>{if(active)setError(e.message);}).finally(()=>{if(active)setLoading(false);});return()=>{active=false;};},[token,refresh]);
 const cards=deviceCards(items),selected=cards.find(item=>item.deviceId===deviceId);
 const remove=async(id?:string)=>{setDeleteTarget(undefined);try{if(token)await serverHistory(token,id?{action:'delete-device',deviceId:id}:{action:'clear'});else localStorage.setItem(LOCAL_KEY,JSON.stringify(id?loadGuestHistory().filter(x=>x.deviceId!==id):[]));setRefresh(x=>x+1);}catch(e){setError(e instanceof Error?e.message:'Gagal menghapus riwayat.');}};
 const title=view==='devices'?'Perangkatmu':view==='history'?'Riwayat sesi':selected?.name||`Perangkat ${deviceId}`;
 const preview=(item:HistoryItem)=><div className="history-banner">{item.preview?<img src={item.preview} alt={`Wallpaper tersimpan ${item.name}`} loading="lazy"/>:<span className="history-no-preview">Belum ada wallpaper</span>}</div>;
 return <main className="history-page workspace-page">
 <header className="history-heading"><div><p className="eyebrow">XYDESK / {view==='history'?'RIWAYAT':view==='detail'?'DETAIL PERANGKAT':'PERANGKAT'}</p><h1>{title}</h1><p>{view==='devices'?'Pilih perangkat untuk melihat detail sebelum terhubung.':view==='history'?'Setiap koneksi punya catatan tersendiri, tanpa menyimpan password.':'Spesifikasi dan wallpaper terakhir, bukan status online saat ini.'}</p></div><a className="btn primary" href="/connect">Hubungkan perangkat baru</a></header>
 <div className="workspace-summary"><span>{cards.length} perangkat</span><span>{items.length} sesi terakhir</span><span>{token?'Tersimpan pada akun':'Tamu · browser ini'}</span></div>
 <div className="history-actions"><button className="btn ghost" onClick={()=>setRefresh(x=>x+1)}>Muat ulang</button>{view!=='detail'&&<button className="btn ghost" disabled={!items.length} onClick={()=>setDeleteTarget(null)}>Hapus riwayat</button>}{view==='detail'&&<a className="btn ghost" href="/devices">Kembali ke perangkat</a>}</div>
 {error&&<p role="alert">{error}</p>}{loading&&<p role="status">Memuat perangkat…</p>}
 {!loading&&!error&&view==='devices'&&<div className="history-grid">{cards.map(item=><article className="history-card" key={item.deviceId}><a className="device-route-card" href={`/devices/${item.deviceId}`}>{preview(item)}<div className="device-card-body"><h2>{item.name}</h2><p>ID {item.deviceId}</p><small>Terakhir {new Date(item.endedAt).toLocaleString('id-ID')}</small><span className="device-detail-link">Lihat perangkat →</span></div></a></article>)}</div>}
 {!loading&&!error&&view==='history'&&<div className="session-ledger">{[...items].sort((a,b)=>b.endedAt-a.endedAt).map(item=><a key={item.id} className="session-ledger-row" href={`/devices/${item.deviceId}`}><div><strong>{item.name}</strong><small>ID {item.deviceId}</small></div><span>{status[item.state]||'Sesi terakhir'}</span><span>{new Date(item.endedAt).toLocaleString('id-ID')}</span><span>{Math.max(0,Math.round((item.endedAt-item.startedAt)/1000))} detik</span><span aria-hidden="true">→</span></a>)}</div>}
 {!loading&&view==='detail'&&<section className="device-detail-page"><div className="device-preview-large">{selected?preview(selected):<div className="history-no-preview">Belum ada preview perangkat ini</div>}</div><div className="device-detail-content"><p className="eyebrow">TUJUAN REMOTE</p><h2>{selected?.name||'PC Windows'}</h2><p className="device-id">{deviceId}</p><p>Izin tersimpan dicoba saat membuka sesi. Jika belum ada atau dicabut, password diminta di halaman sesi.</p><a className="btn primary" href={`/session/${deviceId}`}>Buka sesi perangkat</a><h3>Spesifikasi terakhir</h3>{Object.keys(selected?.specs||{}).length?<dl>{Object.entries(selected!.specs).map(([key,value])=><div key={key}><dt>{key.toUpperCase()}</dt><dd>{String(value)}</dd></div>)}</dl>:<p>Belum ada spesifikasi tersimpan. Host mengirimnya setelah tersambung.</p>}{selected&&<button className="text-action danger" onClick={()=>setDeleteTarget(deviceId)}>Hapus perangkat dari riwayat</button>}</div></section>}
 {!loading&&!items.length&&!error&&view!=='detail'&&<div className="history-empty"><img className="history-empty-art" src="/float-pc-sleep.webp" alt="" aria-hidden="true"/><h2>Belum ada {view==='devices'?'perangkat':'sesi'} tersimpan</h2><p>Mulai koneksi dengan ID dan password dari panel host. Perangkat tampil di sini setelah sesi tercatat.</p><a className="btn primary" href="/connect">Mulai koneksi pertama</a></div>}
 {deleteTarget!==undefined&&<dialog className="history-confirm" ref={dialog} onCancel={()=>setDeleteTarget(undefined)} aria-label="Hapus riwayat"><h2>Hapus {deleteTarget?'perangkat ini':'semua riwayat'}?</h2><p>Preview dihapus, tetapi izin reconnect tidak dicabut. Cabut izin dari host jika perangkat tidak lagi dipercaya.</p><button className="btn ghost" onClick={()=>setDeleteTarget(undefined)}>Batal</button><button className="btn primary" onClick={()=>void remove(deleteTarget??undefined)}>Hapus</button></dialog>}
 </main>;
}

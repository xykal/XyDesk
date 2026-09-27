import {MappingPicker} from './mapping_picker';
import {canonicalKey} from './remote_pointer';
import {useEffect,useRef,useState} from 'react';
import {InputCodec} from './rtc';
import {enterSessionFullscreen,leaveSessionFullscreen} from './session_fullscreen';
export type Mapping={id:string;label:string;kind:'key'|'mouse'|'scroll'|'scrollX'|'chord'|'toggle'|'stickKeys'|'stickMouse';name?:string;displayLabel?:string;code:number;keys?:number[];x:number;y:number;size:number;radius?:number};
const KEY='xydesk.mapping.v1';
const clamp=(n:number,min:number,max:number)=>Math.min(max,Math.max(min,n));
export function normalizeMappings(raw:unknown):Mapping[]{
 if(!Array.isArray(raw))return [];
 // Default radius = setengah ukuran (bulat penuh); layout tersimpan dengan radius sendiri dipertahankan.
 const ids=new Set<string>();return raw.slice(0,24).filter(x=>x&&typeof x.id==='string'&&x.id.length<=50&&!ids.has(x.id)&&ids.add(x.id)&&['key','mouse','scroll','scrollX','chord','toggle','stickKeys','stickMouse'].includes(x.kind)&&Number.isFinite(x.code)).map(x=>{const size=clamp(Number(x.size)||56,36,160);return {id:x.id.slice(0,50),label:'',kind:x.kind,name:typeof x.name==='string'?x.name.slice(0,48):'',displayLabel:(x.kind==='key'||x.kind==='chord')&&typeof x.displayLabel==='string'?x.displayLabel.slice(0,24):'',keys:x.kind==='chord'?chordKeys(x.keys):x.kind==='stickKeys'?normalizeStickKeys(x.keys):undefined,code:clamp(Math.round(x.code),x.kind.startsWith('scroll')?-120:0,x.kind==='key'?255:x.kind==='mouse'?4:120),x:clamp(Number(x.x)||0,0,100),y:clamp(Number(x.y)||0,0,100),size,radius:clamp(Number.isFinite(x.radius)?x.radius:Math.round(size/2),0,80)};}).map(x=>({...x,label:mappingLabel(x)}));
}
export function chordKeys(raw:unknown):number[]{
 if(!Array.isArray(raw))return [];
 const modifier=(n:number)=>[16,17,18,91,92,160,161,162,163,164,165].includes(n);
 return [...new Set(raw.filter(n=>Number.isInteger(n)&&n>0&&n<=255))].slice(0,6).sort((a,b)=>Number(modifier(b))-Number(modifier(a)));
}
export const KEY_OPTIONS:(number|string)[][]=[
 ...Array.from({length:26},(_,i)=>[65+i,String.fromCharCode(65+i)]),
 ...Array.from({length:10},(_,i)=>[48+i,String(i)]),
 ...Array.from({length:24},(_,i)=>[112+i,`F${i+1}`]),
 [160,'Shift kiri'],[161,'Shift kanan'],[162,'Ctrl kiri'],[163,'Ctrl kanan'],[164,'Alt kiri'],[165,'Alt kanan'],[92,'Windows kanan'],[93,'Menu'],[173,'Mute'],[174,'Volume turun'],[175,'Volume naik'],[176,'Media berikutnya'],[177,'Media sebelumnya'],[178,'Media stop'],[179,'Media play/pause'],
 [32,'Spasi'],[13,'Enter'],[27,'Esc'],[9,'Tab'],[16,'Shift'],[17,'Ctrl'],[18,'Alt'],[91,'Windows'],[8,'Backspace'],[46,'Delete'],[37,'←'],[38,'↑'],[39,'→'],[40,'↓'],
 [36,'Home'],[35,'End'],[33,'Page Up'],[34,'Page Down'],[45,'Insert'],[20,'Caps Lock'],[144,'Num Lock'],[145,'Scroll Lock'],[19,'Pause'],[44,'Print Screen'],
 ...Array.from({length:10},(_,i)=>[96+i,`Numpad ${i}`]),[106,'Numpad ×'],[107,'Numpad +'],[109,'Numpad −'],[110,'Numpad .'],[111,'Numpad ÷'],
 [186,';'],[187,'='],[188,','],[189,'-'],[190,'.'],[191,'/'],[192,'`'],[219,'['],[220,'Backslash'],[221,']'],[222,"'"],
];
export function mappingLabel(m:{kind:string;code:number;keys?:number[]}):string {
 const key=(code:number)=>String(KEY_OPTIONS.find(x=>x[0]===code)?.[1]??`Key ${code}`);
 if(m.kind==='stickKeys')return 'Stick keyboard';
 if(m.kind==='stickMouse')return 'Stick mouse';
 if(m.kind==='key')return key(m.code);
 if(m.kind==='chord')return chordKeys(m.keys).map(key).join(' + ')||'Pilih kombinasi';
 if(m.kind==='toggle')return 'Ganti mode gerak';
 if(m.kind==='mouse')return ['Klik kiri','Klik kanan','Klik tengah','Kembali','Maju'][m.code]||'Mouse';
 return m.kind==='scrollX'?(m.code>0?'Scroll kanan':'Scroll kiri'):(m.code>0?'Scroll atas':'Scroll bawah');
}

const defaults=()=>normalizeMappings([
 {id:'left',kind:'mouse',code:0,x:70,y:68,size:56},
 {id:'right',kind:'mouse',code:1,x:84,y:68,size:56},
 {id:'switch',kind:'toggle',code:0,x:77,y:84,size:52},
 {id:'up',kind:'scroll',code:120,x:16,y:70,size:52},
 {id:'down',kind:'scroll',code:-120,x:16,y:84,size:52},
]);
const fpsDefaults=()=>normalizeMappings([
 {id:'w',label:'W',kind:'key',code:87,x:14,y:44,size:52},{id:'a',label:'A',kind:'key',code:65,x:7,y:60,size:52},{id:'s',label:'S',kind:'key',code:83,x:14,y:60,size:52},{id:'d',label:'D',kind:'key',code:68,x:21,y:60,size:52},
 {id:'shift',label:'Shift',kind:'key',code:16,x:8,y:82,size:56},{id:'ctrl',label:'Ctrl',kind:'key',code:17,x:22,y:82,size:56},{id:'space',label:'Spasi',kind:'key',code:32,x:83,y:78,size:64},
 {id:'e',label:'E',kind:'key',code:69,x:86,y:38,size:48},{id:'r',label:'R',kind:'key',code:82,x:94,y:38,size:48},
 {id:'left',label:'Klik kiri',kind:'mouse',code:0,x:68,y:54,size:56},{id:'right',label:'Klik kanan',kind:'mouse',code:1,x:84,y:54,size:56},
]);
export function withPointerDefaults(items:Mapping[]):Mapping[]{
 try{if(localStorage.getItem('xydesk.mapping.pointer.v1'))return items;localStorage.setItem('xydesk.mapping.pointer.v1','1');}catch{return items;}
 const missing=defaults().filter(d=>(d.kind==='mouse'||d.kind==='scroll')&&!items.some(x=>x.kind===d.kind&&x.code===d.code));
 return missing.length?normalizeMappings([...items,...missing]):items;
}
export class MappingHolds {
 private owners=new Map<string,Mapping>();private counts=new Map<string,number>();
 constructor(private send:(b:Uint8Array)=>void){}
 private actions(m:Mapping):Mapping[]{return (m.kind==='chord'?(m.keys||[]).map(code=>({...m,kind:'key' as const,code})): [m]).map(a=>a.kind==='key'?{...a,code:canonicalKey(a.code)}:a);}
 down(owner:string,m:Mapping){if(this.owners.has(owner)||m.kind==='toggle'||m.kind==='stickKeys'||m.kind==='stickMouse')return;if(m.kind==='scroll'||m.kind==='scrollX'){this.send(InputCodec.scroll(m.kind==='scrollX'?m.code:0,m.kind==='scroll'?m.code:0));return;}this.owners.set(owner,m);for(const a of this.actions(m)){const key=a.kind+':'+a.code,n=this.counts.get(key)||0;this.counts.set(key,n+1);if(n===0)this.send(a.kind==='key'?InputCodec.key(a.code,true):InputCodec.mouseButton(a.code,true));}}
 up(owner:string){const m=this.owners.get(owner);if(!m)return;this.owners.delete(owner);for(const a of this.actions(m).reverse()){const key=a.kind+':'+a.code,n=(this.counts.get(key)||1)-1;if(n){this.counts.set(key,n);}else{this.counts.delete(key);this.send(a.kind==='key'?InputCodec.key(a.code,false):InputCodec.mouseButton(a.code,false));}}}
 reset(){for(const owner of [...this.owners.keys()])this.up(owner);}
}
export function normalizeStickKeys(raw:unknown):number[]{
 const fallback=[87,83,65,68];
 return fallback.map((v,i)=>Array.isArray(raw)&&Number.isInteger(raw[i])&&raw[i]>0&&raw[i]<=255?raw[i]:v);
}
export function stickDirections(x:number,y:number):boolean[]{return [(y < -0.28), (y > 0.28), (x < -0.28), (x > 0.28)];}
export function CustomControlMapping({send,onToggleMode,onEditStart}:{send:(b:Uint8Array)=>void;onToggleMode?:()=>void;onEditStart?:()=>void}){
 const [landscape,setLandscape]=useState(()=>innerWidth>innerHeight),[edit,setEdit]=useState(false);
 const orientation=landscape?'landscape':'portrait';
 const load=(o=orientation)=>{try{const raw=JSON.parse(localStorage.getItem(KEY)||'{}')[o];return Array.isArray(raw)?normalizeMappings(raw):null;}catch{return null;}};
 const [items,setItems]=useState<Mapping[]>(()=>withPointerDefaults(load()??defaults()));
 const [selected,setSelected]=useState(''),[panel,setPanel]=useState<'library'|'properties'|null>(null),[category,setCategory]=useState('keyboard');
 const [side,setSide]=useState<'left'|'right'>(()=>{try{return localStorage.getItem('xydesk.mapping.side')==='left'?'left':'right';}catch{return 'right';}});
 const [notice,setNotice]=useState('');
 const editingWanted=useRef(false);
 const selectedItem=items.find(m=>m.id===selected);
 const anchor=useRef<HTMLDivElement|null>(null),fullscreenOwned=useRef<HTMLElement|null>(null),drafts=useRef<Record<string,Mapping[]>>({}),priorOrientation=useRef(orientation);
 const sendRef=useRef(send);sendRef.current=send;
 const holds=useRef<MappingHolds|null>(null);if(!holds.current)holds.current=new MappingHolds(b=>sendRef.current(b));
 const drag=useRef<{pointer:number;id:string;dx:number;dy:number}|null>(null);
 useEffect(()=>{const media=matchMedia('(orientation: landscape)');const resize=()=>setLandscape(media.matches);media.addEventListener('change',resize);return()=>media.removeEventListener('change',resize);},[]);
 useEffect(()=>{if(priorOrientation.current===orientation)return;drafts.current[priorOrientation.current]=items;priorOrientation.current=orientation;holds.current!.reset();drag.current=null;setItems(drafts.current[orientation]??load()??defaults());setSelected('');setPanel(edit?'library':null);},[orientation]);
 useEffect(()=>{const reset=()=>holds.current!.reset();const hidden=()=>{if(document.hidden)reset();};window.addEventListener('blur',reset);document.addEventListener('visibilitychange',hidden);return()=>{reset();window.removeEventListener('blur',reset);document.removeEventListener('visibilitychange',hidden);};},[]);
 useEffect(()=>{const surface=anchor.current?.closest<HTMLElement>('.video-surface');if(!edit||!surface)return;holds.current!.reset();surface.classList.add('mapping-studio-active');const overflow=document.body.style.overflow;document.body.style.overflow='hidden';return()=>{surface.classList.remove('mapping-studio-active');document.body.style.overflow=overflow;};},[edit]);
 useEffect(()=>()=>{editingWanted.current=false;if(fullscreenOwned.current)void leaveSessionFullscreen(fullscreenOwned.current);},[]);
 useEffect(()=>{if(!edit)return;const surface=anchor.current?.closest<HTMLElement>('.video-surface');if(!surface)return;const previous=document.activeElement as HTMLElement|null;const focusable=()=>Array.from(surface.querySelectorAll<HTMLElement>('button:not(:disabled),input:not(:disabled),select:not(:disabled),summary,[tabindex="0"]')).filter(el=>el.getClientRects().length>0);const frame=requestAnimationFrame(()=>focusable()[0]?.focus());const key=(event:KeyboardEvent)=>{if(event.key!=='Tab')return;const all=focusable();const first=all[0],last=all[all.length-1];if(!first)return;if(event.shiftKey&&(document.activeElement===first||!surface.contains(document.activeElement))){event.preventDefault();last?.focus();}else if(!event.shiftKey&&(document.activeElement===last||!surface.contains(document.activeElement))){event.preventDefault();first.focus();}};document.addEventListener('keydown',key);return()=>{cancelAnimationFrame(frame);document.removeEventListener('keydown',key);if(previous?.isConnected)previous.focus();};},[edit]);
 const startEdit=()=>{onEditStart?.();editingWanted.current=true;holds.current!.reset();setEdit(true);setPanel('library');const surface=anchor.current?.closest<HTMLElement>('.video-surface');if(surface&&document.fullscreenElement!==surface){void enterSessionFullscreen(surface).then(ok=>{if(!editingWanted.current){if(ok)void leaveSessionFullscreen(surface);return;}if(ok)fullscreenOwned.current=surface;else setNotice('Fullscreen perangkat tidak tersedia; editor tetap memenuhi halaman.');});}};
 const finish=()=>{editingWanted.current=false;setEdit(false);setPanel(null);holds.current!.reset();if(fullscreenOwned.current){void leaveSessionFullscreen(fullscreenOwned.current);fullscreenOwned.current=null;}};
 const save=()=>{if(Object.values({...drafts.current,[orientation]:items}).some(layout=>layout.some(m=>m.kind==='chord'&&!m.keys?.length))){setNotice('Pilih minimal satu tombol dalam kombinasi.');return;}try{const raw=JSON.parse(localStorage.getItem(KEY)||'{}');const all=raw&&typeof raw==='object'&&!Array.isArray(raw)?raw:{};Object.assign(all,drafts.current,{[orientation]:items});localStorage.setItem(KEY,JSON.stringify(all));setNotice('Layout disimpan.');drafts.current={};finish();}catch{setNotice('Tidak bisa menyimpan: storage browser tidak tersedia. Layout belum disimpan.');}};
 const update=(patch:Partial<Mapping>)=>setItems(old=>normalizeMappings(old.map(m=>m.id===selected?{...m,...patch}:m)));
 const select=(id:string)=>{setSelected(id);setPanel('properties');};
 const add=(kind:Mapping['kind'],code=69,keys?:number[],displayLabel?:string)=>{if(items.length>=24){setNotice('Maksimal 24 kontrol per orientasi.');return;}const id=crypto.randomUUID();setItems(old=>normalizeMappings([...old,{id,kind,code,keys,displayLabel,x:50,y:55,size:kind.startsWith('stick')?144:64}]));select(id);};
 const position=(m:Mapping)=>({left:`clamp(${m.size/2}px, ${m.x}%, calc(100% - ${m.size/2}px))`,top:`clamp(${m.size/2}px, ${m.y}%, calc(100% - ${m.size/2}px))`,width:m.size,height:m.size,borderRadius:m.radius??m.size/2});
 return <>
 <div ref={anchor} data-editing={edit} className={`mapping-tools${edit?' studio-toolbar':''}`} onPointerDown={e=>e.stopPropagation()}>
 {edit?<><strong>Control Studio <small>{orientation} · {items.length}/24</small></strong><button type="button" onClick={()=>setPanel(panel==='library'?null:'library')}>Tambah kontrol</button><button type="button" onClick={save}>Simpan</button><button type="button" onClick={()=>{if(window.confirm('Buang perubahan yang belum disimpan?')){drafts.current={};setItems(load()??defaults());finish();}}}>Batal</button></>:<button type="button" onClick={startEdit}>Atur kontrol · fullscreen</button>}
 {notice&&<small role="status">{notice}</small>}</div>
 {items.map(m=>!edit&&(m.kind==='stickKeys'||m.kind==='stickMouse')?<MappingStick key={m.id} mapping={m} send={send} holds={holds.current!} style={position(m)}/>:<button key={m.id} type="button" aria-label={m.name||m.label} title={m.label} className={`mapping-button${edit?' editing':''}${selected===m.id&&edit?' selected':''}`} style={position(m)}
 onPointerDown={e=>{e.stopPropagation();e.preventDefault();e.currentTarget.setPointerCapture(e.pointerId);if(edit){select(m.id);const r=e.currentTarget.getBoundingClientRect();drag.current={pointer:e.pointerId,id:m.id,dx:e.clientX-r.left-r.width/2,dy:e.clientY-r.top-r.height/2};}else if(m.kind==='toggle')onToggleMode?.();else holds.current!.down(m.id+':'+e.pointerId,m);}}
 onPointerMove={e=>{e.stopPropagation();const d=drag.current;if(!edit||!d||d.pointer!==e.pointerId||d.id!==m.id)return;const r=anchor.current!.closest('.video-surface')!.getBoundingClientRect();setItems(old=>old.map(x=>x.id===m.id?{...x,x:clamp((e.clientX-r.left-d.dx)/r.width*100,0,100),y:clamp((e.clientY-r.top-d.dy)/r.height*100,0,100)}:x));}}
 onPointerUp={e=>{e.stopPropagation();drag.current=null;holds.current!.up(m.id+':'+e.pointerId);}} onPointerCancel={e=>{drag.current=null;holds.current!.up(m.id+':'+e.pointerId);}} onLostPointerCapture={e=>{drag.current=null;holds.current!.up(m.id+':'+e.pointerId);}}
 onClick={e=>{if(edit&&e.detail===0)select(m.id);}}
 onKeyDown={e=>{if(!['Enter',' '].includes(e.key))return;e.preventDefault();if(edit)select(m.id);else if(!e.repeat){if(m.kind==='toggle')onToggleMode?.();else holds.current!.down('keyboard:'+m.id,m);}}} onKeyUp={e=>{if(['Enter',' '].includes(e.key)){e.preventDefault();holds.current!.up('keyboard:'+m.id);}}} onContextMenu={e=>e.preventDefault()}>{m.kind==='key'||m.kind==='chord'?(m.displayLabel||m.label):m.kind.startsWith('stick')?<span className="stick-preview">✥</span>:<MappingGlyph kind={m.kind} code={m.code}/>}</button>)}
 {edit&&panel&&<aside className={`mapping-editor studio-panel ${side}`} aria-label={panel==='library'?'Bibliotek kontrol':'Properti kontrol'} onPointerDown={e=>e.stopPropagation()} onWheel={e=>e.stopPropagation()}>
 <div className="studio-panel-heading"><strong>{panel==='library'?'Tambah kontrol':'Properti kontrol'}</strong><button type="button" aria-label="Pindahkan panel ke sisi lain" onClick={()=>{const next=side==='left'?'right':'left';setSide(next);try{localStorage.setItem('xydesk.mapping.side',next);}catch{}}}>⇄</button><button type="button" aria-label="Tutup panel properti" onClick={()=>setPanel(null)}>×</button></div>
 {panel==='library'?<><p>Pilih jenis, tempatkan pada canvas, lalu atur propertinya. Saat mengedit, input tidak dikirim ke PC.</p><div className="studio-categories">{[['keyboard','Keyboard'],['stick','Stick'],['mouse','Mouse'],['chord','Kombinasi'],['shortcut','Copy & aksi']].map(([id,label])=><button type="button" key={id} aria-pressed={category===id} onClick={()=>setCategory(id)}>{label}</button>)}</div>
 {category==='keyboard'&&<MappingPicker label="Keyboard lengkap" options={KEY_OPTIONS} values={[]} onChange={([code])=>add('key',Number(code))}/>}
 {category==='stick'&&<div className="studio-library"><button type="button" onClick={()=>add('stickKeys',0,[87,83,65,68])}>Stick WASD <small>4 arah + diagonal · tombol bisa diganti</small></button><button type="button" onClick={()=>add('stickKeys',0,[38,40,37,39])}>Stick panah <small>Atas, bawah, kiri, kanan</small></button><button type="button" onClick={()=>add('stickMouse',0)}>Stick mouse <small>Geser kursor relatif; tahan untuk bergerak</small></button><button type="button" disabled>Gamepad analog asli <small>Belum didukung host. Memerlukan protokol dan virtual gamepad; tidak disamarkan sebagai WASD.</small></button></div>}
 {category==='mouse'&&<div className="studio-library">{[0,1,2,3,4].map(code=><button type="button" key={code} onClick={()=>add('mouse',code)}><MappingGlyph kind="mouse" code={code}/>{mappingLabel({kind:'mouse',code})}</button>)}{(['scroll','scrollX'] as const).flatMap(kind=>[120,-120].map(code=><button type="button" key={kind+code} onClick={()=>add(kind,code)}><MappingGlyph kind={kind} code={code}/>{mappingLabel({kind,code})}</button>))}<button type="button" onClick={()=>add('toggle',0)}>Ganti trackpad / sentuh langsung</button></div>}
 {category==='chord'&&<div className="studio-library"><button type="button" onClick={()=>add('chord',0,[17,67])}>Buat kombinasi keyboard <small>Maksimal 6 tombol, modifier dilepas dengan aman</small></button></div>}
 {category==='shortcut'&&<><p>Shortcut bekerja pada aplikasi di PC host, bukan clipboard browser/HP.</p><div className="studio-library">{[['Copy',67],['Paste',86],['Cut',88],['Undo',90],['Select all',65]].map(([label,code])=><button type="button" key={label} onClick={()=>add('chord',0,[17,Number(code)],String(label))}>{label}</button>)}</div></>}
 <details><summary>Preset layout</summary><p>Mengganti seluruh canvas saat ini.</p><button type="button" onClick={()=>{if(confirm('Ganti canvas dengan preset mouse?'))setItems(defaults());}}>Mouse</button><button type="button" onClick={()=>{if(confirm('Ganti canvas dengan preset FPS?'))setItems(fpsDefaults());}}>FPS</button></details></>:
 selectedItem?<><p className="studio-action-summary">{selectedItem.label}</p><label>Nama kontrol<input maxLength={48} value={selectedItem.name||''} placeholder={selectedItem.label} onChange={e=>update({name:e.target.value})}/></label>
 {(selectedItem.kind==='key'||selectedItem.kind==='chord')?<label>Teks pada tombol<input maxLength={24} value={selectedItem.displayLabel||''} placeholder={selectedItem.label} onChange={e=>update({displayLabel:e.target.value})}/><small>Kosongkan untuk label otomatis.</small></>:<p>Kontrol ini memakai ikon tetap. Nama hanya untuk editor dan aksesibilitas.</p>}
 {selectedItem.kind==='key'&&<MappingPicker label="Aksi keyboard" options={KEY_OPTIONS} values={[selectedItem.code]} onChange={([code])=>update({code:Number(code)})}/>}
 {selectedItem.kind==='chord'&&<><p>{mappingLabel(selectedItem)}</p><MappingPicker label="Kombinasi" multiple options={KEY_OPTIONS} values={selectedItem.keys||[]} onChange={keys=>update({keys:chordKeys(keys)})}/></>}
 {selectedItem.kind==='stickKeys'&&normalizeStickKeys(selectedItem.keys).map((code,i)=><label key={i}>{['Atas','Bawah','Kiri','Kanan'][i]}<select value={code} onChange={e=>{const keys=normalizeStickKeys(selectedItem.keys);keys[i]=Number(e.target.value);update({keys});}}>{KEY_OPTIONS.map(([vk,label])=><option key={vk} value={vk}>{label}</option>)}</select></label>)}
 {selectedItem.kind==='stickMouse'&&<p>Kecepatan mengikuti jarak dari tengah. Lepaskan untuk berhenti. Ini kursor mouse, bukan sumbu gamepad.</p>}
 {(['mouse','scroll','scrollX'] as string[]).includes(selectedItem.kind)&&<MappingPicker label="Aksi" options={selectedItem.kind==='mouse'?[[0,'Klik kiri'],[1,'Klik kanan'],[2,'Klik tengah'],[3,'Kembali'],[4,'Maju']]:selectedItem.kind==='scrollX'?[[120,'Kanan'],[-120,'Kiri']]:[[120,'Atas'],[-120,'Bawah']]} values={[selectedItem.code]} onChange={([code])=>update({code:Number(code)})}/>}
 <label>Ukuran · {selectedItem.size}px<input type="range" min="36" max="160" value={selectedItem.size} onChange={e=>update({size:Number(e.target.value)})}/></label><label>Radius · {selectedItem.radius??selectedItem.size/2}px<input type="range" min="0" max="80" value={selectedItem.radius??selectedItem.size/2} onChange={e=>update({radius:Number(e.target.value)})}/></label>
 <div className="studio-position">{(['x','y'] as const).map(axis=><label key={axis}>{axis.toUpperCase()} %<input type="number" min="0" max="100" value={Math.round(selectedItem[axis])} onChange={e=>update({[axis]:Number(e.target.value)})}/></label>)}</div><button type="button" onClick={()=>{setItems(old=>old.filter(m=>m.id!==selected));setSelected('');setPanel('library');}}>Hapus kontrol</button></>:<p>Pilih kontrol pada canvas untuk mengaturnya.</p>}
 </aside>}
 </>;
}

function MappingStick({mapping:m,send,holds,style}:{mapping:Mapping;send:(b:Uint8Array)=>void;holds:MappingHolds;style:React.CSSProperties}){
 const vector=useRef({x:0,y:0}),pointer=useRef<number|null>(null),timer=useRef<ReturnType<typeof setInterval>|null>(null);
 const [knob,setKnob]=useState({x:0,y:0});
 const reset=()=>{if(timer.current)clearInterval(timer.current);timer.current=null;pointer.current=null;vector.current={x:0,y:0};setKnob({x:0,y:0});for(let i=0;i<4;i++)holds.up('stick:'+m.id+':'+i);};
 useEffect(()=>{const hidden=()=>{if(document.hidden)reset();};window.addEventListener('blur',reset);document.addEventListener('visibilitychange',hidden);return()=>{reset();window.removeEventListener('blur',reset);document.removeEventListener('visibilitychange',hidden);};},[m.id,holds]);
 const move=(e:React.PointerEvent<HTMLDivElement>)=>{if(pointer.current!==e.pointerId)return;const r=e.currentTarget.getBoundingClientRect();let x=(e.clientX-r.left-r.width/2)/(r.width/2),y=(e.clientY-r.top-r.height/2)/(r.height/2);const length=Math.max(1,Math.hypot(x,y));x/=length;y/=length;vector.current={x,y};setKnob({x:x*30,y:y*30});if(m.kind==='stickKeys')stickDirections(x,y).forEach((pressed,i)=>{const owner='stick:'+m.id+':'+i;if(pressed)holds.down(owner,{...m,kind:'key',code:normalizeStickKeys(m.keys)[i]});else holds.up(owner);});};
 return <div className="mapping-button mapping-stick" role="group" aria-label={m.name||m.label} style={style} onContextMenu={e=>e.preventDefault()} onPointerDown={e=>{e.stopPropagation();e.preventDefault();if(pointer.current!==null)return;pointer.current=e.pointerId;e.currentTarget.setPointerCapture(e.pointerId);move(e);if(m.kind==='stickMouse')timer.current=setInterval(()=>{const {x,y}=vector.current;if(Math.hypot(x,y)>.2)send(InputCodec.mouseMoveRel(Math.round(x*14),Math.round(y*14)));},33);}} onPointerMove={e=>{e.stopPropagation();move(e);}} onPointerUp={e=>{e.stopPropagation();if(pointer.current===e.pointerId)reset();}} onPointerCancel={reset} onLostPointerCapture={reset}><span className="stick-cross" aria-hidden="true">＋</span><span className="stick-knob" style={{transform:`translate(${knob.x}px,${knob.y}px)`}} aria-hidden="true"/><span className="stick-caption">{m.kind==='stickKeys'?'KEY':'MOUSE'}</span></div>;
}
// Kontrol pointer bawaan memakai glyph, bukan label panjang di atas desktop.
// Label teks tetap tersedia melalui aria-label untuk pembaca layar dan editor.
function MappingGlyph({kind,code}:{kind:Mapping['kind'];code:number}) {
 if(kind==='mouse') return <svg className="mapping-glyph" viewBox="0 0 24 24" aria-hidden="true"><rect x="5" y="2" width="14" height="20" rx="7"/><path d="M12 2v8M8.5 7.5h3.5M12 7.5h3.5"/><circle cx={code===0?'9.5':code===1?'14.5':'12'} cy="5.5" r="1.35"/></svg>;
 if(kind==='toggle') return <svg className="mapping-glyph" viewBox="0 0 24 24" aria-hidden="true"><path d="M7 7h10l-3-3M17 17H7l3 3M17 7l-3 3M7 17l3-3"/></svg>;
 if(kind==='scroll' || kind==='scrollX') return <svg className="mapping-glyph" viewBox="0 0 24 24" aria-hidden="true">{kind==='scrollX'?<><path d="M5 12h14M9 8l-4 4 4 4M15 8l4 4-4 4"/><circle cx="12" cy="12" r="2"/></>:<><path d={code>0?'M12 19V5M7 10l5-5 5 5':'M12 5v14M7 14l5 5 5-5'}/><circle cx="12" cy="12" r="2"/></>}</svg>;
 return <span className="mapping-key-glyph">{mappingLabel({kind,code})}</span>;
}

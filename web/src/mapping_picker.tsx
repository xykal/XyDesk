import {useState} from 'react';
const groups=['QWERTY','F1–F24','Navigasi','Numpad','Modifier','Media','Semua'];
const qwerty=[...'1234567890QWERTYUIOPASDFGHJKLZXCVBNM'].map(c=>c.charCodeAt(0));
function groupKeys(group:string,code:number){
 if(group==='QWERTY')return qwerty.includes(code)||[32,13,8,9,186,187,188,189,190,191,192,219,220,221,222].includes(code);
 if(group==='F1–F24')return code>=112&&code<=135;
 if(group==='Numpad')return code>=96&&code<=111||code===144;
 if(group==='Modifier')return [16,17,18,20,91,92,93,160,161,162,163,164,165].includes(code);
 if(group==='Media')return code>=173&&code<=179;
 if(group==='Navigasi')return [27,33,34,35,36,37,38,39,40,44,45,46,19,145].includes(code);
 return true;
}
export function MappingPicker({label,options,values,onChange,multiple=false}:{label:string;options:(number|string)[][];values:(number|string)[];onChange:(values:(number|string)[])=>void;multiple?:boolean}){
 const [query,setQuery]=useState(''),[group,setGroup]=useState('QWERTY');
 const keyboard=options.length>80;
 const filtered=options.filter(([value,name])=>String(name).toLowerCase().includes(query.trim().toLowerCase())&&(!keyboard||query||groupKeys(group,Number(value))));
 if(keyboard&&group==='QWERTY'&&!query)filtered.sort((a,b)=>(qwerty.indexOf(Number(a[0]))<0?999:qwerty.indexOf(Number(a[0])))-(qwerty.indexOf(Number(b[0]))<0?999:qwerty.indexOf(Number(b[0]))));
 return <fieldset className="mapping-picker"><legend>{label}</legend>
 {options.length>12&&<input type="search" aria-label={`Cari ${label.toLowerCase()}`} placeholder="Cari tombol, mis. F1 / Ctrl…" value={query} onChange={e=>setQuery(e.target.value)}/>}
 {keyboard&&<div className="mapping-key-groups" aria-label="Kelompok keyboard">{groups.map(g=><button type="button" key={g} aria-pressed={group===g} onClick={()=>{setGroup(g);setQuery('');}}>{g}</button>)}</div>}
 <div className={`mapping-options${keyboard?' keyboard-options':''}`} role="group" aria-label={label}>
 {filtered.map(([value,name])=>{const selected=values.includes(value);return <button type="button" key={value} aria-pressed={selected} disabled={multiple&&!selected&&values.length>=6} onClick={()=>onChange(multiple?(selected?values.filter(v=>v!==value):[...values,value]):[value])}>{String(name)}</button>;})}
 {!filtered.length&&<p>Tombol tidak ditemukan.</p>}</div>
 {multiple&&<small>{values.length}/6 tombol · modifier ditekan lebih dulu</small>}
 </fieldset>;
}

import {useEffect,useRef,useState} from 'react';
import {createPortal} from 'react-dom';
type Request={title:string;message:string;confirmLabel?:string;notice?:boolean;resolve:(accepted:boolean)=>void};
const eventName='xydesk:dialog';
export function confirmAction(message:string,title='Konfirmasi',confirmLabel='Lanjutkan'):Promise<boolean>{
 return new Promise(resolve=>window.dispatchEvent(new CustomEvent<Request>(eventName,{detail:{message,title,confirmLabel,resolve}})));
}
export async function showNotice(message:string,title='Informasi'):Promise<void>{
 await new Promise<boolean>(resolve=>window.dispatchEvent(new CustomEvent<Request>(eventName,{detail:{message,title,notice:true,resolve}})));
}
export function AppDialogs(){
 const [queue,setQueue]=useState<Request[]>([]);const pending=useRef<Request[]>([]);const dialog=useRef<HTMLDialogElement>(null);
 useEffect(()=>{const receive=(event:Event)=>{const item=(event as CustomEvent<Request>).detail;pending.current=[...pending.current,item];setQueue(pending.current);};window.addEventListener(eventName,receive);return()=>{window.removeEventListener(eventName,receive);for(const item of pending.current)item.resolve(false);pending.current=[];};},[]);
 const current=queue[0];
 useEffect(()=>{const el=dialog.current;if(!current||!el)return;el.showModal();return()=>el.close();},[current]);
 if(!current)return null;
 const finish=(accepted:boolean)=>{current.resolve(accepted);pending.current=pending.current.slice(1);setQueue(pending.current);};
 // Keep the dialog in the active fullscreen tree. No browser alert/confirm,
 // orientation API call or fullscreen exit is involved in presenting this UI.
 return createPortal(<dialog ref={dialog} className="app-dialog" aria-labelledby="app-dialog-title" aria-describedby="app-dialog-message" onCancel={e=>{e.preventDefault();finish(false);}} onKeyDown={e=>e.stopPropagation()} onPointerDown={e=>e.stopPropagation()}>
  <h2 id="app-dialog-title">{current.title}</h2><p id="app-dialog-message">{current.message}</p>
  <div className="app-dialog-actions">{!current.notice&&<button type="button" autoFocus onClick={()=>finish(false)}>Batal</button>}<button type="button" className="dialog-primary" autoFocus={current.notice} onClick={()=>finish(true)}>{current.notice?'Mengerti':current.confirmLabel}</button></div>
 </dialog>,document.fullscreenElement??document.body);
}

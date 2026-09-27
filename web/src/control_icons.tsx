/** Shared mouse silhouette: the active physical button is filled, not renamed. */
export function MouseButtonIcon({button=0}:{button?:number}) {
 return <svg className="mapping-glyph mouse-button-icon" viewBox="0 0 32 32" width="32" height="32" fill="none" stroke="currentColor" strokeWidth="1.65" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
 <rect x="7" y="2" width="18" height="28" rx="9"/>
 <path d="M16 3v9M7 14h18" opacity=".65"/>
 {button===0&&<path d="M14 4a7 7 0 0 0-5 7v1h5z" fill="currentColor" stroke="none"/>}
 {button===1&&<path d="M18 4a7 7 0 0 1 5 7v1h-5z" fill="currentColor" stroke="none"/>}
 <rect x="14.5" y="7" width="3" height="6" rx="1.5" fill={button===2?'currentColor':'none'}/>
 {button===3&&<path d="m17 18-4 4 4 4M13 22h7"/>}
 {button===4&&<path d="m15 18 4 4-4 4M19 22h-7"/>}
 </svg>;
}

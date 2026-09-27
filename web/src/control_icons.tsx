/** Outline only. The emphasized contour identifies the physical mouse button. */
export function MouseButtonIcon({button=0}:{button?:number}) {
 return <svg className="mapping-glyph mouse-button-icon" viewBox="0 0 32 32" width="32" height="32" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
  <rect x="7" y="2" width="18" height="28" rx="9"/>
  <path d="M16 3v4M7 14h18"/>
  <rect x="14.5" y="7" width="3" height="6" rx="1.5" strokeWidth={button===2?2.8:1.4}/>
  {button===0&&<path d="M13 4a7 7 0 0 0-4 7" strokeWidth="3"/>}
  {button===1&&<path d="M19 4a7 7 0 0 1 4 7" strokeWidth="3"/>}
  {button===3&&<path d="m16 19-4 3 4 3M12 22h8"/>}
  {button===4&&<path d="m16 19 4 3-4 3M20 22h-8"/>}
 </svg>;
}

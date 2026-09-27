/** Symmetric, optically centered mouse. Filled half indicates the physical button. */
export function MouseButtonIcon({button=0}:{button?:number}) {
 return <svg className="mapping-glyph mouse-button-icon" viewBox="0 0 32 32" width="32" height="32" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
  <rect x="5" y="3" width="22" height="26" rx="10"/>
  {button===0&&<path d="M14 5a8 8 0 0 0-7 8v1h7z" fill="currentColor" stroke="none"/>}
  {button===1&&<path d="M18 5a8 8 0 0 1 7 8v1h-7z" fill="currentColor" stroke="none"/>}
  <path d="M16 4v4M6 16h20"/>
  <rect x="14.5" y="8" width="3" height="6" rx="1.5" fill={button===2?'currentColor':'none'}/>
  {button===3&&<path d="m16 19-4 3 4 3M12 22h8"/>}
  {button===4&&<path d="m16 19 4 3-4 3M20 22h-8"/>}
 </svg>;
}

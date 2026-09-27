// Browser-only test entry. Vite resolves one React instance for components/hooks.
import React, {useState} from 'react';
import {createRoot} from 'react-dom/client';
import {SessionRail, VirtualKeyboard, StatisticsPanel} from '../src/session_ui';
export function mountControls(container:HTMLElement,send:(bytes:Uint8Array)=>void){
 function Controls(){
  const [collapsed,setCollapsed]=useState(false),[kb,setKb]=useState(false);
  const [stats,setStats]=useState(false);
  const noop=()=>{};
  return <><SessionRail collapsed={collapsed} onToggleCollapsed={()=>setCollapsed(v=>!v)}
   statisticsOpen={stats} onStatistics={()=>setStats(v=>!v)} audioOn={false} onAudio={noop} micOn={false} onMic={noop} kbOpen={kb} onKeyboard={()=>setKb(v=>!v)}
   padOpen={false} onPad={noop} trackpad={false} onTrackpad={noop} onClipboardPush={noop} onClipboardPull={noop}
   onFullscreen={noop} fullscreenOn={false} panelOpen={false} onPanel={noop} onDisconnect={noop}/>
   {stats&&<StatisticsPanel stats={{width:1280,height:720,fps:30,mbps:1,rttMs:90,lossPct:0,codec:"H264",transportPath:"direct-p2p",transportProtocol:"UDP"}} onClose={()=>setStats(false)}/>}
   {kb&&<VirtualKeyboard send={send} onClose={()=>setKb(false)}/>}</>;
 }
 const root=createRoot(container);root.render(<Controls/>);return ()=>root.unmount();
}

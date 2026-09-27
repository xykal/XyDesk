// Browser-only entry: actual components and real mapping input, no host credentials.
import React, {useState} from 'react';
import {createRoot} from 'react-dom/client';
import {SessionRail, VirtualKeyboard, SessionPanel, DEFAULT_PREFS} from '../src/session_ui';
import type {PanelTab} from '../src/session_ui';
import {CustomControlMapping} from '../src/control_mapping';
export function mountControls(container:HTMLElement,send:(bytes:Uint8Array)=>void){
 function Controls(){
  const [collapsed,setCollapsed]=useState(false),[kb,setKb]=useState(false),[panel,setPanel]=useState(false),[pad,setPad]=useState(true);
  const [tab,setTab]=useState<PanelTab>('gambar'),[prefs,setPrefs]=useState(DEFAULT_PREFS);
  const noop=()=>{};
  return <div className="video-surface" style={{position:'absolute',inset:0,width:'100%',height:'100%',minHeight:0,aspectRatio:'auto',background:'linear-gradient(120deg,#244556,#66878a)'}}>
   <SessionRail collapsed={collapsed} onToggleCollapsed={()=>setCollapsed(v=>!v)}
   statisticsOpen={panel&&tab==='statistik'} onStatistics={()=>{setTab('statistik');setPanel(true);}} audioOn={false} onAudio={noop} micOn={false} onMic={noop} kbOpen={kb} onKeyboard={()=>setKb(v=>!v)}
   padOpen={pad} onPad={()=>setPad(v=>!v)} trackpad={false} onTrackpad={noop} onClipboardPush={noop} onClipboardPull={noop}
   onFullscreen={noop} fullscreenOn={false} panelOpen={panel} onPanel={()=>setPanel(v=>!v)} onDisconnect={noop}/>
   {panel&&<SessionPanel activeTab={tab} onTabChange={setTab} controlsVisible={pad} onControlsVisibilityChange={setPad} audioOn={false} onAudio={noop} micOn={false} onMic={noop}
    prefs={prefs} onChange={setPrefs} onClose={()=>setPanel(false)} hostId="123456789" onDisconnect={noop}
    stats={{width:1280,height:720,fps:30,mbps:1,rttMs:90,lossPct:0,codec:"H264",transportPath:"direct-p2p",transportProtocol:"UDP"}}
    displays={[{index:0,name:'Monitor utama',width:1920,height:1080}]} wantedDisplay={0} onSelectDisplay={noop} connectedAt={null} railCollapsed={collapsed} totalSesiDetik={null} trackpad={false} onTrackpadMode={noop}/>}
   {pad&&<CustomControlMapping send={send}/>}
   {kb&&<VirtualKeyboard send={send} onClose={()=>setKb(false)}/>}</div>;
 }
 const root=createRoot(container);root.render(<Controls/>);return ()=>root.unmount();
}

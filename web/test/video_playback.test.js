import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';
import {transformWithOxc} from 'vite';
const {code}=await transformWithOxc(readFileSync(new URL('../src/video_playback.ts',import.meta.url),'utf8').replace(/^export /gm,'')+'\nexports.api={videoOnlyStream,playRemoteVideo,startRemoteVideoPlayback};','video_playback.ts');
function api(){const exports={};vm.runInNewContext(code,{exports,setTimeout,clearTimeout,MediaStream:class{constructor(tracks){this.tracks=tracks;}}});return exports.api;}
test('sink video hanya membawa track video, bukan audio yang tidak mengalir',()=>{
 const video={kind:'video'},audio={kind:'audio'};const stream={getVideoTracks:()=>[video],getTracks:()=>[video,audio]};
 const filtered=api().videoOnlyStream(stream);assert.equal(filtered.tracks.length,1);assert.equal(filtered.tracks[0],video);
});
test('play eksplisit mengaktifkan muted/inline dan tidak mengganti stream saat retry',async()=>{
 const stream={};let writes=0,plays=0;let src;
 const el={set srcObject(v){writes++;src=v;},get srcObject(){return src;},play:async()=>{plays++;}};
 const {playRemoteVideo}=api();await playRemoteVideo(el,stream);await playRemoteVideo(el,stream);
 assert.equal(el.muted,true);assert.equal(el.playsInline,true);assert.equal(writes,1);assert.equal(plays,2);
});
test('penolakan play diteruskan untuk tombol gesture, bukan ditelan',async()=>{
 const el={play:async()=>{throw new Error('NotAllowedError');}};
 await assert.rejects(api().playRemoteVideo(el,{}),/NotAllowedError/);
});
function player() {
 const listeners = new Map();
 return {srcObject:null,paused:true,plays:0,pauses:0,
  play:async function(){this.plays++;this.paused=false;},
  pause(){this.pauses++;this.paused=true;},
  addEventListener(name,fn){listeners.set(name,fn);},
  removeEventListener(name,fn){if(listeners.get(name)===fn)listeners.delete(name);},listeners};
}
test('first session mounts the decoded stream on an initially EMPTY video element',async()=>{
 const el=player(), stream={}; const states=[];
 const stop=api().startRemoteVideoPlayback(el,stream,()=>true,b=>states.push(b));
 assert.equal(el.srcObject,stream);assert.equal(el.plays,1);
 await new Promise(setImmediate);
 assert.equal(el.paused,false);assert.equal(states.at(-1),false);
 stop();assert.equal(el.srcObject,null);assert.equal(el.listeners.size,0);
});
test('StrictMode cleanup/remount reattaches without stopping peer-owned tracks',()=>{
 const el=player(), stream={getTracks:()=>[{stop(){throw Error('must not stop tracks');}}]};
 const {startRemoteVideoPlayback}=api();
 startRemoteVideoPlayback(el,stream,()=>true)();
 const stop=startRemoteVideoPlayback(el,stream,()=>true);
 assert.equal(el.srcObject,stream);assert.equal(el.plays,2);stop();
});
test('stale playback owner cannot attach or clear another stream',()=>{
 const el=player(), stream={};const stop=api().startRemoteVideoPlayback(el,stream,()=>false);
 assert.equal(el.srcObject,null);assert.equal(el.plays,0);stop();
 const active=api().startRemoteVideoPlayback(el,stream,()=>true);
 const newer={};el.srcObject=newer;active();assert.equal(el.srcObject,newer);
});
test('rejected play after teardown schedules no retry or blocked UI update',async()=>{
 const el=player();let reject;el.play=()=>new Promise((_,r)=>{reject=r;});
 const states=[];const stop=api().startRemoteVideoPlayback(el,{},()=>true,b=>states.push(b));
 stop();reject(Error('aborted'));await new Promise(setImmediate);
 assert.deepEqual(states,[]);assert.equal(el.srcObject,null);
});
test('React session delegates first attachment to the lifecycle controller',()=>{
 const source=readFileSync(new URL('../src/connect_screen.tsx',import.meta.url),'utf8');
 assert.match(source,/videoPlaybackStop\.current = startRemoteVideoPlayback\(video, remoteVideoStream/);
 assert.doesNotMatch(source,/if\s*\([^\n]*video\.srcObject !== remoteVideoStream\) return/);
});

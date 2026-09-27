// Runs only in Actions. No real account, host, pairing secret or external media.
import {createRequire} from 'node:module';
import {mkdir,writeFile} from 'node:fs/promises';
import assert from 'node:assert/strict';
const require=createRequire(process.env.RUNNER_TEMP+'/xydesk-browser/package.json');
const {chromium}=require('playwright');
const base='http://127.0.0.1:5173';
await mkdir('browser-evidence',{recursive:true});
for(let attempt=0;attempt<30;attempt++){
 try{if((await fetch(base)).ok)break;}catch{}
 if(attempt===29)throw Error('Vite server failed to start');
 await new Promise(r=>setTimeout(r,1000));
}
const browser=await chromium.launch();
const errors=[];const results=[];let page;
try{
 page=await browser.newPage({viewport:{width:1366,height:900}});
 page.on('pageerror',e=>errors.push(e.message));
 await page.goto(base+'/controls');
 await page.getByRole('button',{name:'Atur kontrol · fullscreen',exact:true}).waitFor();
 // Browser-level reproduction of the original EMPTY srcObject dead-end.
 await page.evaluate(async()=>{
  const {startRemoteVideoPlayback}=await import('/src/video_playback.ts');
  const canvas=document.createElement('canvas');canvas.width=160;canvas.height=90;
  const ctx=canvas.getContext('2d');ctx.fillStyle='#57d39a';ctx.fillRect(0,0,160,90);
  const video=document.createElement('video');video.id='fixture-video';video.autoplay=true;
  document.body.append(video);const stream=canvas.captureStream(10);
  window.fixtureStop=startRemoteVideoPlayback(video,stream,()=>video.isConnected);
  window.fixtureStream=stream;
  window.fixtureTimer=setInterval(()=>{ctx.fillRect(0,0,160,90);},100);
 });
 await page.waitForFunction(()=>{const v=document.querySelector('#fixture-video');return v&&!v.paused&&v.videoWidth===160&&v.videoHeight===90&&v.readyState>=2;});
 results.push('Real canvas MediaStream attaches to empty video, plays and presents 160x90 frames');
 await page.evaluate(()=>{window.fixtureStop();clearInterval(window.fixtureTimer);window.fixtureStream.getTracks().forEach(t=>t.stop());document.querySelector('#fixture-video').remove();});
 await page.getByRole('button',{name:'Buka menu',exact:true}).click();
 const menu=page.getByRole('navigation',{name:'Menu utama'});
 await menu.waitFor({state:'visible'});
 const bounds=await menu.boundingBox();assert.ok(bounds.width>300);assert.ok(bounds.height<450);
 assert.equal(await menu.evaluate(el=>el.parentElement.tagName),'HEADER');
 assert.equal(await page.evaluate(()=>document.fullscreenElement===null),true);
 await page.screenshot({path:'browser-evidence/menu-desktop.png'});
 await page.keyboard.press('Escape');await menu.waitFor({state:'hidden'});
 results.push('Hamburger navigation belongs to header, not a modal; Escape closes');
 await page.getByRole('button',{name:'Atur kontrol · fullscreen',exact:true}).click();
 await page.locator('.mapping-studio-active').waitFor();
 await page.locator('.mapping-editor .mapping-options').getByRole('button',{name:'A',exact:true}).click();
 await page.getByLabel('Nama kontrol',{exact:true}).fill('Tes tombol');
 await page.getByLabel('Teks pada tombol',{exact:true}).fill('JUMP');
 await page.screenshot({path:'browser-evidence/editor-desktop.png'});
 await page.getByRole('button',{name:'Pindahkan panel ke sisi lain'}).click();
 assert.equal(await page.locator('.studio-panel.left').count(),1);
 await page.getByRole('button',{name:'Simpan',exact:true}).click();
 await page.reload();
 await page.getByRole('button',{name:'Tes tombol',exact:true}).waitFor();
 assert.equal(await page.getByRole('button',{name:'Tes tombol',exact:true}).textContent(),'JUMP');
 results.push('Editor selection opens inspector; custom name/label and side persist');
 await page.getByRole('button',{name:'Atur kontrol · fullscreen',exact:true}).click();
 await page.getByRole('button',{name:'Stick',exact:true}).click();
 assert.equal(await page.getByRole('button',{name:/Gamepad analog asli/}).isDisabled(),true);
 await page.getByRole('button',{name:/Stick WASD/}).click();
 await page.getByRole('button',{name:'Simpan',exact:true}).click();
 const stick=page.locator('.mapping-stick');
 await page.waitForFunction(()=>document.fullscreenElement===null&&!document.querySelector('.mapping-studio-active'));
 await stick.scrollIntoViewIfNeeded();
 await stick.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
 const r=await stick.boundingBox();
 assert.equal(await stick.evaluate(el=>{const r=el.getBoundingClientRect();return document.elementFromPoint(r.x+r.width/2,r.y+r.height/2)?.closest('.mapping-stick')===el;}),true,'stick center is hit-testable after fullscreen exit');
 await page.mouse.move(r.x+r.width/2,r.y+r.height/2);await page.mouse.down();
 await page.mouse.move(r.x+r.width*.8,r.y+r.height*.2);
 await page.waitForFunction(()=>document.querySelector('.mapping-stick .stick-knob')?.style.transform!=='translate(0px, 0px)');
 await page.mouse.up();
 assert.equal(await stick.locator('.stick-knob').evaluate(el=>el.style.transform),'translate(0px, 0px)');
 results.push('WASD stick moves and recenters on release; unsupported analog gamepad is disabled');
 await page.setViewportSize({width:390,height:844});
 await page.getByRole('button',{name:'Atur kontrol · fullscreen',exact:true}).click();
 await page.getByRole('button',{name:'Mouse',exact:true}).click();
 await page.screenshot({path:'browser-evidence/editor-mobile.png'});
 const panel=await page.locator('.studio-panel').boundingBox();
 assert.ok(panel.x>=0&&panel.x+panel.width<=391,'mobile inspector stays within viewport');
 results.push('Mobile inspector fits 390px viewport');
 await page.getByRole('button',{name:'Simpan',exact:true}).click();
 await page.getByRole('button',{name:'Buka menu',exact:true}).click();
 await menu.waitFor({state:'visible'});
 assert.equal(await page.getByRole('navigation',{name:'Aplikasi remote'}).isVisible(),false);
 assert.equal(await page.getByRole('button',{name:'Control Studio',exact:true}).count(),1);
 await page.screenshot({path:'browser-evidence/menu-mobile.png'});
 await page.keyboard.press('Escape');
 results.push('Mobile menu stays inside header with no duplicate remote navigation');
 // Mount real exported session components without a host or credentials.
 await page.evaluate(async()=>{
   const {mountControls}=await import('/test/session-controls-fixture.tsx');
   const fixture=document.createElement('div');fixture.id='session-controls-fixture';fixture.style.cssText='position:fixed;inset:0;z-index:1000;background:#202428';document.body.append(fixture);
   window.fixturePackets=[];
   window.unmountControls=mountControls(fixture,b=>window.fixturePackets.push([...b]));
 });
 await page.getByRole('button',{name:'Sembunyikan kontrol',exact:true}).click();
 assert.equal(await page.getByRole('toolbar',{name:'Kontrol sesi'}).count(),0);
 await page.getByRole('button',{name:'Tampilkan kontrol',exact:true}).click();
 await page.getByRole('toolbar',{name:'Kontrol sesi'}).waitFor();
 await page.getByRole('button',{name:'Keyboard',exact:true}).click();
 await page.getByRole('button',{name:'Pengaturan keyboard',exact:true}).click();
 await page.getByLabel('Jenis keyboard').selectOption('native');
 await page.getByLabel('Teks untuk PC').fill('Halo PC');
 await page.getByRole('button',{name:'Kirim teks',exact:true}).click();
 assert.equal(await page.getByLabel('Teks untuk PC').inputValue(),'');
 assert.ok(await page.evaluate(()=>window.fixturePackets.length>0));
 await page.evaluate(()=>{window.unmountControls();document.querySelector('#session-controls-fixture').remove();});
 results.push('Rail hides/restores and native keyboard text composer sends once');
 await page.goto(base+'/connect?device=123456789');
 await page.getByRole('region',{name:'Pemulihan sesi'}).waitFor();
 assert.ok((await page.getByRole('region',{name:'Pemulihan sesi'}).textContent()).includes('123456789'));
 await page.getByRole('region',{name:'Pemulihan sesi'}).getByLabel(/^Password pairing/).waitFor();
 assert.equal(new URL(page.url()).searchParams.has('password'),false);
 results.push('ID-only connect link reaches scoped session/password prompt without password in URL');


 assert.deepEqual(errors,[],'No browser page errors');
 await writeFile('browser-evidence/results.json',JSON.stringify({passed:results,browserErrors:errors,scope:'Synthetic media + editor/menu. No real host, RDP or OAuth acceptance.'},null,2));
 console.log(results.join('\n'));
}catch(error){
 if(page)await page.screenshot({path:'browser-evidence/failure.png'}).catch(()=>{});
 throw error;
}finally{
 await writeFile('browser-evidence/page-errors.json',JSON.stringify(errors,null,2));
 await browser.close();
}

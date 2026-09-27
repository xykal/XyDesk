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
async function settledScreenshot(options){
 await page.evaluate(()=>document.getAnimations().filter(a=>a.effect?.getTiming().iterations!==Infinity).forEach(a=>a.finish()));
 await page.screenshot(options);
}
try{
 page=await browser.newPage({viewport:{width:1366,height:900}});
 page.on('pageerror',e=>errors.push(e.message));
 page.on('dialog',async dialog=>{errors.push('Unexpected browser dialog: '+dialog.type());await dialog.dismiss();});
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
 assert.equal(await page.locator('.remote-menu-toggle').isVisible(),false);
 await settledScreenshot({path:'browser-evidence/navigation-desktop.png'});
 await page.setViewportSize({width:390,height:844});
 await page.getByRole('button',{name:'Buka menu',exact:true}).click();
 const menu=page.getByRole('navigation',{name:'Menu utama'});
 await menu.waitFor({state:'visible'});
 const menuDialog=page.getByRole('dialog',{name:'Navigasi XyDesk'});
 await menuDialog.evaluate(el=>el.getAnimations().forEach(a=>a.finish()));
 const bounds=await menuDialog.boundingBox();assert.equal(bounds.x,0);assert.equal(bounds.y,0);assert.equal(bounds.width,390);assert.equal(bounds.height,844);
 assert.equal(await menu.evaluate(el=>el.parentElement.tagName),'DIALOG');
 assert.equal(await page.evaluate(()=>document.fullscreenElement===null),true);
 await settledScreenshot({path:'browser-evidence/menu-fullscreen-portrait.png'});
 await page.setViewportSize({width:844,height:390});
 const landscapeMenu=await menuDialog.boundingBox();assert.equal(landscapeMenu.width,844);assert.equal(landscapeMenu.height,390);
 await settledScreenshot({path:'browser-evidence/menu-fullscreen-landscape.png'});
 await page.keyboard.press('Escape');await menu.waitFor({state:'hidden'});
 await page.setViewportSize({width:1366,height:900});
 assert.equal(await page.locator('.remote-menu-toggle').isVisible(),false);
 results.push('Desktop has no hamburger; mobile navigation fills viewport without Fullscreen API and Escape closes');
 await page.getByRole('button',{name:'Atur kontrol · fullscreen',exact:true}).click();
 // Custom dialog must stay in fullscreen and never invoke window.confirm.
 await page.getByRole('button',{name:'Batal',exact:true}).click();
 await page.getByRole('dialog',{name:'Batalkan perubahan?'}).waitFor();
 assert.equal(await page.evaluate(()=>!!document.fullscreenElement),true);
 await page.getByRole('dialog').getByRole('button',{name:'Batal',exact:true}).click();
 results.push('Custom confirmation remains in device fullscreen');

 await page.locator('.mapping-studio-active').waitFor();
 await page.locator('.mapping-editor .mapping-options').getByRole('button',{name:'A',exact:true}).click();
 await page.getByLabel('Nama kontrol',{exact:true}).fill('Tes tombol');
 await page.getByLabel('Teks pada tombol',{exact:true}).fill('JUMP');
 await settledScreenshot({path:'browser-evidence/editor-desktop.png'});
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
 await settledScreenshot({path:'browser-evidence/editor-mobile.png'});
 const panel=await page.locator('.studio-panel').boundingBox();
 assert.ok(panel.x>=0&&panel.x+panel.width<=391,'mobile inspector stays within viewport');
 results.push('Mobile inspector fits 390px viewport');
 await page.getByRole('button',{name:'Simpan',exact:true}).click();
 await page.getByRole('button',{name:'Buka menu',exact:true}).click();
 await menu.waitFor({state:'visible'});
 assert.equal(await page.getByRole('navigation',{name:'Aplikasi remote'}).isVisible(),false);
 assert.equal(await page.getByRole('button',{name:'Control Studio',exact:true}).count(),1);
 await settledScreenshot({path:'browser-evidence/menu-mobile.png'});
 await page.keyboard.press('Escape');
 results.push('Mobile fullscreen navigation has no duplicate desktop links');
 // Mount real exported session components without a host or credentials.
 await page.evaluate(async()=>{
   const {mountControls}=await import('/test/session-controls-fixture.tsx');
   const fixture=document.createElement('div');fixture.id='session-controls-fixture';fixture.className='remote-session';fixture.style.cssText='position:fixed;inset:0;z-index:1000;background:#202428';document.body.append(fixture);
   window.fixturePackets=[];
   window.unmountControls=mountControls(fixture,b=>window.fixturePackets.push([...b]));
 });
 const fixture=page.locator('#session-controls-fixture');
 const mapping=fixture.locator('.mapping-button');const mappingCount=await mapping.count();assert.ok(mappingCount>0);
 await page.getByRole('button',{name:'Sembunyikan rail',exact:true}).click();
 assert.equal(await mapping.count(),mappingCount,'hiding rail preserves mapped controls');
 assert.equal(await mapping.evaluateAll(items=>items.every(el=>getComputedStyle(el).backgroundColor==='rgba(0, 0, 0, 0)'&&getComputedStyle(el).backgroundImage==='none')),true,'input controls have no background');
 await settledScreenshot({path:'browser-evidence/controls-transparent.png'});
 assert.equal(await page.getByRole('toolbar',{name:'Kontrol sesi'}).count(),0);
 await page.getByRole('button',{name:'Tampilkan rail',exact:true}).click();
 await page.getByRole('toolbar',{name:'Kontrol sesi'}).waitFor();
 await page.getByRole('button',{name:'Statistik koneksi',exact:true}).click();
 await page.getByRole('complementary',{name:'Statistik koneksi'}).waitFor();
 assert.ok((await page.getByRole('complementary',{name:'Statistik koneksi'}).textContent()).includes('Langsung (P2P)'));
 const drawer=page.getByRole('complementary',{name:'Pengaturan sesi',exact:true});
 assert.equal(await drawer.getByRole('tab').count(),5);
 assert.equal(await mapping.count(),mappingCount,'opening settings preserves mapped controls');
 for(const viewport of [{width:390,height:844},{width:844,height:390},{width:1366,height:900}]){
   await page.setViewportSize(viewport);
   await drawer.evaluate(el=>el.getAnimations().forEach(a=>a.finish()));
   const rect=await drawer.boundingBox();assert.ok(Math.abs(rect.y)<1);assert.ok(Math.abs(rect.height-viewport.height)<1);assert.ok(Math.abs(rect.x+rect.width-viewport.width)<1,'drawer stays docked right and full height');
   for(const name of ['Video','Kontrol','Audio','Statistik','Sesi']){
    await drawer.getByRole('tab',{name,exact:true}).click();
    const body=drawer.getByRole('tabpanel');assert.equal(await body.evaluate(el=>el.scrollWidth<=el.clientWidth+1),true,'no horizontal overflow');
   }
   await drawer.getByRole('tab',{name:'Video',exact:true}).click();
   await settledScreenshot({path:`browser-evidence/session-video-${viewport.width}.png`});
 }
 await drawer.getByRole('tab',{name:'Video',exact:true}).focus();
 await page.keyboard.press('ArrowRight');
 assert.equal(await drawer.getByRole('tab',{name:'Kontrol',exact:true}).getAttribute('aria-selected'),'true');
 await settledScreenshot({path:'browser-evidence/session-control-desktop.png'});
 await drawer.getByRole('tab',{name:'Statistik',exact:true}).click();
 await settledScreenshot({path:'browser-evidence/session-statistics-desktop.png'});
 await page.emulateMedia({reducedMotion:'reduce'});
 assert.equal(await drawer.evaluate(el=>getComputedStyle(el).animationName),'none');
 await page.emulateMedia({reducedMotion:'no-preference'});
 const beforeCloseCount=await mapping.count();
 await page.getByRole('button',{name:'Tutup pengaturan sesi',exact:true}).click();
 assert.equal(await mapping.count(),beforeCloseCount,'closing panel preserves current orientation mapping');
 await page.setViewportSize({width:390,height:844});
 results.push('Full-height right drawer across portrait/landscape/desktop; five categories; independent rail/mapping; reduced motion respected');
 await page.getByRole('button',{name:'Keyboard',exact:true}).click();
 const key=page.locator('.vkb-key').filter({hasText:/^q$/});
 const keyRect=await key.boundingBox();assert.ok(keyRect.height>=48&&keyRect.width>=28,'touch keyboard is not compressed desktop layout');
 assert.equal(await page.locator('.touch-layout .vkb-row').evaluateAll(rows=>rows.every(row=>row.scrollWidth<=row.clientWidth+1&&row.getBoundingClientRect().right<=innerWidth)),true,'every touch row fits without horizontal scrolling');
 assert.equal(await page.locator('.srail-keyboard').isVisible(),false,'floating keyboard toggle does not cover typing keys');
 await settledScreenshot({path:'browser-evidence/touch-keyboard.png'});

 await page.getByRole('button',{name:'Pengaturan keyboard',exact:true}).click();
 const opacity=page.getByRole('slider',{name:'Transparansi background keyboard'});
 await opacity.focus();await page.keyboard.press('Home');
 for(let i=0;i<13;i++)await page.keyboard.press('ArrowRight');
 assert.equal(await page.evaluate(()=>localStorage.getItem('xydesk.keyboard.transparency')),'65');
 const keyboardPaint=await key.evaluate(el=>({background:getComputedStyle(el).backgroundColor,opacity:getComputedStyle(el).opacity}));
 assert.ok(keyboardPaint.background.endsWith('0.35)'),keyboardPaint.background);
 assert.equal(keyboardPaint.opacity,'1');
 await settledScreenshot({path:'browser-evidence/keyboard-transparent.png'});
 await page.getByLabel('Jenis keyboard').selectOption('native');
 await page.getByLabel('Teks untuk PC').fill('Halo PC');
 await page.getByRole('button',{name:'Kirim teks',exact:true}).click();
 assert.equal(await page.getByLabel('Teks untuk PC').inputValue(),'');
 assert.ok(await page.evaluate(()=>window.fixturePackets.length>0));
 await page.evaluate(()=>{window.unmountControls();document.querySelector('#session-controls-fixture').remove();});
 results.push('Rail hides/restores and native keyboard text composer sends once');
 await page.goto(base+'/connect?device=123456789');
 await page.getByRole('region',{name:'Pemulihan sesi'}).waitFor();
 assert.equal((await page.getByRole('region',{name:'Pemulihan sesi'}).textContent()).includes('123456789'),false,'connection view does not print the device ID');
 await page.getByRole('region',{name:'Pemulihan sesi'}).getByLabel(/^Password pairing/).waitFor();
 assert.equal(new URL(page.url()).searchParams.has('password'),false);
 results.push('ID-only connect link reaches scoped session/password prompt without password in URL');


 await page.evaluate(async()=>{
  const {mountMorph}=await import('/test/connection-morph-fixture.tsx');mountMorph();
 });
 const morph=page.locator('#morph-fixture .connection-morph-motion');await morph.waitFor();
 for(const [time,label] of [[0,'phone'],[2.5,'pc'],[4.5,'tablet']]){
  await morph.evaluate((el,t)=>{el.pauseAnimations();el.setCurrentTime(t);},time);
  const width=await morph.locator('rect').evaluate(el=>el.width.animVal.value);
  assert.ok(label==='phone'?width<50:label==='pc'?width>120:width>80&&width<100);
  await settledScreenshot({path:`browser-evidence/loading-${label}.png`});
 }
 await page.emulateMedia({reducedMotion:'reduce'});
 assert.equal(await morph.isVisible(),false);
 assert.equal(await page.locator('.connection-morph-static').isVisible(),true);
 results.push('Keyboard background transparency preserves opaque text; continuous device morph and reduced-motion fallback');
 assert.deepEqual(errors,[],'No browser page errors');
 await writeFile('browser-evidence/results.json',JSON.stringify({passed:results,browserErrors:errors,scope:'Synthetic media + editor/menu. No real host, RDP or OAuth acceptance.'},null,2));
 console.log(results.join('\n'));
}catch(error){
 if(page)await settledScreenshot({path:'browser-evidence/failure.png'}).catch(()=>{});
 throw error;
}finally{
 await writeFile('browser-evidence/page-errors.json',JSON.stringify(errors,null,2));
 await browser.close();
}

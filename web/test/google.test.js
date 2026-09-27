import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {readFileSync} from 'node:fs';
import {webcrypto} from 'node:crypto';
import {transformWithOxc} from 'vite';
const input=readFileSync(new URL('../src/google.ts',import.meta.url),'utf8')
 .replace(/import\.meta\.env\.VITE_GOOGLE_CLIENT_ID/g,"'test-client'").replace(/^export /gm,'');
const {code}=await transformWithOxc(input+'\nexports.api={beginGoogleLogin,consumeGoogleRedirect,storeGoogleIdToken,getStoredGoogleIdToken,clearStoredGoogleIdToken};','google.ts');
function setup(){
 const local=new Map(),session=new Map();
 const storage=m=>({getItem:k=>m.get(k)??null,setItem:(k,v)=>m.set(k,v),removeItem:k=>m.delete(k)});
 const window={location:{origin:'https://example.test',pathname:'/auth/callback',hash:'',href:''},history:{replaceState(){window.location.hash='';}}};
 const exports={};vm.runInNewContext(code,{exports,window,sessionStorage:storage(session),localStorage:storage(local),crypto:webcrypto,URLSearchParams,atob,Date});
 return {...exports.api,window,session};
}
function callback(s,nonce){
 s.beginGoogleLogin();const url=new URL(s.window.location.href);
 const payload={nonce:nonce??url.searchParams.get('nonce'),exp:Date.now()/1000+3600};
 const token='e30.'+Buffer.from(JSON.stringify(payload)).toString('base64url')+'.fixture';
 s.window.location.hash='#'+new URLSearchParams({id_token:token,state:url.searchParams.get('state')});return token;
}
test('OAuth state and nonce match once; fragment is scrubbed',()=>{const s=setup(),token=callback(s);assert.equal(s.consumeGoogleRedirect(),token);assert.equal(s.window.location.hash,'');assert.equal(s.consumeGoogleRedirect(),null);assert.equal(s.session.size,0);});
test('OAuth rejects mismatched nonce and consumes correlation state',()=>{const s=setup();callback(s,'wrong');assert.equal(s.consumeGoogleRedirect(),null);assert.equal(s.window.location.hash,'');assert.equal(s.session.size,0);});
test('OAuth rejects missing nonce despite matching state',()=>{const s=setup();callback(s);s.session.delete('xydesk.google.nonce');assert.equal(s.consumeGoogleRedirect(),null);});
test('logout helper removes founder Google token',()=>{const s=setup(),token=callback(s);s.storeGoogleIdToken(token);assert.equal(s.getStoredGoogleIdToken(),token);s.clearStoredGoogleIdToken();assert.equal(s.getStoredGoogleIdToken(),null);});

test('OAuth callback stays on the origin that stores its nonce',()=>{const s=setup();s.beginGoogleLogin();assert.equal(new URL(s.window.location.href).searchParams.get('redirect_uri'),'https://example.test/auth/callback');});

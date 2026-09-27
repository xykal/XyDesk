import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {readFileSync} from 'node:fs';
import {transformWithOxc} from 'vite';
const source=readFileSync(new URL('../src/session_restore.ts',import.meta.url),'utf8').replace(/^export /gm,'')+'\nexports.api={readDestination,rememberDestination,forgetDestination,sessionPath,SESSION_RESTORE_KEY};';
const {code}=await transformWithOxc(source,'restore.ts');
function setup(){const exports={},data=new Map();vm.runInNewContext(code,{exports});return {...exports.api,data,storage:{getItem:k=>data.get(k)||null,setItem:(k,v)=>data.set(k,v),removeItem:k=>data.delete(k)}};}
const fragment='#session/'+'a'.repeat(64);
test('restore persists destination, never credentials; same account only',()=>{const s=setup();assert.equal(s.rememberDestination(s.storage,'123456789',fragment,'member:1',100),true);const value=s.readDestination(s.storage,'member:1',101);assert.equal(value.deviceId,'123456789');assert.equal(value.fragment,fragment);assert.deepEqual(Object.keys(value).sort(),['deviceId','fragment','savedAt','scope','version']);assert.equal(s.readDestination(s.storage,'guest',101),null);});
test('future, malformed and unavailable storage do not restore',()=>{const s=setup();s.rememberDestination(s.storage,'123456789',fragment,'guest',100);assert.equal(s.readDestination(s.storage,'guest',99),null);assert.ok(s.readDestination(s.storage,'guest',8*86400000));assert.equal(s.readDestination({getItem(){throw Error();}},'guest'),null);s.storage.setItem(s.SESSION_RESTORE_KEY,'broken');assert.equal(s.readDestination(s.storage,'guest'),null);});
test('disconnect clears own tab destination, not another tab',()=>{const s=setup();s.rememberDestination(s.storage,'123456789',fragment,'guest',100);s.forgetDestination(s.storage,'#session/'+'b'.repeat(64));assert.ok(s.readDestination(s.storage,'guest',101));s.forgetDestination(s.storage,fragment);assert.equal(s.readDestination(s.storage,'guest',101),null);});
test('session route is bookmarkable; secrets and invalid device identifiers cannot become fragments',()=>{const s=setup();assert.equal(s.sessionPath('123456789',fragment),'/session/123456789'+fragment);assert.equal(s.sessionPath('123456789','#token=secret'),'/session/123456789');assert.equal(s.sessionPath('../evil'),'/session');});

test('malformed metadata cannot inject a non-string device or extra credentials',()=>{const s=setup();s.storage.setItem(s.SESSION_RESTORE_KEY,JSON.stringify({version:1,deviceId:123456789,fragment,scope:'guest',savedAt:100}));assert.equal(s.readDestination(s.storage,'guest',101),null);s.storage.setItem(s.SESSION_RESTORE_KEY,JSON.stringify({version:1,deviceId:'123456789',fragment,scope:'guest',savedAt:100,token:'fixture-not-real'}));assert.equal(s.readDestination(s.storage,'guest',101).token,undefined);});

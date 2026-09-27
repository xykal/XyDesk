import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {readFileSync} from 'node:fs';
import {transformWithOxc} from 'vite';
const {code}=await transformWithOxc(readFileSync(new URL('../src/request.ts',import.meta.url),'utf8').replace(/^export /gm,'')+'\nexports.request=request;','request.ts');
function load(fetch){const exports={};vm.runInNewContext(code,{exports,fetch,AbortController,DOMException,setTimeout,clearTimeout});return exports.request;}
test('deadline includes a hanging response body',async()=>{let signal;const request=load(async(_url,init)=>{signal=init.signal;return {};});await assert.rejects(request('/test',{},()=>new Promise(()=>{}),undefined,5),e=>e.name==='TimeoutError');assert.equal(signal.aborted,true);});
test('cancel settles even when transport does not reject itself',async()=>{const request=load(()=>new Promise(()=>{})),abort=new AbortController();const pending=request('/test',{},async()=>1,abort.signal);abort.abort();await assert.rejects(pending,e=>e.name==='AbortError');});
test('already cancelled attempts do not call fetch',async()=>{let calls=0;const request=load(async()=>{calls++;return {};});const abort=new AbortController();abort.abort();await assert.rejects(request('/test',{},async()=>1,abort.signal));assert.equal(calls,0);});

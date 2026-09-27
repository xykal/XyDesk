import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {readFileSync} from 'node:fs';
import {transformWithOxc} from 'vite';
const {code}=await transformWithOxc(readFileSync(new URL('../src/connection_attempt.ts',import.meta.url),'utf8').replace(/^export /gm,'')+'\nexports.ConnectionAttempt=ConnectionAttempt;','attempt.ts');
function setup(){const exports={},timers=new Map();let id=0;vm.runInNewContext(code,{exports,AbortController,setTimeout:fn=>{timers.set(++id,fn);return id;},clearTimeout:key=>timers.delete(key)});return {scope:new exports.ConnectionAttempt(),timers};}
test('manual connect aborts old request and deletes its automatic retry',()=>{const {scope,timers}=setup();const old=scope.begin();let calls=0;scope.schedule(old,()=>calls++,10);const stale=[...timers.values()][0];const next=scope.begin();assert.equal(old.aborted,true);assert.equal(timers.size,0);stale();assert.equal(calls,0);assert.equal(scope.isCurrent(next),true);});
test('disconnect prevents delayed work and only the latest retry survives',()=>{const {scope,timers}=setup();const signal=scope.begin();scope.schedule(signal,()=>{},1);scope.schedule(signal,()=>{},2);assert.equal(timers.size,1);scope.cancel();assert.equal(timers.size,0);assert.equal(signal.aborted,true);scope.schedule(signal,()=>{},1);assert.equal(timers.size,0);});

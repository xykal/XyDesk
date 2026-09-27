import test from 'node:test';
import assert from 'node:assert/strict';
let count=0;
async function fixture(data,run){
 const original=Object.getOwnPropertyDescriptor(globalThis,'navigator');
 Object.defineProperty(globalThis,'navigator',{configurable:true,value:{userAgentData:data}});
 try{const module=await import(`../src/device_label.ts?fixture=${++count}`);await Promise.resolve();await run(module.controllerDisplayLabel);}
 finally{if(original)Object.defineProperty(globalThis,'navigator',original);else delete globalThis.navigator;}
}
test('optional device model only enriches display name',async()=>{
 await fixture({getHighEntropyValues:async hints=>{assert.deepEqual(hints,['model']);return {model:'23021RAAEG'};}},label=>assert.equal(label('Operator'),'23021RAAEG · Operator'));
});
test('unsupported, withheld, generic and rejected metadata keep existing fallback',async()=>{
 for(const value of [undefined,{getHighEntropyValues:async()=>({model:''})},{getHighEntropyValues:async()=>({model:'K'})},{getHighEntropyValues:async()=>{throw Error('blocked');}}])await fixture(value,label=>assert.equal(label('Operator'),'Operator'));
});
test('pending model lookup never blocks pairing label and label stays bounded',async()=>{
 await fixture({getHighEntropyValues:()=>new Promise(()=>{})},label=>assert.equal(label(undefined),undefined));
 await fixture({getHighEntropyValues:async()=>({model:'Redmi Note 12'})},label=>assert.equal(label('x'.repeat(80)).length,48));
});

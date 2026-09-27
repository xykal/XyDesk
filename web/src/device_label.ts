// Optional display-only metadata. Never awaited by pairing, streaming or input.
// No serial, IMEI, fingerprint or third-party lookup. Browsers may return nothing.
let model = '';
type ModelNavigator = Navigator & {userAgentData?: {getHighEntropyValues?: (hints:string[])=>Promise<{model?:string}>}};
if(typeof navigator!=='undefined'){
 const data=(navigator as ModelNavigator).userAgentData;
 try{void data?.getHighEntropyValues?.(['model']).then(value=>{
  const candidate=value.model?.trim();
  if(candidate&&candidate!=='K'&&candidate.length<=40&&!/[\x00-\x1f\x7f]/.test(candidate))model=candidate;
 }).catch(()=>{});}catch{/* unsupported/blocked: keep existing label */}
}
export function controllerDisplayLabel(fallback:string|undefined):string|undefined{
 return model?(fallback?`${model} · ${fallback}`:model).slice(0,48):fallback;
}

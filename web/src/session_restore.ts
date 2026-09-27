// Hanya tujuan/tampilan sesi. Tidak pernah menyimpan password atau bearer.
export const SESSION_RESTORE_KEY = 'xydesk.session.destination.v1';
export type SessionDestination = {version:1;deviceId:string;fragment:string;scope:string;savedAt:number};
export function sessionPath(deviceId:string, fragment=''):string {
  if(!/^\d{9}$/.test(deviceId)) return '/session';
  return `/session/${deviceId}` + (/^#session\/[0-9a-f]{64}$/.test(fragment)?fragment:'');
}
export function readDestination(storage:Pick<Storage,'getItem'>, scope:string|null, now=Date.now()):SessionDestination|null {
  try {
    const value=JSON.parse(storage.getItem(SESSION_RESTORE_KEY)||'null');
    if(!scope||!value||value.version!==1||value.scope!==scope||typeof value.deviceId!=='string'||typeof value.fragment!=='string'||!/^\d{9}$/.test(value.deviceId)||!/^#session\/[0-9a-f]{64}$/.test(value.fragment)||!Number.isFinite(value.savedAt)||value.savedAt<0||value.savedAt>now)return null;
    return {version:1,deviceId:value.deviceId,fragment:value.fragment,scope,savedAt:value.savedAt};
  } catch { return null; }
}
export function rememberDestination(storage:Pick<Storage,'setItem'>, deviceId:string, fragment:string, scope:string|null, now=Date.now()):boolean {
  if(!scope||!/^\d{9}$/.test(deviceId)||!/^#session\/[0-9a-f]{64}$/.test(fragment))return false;
  try {storage.setItem(SESSION_RESTORE_KEY,JSON.stringify({version:1,deviceId,fragment,scope,savedAt:now}));return true;}catch{return false;}
}
export function forgetDestination(storage:Pick<Storage,'getItem'|'removeItem'>, fragment?:string) {
  try {const value=JSON.parse(storage.getItem(SESSION_RESTORE_KEY)||'null');if(!fragment||value?.fragment===fragment)storage.removeItem(SESSION_RESTORE_KEY);}catch{}
}

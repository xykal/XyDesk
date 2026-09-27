export const PUBLIC_ORIGIN='https://www.xydesk.my.id';
export const REMOTE_ORIGIN='https://remote.xydesk.my.id';
export function remotePath(path:string):boolean {
  return /^\/(connect|devices|history|session|controls)(\/|$|[?#])/.test(path);
}
export function routeHref(path:string, hostname=window.location.hostname):string {
  if(!path.startsWith('/')||path.startsWith('//'))return PUBLIC_ORIGIN+'/';
  if(!['www.xydesk.my.id','remote.xydesk.my.id','app.xydesk.my.id','xydesk.my.id'].includes(hostname))return path;
  return (remotePath(path)?REMOTE_ORIGIN:PUBLIC_ORIGIN)+path;
}

// Hanya route halaman; aset pada setiap origin tetap dapat dilayani.
export function pageRedirect(url) {
  const host=url.hostname,path=url.pathname.replace(/\/$/,'')||'/';
  const remote=/^\/(connect|devices|history|session|controls)(\/|$)/.test(path);
  const publicPage=path==='/'||/^\/(download|legal|news|n|billing)(\/|$)/.test(path);
  let target='';
  if(host==='app.xydesk.my.id'||host==='xydesk.my.id')target=remote?'remote.xydesk.my.id':'www.xydesk.my.id';
  else if(host==='www.xydesk.my.id'&&remote)target='remote.xydesk.my.id';
  else if(host==='remote.xydesk.my.id'&&publicPage&&path!=='/')target='www.xydesk.my.id';
  // /auth/callback tetap di origin www/remote yang menyimpan nonce OAuth.
  if(!target&&['www.xydesk.my.id','remote.xydesk.my.id'].includes(host)&&url.protocol==='http:')target=host;
  if(!target)return null;
  const destination=new URL(url);destination.hostname=target;destination.protocol='https:';destination.port='';
  return destination.href;
}

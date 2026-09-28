import {routeHref} from './site_routes';
import {readDestination, sessionPath} from './session_restore';
import {browserAccessScope} from './guest_access';
import { isSessionFragment } from './session_runtime';
import { useCallback, useEffect, useState } from 'react';



export type StaticRoute = '/' | '/connect' | '/download' | '/legal' | '/news' | '/billing' | '/history' | '/devices' | '/controls' | '/auth/callback';
export type Route = StaticRoute | NewsDetailRoute | {page: 'device'|'session'; deviceId:string};
export interface NewsDetailRoute {
  page: 'news-detail';
  slug: string;
}
export type AuthStep = 'closed' | 'login' | 'otp';

// Blok gambar di badan berita: baris sendiri berbentuk
// ![keterangan](https://app.xydesk.my.id/news/shots/....jpg).
// Hanya gambar dari domain sendiri yang dirender — sesuai docs/NEWS_STYLE.md;
// baris lain tetap tampil sebagai paragraf biasa.
export const NEWS_IMAGE_BLOCK = /^!\[([^\]]*)\]\((https:\/\/((app|www|remote)\.)?xydesk\.my\.id\/[^)\s]+)\)$/;

export const TOKEN_KEY = 'xydesk.web.jwt';
export const GUEST_TOKEN_KEY = 'xydesk.web.guestJwt';
export const LAST_HOST_KEY = 'xydesk.web.lastHost';
export const RELEASE_BASE =
  'https://github.com/xykal/XyDesk/releases/latest/download';
export const WHATSAPP_CHANNEL =
  'https://whatsapp.com/channel/0029VbB7nwuJZg3ym6UQ4Z1L';

export const downloads = [
  {
    platform: 'Android',
    architecture: 'ARM64',
    file: 'XyDesk-Android-arm64-v8a.apk',
    note: 'Rekomendasi untuk hampir semua HP modern',
  },
  {
    platform: 'Android',
    architecture: 'ARMv7 32-bit',
    file: 'XyDesk-Android-armeabi-v7a.apk',
    note: 'Untuk perangkat Android lama',
  },
  {
    platform: 'Windows',
    architecture: 'x64',
    file: 'XyDesk-x64.exe',
    note: 'Aplikasi terpadu Connect + Host untuk Intel atau AMD',
  },
  {
    platform: 'Windows',
    architecture: 'Arm64',
    file: 'XyDesk-arm64.exe',
    note: 'Aplikasi terpadu Connect + Host untuk Windows on Arm',
  },
] as const;

export function routePath(r: Route): string {
  if (typeof r === 'string') return r;
  return r.page==='news-detail'?`/news/${r.slug}`:r.page==='device'?`/devices/${r.deviceId}`:sessionPath(r.deviceId);
}

export function currentRoute(): Route {
  const raw = window.location.pathname.replace(/\/$/, '') || '/';
  const linkedDevice = raw==='/connect' ? new URLSearchParams(window.location.search).get('device') : null;
  if(linkedDevice && /^\d{9}$/.test(linkedDevice))return {page:'session',deviceId:linkedDevice};
  const session=/^\/session(?:\/(\d{9}))?$/.exec(raw);
  if(session)return {page:'session',deviceId:session[1]||''};
  const device=/^\/devices\/(\d{9})$/.exec(raw);
  if(device)return {page:'device',deviceId:device[1]};
  let restored:ReturnType<typeof readDestination>=null;try{restored=readDestination(localStorage,browserAccessScope());}catch{}
  if(raw.startsWith('/session/'))return {page:'session',deviceId:''};
  if(raw.startsWith('/devices/'))return '/devices';
  if(raw==='/'&&window.location.hostname==='remote.xydesk.my.id') {
    if(restored){window.history.replaceState({},'',sessionPath(restored.deviceId,restored.fragment));return {page:'session',deviceId:restored.deviceId};}
    return '/devices';
  }
  if(['/connect','/history'].includes(raw)&&isSessionFragment(window.location.hash)) {
    let last='';try{last=(localStorage.getItem(LAST_HOST_KEY)||'').replace(/[^0-9]/g,'');}catch{}
    return {page:'session',deviceId:restored?.deviceId||(/^\d{9}$/.test(last)?last:'')};
  }

  // Share short link /n/:slug (news.xydesk.my.id/n/:slug) — also handle locally for OG preview / direct link
  if (raw.startsWith('/n/')) {
    const slug = decodeURIComponent(raw.slice('/n/'.length));
    if (slug) return { page: 'news-detail', slug };
    return '/news';
  }
  if (raw.startsWith('/news/')) {
    const slug = decodeURIComponent(raw.slice('/news/'.length));
    if (slug) return { page: 'news-detail', slug };
    return '/news';
  }
  switch (raw) {
    case '/devices': return '/devices';
    case '/auth/callback': return '/auth/callback';
    case '/connect':
      return '/connect';
    case '/history':
      return '/history';
    case '/download':
      return '/download';
    case '/legal':
      return '/legal';
    case '/news':
      return '/news';
    case '/billing':
      return '/billing';
    case '/controls':
      return '/controls';
    case '/n':
      return '/news';
    default:
      return '/';
  }
}

export function useRoute(): [Route, (r: Route) => void] {
  const [route, setRoute] = useState<Route>(currentRoute);
  useEffect(() => {
    const onChange = () => {
      setRoute(currentRoute());
      window.scrollTo(0, 0);
    };
    window.addEventListener('popstate', onChange);
    window.addEventListener('hashchange', onChange);
    return () => {window.removeEventListener('popstate', onChange);window.removeEventListener('hashchange', onChange);};
  }, []);
  const navigate = useCallback((r: Route) => {
    const href=routeHref(routePath(r));
    if(new URL(href,window.location.origin).origin!==window.location.origin){window.location.assign(href);return;}
    window.history.pushState({}, '', routePath(r));
    setRoute(r);
    window.scrollTo(0, 0);
  }, []);
  return [route, navigate];
}


// Kunci akses sementara sebelum rilis. Hanya hash yang disimpan di repo;
// kuncinya dipegang operator. Kosongkan ACCESS_KEY_SHA256 untuk membuka web.
export const ACCESS_KEY_SHA256 = 'acc6fc1cb752f51c5b7bf9e201e50f6aa4851510c109b15f994aea6d51c3d9a2';
export const ACCESS_PARAM = 'akses';
export const ACCESS_STORAGE = 'xydesk.access';

const sha256 = async (s: string) => {
  const buf = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(s));
  return Array.from(new Uint8Array(buf)).map((b) => b.toString(16).padStart(2, '0')).join('');
};

// Dipanggil sebelum render: true bila boleh masuk.
export async function checkAccess(): Promise<boolean> {
  if (!ACCESS_KEY_SHA256) return true;
  // Uji otomatis (Playwright/CI shots) lewat; ini kunci sementara, bukan pengaman data.
  if (navigator.webdriver) return true;
  const url = new URL(window.location.href);
  const fromUrl = url.searchParams.get(ACCESS_PARAM);
  if (fromUrl) {
    if ((await sha256(fromUrl)) === ACCESS_KEY_SHA256) {
      localStorage.setItem(ACCESS_STORAGE, ACCESS_KEY_SHA256);
      url.searchParams.delete(ACCESS_PARAM);
      history.replaceState(null, '', url.toString());
      return true;
    }
    return false;
  }
  return localStorage.getItem(ACCESS_STORAGE) === ACCESS_KEY_SHA256;
}

import { XyDeskMark } from './xydesk_mark';

// Halaman yang dilihat publik selama web masih dikunci sebelum rilis.
export function ComingSoon() {
  return (
    <main className="coming-soon">
      <XyDeskMark size={72} variant="splash" />
      <h1>XyDesk</h1>
      <p>Belum dirilis. Kami sedang menyiapkan versi publik pertama.</p>
      <small>XyVerse Technology Global</small>
    </main>
  );
}

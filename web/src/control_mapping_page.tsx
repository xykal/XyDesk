import { Route } from './app_routes';
import { CustomControlMapping } from './control_mapping';
import { useState } from 'react';


export function ControlMappingPage({ navigate }: { navigate: (r: Route) => void }) {
  const [trackpad, setTrackpad] = useState(false);
  return (
    <main className="content-page control-mapping-page">
      <p className="eyebrow">CONTROL MAPPING</p>
      <h1>Rancang kontrol. Mainkan caramu.</h1>
      <p className="page-lead">
        Buka editor fullscreen, pilih keyboard, stick, mouse atau kombinasi.
        Ketuk kontrol untuk membuka properti di samping. Layout portrait dan landscape disimpan terpisah.
      </p>
      <div className="mapping-page-card">
        <div className="mapping-page-toolbar">
          <div>
            <strong>Pratinjau layout</strong>
            <span>Mode: <b>{trackpad ? 'Trackpad' : 'Langsung'}</b> · Tekan <b>Atur kontrol</b> untuk membuka editor fullscreen.</span>
          </div>
          <button className="btn ghost" type="button" onClick={() => navigate('/connect')}>Buka Connect</button>
        </div>
        <div className="mapping-page-stage video-surface" aria-label="Pratinjau control mapping">
          <div className="mapping-page-desktop" aria-hidden="true">
            <span>Pratinjau desktop host</span>
            <i /><i /><i />
          </div>
          <CustomControlMapping onToggleMode={() => setTrackpad(value => !value)} send={() => {}} />
        </div>
        <p className="mapping-page-note">
          Saat tersambung, tombol yang sama mengirim input secara langsung ke PC. Keyboard virtual
          tersedia dari tombol keyboard di HUD dan tetap membedakan Shift, Caps Lock, huruf besar,
          serta huruf kecil.
        </p>
      </div>
    </main>
  );
}


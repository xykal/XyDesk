import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';
import {AppDialogs} from './app_dialog';
import { ComingSoon } from './coming_soon';
import { checkAccess } from './config/access';
import './style.css';
import './device.css';
import './session.css';

const root = createRoot(document.getElementById('root')!);
void checkAccess().then((ok) => {
  root.render(
    <StrictMode>
      {ok ? (
        <>
          <AppDialogs />
          <App />
        </>
      ) : (
        <ComingSoon />
      )}
    </StrictMode>,
  );
});

document.addEventListener('contextmenu',e=>{if(e.target instanceof HTMLImageElement)e.preventDefault();});
document.addEventListener('dragstart',e=>{if(e.target instanceof HTMLImageElement)e.preventDefault();});

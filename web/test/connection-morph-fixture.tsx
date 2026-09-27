import React from 'react';
import {createRoot} from 'react-dom/client';
import {ConnectionMorph} from '../src/connection_morph';
export function mountMorph(){
 const mount=document.createElement('div');mount.id='morph-fixture';
 mount.style.cssText='position:fixed;inset:0;z-index:9999;background:#100b17;display:grid;place-items:center';
 document.body.append(mount);createRoot(mount).render(<ConnectionMorph/>);
}

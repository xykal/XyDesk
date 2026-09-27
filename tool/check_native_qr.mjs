// Actions-only: decode the C++ encoder output with the web's independent scanner.
import {createRequire} from 'node:module';import {readFileSync} from 'node:fs';import {resolve} from 'node:path';import assert from 'node:assert/strict';
const require=createRequire(resolve('web/package.json'));const jsQR=require('jsqr');
const {link,size,modules}=JSON.parse(readFileSync(process.argv[2],'utf8'));const scale=8,width=(size+8)*scale;const rgba=new Uint8ClampedArray(width*width*4).fill(255);
for(let y=0;y<size;y++)for(let x=0;x<size;x++)if(modules[y*size+x])for(let dy=0;dy<scale;dy++)for(let dx=0;dx<scale;dx++){const i=(((y+4)*scale+dy)*width+(x+4)*scale+dx)*4;rgba[i]=rgba[i+1]=rgba[i+2]=0;}
const decoded=jsQR(rgba,width,width);assert.equal(decoded?.data,link);assert.equal(link,'https://remote.xydesk.my.id/connect?device=123456789');assert.equal(new URL(link).searchParams.size,1);console.log('Native QR roundtrip: ID-only link decoded with jsQR');

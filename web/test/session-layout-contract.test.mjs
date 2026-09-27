import test from 'node:test';import assert from 'node:assert/strict';import {readFileSync} from 'node:fs';
const app=readFileSync(new URL('../src/App.tsx',import.meta.url),'utf8');
test('rail collapse cannot disable or unmount screen mapping controls',()=>{
 assert.match(app,/onToggleCollapsed=\{\(\) => setRailHidden\(v=>!v\)\}/);
 assert.match(app,/padOpen && <CustomControlMapping/);
 assert.doesNotMatch(app,/padOpen && !railHidden/);
});
test('settings and statistics share one drawer without turning off mapping',()=>{
 assert.match(app,/onPanel=\{\(\) => \{setPanelOpen\(v=>!v\);\}\}/);
 assert.match(app,/activeTab=\{panelTab\}/);
 assert.doesNotMatch(app,/setStatisticsOpen/);
});

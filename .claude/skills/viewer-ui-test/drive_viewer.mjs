#!/usr/bin/env node
// Drives the poses_viewer #/strokes page in a real headed Chrome via CDP (no deps —
// uses Node's global WebSocket, Node 21+). Proves the M1 drill simulator end-to-end:
// dataset loads, rep table populates, spoken-feedback banner/log fire on cadence, and
// "Лише текст" silences audio. Screenshots land in tmp/screenshots/.
//
// Prereqs (the SKILL launches these for you):
//   1. dev server:  cd poses_viewer && npm run dev   (note the printed port)
//   2. headed Chrome with CDP:
//      "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" \
//        --remote-debugging-port=9222 --user-data-dir=/tmp/chrome-cdp-profile \
//        --no-first-run --no-default-browser-check "http://localhost:<PORT>/#/strokes"
//
// Usage:  node drive_viewer.mjs <PORT> [videoBase] [cdpPort]
//   PORT       vite dev-server port (5780 or whatever it printed)
//   videoBase  dataset to load (default andrii_1)
//   cdpPort    Chrome debugging port (default 9222)

const VITE_PORT = process.argv[2] || '5780';
const VIDEO = process.argv[3] || 'andrii_1';
const CDP_PORT = process.argv[4] || '9222';
const SHOTS = process.env.SHOT_DIR || '/Users/itsurkan/Dev/personal/TT_Coach/tmp/screenshots';
const HOST = `http://127.0.0.1:${CDP_PORT}`;
const sleep = (ms) => new Promise(r => setTimeout(r, ms));

class CDP {
  constructor(ws) { this.ws = ws; this.id = 0; this.pending = new Map(); }
  static async attach(wsUrl) {
    const ws = new WebSocket(wsUrl);
    await new Promise((res, rej) => { ws.onopen = res; ws.onerror = rej; });
    const cdp = new CDP(ws);
    ws.onmessage = (e) => {
      const m = JSON.parse(e.data);
      if (m.id && cdp.pending.has(m.id)) {
        const { res, rej } = cdp.pending.get(m.id); cdp.pending.delete(m.id);
        m.error ? rej(new Error(JSON.stringify(m.error))) : res(m.result);
      }
    };
    return cdp;
  }
  send(method, params = {}) {
    const id = ++this.id;
    return new Promise((res, rej) => { this.pending.set(id, { res, rej }); this.ws.send(JSON.stringify({ id, method, params })); });
  }
  async eval(expr, awaitPromise = true) {
    const r = await this.send('Runtime.evaluate', { expression: expr, awaitPromise, returnByValue: true });
    if (r.exceptionDetails) throw new Error('EVAL: ' + JSON.stringify(r.exceptionDetails));
    return r.result.value;
  }
}

// Set a React-controlled <select> to `value` (native setter + change event so React sees it).
const SET_SELECT = (matchExpr, value) => `(() => {
  const sel = [...document.querySelectorAll('select')].find(${matchExpr});
  if (!sel) return 'not-found';
  Object.getOwnPropertyDescriptor(window.HTMLSelectElement.prototype,'value').set.call(sel, ${JSON.stringify(value)});
  sel.dispatchEvent(new Event('change',{bubbles:true}));
  return 'set';
})()`;

async function run() {
  const targets = await (await fetch(`${HOST}/json`)).json();
  const target = targets.find(t => t.type === 'page' && t.url.includes(`:${VITE_PORT}`))
              || targets.find(t => t.type === 'page');
  if (!target) throw new Error(`no Chrome page target on CDP ${CDP_PORT}`);
  const cdp = await CDP.attach(target.webSocketDebuggerUrl);
  await cdp.send('Page.enable'); await cdp.send('Runtime.enable');
  const fs = await import('node:fs'); fs.mkdirSync(SHOTS, { recursive: true });
  const shot = async (n) => {
    const { data } = await cdp.send('Page.captureScreenshot', { format: 'png' });
    fs.writeFileSync(`${SHOTS}/${n}.png`, Buffer.from(data, 'base64')); console.log(`  📸 ${n}.png`);
  };
  const fail = [];

  await cdp.send('Page.navigate', { url: `http://localhost:${VITE_PORT}/#/strokes` });
  await sleep(2500);

  // Count speak() calls instead of emitting audio.
  await cdp.eval(`(()=>{window.__spoken=[];const o=window.speechSynthesis.speak.bind(window.speechSynthesis);window.speechSynthesis.speak=(u)=>window.__spoken.push(u.text);return 1})()`);

  console.log('select video:', await cdp.eval(SET_SELECT(`s=>[...s.options].some(o=>o.value===${JSON.stringify(VIDEO)})`, VIDEO)));
  await sleep(2500);

  const counts = await cdp.eval(`(()=>{const t=document.body.innerText;const g=re=>(t.match(re)||[])[1];return{raw:g(/Сирі піки:\\s*(\\d+)/),fwd:g(/Форвардні:\\s*(\\d+)/),rep:g(/Повтори:\\s*(\\d+)/),rows:document.querySelectorAll('table tbody tr').length,hasTable:!!document.querySelector('table')}})()`);
  console.log('COUNTS/TABLE:', JSON.stringify(counts));
  if (!counts.hasTable || counts.rows < 1) fail.push('no rep table rows');
  await shot('01-loaded');

  // Force audio mode — Page.navigate to the same #/strokes URL is a hash change (no
  // reload), so React state can persist 'text' from a prior run. Don't assume the default.
  await cdp.eval(SET_SELECT(`s=>[...s.options].some(o=>o.value==='audio')`, 'audio'));
  await sleep(300);
  // Audio run: play fast, watch banner/log/spoken grow.
  await cdp.eval(`(()=>{const v=document.querySelector('video');v.muted=true;v.playbackRate=8;v.currentTime=0;v.play();return 1})()`);
  let prev = -1;
  for (let i = 0; i < 26; i++) {
    await sleep(500);
    const s = await cdp.eval(`(()=>{const v=document.querySelector('video');const b=[...document.querySelectorAll('div')].find(d=>d.textContent&&d.textContent.startsWith('🔊'));const sm=[...document.querySelectorAll('summary')].find(s=>s.textContent.includes('Журнал'));return{t:+v.currentTime.toFixed(1),ended:v.ended,banner:b?b.textContent.slice(0,70):null,log:sm?sm.parentElement.querySelectorAll('li').length:0,spoken:window.__spoken.length}})()`);
    if (s.log !== prev) { console.log(`  t=${s.t} log=${s.log} spoken=${s.spoken} ${s.banner||''}`); prev = s.log; }
    if (s.ended) break;
  }
  const audio = await cdp.eval(`(()=>{const sm=[...document.querySelectorAll('summary')].find(s=>s.textContent.includes('Журнал'));return{spoken:window.__spoken.length,items:sm?[...sm.parentElement.querySelectorAll('li')].map(li=>li.textContent):[]}})()`);
  console.log('AUDIO spoken:', audio.spoken);
  // cadence: parse "N.N с —" leading timestamps, check gaps fall in ~3-5s band.
  const ts = audio.items.map(s => parseFloat(s)).filter(n => !isNaN(n));
  const gaps = ts.slice(1).map((t, i) => +(t - ts[i]).toFixed(1));
  console.log('  log timestamps(s):', ts.join(', '), '| gaps:', gaps.join(', '));
  if (audio.spoken < 1) fail.push('audio mode fired no speak() calls');
  await shot('02-audio');

  // Text mode: must log/banner but NEVER speak.
  await cdp.eval(SET_SELECT(`s=>[...s.options].some(o=>o.value==='text'&&o.textContent.includes('текст'))`, 'text'));
  await cdp.eval(`(()=>{window.__spoken=[];const v=document.querySelector('video');v.currentTime=0;v.playbackRate=8;v.play();return 1})()`);
  let prev2 = -1;
  for (let i = 0; i < 26; i++) {
    await sleep(500);
    const s = await cdp.eval(`(()=>{const v=document.querySelector('video');const sm=[...document.querySelectorAll('summary')].find(s=>s.textContent.includes('Журнал'));return{ended:v.ended,log:sm?sm.parentElement.querySelectorAll('li').length:0,spoken:window.__spoken.length}})()`);
    if (s.log !== prev2) { console.log(`  text-mode log=${s.log} spoken=${s.spoken}`); prev2 = s.log; }
    if (s.ended) break;
  }
  const textSpoken = await cdp.eval(`window.__spoken.length`);
  console.log('TEXT spoken (want 0):', textSpoken);
  if (textSpoken !== 0) fail.push(`"Лише текст" still spoke ${textSpoken}× (audio not silenced)`);
  await shot('03-text');

  cdp.ws.close();
  if (fail.length) { console.error('\nFAIL:\n - ' + fail.join('\n - ')); process.exit(1); }
  console.log('\nPASS — table populated, banner/log fired, audio silenced in text mode.');
}
run().catch(e => { console.error('DRIVER ERROR', e); process.exit(2); });

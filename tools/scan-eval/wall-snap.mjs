// wall-snap.mjs — the MIRROR of shared/.../scan/WallSnap.kt, plus a corpus runner that draws the result.
//
// WHY: the wall snap reads the picture, and the pictures of the recorded corpus live beside this
// repository on the owner's machine (Documents/Sample Floor plans), not in it. CI proves the snap on
// the bundled fixture with exact truth (WallSnapTest); THIS is how it is checked against every real
// sheet: the same arithmetic, the same constants, run over every recording that has its image, with
// an overlay per sheet to LOOK at (red = as read, green = snapped, blue = hand-marked truth where one
// exists in truth-rooms.json) and a table of overlap-with-truth before -> after.
//
// ⚠ Keep in step with WallSnap.kt: every constant in P and every rule in snapAll is the Kotlin,
// line for line. A change to one without the other is a mirror that lies.
//
// Run:   cd tools/scan-eval && npm install          # once: jimp, the pure-JS image codec
//        node wall-snap.mjs                          # every recording with an image beside it
//        node wall-snap.mjs --only=plan-018          # ids containing the text
//        node wall-snap.mjs --plans=/path/to/images  # another folder of sheet images
// Output: tools/scan-eval/out/wall-snap/<id>.png + the table on stdout.
import J from 'jimp';
export const P = { reachRoom: 0.30, reachFrame: 0.06, minReach: 6, inset: 0.12, minScore: 0.6, thickShare: 0.45, minThick: 2, maxThickShare: 0.06, greyShare: 0.7, satMax: 40, darkCore: 0.5, inkDrop: 35, floorDrop: 30, proximity: 0.35, shrinkMin: 0.5, growMax: 1.5, frameReach: 0.08, extend: 0.25, extMin: 0.6 };

export async function loadImg(path) {
  const im = await J.read(path); const W = im.bitmap.width, H = im.bitmap.height, d = im.bitmap.data;
  const luma = new Uint8Array(W * H), sat = new Uint8Array(W * H);
  for (let i = 0, p = 0; i < luma.length; i++, p += 4) { const r = d[p], g = d[p + 1], b = d[p + 2]; luma[i] = Math.floor((r * 299 + g * 587 + b * 114) / 1000); sat[i] = Math.max(r, g, b) - Math.min(r, g, b); }
  return { W, H, luma, sat, im, white: percentile(luma, 0, W * H, 0.95) };
}
function percentile(arr, from, to, q) { const h = new Uint32Array(256); let n = 0; for (let i = from; i < to; i++) { h[arr[i]]++; n++; } if (!n) return 255; let acc = 0; const target = Math.ceil(q * n); for (let v = 0; v < 256; v++) { acc += h[v]; if (acc >= target) return v; } return 255; }
const idx = (L, x, y) => (x < 0 || y < 0 || x >= L.W || y >= L.H) ? -1 : y * L.W + x;
const ink = (L, i, bg) => i >= 0 && L.luma[i] < bg - P.inkDrop && L.sat[i] < P.satMax;
const darkLevel = (L) => Math.max(70, Math.min(140, Math.floor(0.55 * L.white)));

/** The floor level for one edge: p90 of the luma over the whole search region, floored at white-30. */
function regionBg(L, vertical, from, to, a0, a1, cap) {
  const h = new Uint32Array(256); let n = 0;
  const c0 = Math.max(0, from - cap), c1 = Math.min((vertical ? L.W : L.H) - 1, to + cap);
  for (let c = c0; c <= c1; c++) for (let a = a0; a < a1; a++) { const i = idx(L, vertical ? c : a, vertical ? a : c); if (i >= 0) { h[L.luma[i]]++; n++; } }
  if (!n) return L.white; let acc = 0; const target = Math.ceil(0.9 * n); let p90 = 255; for (let v = 0; v < 256; v++) { acc += h[v]; if (acc >= target) { p90 = v; break; } }
  return Math.min(L.white, Math.max(L.white - P.floorDrop, p90));
}
function cont(L, vertical, c, a0, a1, bg) { let d = 0, n = 0; for (let a = a0; a < a1; a++) { n++; for (let j = -1; j <= 1; j++) { if (ink(L, idx(L, vertical ? c + j : a, vertical ? a : c + j), bg)) { d++; break; } } } return n ? d / n : 0; }
function runAcross(L, vertical, c, a, cap, bg, dark) {
  const at = (cc) => idx(L, vertical ? cc : a, vertical ? a : cc);
  if (!ink(L, at(c), bg)) return null;
  let lo = c, hi = c; while (c - lo < cap && ink(L, at(lo - 1), bg)) lo--; while (hi - c < cap && ink(L, at(hi + 1), bg)) hi++;
  let isDark = false, grey = 0, n = 0;
  for (let cc = lo; cc <= hi; cc++) { const i = at(cc); n++; if (L.luma[i] < dark) isDark = true; if (L.sat[i] < P.satMax) grey++; }
  return { lo, hi, dark: isDark, grey: grey / n };
}
const pct = (arr, q) => { if (!arr.length) return 0; const s = arr.slice().sort((p, r) => p - r); return s[Math.floor(q * (s.length - 1))]; };
function lineStats(L, vertical, c, a0, a1, cap, bg, dark) {
  const th = [], los = [], his = []; let darkN = 0, greySum = 0, n = 0;
  for (let a = a0; a < a1; a += 2) { const r = runAcross(L, vertical, c, a, cap, bg, dark); if (!r) continue; n++; th.push(r.hi - r.lo + 1); los.push(r.lo); his.push(r.hi); if (r.dark) darkN++; greySum += r.grey; }
  return { thick: pct(th, 0.1), lo: pct(los, 0.5), hi: pct(his, 0.5), dark: n ? darkN / n : 0, grey: n ? greySum / n : 0 };
}
function candidates(L, vertical, edge, a0, a1, reach, lo, hi, cap, dark) {
  const from = Math.max(lo, Math.round(edge - reach)), to = Math.min(hi, Math.round(edge + reach));
  if (to < from) return [];
  const bg = regionBg(L, vertical, from, to, a0, a1, cap);
  const sc = []; for (let c = from; c <= to; c++) sc.push(cont(L, vertical, c, a0, a1, bg));
  const out = []; let s = -1;
  for (let i = 0; i <= sc.length; i++) {
    const strong = i < sc.length && sc[i] >= P.minScore;
    if (strong && s < 0) s = i;
    if (!strong && s >= 0) {
      const c = Math.round(from + (s + i - 1) / 2); const len = a1 - a0;
      const e0 = Math.round(a0 - P.extend * len), e1 = Math.round(a1 + P.extend * len);
      out.push({ c, score: Math.max(...sc.slice(s, i)), ext: cont(L, vertical, c, e0, e1, bg), ...lineStats(L, vertical, c, a0, a1, cap, bg, dark) }); s = -1;
    }
  }
  return out;
}
const isWall = (q, tMin, tMax) => q.thick >= tMin && q.thick <= tMax && q.grey >= P.greyShare && q.dark >= P.darkCore && q.ext >= P.extMin;
function outerWallThickness(L, f, tMax, dark) {
  const found = [];
  const bx0 = Math.round(f.y0 + 0.1 * (f.y1 - f.y0)), bx1 = Math.round(f.y1 - 0.1 * (f.y1 - f.y0)), by0 = Math.round(f.x0 + 0.1 * (f.x1 - f.x0)), by1 = Math.round(f.x1 - 0.1 * (f.x1 - f.x0));
  const rw = P.frameReach * (f.x1 - f.x0), rh = P.frameReach * (f.y1 - f.y0);
  for (const [vertical, edge, a0, a1, reach] of [[true, f.x0, bx0, bx1, rw], [true, f.x1, bx0, bx1, rw], [false, f.y0, by0, by1, rh], [false, f.y1, by0, by1, rh]]) {
    const cs = candidates(L, vertical, edge, a0, a1, reach, 0, (vertical ? L.W : L.H) - 1, tMax, dark).filter((q) => q.grey >= P.greyShare && q.dark >= P.darkCore && q.thick <= tMax && q.score >= 0.5);
    if (cs.length) found.push(Math.max(...cs.map((q) => q.thick)));
  }
  return found.length ? pct(found, 0.5) : null;
}
/** boxes and frame as pixel EDGES {x0,y0,x1,y1} (x1/y1 exclusive). */
export function snapAll(L, boxes, frame, log) {
  const dark = darkLevel(L);
  const tMax = Math.max(6, Math.round(P.maxThickShare * Math.min(frame.x1 - frame.x0, frame.y1 - frame.y0)));
  const per = boxes.map((b) => {
    const w = b.x1 - b.x0, h = b.y1 - b.y0; if (w < 2 || h < 2) return null;
    const cx = (b.x0 + b.x1) / 2, cy = (b.y0 + b.y1) / 2;
    const rx = Math.max(P.minReach, P.reachRoom * w, P.reachFrame * (frame.x1 - frame.x0)), ry = Math.max(P.minReach, P.reachRoom * h, P.reachFrame * (frame.y1 - frame.y0));
    const bx0 = Math.round(b.y0 + P.inset * h), bx1 = Math.round(b.y1 - P.inset * h), by0 = Math.round(b.x0 + P.inset * w), by1 = Math.round(b.x1 - P.inset * w);
    return {
      left: { edge: b.x0, reach: rx, face: 'hi', c: candidates(L, true, b.x0, bx0, bx1, rx, 0, Math.floor(cx) - 1, tMax, dark) },
      right: { edge: b.x1, reach: rx, face: 'lo', c: candidates(L, true, b.x1, bx0, bx1, rx, Math.ceil(cx) + 1, L.W - 1, tMax, dark) },
      top: { edge: b.y0, reach: ry, face: 'hi', c: candidates(L, false, b.y0, by0, by1, ry, 0, Math.floor(cy) - 1, tMax, dark) },
      bottom: { edge: b.y1, reach: ry, face: 'lo', c: candidates(L, false, b.y1, by0, by1, ry, Math.ceil(cy) + 1, L.H - 1, tMax, dark) },
    };
  });
  // the reference thickness: the outer wall, or the thick end of the grey lines beside the rooms — whichever is thicker
  const outer = outerWallThickness(L, frame, tMax, dark);
  const th = []; for (const e of per) if (e) for (const k of Object.values(e)) for (const q of k.c) if (q.grey >= P.greyShare && q.dark >= P.darkCore && q.thick >= 2 && q.thick <= tMax) th.push(q.thick);
  const rooms = th.length >= 4 ? pct(th, 0.9) : null;
  const tRef = Math.max(outer ?? 0, rooms ?? 0) || null; const how = `outer ${outer} rooms ${rooms}`;
  if (tRef == null) { log?.push(`no wall reference: nothing snapped`); return boxes.map((b) => ({ ...b })); }
  const tMin = Math.max(P.minThick, P.thickShare * tRef);
  log?.push(`white ${L.white} dark<${dark} wall ref ${tRef}px (${how}) walls ${tMin.toFixed(1)}..${tMax}px`);
  return boxes.map((b, i) => {
    const e = per[i]; if (!e) return { ...b };
    const pick = {};
    for (const [name, k] of Object.entries(e)) {
      let best = null, bv = -Infinity;
      for (const q of k.c) { if (!isWall(q, tMin, tMax)) continue; const v = q.score - P.proximity * Math.abs(q.c - k.edge) / k.reach; if (v > bv) { bv = v; best = q; } }
      pick[name] = best ? (k.face === 'hi' ? best.hi + 1 : best.lo) : k.edge;
      log?.push(`  ${(b.label || '').padEnd(14)} ${name.padEnd(6)} ${String(k.edge).padStart(5)} -> ${String(pick[name]).padStart(5)}  ` + k.c.map((q) => `[${q.c} s${q.score.toFixed(2)} t${q.thick} g${q.grey.toFixed(2)} d${q.dark.toFixed(2)} e${q.ext.toFixed(2)}${isWall(q, tMin, tMax) ? '*' : ''}]`).join(' '));
    }
    let x0 = pick.left, x1 = pick.right, y0 = pick.top, y1 = pick.bottom;
    const w = b.x1 - b.x0, h = b.y1 - b.y0;
    if (x1 - x0 < P.shrinkMin * w || x1 - x0 > P.growMax * w) { x0 = b.x0; x1 = b.x1; }
    if (y1 - y0 < P.shrinkMin * h || y1 - y0 > P.growMax * h) { y0 = b.y0; y1 = b.y1; }
    return { label: b.label, x0, y0, x1, y1 };
  });
}
export function rect(im, b, color, t = 3) { const W = im.bitmap.width, H = im.bitmap.height; const put = (x, y) => { if (x >= 0 && y >= 0 && x < W && y < H) im.setPixelColor(color, Math.round(x), Math.round(y)); }; for (let k = 0; k < t; k++) { for (let x = b.x0; x <= b.x1; x++) { put(x, b.y0 + k); put(x, b.y1 - k); } for (let y = b.y0; y <= b.y1; y++) { put(b.x0 + k, y); put(b.x1 - k, y); } } }
export const RED = J.rgbaToInt(220, 30, 30, 255), GREEN = J.rgbaToInt(20, 160, 60, 255), BLUE = J.rgbaToInt(30, 60, 220, 255);
export const iou = (a, b) => { const ix = Math.max(0, Math.min(a.x1, b.x1) - Math.max(a.x0, b.x0)), iy = Math.max(0, Math.min(a.y1, b.y1) - Math.max(a.y0, b.y0)); const i = ix * iy; return i / ((a.x1 - a.x0) * (a.y1 - a.y0) + (b.x1 - b.x0) * (b.y1 - b.y0) - i); };


// ---- the corpus runner ------------------------------------------------------------------------------
import { readFileSync, readdirSync, existsSync, mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join, basename } from 'node:path';

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = join(HERE, '..', '..');
const arg = (name) => (process.argv.find((a) => a.startsWith(`--${name}=`)) || '').split('=').slice(1).join('=') || null;

if (process.argv[1] && basename(process.argv[1]) === 'wall-snap.mjs') {
  const only = arg('only');
  const plansDir = arg('plans') || join(ROOT, 'Documents', 'Sample Floor plans');
  const outDir = join(HERE, 'out', 'wall-snap'); mkdirSync(outDir, { recursive: true });
  const truthAll = existsSync(join(HERE, 'truth-rooms.json')) ? JSON.parse(readFileSync(join(HERE, 'truth-rooms.json'), 'utf8')) : {};
  const findImage = (stem) => {
    const dirs = [plansDir, join(HERE, 'fixtures')];
    for (const d of dirs) { if (!existsSync(d)) continue; for (const f of readdirSync(d)) if (/\.(png|jpe?g)$/i.test(f) && f.replace(/\.[^.]+$/, '') === stem) return join(d, f); }
    return null;
  };
  const sane = (b) => b && b.w > 0.05 && b.h > 0.05 && b.w <= 1 && b.h <= 1 && b.x >= -0.02 && b.y >= -0.02 && b.x + b.w <= 1.05 && b.y + b.h <= 1.05 ? b : null;
  const page = (bld, r) => { const b = sane(bld); return b ? { label: r.label, x: b.x + r.x * b.w, y: b.y + r.y * b.h, w: r.w * b.w, h: r.h * b.h } : { label: r.label, x: r.x, y: r.y, w: r.w, h: r.h }; };
  const clamp01 = (v) => Math.max(0, Math.min(1, v));
  const rows = [];
  const live = join(HERE, 'out', 'live');
  const files = existsSync(live) ? readdirSync(live).filter((f) => f.endsWith('.v6.json')).map((f) => join(live, f)) : [];
  // The bundled fixture plans always come along: their recordings are body-framed with no building
  // box, so the plan body (900 x 1200 at 50,120 on a 1000 x 1400 sheet) is supplied as one.
  const BODY = { x: 50 / 1000, y: 120 / 1400, w: 900 / 1000, h: 1200 / 1400 };
  const fixtures = ['plan-01', 'plan-01-jpeg', 'plan-01-photo'].map((id) => join(ROOT, 'shared', 'src', 'main', 'resources', 'scan', id + '.json'));
  for (const f of [...fixtures, ...files]) {
    if (!existsSync(f)) continue;
    const isFixture = fixtures.includes(f);
    const id = basename(f).replace(/\.openai_.*$/, '').replace(/\.json$/, '');
    if (only && !id.includes(only)) continue;
    const rec = JSON.parse(readFileSync(f, 'utf8')); const rep = rec.reply || rec;
    if (isFixture && !rep.building) rep.building = BODY;
    const stem = (rec.file || id).replace(/\.[^.]+$/, '');
    const img = findImage(stem); if (!img) { rows.push([id, 'no image']); continue; }
    const L = await loadImg(img);
    const rooms = (rep.rooms || []).filter((r) => r.w > 0 && r.h > 0).map((r) => page(rep.building, r));
    if (!rooms.length) { rows.push([id, 'no rooms']); continue; }
    const frameBox = sane(rep.building) || { x: Math.min(...rooms.map((r) => r.x)), y: Math.min(...rooms.map((r) => r.y)), w: 0, h: 0 };
    if (!frameBox.w) { frameBox.w = Math.max(...rooms.map((r) => r.x + r.w)) - frameBox.x; frameBox.h = Math.max(...rooms.map((r) => r.y + r.h)) - frameBox.y; }
    const E = (b) => ({ label: b.label, x0: Math.round(clamp01(b.x) * L.W), y0: Math.round(clamp01(b.y) * L.H), x1: Math.round(clamp01(b.x + b.w) * L.W), y1: Math.round(clamp01(b.y + b.h) * L.H) });
    const read = rooms.map(E); const log = []; const out = snapAll(L, read, E(frameBox), log);
    const truth = truthAll[id] || truthAll[stem] || null;
    let before = null, after = null;
    if (truth) {
      const claimed = new Set(); let a = 0, b = 0, n = 0;
      read.forEach((q, i) => {
        const want = String(q.label).toUpperCase().trim(); let best = -1, hit = null;
        truth.forEach((t, j) => { if (claimed.has(j) || t.label.toUpperCase() !== want) return; const tb = E(t); const v = Math.max(0, Math.min(q.x1, tb.x1) - Math.max(q.x0, tb.x0)) * Math.max(0, Math.min(q.y1, tb.y1) - Math.max(q.y0, tb.y0)); if (v > best) { best = v; hit = j; } });
        if (hit == null) return; claimed.add(hit); const tb = E(truth[hit]); a += iou(q, tb); b += iou(out[i], tb); n++;
        rect(L.im, tb, BLUE, 2);
      });
      if (n) { before = a / n; after = b / n; }
    }
    read.forEach((q, i) => { rect(L.im, q, RED, 3); rect(L.im, out[i], GREEN, 3); });
    await L.im.writeAsync(join(outDir, id + '.png'));
    const moved = out.filter((o, i) => o.x0 !== read[i].x0 || o.y0 !== read[i].y0 || o.x1 !== read[i].x1 || o.y1 !== read[i].y1).length;
    rows.push([id, `${rooms.length} rooms, ${moved} moved` + (before != null ? `, truth ${(before * 100).toFixed(0)} % -> ${(after * 100).toFixed(0)} %` : ''), log[0] || '']);
  }
  console.log('sheet'.padEnd(34) + 'result');
  for (const r of rows) console.log(r[0].padEnd(34) + r.slice(1).join('   '));
  console.log(`\noverlays in ${outDir} — LOOK at them; the table cannot see a box on the wrong room.`);
}

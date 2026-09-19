// exp-room-fill.mjs — THE PROBE behind "should the whole approach be re-thought?" (19 Sep 2026).
//
// THE QUESTION. The reader transcribes a sheet's TEXT at ~95 % and guesses its RECTANGLES at
// 40-70 %. WallSnap already moves each rectangle's four EDGES onto walls found in the picture. This
// asks the next question: instead of nudging the model's rectangle, can a room be GROWN from a point
// inside it until it meets its own walls — so the shape comes from the photograph and nothing but
// the label comes from the model?
//
// WHAT IT DOES, per sheet:
//   1. ink mask  — ink = luma < regionBg - INK_DROP AND saturation < SAT_MAX. WallSnap's own test.
//   2. close     — dilate then erode by the sheet's measured wall thickness, so a door opening in a
//                  wall does not let one room leak into the next.
//   3. seed      — a lattice of candidate points inside the room's (wall-snapped) rectangle.
//   4. fill      — 4-way flood over non-ink pixels from each seed, bounded by the building box.
//   5. leak gate — a fill is discarded when it covers more than LEAK_SHARE of the building, or when
//                  it swallows another room's rectangle centre. The room then keeps its old box.
//   6. score     — the kept fill's bounding box against the hand-marked rooms in truth-rooms.json.
//
// ⚠ NOT SHIPPED and not a mirror of any Kotlin. It is a measurement, to be read beside its pictures.
// The seeds here come from the reader's rectangle; the real design would take them from the pixel
// position of the printed caption (on-device OCR), which is strictly better and costs no rupees.
//
// Run:  cd tools/scan-eval && node exp-room-fill.mjs                 # every sheet with truth
//       node exp-room-fill.mjs --only=floor-plan-1                   # one
//       node exp-room-fill.mjs --all                                 # every sheet with an image
// Out:  out/room-fill/<id>.png  (red = box as the app draws it today, green = the fill's box,
//                                blue = hand-marked truth) + a table on stdout.
import { readFileSync, writeFileSync, mkdirSync, existsSync, readdirSync } from 'node:fs';
import { dirname, join, basename } from 'node:path';
import { fileURLToPath } from 'node:url';
import J from 'jimp';
import { loadImg, P } from './wall-snap.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = join(HERE, '..', '..');
const PLANS = join(ROOT, 'Documents', 'Sample Floor plans');
const PNGS = join(HERE, 'out', 'plans-png');
const LIVE = join(HERE, 'out', 'live');
const OUT = join(HERE, 'out', 'room-fill');

const ARG = (n) => (process.argv.find((a) => a.startsWith(`--${n}=`)) || '').split('=')[1] || null;
const ONLY = ARG('only');
const ALL = process.argv.includes('--all');

/** A fill covering more of the building than this has leaked through a door or a broken wall. */
const LEAK_SHARE = 0.45;
/** Seeds are tried on this lattice inside the room's rectangle, inset to keep off its own walls. */
const SEED_N = 5;
const SEED_INSET = 0.18;
/** A fill smaller than this share of the room it was seeded in is trapped inside the room's own
 *  printed caption box, or inside a bed, a counter or a door swing — not the room. */
const MIN_FILL_SHARE = 0.40;

// ---------------------------------------------------------------------------------------------

function inkMask(L) {
  const { W, H, luma, sat } = L;
  const bg = L.white;
  const m = new Uint8Array(W * H);
  for (let i = 0; i < m.length; i++) m[i] = (luma[i] < bg - P.inkDrop && sat[i] < P.satMax) ? 1 : 0;
  return m;
}

/** Longest run of ink along a scanline, sampled — the sheet's own wall thickness, as WallSnap does. */
function wallThickness(mask, W, H) {
  const runs = [];
  for (let y = Math.floor(H * 0.1); y < H * 0.9; y += Math.max(1, Math.floor(H / 120))) {
    let run = 0;
    for (let x = 0; x < W; x++) {
      if (mask[y * W + x]) run++;
      else { if (run >= 2 && run < W * P.maxThickShare) runs.push(run); run = 0; }
    }
  }
  if (!runs.length) return 3;
  runs.sort((a, b) => a - b);
  return Math.max(2, Math.min(40, runs[Math.floor(runs.length * 0.75)]));
}

/** Binary close: dilate by r then erode by r. Seals door openings narrower than 2r. */
function close(mask, W, H, r) {
  const dil = dilate(mask, W, H, r);
  return erode(dil, W, H, r);
}
function dilate(m, W, H, r) {
  const a = new Uint8Array(W * H), b = new Uint8Array(W * H);
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    let v = 0;
    for (let d = -r; d <= r && !v; d++) { const xx = x + d; if (xx >= 0 && xx < W && m[y * W + xx]) v = 1; }
    a[y * W + x] = v;
  }
  for (let x = 0; x < W; x++) for (let y = 0; y < H; y++) {
    let v = 0;
    for (let d = -r; d <= r && !v; d++) { const yy = y + d; if (yy >= 0 && yy < H && a[yy * W + x]) v = 1; }
    b[y * W + x] = v;
  }
  return b;
}
function erode(m, W, H, r) {
  const a = new Uint8Array(W * H), b = new Uint8Array(W * H);
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    let v = 1;
    for (let d = -r; d <= r && v; d++) { const xx = x + d; if (xx < 0 || xx >= W || !m[y * W + xx]) v = 0; }
    a[y * W + x] = v;
  }
  for (let x = 0; x < W; x++) for (let y = 0; y < H; y++) {
    let v = 1;
    for (let d = -r; d <= r && v; d++) { const yy = y + d; if (yy < 0 || yy >= H || !a[yy * W + x]) v = 0; }
    b[y * W + x] = v;
  }
  return b;
}

/** 4-way flood over non-ink pixels inside `frame`. Returns {area, x0,y0,x1,y1, seen} or null. */
function fill(mask, W, H, sx, sy, frame, cap) {
  const i0 = sy * W + sx;
  if (mask[i0]) return null;
  const seen = new Uint8Array(W * H);
  const q = new Int32Array(cap + 8);
  let head = 0, tail = 0, area = 0;
  let x0 = sx, y0 = sy, x1 = sx, y1 = sy;
  q[tail++] = i0; seen[i0] = 1;
  while (head < tail) {
    const i = q[head++];
    const x = i % W, y = (i - x) / W;
    area++;
    if (x < x0) x0 = x; if (x > x1) x1 = x;
    if (y < y0) y0 = y; if (y > y1) y1 = y;
    if (area > cap) return { leaked: true, area, x0, y0, x1, y1, seen };
    if (x > frame.x0 && !mask[i - 1] && !seen[i - 1]) { seen[i - 1] = 1; q[tail++] = i - 1; }
    if (x < frame.x1 && !mask[i + 1] && !seen[i + 1]) { seen[i + 1] = 1; q[tail++] = i + 1; }
    if (y > frame.y0 && !mask[i - W] && !seen[i - W]) { seen[i - W] = 1; q[tail++] = i - W; }
    if (y < frame.y1 && !mask[i + W] && !seen[i + W]) { seen[i + W] = 1; q[tail++] = i + W; }
  }
  return { leaked: false, area, x0, y0, x1, y1, seen };
}

const iou = (a, b) => {
  const w = Math.min(a.x1, b.x1) - Math.max(a.x0, b.x0);
  const h = Math.min(a.y1, b.y1) - Math.max(a.y0, b.y0);
  if (!(w > 0) || !(h > 0)) return 0;
  const inter = w * h;
  return inter / ((a.x1 - a.x0) * (a.y1 - a.y0) + (b.x1 - b.x0) * (b.y1 - b.y0) - inter);
};

// ---------------------------------------------------------------------------------------------

const truth = JSON.parse(readFileSync(join(HERE, 'truth-rooms.json'), 'utf8'));
const sheetsWithTruth = Object.keys(truth).filter((k) => k !== '_README');

function findImage(stem) {
  for (const dir of [PNGS, PLANS]) {
    if (!existsSync(dir)) continue;
    for (const f of readdirSync(dir)) {
      const b = f.replace(/\.[^.]+$/, '');
      if (b === stem && /\.(png|jpg|jpeg)$/i.test(f)) return join(dir, f);
    }
  }
  return null;
}

function findReply(stem) {
  const pick = readdirSync(LIVE).filter((f) => f.startsWith(stem + '.') || f === stem + '.json');
  const v6 = pick.find((f) => f.includes('.v6.'));
  const plain = pick.find((f) => f === stem + '.json');
  const f = v6 || plain || pick[0];
  return f ? JSON.parse(readFileSync(join(LIVE, f), 'utf8')) : null;
}

const saneBuilding = (b) => (b && b.w > 0.2 && b.h > 0.2 && b.x > -0.1 && b.y > -0.1
  && b.x + b.w < 1.1 && b.y + b.h < 1.1) ? b : null;
const pageOf = (bld, r) => {
  const b = saneBuilding(bld);
  return b ? { x: b.x + r.x * b.w, y: b.y + r.y * b.h, w: r.w * b.w, h: r.h * b.h }
    : { x: r.x, y: r.y, w: r.w, h: r.h };
};

mkdirSync(OUT, { recursive: true });
const stems = ONLY ? [ONLY] : (ALL ? [...new Set(readdirSync(LIVE).map((f) => f.split('.')[0]))] : sheetsWithTruth);

console.log('%s %s %s %s %s', 'sheet'.padEnd(34), 'rooms'.padEnd(6), 'filled'.padEnd(7), 'leaked'.padEnd(7), 'IoU vs truth  read -> fill');
console.log('-'.repeat(106));

for (const stem of stems) {
  const img = findImage(stem);
  const rep = findReply(stem);
  if (!img || !rep) { console.log(`${stem.padEnd(34)} (no ${!img ? 'image' : 'reply'})`); continue; }
  const reply = rep.reply || rep;
  const rooms = reply.rooms || [];
  if (!rooms.length) { console.log(`${stem.padEnd(34)} (no rooms)`); continue; }

  const L = await loadImg(img);
  const { W, H } = L;
  const raw = inkMask(L);
  const t = wallThickness(raw, W, H);
  const mask = close(raw, W, H, Math.max(1, Math.round(t * 0.6)));

  const bld = saneBuilding(reply.building) || { x: 0, y: 0, w: 1, h: 1 };
  const frame = {
    x0: Math.max(0, Math.floor(bld.x * W)), y0: Math.max(0, Math.floor(bld.y * H)),
    x1: Math.min(W - 1, Math.ceil((bld.x + bld.w) * W)), y1: Math.min(H - 1, Math.ceil((bld.y + bld.h) * H)),
  };
  const frameArea = (frame.x1 - frame.x0) * (frame.y1 - frame.y0);
  const cap = Math.floor(frameArea * LEAK_SHARE);

  const boxes = rooms.map((r) => {
    const p = pageOf(reply.building, r);
    return {
      label: r.label,
      x0: Math.round(p.x * W), y0: Math.round(p.y * H),
      x1: Math.round((p.x + p.w) * W), y1: Math.round((p.y + p.h) * H),
    };
  });
  const centres = boxes.map((b) => [Math.round((b.x0 + b.x1) / 2), Math.round((b.y0 + b.y1) / 2)]);

  const filled = [];
  let leaks = 0;
  for (let i = 0; i < boxes.length; i++) {
    const b = boxes[i];
    const bw = b.x1 - b.x0, bh = b.y1 - b.y0;
    let best = null;
    for (let gy = 0; gy < SEED_N; gy++) {
      for (let gx = 0; gx < SEED_N; gx++) {
        const sx = Math.round(b.x0 + bw * (SEED_INSET + (1 - 2 * SEED_INSET) * (gx + 0.5) / SEED_N));
        const sy = Math.round(b.y0 + bh * (SEED_INSET + (1 - 2 * SEED_INSET) * (gy + 0.5) / SEED_N));
        if (sx <= frame.x0 || sx >= frame.x1 || sy <= frame.y0 || sy >= frame.y1) continue;
        const f = fill(mask, W, H, sx, sy, frame, cap);
        if (!f || f.leaked) continue;
        // a fill that swallows another room's centre has run through a doorway
        let swallows = 0;
        for (let j = 0; j < centres.length; j++) {
          if (j === i) continue;
          const [cx, cy] = centres[j];
          if (cx >= f.x0 && cx <= f.x1 && cy >= f.y0 && cy <= f.y1 && f.seen[cy * W + cx]) swallows++;
        }
        if (swallows) continue;
        if (!best || f.area > best.area) best = f;
      }
    }
    const boxArea = Math.max(1, (b.x1 - b.x0) * (b.y1 - b.y0));
    const trapped = best && (best.x1 - best.x0) * (best.y1 - best.y0) < boxArea * MIN_FILL_SHARE;
    if (best && !trapped) filled.push({ ...b, fx0: best.x0, fy0: best.y0, fx1: best.x1, fy1: best.y1 });
    else { leaks++; filled.push({ ...b, fx0: b.x0, fy0: b.y0, fx1: b.x1, fy1: b.y1, fell_back: true }); }
  }

  // ---- score against truth, matching a marked room to the nearest reader box of the same label ----
  let readIoU = 0, fillIoU = 0, n = 0;
  const marks = truth[stem] || [];
  const used = new Set();
  for (const m of marks) {
    const tb = { x0: m.x * W, y0: m.y * H, x1: (m.x + m.w) * W, y1: (m.y + m.h) * H };
    let pick = -1, bestD = Infinity;
    for (let i = 0; i < filled.length; i++) {
      if (used.has(i)) continue;
      if (String(filled[i].label).toUpperCase().trim() !== String(m.label).toUpperCase().trim()) continue;
      const dx = (filled[i].x0 + filled[i].x1) / 2 - (tb.x0 + tb.x1) / 2;
      const dy = (filled[i].y0 + filled[i].y1) / 2 - (tb.y0 + tb.y1) / 2;
      const d = dx * dx + dy * dy;
      if (d < bestD) { bestD = d; pick = i; }
    }
    if (pick < 0) continue;
    used.add(pick);
    const f = filled[pick];
    readIoU += iou(tb, f);
    fillIoU += iou(tb, { x0: f.fx0, y0: f.fy0, x1: f.fx1, y1: f.fy1 });
    n++;
  }

  // ---- draw ----
  const im = await J.read(img);
  const line = (x0, y0, x1, y1, c, w = 3) => {
    for (let k = 0; k < w; k++) {
      for (let x = Math.max(0, x0); x <= Math.min(W - 1, x1); x++) {
        im.setPixelColor(c, x, Math.min(H - 1, Math.max(0, y0 + k)));
        im.setPixelColor(c, x, Math.min(H - 1, Math.max(0, y1 - k)));
      }
      for (let y = Math.max(0, y0); y <= Math.min(H - 1, y1); y++) {
        im.setPixelColor(c, Math.min(W - 1, Math.max(0, x0 + k)), y);
        im.setPixelColor(c, Math.min(W - 1, Math.max(0, x1 - k)), y);
      }
    }
  };
  const RED = 0xd32f2fff, GREEN = 0x1b8a3aff, BLUE = 0x1565c0ff;
  for (const f of filled) {
    line(f.x0, f.y0, f.x1, f.y1, RED, 2);
    line(f.fx0, f.fy0, f.fx1, f.fy1, GREEN, 3);
  }
  for (const m of marks) line(Math.round(m.x * W), Math.round(m.y * H),
    Math.round((m.x + m.w) * W), Math.round((m.y + m.h) * H), BLUE, 2);
  await im.writeAsync(join(OUT, stem + '.png'));

  const score = n ? `${(100 * readIoU / n).toFixed(0)}% -> ${(100 * fillIoU / n).toFixed(0)}%  (${n} marked)` : '(no truth)';
  console.log('%s %s %s %s %s', stem.padEnd(34), String(rooms.length).padEnd(6),
    String(filled.filter((f) => !f.fell_back).length).padEnd(7), String(leaks).padEnd(7), score);
}

console.log(`\noverlays in ${OUT} — red = today's box, green = grown from inside, blue = hand-marked truth.`);
console.log('LOOK at them. A table cannot see a fill that found the right walls of the wrong room.');

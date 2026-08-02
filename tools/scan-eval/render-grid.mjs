// render-grid.mjs — DRAW what the mapper hands the editor, so a session can LOOK at a scan result
// instead of inferring it from counts. Complements audit-mapper.mjs (numbers) with pictures.
//
// WHY: the owner compares the app's grid against his paper sheet by eye. Until now the assistant
// could only do that by asking him for phone screenshots. This renders the exact same geometry the
// app would draw (same mapper mirror, same synonym table, same grid) as an SVG per plan, plus a
// gallery page, so "does the kitchen run the right way?" is answered by looking.
//
// Run:  node tools/scan-eval/render-grid.mjs --only=X                  # whole recorded corpus
//       node tools/scan-eval/render-grid.mjs --only=X --plan=owner    # ids containing "owner"
//       node tools/scan-eval/render-grid.mjs --only=X --reply=path.json [--image=plan.png]
//                                                                      # a live scan (see scan-live.py)
// Output: tools/scan-eval/out/render/<id>.svg + index.html (open in a browser).
//
// ⚠ --only=X is REQUIRED in argv — importing sim.mjs without it runs the fuzz suites.
import { readFileSync, writeFileSync, mkdirSync, existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join, basename, resolve } from 'node:path';
import { scanMap, parsePrinted, printedTextOf } from '../grid-prototype/sim.mjs';
import { plans, fullResolve, contextOf } from './audit-mapper.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));
const OUT = join(HERE, 'out', 'render');
mkdirSync(OUT, { recursive: true });

const arg = (name) => (process.argv.find((a) => a.startsWith(`--${name}=`)) || '').split('=').slice(1).join('=') || null;
const ONLY_PLAN = arg('plan');
const REPLY_PATH = arg('reply');
const IMAGE_PATH = arg('image');

// Approximate the app's room palette — close enough that a side-by-side against a phone
// screenshot reads as the same plan. Geometry is exact; colours are cosmetic.
const PALETTE = {
  BEDROOM: ['#dcd5ec', '#5b4ba6'], GUEST_BEDROOM: ['#dcd5ec', '#5b4ba6'],
  MASTER_BEDROOM: ['#e0d3c2', '#7a5c34'],
  TOILET: ['#efd2cb', '#c0392b'], BATHROOM: ['#efd2cb', '#c0392b'],
  KITCHEN: ['#f6dcc0', '#d97e2c'],
  LIVING: ['#cfe3dc', '#1e6f64'], DINING: ['#d7e8e2', '#1e6f64'],
  BALCONY: ['#e6d9ee', '#8e44ad'],
  ENTRANCE: ['#dcdcd2', '#6b6b5e'], CORRIDOR: ['#dcdcd2', '#6b6b5e'], STAIRCASE: ['#dcdcd2', '#6b6b5e'],
  UTILITY: ['#dfe0d5', '#6b6b5e'], STORE: ['#dfe0d5', '#6b6b5e'], GARAGE: ['#dfe0d5', '#6b6b5e'],
  POOJA: ['#f2e4bd', '#a8842c'], STUDY: ['#cdd9e8', '#33567d'], BASEMENT: ['#dcdcd2', '#6b6b5e'],
  COURTYARD: ['#d9e6cf', '#4a7a34'],
};
const esc = (s) => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');

function svgFor(id, out) {
  const CELL = 56, PAD = 34;
  if (out.kind !== 'placed') {
    const names = (out.rooms || []).map((r) => r.label).join(' · ');
    return `<svg xmlns="http://www.w3.org/2000/svg" width="720" height="90" font-family="sans-serif">
  <rect width="100%" height="100%" fill="#f4f1e8"/>
  <text x="12" y="34" font-size="18" fill="#7a2f22">${esc(id)} — ${out.kind.toUpperCase()} (${esc(out.reason)})</text>
  <text x="12" y="62" font-size="12" fill="#555">${esc(names.slice(0, 160))}</text>
</svg>`;
  }
  const W = out.cols * CELL + PAD * 2, H = out.rows * CELL + PAD * 2;
  const px = (c) => PAD + c * CELL, py = (r) => PAD + r * CELL;
  const bits = [];
  bits.push(`<rect width="100%" height="100%" fill="#f4f1e8"/>`);
  // content bounding box + its empty cells, hatched so looseness is visible at a glance
  const minC = Math.min(...out.rooms.map((r) => r.rect.col)), maxC = Math.max(...out.rooms.map((r) => r.rect.col + r.rect.w));
  const minR = Math.min(...out.rooms.map((r) => r.rect.row)), maxR = Math.max(...out.rooms.map((r) => r.rect.row + r.rect.h));
  const held = new Set();
  for (const r of out.rooms) for (let c = r.rect.col; c < r.rect.col + r.rect.w; c++) for (let w = r.rect.row; w < r.rect.row + r.rect.h; w++) held.add(`${c},${w}`);
  for (let c = minC; c < maxC; c++) for (let r = minR; r < maxR; r++) {
    if (!held.has(`${c},${r}`)) bits.push(`<rect x="${px(c) + 2}" y="${py(r) + 2}" width="${CELL - 4}" height="${CELL - 4}" fill="#e8e2cf" stroke="#c9bfa0" stroke-dasharray="4 3"/>`);
  }
  for (let c = 0; c <= out.cols; c++) bits.push(`<line x1="${px(c)}" y1="${py(0)}" x2="${px(c)}" y2="${py(out.rows)}" stroke="#d5cfbc" stroke-width="1"/>`);
  for (let r = 0; r <= out.rows; r++) bits.push(`<line x1="${px(0)}" y1="${py(r)}" x2="${px(out.cols)}" y2="${py(r)}" stroke="#d5cfbc" stroke-width="1"/>`);
  for (const r of out.rooms) {
    const [fill, stroke] = PALETTE[r.type] || ['#e0e0e0', '#666'];
    const x = px(r.rect.col) + 3, y = py(r.rect.row) + 3, w = r.rect.w * CELL - 6, h = r.rect.h * CELL - 6;
    bits.push(`<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="8" fill="${fill}" fill-opacity="0.9" stroke="${stroke}" stroke-width="1.6"/>`);
    const label = String(r.label).split(/[0-9(]/)[0].trim() || r.type;
    const cxm = x + w / 2, cym = y + h / 2;
    const vertical = h > w * 1.6 && label.length > 6;
    const fs = Math.max(9, Math.min(14, (vertical ? h : w) / (label.length * 0.62)));
    bits.push(`<text x="${cxm}" y="${cym}" font-size="${fs}" fill="#222" text-anchor="middle" dominant-baseline="middle"${vertical ? ` transform="rotate(90 ${cxm} ${cym})"` : ''}>${esc(label)}</text>`);
    // the printed size, small, under the name — SEEING 4.07x2.50 on a 1x1 tile is the point
    const p = r.printed;
    if (p && !vertical && h > 30) {
      bits.push(`<text x="${cxm}" y="${cym + fs * 0.9 + 2}" font-size="8.5" fill="#555" text-anchor="middle">${esc(`${(p.w / 1000).toFixed(2)}x${(p.h / 1000).toFixed(2)}m -> ${r.rect.w}x${r.rect.h}`)}</text>`);
    } else if (r.strip && !vertical && h > 30) {
      // a single-dimension strip: show the printed depth the narrow axis came from
      bits.push(`<text x="${cxm}" y="${cym + fs * 0.9 + 2}" font-size="8.5" fill="#555" text-anchor="middle">${esc(`${(r.strip / 1000).toFixed(2)}m deep -> ${r.rect.w}x${r.rect.h}`)}</text>`);
    }
  }
  bits.push(`<text x="${W / 2}" y="${PAD - 12}" font-size="12" fill="#666" text-anchor="middle" letter-spacing="3">NORTH</text>`);
  bits.push(`<text x="${PAD}" y="${H - 8}" font-size="11" fill="#666">WEST</text>`);
  bits.push(`<text x="${W / 2}" y="${H - 8}" font-size="11" fill="#666" text-anchor="middle">SOUTH</text>`);
  bits.push(`<text x="${W - PAD}" y="${H - 8}" font-size="11" fill="#666" text-anchor="end">EAST</text>`);
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" font-family="sans-serif">\n${bits.join('\n')}\n</svg>`;
}

const jobs = [];
if (REPLY_PATH) {
  const raw = JSON.parse(readFileSync(resolve(REPLY_PATH), 'utf8'));
  const reply = raw.reply || raw;
  const aspect = raw.imageSize ? raw.imageSize[0] / raw.imageSize[1]
    : (arg('aspect') ? Number(arg('aspect')) : 1);
  jobs.push({ id: basename(REPLY_PATH).replace(/\.json$/, ''), reply, aspect, image: IMAGE_PATH });
} else {
  for (const p of plans) {
    if (ONLY_PLAN && !p.id.includes(ONLY_PLAN)) continue;
    jobs.push({ id: p.id.replace(/[^\w.-]+/g, '_'), reply: p.reply, aspect: p.aspect, image: null });
  }
}

const cards = [];
for (const job of jobs) {
  const ctx = contextOf((job.reply.rooms || []).map((b) => b.label));
  const out = scanMap(job.reply, job.aspect, { resolveLabel: (raw) => fullResolve(raw, ctx) });
  const svg = svgFor(job.id, out);
  writeFileSync(join(OUT, `${job.id}.svg`), svg);
  // Geometry as data, for render-png.py (PIL) — same mapper output, a viewer that works headless.
  writeFileSync(join(OUT, `${job.id}.geom.json`), JSON.stringify({
    id: job.id, kind: out.kind, reason: out.reason || null,
    cols: out.cols || 0, rows: out.rows || 0,
    rooms: (out.rooms || []).map((r) => ({
      type: r.type, label: r.label, rect: r.rect || null,
      printed: r.printed ? { w: r.printed.w, h: r.printed.h } : null,
      strip: r.strip || null,
    })),
  }, null, 1));
  const caption = out.kind === 'placed'
    ? `${out.cols}x${out.rows} · ${out.rooms.length}/${(job.reply.rooms || []).length} rooms placed`
    : `${out.kind.toUpperCase()} — ${out.reason}`;
  const img = job.image && existsSync(resolve(job.image))
    ? `<img src="${esc(resolve(job.image).replace(/\\/g, '/'))}" alt="plan">` : '';
  cards.push(`<div class="card"><h3>${esc(job.id)}</h3><p>${esc(caption)}</p><div class="pair">${img}${svg}</div></div>`);
  console.log(`${job.id.padEnd(28)} ${caption}`);
}

writeFileSync(join(OUT, 'index.html'), `<!doctype html><meta charset="utf-8"><title>scan renders</title>
<style>body{font-family:sans-serif;background:#efece3;margin:20px}.card{background:#fff;border:1px solid #ccc;border-radius:10px;padding:14px;margin:0 0 26px}
.pair{display:flex;gap:18px;align-items:flex-start;flex-wrap:wrap}.pair img{max-width:46%;max-height:760px;border:1px solid #ddd}h3{margin:0 0 4px}p{margin:0 0 10px;color:#555}</style>
${cards.join('\n')}`);
console.log(`\nwrote ${jobs.length} svgs + index.html to ${OUT}`);

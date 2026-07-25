// sim.mjs — headless behavioural mirror of the guided-grid editor, for finding gesture bugs.
//
// WHY THIS EXISTS (owner report #11): the Kotlin :shared tests cover the PURE maths, and the
// Robolectric harness renders the screen UNSELECTED, so neither ever crosses the seam where a real
// finger meets the live grid — which is exactly where the on-device bugs lived (stale plot size →
// off-grid placement, taps missing the room, grips in the wrong place). The old Chrome prototype was
// a fixed 8×8 with NO plot resize, so the whole rectangular-grid + resize class of bug was invisible
// to it too.
//
// This file ports the EXACT post-fix logic — the pure maths (GridEditing.kt / GridResize.kt) AND the
// finger/coordinate pipeline (GuidedGridScreen.kt: handleCentre, hitTest, placementAt, the drag
// arbiter) — then drives it through thousands of random ORDERS of operations, asserting invariants
// after every single step. A violation prints the seed + the exact operation sequence to reproduce.
//
// Run:  node tools/grid-prototype/sim.mjs [iterations]
// Zero dependencies. Keep it in lock-step with the Kotlin — this is a mirror, not a second design.

// ----------------------------------------------------------------------------------------------
// Ported pure maths — GridEditing.kt
// ----------------------------------------------------------------------------------------------
const MIN_GRID = 4, MAX_GRID = 10;
const clampInt = (v, lo, hi) => Math.max(lo, Math.min(hi, v));

// CellRect = {col,row,w,h}. right = col+w, bottom = row+h (exclusive).
const right = (r) => r.col + r.w;
const bottom = (r) => r.row + r.h;
const overlaps = (a, b) => a.col < right(b) && b.col < right(a) && a.row < bottom(b) && b.row < bottom(a);
const anyOverlap = (cand, others) => others.some((o) => overlaps(cand, o));

function clampToGrid(r, cols, rows) {
  const w = clampInt(r.w, 1, cols);
  const h = clampInt(r.h, 1, rows);
  return { col: clampInt(r.col, 0, cols - w), row: clampInt(r.row, 0, rows - h), w, h };
}

function firstFreeSlot(w, h, cols, rows, placed) {
  if (w > cols || h > rows) return null;
  for (let row = 0; row <= rows - h; row++)
    for (let col = 0; col <= cols - w; col++)
      if (!anyOverlap({ col, row, w, h }, placed)) return [col, row];
  return null;
}

function fitWithoutOverlap(rects, cols, rows) {
  const placed = [];
  for (const r of rects) {
    const clamped = clampToGrid(r, cols, rows);
    if (!anyOverlap(clamped, placed)) { placed.push(clamped); continue; }
    const slot = firstFreeSlot(clamped.w, clamped.h, cols, rows, placed);
    if (!slot) return null;
    placed.push({ col: slot[0], row: slot[1], w: clamped.w, h: clamped.h });
  }
  return placed;
}

// Handle = 'TL' | 'TR' | 'BL' | 'BR'
const handlesFor = (r) => (r.w === 1 && r.h === 1) ? ['BR'] : ['TL', 'TR', 'BL', 'BR'];
function handleAnchor(r, h) {
  switch (h) {
    case 'TL': return [r.col, r.row];
    case 'TR': return [right(r), r.row];
    case 'BL': return [r.col, bottom(r)];
    case 'BR': return [right(r), bottom(r)];
  }
}

function moveBy(start, dCol, dRow, cols, rows) {
  return {
    col: clampInt(start.col + dCol, 0, cols - start.w),
    row: clampInt(start.row + dRow, 0, rows - start.h),
    w: start.w, h: start.h,
  };
}

function resizeBy(start, handle, dCol, dRow, cols, rows, minCells = 1) {
  const pullsRight = handle === 'TR' || handle === 'BR';
  const pullsDown = handle === 'BL' || handle === 'BR';
  let col, w;
  if (pullsRight) { col = start.col; w = clampInt(start.w + dCol, minCells, cols - start.col); }
  else { col = clampInt(start.col + dCol, 0, right(start) - minCells); w = right(start) - col; }
  let row, h;
  if (pullsDown) { row = start.row; h = clampInt(start.h + dRow, minCells, rows - start.row); }
  else { row = clampInt(start.row + dRow, 0, bottom(start) - minCells); h = bottom(start) - row; }
  return { col, row, w, h };
}

function snapWithHysteresis(exact, current, stick = 0.15) {
  const target = Math.round(exact);
  if (target === current) return current;
  return Math.abs(exact - current) > 0.5 + stick ? target : current;
}

function cellIndex(coordPx, cellPx, grid) {
  if (cellPx <= 0) return 0;
  return clampInt(Math.floor(coordPx / cellPx), 0, grid - 1);
}

// ----------------------------------------------------------------------------------------------
// Ported plot-resize — GridResize.kt
// ----------------------------------------------------------------------------------------------
function resolveGridResize(rooms, door, curCols, curRows, reqCols, reqRows) {
  const c = clampInt(reqCols, MIN_GRID, MAX_GRID);
  const r = clampInt(reqRows, MIN_GRID, MAX_GRID);
  if (c === curCols && r === curRows) return { cols: curCols, rows: curRows, rooms, door, changed: false };
  const fitted = fitWithoutOverlap(rooms.map((x) => ({ col: x.col, row: x.row, w: x.w, h: x.h })), c, r);
  if (!fitted) return null; // REFUSE
  let changed = false;
  const repacked = rooms.map((room, i) => ({ ...room, col: fitted[i].col, row: fitted[i].row, w: fitted[i].w, h: fitted[i].h }));
  const newRooms = repacked.some((rm, i) => rm.col !== rooms[i].col || rm.row !== rooms[i].row || rm.w !== rooms[i].w || rm.h !== rooms[i].h)
    ? (changed = true, repacked) : rooms;
  let newDoor = door;
  if (door) {
    const fits = (door.side === 'N' || door.side === 'S') ? (door.cell >= 0 && door.cell < c)
      : (door.cell >= 0 && door.cell < r);
    if (!fits) { newDoor = null; changed = true; }
  }
  return { cols: c, rows: r, rooms: newRooms, door: newDoor, changed };
}

function clampDoorToRooms(door, rooms) {
  if (!door) return null;
  if (rooms.length === 0) return null;
  const minC = Math.min(...rooms.map((r) => r.col)), maxC = Math.max(...rooms.map((r) => r.col + r.w));
  const minR = Math.min(...rooms.map((r) => r.row)), maxR = Math.max(...rooms.map((r) => r.row + r.h));
  if (door.side === 'N' || door.side === 'S') return { ...door, cell: clampInt(door.cell, minC, maxC - 1) };
  return { ...door, cell: clampInt(door.cell, minR, maxR - 1) };
}

// ----------------------------------------------------------------------------------------------
// Ported finger/coordinate pipeline — GuidedGridScreen.kt (POST-FIX)
// ----------------------------------------------------------------------------------------------
// Screen geometry the sim uses. The real screen picks a width; cellPx = gridWpx/cols == gridHpx/rows
// because the grid is aspectRatio(cols/rows) with square cells. We mirror that exactly.
const GRID_W_DP = 312; // ~360dp phone minus padding — matches the prototype/canvas
const HANDLE_TOUCH = 48, HANDLE_GRIP = 12;

function geom(cols, rows) {
  const cellPx = GRID_W_DP / cols;         // square cells
  return { cellPx, gridWpx: cellPx * cols, gridHpx: cellPx * rows };
}

// handleCentre — POST-FIX: clampPx is the small grip radius, NOT the old ~24dp inward shove.
function handleCentre(rect, handle, cellPx, gridWpx, gridHpx, clampPx) {
  const [c, r] = handleAnchor(rect, handle);
  const clamp = (v, extent) => {
    const lo = Math.min(clampPx, extent / 2);
    const hi = Math.max(extent - clampPx, extent / 2);
    return clampInt(v, lo, hi);
  };
  return [clamp(c * cellPx, gridWpx), clamp(r * cellPx, gridHpx)];
}

// hitTest — POST-FIX: grab radius capped at half a cell; grips use the small clamp; AND a grip only
// wins when the finger is closer to that corner than to the room's CENTRE — so the middle of a room
// always moves, at any room size or grid density (the edge-clamp otherwise pulls a tiny room's grip
// far enough inward to swallow its own centre — owner #7 on 1×1 rooms).
function hitTest(pos, rooms, selectedId, cellPx, gridWpx, gridHpx, touchPx, gripClampPx) {
  const radius = Math.min(touchPx / 2, cellPx * 0.5);
  const sel = rooms.find((r) => r.id === selectedId);
  if (sel) {
    const ccx = (sel.col + sel.w / 2) * cellPx, ccy = (sel.row + sel.h / 2) * cellPx;
    const dToCentre = (pos[0] - ccx) ** 2 + (pos[1] - ccy) ** 2;
    for (const h of handlesFor(sel)) {
      const [cx, cy] = handleCentre(sel, h, cellPx, gridWpx, gridHpx, gripClampPx);
      const dToGrip = (pos[0] - cx) ** 2 + (pos[1] - cy) ** 2;
      if (dToGrip <= radius * radius && dToGrip < dToCentre) return { roomId: sel.id, rect: sel, handle: h };
    }
  }
  for (let i = rooms.length - 1; i >= 0; i--) {
    const r = rooms[i];
    if (pos[0] >= r.col * cellPx && pos[0] < right(r) * cellPx &&
        pos[1] >= r.row * cellPx && pos[1] < bottom(r) * cellPx)
      return { roomId: r.id, rect: r, handle: null };
  }
  return null;
}

function placementAt(pos, cellPx, rooms, cols, rows) {
  const col = cellIndex(pos[0], cellPx, cols);
  const row = cellIndex(pos[1], cellPx, rows);
  const rect = { col, row, w: Math.min(2, cols - col), h: Math.min(2, rows - row) };
  return { rect, blocked: anyOverlap(rect, rooms) };
}

// The drawn centre of a room, in px — where a finger aiming at the room lands.
function drawnCentrePx(r, cellPx) {
  return [(r.col + r.w / 2) * cellPx, (r.row + r.h / 2) * cellPx];
}

// ----------------------------------------------------------------------------------------------
// Editor state machine + the gesture arbiter (mirrors awaitEachGesture)
// ----------------------------------------------------------------------------------------------
function makeEditor() {
  return { rooms: [], door: null, cols: 8, rows: 8, selectedId: null, nextId: 0 };
}

function newRoomId(ed) {
  let n = ed.rooms.length;
  while (ed.rooms.some((r) => r.id === `room-${n}`)) n++;
  return `room-${n}`;
}

// Place a room: finger down at downPx, drags to targetPx, lifts. Commit on lift if not blocked.
function opPlace(ed, type, downPx, targetPx) {
  const { cellPx } = geom(ed.cols, ed.rows);
  let ghost = placementAt(downPx, cellPx, ed.rooms, ed.cols, ed.rows);
  ghost = placementAt(targetPx, cellPx, ed.rooms, ed.cols, ed.rows); // finger slid to target
  if (ghost.blocked) return; // lift on a blocked cell → refused
  const id = newRoomId(ed);
  ed.rooms.push({ id, type, ...ghost.rect });
  ed.selectedId = id;
  ed.door = clampDoorToRooms(ed.door, ed.rooms); // app: onRoomsChange → updateRooms → clampDoorToRooms
}

// Move or resize: finger down at downPx (hit decides), raw delta = targetPx-downPx, lift commits.
function opDrag(ed, downPx, targetPx) {
  const { cellPx, gridWpx, gridHpx } = geom(ed.cols, ed.rows);
  const hit = hitTest(downPx, ed.rooms, ed.selectedId, cellPx, gridWpx, gridHpx, HANDLE_TOUCH, HANDLE_GRIP / 2);
  if (!hit) { ed.selectedId = null; return; }
  if (hit.roomId !== ed.selectedId) ed.selectedId = hit.roomId;
  const handle = hit.handle;
  const start = { ...hit.rect };
  const others = ed.rooms.filter((r) => r.id !== hit.roomId);
  // Frozen start + RAW delta accumulator (never feed back the snapped value).
  const rawX = targetPx[0] - downPx[0], rawY = targetPx[1] - downPx[1];
  let steps = [0, 0];
  let rect = start;
  const stepCol = snapWithHysteresis(rawX / cellPx, steps[0]);
  const stepRow = snapWithHysteresis(rawY / cellPx, steps[1]);
  if (stepCol !== steps[0] || stepRow !== steps[1]) {
    const attempt = handle == null ? moveBy(start, stepCol, stepRow, ed.cols, ed.rows)
      : resizeBy(start, handle, stepCol, stepRow, ed.cols, ed.rows);
    if (!anyOverlap(attempt, others)) rect = attempt;
    steps = [stepCol, stepRow];
  }
  if (rect !== start && (rect.col !== start.col || rect.row !== start.row || rect.w !== start.w || rect.h !== start.h)) {
    const room = ed.rooms.find((r) => r.id === hit.roomId);
    Object.assign(room, rect);
    ed.door = clampDoorToRooms(ed.door, ed.rooms); // app: onRoomsChange → updateRooms → clampDoorToRooms
  }
}

function opPlotResize(ed, reqCols, reqRows) {
  const res = resolveGridResize(ed.rooms, ed.door, ed.cols, ed.rows, reqCols, reqRows);
  if (!res) return; // refused
  ed.cols = res.cols; ed.rows = res.rows; ed.rooms = res.rooms; ed.door = res.door;
  // THE FIX (v0.3.8): re-clamp the door to the footprint after the repack, exactly as updateRooms
  // does on a room edit. Without it, a plot resize that moves rooms leaves the door off the shrunken
  // footprint — displayed ≠ scored/reloaded (the F4 class of bug, missed on the plot-resize path).
  ed.door = clampDoorToRooms(ed.door, ed.rooms);
}

function opPlaceDoor(ed, tapPx) {
  if (ed.rooms.length === 0) return;
  const { cellPx } = geom(ed.cols, ed.rows);
  const col = cellIndex(tapPx[0], cellPx, ed.cols);
  const row = cellIndex(tapPx[1], cellPx, ed.rows);
  const distN = row, distS = ed.rows - 1 - row, distW = col, distE = ed.cols - 1 - col;
  const fMinC = Math.min(...ed.rooms.map((r) => r.col)), fMaxC = Math.max(...ed.rooms.map((r) => r.col + r.w));
  const fMinR = Math.min(...ed.rooms.map((r) => r.row)), fMaxR = Math.max(...ed.rooms.map((r) => r.row + r.h));
  const cCol = clampInt(col, fMinC, fMaxC - 1), cRow = clampInt(row, fMinR, fMaxR - 1);
  const m = Math.min(distN, distS, distW, distE);
  ed.door = m === distN ? { side: 'N', cell: cCol } : m === distS ? { side: 'S', cell: cCol }
    : m === distW ? { side: 'W', cell: cRow } : { side: 'E', cell: cRow };
}

function opRemove(ed) {
  if (!ed.selectedId) return;
  ed.rooms = ed.rooms.filter((r) => r.id !== ed.selectedId);
  ed.door = clampDoorToRooms(ed.door, ed.rooms);
  ed.selectedId = null;
}

// ----------------------------------------------------------------------------------------------
// Invariants — checked after EVERY operation
// ----------------------------------------------------------------------------------------------
function checkInvariants(ed) {
  const problems = [];
  // 1. No two rooms overlap (score-corrupting: engine double-counts a buried room).
  for (let i = 0; i < ed.rooms.length; i++)
    for (let j = i + 1; j < ed.rooms.length; j++)
      if (overlaps(ed.rooms[i], ed.rooms[j])) problems.push(`OVERLAP ${ed.rooms[i].id}∩${ed.rooms[j].id}`);
  // 2. Every room fully inside the grid.
  for (const r of ed.rooms)
    if (r.col < 0 || r.row < 0 || right(r) > ed.cols || bottom(r) > ed.rows || r.w < 1 || r.h < 1)
      problems.push(`OFFGRID ${r.id} (${r.col},${r.row},${r.w}×${r.h}) in ${ed.cols}×${ed.rows}`);
  // 3. Door sits on the room footprint (else it displays ≠ scored/reloaded).
  if (ed.door && ed.rooms.length) {
    const minC = Math.min(...ed.rooms.map((r) => r.col)), maxC = Math.max(...ed.rooms.map((r) => r.col + r.w));
    const minR = Math.min(...ed.rooms.map((r) => r.row)), maxR = Math.max(...ed.rooms.map((r) => r.row + r.h));
    const on = (ed.door.side === 'N' || ed.door.side === 'S') ? (ed.door.cell >= minC && ed.door.cell <= maxC - 1)
      : (ed.door.cell >= minR && ed.door.cell <= maxR - 1);
    if (!on) problems.push(`DOOR-OFF-FOOTPRINT ${ed.door.side}@${ed.door.cell}`);
  }
  if (ed.door && ed.rooms.length === 0) problems.push(`DOOR-NO-ROOMS`);
  // 4. DRAW/HIT CONSISTENCY: tapping a room's drawn centre selects THAT room (catches #6.2/#7).
  const { cellPx, gridWpx, gridHpx } = geom(ed.cols, ed.rows);
  for (const r of ed.rooms) {
    const c = drawnCentrePx(r, cellPx);
    const hit = hitTest(c, ed.rooms, null, cellPx, gridWpx, gridHpx, HANDLE_TOUCH, HANDLE_GRIP / 2);
    // The topmost room at the centre must be r itself (a later room may legitimately cover it, but
    // rooms never overlap by invariant 1, so the centre of r can only belong to r).
    if (!hit || hit.roomId !== r.id) problems.push(`DRAW≠HIT ${r.id} centre→${hit ? hit.roomId : 'nothing'}`);
  }
  // 5. CENTRE-MOVES: tapping a SELECTED room's centre must MOVE it, never resize (catches #7).
  for (const r of ed.rooms) {
    const c = drawnCentrePx(r, cellPx);
    const hit = hitTest(c, ed.rooms, r.id, cellPx, gridWpx, gridHpx, HANDLE_TOUCH, HANDLE_GRIP / 2);
    if (hit && hit.roomId === r.id && hit.handle != null)
      problems.push(`CENTRE-RESIZES ${r.id} (${r.w}×${r.h}) handle=${hit.handle}`);
  }
  return problems;
}

// ----------------------------------------------------------------------------------------------
// Seeded fuzz over random ORDERS of operations
// ----------------------------------------------------------------------------------------------
function mulberry32(seed) {
  return function () {
    seed |= 0; seed = (seed + 0x6D2B79F5) | 0;
    let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
const TYPES = ['Living', 'Kitchen', 'Master', 'Bedroom', 'Pooja', 'Toilet', 'Stairs'];

function randomOp(rng, ed) {
  const { cellPx } = geom(ed.cols, ed.rows);
  const px = () => [rng() * ed.cols * cellPx, rng() * ed.rows * cellPx];
  const kind = rng();
  if (kind < 0.30 || ed.rooms.length === 0) return { op: 'place', type: TYPES[(rng() * TYPES.length) | 0], down: px(), target: px() };
  if (kind < 0.55) return { op: 'drag', down: px(), target: px() };
  if (kind < 0.75) return { op: 'plot', cols: 3 + ((rng() * 9) | 0), rows: 3 + ((rng() * 9) | 0) }; // deliberately over/under range to test clamp+refuse
  if (kind < 0.85) return { op: 'door', tap: px() };
  if (kind < 0.93) return { op: 'select', down: px() };
  return { op: 'remove' };
}

function applyOp(ed, o) {
  switch (o.op) {
    case 'place': opPlace(ed, o.type, o.down, o.target); break;
    case 'drag': opDrag(ed, o.down, o.target); break;
    case 'plot': opPlotResize(ed, o.cols, o.rows); break;
    case 'door': opPlaceDoor(ed, o.tap); break;
    case 'select': { const { cellPx, gridWpx, gridHpx } = geom(ed.cols, ed.rows);
      const h = hitTest(o.down, ed.rooms, ed.selectedId, cellPx, gridWpx, gridHpx, HANDLE_TOUCH, HANDLE_GRIP / 2);
      ed.selectedId = h ? h.roomId : null; break; }
    case 'remove': opRemove(ed); break;
  }
}

function run(iterations) {
  let failures = 0, firstFail = null;
  const tally = {}; // category → count of failing seeds
  for (let seed = 1; seed <= iterations; seed++) {
    const rng = mulberry32(seed);
    const ed = makeEditor();
    const seq = [];
    const steps = 6 + ((rng() * 20) | 0);
    for (let s = 0; s < steps; s++) {
      const o = randomOp(rng, ed);
      seq.push(o);
      applyOp(ed, o);
      const problems = checkInvariants(ed);
      if (problems.length) {
        failures++;
        const cat = problems[0].split(/[ ]/)[0]; // the leading tag, e.g. OVERLAP / DRAW≠HIT
        tally[cat] = (tally[cat] || 0) + 1;
        if (!firstFail) firstFail = { seed, step: s, problems, seq: seq.slice(), state: JSON.parse(JSON.stringify(ed)) };
        break;
      }
    }
  }
  console.log(`\nFuzzed ${iterations} random operation-orders (up to ~26 ops each).`);
  if (failures === 0) {
    console.log('✅ NO invariant violations — no overlap, no off-grid room, door always on footprint,');
    console.log('   draw==hit at every room centre, and a selected room\'s centre always moves (never resizes).');
  } else {
    console.log(`❌ ${failures} of ${iterations} sequences violated an invariant.`);
    console.log(`   by category:`, tally);
    console.log(`   first failure: seed=${firstFail.seed} step=${firstFail.step}`);
    console.log(`   problems: ${firstFail.problems.join(' | ')}`);
    console.log(`   final grid ${firstFail.state.cols}×${firstFail.state.rows}, rooms:`,
      firstFail.state.rooms.map((r) => `${r.id}:${r.col},${r.row},${r.w}x${r.h}`).join('  '));
    console.log(`   sequence:`);
    for (const o of firstFail.seq) console.log(`     `, JSON.stringify(o));
    process.exitCode = 1;
  }
}

const iters = parseInt(process.argv[2] || '20000', 10);
run(iters);

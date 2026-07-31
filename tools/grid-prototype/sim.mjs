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
// Node built-ins only, no packages. Keep it in lock-step with the Kotlin — this is a mirror, not a
// second design.
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

// Fault injection for suites A–D. Suite E reads the same flag into its own local. An invariant that
// has never been seen to FAIL is an invariant nobody has tested — every check here is proven to bite
// by running the suite with the corresponding `--inject=` and watching it go red.
const INJECT = (process.argv.find((a) => a.startsWith('--inject=')) || '').split('=')[1] || null;

// ----------------------------------------------------------------------------------------------
// Ported pure maths — GridEditing.kt
// ----------------------------------------------------------------------------------------------
const MIN_GRID = 4, MAX_GRID = 10;
const GRID = 8; // NewPlanViewModel.GRID — the row-flip origin (engine y = GRID − row). Must match Kotlin.
const clampInt = (v, lo, hi) => Math.max(lo, Math.min(hi, v));
const clampF = (v, lo, hi) => Math.max(lo, Math.min(hi, v));

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

/** GridResize.gridSizeForRooms — the plot shape a reopened home is drawn on (tightest enclosing grid). */
function gridSizeForRooms(rooms) {
  const cols = clampInt(rooms.length ? Math.max(...rooms.map((r) => r.col + r.w)) : GRID, MIN_GRID, MAX_GRID);
  const rows = clampInt(rooms.length ? Math.max(...rooms.map((r) => r.row + r.h)) : GRID, MIN_GRID, MAX_GRID);
  return [cols, rows];
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
// Ported grid⇄engine flip — PlanConversion.kt (buildEnginePlan / gridRoomsFromPlan / gridDoorFromPlan)
// This is the reopen round-trip + the score's translation-invariance seam. A room's grid (col,row,w,h)
// becomes an engine polygon with y = GRID − row (north grows as row decreases); the footprint is the
// rooms' bounding box; the door becomes a point + wall span on that footprint perimeter. gridRoomsFromPlan
// / gridDoorFromPlan are the exact inverses, used on reopen. We cannot run the Kotlin scoring engine in
// JS, but the engine is PROVEN translation-invariant (Kotlin StressCorpusTest/rotation-invariance), so
// "same normalized geometry ⇒ same score" — we assert the geometry, which is the portable half.
// ----------------------------------------------------------------------------------------------
const ex = (col) => col;          // east grows with column
const ey = (row) => GRID - row;   // north grows as row decreases

function doorGeometry(d, minC, maxC, minR, maxR) {
  const alongCol = clampF(d.cell + 0.5, minC + 0.5, maxC - 0.5);
  const alongRow = clampF(d.cell + 0.5, minR + 0.5, maxR - 0.5);
  switch (d.side) {
    case 'N': return { centre: { x: ex(alongCol), y: ey(minR) }, ws: { x: ex(minC), y: ey(minR) }, we: { x: ex(maxC), y: ey(minR) } };
    case 'S': return { centre: { x: ex(alongCol), y: ey(maxR) }, ws: { x: ex(minC), y: ey(maxR) }, we: { x: ex(maxC), y: ey(maxR) } };
    case 'E': return { centre: { x: ex(maxC), y: ey(alongRow) }, ws: { x: ex(maxC), y: ey(minR) }, we: { x: ex(maxC), y: ey(maxR) } };
    case 'W': return { centre: { x: ex(minC), y: ey(alongRow) }, ws: { x: ex(minC), y: ey(minR) }, we: { x: ex(minC), y: ey(maxR) } };
  }
}

function buildEnginePlan(rooms, door) {
  if (rooms.length === 0) return null;
  const engineRooms = rooms.map((r) => {
    const x0 = ex(r.col), x1 = ex(r.col + r.w);
    const yTop = ey(r.row), yBottom = ey(r.row + r.h);
    return { id: r.id, type: r.type, polygon: [{ x: x0, y: yBottom }, { x: x1, y: yBottom }, { x: x1, y: yTop }, { x: x0, y: yTop }] };
  });
  const minC = Math.min(...rooms.map((r) => r.col)), maxC = Math.max(...rooms.map((r) => r.col + r.w));
  const minR = Math.min(...rooms.map((r) => r.row)), maxR = Math.max(...rooms.map((r) => r.row + r.h));
  const outline = [{ x: ex(minC), y: ey(maxR) }, { x: ex(maxC), y: ey(maxR) }, { x: ex(maxC), y: ey(minR) }, { x: ex(minC), y: ey(minR) }];
  let doors = [];
  if (door) { const g = doorGeometry(door, minC, maxC, minR, maxR); doors = [{ centre: g.centre, wallStart: g.ws, wallEnd: g.we, isMainEntrance: true }]; }
  return { rooms: engineRooms, outline, doors };
}

function gridRoomsFromPlan(plan) {
  return plan.rooms.map((room) => {
    if (room.polygon.length === 0) return null;
    const xs = room.polygon.map((p) => p.x), ys = room.polygon.map((p) => p.y);
    const x0 = Math.min(...xs), x1 = Math.max(...xs), yTop = Math.max(...ys), yBottom = Math.min(...ys);
    return {
      id: room.id, type: room.type,
      col: Math.round(x0), row: Math.round(GRID - yTop),
      w: Math.max(1, Math.round(x1 - x0)), h: Math.max(1, Math.round(yTop - yBottom)),
    };
  }).filter(Boolean);
}

function gridDoorFromPlan(plan, rooms) {
  const d = plan.doors.find((x) => x.isMainEntrance);
  if (!d) return null;
  if (rooms.length === 0) return null;
  const minC = Math.min(...rooms.map((r) => r.col)), maxC = Math.max(...rooms.map((r) => r.col + r.w));
  const minR = Math.min(...rooms.map((r) => r.row)), maxR = Math.max(...rooms.map((r) => r.row + r.h));
  const yNorth = GRID - minR, ySouth = GRID - maxR, xEast = maxC, xWest = minC, eps = 1e-6;
  const horizontal = Math.abs(d.wallStart.y - d.wallEnd.y) < eps;
  if (horizontal && Math.abs(d.centre.y - yNorth) < eps) return { side: 'N', cell: Math.round(d.centre.x - 0.5) };
  if (horizontal && Math.abs(d.centre.y - ySouth) < eps) return { side: 'S', cell: Math.round(d.centre.x - 0.5) };
  if (Math.abs(d.centre.x - xEast) < eps) return { side: 'E', cell: Math.round((GRID - d.centre.y) - 0.5) };
  if (Math.abs(d.centre.x - xWest) < eps) return { side: 'W', cell: Math.round((GRID - d.centre.y) - 0.5) };
  return null;
}

/**
 * Where the door marker is DRAWN — PlanConversion.doorMarkerCell (the v0.3.9 fix). It pins the marker
 * to the rooms' FOOTPRINT edge (the house's outer wall), never the plot edge: that is where the engine
 * scores it, where placeDoor clamps it, and where reopen lands it. Returns the [col,row] cell.
 */
function doorMarkerCell(door, rooms, cols, rows) {
  const minC = rooms.length ? Math.min(...rooms.map((r) => r.col)) : 0;
  const maxC = rooms.length ? Math.max(...rooms.map((r) => r.col + r.w)) : cols;
  const minR = rooms.length ? Math.min(...rooms.map((r) => r.row)) : 0;
  const maxR = rooms.length ? Math.max(...rooms.map((r) => r.row + r.h)) : rows;
  switch (door.side) {
    case 'N': return [door.cell, minR];
    case 'S': return [door.cell, maxR - 1];
    case 'W': return [minC, door.cell];
    case 'E': return [maxC - 1, door.cell];
  }
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
  // `violations` collects problems an operation can only detect by comparing BEFORE with AFTER —
  // "the retype moved a room" is not a property of the resulting state, it is a property of the
  // change. checkInvariants drains it, so such a finding reports exactly like any other.
  return { rooms: [], door: null, cols: 8, rows: 8, selectedId: null, nextId: 0, violations: [] };
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

// Compose default touch slop, in the sim's dp-px units (the grid is drawn ~1dp:1px, cellPx = 312/cols).
// A gesture must exceed this before it becomes a drag; below it, the finger-down's selection is the
// whole effect (a tap). Mirrors awaitTouchSlopOrCancellation.
const TOUCH_SLOP = 8;

// Move or resize with a MULTI-STEP path — a sequence of absolute finger positions, not a single delta.
// This is what actually exercises snapWithHysteresis (its `current` state carries between events, so the
// final snapped cell is PATH-dependent) and the frozen-start-rect + blocked-carry state machine (rect
// holds the last non-overlapping attempt while `attempted`/`steps` keep advancing). A single-delta drag
// can never reach the intermediate hysteresis/blocked states, which is where a gesture bug hides.
//
// Mirrors GuidedGridScreen's arbiter exactly: down → hitTest (uses the CURRENT selectedId, so grips only
// win on the already-selected room) → touch-slop gate → for each event, raw = (absolute pos − down),
// advance(raw) derived ALWAYS from the frozen start rect. Lift commits `state.rect`.
function opDrag(ed, downPx, pathPx) {
  const { cellPx, gridWpx, gridHpx } = geom(ed.cols, ed.rows);
  const hit = hitTest(downPx, ed.rooms, ed.selectedId, cellPx, gridWpx, gridHpx, HANDLE_TOUCH, HANDLE_GRIP / 2);
  if (!hit) { ed.selectedId = null; return; } // empty space → deselect, page could scroll
  if (hit.roomId !== ed.selectedId) ed.selectedId = hit.roomId;

  // Touch-slop gate: the drag only begins once the finger has moved past TOUCH_SLOP from the down point.
  // Before that a lift is a tap (the selection above was its whole effect). Find the first waypoint that
  // crosses slop; if none do, it stays a tap.
  const dist = (p) => Math.hypot(p[0] - downPx[0], p[1] - downPx[1]);
  const startIdx = pathPx.findIndex((p) => dist(p) > TOUCH_SLOP);
  if (startIdx === -1) return; // never exceeded slop → tap, selection only

  const handle = hit.handle;
  const start = { ...hit.rect }; // FROZEN — every attempt is derived from this, never from the preview
  const others = ed.rooms.filter((r) => r.id !== hit.roomId);

  let steps = [0, 0];
  let rect = start;      // last non-overlapping position (what a lift commits)
  let attempted = start; // where the finger actually is (differs from rect only while blocked)
  let blocked = false;
  const advance = (raw) => {
    const stepCol = snapWithHysteresis(raw[0] / cellPx, steps[0]);
    const stepRow = snapWithHysteresis(raw[1] / cellPx, steps[1]);
    if (stepCol === steps[0] && stepRow === steps[1]) return;
    const attempt = handle == null ? moveBy(start, stepCol, stepRow, ed.cols, ed.rows)
      : resizeBy(start, handle, stepCol, stepRow, ed.cols, ed.rows);
    const bad = anyOverlap(attempt, others);
    steps = [stepCol, stepRow];
    attempted = attempt;
    rect = bad ? rect : attempt;
    blocked = bad;
  };

  // raw at each event = (absolute finger pos − down pos), exactly as the real accumulator sums to.
  for (let i = startIdx; i < pathPx.length; i++) {
    advance([pathPx[i][0] - downPx[0], pathPx[i][1] - downPx[1]]);
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

// doorForTap (PlanConversion.kt) — which wall a tap MEANS. Distances are to the HOUSE's wall lines,
// never the plot's edges (UAT S8), signed so a tap out in the margin picks the wall it lies beyond,
// and in FRACTIONAL cells so a 1-cell-deep house can still tell north from south. The plot is not a
// parameter at all: that is the fix stated structurally.
function doorForTap(xCells, yCells, rooms) {
  if (rooms.length === 0) return null;
  const fMinC = Math.min(...rooms.map((r) => r.col)), fMaxC = Math.max(...rooms.map((r) => r.col + r.w));
  const fMinR = Math.min(...rooms.map((r) => r.row)), fMaxR = Math.max(...rooms.map((r) => r.row + r.h));
  const distN = yCells - fMinR, distS = fMaxR - yCells, distW = xCells - fMinC, distE = fMaxC - xCells;
  const cCol = clampInt(Math.floor(xCells), fMinC, fMaxC - 1);
  const cRow = clampInt(Math.floor(yCells), fMinR, fMaxR - 1);
  const m = Math.min(distN, distS, distW, distE);
  return m === distN ? { side: 'N', cell: cCol } : m === distS ? { side: 'S', cell: cCol }
    : m === distW ? { side: 'W', cell: cRow } : { side: 'E', cell: cRow };
}

function opPlaceDoor(ed, tapPx) {
  if (ed.rooms.length === 0) return;
  const { cellPx } = geom(ed.cols, ed.rows);
  const d = doorForTap(tapPx[0] / cellPx, tapPx[1] / cellPx, ed.rooms);
  if (d) ed.door = d;
}

function opRemove(ed) {
  if (!ed.selectedId) return;
  ed.rooms = ed.rooms.filter((r) => r.id !== ed.selectedId);
  ed.door = clampDoorToRooms(ed.door, ed.rooms);
  ed.selectedId = null;
}

// ----------------------------------------------------------------------------------------------
// The WCAG 2.2 SC 2.5.7 BUTTON paths — GuidedGridScreen.applyToSelected / SelectedRoomTools /
// the plot-size EditorKeys / RoomTile's semantics onClick.
//
// ⚠ WHY THESE ARE MIRRORED SEPARATELY: this arithmetic lives HAND-WRITTEN INSIDE THE COMPOSABLE, not
// in the pure `shared` module — `onResize`'s clamp in particular (`(w+dw).coerceIn(1, cols-col)`) is a
// different code path from `resizeBy`, which is the only resize the gesture fuzz ever reaches. These
// are also the paths an older / less phone-literate user (a large part of this audience) and every
// TalkBack user actually uses, so they carry the same score-corrupting risk as a drag and deserve the
// same invariant pressure. RoomTile's `onSelect` is likewise a DIFFERENT selection path from hitTest
// (a semantics click, no pointer maths at all).
// ----------------------------------------------------------------------------------------------

/** GuidedGridScreen.applyToSelected — refused on overlap, no-op when nothing changes. */
function applyToSelected(ed, next) {
  const sel = ed.rooms.find((r) => r.id === ed.selectedId);
  if (!sel) return;
  if (anyOverlap(next, ed.rooms.filter((r) => r.id !== sel.id))) return;   // refused (haptics.reject)
  if (next.col === sel.col && next.row === sel.row && next.w === sel.w && next.h === sel.h) return;
  ed.rooms = ed.rooms.map((r) => (r.id === sel.id ? { ...r, ...next } : r));
  ed.door = clampDoorToRooms(ed.door, ed.rooms);   // onRoomsChange → vm.updateRooms
}

/** SelectedRoomTools onNudge — the ◀▲▼▶ move arrows. */
function opNudge(ed, dCol, dRow) {
  const sel = ed.rooms.find((r) => r.id === ed.selectedId);
  if (!sel) return;
  applyToSelected(ed, moveBy(sel, dCol, dRow, ed.cols, ed.rows));
}

/** SelectedRoomTools onResize — the W/H −/+ steppers. Grows east/south only, clamped to the plot. */
function opStepResize(ed, dw, dh) {
  const sel = ed.rooms.find((r) => r.id === ed.selectedId);
  if (!sel) return;
  applyToSelected(ed, {
    col: sel.col, row: sel.row,
    w: clampInt(sel.w + dw, 1, ed.cols - sel.col),
    h: clampInt(sel.h + dh, 1, ed.rows - sel.row),
  });
}

/** The "Plot size" −/+ keys: onGridChange(cols ± 1, rows) → updateGrid → resolveGridResize. */
function opPlotStep(ed, dCols, dRows) {
  opPlotResize(ed, ed.cols + dCols, ed.rows + dRows);
}

/** RoomTile's semantics onClick (`onSelect`) — the TalkBack selection path: no pointer maths. */
function opSelectTile(ed, index) {
  if (ed.rooms.length === 0) return;
  ed.selectedId = ed.rooms[index % ed.rooms.length].id;
}

/** SelectedRoomTools "Done" — clears the selection without touching geometry. */
function opDone(ed) { ed.selectedId = null; }

/** RoomRetype.kt — change one room's kind, touching nothing else. Same instance when nothing moves. */
function retypeRoom(rooms, id, type) {
  const i = rooms.findIndex((r) => r.id === id);
  if (i < 0 || rooms[i].type === type) return rooms;
  return rooms.map((r, idx) => (idx === i ? { ...r, type } : r));
}

/**
 * ⭐ SelectedRoomTools' room-type picker — `onRetype = onRoomsChange(retypeRoom(rooms, id, type))`.
 *
 * Changing a room's kind changes the SCORE (a master bedroom weighs 3.0 against a bedroom's 1.5; and
 * CORRIDOR carries no rule at all, so a lobby read as one is NOT_SCORED and drops out of the weighted
 * average entirely), so it travels the same onRoomsChange → updateRooms road as a drag. What it must
 * never do is touch geometry — and that is the whole risk here. The obvious
 * "helpful" future edit is to re-pack or re-clamp after a retype, exactly as `updateGrid` does; that
 * would silently move a room the user did not touch, which is the same class of defect as the
 * relocating packer Suite E's TRIMMED-MOVED exists to stop.
 *
 * So the check is BEFORE-vs-AFTER, not a property of the final state: every id, position, size and
 * the list order must be identical, only the target's kind may differ, and the door may not move.
 */
function opRetype(ed, typeIndex) {
  const sel = ed.rooms.find((r) => r.id === ed.selectedId);
  if (!sel) return;
  const type = ALL_TYPES[typeIndex % ALL_TYPES.length];
  const before = ed.rooms.map((r) => ({ ...r }));
  const doorBefore = ed.door ? { ...ed.door } : null;

  // Three FAULT INJECTIONS, each a mistake someone would plausibly make rather than a contrivance.
  // Note what they have in common: every one leaves the editor in a perfectly LEGAL state, so no
  // state-shaped invariant notices. That is why this check compares before with after.
  if (INJECT === 'retype-readds') {
    // ⭐ The naive implementation — and exactly the workaround this control replaces: remove the room
    // and add a new one of the chosen kind. The re-add goes through the NEW-room path, so it lands in
    // the first free slot instead of where the user's room actually was. Fires RETYPE-MOVED.
    const others = ed.rooms.filter((r) => r.id !== sel.id);
    const slot = firstFreeSlot(sel.w, sel.h, ed.cols, ed.rows, others);
    ed.rooms = slot ? [...others, { ...sel, type, col: slot[0], row: slot[1] }] : others;
  } else if (INJECT === 'retype-spreads') {
    // Keying the change on the room's KIND instead of its id — one character's difference in Kotlin,
    // and it silently retypes every other bedroom in the home. Fires RETYPE-SPREAD.
    ed.rooms = ed.rooms.map((r) => (r.type === sel.type ? { ...r, type } : r));
  } else if (INJECT === 'retype-drops-door') {
    // Routing a retype through the plot-resize style of update, which clears a door on a wall that no
    // longer exists — except here every wall still exists. Fires RETYPE-DOOR.
    ed.rooms = retypeRoom(ed.rooms, sel.id, type);
    ed.door = null;
  } else {
    ed.rooms = retypeRoom(ed.rooms, sel.id, type);
  }
  ed.door = clampDoorToRooms(ed.door, ed.rooms);

  if (ed.rooms.length !== before.length) {
    ed.violations.push(`RETYPE-COUNT ${before.length}→${ed.rooms.length}`);
    return;
  }
  for (let i = 0; i < before.length; i++) {
    const a = before[i], b = ed.rooms[i];
    if (a.id !== b.id || a.col !== b.col || a.row !== b.row || a.w !== b.w || a.h !== b.h) {
      ed.violations.push(
        `RETYPE-MOVED ${a.id} ${a.col},${a.row},${a.w}x${a.h}→${b.col},${b.row},${b.w}x${b.h}`);
    }
    const shouldChange = a.id === sel.id;
    if (!shouldChange && a.type !== b.type) ed.violations.push(`RETYPE-SPREAD ${a.id} ${a.type}→${b.type}`);
    if (shouldChange && b.type !== type) ed.violations.push(`RETYPE-MISSED ${a.id} wanted ${type}, got ${b.type}`);
  }
  const d = ed.door;
  const moved = (!d) !== (!doorBefore) || (d && doorBefore && (d.side !== doorBefore.side || d.cell !== doorBefore.cell));
  if (moved) {
    ed.violations.push(
      `RETYPE-DOOR ${doorBefore ? doorBefore.side + '@' + doorBefore.cell : 'null'}` +
      `→${d ? d.side + '@' + d.cell : 'null'}`);
  }
}

// ----------------------------------------------------------------------------------------------
// Invariants — checked after EVERY operation
// ----------------------------------------------------------------------------------------------
function checkInvariants(ed) {
  // Findings an operation could only make by comparing BEFORE with AFTER (see opRetype): "a room
  // moved when only its kind should have changed" is a property of the change, not of the result.
  const problems = ed.violations.splice(0);
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
  // 6. REOPEN ROUND-TRIP: the whole live state must survive save→reload through the engine-plan flip.
  // buildEnginePlan → gridRoomsFromPlan must return the rooms byte-for-byte (id/type/col/row/w/h), and
  // gridDoorFromPlan must return the SAME door — every editor op keeps the door clamped onto the
  // footprint, so the flip (which clamps identically) must recover it exactly. A mismatch means a home
  // would reopen showing something different from what was scored (the F4/S2 class, proven per-state).
  if (ed.rooms.length) {
    const plan = buildEnginePlan(ed.rooms, ed.door);
    const rr = gridRoomsFromPlan(plan);
    if (rr.length !== ed.rooms.length) problems.push(`REOPEN-ROOM-COUNT ${rr.length}≠${ed.rooms.length}`);
    else for (let i = 0; i < rr.length; i++) {
      const a = rr[i], b = ed.rooms[i];
      if (a.id !== b.id || a.type !== b.type || a.col !== b.col || a.row !== b.row || a.w !== b.w || a.h !== b.h)
        problems.push(`REOPEN-ROOM ${b.id} ${b.col},${b.row},${b.w}x${b.h}→${a.col},${a.row},${a.w}x${a.h}`);
    }
    const rd = gridDoorFromPlan(plan, rr);
    const want = ed.door;
    const doorEq = (!rd && !want) || (rd && want && rd.side === want.side && rd.cell === want.cell);
    if (!doorEq) problems.push(`REOPEN-DOOR ${want ? want.side + '@' + want.cell : 'null'}→${rd ? rd.side + '@' + rd.cell : 'null'}`);
    // 6b. The plot `load()` re-derives for the reopened rooms must CONTAIN them. gridSizeForRooms
    // clamps to MAX_GRID, so a stored room reaching past that would reopen hanging outside the plot —
    // and every finger calculation would then be in a different coordinate space from the drawing
    // (the exact v0.3.7 class of bug, arriving through the database instead of a stepper).
    const [dc, dr] = gridSizeForRooms(rr);
    for (const r of rr)
      if (r.col < 0 || r.row < 0 || right(r) > dc || bottom(r) > dr)
        problems.push(`REOPEN-PLOT ${r.id} ${r.col},${r.row},${r.w}x${r.h} outside re-derived ${dc}×${dr}`);
  }
  // 7. DOOR MARKER: where the door is DRAWN (doorMarkerCell — the v0.3.9 fix) must sit ON the
  // footprint wall that matches its side, and be a real in-grid cell. This is the "displayed ==
  // scored == reloaded" guarantee at the DRAWING end: invariant 3 proves the door's stored cell is on
  // the footprint, this proves the marker the user actually sees is on the same wall.
  if (ed.door && ed.rooms.length) {
    const minC = Math.min(...ed.rooms.map((r) => r.col)), maxC = Math.max(...ed.rooms.map((r) => r.col + r.w));
    const minR = Math.min(...ed.rooms.map((r) => r.row)), maxR = Math.max(...ed.rooms.map((r) => r.row + r.h));
    const [mc, mr] = doorMarkerCell(ed.door, ed.rooms, ed.cols, ed.rows);
    if (mc < 0 || mr < 0 || mc >= ed.cols || mr >= ed.rows) problems.push(`MARKER-OFFGRID ${mc},${mr} in ${ed.cols}×${ed.rows}`);
    // On the named wall, and within the footprint's span along that wall.
    const onWall =
      ed.door.side === 'N' ? (mr === minR && mc >= minC && mc < maxC) :
      ed.door.side === 'S' ? (mr === maxR - 1 && mc >= minC && mc < maxC) :
      ed.door.side === 'W' ? (mc === minC && mr >= minR && mr < maxR) :
                             (mc === maxC - 1 && mr >= minR && mr < maxR);
    if (!onWall) problems.push(`MARKER-OFF-WALL ${ed.door.side}@${ed.door.cell} drawn ${mc},${mr} footprint ${minC}..${maxC},${minR}..${maxR}`);
  }
  // 8. The plot itself never leaves its allowed range (the steppers clamp to MIN_GRID..MAX_GRID).
  if (ed.cols < MIN_GRID || ed.cols > MAX_GRID || ed.rows < MIN_GRID || ed.rows > MAX_GRID)
    problems.push(`PLOT-OUT-OF-RANGE ${ed.cols}×${ed.rows}`);
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
// UiMappers.ALL_ROOM_TYPES — every kind the room-type picker offers. Deliberately longer than the
// palette's eleven: the eight after "Balcony" can be read off a plan but have never been placeable,
// so before the picker existed a room read as one of them could be deleted and never restored.
const ALL_TYPES = [
  'Living', 'Kitchen', 'Master', 'Bedroom', 'Pooja', 'Toilet', 'Stairs', 'Study', 'Dining', 'Store',
  'Balcony', 'Entrance', 'Corridor', 'Utility', 'Bathroom', 'Guest', 'Courtyard', 'Garage', 'Basement',
];

function randomOp(rng, ed) {
  const { cellPx } = geom(ed.cols, ed.rows);
  const px = () => [rng() * ed.cols * cellPx, rng() * ed.rows * cellPx];
  const kind = rng();
  if (kind < 0.30 || ed.rooms.length === 0) return { op: 'place', type: TYPES[(rng() * TYPES.length) | 0], down: px(), target: px() };
  if (kind < 0.55) {
    // A MULTI-STEP path: 1–5 waypoints the finger slides through before lifting. This is what reaches
    // the hysteresis/blocked intermediate states a single delta can't (task a). Bias the down point at
    // a room or its grip so the drag actually grabs something a good fraction of the time.
    const n = 1 + ((rng() * 5) | 0);
    const path = [];
    for (let i = 0; i < n; i++) path.push(px());
    let down = px();
    if (ed.rooms.length && rng() < 0.7) { // aim at a random room's centre or a corner
      const r = ed.rooms[(rng() * ed.rooms.length) | 0];
      const anchor = rng() < 0.5 ? drawnCentrePx(r, cellPx)
        : [handleAnchor(r, handlesFor(r)[(rng() * handlesFor(r).length) | 0])[0] * cellPx,
           handleAnchor(r, handlesFor(r)[(rng() * handlesFor(r).length) | 0])[1] * cellPx];
      down = [anchor[0] + (rng() - 0.5) * cellPx, anchor[1] + (rng() - 0.5) * cellPx];
    }
    return { op: 'drag', down, path };
  }
  if (kind < 0.73) return { op: 'plot', cols: 3 + ((rng() * 9) | 0), rows: 3 + ((rng() * 9) | 0) }; // deliberately over/under range to test clamp+refuse
  if (kind < 0.75) return { op: 'retype', type: (rng() * ALL_TYPES.length) | 0 };
  if (kind < 0.85) return { op: 'door', tap: px() };
  if (kind < 0.93) return { op: 'select', down: px() };
  return { op: 'remove' };
}

function applyOp(ed, o) {
  switch (o.op) {
    case 'place': opPlace(ed, o.type, o.down, o.target); break;
    case 'drag': opDrag(ed, o.down, o.path); break;
    case 'plot': opPlotResize(ed, o.cols, o.rows); break;
    case 'door': opPlaceDoor(ed, o.tap); break;
    case 'select': { const { cellPx, gridWpx, gridHpx } = geom(ed.cols, ed.rows);
      const h = hitTest(o.down, ed.rooms, ed.selectedId, cellPx, gridWpx, gridHpx, HANDLE_TOUCH, HANDLE_GRIP / 2);
      ed.selectedId = h ? h.roomId : null; break; }
    case 'remove': opRemove(ed); break;
    // --- button (WCAG) paths, Suite D ---
    case 'nudge': opNudge(ed, o.dc, o.dr); break;
    case 'step': opStepResize(ed, o.dw, o.dh); break;
    case 'plotkey': opPlotStep(ed, o.dc, o.dr); break;
    case 'tile': opSelectTile(ed, o.index); break;
    case 'done': opDone(ed); break;
    case 'retype': opRetype(ed, o.type); break;
  }
}

// ----------------------------------------------------------------------------------------------
// Suite D — the WCAG BUTTON paths, fuzzed and INTERLEAVED with finger gestures.
//
// A real user mixes the two constantly (drag a room roughly into place, then nudge it one cell with
// an arrow, then resize the plot), and a TalkBack user uses nothing BUT the buttons. The button
// arithmetic is hand-written inside the Composable rather than in the tested `shared` module, so it
// is the least-proven geometry in the editor. Same invariants as Suite A — plus the new door-marker
// and plot-range checks, which every suite now carries.
// ----------------------------------------------------------------------------------------------
function randomButtonOp(rng, ed) {
  const { cellPx } = geom(ed.cols, ed.rows);
  const px = () => [rng() * ed.cols * cellPx, rng() * ed.rows * cellPx];
  const kind = rng();
  // Always keep a room supply, and keep a selection alive so the arrows/steppers actually do work.
  if (ed.rooms.length === 0) return { op: 'place', type: TYPES[(rng() * TYPES.length) | 0], down: px(), target: px() };
  if (!ed.selectedId && kind < 0.5) return { op: 'tile', index: (rng() * ed.rooms.length) | 0 };
  if (kind < 0.14) return { op: 'place', type: TYPES[(rng() * TYPES.length) | 0], down: px(), target: px() };
  if (kind < 0.22) return { op: 'tile', index: (rng() * ed.rooms.length) | 0 };
  if (kind < 0.44) { // a move arrow
    const d = [[-1, 0], [1, 0], [0, -1], [0, 1]][(rng() * 4) | 0];
    return { op: 'nudge', dc: d[0], dr: d[1] };
  }
  if (kind < 0.66) { // a size stepper
    const d = [[-1, 0], [1, 0], [0, -1], [0, 1]][(rng() * 4) | 0];
    return { op: 'step', dw: d[0], dh: d[1] };
  }
  if (kind < 0.80) { // a plot-size key (one step at a time, exactly as the UI does)
    const d = [[-1, 0], [1, 0], [0, -1], [0, 1]][(rng() * 4) | 0];
    return { op: 'plotkey', dc: d[0], dr: d[1] };
  }
  if (kind < 0.86) return { op: 'retype', type: (rng() * ALL_TYPES.length) | 0 };
  if (kind < 0.90) return { op: 'door', tap: px() };
  if (kind < 0.94) return { op: 'done' };
  if (kind < 0.97) return { op: 'remove' };
  // Occasionally a real finger drag, so the two paths are proven to compose.
  const n = 1 + ((rng() * 3) | 0);
  const path = []; for (let i = 0; i < n; i++) path.push(px());
  return { op: 'drag', down: px(), path };
}

// ----------------------------------------------------------------------------------------------
// Suite B — resizeBy invariants (pure, in isolation): the corner OPPOSITE the dragged handle stays
// pinned, and the rect never inverts below 1×1 or leaves the grid, for ANY delta. The editor only ever
// resizes with a handle from handlesFor(rect), so we test exactly those. (Task b.)
// ----------------------------------------------------------------------------------------------
function pinnedCorner(handle) {
  // The corner that must NOT move, expressed as which of {col,row,right,bottom} of `start` is preserved.
  switch (handle) {
    case 'BR': return { col: true, row: true };        // TL pinned
    case 'TL': return { right: true, bottom: true };    // BR pinned
    case 'TR': return { col: true, bottom: true };      // BL pinned
    case 'BL': return { right: true, row: true };       // TR pinned
  }
}
function fuzzResize(iters) {
  const fails = [];
  for (let seed = 1; seed <= iters; seed++) {
    const rng = mulberry32(seed * 2654435761);
    const cols = MIN_GRID + ((rng() * (MAX_GRID - MIN_GRID + 1)) | 0);
    const rows = MIN_GRID + ((rng() * (MAX_GRID - MIN_GRID + 1)) | 0);
    const w = 1 + ((rng() * cols) | 0), h = 1 + ((rng() * rows) | 0);
    const start = clampToGrid({ col: (rng() * cols) | 0, row: (rng() * rows) | 0, w, h }, cols, rows);
    const hs = handlesFor(start);
    const handle = hs[(rng() * hs.length) | 0];
    const dCol = ((rng() * 41) | 0) - 20, dRow = ((rng() * 41) | 0) - 20;
    const out = resizeBy(start, handle, dCol, dRow, cols, rows);
    const problems = [];
    if (out.w < 1 || out.h < 1) problems.push(`INVERT ${out.w}×${out.h}`);
    if (out.col < 0 || out.row < 0 || right(out) > cols || bottom(out) > rows) problems.push(`OFFGRID ${out.col},${out.row},${out.w}x${out.h} in ${cols}×${rows}`);
    const pin = pinnedCorner(handle);
    if (pin.col && out.col !== start.col) problems.push(`UNPINNED-LEFT ${out.col}≠${start.col}`);
    if (pin.row && out.row !== start.row) problems.push(`UNPINNED-TOP ${out.row}≠${start.row}`);
    if (pin.right && right(out) !== right(start)) problems.push(`UNPINNED-RIGHT ${right(out)}≠${right(start)}`);
    if (pin.bottom && bottom(out) !== bottom(start)) problems.push(`UNPINNED-BOTTOM ${bottom(out)}≠${bottom(start)}`);
    if (problems.length) fails.push({ seed, start, handle, dCol, dRow, cols, rows, out, problems });
  }
  return fails;
}

// ----------------------------------------------------------------------------------------------
// Suite C — reopen round-trip, score translation-invariance, and door-side stability on thin footprints
// over independent random footprints (not just editor-reachable states). (Tasks c + d.)
// ----------------------------------------------------------------------------------------------
function randomRooms(rng, cols, rows, n) {
  const placed = [];
  for (let k = 0; k < n; k++) {
    // Bias toward THIN rooms (w or h = 1) so the thin-footprint door classification is well exercised.
    const w = Math.min(cols, rng() < 0.5 ? 1 : 1 + ((rng() * cols) | 0));
    const h = Math.min(rows, rng() < 0.5 ? 1 : 1 + ((rng() * rows) | 0));
    const slot = firstFreeSlot(w, h, cols, rows, placed);
    if (!slot) continue;
    placed.push({ id: `room-${k}`, type: TYPES[(rng() * TYPES.length) | 0], col: slot[0], row: slot[1], w, h });
  }
  return placed;
}
function normPlan(plan) {
  // Subtract the footprint's min corner from every point: two configs that differ only by a rigid
  // translation normalize to byte-identical geometry, which the (translation-invariant) engine scores
  // identically. Comparing this is the portable proxy for "the score is translation-invariant".
  const xs = plan.outline.map((p) => p.x), ys = plan.outline.map((p) => p.y);
  const ox = Math.min(...xs), oy = Math.min(...ys);
  const s = (p) => ({ x: p.x - ox, y: p.y - oy });
  return JSON.stringify({
    rooms: plan.rooms.map((r) => ({ id: r.id, type: r.type, polygon: r.polygon.map(s) })),
    outline: plan.outline.map(s),
    doors: plan.doors.map((d) => ({ centre: s(d.centre), wallStart: s(d.wallStart), wallEnd: s(d.wallEnd) })),
  });
}
function translateConfig(rooms, door, dc, dr) {
  const r2 = rooms.map((r) => ({ ...r, col: r.col + dc, row: r.row + dr }));
  let d2 = door;
  if (door) d2 = (door.side === 'N' || door.side === 'S') ? { ...door, cell: door.cell + dc } : { ...door, cell: door.cell + dr };
  return [r2, d2];
}
function fuzzRoundTrip(iters) {
  const fails = [];
  for (let seed = 1; seed <= iters; seed++) {
    const rng = mulberry32(seed * 40503 + 7);
    const cols = MIN_GRID + ((rng() * (MAX_GRID - MIN_GRID + 1)) | 0);
    const rows = MIN_GRID + ((rng() * (MAX_GRID - MIN_GRID + 1)) | 0);
    const rooms = randomRooms(rng, cols, rows, 1 + ((rng() * 4) | 0));
    if (rooms.length === 0) continue;
    const minC = Math.min(...rooms.map((r) => r.col)), maxC = Math.max(...rooms.map((r) => r.col + r.w));
    const minR = Math.min(...rooms.map((r) => r.row)), maxR = Math.max(...rooms.map((r) => r.row + r.h));
    // A door, sometimes deliberately OFF the footprint so we prove recovery == clampDoorToRooms.
    let door = null;
    if (rng() < 0.85) {
      const side = ['N', 'S', 'E', 'W'][(rng() * 4) | 0];
      const off = rng() < 0.3; // stray beyond the footprint on purpose
      const cell = (side === 'N' || side === 'S')
        ? (off ? minC - 2 + ((rng() * (maxC - minC + 4)) | 0) : minC + ((rng() * (maxC - minC)) | 0))
        : (off ? minR - 2 + ((rng() * (maxR - minR + 4)) | 0) : minR + ((rng() * (maxR - minR)) | 0));
      door = { side, cell };
    }
    const problems = [];

    // (c1) Rooms survive the flip byte-for-byte.
    const plan = buildEnginePlan(rooms, door);
    const rr = gridRoomsFromPlan(plan);
    if (rr.length !== rooms.length) problems.push(`ROOM-COUNT ${rr.length}≠${rooms.length}`);
    else for (let i = 0; i < rr.length; i++) {
      const a = rr[i], b = rooms[i];
      if (a.col !== b.col || a.row !== b.row || a.w !== b.w || a.h !== b.h) problems.push(`ROOM ${b.id} ${b.col},${b.row},${b.w}x${b.h}→${a.col},${a.row},${a.w}x${a.h}`);
    }

    // (c2/d) Door recovers to exactly what clampDoorToRooms would store — side AND cell, incl. thin/off.
    const rd = gridDoorFromPlan(plan, rr);
    const want = clampDoorToRooms(door, rooms);
    const doorEq = (!rd && !want) || (rd && want && rd.side === want.side && rd.cell === want.cell);
    if (!doorEq) problems.push(`DOOR ${door ? door.side + '@' + door.cell : 'null'} want ${want ? want.side + '@' + want.cell : 'null'} got ${rd ? rd.side + '@' + rd.cell : 'null'}`);

    // (c4) ⭐ doorForTap picks the wall the finger MEANT (UAT S8). Two properties, over taps swept
    // across the whole plot including all four margins:
    //   - a tap strictly BEYOND exactly one of the house's walls must choose that wall (the S8 bug:
    //     distances were measured to the PLOT's edges, so a tap right above the house could come back
    //     West because the plot's west edge happened to be nearer);
    //   - every tap must already be footprint-clamped, so displayed == scored == reloaded.
    // Only the FIRST offending tap per footprint is reported: the sweep is ~hundreds of taps and a
    // real regression trips most of them, which would bury the useful line in a wall of text.
    let tapReported = false;
    for (let tx = 0; tx <= cols * 2 && !tapReported; tx++) {
      for (let ty = 0; ty <= rows * 2 && !tapReported; ty++) {
        const x = tx * 0.5, y = ty * 0.5;
        const d = doorForTap(x, y, rooms);
        if (!d) continue;
        const beyond = [];
        if (y < minR) beyond.push('N');
        if (y > maxR) beyond.push('S');
        if (x < minC) beyond.push('W');
        if (x > maxC) beyond.push('E');
        if (beyond.length === 1 && d.side !== beyond[0]) {
          problems.push(`TAP-WRONG-WALL (${x},${y}) beyond ${beyond[0]} but chose ${d.side} · footprint ${minC}..${maxC},${minR}..${maxR}`);
          tapReported = true;
        }
        const clamped = clampDoorToRooms(d, rooms);
        if (!clamped || clamped.side !== d.side || clamped.cell !== d.cell) {
          problems.push(`TAP-UNCLAMPED (${x},${y}) ${d.side}@${d.cell} → ${clamped ? clamped.side + '@' + clamped.cell : 'null'}`);
          tapReported = true;
        }
      }
    }

    // (c3) Score translation-invariance: shift the whole config; normalized engine geometry is identical.
    const dc = ((rng() * 13) | 0) - 6, dr = ((rng() * 13) | 0) - 6;
    if (dc !== 0 || dr !== 0) {
      const [r2, d2] = translateConfig(rooms, door, dc, dr);
      const p2 = buildEnginePlan(r2, d2);
      if (normPlan(plan) !== normPlan(p2)) problems.push(`TRANSLATION-VARIANT by (${dc},${dr})`);
    }

    if (problems.length) fails.push({ seed, cols, rows, rooms, door, problems });
  }
  return fails;
}

function run(iterations, gen = randomOp, seedSalt = 0) {
  let failures = 0, firstFail = null;
  const tally = {}; // category → count of failing seeds
  for (let seed = 1; seed <= iterations; seed++) {
    const rng = mulberry32(seed + seedSalt);
    const ed = makeEditor();
    const seq = [];
    const steps = 6 + ((rng() * 20) | 0);
    for (let s = 0; s < steps; s++) {
      const o = gen(rng, ed);
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
    console.log('   draw==hit at every room centre, a selected room\'s centre always moves (never resizes),');
    console.log('   the door marker is drawn on the matching footprint wall, the plot stays in range,');
    console.log('   and changing a room\'s KIND moves no room, spreads to no other room and never');
    console.log('   disturbs the front door.');
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

// ----------------------------------------------------------------------------------------------
// Suite E — ScanMapper: random MODEL OUTPUT in, editor-legal rooms out.
//
// WHY: scan hands the guided grid a plan the user has never touched. Everything the editor's four
// existing suites prove about a hand-built plan has to hold for a scanned one too — because
// downstream it IS one. So this fuzzes what a vision model can emit (NaN, inverted rects, boxes off
// the page, forty rooms, everything stacked, captions we don't know) and asserts the mapper's output
// is indistinguishable from something a finger drew.
//
// This is a MIRROR of shared/.../scan/ScanMapper.kt — the geometry half. The synonym table is not
// ported (it is data, tested exhaustively in RoomLabelsTest against real captions); a handful of
// representative captions is enough to exercise the map/drop/unknown branches.
//
// ⭐ THE INVARIANT THAT MATTERS MOST: `TRIMMED-MOVED`. A room the mapper emits must still overlap the
// cells it was READ at. Trimming shrinks, so it always does; `fitWithoutOverlap` RELOCATES, so it
// would not. That single assertion is what stops a future edit from quietly reusing the editor's
// packer and silently moving a kitchen the user has not seen — and the kitchen's zone is scored.
// ----------------------------------------------------------------------------------------------
const HERE = dirname(fileURLToPath(import.meta.url));
const FIXTURES = join(HERE, '..', '..', 'shared', 'src', 'main', 'resources', 'scan');

// ⭐ The gate is ROOM COUNT, not coverage. Coverage was demoted after the model's rectangles were
// drawn back over real plans and judged by eye: two plans with identical coverage placed well and
// badly, and the corpus's highest coverage placed worse than its lowest-but-one. See
// ScanMapper.MAX_TRUSTED_ROOMS for the table.
const MAX_TRUSTED_ROOMS = 12;
const UNIFORM_AREA_VARIATION = 0.15;
const UNIFORM_MIN_ROOMS = 4;
const CONTAINMENT_FRACTION = 0.90;
const MIN_PLACED_ROOMS = 2;
const MIN_PLACED_FRACTION = 0.6;
const COVERAGE_SAMPLES = 140;

/** A trimmed-down RoomLabels: enough captions to reach every branch. */
const LABEL_TABLE = {
  'LIVING ROOM': 'LIVING', KITCHEN: 'KITCHEN', 'MASTER BEDROOM': 'MASTER_BEDROOM',
  BEDROOM: 'BEDROOM', TOILET: 'TOILET', BATH: 'BATHROOM', POOJA: 'POOJA', BALCONY: 'BALCONY',
  DINING: 'DINING', STUDY: 'STUDY', STORE: 'STORE', UTILITY: 'UTILITY',
};
const LABEL_DROP = new Set(['DRESS', 'DRESSING', 'DUCT', 'LIFT', 'SHAFT', 'WARDROBE']);
/** Mirrors RoomLabels.clean: the parenthetical, the feet/inch marks, the printed dimensions, the
 *  index suffix. ⚠ The `X` between two numbers is a dimension separator (`BEDROOM 6750X4350`), not a
 *  letter — without that step a real caption never resolves, which is how this mirror first drifted. */
function cleanLabel(raw) {
  return String(raw).toUpperCase()
    .replace(/\([^)]*\)/g, ' ')
    .replace(/['"‘’“”′″]/g, ' ')
    .replace(/(?<=[0-9])[ ]*X[ ]*(?=[0-9])/g, ' ')
    .replace(/[^0-9A-Z]/g, ' ')
    .split(' ').filter((t) => t && !/^[0-9]+$/.test(t)).join(' ');
}
const LOOSE_KEYS = Object.keys(LABEL_TABLE).sort((a, b) => b.length - a.length || a.localeCompare(b));
function resolveLabel(raw) {
  const c = cleanLabel(raw);
  if (!c) return { kind: 'unknown' };
  if (LABEL_TABLE[c]) return { kind: 'room', type: LABEL_TABLE[c] };
  if (LABEL_DROP.has(c)) return { kind: 'drop' };
  for (const d of LABEL_DROP) if (c.includes(d)) return { kind: 'drop' };
  const hit = LOOSE_KEYS.find((k) => c.includes(k));
  if (hit) return { kind: 'room', type: LABEL_TABLE[hit] };
  return { kind: 'unknown' };
}

function scanSanitise(b, inject) {
  const fin = (v) => typeof v === 'number' && Number.isFinite(v);
  if (!fin(b.x) || !fin(b.y) || !fin(b.w) || !fin(b.h)) return null;
  if (b.w <= 0 || b.h <= 0) return null;
  // FAULT INJECTION 'no-clamp': trust the model's coordinates instead of clamping them into the
  // unit square. A box at x = 1.4 then snaps to cells beyond the grid's east edge.
  if (inject === 'no-clamp') {
    const conf = fin(b.confidence) ? b.confidence : 0;
    return { label: b.label, x: b.x, y: b.y, w: b.w, h: b.h, confidence: conf };
  }
  const x0 = clampF(b.x, 0, 1), y0 = clampF(b.y, 0, 1);
  const x1 = clampF(b.x + b.w, 0, 1), y1 = clampF(b.y + b.h, 0, 1);
  if (x1 <= x0 || y1 <= y0) return null;
  const conf = fin(b.confidence) ? clampF(b.confidence, 0, 1) : 0;
  return { label: b.label, x: x0, y: y0, w: x1 - x0, h: y1 - y0, confidence: conf };
}

/** Identical lattice to tools/scan-eval/batch-real.py, which is what the threshold is calibrated on. */
function coverageOf(boxes, n = COVERAGE_SAMPLES) {
  if (!boxes.length) return 0;
  let hit = 0;
  for (let gy = 0; gy < n; gy++) {
    const py = (gy + 0.5) / n;
    for (let gx = 0; gx < n; gx++) {
      const px = (gx + 0.5) / n;
      for (const b of boxes) {
        if (b.x <= px && px < b.x + b.w && b.y <= py && py < b.y + b.h) { hit++; break; }
      }
    }
  }
  return hit / (n * n);
}

function areaVariationOf(boxes) {
  if (!boxes.length) return 0;
  const areas = boxes.map((b) => b.w * b.h);
  const mean = areas.reduce((a, v) => a + v, 0) / areas.length;
  if (mean <= 0) return 0;
  const variance = areas.reduce((a, v) => a + (v - mean) * (v - mean), 0) / areas.length;
  return Math.sqrt(variance) / mean;
}

function containedIn(inner, outer) {
  const ia = inner.w * inner.h, oa = outer.w * outer.h;
  if (ia <= 0 || ia >= oa) return false;
  const ix = Math.max(0, Math.min(inner.x + inner.w, outer.x + outer.w) - Math.max(inner.x, outer.x));
  const iy = Math.max(0, Math.min(inner.y + inner.h, outer.y + outer.h) - Math.max(inner.y, outer.y));
  return (ix * iy) / ia >= CONTAINMENT_FRACTION;
}

function scanGridFor(aspect, inject) {
  if (aspect == null || !Number.isFinite(aspect) || aspect <= 0) return [MAX_GRID, MAX_GRID];
  // FAULT INJECTION 'grid-unclamped': take the aspect ratio at face value without holding the grid
  // inside the editor's MIN_GRID..MAX_GRID range.
  const fit = (v) => (inject === 'grid-unclamped' ? Math.round(v) : clampInt(Math.round(v), MIN_GRID, MAX_GRID));
  return aspect >= 1 ? [MAX_GRID, fit(MAX_GRID / aspect)] : [fit(MAX_GRID * aspect), MAX_GRID];
}

/**
 * ⭐ The drawing area is the HOME (the rooms' own bounding box, clamped to the picture), not the
 * picture. Mirrors ScanMapper.frameOf / homeAspect.
 */
function scanFrameOf(boxes) {
  if (!boxes.length) return null;
  const x0 = Math.max(0, Math.min(...boxes.map((b) => b.x)));
  const y0 = Math.max(0, Math.min(...boxes.map((b) => b.y)));
  const x1 = Math.min(1, Math.max(...boxes.map((b) => b.x + b.w)));
  const y1 = Math.min(1, Math.max(...boxes.map((b) => b.y + b.h)));
  if (x1 <= x0 || y1 <= y0) return null;
  return { x: x0, y: y0, w: x1 - x0, h: y1 - y0 };
}

function scanHomeAspect(frame, imageAspect) {
  const a = (typeof imageAspect === 'number' && Number.isFinite(imageAspect) && imageAspect > 0) ? imageAspect : 1;
  return (frame.w * a) / frame.h;
}

/**
 * Each EDGE rounded independently, so rooms flush on the plan stay flush on the grid — but measured
 * against the FRAME, and with a one-cell floor so a small room rounds SMALL rather than away.
 */
function scanSnap(b, frame, imageAspect, cols, rows, inject) {
  const a = (typeof imageAspect === 'number' && Number.isFinite(imageAspect) && imageAspect > 0) ? imageAspect : 1;
  const pw = frame.w * a, ph = frame.h;
  const s = Math.min(cols / pw, rows / ph);
  const ox = (cols - pw * s) / 2, oy = (rows - ph * s) / 2;
  const L = ((b.x - frame.x) * a) * s + ox;
  const R = (((b.x + b.w) - frame.x) * a) * s + ox;
  const T = (b.y - frame.y) * s + oy;
  const B = ((b.y + b.h) - frame.y) * s + oy;
  // FAULT INJECTION 'no-clamp' removes the grid clamp too, so a box the reader put off the page —
  // which the frame can never cover, because the frame is clamped to the picture — lands off-grid.
  if (inject === 'no-clamp') {
    return { col: Math.round(L), row: Math.round(T), w: Math.round(R) - Math.round(L), h: Math.round(B) - Math.round(T) };
  }
  // FAULT INJECTION 'round-away': drop a room whose edges round together instead of keeping one
  // cell — the behaviour that silently deleted 10 rooms across the 30 real plans, nearly all toilets.
  const left = clampInt(Math.round(L), 0, cols - 1);
  const top = clampInt(Math.round(T), 0, rows - 1);
  if (inject === 'round-away' && (Math.round(R) <= Math.round(L) || Math.round(B) <= Math.round(T))) return null;
  const rightE = clampInt(Math.round(R), left + 1, cols);
  const bottomE = clampInt(Math.round(B), top + 1, rows);
  return { col: left, row: top, w: rightE - left, h: bottomE - top };
}

function scanRetract(r, o) {
  const options = [];
  if (right(o) > r.col && right(o) < right(r)) options.push({ col: right(o), row: r.row, w: right(r) - right(o), h: r.h });
  if (o.col > r.col && o.col < right(r)) options.push({ col: r.col, row: r.row, w: o.col - r.col, h: r.h });
  if (bottom(o) > r.row && bottom(o) < bottom(r)) options.push({ col: r.col, row: bottom(o), w: r.w, h: bottom(r) - bottom(o) });
  if (o.row > r.row && o.row < bottom(r)) options.push({ col: r.col, row: r.row, w: r.w, h: o.row - r.row });
  if (!options.length) return null;
  return options.reduce((best, c) => (c.w * c.h > best.w * best.h ? c : best));
}

function scanTrimAgainst(cand, blockers) {
  let r = cand, guard = cand.w * cand.h + 4;
  while (guard-- > 0) {
    const hit = blockers.find((b) => overlaps(b, r));
    if (!hit) return r;
    r = scanRetract(r, hit);
    if (!r) return null;
  }
  return null;
}

/** The whole mapper. Returns {kind:'placed'|'assisted'|'refused', ...}. */
// ---- printed room sizes (RoomDimensions.kt) --------------------------------------------------
// The reader reads TEXT at ~95 % and guesses rectangles at 40-70 %, so where a caption prints the
// room's size that number beats the rectangle it arrived with. Mirrors RoomDimensions.parse.
const FRACTIONS = { '\u00bd': 0.5, '\u00bc': 0.25, '\u00be': 0.75, '\u2153': 1/3, '\u2154': 2/3, '\u215b': 0.125 };
const FEET_INCHES = "(\d+)\s*['\u2032\u2019]\s*-?\s*(\d+)?\s*([\u00bd\u00bc\u00be\u2153\u2154\u215b])?\s*[\"\u2033\u201d]?";
const PAIR_FEET_INCHES = new RegExp(FEET_INCHES + "\s*[X\u00d7]\s*" + FEET_INCHES);
const PAIR_PLAIN = /(?<![\d.'"\u2032\u2033])(\d{2,5})\s*(?:MM|CM)?\s*[X\u00d7]\s*(\d{2,5})\s*(?:MM|CM)?(?!\d)/;
const MM_PER_FOOT = 304.8, FEET_IF_UNDER = 100;

function feetInches(f, i, fr) {
  return Number(f) + (Number(i || 0) + (fr ? (FRACTIONS[fr] || 0) : 0)) / 12;
}

function parsePrinted(label) {
  const s = String(label).toUpperCase();
  const m = PAIR_FEET_INCHES.exec(s);
  if (m) {
    const a = feetInches(m[1], m[2], m[3]), b = feetInches(m[4], m[5], m[6]);
    return (a > 0 && b > 0) ? { w: a * MM_PER_FOOT, h: b * MM_PER_FOOT } : null;
  }
  const n = PAIR_PLAIN.exec(s);
  if (n) {
    const a = Number(n[1]), b = Number(n[2]);
    if (!(a > 0 && b > 0)) return null;
    return (a < FEET_IF_UNDER && b < FEET_IF_UNDER) ? { w: a * MM_PER_FOOT, h: b * MM_PER_FOOT } : { w: a, h: b };
  }
  return null;
}

/**
 * ScanMapper.reshapeToPrinted. Total area preserved, top-left corner kept, and orientation taken
 * from the PRINTED ORDER (first number is the width).
 *
 * FAULT INJECTION 'printed-follows-model' restores the rule this replaced -- resolving each room's
 * orientation against the rectangle the reader drew. It looks reasonable and it silently defeats the
 * whole feature, because the reader's sense of which way a room runs is exactly what was wrong.
 */
function reshapeToPrinted(snapped, cols, rows, inject) {
  if (inject === 'no-printed-sizes') return snapped;
  const printed = snapped.map((s) => parsePrinted(s.c.box.label));
  let cellTotal = 0, printedTotal = 0;
  snapped.forEach((s, i) => {
    if (printed[i]) { cellTotal += s.rect.w * s.rect.h; printedTotal += printed[i].w * printed[i].h; }
  });
  if (cellTotal <= 0 || printedTotal <= 0) return snapped;
  const cellsPerSquareMm = cellTotal / printedTotal;

  return snapped.map((s, i) => {
    const size = printed[i];
    if (!size) return s;
    const target = size.w * size.h * cellsPerSquareMm;
    if (!isFinite(target) || target <= 0) return s;
    let ratio = size.w / size.h;
    if (inject === 'printed-follows-model') {
      const drawn = s.rect.w / s.rect.h;
      if (Math.abs(Math.log(ratio) - Math.log(drawn)) > Math.abs(Math.log(1 / ratio) - Math.log(drawn))) ratio = 1 / ratio;
    }
    if (!isFinite(ratio) || ratio <= 0) return s;
    // Shrink to fit PROPORTIONALLY: clamping height after choosing width inverts a tall room in a
    // shallow grid, which is the exact error this feature removes. Proportion beats area.
    let wf = Math.sqrt(target * ratio), hf = Math.sqrt(target / ratio);
    if (!isFinite(wf) || !isFinite(hf) || wf <= 0 || hf <= 0) return s;
    const fit = Math.min(1, cols / wf, rows / hf);
    wf *= fit; hf *= fit;
    const w = clampInt(Math.round(wf), 1, cols);
    const h = clampInt(Math.round(hf), 1, rows);
    const col = clampInt(s.rect.col, 0, cols - w), row = clampInt(s.rect.row, 0, rows - h);
    return { ...s, rect: { col, row, w, h }, asRead: { col, row, w, h } };
  });
}

function scanMap(draft, imageAspect, opts = {}) {
  const dropped = [];
  const clean = [];
  const inject = opts.inject || null;
  for (const b of draft.rooms || []) {
    const c = scanSanitise(b, inject);
    if (!c) dropped.push({ label: b.label, reason: 'INVALID_GEOMETRY' });
    else clean.push(c);
  }
  const coverage = coverageOf(clean);
  const variation = areaVariationOf(clean);
  const notes = () => ({ coverage, variation, dropped: dropped.slice() });

  const planType = draft.planType || 'UNKNOWN';
  if (planType === '3D_RENDER') return { kind: 'refused', reason: 'NOT_2D', notes: notes() };
  if (planType === 'NOT_A_PLAN') return { kind: 'refused', reason: 'NOT_A_PLAN', notes: notes() };
  if (clean.filter((b) => /^(UNIT|FLAT|TYPE|APARTMENT|APT|BLOCK|TOWER|PLOT)$/
    .test(String(b.label).toUpperCase().replace(/[^0-9A-Z]/g, ' ').split(' ').filter((t) => t && !/^[0-9]+$/.test(t))[0] || '')
    && String(b.label).toUpperCase().replace(/[^0-9A-Z]/g, ' ').split(' ').filter((t) => t && !/^[0-9]+$/.test(t)).length <= 1).length >= 2) {
    return { kind: 'refused', reason: 'MULTI_UNIT', notes: notes() };
  }
  if (draft.unreadable || draft.hasRoomLabels === false) return { kind: 'refused', reason: 'NO_LABELS', notes: notes() };
  if (!clean.length) return { kind: 'refused', reason: 'NO_ROOMS', notes: notes() };

  const typed = [];
  let unknown = 0;
  for (const b of clean) {
    const m = resolveLabel(b.label);
    if (m.kind === 'room') typed.push({ box: b, type: m.type, flags: new Set() });
    else if (m.kind === 'drop') dropped.push({ label: b.label, reason: 'NOT_HABITABLE' });
    else { unknown++; dropped.push({ label: b.label, reason: 'UNKNOWN_LABEL' }); }
  }

  const rooms = [];
  for (const c of typed) {
    if (typed.some((o) => o !== c && containedIn(c.box, o.box))) dropped.push({ label: c.box.label, reason: 'SUB_AREA' });
    else rooms.push(c);
  }
  if (!rooms.length) return { kind: 'refused', reason: unknown > 0 ? 'NO_LABELS' : 'NO_ROOMS', notes: notes() };

  const identified = rooms.slice().sort((a, b) => a.box.y - b.box.y || a.box.x - b.box.x)
    .map((c) => ({ type: c.type, label: c.box.label, rect: null }));

  // FAULT INJECTION 'no-uniform-gate' / 'no-coverage-gate': trust the model's rectangles. Each is
  // the shape of a plausible "why are we throwing away geometry?" change.
  if (inject !== 'no-uniform-gate' && rooms.length >= UNIFORM_MIN_ROOMS && variation < UNIFORM_AREA_VARIATION) {
    return { kind: 'assisted', reason: 'UNIFORM_BOXES', rooms: identified, notes: notes() };
  }
  if (inject !== 'no-roomcount-gate' && rooms.length > MAX_TRUSTED_ROOMS) {
    return { kind: 'assisted', reason: 'TOO_MANY_ROOMS', rooms: identified, notes: notes() };
  }

  // ⭐ The drawing area is the HOME, not the picture — a builder's sheet is mostly logo, title block
  // and blank paper, and mapping the reader's coordinates straight onto the grid handed the home
  // whatever fraction of the PAPER it happened to occupy. The frame is CLAMPED to the picture, which
  // is what keeps `no-clamp` biting: an off-page box still falls outside a frame that can never leave
  // the unit square.
  // ⚠ `--inject=frame-picture` restores the old behaviour and this suite stays GREEN, which is the
  // honest position: framing on the picture is not ILLEGAL, only worse. It is measured instead, on
  // the 30 real replies — the home goes from filling 71 % of the grid to 97 %, and rooms lost to
  // rounding from 10 to 0 (tools/scan-eval/exp-frame.py). Not claimed as fuzz-proven.
  const frame = inject === 'frame-picture'
    ? { x: 0, y: 0, w: 1, h: 1 }
    : scanFrameOf(rooms.map((c) => c.box));
  if (!frame) return { kind: 'assisted', reason: 'TOO_FEW_PLACED', rooms: identified, notes: notes() };
  const roundedAway = [];
  const [cols, rows] = scanGridFor(scanHomeAspect(frame, imageAspect), inject);
  let snapped = [];
  for (const c of rooms) {
    const rect = scanSnap(c.box, frame, imageAspect, cols, rows, inject);
    if (!rect) { roundedAway.push(c.box.label); dropped.push({ label: c.box.label, reason: 'DEGENERATE' }); }
    // The printed size is attached here, NOT inside reshapeToPrinted, so that deleting the
    // reshaping (--inject=no-printed-sizes) still leaves the invariant something to judge. An
    // injection that removes a feature has to go red; one that merely stops recording it would not.
    else snapped.push({ c, rect, asRead: rect, asReadRaw: rect, printed: parsePrinted(c.box.label) });
  }
  snapped = reshapeToPrinted(snapped, cols, rows, inject);

  // ⭐ SMALLEST first: what a cut costs is proportional, so the room that cannot absorb one goes
  // ahead of the room that can, and a room lost outright is RESCUED — it jumps the queue and the
  // whole placement is redone.
  // ⚠ `--inject=confidence-first` and `--inject=no-rescue` both leave this suite GREEN, and that is
  // stated rather than dressed up: losing a room is REPORTED, so it is legal, and random replies do
  // not reliably reproduce the case. Both are measured on the 30 real replies instead — 4 rooms lost
  // against 0. What IS fuzz-proven here is `no-onecell`, which fires LOST-WITH-ROOM-TO-SPARE.
  function placeAll(rescued) {
    const rank = (x) => (rescued.has(x.c) ? 0 : 1);
    const order = snapped.slice().sort((a, b) =>
      rank(a) - rank(b) || (inject === 'confidence-first'
        ? (b.c.box.confidence - a.c.box.confidence || (b.rect.w * b.rect.h) - (a.rect.w * a.rect.h))
        : ((a.rect.w * a.rect.h) - (b.rect.w * b.rect.h) || b.c.box.confidence - a.c.box.confidence))
      || String(a.c.box.label).localeCompare(String(b.c.box.label)));
    const placed = [], lost = [];
    for (const s2 of order) {
      // ⚠ FAULT INJECTION 'repack' reuses the editor's relocating packer here — the thing this suite
      // exists to forbid. It produces a legal-looking layout that TRIMMED-MOVED catches.
      // 'no-trim' is the cruder version: just take what the model said.
      // The printed shape first, then the shape it was READ with, then one cell at its own corner.
      // Re-shaping must never cost a room: a room that vanishes changes the footprint the engine
      // scores, which is worse than a room of the wrong shape (which the user can see and fix).
      let trimmed, basis = s2.rect;
      if (inject === 'repack') {
        trimmed = (fitWithoutOverlap([...placed.map((p) => p.rect), s2.rect], cols, rows) || []).slice(-1)[0] || null;
      } else if (inject === 'no-trim') {
        trimmed = s2.rect;
      } else {
        trimmed = scanTrimAgainst(s2.rect, placed.map((p) => p.rect));
        if (!trimmed) {         // the printed shape will not fit here; fall back to the read shape
          basis = s2.asReadRaw || s2.rect;
          trimmed = scanTrimAgainst(basis, placed.map((p) => p.rect));
        }
      }
      if (!trimmed && inject !== 'no-onecell') {
        const one = { col: s2.rect.col, row: s2.rect.row, w: 1, h: 1 };
        if (!placed.some((p) => overlaps(p.rect, one))) { trimmed = one; basis = s2.rect; }
      }
      if (!trimmed) { lost.push(s2.c); continue; }
      // `asRead` is the rectangle this placement was actually DERIVED from, so the anti-relocation
      // check compares like with like whether the printed shape or the read shape was used. It still
      // catches a relocating packer, which moves a room to a free slot unrelated to either.
      placed.push({ c: s2.c, rect: trimmed, asRead: basis, printed: s2.printed || null, shaped: s2.rect });
    }
    return { placed, lost };
  }

  let rescued = new Set();
  let attempt = placeAll(rescued);
  for (let guard = snapped.length; guard-- > 0 && inject !== 'no-rescue';) {
    if (!attempt.lost.length || attempt.lost.every((c) => rescued.has(c))) break;
    attempt.lost.forEach((c) => rescued.add(c));
    attempt = placeAll(rescued);
  }
  const placed = attempt.placed;
  // ⭐ Why each loss happened, so the suite can judge it. The ONLY legitimate reason left is that
  // another room already holds the very cell this one starts in — two rectangles read in the same
  // place. Anything else means a room was given up while somewhere of its own was still free.
  const lostWithFreeCorner = [];
  for (const c of attempt.lost) {
    dropped.push({ label: c.box.label, reason: 'OVERLAP_UNRESOLVABLE' });
    const src = snapped.find((x) => x.c === c);
    const one = src && { col: src.rect.col, row: src.rect.row, w: 1, h: 1 };
    if (one && !placed.some((pp) => overlaps(pp.rect, one))) lostWithFreeCorner.push(c.box.label);
  }

  if (placed.length < MIN_PLACED_ROOMS || placed.length < rooms.length * MIN_PLACED_FRACTION) {
    return { kind: 'assisted', reason: 'TOO_FEW_PLACED', rooms: identified, notes: notes() };
  }
  const out = placed.slice().sort((a, b) => a.rect.row - b.rect.row || a.rect.col - b.rect.col)
    .map((p) => ({ type: p.c.type, label: p.c.box.label, rect: p.rect, asRead: p.asRead, printed: p.printed, shaped: p.shaped }));
  return { kind: 'placed', cols, rows, rooms: out, notes: notes(), sanitised: clean, roundedAway, lostWithFreeCorner };
}

/** Every invariant the editor guarantees, asserted on what the mapper emits. */
function scanInvariants(out) {
  const problems = [];
  if (out.kind !== 'placed') {
    for (const r of out.rooms || []) if (r.rect) problems.push('ASSISTED-HAS-GEOMETRY');
    return problems;
  }
  // ⭐ SANITISED-IN-PAGE. sanitise() exists to guarantee every box lies inside the picture, and the
  // frame is clamped to the picture, so this is the property that keeps the `no-clamp` injection
  // biting now that the grid is framed on the home. Checked directly rather than inferred from an
  // off-grid rectangle, which is strictly stronger: the old check only noticed when rounding happened
  // to carry the error all the way through.
  for (const b of out.sanitised || []) {
    if (b.x < 0 || b.y < 0 || b.x + b.w > 1 + 1e-9 || b.y + b.h > 1 + 1e-9) {
      problems.push(`SANITISED-IN-PAGE ${b.label} ${b.x.toFixed(2)},${b.y.toFixed(2)} ${b.w.toFixed(2)}x${b.h.toFixed(2)}`);
    }
  }
  // ⭐ NO-ROUNDING-LOSS. A room may round SMALL, never away. Ten rooms across the thirty real plans
  // used to vanish here — almost all toilets, each one a scored input silently absent from a paid
  // score, with only a line in "we also saw" to show for it.
  for (const label of out.roundedAway || []) problems.push(`ROUNDED-AWAY ${label}`);
  // ⭐ LOST-WITH-ROOM-TO-SPARE. A room may only be given up when another room already holds the cell
  // it starts in. Anywhere else free means it should have been kept — one cell in the right place is
  // a room the user can see and resize, and a room that never arrives is one they cannot re-add.
  for (const label of out.lostWithFreeCorner || []) problems.push(`LOST-WITH-ROOM-TO-SPARE ${label}`);
  const { cols, rows, rooms } = out;
  if (!(cols >= MIN_GRID && cols <= MAX_GRID && rows >= MIN_GRID && rows <= MAX_GRID)) {
    problems.push(`PLOT-RANGE ${cols}x${rows}`);
  }
  if (rooms.length < MIN_PLACED_ROOMS) problems.push(`TOO-FEW ${rooms.length}`);
  for (const r of rooms) {
    const q = r.rect;
    if (q.w < 1 || q.h < 1) problems.push(`SUB-CELL ${JSON.stringify(q)}`);
    if (q.col < 0 || q.row < 0 || right(q) > cols || bottom(q) > rows) problems.push(`OFF-GRID ${JSON.stringify(q)}`);
    // ⭐ the anti-relocation invariant — see the header.
    if (!overlaps(q, r.asRead)) problems.push(`TRIMMED-MOVED ${JSON.stringify(q)} vs read ${JSON.stringify(r.asRead)}`);
  }
  for (let i = 0; i < rooms.length; i++) {
    for (let j = i + 1; j < rooms.length; j++) {
      if (overlaps(rooms[i].rect, rooms[j].rect)) problems.push(`OVERLAP ${i}/${j}`);
    }
  }
  // ⭐ PRINTED-ORIENTATION: where the plan PRINTS a room's size, the room we hand over must run
  // the way the plan says it runs. Checked on the shaped rectangle, before the overlap trim, because
  // trimming is a deliberate, flagged concession to a bad POSITION and must not be read as licence to
  // ignore the text. This is the invariant that catches resolving orientation against the reader's own
  // rectangle -- which looks sensible, and reproduces exactly the defect the feature exists to fix: on
  // the owner's plan the reader called the passage three times wider than tall when it is four times
  // taller than wide, so deferring to it kept the passage horizontal.
  for (const r of rooms) {
    if (!r.printed || !r.shaped) continue;
    const printedSense = Math.sign(r.printed.w - r.printed.h);
    const drawnSense = Math.sign(r.shaped.w - r.shaped.h);
    // Only judge rooms the grid can actually express a direction for: a room that rounds to a square
    // on a 10-cell grid is not evidence either way.
    if (printedSense !== 0 && drawnSense !== 0 && printedSense !== drawnSense) {
      problems.push(`PRINTED-ORIENTATION ${r.label} printed ${Math.round(r.printed.w)}x${Math.round(r.printed.h)} drawn ${r.shaped.w}x${r.shaped.h}`);
    }
  }
  // The scanned plan must behave like a hand-drawn one for everything downstream: reopening it must
  // re-derive a plot that still contains every room, and every tap anywhere on it must yield a door
  // that sits on the house's own wall (the v0.3.11 S8 guarantee, now via a plan nobody drew).
  const grid = rooms.map((r) => ({ id: r.label, col: r.rect.col, row: r.rect.row, w: r.rect.w, h: r.rect.h }));
  const [rc, rr] = gridSizeForRooms(grid);
  for (const g of grid) {
    if (g.col + g.w > rc || g.row + g.h > rr) problems.push(`REOPEN-OUTSIDE ${g.id}`);
  }
  const minC = Math.min(...grid.map((g) => g.col)), maxC = Math.max(...grid.map((g) => g.col + g.w));
  const minR = Math.min(...grid.map((g) => g.row)), maxR = Math.max(...grid.map((g) => g.row + g.h));
  for (let i = 0; i <= cols * 2; i++) {
    for (let j = 0; j <= rows * 2; j++) {
      const d = doorForTap(i / 2, j / 2, grid);
      if (!d) { problems.push('DOOR-NULL'); continue; }
      const [dc, dr] = doorMarkerCell(d, grid, cols, rows);
      const onWall = (d.side === 'N' && dr === minR) || (d.side === 'S' && dr === maxR - 1)
        || (d.side === 'W' && dc === minC) || (d.side === 'E' && dc === maxC - 1);
      if (!onWall) problems.push(`DOOR-OFF-FOOTPRINT ${d.side}@${d.cell} -> ${dc},${dr}`);
    }
  }
  return problems;
}

const SCAN_LABELS = [
  'LIVING ROOM', 'KITCHEN', 'MASTER BEDROOM', 'BEDROOM 2', 'TOILET', 'BATH', 'POOJA', 'BALCONY',
  'DINING', 'STUDY', 'STORE', 'UTILITY', 'DRESS', 'DUCT', 'LIFT', 'ZORBING PIT', '7', 'UNIT-1',
  // ⭐ Captions that PRINT a size, in both conventions the corpus contains, so the reshaping and
  // the PRINTED-ORIENTATION invariant are actually exercised. Deliberately a mix of tall, wide and
  // near-square rooms -- and the owner's own passage, which is the extreme case (4x taller than wide).
  "LOBBY 10'-0\"X12'-9\"", "PASSAGE 2'-3\"X9'-6\"", "KITCHEN 6'-11\"X9'-7\"",
  "BED ROOM 10'-0\"X10'-6\"", "W.C 5'-0\"X2'-11\"", "BATH 5'-0\"X3'-8½\"",
  'BEDROOM 6750X4350', 'KITCHEN 2950X4200', 'TOILET 1350X2250', 'DINING 4175X2975',
  'BALCONY 3300X2000', "STUDY-ROOM 2425X2000",
];

function randomDraft(rnd) {
  const n = Math.floor(rnd() * 26);
  const rooms = [];
  for (let i = 0; i < n; i++) {
    const roll = rnd();
    let x = rnd(), y = rnd(), w = rnd() * 0.6, h = rnd() * 0.6;
    if (roll < 0.04) x = NaN;                        // the model emitted junk
    else if (roll < 0.08) w = -w;                    // inverted
    else if (roll < 0.12) { x = 1 + rnd(); }         // off the page entirely
    else if (roll < 0.18) { w = rnd() * 0.01; h = rnd() * 0.01; } // sub-cell
    else if (roll < 0.24) { x = 0; y = 0; w = 1; h = 1; }         // swallows the plan
    rooms.push({ label: SCAN_LABELS[Math.floor(rnd() * SCAN_LABELS.length)], x, y, w, h, confidence: rnd() });
  }
  const t = rnd();
  return {
    planType: t < 0.08 ? '3D_RENDER' : t < 0.12 ? 'NOT_A_PLAN' : t < 0.2 ? 'UNKNOWN' : '2D_PLAN',
    hasRoomLabels: rnd() > 0.05,
    unreadable: rnd() < 0.05,
    rooms,
    planConfidence: 0.95,
  };
}

/** A plan that TILES cleanly — the Placed path, which the purely-random drafts rarely reach. */
function randomTiledDraft(rnd) {
  const cols = 2 + Math.floor(rnd() * 3), rows = 2 + Math.floor(rnd() * 3);
  const xs = [0], ys = [0];
  for (let i = 1; i < cols; i++) xs.push(Number((i / cols + (rnd() - 0.5) * 0.08).toFixed(4)));
  for (let i = 1; i < rows; i++) ys.push(Number((i / rows + (rnd() - 0.5) * 0.08).toFixed(4)));
  xs.push(1); ys.push(1);
  xs.sort((a, b) => a - b); ys.sort((a, b) => a - b);
  const rooms = [];
  let k = 0;
  for (let r = 0; r < rows; r++) {
    for (let c = 0; c < cols; c++) {
      rooms.push({
        label: SCAN_LABELS[k++ % 12],
        x: xs[c], y: ys[r], w: xs[c + 1] - xs[c], h: ys[r + 1] - ys[r],
        confidence: 0.3 + rnd() * 0.7,
      });
    }
  }
  // …then a few strays on top, which is exactly how a real reply goes wrong.
  const strays = Math.floor(rnd() * 3);
  for (let i = 0; i < strays; i++) {
    rooms.push({
      label: SCAN_LABELS[Math.floor(rnd() * SCAN_LABELS.length)],
      x: rnd() * 0.8, y: rnd() * 0.8, w: 0.05 + rnd() * 0.3, h: 0.05 + rnd() * 0.3,
      confidence: rnd(),
    });
  }
  return { planType: '2D_PLAN', hasRoomLabels: true, unreadable: false, rooms, planConfidence: 0.95 };
}

function fuzzScan(iterations, inject) {
  const fails = [];
  const tally = {};
  let placed = 0, assisted = 0, refused = 0;
  for (let seed = 1; seed <= iterations; seed++) {
    const rnd = mulberry32(seed ^ 0x5CA9);
    const draft = seed % 2 === 0 ? randomTiledDraft(rnd) : randomDraft(rnd);
    // Includes proportions well outside what the grid can express (a 4:1 letterbox site plan, a
    // narrow 1:5 strip): the clamp into MIN_GRID..MAX_GRID is what has to hold, so it must be reached.
    const aspect = [null, 0.18, 0.5, 1.0, 1.4, 2.5, 4.0, 7.5][Math.floor(rnd() * 8)];
    let out;
    try {
      out = scanMap(draft, aspect, { inject });
    } catch (e) {
      fails.push({ seed, problems: [`THREW ${e.message}`], draft });
      continue;
    }
    if (out.kind === 'placed') placed++; else if (out.kind === 'assisted') assisted++; else refused++;
    const problems = scanInvariants(out);
    // Determinism: the same reply must always map to the same plan.
    const again = scanMap(draft, aspect, { inject });
    if (JSON.stringify(again) !== JSON.stringify(out)) problems.push('NON-DETERMINISTIC');
    if (problems.length) {
      for (const p of problems) { const cat = p.split(' ')[0]; tally[cat] = (tally[cat] || 0) + 1; }
      if (fails.length < 3) fails.push({ seed, problems, draft, out });
    }
  }
  return { fails, tally, mix: { placed, assisted, refused } };
}

/**
 * The cases with known answers: the three replies the real Groq API actually returned, plus the two
 * fabrications the measurement work caught. Each gate has a case here that ONLY it can catch, so
 * removing a gate has to show up as a failure rather than as a slightly different number.
 */
function scanPinnedCases(inject) {
  const problems = [];
  const opts = { inject };

  // ⚠ plan-01-photo now PLACES: it has 8 rooms, which is under the gate, and its geometry is known
  // to be wrong (2/8 right). Nothing available catches it — its coverage, 0.569, sits ABOVE both
  // real plans that placed perfectly. That is stated in ScanMapper and it is what the mandatory
  // confirmation step exists for. Pinned so the trade-off stays deliberate rather than forgotten.
  //
  // ⚠ Only the plan-01 family is pinned HERE. `real-dense` is a 24-space floor plate whose captions
  // (LOBBY, SIT-OUT, C.TOILET, LOUNGE, STUDY-ROOM…) need the FULL synonym table to resolve, and this
  // mirror deliberately carries a cut-down one. Since the gate is now the room COUNT, a smaller table
  // changes the answer — so the real-reply expectations live in Kotlin's RecordedScanTest, where the
  // real table runs. This file keeps what it can check honestly: geometry.
  const expect = [
    ['plan-01', 'placed', 1.0, 8],
    ['plan-01-jpeg', 'placed', 1.0, 8],
    ['plan-01-photo', 'placed', 0.569, 8],
  ];
  for (const [id, kind, cov, nrooms] of expect) {
    let rec;
    try {
      rec = JSON.parse(readFileSync(join(FIXTURES, `${id}.json`), 'utf8'));
    } catch {
      problems.push(`${id}: fixture missing from shared/src/main/resources/scan/`);
      continue;
    }
    const out = scanMap(rec.reply, null, opts);
    if (out.kind !== kind) problems.push(`${id}: expected ${kind}, got ${out.kind} (${out.reason || ''})`);
    if (Math.abs(out.notes.coverage - cov) > 0.005) {
      problems.push(`${id}: coverage ${out.notes.coverage.toFixed(3)}, measured ${cov}`);
    }
    const n = (out.rooms || []).length;
    if (n !== nrooms) problems.push(`${id}: ${n} rooms, expected ${nrooms} from the real reply`);
  }

  // §3j D2 — a numbered-legend plan came back as rooms of IDENTICAL size at confidence 0.95. Here
  // the fabrication also TILES the plan, so coverage waves it through and only the area-variance
  // detector can catch it.
  const names = ['LIVING ROOM', 'KITCHEN', 'MASTER BEDROOM', 'BEDROOM', 'TOILET', 'BATH',
    'POOJA', 'DINING', 'STUDY', 'STORE', 'BALCONY', 'UTILITY'];
  const fabricated = names.map((label, i) => ({
    label, x: (i % 4) * 0.25, y: Math.floor(i / 4) * (1 / 3), w: 0.25, h: 1 / 3, confidence: 0.95,
  }));
  const fab = scanMap({ planType: '2D_PLAN', hasRoomLabels: true, rooms: fabricated, planConfidence: 0.95 }, null, opts);
  if (fab.kind !== 'assisted' || fab.reason !== 'UNIFORM_BOXES') {
    problems.push(`fabricated-uniform: expected assisted/UNIFORM_BOXES, got ${fab.kind}/${fab.reason || ''}`);
  }

  // A dense floor plate: names read fine, geometry not trusted. The gate that catches it is the
  // room count, so build one with more rooms than a single home has.
  const dense = Array.from({ length: 18 }, (_, i) => ({
    label: SCAN_LABELS[i % 12], x: (i % 6) * 0.16, y: Math.floor(i / 6) * 0.33,
    w: 0.10 + (i % 3) * 0.03, h: 0.20 + (i % 2) * 0.06, confidence: 0.9,
  }));
  const dn = scanMap({ planType: '2D_PLAN', hasRoomLabels: true, rooms: dense, planConfidence: 0.95 }, null, opts);
  if (dn.kind !== 'assisted' || dn.reason !== 'TOO_MANY_ROOMS') {
    problems.push(`dense-plate: expected assisted/TOO_MANY_ROOMS, got ${dn.kind}/${dn.reason || ''}`);
  }
  return problems;
}

const iters = parseInt(process.argv[2] || '20000', 10);

// `--only=E` runs a single suite. Fault-injection runs use it: proving one invariant bites should
// not mean waiting for 200 000 unrelated iterations.
const onlyArg = (process.argv.find((a) => a.startsWith('--only=')) || '').split('=')[1] || null;
const only = (s) => onlyArg == null || onlyArg.toUpperCase().includes(s);

// Suite B + C run at a fixed, generous count independent of the editor-fuzz count — they're cheap.
const resizeIters = Math.max(iters, 50000);
const roundTripIters = Math.max(iters, 50000);

if (only('B')) {
console.log(`\n── Suite B: resizeBy invariants (opposite corner pinned, never inverts, stays in grid) ──`);
const resizeFails = fuzzResize(resizeIters);
if (resizeFails.length === 0) {
  console.log(`✅ ${resizeIters} random resizes — opposite corner always pinned, w,h ≥ 1, never off-grid.`);
} else {
  const f = resizeFails[0];
  console.log(`❌ ${resizeFails.length}/${resizeIters} resizes broke an invariant.`);
  console.log(`   first: seed=${f.seed} start=${JSON.stringify(f.start)} handle=${f.handle} d=(${f.dCol},${f.dRow}) grid ${f.cols}×${f.rows}`);
  console.log(`   out=${JSON.stringify(f.out)}  problems: ${f.problems.join(' | ')}`);
  process.exitCode = 1;
}
}

if (only('C')) {
console.log(`\n── Suite C: reopen round-trip · score translation-invariance · door side on thin footprints ──`);
const rtFails = fuzzRoundTrip(roundTripIters);
if (rtFails.length === 0) {
  console.log(`✅ ${roundTripIters} random footprints — rooms + door survive save→reload byte-for-byte,`);
  console.log(`   score is translation-invariant, and the door side is stable on thin (1-cell) footprints.`);
} else {
  const f = rtFails[0];
  console.log(`❌ ${rtFails.length}/${roundTripIters} footprints broke an invariant.`);
  console.log(`   first: seed=${f.seed} grid ${f.cols}×${f.rows} door=${f.door ? f.door.side + '@' + f.door.cell : 'null'}`);
  console.log(`   rooms:`, f.rooms.map((r) => `${r.id}:${r.col},${r.row},${r.w}x${r.h}`).join('  '));
  console.log(`   problems: ${f.problems.join(' | ')}`);
  process.exitCode = 1;
}
}

if (only('A')) {
  console.log(`\n── Suite A: editor gesture-order fuzz (multi-step drags, hysteresis, blocked-carry) ──`);
  run(iters);
}

if (only('D')) {
  console.log(`\n── Suite D: WCAG button paths (move arrows · size steppers · plot keys · tile select · retype) ──`);
  if (INJECT) console.log(`   ⚠ FAULT INJECTED: ${INJECT}`);
  run(iters, randomButtonOp, 0x5EED);
}

// ---- Suite E: ScanMapper ------------------------------------------------------------------------
if (only('E')) {
console.log(`\n── Suite E: ScanMapper (random model output → editor-legal rooms) ──`);
const inject = (process.argv.find((a) => a.startsWith('--inject=')) || '').split('=')[1] || null;
if (inject) console.log(`   ⚠ FAULT INJECTED: ${inject}`);

const pinned = scanPinnedCases(inject);
if (pinned.length === 0) {
  console.log('✅ the recorded Groq replies map exactly as pinned, and both fabrication detectors fire');
  console.log('   (clean render + JPEG + phone photo all Placed — the photo is a known, documented');
  console.log('    miss that only the user\'s confirmation catches; a dense floor plate → Assisted)');
} else {
  console.log('❌ a recorded reply no longer maps as it was measured:');
  for (const p of pinned) console.log(`     ${p}`);
  process.exitCode = 1;
}

const scanIters = Math.max(iters, 20000);
const scan = fuzzScan(scanIters, inject);
console.log(`   outcome mix over ${scanIters} random replies:`,
  `${scan.mix.placed} placed · ${scan.mix.assisted} assisted · ${scan.mix.refused} refused`);
if (scan.fails.length === 0) {
  console.log('✅ every mapped plan is editor-legal — no overlap, nothing off-grid, nothing under a cell,');
  console.log('   a trimmed room never MOVES from where it was read, reopening keeps every room inside');
  console.log('   the plot, every tap lands a door on the house\'s own wall, and mapping is deterministic.');
} else {
  console.log(`❌ ${scan.fails.length} replies produced an illegal plan.`);
  console.log('   by category:', scan.tally);
  const f = scan.fails[0];
  console.log(`   first failure: seed=${f.seed}`);
  console.log(`   problems: ${f.problems.join(' | ')}`);
  console.log(`   draft: ${JSON.stringify(f.draft).slice(0, 900)}`);
  process.exitCode = 1;
}
}

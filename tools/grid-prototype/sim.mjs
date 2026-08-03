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
// ⭐⭐ …and the room count now only decides a plan that prints NO sizes. Where the rooms state their
// own dimensions, the reader's rectangles supply the arrangement (which it gets right) and its own
// printed text supplies every measurement (which it reads at ~95 %). Counting rooms threw out the
// owner's fifteen-space flat, which is an ordinary Indian apartment. See
// ScanMapper.SIZED_SHARE_TO_TRUST / MAX_ROOMS_EVER / MAX_OVERFLOW.
const SIZED_SHARE_TO_TRUST = 2 / 3;
const MAX_ROOMS_EVER = 20;
const MAX_OVERFLOW = 2.0;
const MIN_SPILLED_ROOMS = 2;
/** A sheet naming a lift shows a whole floor, not one home. Mirrors RoomLabels.isFloorPlateLabel. */
const FLOOR_PLATE_WORDS = ['LIFT', 'ELEVATOR'];
const isFloorPlateLabel = (raw) => FLOOR_PLATE_WORDS.some((w) => cleanLabel(raw).includes(w));
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
  // The greencourt-336 fixture family's captions, so its pinned cases run END-TO-END here with
  // the same seven rooms the app sees (3 of 7 resolving gives a different frame, grid and pins).
  // ⚠ LOBBY is context-sensitive in the real table (CORRIDOR when the plan also names a living
  // room, LIVING when — as on this fixture — it does not). This mirror has no context machinery,
  // so it keys the fixture family's answer; the type-level truth is pinned in RoomLabelsTest.
  'BED ROOM': 'BEDROOM', WC: 'TOILET', PASSAGE: 'CORRIDOR', LOBBY: 'LIVING',
  // The client's Tower E&F `MAIDS RM.` — added the day every reader was found transcribing it
  // faithfully while the table discarded it (plan doc §3p; the plural was the whole gap).
  'MAIDS RM': 'BEDROOM', 'MAIDS ROOM': 'BEDROOM', 'MAID RM': 'BEDROOM', MAIDS: 'BEDROOM',
};
const LABEL_DROP = new Set(['DRESS', 'DRESSING', 'DUCT', 'LIFT', 'SHAFT', 'WARDROBE']);
/** Mirrors RoomLabels.clean: the parenthetical, the feet/inch marks, the printed dimensions, the
 *  index suffix. ⚠ The `X` between two numbers is a dimension separator (`BEDROOM 6750X4350`), not a
 *  letter — without that step a real caption never resolves, which is how this mirror first drifted. */
function cleanLabel(raw) {
  return String(raw).toUpperCase()
    .replace(/\([^)]*\)/g, ' ')
    .replace(/['"‘’“”′″]/g, ' ')
    // Dots DELETE, exactly as RoomLabels.clean does — `W.C` must become `WC`, not `W C`, or the
    // caption resolves to nothing here while Kotlin reads it fine (found by the reshape-flat pin).
    .replace(/\./g, '')
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

/**
 * ⭐ A reply that overruns the page is SHRUNK to fit, uniformly, before anything is cut off.
 * Mirrors ScanMapper.shrinkToPage.
 *
 * The reader routinely returns rooms outside the unit square — it lays out a template and the
 * template runs long. Clamping them at the edge DELETED one of the owner's three balconies and
 * flattened his utility to a third of its depth. A uniform scale + offset cancels exactly against
 * the home-framed snap, so it cannot disturb a reply that already fits.
 *
 * FAULT INJECTION 'no-shrink' restores the amputation.
 */
function shrinkToPage(boxes, inject) {
  if (inject === 'no-shrink') return boxes;
  const fin = (v) => typeof v === 'number' && Number.isFinite(v);
  const real = boxes.filter((b) => fin(b.x) && fin(b.y) && fin(b.w) && fin(b.h));
  if (!real.length) return boxes;
  // ⭐ ONE room outside the page is that room being wrong (clamped and flagged, or dropped if it is
  // entirely off the sheet); SEVERAL is the layout running long, which is the only case a scale can
  // fix. Mirrors ScanMapper.MIN_SPILLED_ROOMS.
  const spilled = real.filter((b) => b.x < 0 || b.y < 0 || b.x + b.w > 1 || b.y + b.h > 1).length;
  if (spilled < MIN_SPILLED_ROOMS) return boxes;
  const x0 = Math.min(0, ...real.map((b) => b.x)), y0 = Math.min(0, ...real.map((b) => b.y));
  const x1 = Math.max(1, ...real.map((b) => b.x + b.w)), y1 = Math.max(1, ...real.map((b) => b.y + b.h));
  const span = Math.max(x1 - x0, y1 - y0);
  if (span <= 1 || span > MAX_OVERFLOW) return boxes;
  const s = 1 / span;
  return boxes.map((b) => (fin(b.x) && fin(b.y) && fin(b.w) && fin(b.h)
    ? { ...b, x: (b.x - x0) * s, y: (b.y - y0) * s, w: b.w * s, h: b.h * s }
    : b));
}

/**
 * ⭐ Widen the grid until the home's biggest room can be drawn the way its own plan prints it.
 * Mirrors ScanMapper.widenForWidest. Never narrows; does nothing unless the printed sizes prove the
 * grid too narrow to be honest.
 *
 * FAULT INJECTION 'no-widen' removes it — the state in which the owner's living/dining, printed
 * plainly wider than deep, came out taller than wide because the grid had only four columns.
 */
function widenForWidest([cols, rows], boxes, inject) {
  if (inject === 'no-widen') return [cols, rows];
  const sizes = boxes.map((b) => parsePrinted(printedTextOf(b))).filter((p) => p && p.w * p.h > 0);
  if (!sizes.length) return [cols, rows];
  const total = sizes.reduce((a, p) => a + p.w * p.h, 0);
  const widest = sizes.reduce((a, p) => (p.w * p.h > a.w * a.h ? p : a));
  const ratio = widest.w / widest.h;
  if (!Number.isFinite(ratio) || ratio <= 1) return [cols, rows];
  const share = (widest.w * widest.h) / total;
  if (!Number.isFinite(share) || share <= 0) return [cols, rows];
  const need = Math.round(Math.sqrt(cols * rows * share * ratio));
  if (need < cols) return [cols, rows];
  return [Math.min(MAX_GRID, need + 1), rows];
}

function scanSanitise(b, inject) {
  const fin = (v) => typeof v === 'number' && Number.isFinite(v);
  if (!fin(b.x) || !fin(b.y) || !fin(b.w) || !fin(b.h)) return null;
  if (b.w <= 0 || b.h <= 0) return null;
  // FAULT INJECTION 'no-clamp': trust the model's coordinates instead of clamping them into the
  // unit square. A box at x = 1.4 then snaps to cells beyond the grid's east edge.
  // ⚠ `printedSize` is carried through EVERY branch. Kotlin's sanitise is a `copy()` so it survives
  // for free there; this mirror rebuilt the object and silently dropped it, which quietly disabled
  // printed sizes, the widening and their fault injections all at once — and every one of them still
  // reported green. A mirror that loses a field is a mirror that stops mirroring.
  if (inject === 'no-clamp') {
    const conf = fin(b.confidence) ? b.confidence : 0;
    return { label: b.label, printedSize: printedTextOf(b), x: b.x, y: b.y, w: b.w, h: b.h, confidence: conf };
  }
  const x0 = clampF(b.x, 0, 1), y0 = clampF(b.y, 0, 1);
  const x1 = clampF(b.x + b.w, 0, 1), y1 = clampF(b.y + b.h, 0, 1);
  if (x1 <= x0 || y1 <= y0) return null;
  const conf = fin(b.confidence) ? clampF(b.confidence, 0, 1) : 0;
  return {
    label: b.label, printedSize: printedTextOf(b),
    x: x0, y: y0, w: x1 - x0, h: y1 - y0, confidence: conf,
  };
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
const WALL_TOLERANCE = 0.5;
const WALL_MAX_SPAN = 0.75;

/**
 * Group near-equal edges into shared wall lines. Mirrors ScanMapper.wallLines.
 *
 * ⭐ A chain that grows past WALL_MAX_SPAN is split at its LARGEST INTERNAL GAP, recursively — not
 * cut off at whichever edge happened to arrive when the cap was reached. The old greedy cut was
 * order-fragile: on the jittered tower-D1 sheet the living room's top edge chained onto the master
 * bedroom's bottom edge, dragged the span over the cap, and the cut then landed BETWEEN the master
 * bedroom's bottom and the master toilet's top — the same physical wall — reopening the §2c moat
 * the wall lines exist to close. Splitting at the widest gap puts the seam where the geometry says
 * a seam is; the tolerance and cap semantics are unchanged.
 */
function scanClusterLines(values, max) {
  const sorted = [...new Set(values)].sort((p, q) => p - q);
  const assigned = new Map();
  const emit = (lo, hi) => {           // sorted[lo..hi] inclusive, tolerance-chained
    if (sorted[hi] - sorted[lo] > WALL_MAX_SPAN && hi > lo) {
      let cut = lo, widest = -1;
      for (let k = lo; k < hi; k++) {
        const gap = sorted[k + 1] - sorted[k];
        if (gap > widest) { widest = gap; cut = k; }
      }
      emit(lo, cut); emit(cut + 1, hi);
      return;
    }
    let sum = 0;
    for (let k = lo; k <= hi; k++) sum += sorted[k];
    const line = clampInt(Math.round(sum / (hi - lo + 1)), 0, max);
    for (let k = lo; k <= hi; k++) assigned.set(sorted[k], line);
  };
  let i = 0;
  while (i < sorted.length) {
    let j = i + 1;
    while (j < sorted.length && sorted[j] - sorted[j - 1] <= WALL_TOLERANCE) j++;
    emit(i, j - 1);
    i = j;
  }
  return assigned;
}

/**
 * ⭐ The home FILLS the grid — one scale per axis, no letterbox. Mirrors ScanMapper.snapAll.
 *
 * The old uniform fit (one scale, centred in the slack axis) letterboxed the home whenever the
 * grid's whole-cell shape disagreed with the frame's: on the owner's flat the widened 5-column grid
 * held 3.64 columns of content, and the 0.68-cell margin rounded into a FULL EMPTY COLUMN on the
 * west — his home drawn hugging the east edge, off the west wall his sheet puts it on. Worse, the
 * column widenForWidest added for the living room was eaten by that margin instead of widening
 * anything. Per-axis fill hands the widened column to the rooms, which is what it was FOR.
 *
 * The distortion the uniform fit protected against no longer needs protecting: every sized room's
 * proportions are re-imposed from the PRINTED size (reshapeToPrinted), and the stretch is bounded —
 * the grid's shape comes from the home's own aspect, so the slack is rounding plus deliberate
 * widening, under one cell either axis on every recorded plan.
 *
 * FAULT INJECTION 'letterbox' restores the uniform centred fit.
 */
function scanProject(frame, imageAspect, cols, rows, inject) {
  const a = (typeof imageAspect === 'number' && Number.isFinite(imageAspect) && imageAspect > 0) ? imageAspect : 1;
  const pw = frame.w * a, ph = frame.h;
  let sx = cols / pw, sy = rows / ph, ox = 0, oy = 0;
  if (inject === 'letterbox') {
    sx = sy = Math.min(cols / pw, rows / ph);
    ox = (cols - pw * sx) / 2; oy = (rows - ph * sy) / 2;
  }
  return { cx: (v) => ((v - frame.x) * a) * sx + ox, cy: (v) => (v - frame.y) * sy + oy };
}

function scanWallLines(boxes, frame, imageAspect, cols, rows, inject) {
  const { cx, cy } = scanProject(frame, imageAspect, cols, rows, inject);
  const xs = [], ys = [];
  for (const b of boxes) { xs.push(cx(b.x), cx(b.x + b.w)); ys.push(cy(b.y), cy(b.y + b.h)); }
  return { x: scanClusterLines(xs, cols), y: scanClusterLines(ys, rows) };
}

function scanSnap(b, frame, imageAspect, cols, rows, inject, walls) {
  const { cx, cy } = scanProject(frame, imageAspect, cols, rows, inject);
  const L0 = cx(b.x);
  const R0 = cx(b.x + b.w);
  const T0 = cy(b.y);
  const B0 = cy(b.y + b.h);
  const L = walls ? (walls.x.get(L0) ?? L0) : L0;
  const R = walls ? (walls.x.get(R0) ?? R0) : R0;
  const T = walls ? (walls.y.get(T0) ?? T0) : T0;
  const B = walls ? (walls.y.get(B0) ?? B0) : B0;
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

/**
 * ⭐⭐ The LARGEST free sub-rectangle of [cand] — exact, replacing the greedy edge-retraction above.
 *
 * The greedy chain retracted one whole edge per collision, each step locally best, and the sum was
 * often terrible: measured on the corpus, plan-014's living/dining went 5x4 → 2x3 (wide → DEEP) and
 * plan-026's lobby/dining 4x3 → 1x3, because each retraction threw away a full slice when the
 * blocker only touched a corner of it. This searches every sub-rectangle instead (≤ ~3000 for a
 * 10×10 room, O(1) apiece via prefix sums) and keeps the best by: most cells, then agreement with
 * the PRINTED orientation, then least displaced. Exact beats greedy, and the tie-break means a room
 * never gives up the way its own sheet says it runs to save zero cells.
 *
 * Mirrors ScanMapper.bestFreeSubRect. FAULT INJECTION 'greedy-trim' restores the old chain — the
 * state that inverted big rooms and could lose one entirely while free cells sat inside its own
 * read rectangle (LOST-WITH-ROOM-TO-SPARE fires).
 */
function bestFreeSubRect(cand, blockers, printedRatio) {
  const W = cand.w, H = cand.h;
  if (W < 1 || H < 1) return null;
  // pre[r][c] = blocked cells in cand's own frame, rows/cols < r,c (prefix sum)
  const pre = Array.from({ length: H + 1 }, () => new Array(W + 1).fill(0));
  for (let r = 0; r < H; r++) {
    for (let c = 0; c < W; c++) {
      const cell = { col: cand.col + c, row: cand.row + r, w: 1, h: 1 };
      const hit = blockers.some((b) => overlaps(b, cell)) ? 1 : 0;
      pre[r + 1][c + 1] = hit + pre[r][c + 1] + pre[r + 1][c] - pre[r][c];
    }
  }
  const agree = (w, h) => (printedRatio == null || printedRatio === 1 ? 1
    : w === h ? 1 : (printedRatio > 1) === (w > h) ? 2 : 0);
  let best = null;
  for (let r0 = 0; r0 < H; r0++) {
    for (let r1 = r0 + 1; r1 <= H; r1++) {
      for (let c0 = 0; c0 < W; c0++) {
        for (let c1 = c0 + 1; c1 <= W; c1++) {
          if (pre[r1][c1] - pre[r0][c1] - pre[r1][c0] + pre[r0][c0] > 0) continue;
          const w = c1 - c0, h = r1 - r0;
          const score = [w * h, agree(w, h), -(r0 + c0), -r0, -c0];
          let better = best == null;
          if (!better) {
            for (let i = 0; i < score.length; i++) {
              if (score[i] === best.score[i]) continue;
              better = score[i] > best.score[i];
              break;
            }
          }
          if (better) best = { score, rect: { col: cand.col + c0, row: cand.row + r0, w, h } };
        }
      }
    }
  }
  return best ? best.rect : null;
}

/** The whole mapper. Returns {kind:'placed'|'assisted'|'refused', ...}. */
// ---- printed room sizes (RoomDimensions.kt) --------------------------------------------------
// The reader reads TEXT at ~95 % and guesses rectangles at 40-70 %, so where a caption prints the
// room's size that number beats the rectangle it arrived with. Mirrors RoomDimensions.parse.
// ASCII forms too (plan doc \u00a73p): the models transcribe a printed \u00bd as the three characters 1/2,
// so 10'-7\u00bd" arrives as 10'-71/2". Regex backtracking resolves it: inches=71 leaves /2 unmatched,
// the engine retreats to 7 + 1/2. (A transcribed 11\u00bd parses as 1\u00bd \u2014 ambiguous ASCII, under-read
// on purpose: ten inches under beats refusing the size.) Mirrors RoomDimensions.FRACTIONS.
const FRACTIONS = { '\u00bd': 0.5, '\u00bc': 0.25, '\u00be': 0.75, '\u2153': 1/3, '\u2154': 2/3, '\u215b': 0.125,
  '1/2': 0.5, '1/4': 0.25, '3/4': 0.75, '1/3': 1/3, '2/3': 2/3, '1/8': 0.125 };
// \u26a0 DOUBLE-escaped, because this is a plain string fed to `new RegExp`. It was written with single
// backslashes once, and JavaScript silently reads "\d" in a string literal as the letter d \u2014 so the
// pattern could never match, every feet-inches size in the corpus silently failed to parse in this
// mirror, and the PRINTED-ORIENTATION invariant never judged a single feet-inch room while Kotlin
// parsed them all. Found by the corpus audit: plan-020, sized on every caption, came out ASSISTED
// here and PLACED in Kotlin. A mirror that quietly skips a branch is a mirror that stops mirroring.
// The (?!/) after the inches is load-bearing (mirrors RoomDimensions): at the END of a caption
// nothing forces backtracking, so 9'-41/2" would match as 41 inches with /2" as trailing junk.
const FRACTION = "(\u00bd|\u00bc|\u00be|\u2153|\u2154|\u215b|1/2|1/4|3/4|1/3|2/3|1/8)";
const FEET_INCHES = "(\\d+)\\s*['\u2032\u2019]\\s*-?\\s*(\\d+)?(?!/)\\s*" + FRACTION + "?\\s*[\"\u2033\u201d]?";
const PAIR_FEET_INCHES = new RegExp(FEET_INCHES + "\\s*[X\u00d7]\\s*" + FEET_INCHES);
const PAIR_PLAIN = /(?<![\d.'"\u2032\u2033])(\d{2,5})\s*(?:MM|CM)?\s*[X\u00d7]\s*(\d{2,5})\s*(?:MM|CM)?(?!\d)/;
const MM_PER_FOOT = 304.8, FEET_IF_UNDER = 100;

function feetInches(f, i, fr) {
  return Number(f) + (Number(i || 0) + (fr ? (FRACTIONS[fr] || 0) : 0)) / 12;
}

/**
 * ⭐ The reader's own `size` field first, the caption second. Mirrors RoomDimensions.of.
 *
 * `size` is the name ON THE WIRE (what the model returns and what the recorded fixtures store);
 * `printedSize` is what Kotlin calls it after deserialising. Both are accepted so a fixture can be
 * read here exactly as it sits on disk.
 */
function printedTextOf(box) {
  if (!box) return '';
  const sz = box.printedSize != null ? box.printedSize : box.size;
  return (typeof sz === 'string' && sz.trim()) ? sz : (box.label || '');
}

/** Metres as Indian sheets print them: `3.72m X 4.50m`. The M is required after BOTH numbers, or
 *  `12X14` — a foot pair — would read as a twelve-metre room. Mirrors RoomDimensions.PAIR_METRES. */
const PAIR_METRES = /(?<![\d.])(\d{1,2}(?:\.\d{1,3})?)\s*M\s*[X×]\s*(\d{1,2}(?:\.\d{1,3})?)\s*M(?![A-Z])/;
const MM_PER_METRE = 1000;

function parsePrinted(label) {
  const s = String(label).toUpperCase();
  const m = PAIR_FEET_INCHES.exec(s);
  if (m) {
    const a = feetInches(m[1], m[2], m[3]), b = feetInches(m[4], m[5], m[6]);
    return (a > 0 && b > 0) ? { w: a * MM_PER_FOOT, h: b * MM_PER_FOOT } : null;
  }
  const me = PAIR_METRES.exec(s);
  if (me) {
    const a = Number(me[1]), b = Number(me[2]);
    return (a > 0 && b > 0) ? { w: a * MM_PER_METRE, h: b * MM_PER_METRE } : null;
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
 * ⭐ A SINGLE-dimension strip caption: `BALCONY 1825 WIDE` / `5'-5" WIDE`. Mirrors
 * RoomDimensions.stripDepth.
 *
 * On real sheets this caption sits on the strips — balconies, service ledges — and the number is
 * the strip's clear DEPTH (how far it comes out from the home), the one dimension a strip has;
 * its length is whatever wall it runs along. It used to parse as NO size, which produced the
 * owner's 336 sq ft plan: every sized room shrank to print scale while the balcony kept the
 * reader's sketch rectangle — a quarter of the whole page — and towered over every real room.
 * The pair forms above win first, so `10'X12' WIDE OPEN` can never half-match as a strip.
 */
const STRIP_FEET_WIDE = new RegExp(FEET_INCHES + "\\s*WIDE");
const STRIP_PLAIN_WIDE = /(?<![\d.'"′″])(\d{2,5})\s*(?:MM|CM)?\s*WIDE/;

function parseStripDepth(label) {
  const s = String(label).toUpperCase();
  if (parsePrinted(s)) return null;
  const f = STRIP_FEET_WIDE.exec(s);
  if (f) {
    const d = feetInches(f[1], f[2], f[3]);
    return d > 0 ? d * MM_PER_FOOT : null;
  }
  const p = STRIP_PLAIN_WIDE.exec(s);
  if (p) {
    const d = Number(p[1]);
    if (!(d > 0)) return null;
    return d < FEET_IF_UNDER ? d * MM_PER_FOOT : d;
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
  const printed = snapped.map((s) => parsePrinted(printedTextOf(s.c.box)));
  let cellTotal = 0, printedTotal = 0;
  snapped.forEach((s, i) => {
    if (printed[i]) { cellTotal += s.rect.w * s.rect.h; printedTotal += printed[i].w * printed[i].h; }
  });
  if (cellTotal <= 0 || printedTotal <= 0) return snapped;
  // ⚠ MEASURED AND NOT SHIPPED (2 Aug 2026): deriving this scale from the HOME's box instead of the
  // sketch's cells — (cols*rows − unsized cells) / printed total, so the sized rooms share the whole
  // home. It assumes wall-to-wall rooms, overshoots by the wall/void share, and the overlap trims
  // then FLIPPED six rooms across the corpus against their own printed orientation (138/148 → 135,
  // including the owner's kitchen, 3x2 → 1x2). The grid-fill change alone grows every snapped cell
  // and takes the corpus to 140/148 with nothing flipped, so the sketch-derived scale stays.
  const cellsPerSquareMm = cellTotal / printedTotal;
  // ⭐ The plan's own wall lines, so a re-shaped room's far edges CLICK back onto a neighbour's wall
  // instead of shrinking one cell clear of it and re-opening the moat the shared snap just closed.
  const xLines = new Set(), yLines = new Set();
  for (const s of snapped) { xLines.add(s.rect.col); xLines.add(right(s.rect)); yLines.add(s.rect.row); yLines.add(bottom(s.rect)); }
  const magnet = (edge, lines, lowest, highest) => {
    let best = null;
    for (const l of lines) if (l >= lowest && l <= highest && (best === null || Math.abs(l - edge) < Math.abs(best - edge))) best = l;
    return (best !== null && Math.abs(best - edge) <= 1 && inject !== 'no-magnet') ? best : edge;
  };
  // ⭐ Where the printed size and the observed walls disagree by ONE cell, the gap breaks toward
  // the UNSIZED side. A room read flush between two walls four cells apart whose caption prints
  // three has to give one flushness up; giving up the wrong one reopens the §2c moat (the master
  // toilet a cell adrift of the master bedroom, the defect the owner photographed). A neighbour
  // that carries a printed size is position evidence — its own reshape anchors that shared wall —
  // so the re-shaped room SLIDES one cell to stay flush with the sized neighbour and lets the gap
  // open against the unsized one. The slide stays inside the room's own read span (never
  // relocation), never resizes (printed proportions untouched), and is skipped entirely when both
  // sides are sized or both unsized (no evidence either way).
  const printedOf = new Map(snapped.map((s, i) => [s.rect, printed[i]]));
  const flushSized = (edgeTest) => snapped.some((o) => edgeTest(o.rect) && printedOf.get(o.rect));
  const rightFlushSized = (rect) => flushSized((o) => o !== rect
    && o.col === right(rect) && o.row < bottom(rect) && rect.row < bottom(o));
  const leftFlushSized = (rect) => flushSized((o) => o !== rect
    && right(o) === rect.col && o.row < bottom(rect) && rect.row < bottom(o));
  const bottomFlushSized = (rect) => flushSized((o) => o !== rect
    && o.row === bottom(rect) && o.col < right(rect) && rect.col < right(o));
  const topFlushSized = (rect) => flushSized((o) => o !== rect
    && bottom(o) === rect.row && o.col < right(rect) && rect.col < right(o));

  const slidDown = [], slidRight = [];   // sized rooms that slid, with their READ rects — the
  const shaped = snapped.map((s, i) => { // unsized cascade below re-anchors flush neighbours to them
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
    // ⚠ MEASURED AND NOT SHIPPED (2 Aug 2026): growing about the room's CENTRE instead of this
    // corner anchor. It reads as the fairer rule — growth spread to all four walls instead of
    // down-and-right — and on the corpus it was strictly worse: orientation 140 → 139, average fill
    // 70 → 67, and the owner's living/dining flattened from 3x2 to 3x1 because centre-shifted rooms
    // slide off the wall lines the magnet needs. The corner the reader gave stays the anchor.
    const col = clampInt(s.rect.col, 0, cols - w), row = clampInt(s.rect.row, 0, rows - h);
    // ⚠ The magnet may close a gap but must NEVER flip a room against the size its plan prints —
    // this suite caught it doing exactly that on a 1350x2250 toilet. Most-magnetic candidate first;
    // doing nothing always agrees, because the rounding above is monotonic.
    // A SCORE, not a yes/no: 2 it runs the right way, 1 it is square, 0 it runs the wrong way.
    // Clicking onto a wall may never make a room agree with its own plan LESS than it already did —
    // a plain yes/no let a kitchen printed 6'-11"x9'-7" be stretched from deeper-than-wide to square.
    const agree = (ww, hh) => (ratio === 1 ? 2 : ww === hh ? 1 : ((ratio > 1) === (ww > hh) ? 2 : 0));
    const free = agree(w, h);
    let col2 = col, row2 = row;
    // ⭐ The home's OUTER WALL is the strongest position evidence there is. The frame IS the home
    // (the letterbox fix), so a room READ flush against the grid's east or south edge was read
    // against the building's own outer wall — and nothing can stand between a room and the outside
    // of the building. The top-left anchor was abandoning exactly that: the owner's 336 sq ft
    // bedroom, read flush to the east wall his sheet puts it on, shrank to its printed width and
    // drifted two columns off it, leaving a dead block on the home's east edge. The shrink slack
    // belongs INTERIOR (walls, wardrobes, the sketch's own inflation), never between a room and
    // an outer wall it was read touching. West and north flushness survive automatically (the
    // top-left corner is the anchor); this is the same promise for the other two walls. A room
    // read flush BOTH sides of an axis keeps the top-left default (either end abandons an outer
    // wall; the evidence ties).
    //
    // ⚠ Same discipline as the slide: the anchor may only enter cells NO other read rectangle
    // holds. Without the guard it shoved plan-009's hall — read mid-plan, flush south — into the
    // bedroom read below-left of it, and the trimmer crushed the hall to a 2x1 sliver. Closing a
    // template gap is evidence; opening a fight with a room the reader drew is not.
    // FAULT INJECTION 'no-edge-anchor' restores the drift.
    const bandFree = (c0, r0, ww, hh) => ww <= 0 || hh <= 0 || !snapped.some((o) => o.rect !== s.rect
      && overlaps(o.rect, { col: c0, row: r0, w: ww, h: hh }));
    // The band is CLIPPED to the room's own read span in the cross axis: an east anchor owns only
    // the horizontal displacement, so a fight the shaped rect's GROWTH picks (a grown row reaching
    // a neighbour below — it does so at either position) stays the trimmer's business, exactly as
    // it is without the anchor.
    const bandE = [Math.max(row, s.rect.row), Math.min(row + h, bottom(s.rect))];
    const anchoredE = inject !== 'no-edge-anchor'
      && right(s.rect) === cols && s.rect.col > 0 && col + w !== cols
      && bandFree(col + w, bandE[0], (cols - w) - col, bandE[1] - bandE[0]);
    if (anchoredE) col2 = cols - w;
    const bandS = [Math.max(col2, s.rect.col), Math.min(col2 + w, right(s.rect))];
    const anchoredS = inject !== 'no-edge-anchor'
      && bottom(s.rect) === rows && s.rect.row > 0 && row + h !== rows
      && bandFree(bandS[0], row + h, bandS[1] - bandS[0], (rows - h) - row);
    if (anchoredS) row2 = rows - h;
    if (inject !== 'no-magnet') {
      // …and only into cells NO other read rectangle holds: the slide may close a rounding gap,
      // never open a fight with a neighbour the reader actually drew there.
      const stripFree = (c0, r0, ww, hh) => !snapped.some((o) => o.rect !== s.rect
        && overlaps(o.rect, { col: c0, row: r0, w: ww, h: hh }));
      if (!anchoredE && col2 + w === right(s.rect) - 1 && rightFlushSized(s.rect) && !leftFlushSized(s.rect)
        && stripFree(col2 + w, row2, 1, h)) { col2 = col2 + 1; slidRight.push(s.rect); }
      if (!anchoredS && row2 + h === bottom(s.rect) - 1 && bottomFlushSized(s.rect) && !topFlushSized(s.rect)
        && stripFree(col2, row2 + h, w, 1)) { row2 = row2 + 1; slidDown.push(s.rect); }
    }
    const rightE = magnet(col2 + w, xLines, col2 + 1, cols);
    const bottomE = magnet(row2 + h, yLines, row2 + 1, rows);
    const [rr, bb] = [[rightE, bottomE], [rightE, row2 + h], [col2 + w, bottomE], [col2 + w, row2 + h]]
      .find(([x, y]) => agree(x - col2, y - row2) >= free);
    const out = { col: col2, row: row2, w: rr - col2, h: bb - row2 };
    return { ...s, rect: out, asRead: out };
  });
  // ⭐ The unsized cascade: a room with no printed size that was read FLUSH on the side a slide
  // vacated rides one cell along with it. Without this the slide preserved the sized wall but set
  // its unsized neighbour adrift — on tower-D1 each balcony floated one cell off the bedroom it
  // belongs to (a fresh §2c moat) and the vacated strips merged into a seventeen-cell hole along
  // the north edge. One pass, unsized rooms only, one cell, and only into cells that are free once
  // the slide is accounted for; the moved rect becomes the room's own basis, exactly as a re-shaped
  // rect does.
  const cascaded = inject === 'no-magnet' ? shaped : shaped.map((s, i) => {
    if (printed[i]) return s;
    let { col, row } = s.rect;
    const { w, h } = s.rect;
    if (slidDown.some((r) => r.row === bottom(s.rect) && r.col < right(s.rect) && s.rect.col < right(r))
      && !shaped.some((o) => o.rect !== s.rect && overlaps(o.rect, { col, row: row + 1, w, h }))
      && row + 1 + h <= rows) row += 1;
    if (slidRight.some((r) => r.col === right(s.rect) && r.row < bottom(s.rect) && s.rect.row < bottom(r))
      && !shaped.some((o) => o.rect !== s.rect && overlaps(o.rect, { col: col + 1, row, w, h }))
      && col + 1 + w <= cols) col += 1;
    if (col === s.rect.col && row === s.rect.row) return s;
    const out = { col, row, w, h };
    return { ...s, rect: out, asRead: out };
  });
  // ⭐ The STRIP pass — a room whose caption prints ONE dimension (`BALCONY 1825 WIDE`) gets that
  // dimension as its printed DEPTH, on the same linear scale the pair-sized rooms set. Its length
  // and its position stay the reader's (arrangement evidence, exactly like a pair room's corner),
  // so only the strip's narrow axis changes — a strip may never flip against the way it was read.
  //
  // The ANCHOR is the slide doctrine applied to a resize: the strip keeps the long wall it shares
  // with a PAIR-SIZED room (that neighbour's own re-shape anchors the shared wall — position
  // evidence) and the gap opens toward the sketch side. Both sides sized → the two walls pin the
  // strip and its caption is outvoted: skipped. Neither → the grid edge is the home's outer wall
  // (the frame IS the home since the letterbox fix), so an edge-flush strip keeps the edge.
  // Runs AFTER the cascade so a strip that rode a slide reshapes from where it now sits.
  // FAULT INJECTION 'no-strip-captions' restores v0.6.4: the owner's 336 sq ft plan draws its
  // balcony three rows deep — a quarter of the grid — towering over the bedroom it belongs to.
  const linear = Math.sqrt(cellsPerSquareMm);
  return cascaded.map((s, i) => {
    if (printed[i] || inject === 'no-strip-captions') return s;
    const depthMm = parseStripDepth(printedTextOf(s.c.box));
    if (!depthMm || !isFinite(linear) || linear <= 0) return s;
    const r = s.rect;
    if (r.w === r.h) return s;              // no long axis — which way the strip runs is unreadable
    const wide = r.w > r.h;                 // a wide strip's depth is its HEIGHT; a tall one's, its WIDTH
    const long = wide ? r.w : r.h;
    // Capped by the long axis (a strip may never flip against the way it was read) AND by the
    // grid's cross axis (a depth the grid cannot hold — seen on a 10x4 letterbox fuzz grid —
    // must not push the strip off it).
    const d = clampInt(Math.round(depthMm * linear), 1, Math.min(long, wide ? rows : cols));
    if (d === (wide ? r.h : r.w)) return s;
    const sizedAt = (test) => cascaded.some((o, j) => o !== s && printed[j] && test(o.rect));
    let out;
    if (wide) {
      const below = sizedAt((o) => o.row === bottom(r) && o.col < right(r) && r.col < right(o));
      const above = sizedAt((o) => bottom(o) === r.row && o.col < right(r) && r.col < right(o));
      if (below && above) return s;
      let row;
      if (below) row = bottom(r) - d;
      else if (above) row = r.row;
      else if (bottom(r) === rows && r.row > 0) row = rows - d;
      else row = r.row;
      out = { col: r.col, row: clampInt(row, 0, rows - d), w: r.w, h: d };
    } else {
      const rightS = sizedAt((o) => o.col === right(r) && o.row < bottom(r) && r.row < bottom(o));
      const leftS = sizedAt((o) => right(o) === r.col && o.row < bottom(r) && r.row < bottom(o));
      if (rightS && leftS) return s;
      let col;
      if (rightS) col = right(r) - d;
      else if (leftS) col = r.col;
      else if (right(r) === cols && r.col > 0) col = cols - d;
      else col = r.col;
      out = { col: clampInt(col, 0, cols - d), row: r.row, w: d, h: r.h };
    }
    return { ...s, rect: out, asRead: out, strip: depthMm };
  });
}

function scanMap(draft, imageAspect, opts = {}) {
  const dropped = [];
  const clean = [];
  const inject = opts.inject || null;
  // ⭐ Shrink an overrunning reply onto the page BEFORE anything is cut off it. See shrinkToPage.
  for (const b of shrinkToPage(draft.rooms || [], inject)) {
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
  // The audit tool substitutes the FULL synonym table here (this mirror deliberately carries a
  // cut-down one — see the Suite E header). Absent an override, behaviour is byte-identical.
  const resolve = opts.resolveLabel || resolveLabel;
  for (const b of clean) {
    const m = resolve(b.label);
    if (m.kind === 'room') typed.push({ box: b, type: m.type, flags: new Set() });
    else if (m.kind === 'drop') dropped.push({ label: b.label, reason: 'NOT_HABITABLE' });
    else { unknown++; dropped.push({ label: b.label, reason: 'UNKNOWN_LABEL' }); }
  }

  // ⭐⭐ A TYPED room is NEVER dropped for sitting inside another room's rectangle.
  //
  // The geometric sub-area drop was measured across every recorded real reply (41 plans,
  // tools/scan-eval/audit-mapper.mjs): it fired 24 times, and EVERY ONE was a real scored room —
  // nine toilets, a pooja, balconies, a study, a servant room. Not a single genuine dressing area
  // reached it, because genuine sub-areas are caught BY NAME (dress/wardrobe/duct) before geometry
  // runs. The reader lays out template rectangles, so "wholly inside another room" describes its
  // sloppiness, not the home. The contained room stays; the overlap resolver trims the pair apart.
  // FAULT INJECTION 'drop-contained' restores the drop — the owner's own master toilet vanishes.
  const rooms = [];
  for (const c of typed) {
    if (inject === 'drop-contained' && typed.some((o) => o !== c && containedIn(c.box, o.box))) {
      dropped.push({ label: c.box.label, reason: 'SUB_AREA' });
    } else rooms.push(c);
  }
  if (!rooms.length) return { kind: 'refused', reason: unknown > 0 ? 'NO_LABELS' : 'NO_ROOMS', notes: notes() };

  const identified = rooms.slice().sort((a, b) => a.box.y - b.box.y || a.box.x - b.box.x)
    .map((c) => ({ type: c.type, label: c.box.label, rect: null }));

  // FAULT INJECTION 'no-uniform-gate' / 'no-coverage-gate': trust the model's rectangles. Each is
  // the shape of a plausible "why are we throwing away geometry?" change.
  if (inject !== 'no-uniform-gate' && rooms.length >= UNIFORM_MIN_ROOMS && variation < UNIFORM_AREA_VARIATION) {
    return { kind: 'assisted', reason: 'UNIFORM_BOXES', rooms: identified, notes: notes() };
  }
  // ⭐ A sheet naming a LIFT shows a whole floor, not one home — the distinction the room count was
  // proxying for, said outright. Checked against everything read, including spaces already dropped
  // as not habitable, because a lift is exactly the kind of space that gets dropped.
  if ((draft.rooms || []).some((b) => isFloorPlateLabel(b.label))) {
    return { kind: 'assisted', reason: 'FLOOR_PLATE', rooms: identified, notes: notes() };
  }
  if (rooms.length > MAX_ROOMS_EVER) {
    return { kind: 'assisted', reason: 'TOO_MANY_ROOMS', rooms: identified, notes: notes() };
  }
  // ⭐⭐ …and below that ceiling the count only decides a plan whose rooms state no sizes.
  const sizedCount = rooms.filter((c) => parsePrinted(printedTextOf(c.box))).length;
  if (inject !== 'no-roomcount-gate'
    && sizedCount < rooms.length * SIZED_SHARE_TO_TRUST
    && rooms.length > MAX_TRUSTED_ROOMS) {
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
  let [cols, rows] = widenForWidest(
    scanGridFor(scanHomeAspect(frame, imageAspect), inject), rooms.map((c) => c.box), inject,
  );
  // ⭐ The plan's own WALL LINES: edges within half a cell of each other are the same wall, agreed
  // once and rounded once. Without it, a shared wall reported as 3.48 by one room and 3.52 by its
  // neighbour rounds to 3 and 4 and opens a moat between two rooms that touch.
  // FAULT INJECTION 'no-walls' removes it — the state that shipped a home as a scatter of islands.
  // ⚠ Since the sized-flush slide (2 Aug 2026) the four BUNDLED fixtures no longer go red under
  // this injection — the slide and the magnet re-close the specific seams those pins assert. That
  // is stated rather than dressed up (the frame-picture precedent): the proof lives in the corpus
  // audit instead, where no-walls still tears real plans open — plan-015 empty 5 → 18, plan-017
  // 40 → 54, plan-014 17 → 22 (tools/scan-eval/audit-mapper.mjs --inject=no-walls).
  const walls = inject === 'no-walls' ? null : scanWallLines(rooms.map((c) => c.box), frame, imageAspect, cols, rows, inject);
  let snapped = [];
  for (const c of rooms) {
    const rect = scanSnap(c.box, frame, imageAspect, cols, rows, inject, walls);
    if (!rect) { roundedAway.push(c.box.label); dropped.push({ label: c.box.label, reason: 'DEGENERATE' }); }
    // The printed size is attached here, NOT inside reshapeToPrinted, so that deleting the
    // reshaping (--inject=no-printed-sizes) still leaves the invariant something to judge. An
    // injection that removes a feature has to go red; one that merely stops recording it would not.
    // ⚠ Read via printedTextOf — the `size` FIELD first, the caption second. It read the caption
    // only, so the PRINTED-ORIENTATION invariant never judged a single reply from the current
    // prompt (which puts every size in its own field) while reporting green. Found by the audit.
    else snapped.push({ c, rect, asRead: rect, asReadRaw: rect, printed: parsePrinted(printedTextOf(c.box)) });
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
      const blockers = placed.map((p) => p.rect);
      // A strip has no printed pair, but its rect IS its printed sense (depth from the caption,
      // length as read) — hand that to the trim so a cut prefers keeping it a strip. Judged from
      // the caption, not from whether the re-shape moved anything, so Kotlin can mirror it without
      // carrying the strip flag through placement.
      const ratio = s2.printed ? s2.printed.w / s2.printed.h
        : (s2.rect.w !== s2.rect.h && parseStripDepth(printedTextOf(s2.c.box)) ? s2.rect.w / s2.rect.h : null);
      if (inject === 'repack') {
        trimmed = (fitWithoutOverlap([...blockers, s2.rect], cols, rows) || []).slice(-1)[0] || null;
      } else if (inject === 'no-trim') {
        trimmed = s2.rect;
      } else if (inject === 'greedy-trim') {
        // The pre-audit behaviour: single-edge retraction, read-shape fallback, corner cell only.
        // Kept as an injection because it FAILS two ways the search cannot: it inverts big rooms
        // (PRINTED-ORIENTATION would fire post-trim if judged there; the pinned corpus cases fire
        // instead) and it can lose a room while free cells sit inside its own rectangle
        // (LOST-WITH-ROOM-TO-SPARE).
        trimmed = scanTrimAgainst(s2.rect, blockers);
        if (!trimmed) { basis = s2.asReadRaw || s2.rect; trimmed = scanTrimAgainst(basis, blockers); }
        if (!trimmed) {
          const one = { col: s2.rect.col, row: s2.rect.row, w: 1, h: 1 };
          if (!blockers.some((b) => overlaps(b, one))) { trimmed = one; basis = s2.rect; }
        }
      } else {
        // ⭐ Exact largest-free-sub-rectangle, orientation-aware — see bestFreeSubRect. A one-cell
        // survivor is just the smallest sub-rectangle, so the old corner fallback is subsumed.
        trimmed = bestFreeSubRect(s2.rect, blockers, ratio);
        if (!trimmed) {         // the printed shape's whole footprint is blocked; try the read shape
          basis = s2.asReadRaw || s2.rect;
          trimmed = bestFreeSubRect(basis, blockers, ratio);
        }
      }
      if (!trimmed) { lost.push(s2.c); continue; }
      // `asRead` is the rectangle this placement was actually DERIVED from, so the anti-relocation
      // check compares like with like whether the printed shape or the read shape was used. It still
      // catches a relocating packer, which moves a room to a free slot unrelated to either.
      placed.push({ c: s2.c, rect: trimmed, asRead: basis, printed: s2.printed || null, shaped: s2.rect, strip: s2.strip || null });
    }
    // ⚠ MEASURED AND NOT BUILT: a relaxation pass (each room re-trimmed against everyone's FINAL
    // rectangles until nothing improves, hoping to reclaim cells a mid-queue trim freed). Run
    // across all 41 recorded replies it changed NOTHING — the cells a big room loses are held by
    // neighbours whose placement is legitimate, so there is nothing to reclaim. Machinery that
    // cannot be shown to change an answer does not ship (the exp-frame.py precedent).
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
    // ⭐ Strengthened from "its corner cell was free" to "ANY cell of its own rectangle was free":
    // the search-based trim can always keep one cell anywhere inside the rectangle, so a loss is
    // only ever legal when another room holds every single cell the room was read at.
    const src = snapped.find((x) => x.c === c);
    if (!src) continue;
    let anyFree = false;
    for (let rr = src.rect.row; rr < bottom(src.rect) && !anyFree; rr++) {
      for (let cc = src.rect.col; cc < right(src.rect) && !anyFree; cc++) {
        if (!placed.some((pp) => overlaps(pp.rect, { col: cc, row: rr, w: 1, h: 1 }))) anyFree = true;
      }
    }
    if (anyFree) lostWithFreeCorner.push(c.box.label);
  }

  if (placed.length < MIN_PLACED_ROOMS || placed.length < rooms.length * MIN_PLACED_FRACTION) {
    return { kind: 'assisted', reason: 'TOO_FEW_PLACED', rooms: identified, notes: notes() };
  }
  // ⭐ A grid row or column that ends up entirely OUTSIDE the rooms is deleted, edge-inward.
  //
  // The per-axis fill guarantees the READ frame fills the grid — but the re-shapes then shrink
  // rooms to the size their sheet PRINTS, and where the sketch was inflated (the template's fat
  // strips, a room read wider than its caption) whole edge strips of grid go dead. A dead edge
  // strip is a statement — "this is part of your home and we found nothing in it" — and it is
  // false: the print evidence says the home ENDS where the rooms do. It is also the exact thing
  // the owner photographs (Q14: "a full empty strip down any side is the bug").
  //
  // The engine is untouched by this: it scores the FOOTPRINT — the bounding box of the placed
  // rooms — which collapse preserves cell-for-cell. Only the drawing gets smaller, which on a
  // small flat also means bigger, more tappable cells. Interior holes are never touched: an
  // empty region BETWEEN rooms is honest (an unread corridor), an empty region outside them is
  // not. Never below the editor's MIN_GRID.
  // FAULT INJECTION 'no-edge-collapse' keeps the dead strips (the v0.6.4 state).
  if (inject !== 'no-edge-collapse') {
    const minC = Math.min(...placed.map((p) => p.rect.col));
    const minR = Math.min(...placed.map((p) => p.rect.row));
    const maxC = Math.max(...placed.map((p) => right(p.rect)));
    const maxR = Math.max(...placed.map((p) => bottom(p.rect)));
    if (minC > 0 || minR > 0 || maxC < cols || maxR < rows) {
      for (const p of placed) {
        for (const k of ['rect', 'asRead', 'shaped']) {
          if (p[k]) p[k] = { ...p[k], col: p[k].col - minC, row: p[k].row - minR };
        }
      }
      cols = Math.max(maxC - minC, MIN_GRID);
      rows = Math.max(maxR - minR, MIN_GRID);
    }
  }
  const out = placed.slice().sort((a, b) => a.rect.row - b.rect.row || a.rect.col - b.rect.col)
    .map((p) => ({ type: p.c.type, label: p.c.box.label, rect: p.rect, asRead: p.asRead, printed: p.printed, shaped: p.shaped, strip: p.strip }));
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
  // ⭐ Single-dimension STRIP captions (the strip's printed depth), both conventions — so the
  // strip re-shape and its anchor rules are fuzzed, not just pinned.
  'BALCONY 1825 WIDE', "SERVICE BALCONY 4'-11\" WIDE",
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

  // ⭐⭐ THE OWNER'S OWN FLAT — the first REAL recorded reply for one of his plans, and the case this
  // whole release turns on. Fifteen named spaces, every one of them carrying the size the sheet
  // prints, and rectangles that are plainly a template: four distinct x positions between them.
  //
  // Only geometry is judged here, because this mirror carries a cut-down synonym table and his sheet
  // says VESTIBULE, DRESS & PASSAGE, MBR TOILET — captions only the real table resolves. The room
  // list is pinned in Kotlin's OwnerFlatScanTest, where the real table runs.
  let owner;
  try {
    owner = JSON.parse(readFileSync(join(FIXTURES, 'owner-flat.json'), 'utf8'));
  } catch {
    problems.push('owner-flat: fixture missing from shared/src/main/resources/scan/');
  }
  if (owner) {
    const o = scanMap(owner.reply, owner.imageSize[0] / owner.imageSize[1], opts);
    if (o.kind !== 'placed') {
      // Before this release his flat came back ASSISTED — thirteen identical squares parked in a row
      // — because it has fifteen rooms and the gate was twelve.
      problems.push(`owner-flat: expected placed, got ${o.kind}/${o.reason || ''}`);
    } else {
      // The grid must be WIDE enough for his living/dining. `--inject=no-widen` leaves 4 columns,
      // and in four columns a room printed 7.25 m x 4.30 m cannot be drawn wider than it is deep.
      if (o.cols < 5) problems.push(`owner-flat: grid ${o.cols}x${o.rows} is too narrow for its widest room`);
      const living = (o.rooms || []).find((r) => /LIVING/.test(r.label));
      if (!living) problems.push('owner-flat: the living/dining did not survive');
      else if (living.rect.w < living.rect.h) {
        // `--inject=no-printed-sizes` lands here: without the sizes the sheet prints, his main room
        // comes out taller than wide, which puts it in a different Vastu direction.
        // ⚠ `<`, not `<=`, matching Kotlin's OwnerFlatScanTest: rounding is monotonic, so the room
        // may flatten to a SQUARE — it does, once his master toilet stopped being deleted and took
        // one of the cells — but it may never come out deeper than wide.
        problems.push(`owner-flat: LIVING/DINING is printed 7.25m x 4.30m but drawn `
          + `${living.rect.w}x${living.rect.h} — deeper than wide`);
      }
      // ⭐⭐ His master-bedroom toilet ARRIVES. The reader drew it wholly inside the rectangle it
      // gave the living room, and the geometric sub-area drop deleted it — a 2.5-weight scored
      // room, silently gone from a paid score. Measured across all 41 recorded replies, that drop
      // fired 24 times and every single one was a real room, so it is gone; genuine sub-areas
      // (dressing, wardrobe, duct) are caught by NAME before geometry runs.
      // `--inject=drop-contained` restores the drop and this goes red.
      if (o.kind === 'placed' && !(o.rooms || []).some((r) => /MBR TOILET/.test(r.label))) {
        problems.push('owner-flat: MBR TOILET was deleted — a contained scored room must survive');
      }
      // ⭐ His home REACHES ITS OWN WEST WALL. The letterboxed fit centred 3.64 columns of content
      // in the 5-column grid, and the margin rounded into a full empty column on the west — his
      // home drawn hugging the east edge, off the wall his sheet puts it on, with the column
      // widenForWidest added eaten as margin instead of widening anything.
      // `--inject=letterbox` restores that fit and this goes red.
      if (o.kind === 'placed') {
        const minCol = Math.min(...(o.rooms || []).map((r) => r.rect.col));
        if (minCol > 0) {
          problems.push(`owner-flat: the home starts at column ${minCol} — drawn off its own west wall`);
        }
      }
    }
  }

  // ⭐ tower-D1 — the jittered replica of the owner's FIRST flat, the §2c islands memorial. This
  // fixture lived ONLY in Kotlin (ScanWallLinesTest) until the grid-fill change broke it there
  // while every suite here stayed green — a fixture gap is a drift channel, so it is pinned on
  // both sides now. The two adjacencies are the §2c doctrine itself: rooms that share a wall on
  // the sheet share a grid line here, through snap, re-shape and trim. The sized-flush SLIDE is
  // what holds the second one: the master bedroom's printed height disagrees with its observed
  // walls by one cell of rounding, and the room slides to stay flush with the sized toilet below
  // while the gap opens against the unsized balcony above — walls beat arithmetic on POSITION,
  // print keeps SIZE, and the living/dining stays strictly the biggest room on the sheet.
  {
    const towerD1 = {
      planType: '2D_PLAN', hasRoomLabels: true, planConfidence: 0.95,
      rooms: [
        { label: "BALCONY 5'-5\" WIDE", x: 0.183, y: 0.128, w: 0.141, h: 0.089, confidence: 0.9 },
        { label: "BALCONY 5'-5\" WIDE", x: 0.428, y: 0.058, w: 0.135, h: 0.081, confidence: 0.9 },
        { label: "MASTER BEDROOM 11'-0\"X12'-3\"", x: 0.161, y: 0.238, w: 0.206, h: 0.196, confidence: 0.9 },
        { label: "BEDROOM 10'-0\"X12'-0\"", x: 0.396, y: 0.161, w: 0.191, h: 0.212, confidence: 0.9 },
        { label: "BALCONY 5'-5\" WIDE", x: 0.645, y: 0.288, w: 0.182, h: 0.096, confidence: 0.9 },
        { label: "MASTER TOILET 8'-6\"X5'-5\"", x: 0.159, y: 0.461, w: 0.143, h: 0.120, confidence: 0.9 },
        { label: "TOILET 8'-0\"X5'-5\"", x: 0.351, y: 0.459, w: 0.099, h: 0.143, confidence: 0.9 },
        { label: "LIVING/DINING 19'-0\"X12'-1\"", x: 0.404, y: 0.408, w: 0.419, h: 0.268, confidence: 0.9 },
        { label: "KITCHEN 11'-0\"X7'-0\"", x: 0.545, y: 0.626, w: 0.216, h: 0.124, confidence: 0.9 },
        { label: "SERVICE BALCONY 4'-11\" WIDE", x: 0.414, y: 0.643, w: 0.106, h: 0.079, confidence: 0.9 },
      ],
    };
    const td = scanMap(towerD1, 1725 / 2000, opts);
    if (td.kind !== 'placed') {
      problems.push(`tower-D1: expected placed, got ${td.kind}/${td.reason || ''}`);
    } else {
      const rectOf = (s) => (td.rooms || []).find((r) => String(r.label).startsWith(s))?.rect;
      const m = rectOf('MASTER BEDROOM'), b = rectOf('BEDROOM'), t = rectOf('MASTER TOILET');
      if (!m || !b || !t) problems.push('tower-D1: a pinned room did not survive');
      else {
        if (m.col + m.w !== b.col) {
          problems.push(`tower-D1: master.right ${m.col + m.w} != bedroom.col ${b.col} — the shared wall split`);
        }
        if (m.row + m.h !== t.row) {
          problems.push(`tower-D1: master.bottom ${m.row + m.h} != toilet.row ${t.row} — the §2c moat reopened`);
        }
      }
    }
  }

  // ⭐ plan-020 — a real Gurgaon builder plan whose every size is printed in FEET AND INCHES
  // (`11'-0" x 15'-0"`), the convention the corpus audit found had never been exercised end-to-end
  // here: a broken escape in this mirror's own feet-inches regex meant no feet-inch size ever
  // parsed, every suite stayed green, and Kotlin (whose regex was right) disagreed with this file
  // on the plan's whole outcome — Placed there, Assisted here. Pinned so the two cannot drift
  // silently again. It also carries FOUR rooms captioned just "BALCONY" at four different printed
  // sizes — the duplicate-caption case.
  //
  // ⚠ The magnet's proof MOVED to the corpus audit (the no-walls precedent). This pin carried it
  // from v0.6.3 ("kitchen 12 cells with the magnet, 9 without") — measured while `BED ROOM-1`
  // did not resolve in this mirror's cut-down table. The greencourt-336 pins needed `BED ROOM`
  // in that table (v0.6.5); with the bedroom resolving — as it always has in Kotlin — the
  // mirror's neighbourhood changed and the kitchen here no longer needs the click (6 cells with
  // and without). The magnet still bites across the full-table corpus: without it plan-014 slips
  // an orientation and four empties, plan-017 gains eight empties, plan-026 loses an orientation
  // (tools/scan-eval/audit-mapper.mjs --inject=no-magnet). This pin keeps what the mirror can
  // honestly hold: every balcony survives, the kitchen survives, and the empties never decay.
  let p020;
  try {
    p020 = JSON.parse(readFileSync(join(FIXTURES, 'plan-020.json'), 'utf8'));
  } catch {
    problems.push('plan-020: fixture missing from shared/src/main/resources/scan/');
  }
  if (p020) {
    const o = scanMap(p020.reply, p020.imageSize[0] / p020.imageSize[1], opts);
    if (o.kind !== 'placed') {
      problems.push(`plan-020: expected placed, got ${o.kind}/${o.reason || ''}`);
    } else {
      if ((o.rooms || []).filter((r) => r.label === 'BALCONY').length !== 4) {
        problems.push(`plan-020: all four BALCONY captions must survive as their own rooms`);
      }
      const kitchen = (o.rooms || []).find((r) => /KITCHEN/.test(r.label));
      if (!kitchen) problems.push('plan-020: the kitchen did not survive');
      else if (kitchen.rect.w * kitchen.rect.h < 6) {
        problems.push(`plan-020: KITCHEN ${kitchen.rect.w}x${kitchen.rect.h} — measured 3x2 under `
          + 'the v0.6.5 mirror table; smaller means a placement rule decayed');
      }
      const rects = (o.rooms || []).map((r) => r.rect);
      const minC = Math.min(...rects.map((r) => r.col)), maxC = Math.max(...rects.map((r) => right(r)));
      const minR = Math.min(...rects.map((r) => r.row)), maxR = Math.max(...rects.map((r) => bottom(r)));
      const cells = new Set();
      for (const r of rects) for (let c = r.col; c < right(r); c++) for (let w = r.row; w < bottom(r); w++) cells.add(`${c},${w}`);
      const empty = (maxC - minC) * (maxR - minR) - cells.size;
      // Re-measured 2 Aug 2026 (v0.6.5) under the widened mirror table, with the strip, edge-anchor
      // and edge-collapse rules on: 34. A cap against quiet decay, not a magnet proof — see the
      // header note for where that proof lives now.
      if (empty > 34) {
        problems.push(`plan-020: ${empty} empty cells inside the home's box — measured 34 when pinned`);
      }
    }
  }

  // ⭐ reshape-flat — ScanReshapeTest's clean replica of the owner's Green Court Category-II sheet
  // (his captions, lattice-tidy boxes). It lived ONLY in Kotlin until v0.6.5 — the same fixture
  // gap that cost v0.6.4 three cloud rounds on tower-D1 — and porting it here immediately caught
  // a real divergence: this mirror's label cleaner turned `W.C` into `W C` (Kotlin deletes the
  // dot, giving `WC`), so the room resolved in the app and vanished here. The numbers below are
  // the measured v0.6.5 outcome; ScanReshapeTest asserts the same ones in Kotlin.
  {
    const reshapeFlat = {
      planType: '2D_PLAN', hasRoomLabels: true, planConfidence: 0.95,
      rooms: [
        { label: "BALCONY 6'-0\" WIDE", x: 0.0, y: 0.0, w: 1.0, h: 0.3, confidence: 0.9 },
        { label: "W.C 5'-0\"X2'-11\"", x: 0.0, y: 0.3, w: 0.4, h: 0.1, confidence: 0.9 },
        { label: "BATH 5'-0\"X3'-8½\"", x: 0.0, y: 0.4, w: 0.4, h: 0.2, confidence: 0.9 },
        { label: "KITCHEN 6'-11\"X9'-7\"", x: 0.0, y: 0.6, w: 0.4, h: 0.1, confidence: 0.9 },
        { label: "BED ROOM 10'-0\"X10'-6\"", x: 0.4, y: 0.3, w: 0.6, h: 0.4, confidence: 0.9 },
        { label: "LOBBY 10'-0\"X12'-9\"", x: 0.4, y: 0.7, w: 0.6, h: 0.2, confidence: 0.9 },
        { label: "PASSAGE 2'-3\"X9'-6\"", x: 0.7, y: 0.9, w: 0.3, h: 0.1, confidence: 0.9 },
      ],
    };
    const o = scanMap(reshapeFlat, 1.0, opts);
    if (o.kind !== 'placed' || (o.rooms || []).length !== 7) {
      problems.push(`reshape-flat: expected 7 rooms placed, got ${o.kind}/${(o.rooms || []).length}`);
    } else {
      const rectOf = (s) => (o.rooms || []).find((r) => r.label.startsWith(s));
      const balc = rectOf('BALCONY'), passage = rectOf('PASSAGE'), kitchen = rectOf('KITCHEN');
      const wc = rectOf('W.C'), lobby = rectOf('LOBBY');
      // The strip: full read width kept, depth from the caption (6'-0" ≈ 2 cells on this plan's
      // scale), and the row the shrink frees is collapsed — the balcony no longer towers.
      if (!balc || balc.rect.w !== 10 || balc.rect.h !== 2) {
        problems.push(`reshape-flat: BALCONY ${balc ? balc.rect.w + 'x' + balc.rect.h : 'missing'} — measured 10x2`);
      }
      if (o.rows !== 9) problems.push(`reshape-flat: grid ${o.cols}x${o.rows} — the freed row must collapse to 9`);
      if (!passage || passage.rect.w !== 1 || passage.rect.h <= passage.rect.w) {
        problems.push(`reshape-flat: PASSAGE must stay a 1-wide deep strip`);
      }
      if (!kitchen || kitchen.rect.h <= kitchen.rect.w) problems.push('reshape-flat: KITCHEN must be deeper than wide');
      if (!wc || wc.rect.w < wc.rect.h) problems.push('reshape-flat: W.C must not be deeper than wide');
      if (!lobby || lobby.rect.h < lobby.rect.w || lobby.rect.w * lobby.rect.h < 6) {
        problems.push('reshape-flat: LOBBY must never be a wide bar, and stays one of the bigger rooms');
      }
      if (wc && lobby && lobby.rect.w * lobby.rect.h <= wc.rect.w * wc.rect.h) {
        problems.push('reshape-flat: the lobby must be drawn bigger than the WC');
      }
    }
  }

  // ⭐⭐ greencourt-336 — the owner's 336 sq ft 1BHK, the plan whose drawn output he rejected
  // outright on 2 Aug 2026 ("balcony towering over the rooms, everything clumped left, a huge
  // mostly-empty grid"). Two recordings of the SAME sheet: a clean copy (arrangement reads close
  // to the paper) and the branded builder page (logo third; arrangement scrambled — bedroom read
  // full-width when the sheet has it on the right). Both carry the caption family that caused the
  // towering: `BALCONY 1825 WIDE`, a single printed dimension, which used to parse as NO size, so
  // the balcony kept the reader's quarter-page sketch rectangle while every sized room shrank to
  // print scale.
  //
  // The pins are the three drawing rules this plan forced, one injection each:
  //   · the balcony is a STRIP: 1.825 m deep on the plan's own scale = 2 rows, strictly shallower
  //     than the bedroom behind it (`--inject=no-strip-captions` → 3 rows, red);
  //   · the grid ends where the home does: 9 rows, no dead edge strip left behind by the shrink
  //     (`--inject=no-edge-collapse` → 10 rows, red);
  //   · the bedroom was READ against the home's east wall and STAYS on it when it shrinks to its
  //     printed width (`--inject=no-edge-anchor` → drifts two columns off, red).
  // The branded copy is pinned only for what honest rules can promise on a scrambled read: same
  // strip, same collapse, every room placed. Its arrangement is wrong and stays wrong — measured
  // (§3n): no signal in a single reply separates it from the clean copy, and the one candidate
  // (read-vs-print orientation contradictions) scores the owner's own flat WORSE than this page.
  for (const [id, wantRows] of [['greencourt-336-clean', 9], ['greencourt-336-branded', 9]]) {
    let rec;
    try {
      rec = JSON.parse(readFileSync(join(FIXTURES, `${id}.json`), 'utf8'));
    } catch {
      problems.push(`${id}: fixture missing from shared/src/main/resources/scan/`);
      continue;
    }
    const o = scanMap(rec.reply, rec.imageSize[0] / rec.imageSize[1], opts);
    if (o.kind !== 'placed') {
      problems.push(`${id}: expected placed, got ${o.kind}/${o.reason || ''}`);
      continue;
    }
    if ((o.rooms || []).length !== 7) problems.push(`${id}: ${o.rooms.length} rooms, expected all 7`);
    const balc = (o.rooms || []).find((r) => /BALCONY/.test(r.label));
    const bed = (o.rooms || []).find((r) => /BED/.test(r.label));
    if (!balc || !bed) { problems.push(`${id}: balcony or bedroom did not survive`); continue; }
    if (!(balc.rect.h < bed.rect.h)) {
      problems.push(`${id}: BALCONY ${balc.rect.w}x${balc.rect.h} is not shallower than the `
        + `bedroom (${bed.rect.w}x${bed.rect.h}) — the 1825 WIDE caption stopped being read`);
    }
    if (o.rows !== wantRows) {
      problems.push(`${id}: grid ${o.cols}x${o.rows}, measured ${wantRows} rows — a dead edge `
        + 'strip came back');
    }
    if (id === 'greencourt-336-clean') {
      const right = bed.rect.col + bed.rect.w;
      if (right !== o.cols) {
        problems.push(`greencourt-336-clean: BED ROOM ends at column ${right} of ${o.cols} — read `
          + 'flush against the home\'s east wall, must stay on it');
      }
    }
    if (id === 'greencourt-336-branded') {
      // The LOBBY is this flat's biggest room and the branded read crams it against the passage.
      // The exact sub-rectangle search keeps it 4x5; the greedy single-edge retraction it replaced
      // halves it to 2x5 — `--inject=greedy-trim` goes red HERE (and in the audit it still loses
      // plan-008's parking outright and an orientation on plan-026).
      const lobby = (o.rooms || []).find((r) => /LOBBY/.test(r.label));
      if (!lobby) problems.push('greencourt-336-branded: the lobby did not survive');
      else if (lobby.rect.w * lobby.rect.h < 12) {
        problems.push(`greencourt-336-branded: LOBBY ${lobby.rect.w}x${lobby.rect.h} — the exact `
          + 'trim keeps 20 cells here; under 12 means the greedy retraction is back');
      }
    }
  }

  // ⭐ A reply whose LAYOUT overruns the page is SHRUNK onto it, never cut off at the edge. This is
  // the shape his sheet produced under the previous prompt: a template that runs long, so the last
  // rooms in the sequence fall off the bottom together — and clamping deleted one of them outright,
  // a balcony gone from a home before the user ever saw the plan.
  //
  // ⚠ Two rooms past the edge, not one, and that is the point: one stray box is that box being
  // wrong and must still be clamped or dropped. See ScanMapper.MIN_SPILLED_ROOMS.
  // `--inject=no-shrink` restores the amputation and this goes red.
  const spill = [
    { label: 'LIVING ROOM', x: 0.05, y: 0.05, w: 0.4, h: 0.3, confidence: 0.9 },
    { label: 'KITCHEN', x: 0.5, y: 0.05, w: 0.3, h: 0.3, confidence: 0.9 },
    { label: 'BEDROOM', x: 0.05, y: 0.4, w: 0.4, h: 0.3, confidence: 0.9 },
    { label: 'TOILET', x: 0.5, y: 0.4, w: 0.2, h: 0.2, confidence: 0.9 },
    { label: 'STORE', x: 0.5, y: 0.9, w: 0.3, h: 0.15, confidence: 0.9 },
    { label: 'BALCONY', x: 0.05, y: 1.05, w: 0.3, h: 0.15, confidence: 0.9 },
  ];
  const sp = scanMap({ planType: '2D_PLAN', hasRoomLabels: true, rooms: spill, planConfidence: 0.95 }, 1.0, opts);
  if (sp.kind !== 'placed') problems.push(`spill: expected placed, got ${sp.kind}/${sp.reason || ''}`);
  else if (!(sp.rooms || []).some((r) => /BALCONY/.test(r.label))) {
    problems.push('spill: the room past the bottom of the page was deleted instead of shrunk onto it');
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
  console.log('✅ the owner\'s own flat arrives as a HOME: placed, on a grid wide enough for it, with');
  console.log('   its living/dining drawn the way its plan prints it — and a room past the bottom of');
  console.log('   the page is shrunk onto it rather than deleted');
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

// Exported for tools/scan-eval/audit-mapper.mjs, which runs every RECORDED real reply through this
// mirror and reports per-plan quality. Run it with `--only=X` in argv so no suite fires on import.
export { scanMap, scanInvariants, parsePrinted, parseStripDepth, printedTextOf, resolveLabel, cleanLabel };

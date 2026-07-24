# Floor-plan editor rework — direct manipulation

**Status:** **Build A shipped in v0.2.3** (2026-07-24) — items 1–5 below are built, CI green.
Build B (items 6–10) is next, after the owner has had the interaction in their hands.
Design and research below are settled; do not re-open them.
**Owner ask (2026-07-24):** *"the floor plan builder needs to be more intuitive and easy to use…
I should be able to drag, resize the shapes easily with fingers on screen… tap into it then I get
option to play around with that shape… move it, resize it by dragging the corners."*

**The agreed interaction is already built and working** as an HTML prototype at
[`tools/grid-prototype/index.html`](../tools/grid-prototype/index.html) — open it in Chrome. It is the
specification. Where this document and the prototype disagree, **the prototype wins** for behaviour;
this document owns the Compose implementation detail.

---

## 1. Why the current editor is being replaced

`GuidedGridScreen.kt` today: tap a cell to drop a 2×2 room, then resize only via `W −/+ H −/+`
steppers. No move at all — a misplaced room has to be deleted and re-added. Placement commits on
touch-down, so a mis-tap is instantly committed.

That last point matters more than it sounds: **placement decides the score.** A room one cell over
can land in a different Vastu zone and change the number the client is paying for.

---

## 2. What the research settled

Two research passes ran (mobile floor-plan/shape-editing UX; Compose gesture implementation). The
findings that drove the design, with the reasoning, so a future session does not re-litigate them:

| Decision | Why |
|---|---|
| **Commit placement on finger-LIFT, with a live preview** — not on touch-down | Potter, Weldon & Shneiderman (CHI '88) measured three commit strategies on small targets: commit-on-lift with live feedback produced **~65% fewer wrong-target errors** and ~56% fewer total errors than commit-on-touch, at ~3 s slower and *higher* satisfaction. When placement decides the score, accuracy wins. |
| **Touch-down selects, and the same finger continues into a move past touch-slop** | Requiring tap → lift → drag doubles the gesture count and novices routinely never discover step two. Touch-slop (~8 dp) already prevents accidental moves. |
| **Four corner handles; ONE handle (bottom-right) for a 1×1 room** | A cell is **34–45 dp** on every phone we support (see §3). Four 48 dp targets on a 34 dp square bury the room and each other. Edge-midpoint handles are out for the same reason. |
| **Handles: ~14 dp drawn, ≥48 dp touch, centres clamped inside the grid** | Material's 48 dp floor. Unclamped, a room on an edge has its handles half outside the grid and they cannot be grabbed at all (this was a real defect found in prototype v1). |
| **Size/zone chip pinned to a fixed row above the plan** | The hand covers whatever it is touching. A *fixed* position is never occluded and needs no flip logic. Same principle as the offset cursor in the 1988 experiment. |
| **Label the plan NORTH / EAST / SOUTH / WEST; name the zone in the chip** | This is a Vastu app. Users think in directions, and the zone name ("North-East") is the exact word the report uses. Every serious Indian Vastu competitor reduces input to directions or skips drawing entirely. |
| **Block overlap; refuse with a red preview + snap-back** | The no-error-state rule applied to input: never let the user silently create something the scorer cannot read. |
| **Do NOT dim the other rooms during a drag** | On a floor plan the neighbours are the reference you are aligning against. |
| **Keep the steppers, add move arrows** | **WCAG 2.2 SC 2.5.7 (AA)** requires a single-pointer, non-drag path for every drag action. Position on an 8×8 grid is a pair of integers, so dragging is *not* essential and the exemption does not apply. It is also the path for older and less phone-literate users, who are a large part of this audience. |
| **No long-press anywhere on the primary path** | Timing-dependent gestures are precisely what users with tremor or low phone-literacy fail. |

---

## 3. Hard constraints

**Cell size is below the touch floor on every device.** The grid is `screenWidth − 2 × spacing.s6`:

| Screen | Grid | Cell |
|---|---|---|
| 320 dp | 272 dp | **34.0 dp** |
| 360 dp | 312 dp | **39.0 dp** |
| 412 dp | 364 dp | **45.5 dp** |

Never assume a cell is a valid touch target. Everything is hit-tested with inflated bounds.

**The screen scrolls.** v0.2.2 added `verticalScroll` to `GuidedGridScreen`'s root (so the "Next"
button is reachable). A vertical finger drag inside the grid would therefore fight the page scroll.
**Resolution:** the gesture arbiter calls `change.consume()` inside the touch-slop callback, which
claims the gesture from the ancestor scroller. Do not disable the scroll; do not use
`detectDragGesturesAfterLongPress` to dodge the conflict.

**Compose 1.7.1.** `HapticFeedbackType` here has only `LongPress` and `TextHandleMove`; the richer
set (`SegmentTick`, `Reject`, `Confirm`, …) landed in Compose UI **1.8.0**. Until we bump, route
extra haptics through `LocalView.current.performHapticFeedback(HapticFeedbackConstants.X)` in
`androidMain`. Always `performHapticFeedback`, never a raw `Vibrator` — the former honours the user's
system touch-feedback setting.

**The engine must not be touched.** `GridRoom(id, type, col, row, w, h)` on an 8×8 grid is the
contract `buildPlan()` consumes. Rework the editing *interaction* only; the data model stays.

---

## 4. Implementation spec

### 4.1 Gesture architecture — one arbiter on the parent

Use **`awaitEachGesture` + `awaitFirstDown` + `awaitTouchSlopOrCancellation` + `drag`** on the grid
container, with manual hit-testing. Not per-child gestures.

Three reasons, all load-bearing:
1. Compose hit-tests by bounds, so a child cannot be hit outside its own rect — handles that hang
   outside a room would be dead zones.
2. Hit-testing happens once, on the down; the parent keeps receiving events even when the finger
   leaves its bounds. No pointer-capture dance.
3. Overlap priority becomes explicit: **selected room's handles → room bodies topmost-first → empty.**

**Hit-test priority (exact order):**
```
1. handles of the SELECTED room   (circular test, radius = 24dp, centres clamped into the grid)
2. room bodies, topmost first     (iterate the list reversed)
3. empty space                    -> deselect
```
Circular handle tests, not square — two adjacent handles on a small room must not both claim a point.

**⚠ `pointerInput(Unit)` — never key on the room list.** `pointerInput(rooms)` recreates the gesture
node whenever list identity changes; since the drag writes to the rooms, the gesture cancels itself
on the first frame and the shape freezes with no `onDragEnd`. Capture everything the block reads via
`rememberUpdatedState`. (The current code has exactly this bug shape at
`GuidedGridScreen.kt` — `pointerInput(doorMode, rooms, activeType)`.)

**⚠ Never two `pointerInput` blocks (one tap, one drag) on the same node.** Each runs its own
touch-slop bookkeeping and consumption; a tap with 4–5 px of finger roll gets claimed by the drag
detector and the tap never fires. One loop, one slop decision.

```kotlin
Modifier.pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown()
        val hit = hitTest(down.position, curRooms, curSelected, cell, handleTouchPx)

        if (hit == null) { curSelect(null); return@awaitEachGesture }

        if (hit.roomId != curSelected) curSelect(hit.roomId)   // select on DOWN
        haptics.grab()                                          // on DOWN, never on release

        var overSlop = Offset.Zero
        val slopChange = awaitTouchSlopOrCancellation(down.id) { change, over ->
            change.consume()          // <- claims the gesture from the page scroller
            overSlop = over
        } ?: return@awaitEachGesture   // null = finger lifted before slop = it was a tap

        curGrab(hit.roomId, hit.handle, hit.rect)
        var acc = overSlop
        curDelta(acc)
        val completed = drag(slopChange.id) { change ->
            acc += change.positionChange()      // RAW accumulator only
            curDelta(acc)
            change.consume()
        }
        curRelease(completed)
    }
}
```

### 4.2 State shape — the part that prevents the drift bug

```kotlin
@Immutable
data class DragState(
    val id: String,
    val handle: Handle?,                    // null = moving the whole room
    val startRect: GridRoom,                // FROZEN at finger-down, never mutated
    val rawDelta: Offset = Offset.Zero,     // pure px accumulator, write-only from pointer deltas
    val steps: IntOffset = IntOffset.Zero,  // derived snapped cell delta
)
```

**Two invariants carry the whole design:**
1. `rawDelta` is only ever `rawDelta + change.positionChange()`. Nothing snapped, clamped or rounded
   is ever written back into it.
2. The preview is a **pure function of `startRect + steps`** — never of the previous preview.

Violating either produces one of the two classic bugs: the shape falls progressively behind the
finger and never realigns (snapping the running value), or it lags by up to a full cell and jitters
at boundaries (feeding the snapped value back into the accumulator). Both are permanent once the
drag clamps at a wall.

**Hysteresis** — plain `roundToInt` flickers between two cells when the finger rests on the 0.5
boundary. Require clearly passing the midpoint before leaving the current cell:
```kotlin
private fun snapWithHysteresis(exactCells: Float, current: Int, stick: Float = 0.15f): Int {
    val target = exactCells.roundToInt()
    if (target == current) return current
    return if (abs(exactCells - current) > 0.5f + stick) target else current
}
```

**Resize clamping** — for the TopLeft / TopRight / BottomLeft handles the *position* clamp must be
expressed against the opposite edge (`startRect.col + startRect.cols - minCells`), or dragging a
top-left handle past the bottom-right inverts the rect into a negative size that renders as nothing.

### 4.3 Handles

- 14 dp drawn, **48 dp touch**, centres clamped to `[24dp, gridSize − 24dp]`.
- `handlesFor(room)` returns **one** handle (bottom-right) when `w == 1 && h == 1`, else four corners.
- Handles exist only on the selected room, so at most four handle targets exist at any moment.
- Handles are `clearAndSetSemantics {}` — they are a pointer-only affordance, fully covered for
  screen-reader users by the custom actions in §4.6.

### 4.4 Placement (take-off)

1. A room type is armed from the palette → a persistent `Placing: Kitchen ✕` bar (mode needs a
   redundant signifier and a one-tap exit).
2. On touch-down, a dashed ghost snaps to the cell under the finger; the chip reads
   `Kitchen · 2×2 · North-East`.
3. Sliding moves the ghost cell by cell, with a haptic tick on each change.
4. **Lift commits.** Overlap → ghost red, chip reads "Rooms can't overlap", commit refused.
5. The new room lands **already selected** — this is the discoverability mechanism; the user never
   has to independently learn "tap to select".

### 4.5 Feedback

- Dragged room lifts: elevation + ~1.02 scale.
- Snapped ghost outline at the landing position; tint the target cells.
- Live chip pinned in its own reserved row above the plan (reserved so nothing shifts).
- Haptics: **grab on finger-DOWN** (`LongPress`); tick per snapped-cell change (`CLOCK_TICK` on
  1.7.x); `REJECT` on refused drop; `CONFIRM` on commit. Rate-limit the tick to `steps` changes.

### 4.6 Accessibility — not optional

Because the gesture lives on a raw `pointerInput`, rooms have **no semantics at all** by default —
TalkBack sees an empty canvas.

- Each room node: `contentDescription = "Kitchen, 2 by 2, North-East"`, `role = Role.Button`,
  `stateDescription` selected/not, `onClick` to select.
- **`customActions`** (cap ~8–9, TalkBack renders them as a spoken menu): Move up / down / left /
  right, Make wider / narrower / taller / shorter, Delete.
- A polite `liveRegion` status node announcing outcomes ("Kitchen moved to North-East",
  "Cannot move: another room is there"). Do **not** put a live region on the room node and update it
  during a drag — it would fire every frame.
- `isTraversalGroup = true` on the grid with `traversalIndex` so rooms read **row-major by position**,
  not by insertion order.
- The move arrows and W/H steppers stay visible in the selected-room panel — they are the
  WCAG 2.5.7 conformance path, not a fallback.

### 4.7 Performance

- **Nothing in the drag path may recompose.** Render the live preview in a separate overlay that is
  the only reader of `DragState`, or pass `() -> DragState?` so the read happens in layout/draw.
- `Modifier.offset { IntOffset(...) }` — the **lambda** overload. The non-lambda one reads state in
  composition and recomposes per pointer event. Lint check: **`UseOfNonLambdaOffsetOverload`**;
  promote it to error for this module.
- `graphicsLayer { translationX/Y }` for the pure-translation ghost (skips composition *and* layout).
- `key(room.id)` in the render loop; `mutableIntStateOf` for primitives; draw grid lines in one
  `Canvas`, not 64 Boxes.
- Measure in a **release** build — debug Compose runs 2–5× slower.

### 4.8 Undo

Full-list snapshots (a dozen rooms is a few hundred bytes; a command/diff pattern is premature here).

- **One entry per gesture**, pushed when the drag passes slop — not per pointer event. Possible only
  because `DragState` is separate from the room list.
- **No-op guard:** a gesture that ends where it started must not leave an entry.
- Coalesce rapid stepper taps (~800 ms) into one entry, or undoing a resize takes six taps.
- Cap ~50; any new edit clears redo. Persistent Undo button in the editor chrome — **not** a
  Snackbar (auto-dismisses, TalkBack-hostile, fires far too often here).

---

## 5. Ship order

**Build A — the interaction** (ship and let the owner feel it) — ✅ **DONE, v0.2.3:**
1. ✅ Gesture arbiter + hit-testing + `DragState` (§4.1, §4.2)
2. ✅ Corner handles, 1-cell special case (§4.3)
3. ✅ Take-off placement with ghost + zone chip (§4.4)
4. ✅ Overlap blocking + snap-back (§4.5)
5. ✅ Move/resize feedback + haptics (§4.5)
7. ✅ **Pulled forward from Build B:** move arrows + steppers in the selected-room panel (§4.6).
   The moment move-by-drag exists, WCAG 2.2 SC 2.5.7 requires a non-drag path for it — shipping the
   drag without the arrows would have shipped a conformance failure, and the arrows are ~20 lines.

**Build B — completeness:**
6. Undo (§4.8)
8. Full semantics: customActions, live region, traversal order (§4.6). *Basic* per-room semantics
   (name, size, zone, selected state, a click action) ship in Build A; the custom-action menu does not.
9. Perf pass + `UseOfNonLambdaOffsetOverload` as an error (§4.7). Build A already uses the lambda
   `offset` overload and only re-publishes drag state on a **snapped cell change**, so a drag
   recomposes a handful of times rather than per pointer event — but this has not been measured.
10. Roborazzi screenshot coverage of the editor at the standard config matrix (UI-POLISH §6.4).
    ⚠ **This is the gap that makes Build A's hand-over honest-but-unverified**: there is still no
    machine in this project that has ever *rendered* this screen. See "What was and was not verified"
    in `docs/PHASE-2-PROGRESS.md`.

### What Build A deviates from, deliberately

| Spec | Built | Why |
|---|---|---|
| §4.4 labels the plan `NORTH / EAST / SOUTH / WEST` around the grid | NORTH above; `WEST · SOUTH · EAST` in one row below | Side labels cost the grid ~40 dp of width, and a cell is already only 34 dp at 320 dp. Rotated side text needs a custom layout that cannot be verified without a renderer. Positions still read correctly: west is at the left, east at the right. |
| §4.5 "snap-back" on a refused drag | The room stays at its last **valid** position; the refused position shows as a red ghost | This is what the prototype does, and the prototype wins on behaviour. Snapping all the way back would discard a legal partial move the user made. |
| Chip is one line | `maxLines = 1`, ellipsised | A two-line chip changes the reserved row's height and shifts the whole plan. The same information is repeated in full in the selected-room panel below. |

---

## 6. Acceptance criteria

- [ ] A room can be moved by touching it and sliding; it snaps cell-by-cell and never drifts behind
      the finger, including after clamping against a wall and dragging back.
- [ ] A corner handle resizes; dragging a top-left handle past the bottom-right does **not** invert
      the rect.
- [ ] A 1×1 room can still be moved *and* resized.
- [ ] Placement commits on lift; sliding before lifting adjusts the target; the chip names the zone.
- [ ] Overlapping placement or drag is refused, never silently accepted.
- [ ] A vertical drag inside the grid moves the room; a vertical drag outside it scrolls the page.
- [ ] Every drag action is reachable from a button (arrows + steppers).
- [ ] TalkBack: each room announces name/size/zone and exposes move + resize custom actions.
- [ ] Undo reverses exactly one gesture; a no-op gesture leaves no entry.
- [ ] All existing CI gates stay green, including `check-design-fidelity.mjs` and
      `check-screen-roots.sh`.
- [ ] Engine untouched: `sample-01` still scores exactly **31**.

## 7. Out of scope

Front-door placement mode (keep working as-is), the L-shape outline capture (`PHASE-3-PLAN.md` A1),
pinch-zoom of the canvas, free (unsnapped) positioning, rotation, non-rectangular rooms.

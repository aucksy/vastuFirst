# Full end-to-end audit — 4 August 2026 (v0.7.0, post-§3q)

**Method.** Sixteen parallel read-only passes: eleven opened all 649 committed render goldens
(59 screens × 11 configs) by eye — every image opened or proven byte-identical (MD5) to an opened
baseline; four walked every user flow in the code; one inventoried every user-facing string
(`scratchpad copy-inventory.md`, to be committed alongside the copy phase). The worst claims were
re-verified independently by a second look at the image or the code before ranking. Zero paid scans
were used; no code changed.

**Ranking language:** BLOCKER = a user cannot proceed or is told something false ·
HURTS = looks broken / loses info / silently changes data · MINOR = polish or latent.

---

## A. Fix before anything else — truth and privacy (proposed Phase A)

| # | Finding | Where | Class |
|---|---|---|---|
| A1 | **Consent names the wrong company.** The consent card says "A plan-reading service called Groq, on computers in the United States" — since §3q the image goes to OpenRouter → `openai/gpt-5.6-luna`, escalation `google/gemini-3.1-pro-preview` (up to TWO providers). "What we ask it — only to read the room names" is also stale (it now reads printed sizes too). | `ScanConsentScreen.kt:62` | BLOCKER (privacy) |
| A2 | **The consent version key was not bumped.** `PlanReadingConsent.kt:38-42` promises "if what we send, or who we send it to, ever changes… every user is asked again". Key is still `plan_reading_consent_v1`; Groq-consenters carry over silently. Must become `_v2` with A1. | `PlanReadingConsent.kt:42` | BLOCKER (privacy) |
| A3 | **"Delete all my data" leaves drafts behind.** `PlanRepository.deleteAll()` clears `plan` only; `Draft.sq` has no delete-all and is never touched. Unfinished homes (room layouts) survive the privacy control that promises "permanently removes every saved home". | `PlanRepository.kt:157`, `Draft.sq` | BLOCKER (privacy) |
| A4 | **Opening a home silently rewrites its score.** After a rules update, `load()` writes `score`/`ruleSetVersion` back to the DB on open, bypassing the acknowledge-gated `setRescored` path — the score-change card loses that home unacknowledged, and `updatedAt` is bumped so it jumps the list order. Contradicts HomeViewModel's documented contract. | `NewPlanViewModel.kt:519-524` | HURTS (truth) |
| A5 | **Stale key fallback can bake a dead Groq key into an APK.** `app/build.gradle.kts:87-90` still falls back to `GROQ_API_KEY`; `check-plan-reader-key.py` reads only `OPENROUTER_API_KEY` and green-skips, so the "fail loud" claim in the gradle comment is inverted. CI is safe today; any other build env is the trap. | `app/build.gradle.kts:87-90`, `scripts/check-plan-reader-key.py:24-26` | MINOR now, ugly later |

Mitigation that bounds A1/A2 today: CI builds are keyless until the OpenRouter secret is set, so no
image actually leaves any phone from current builds. A1+A2 must land **before** the first keyed build.

## B. Flow bugs (proposed Phase B, part 1 — behaviour)

| # | Finding | Where | Class |
|---|---|---|---|
| B1 | **Process death mid-flow → eternal spinner + silent save blackout.** No SavedStateHandle: nav survives, VM doesn't, `intent == null`. Score screen's `a == null && rooms.isNotEmpty()` branch is a `LoadingState` with no exit. Worse: `save()` mints `planId` BEFORE the `buildPlan() ?: return` null-check, so the draft autosave loop (`if (planId == null) persistDraft()`) stops writing forever after one tap — and the null-intent draft on disk reproduces the spinner on resume. | `ScoreScreen.kt:135-140`, `NewPlanViewModel.kt:342-344`, `PlanConversion.kt:47` | BLOCKER |
| B2 | **On-photo flow never asks for the front door.** SCAN_REVIEW → MARK_NORTH directly; the classic path's grid offers "Set the front door" as primary. `buildEnginePlan` silently scores `doors = emptyList()`. The doc's "score identical in both flows" holds only because both *can* omit the door — the ask exists in one flow only, and the door is the highest-weighted element. | `VastuNav.kt:206-216`, `ScanReviewScreen.kt:207`, `PlanConversion.kt:81-84` | HURTS |
| B3 | **Unplaced scan can be scored as-is.** With rooms parked in the tray, "Next — mark North" is gated only on `rooms.isNotEmpty()` — the engine scores the parking rows (doorless strip) as the home. | `GuidedGridScreen.kt:1025-1028` | HURTS |
| B4 | **Retype un-parks parked rooms.** Correcting one room's *kind* routes through `updateRooms()`, which unconditionally sets `roomsUnplaced = false` — the screen flips to "Place your rooms" + shape questions over the parking row, the exact state the flag was built to prevent. | `GuidedGridScreen.kt:953`, `NewPlanViewModel.kt:224` | HURTS |
| B5 | **"Change North" from a saved home has no cancel.** Every dial move autosaves ~50 ms later; Back reverts nothing. Experimenting = permanent. | `VastuNav.kt:245-249`, `NewPlanViewModel.kt:196-209` | HURTS |
| B6 | **All-unreadable homes → first-run onboarding.** Launch gate ignores `SavedPlans.unreadable`; the reassuring "still saved" banner is unreachable — user believes data deleted. | `VastuNav.kt:66-73` | HURTS |
| B7 | ✅ CLOSED 5 Aug (v0.7.7): each unreadable home is named on the card with its own Remove behind an are-you-sure that states the rescue-chance price. ~~**Unreadable banner is a permanent dead end.**~~ No per-home delete exists anywhere; the only escape is Settings → delete ALL data. "An update should bring it back" is unconditional and, for a truly corrupt row, false. | `HomeScreen.kt:139-147` | HURTS |
| B8 | **Placed-scan screen names the wrong destination.** With "On the photo" set, the result screen still says "Check them on the grid" and the button opens the photo review. | `ScanScreen.kt:187-191` | HURTS (stale words) |
| B9 | **Settings toggle is a silent no-op for non-Placed scans** (by design, but unlabelled — violates the project's own no-silent-no-op rule). One caption line fixes it. | `SettingsScreen.kt:151-159` | HURTS |
| B10 | Latent 0.0-score overwrite: rescore guard can never return null (`analyze` is total → `insufficient` = score 0, quality INSUFFICIENT not filtered). A future sanitizer change would show "7.1 → 0.0" and write it. | `HomeViewModel.kt:56`, `ScoreChange.kt:63-66` | HURTS (latent) |
| B11 | Fix-loop back stack grows unbounded (Score → change rooms → new Score pushed each cycle). | `VastuNav.kt:261-267` | MINOR |
| B12 | Draft edits in the last ~50 ms before leaving are lost (debounce cancelled with scope); flush from `onCleared()`. | `NewPlanViewModel.kt:184-191` | MINOR |
| B13 | "Start this home again" keeps previous North + intent. | `NewPlanViewModel.kt:470-488` | MINOR |
| B14 | Photo-review process-death shell: "0 rooms / photo could not be shown" but continue still offered; handover singleton never cleared (photo bytes live forever). | `ScanReviewScreen.kt:73-96` | MINOR |
| B15 | Double-tap on back chevrons pops two screens (forward nav debounced; back isn't). | `VastuNav.kt:297`, `Interaction.kt:31-66` | MINOR |
| B16 | Crash-report email: `ActivityNotFoundException` swallowed, then the crash file is deleted unconditionally — user thinks it sent; it's gone. | `SettingsScreen.kt:79-83, 101-114` | MINOR |
| B17 | >20-room fully-sized plan refused with "doesn't print their sizes" (false on that sheet) — ceiling reuses the wrong reason's copy. | `ScanMapper.kt:361-363`, `ScanScreen.kt:204-208` | MINOR |
| B18 | Assisted-scan overflow: rooms 26+ silently dropped (25 parking tiles), not even in the "we also saw" list. | `ScanState.kt:92-98` | MINOR |
| B19 | Reading spinner promises "a few seconds"; escalation worst case ≈ 4.7 min (2 × 20 s connect + 120 s read), no "second look" hint. Bounded, never hangs. | `ScanScreen.kt:169`, `reader-config.json` | MINOR |
| B20 | Review "Check" tag fires on ANY flag; scan screen's CHECK pill fires on two flags only — the two surfaces disagree. | `ScanReviewScreen.kt:184` vs `ScanScreen.kt:345` | MINOR |
| B21 | On-photo review rows use raw `printedSize` (multi-line captions); `shortSize()` exists and is tested but unused there. | `ScanReviewScreen.kt:189` | MINOR |
| B22 | MoreDetails "Back" and "Save and see my score" are identical (answers commit on tap); two-button layout implies discard exists. | `MoreDetailsScreen.kt:91-93` | MINOR |
| B23 | MarkNorth TalkBack announces "Score 0.0 out of 10" while computing (`score ?: 0`). | `MarkNorthScreen.kt:137` | MINOR |
| B24 | From-score North entry still says "Step 2 of 3" / "Yes — read my home". | `MarkNorthScreen.kt:112,210` | MINOR |
| B25 | Rename allows duplicate names; list becomes ambiguous. | `PlanRepository.kt:144-147` | MINOR |
| B26 | "Updated today" never re-evaluates across midnight (composition-time `now`). | `HomeScreen.kt:94` | MINOR |

**Dormant (billing OFF today, fix before the flag flips):**
- PENDING purchases render as silence and auto-refund in 3 days if the app stays closed (no startup acknowledge pass) — `PlayBilling.kt:174-186`, `AppModule.kt:66-73`.
- `billing.state` read once at composition (no Flow): paywall's first frame is likely the false "can't reach Google Play" error — `UnlockScreen.kt:49`.
- Rotation during the Play dialog cancels the callback scope (recoverable via AlreadyOwned) — `UnlockScreen.kt:56-67`.
- Restore button not `busy`-guarded (double-fire) — `UnlockScreen.kt:69-80`.
- Product model vs copy: one non-consumable unlock vs "one-time · this home, forever" — a second home unlocks free (ITEM_ALREADY_OWNED → onUnlocked). Owner decision needed before payments go live.

**Verified clean (so nobody re-litigates):** consent gate unskippable; keyless builds refuse before
network; on-photo applies only to Placed; grid populated before review; North after review both ways;
MAIDS RM + `(?!/)` fraction fix correct incl. caption-end; every refusal state has a way forward;
door coercion consistent everywhere; draft lifecycle + migration tested against real SQLite; v0.5→v0.7
persisted-shape diff is empty; score-change card can't ship without a changeNote; forward nav debounced;
billing honesty strings literally true in every reachable state; price from Play with labelled fallback;
edge-to-edge + dark status icons forced; every screen root uses `screenRoot()`.

## C. What the pictures show (proposed Phase B, part 2 — rendering)

Every claim below was seen by eye in a named golden; the geometry gate cannot see this class
(boxes measure fine; the ink is wrong).

| # | Finding | Screens / configs | Class |
|---|---|---|---|
| C1 | **Room tiles silently lose their names.** The label ladder (shorten → drop) ends in DROP: blank coloured squares. Worst: editor-scanned-unplaced font2_0 ≈ 9 of 19 tray tiles blank — the screen that says "Drag each one to where it really is". Also editor-scanned (Toilet/Bathroom/Corridor blank at font1_3 — a very common setting), editor-scanned-owner (dupes make it worse), editor/editor-retype/editor-selected (Kitchen blank at font2_0; "Bedroom"→"Bed" reads as furniture). | editor family × font1_3/font2_0/w320 | BLOCKER at font2_0 unplaced; HURTS elsewhere |
| C2 | **The price caption shatters next to ₹699.00.** "one-time · this home, forever" renders as a one-character-per-line column ("one-" / "time" / "· this" / "hom" / "e," / "fore" / "ver"). The money row looks broken at the exact moment of purchase. | unlock-paid × w320, font2_0 | HURTS (money) |
| C3 | **Unlock CTA sliced/below fold at font2_0**; on unlock-unreachable the "nothing has been charged" honesty copy is below the fold entirely. | unlock family × font2_0 | HURTS (money) |
| C4 | **Legal screen shatters at font2_0** — tag explanations render one-to-three letters per line, content pushed off-screen; w320 milder. | legal × font2_0/w320 | BLOCKER at font2_0 |
| C5 | **Report header collision at font2_0** — "FULL REPORT" runs into the "ALREADY LIVING HERE" pill (letter touches pill); pill wraps ragged. First glance of the paid document. | report-living × font2_0 | HURTS |
| C6 | **"16 zones · soon" toggle wraps with orphaned dot** — lopsided two-line segment on a paid document. | all report screens × font2_0 | HURTS |
| C7 | **Door badge "D" glyph outgrows its gold circle** (sheared/clipped, reads as a broken shape) — glyph scales with font, circle doesn't. Same class: zone-map compass "N" pokes through its ring at font2_0 (reads as "no entry"). | editor-selected/-shape-ask/-shape-cut/-retype × font2_0; score/score-covered × font2_0 | HURTS |
| C8 | **MarkNorth mini-map labels lose the verdict line at font2_0** (names truncate to 2 letters, tick/cross gone — colour becomes the only signal, which §2 rule 3 forbids); "Poo…"/"Toi…" truncations at w320/font1_3. | marknorth × font sizes | HURTS |
| C9 | **Welcome buries its main action.** All three "What brings you here?" cards below the fold at font2_0/w320/font1_3/w360; first screenful ends mid-sentence in the language note. Six language chips for an English-only app occupy the prime slot. | welcome | HURTS |
| C10 | **Unfinished-home rows ellipsize away name + count + date at font2_0** ("Unfinished …", "5 rooms so f…"). | home-unfinished × font2_0/w320 | HURTS |
| C11 | **home-unreadable notice is bare gold text** — no card, no icon, no action; weakest contrast on the screen; sibling notice (score change) gets a proper card. | home-unreadable × all | HURTS |
| C12 | **Score-change banner fills the whole first screen at font2_0** (both home cards pushed off); "6.8 →" wraps orphaning "7.1". | home-scorechange × font2_0 | HURTS |
| C13 | **Calibrating compass still offers "Set North from this" enabled** while the app says the compass needs settling — the whole score rotates with a reading the app itself distrusts. | marknorth-compass-calibrating × all | HURTS (accuracy) |
| C14 | **No-camera state renders a normal-looking camera button** directly above "This phone doesn't have a camera app we can open." | scan-idle-no-camera × all | HURTS |
| C15 | Settings fold at font2_0: fold lands on "Privacy"; Honesty & sources + Delete all my data below; w320 clips "Delete all my" mid-label. | settings × font2_0/w320 | HURTS (mild) |
| C16 | Discard X sits ~16 dp from "Carry on" with no visual separation — destructive tap next to the resume tap; X has no visible label. | home-unfinished × all | HURTS (mild) |
| C17 | Sibling dialogs disagree on primary-button side (discard: filled LEFT; rename: filled RIGHT). | home dialogs | COSMETIC |
| C18 | editor-door shows two identical filled-green primaries stacked ("Done placing door" / "Next — mark North"). | editor-door × all | HURTS (mild) |
| C19 | "Next — mark North" on editor-wide reads as disabled (grey-on-grey) with no hint what unlocks it. | editor-wide × all | COSMETIC (HURTS if enabled) |
| C20 | Shape-ask question's Yes/No below the fold at w320/font2_0 — a question with no visible answer. Scrolls, but nothing hints it. | editor-shape-ask | COSMETIC |
| C21 | Tile labels kiss borders (0 padding) one step before vanishing. | editor family × narrow/large | COSMETIC |
| C22 | Add-room chip row cuts raw at screen edge (bare pill-arc sliver / "Bedroo" mid-letter) — no end fade/padding. | editor-wide, editor-shape-cut | COSMETIC |
| C23 | Selected-room panel order: Remove/Done sit ABOVE Move/Size; SIZE row half-cut at baseline. | editor-selected × all | COSMETIC |
| C24 | scan-placed-sizes shows raw duplicated caption text (`(2.10mX1.45m) +(1.19mX1.02m) (6'-11"X4'-9") …`) — reads as debug output; unexplained mono-caps "CHECK" badge (also on scan-assisted). | scan-placed-sizes, scan-assisted | COSMETIC (trust) |
| C25 | On-photo review vs classic flow speak different visual dialects (serif ALL-CAPS cards + centred title vs mono STEP header + flat rows) — the §3q "one family" goal not yet met. | scan-review vs scan-placed/retype | COSMETIC |
| C26 | Review checklist repeats "no size printed" on every row; "POOJA / Pooja" duplication. | scan-review × all | COSMETIC |
| C27 | score/score-covered button labels orphan-wrap ("Change which way North / is"). | score × font2_0/w320 | COSMETIC |
| C28 | Loading states (score-loading "Reading your home…", scan-reading) show no spinner in any golden — look hung if slow. | score-loading, scan-reading | COSMETIC (HURTS with B1) |
| C29 | home-empty: text top-left of a huge empty card; dead first screen. | home-empty | COSMETIC |
| C30 | RTL bidi artifacts on English strings everywhere ("10/", ".score", "zones 8") — cosmetic today, fix via LTR isolates when any RTL locale ships. Grid/compass correctly do NOT mirror. | all × rtl | COSMETIC |
| C31 | unlock price format drift: "₹699" (fallback) vs "₹699.00" (Play) across sibling states. | unlock family | COSMETIC |
| C32 | editor-scanned-owner: large green dashed region with no visible caption in any viewport. | editor-scanned-owner | COSMETIC |
| C33 | Refusal-screen primary button text nearly edge-to-edge at font2_0. | scan-refused-* × font2_0 | COSMETIC |

## D. The render harness itself (fix with Phase B re-record)

| # | Finding | Detail |
|---|---|---|
| D1 | **The landscape config has never rendered landscape.** Every `__landscape.png` is 960×1708 px = **480 dp wide × 854 dp tall portrait**. Requested `+w854dp-h480dp-xhdpi` lacks the orientation token; Robolectric normalises. Add `-land` (canonical position: after height, before ui-mode) and re-record; expect NEW findings (short-viewport CTA reachability has never been tested). |
| D2 | **Goldens are top-viewport only.** The buy button, price row, report's lower sections (not-ideal list, disputes payoff, second tradition reading), and score's "biggest problems" list appear in NO golden at any config. §6.7b's "producing them is not optional and neither is looking" currently cannot apply to the bottom half of every long screen. Add tall-canvas or scrolled captures for report/score/unlock. |
| D3 | **dark is a deliberate no-op** (RenderMatrix comment: proves the light palette does not invert) — byte-identical files are the PASS state. Fine, but document it in UI-POLISH §6.4 so the next auditor doesn't re-flag it. |
| D4 | **pseudo_en / hi / ta are inert for text** — all strings are Kotlin literals, not resources; pseudolocale/locale can only affect resource strings. rtl still tests mirroring (real value). Keep, but mark them "armed when strings move to resources (Phase 4)" in UI-POLISH §6.4. |
| D5 | The A5/B1-class lesson stands: the gate measures boxes, not glyphs — C1/C2/C7 all lived behind green gates. The eye pass stays mandatory. |

## E. Copy (proposed Phase C — the headline job)

Full inventory with per-string flags: `docs/COPY-INVENTORY-2026-08-04.md` (312 measured + ~20
grouped ≈ 330 Kotlin strings, ≈3,260 words a user can be shown). The full top-25 rewrite table
with before/after word counts and honesty flags is in the copy agent's report (session transcript)
and summarised here:

1. **⭐ The elephant is the rules JSON, not the Kotlin.** `rules/.../ruleset/` carries ≈5,290 words
   of report prose shown verbatim (defects.json ~3,239w, rooms.json ~804w, remedies.json ~677w,
   disputes.json ~536w). Reading level far above target ("Ishanya — Shiva's quarter… the Purusha's
   head"). A real cut to what a paying reader reads edits these data files; Phase C must include
   them or it misses most of the words.
2. **Measured cut available**: top-25 Kotlin rewrites ≈310 words; extending to every >12-word prose
   string ≈700–800 (~25% of visual copy). 20 of the 25 touch honesty strings — each shortened with
   every claim kept, individually flagged for the phase review.
3. **The report's front-door card is the biggest single wall**: ~120 words / three paragraphs, the
   door-weighting rationale stated twice, meta-caveat before the verdict, unexplained Sanskrit
   fragments ("Purva, the quarter of Indra…").
4. **Noun drift**: "plans" vs "homes". **Verb drift**: "Touch and slide" / "press the plan" /
   "Tap the wall" / "Drag". Pick one set.
5. **House terms reaching customers**: "mandala" (legal), "floor plate", "Shallower plot",
   "C" for centre (marknorth map), "16-zone school", "CHECK" badge, "missing-corner checks".
   Notably CLEAN: no "pada/Brahmasthan/footprint/provenance" ever reaches a screen from Kotlin.
6. **Repetition**: "no size printed" × every row; "Draw it on a grid instead" up to 3× on one
   screen; "Ranked by how much each matters" + "most important first" adjacent on every report.
7. **Programmer plural shown to users**: "Skipped $n room(s)…" (`PlanSanitizer.kt:56`).
8. **DO-NOT-REWORD**: the compass instruction (`MarkNorthScreen.kt:251-252`) — the heading maths
   is written against that exact sentence; shortening it risks wrong directions on every room.
9. **Deliberately untouched**: all billing copy (test-pinned, literally true), consent fact pairs
   (rewritten in Phase A for provider truth, not brevity), privacy-policy constants, both
   disclaimers, the magnetic-compass caveat.
10. Fold wins expected from cuts: welcome (cards above fold), report top (toggle + door card),
    score-change banner, settings (with the Language-row decision), unlock (CTA above fold at 2.0).

## F. Delight / UX proposals (proposed Phase D — each within tokens, or allow-listed with reason)

1. **Score reveal moment**: count-up on the number + band colour sweep on the meter (motion tokens
   only); one-line reassurance under a good score ("Solid — most homes score below this").
2. **home-empty**: centre the empty state, add the grid+compass motif already used on welcome.
3. **report-disputes end-cap**: "That's every open question" footer so the page ends on purpose.
4. **Unify the two scan-check styles into one family** (C25): one room-row component (name case,
   subtitle, card treatment) used by both; keeps §3q's "one family" promise.
5. **Loading states**: indeterminate progress + rotating one-liners ("Checking your kitchen's
   corner…") on scan-reading/score-loading; timeout exits (ties to B1/B19).
6. **Calibrating state**: disable "Set North from this" until settled (C13) with a settling meter.
7. **Post-score nudge**: after first score, a single toast-level line pointing at "Change the rooms
   or the front door" — teaches the fix loop without a tour.
8. Micro-press states on room tiles (scale 0.98 + elevation token) — the design system owns pressed
   states; tiles currently rely on selection only.

## G. Decisions only the owner can make

1. Settings "Language — English" fixed row: drop it (wins back fold space; welcome already shows
   the language story) or keep it? (§3q left this open; fold budget 3 today.)
2. Unlock wording vs product model before payments flip: "this home, forever" implies per-home
   pricing; the single non-consumable makes the second home free. Which is intended?
3. Dark mode: stay proudly light-only (document it) or build a dark palette later? (Config proves
   non-inversion today; night users get a bright screen.)
4. ✅ DONE 5 Aug (v0.7.7) — owner said "continue the planned improvements"; shipped remove-on-unreadable-rows only (finished, healthy homes still have no delete — a smaller decision, still his). Was: Per-home delete (B7): add "remove this home" to rows — recommended, but it's a data-model
   surface he may want to see first.
5. The unreadable-home promise "an update should bring it back": soften to "we'll keep trying" +
   allow removing it?

## H. Proposed phases (each ends: green CI, re-recorded goldens for changed screens looked at by eye, pushed to main)

- **Phase A — truth & privacy** (A1-A5, B8, B9): consent rewrite + `_v2` bump, delete-all closes
  drafts, silent re-score removed, key-fallback removed, stale destination words. Small diff, no
  layout changes, no goldens move except settings caption + scan copy.
- **Phase B — breakage** (B1-B7 + C1-C14 + D1-D2): the process-death trap, door ask in on-photo
  flow, unplaced gating, label ladder never drops to blank, badge glyph caps, money-row layout,
  fold fixes, harness landscape + tall captures; ratchet re-baselined by hand in the same commit.
- **Phase C — the copy cut** (E themes + full pass over the inventory): honesty strings flagged
  individually in the phase report; fold budgets measured before/after via re-recorded goldens.
- **Phase D — delight & family** (F list): tokens-only; deviations allow-listed with reasons.
- **Parked until payments**: the dormant billing list (fix before `PAYMENTS_ENABLED=true`).

*Written by the 4 Aug 2026 audit session. Sources: sixteen agent reports (session transcript),*
*verified spot-checks, and the committed goldens under `app/src/androidUnitTest/roborazzi/`.*

---

**5 Aug 2026, v0.7.7 — B7/G4/G5 shipped, plus the v0.7.4 date-tail blemish.** Each unreadable home
is now named on its card ("Home 3 · 3 Jul" — the identity columns read even when the plan JSON
doesn't) with its own Remove behind an are-you-sure that states the real price: removing ends the
chance a future update rescues it. Healthy homes still have no delete — smaller, separate decision,
still the owner's. The relative dates lost their "Updated" prefix (every caller already writes
context before a "·"), which is exactly the ~8 characters that were pushing "Updated 3 d…" into the
ellipsis at 200 % font. Eye pass: home list at 200 % shows both dates whole; the card's name row and
✕ Remove hold on one line; the remove dialog wraps clean at 200 %. Ratchet: home-unreadable
auto-tightened back to 2 (the card grew without pushing anything off the fold), home-remove entered
at 0; a11y adopted home-unreadable at 1 = the base home screen's pre-existing 1, so the card itself
added zero findings.

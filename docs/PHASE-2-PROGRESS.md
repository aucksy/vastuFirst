# VastuFirst — Phase 2 progress (the guided-grid Android app)

**Milestone:** 4 August 2026 client delivery. **Status: the full guided-grid path is built and
green on CI.** Entry point for a fresh session is `Documents/VastuFirst-Android-Implementation-PRD.md`
(the build plan) + this file (what's done).

## What ships in Phase 2

A real person can, fully offline, on their own phone: pick their intent → place their rooms and
front door on a grid (or load a sample) → mark North on the signature dial with a live score →
see a free score with the top-3 defects → unlock the full, intent-branched report with provenance
tags and disputes → save it and reopen it later.

### Screens (all from the Sage & Gold design system, token-only)
| # | Screen | Notes |
|---|--------|-------|
| 1 | Welcome | Language (English live; 5 scripts "soon" — l10n is Phase 4), intent picker, Continue gated on intent |
| 2 | Add home | Guided grid + samples wired; Upload = "soon" (AI reading is Phase 4) |
| 3 | Guided grid editor | Tap-to-place rooms, resize (steppers), remove; a second mode places the **front door** on an outer wall |
| 5 | Mark North | The `NorthDial` (drag/tap) + slider + N/E/S/W chips + degree stepper; **live score debounced ≤50 ms, off the main thread**; clean centre; **no best-angle affordance** (§0.7) |
| 6 | Score (free) | Big band-coloured number, zone map, top-3 defects, honest count of the rest, "score is our own construction" note |
| 8 | Unlock | Paywall UX at ₹699; **unlocks locally** (payments are Phase 5) and says so honestly |
| 7 | Full report | **Branches on intent**: BUILDING/BUYING lead with layout changes, LIVING leads with remedies; provenance tag on every rule; "already right", "where schools disagree", "not assessed", disclaimer |
| 11 | Saved plans (Home) | Reopen re-runs the engine from the stored plan; two plans side-by-side = the BUYING comparison |
| 12 | Settings | Preferences shown (language/school fixed this phase); honest data controls incl. delete-all |
| 13 | Legal / Honesty & sources | Visible disclaimer + the provenance vocabulary |

## Architecture (how it's wired)
- **Engine untouched** (consumed, not changed). The only additive change to a pure module was
  `@Serializable` on the `Plan` input DTOs so a plan can be persisted — no engine logic touched.
- **`NewPlanViewModel`** (nav-graph-scoped) is the draft home shared across the flow. It converts
  the guided grid → the engine `Plan` (grid rows flipped to engine north-up space) and runs the
  engine off the main thread with a ≤50 ms debounce. The engine is TOTAL, so there is never an
  error state.
- **Persistence:** SQLDelight in `:data` (Android-free — the Android `SqlDriver` is the only
  platform seam, in `:app`). Each row stores the plan JSON + `ruleSetVersion` (so a later ruleset
  change is detectable, never a silent re-score).
- **Components:** an owned, token-only Compose kit in `:designsystem` (no Material theming, so the
  iOS re-skin stays mechanical). DS-local `VastuVerdict`/`VastuProvenance` keep it free of `:shared`.
- **Fonts:** Marcellus / DM Sans / DM Mono bundled as OFL Compose resources (Indic Noto = Phase 4).
- CI guards (module boundaries + token discipline) pass on every push.

## Deliberate deviations from the mock (resolved, not accidental)
- **Front-door step added** to the grid editor — the mock omits it, but the door is the highest-
  weighted element the engine scores.
- **Resize by steppers**, not drag — more reliable one-handed; same outcome.
- **Local unlock** instead of real payment (Razorpay is Phase 5); the paywall UX is intact.
- **Compass-sensor helper omitted** on Mark North (it is explicitly secondary/optional, §6.3);
  dial + slider + degree + chips cover the need. Candidate for a later pass.
- **BUYING comparison** = the saved-plans side-by-side view (no separate compare screen).

## Owner decisions still open (needed before 4 Aug ship, built on placeholders)
1. **Report price** — `₹699` placeholder throughout.
2. **The 8 expert rulings** (§13) — engine runs on the current safe defaults until rulings land.

## Known minors to revisit in review
- Language picker is display-only for the 5 non-English scripts (l10n is Phase 4).
- 16-zone school toggle on the report is display-only (Phase 4).
- Review-gate self-audit done; a physical-device pass at 360 dp + TalkBack is still owner testing.

## Next
Client testing (Phase 3) + the owner decisions above. iOS/payments/AI/languages are Phases 4–5.

**Phase 3 audit round 1 done (v0.2.1)** — see `docs/PHASE-3-AUDIT.md`. Fixed the no-scary-error
gap (INSUFFICIENT no longer shows red 0/100), the save-timing score bug, blank reopen zone map,
ruleSetVersion re-score, intent-blind verdict, unlock "Pay" wording, and the a11y/review-gate items
(48dp targets, icon-button labels, slider semantics, button clip-safety, delete-all confirm).
Deferred + surfaced to owner: L-shaped/notched homes are scored as a filled rectangle (needs an
outline-capture step) — candidate #1 for the refinement plan.

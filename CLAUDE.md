# CLAUDE.md

Claude Code reads this at the start of every session and must follow it.

---

## 1. Project facts

- **Project name:** VastuFirst
- **What we're building:** Android app (iOS + marketing website later) that scores a home's
  layout against Vastu rules and sells a paid report. Offline-first; the score is computed
  on-device.
- **Stack (this project — NOT the generic template):**
  - **Kotlin Multiplatform + Compose Multiplatform** (Android target now; iOS is a later re-target).
  - Pure logic modules are **`kotlin("jvm")`** today; flipping them to `multiplatform` is the
    iOS move (a build-file change, not a rewrite — Product PRD §3.1).
  - DI: **Koin** · DB: **SQLDelight** · Serialization: **kotlinx-serialization** ·
    Async: Coroutines+Flow. Backend: Supabase (Phase 4+). AI: Groq (Phase 4). Payments:
    Razorpay (Phase 5, `:app` only). Versions are pinned in `gradle/libs.versions.toml`.
  - Min/Target SDK **26 / 35**.
- **Module graph:** `engine`, `rules`, `shared`, `data` = pure, **zero Android**;
  `designsystem` = Compose UI kit; `app` = Android entry point + all Android-only deps.
- **Where things live:** `app/` = screens & platform glue · `engine/` = the scoring engine ·
  `rules/` = versioned rule JSON + loader · `shared/` = enums/DTOs · `data/` = persistence ·
  `designsystem/` = `VastuTheme` + components.
- **Source-of-truth docs:** `Documents/VastuFirst-PRD.md` (the WHAT — engine spec),
  `Documents/VastuFirst-Android-Implementation-PRD.md` (the build plan / definition of done),
  the design handoff zip under `Design System/Final Designs/`. Where the Implementation PRD
  and Product PRD disagree, the **Implementation PRD wins** (it's the sequencing document).

## 2. ⭐ Builds are CLOUD-ONLY (hard rule)

This machine cannot build native apps locally (the toolchain was removed; builds crash it).
**Never re-download the Android/Gradle toolchain or try to build/test locally.** Every
compile + test runs on **GitHub Actions** (`.github/workflows/ci.yml`) in `aucksy/vastufirst`.
The loop is: author code → push → watch CI go green → fix from the CI logs. Slower per step,
but it's the only path. "Prove it works" = green CI, not a local claim.

## 2b. ⭐ UI polish is a HARD RULE — `docs/UI-POLISH.md`

**Every build must follow `docs/UI-POLISH.md`. It is binding, not advisory.** It exists because
v0.2.1 shipped with the status bar overlapping every screen, a guided grid that measured to zero
height (so no room could be placed at all), and the default Android robot as the launcher icon —
all while CI was green. **CI compiled the UI without ever rendering it.**

The three non-negotiables:

- **The build must be able to see.** Every screen is rendered headlessly in CI at the standard
  configuration matrix (412 dp, **360 dp**, **200 % font scale**, with insets, Hindi/Tamil) and the
  images are published as an artifact. A screen that has never been rendered is not done.
- **Design conformance is mechanical, not an opinion.** `scripts/check-design-fidelity.mjs` compares
  the theme against the design contract (`handoff/tokens.json` + the `ramp`/`auditRows` data in the
  Sage & Gold design file) and fails the build on drift. Deliberate deviations go in the script's
  allow-list with a reason — never silently.
- **Never hand over an APK for a screen I have not looked at.** Before every release I download the
  rendered screenshots, compare them to the design side by side, walk the review gate, and say which
  screens I verified. "CI is green" is **not** "the screen is right" and must never be presented as
  if it were.

## 3. How to work

1. **Plan first** for anything bigger than a one-line change. Show a numbered plan; wait for "go."
2. Do one step, then stop and show the diff. Don't run a whole phase unattended.
3. **Prove it works** via CI — show the real result, never just claim it passes.
4. Push after each phase and tag a build (standing rule for this owner).

## 4. Golden rules

- **Ask, don't assume.** If a missing detail changes cost/design/result, stop and ask.
- **The owner is not a developer.** Explain in plain, everyday words; lead with what it means
  for them. Detail goes in docs/PROGRESS.md, not chat.
- **Tokens only.** No raw hex outside `VastuColors`; no raw `.dp`/`.sp` outside the theme
  package. `scripts/check-tokens.sh` enforces it in CI.
- **Engine purity.** `engine`/`rules`/`shared`/`data` never import `android.*`/`androidx.*`.
  `scripts/check-boundaries.sh` enforces it in CI. This keeps iOS cheap — don't break it.
- **Rules are data.** Every Vastu value lives in `rules/.../ruleset/*.json`, versioned. A
  disputed/open rule (Product PRD §13) is a config flag, never a Kotlin constant.
- **Engine is tested before it is shown.** No screen consumes a score the engine tests don't cover.
- **No secrets in the repo.** Use a git-ignored `.env`; ask the owner to fill it.
- **Free first.** Never sign up for anything paid without asking.

## 5. STOP and ask the owner for

Accounts/keys (GitHub, Supabase, Groq, Razorpay, Play), purchases/paid upgrades, store/legal
steps, real-device testing, destructive actions, and the owner-only decisions in
Implementation PRD §8 (report price, the eight expert rulings). Use the `ACTION NEEDED` block:

```
🙋 ACTION NEEDED (you)
What: <the one thing you need to do>
Why: <why it's blocking>
How: <exact steps>
Then: <what to tell me so I can continue>
```

## 6. Notes for this project

- Identity for git/author: `simpleapps108@gmail.com` (not the harness login).
- Package: `com.vastufirst.app`. Repo: **private** `aucksy/vastufirst`.
- Deadline: **Phase 2 is a client delivery on 4 August 2026.** The eight expert rulings
  (§13) are needed *before* Phase 2 ships, not Phase 4.
- Phase 1 exit gate: the §15 worked example (`sample-01`) must score **exactly 31**, and a
  rotation-invariance test must still score 31. The engine never scores which way a building
  faces (Product PRD §0.4).

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
  configuration matrix (412 dp, **360 dp**, **320 dp**, **200 % font scale**, dark, landscape, RTL,
  with insets) and the images are published as an artifact. A screen that has never been rendered is
  not done.
- **Design conformance is mechanical, not an opinion.** `scripts/check-design-fidelity.mjs` compares
  the theme against the design contract (`handoff/tokens.json` + the `ramp`/`auditRows` data in the
  Sage & Gold design file) and fails the build on drift. Deliberate deviations go in the script's
  allow-list with a reason — never silently.
- **Never hand over an APK for a screen I have not looked at.** Before every release I download the
  rendered screenshots, compare them to the design side by side, walk the review gate, and say which
  screens I verified. "CI is green" is **not** "the screen is right" and must never be presented as
  if it were.

## 2c. ⭐ HARD RULE — image scans need an approved COUNT first (owner rule, 3 Aug 2026)

Before ANY test, experiment or check that sends a plan image to a paid AI reader (Groq, Gemini,
any provider, any script): tell the owner the EXACT number of image scans about to run, per
provider, with the cost estimate in rupees, and WAIT for his explicit yes. No scan runs before
the yes.

- Existing recordings cost nothing and never need approval — reuse them first.
- An approval covers the stated count only. Anything beyond it — retries, an extra plan, a
  second model, "just one more check" — is a new count and a new ask. A failed call that
  consumed a scan still counts as a scan.
- A budget line in a task prompt is a ceiling, not an approval.
- This binds every session and every tool, including one-off scans.

## 2d. ⭐ HARD RULE — the client log is part of "done" (owner rule, 6 Aug 2026)

**`docs/CLIENT-WEEKLY.md` is not a chore for later. No build is tagged and no release is published
until that build has its own plain-English entry in it, written in the SAME COMMIT as the release.**

- A tagged build with no entry is an unfinished build. Not "finished, write-up pending" — unfinished.
- Same commit, not the next one. A log entry deferred to "after CI goes green" is a log entry that
  the next red build, the next context switch, or the end of a session quietly deletes. This rule
  exists because nine releases went out between 4 and 6 August 2026 while the log's newest entry
  still read Monday 3 August and still linked v0.6.6 — ten days of the client's only window onto the
  work, silently blank, and nobody noticed until the owner did.
- `scripts/check-client-log.sh` enforces it, and it runs on the release path. A release whose tag is
  not named in the log does not publish. Do not route around it.
- The entry is written in the file's own voice — outcomes only, no file names, no code, no build or
  CI talk. The maintainer note at the top of the file is binding; read the existing weeks first.

### ⭐ And our own process failures NEVER appear in the client's file

A stale log, a missed step, a bookkeeping gap, a gate that had to be re-run, a tag that had to be
moved — **these are reported to the owner, in his own report, and are never written into anything the
client reads.** They are not the client's problem, they do not help him, and confessing them to him
buys candour we already owe the owner instead.

⚠ This cuts both ways and the second half is the one that gets forgotten: **the owner is told
plainly, every time.** "It slipped" said to him is honesty; the same sentence in the client's file is
noise dressed up as honesty. Never fix the second by skipping the first.

What the client's file DOES carry is anything that affects the PRODUCT he is being handed — a
feature that regressed, a limit that is real, a decision still open. That is not a process failure;
that is the work.

## 2e. ⭐ HARD RULE — VastuFirst is ENGLISH ONLY, permanently (owner decision, 9 Aug 2026)

**Not "English for now". Not "more languages later". There is no phase that adds one.** The
six-language plan (English, Hindi, Tamil, Telugu, Marathi, Bengali) is **cancelled outright** —
Implementation PRD Phase 4 and Product PRD §7.5 both carry the cancellation.

**Nothing in the product may imply another language is coming.** No language picker, no "soon" pill,
no note on any screen, no roadmap line, no `values-<lang>/` folder, no per-script font ramp, no
Hindi/Tamil render config. If you find one, it is a leftover — delete it, do not honour it.

This rule lives here, in the auto-loaded file, on purpose. A cancellation written only into a
document gets re-obeyed by the next session that reads the document and not the cancellation.

**Two things this does NOT cancel — do not "tidy" them away:**

- **Number formatting follows the phone.** The decimal mark is read from the device's own locale
  data, so a phone set to a comma language writes 6,8. That is the device's setting, not app
  translation, and it is what stops the app writing a wrong number on a phone we never anticipated.
- **The Sanskrit and Vastu vocabulary stays.** Deity names, element names, zone names, provenance
  tags. That is the product, not localisation, and cutting it would cut the thing worth ₹699.

**And the long-text testing survives without it.** "A Hindi report must render without clipping" is
replaced by font scale 2.0, 360 dp and 320 dp — real configurations on a real English phone, and
unlike the language configs they actually change the rendered picture.

## 2f. ⭐ HARD RULE — never write a CI skip marker into a commit MESSAGE (10 Aug 2026)

GitHub reads the skip markers — `[skip ci]`, `[ci skip]`, `[no ci]`, `[skip actions]` — out of the
**head commit message of any push, including a TAG push.** Two consequences, both paid for on v0.9.1
inside half an hour:

- **A commit carrying one can never be released from.** Tag it and the release workflow does not run
  at all: no APK, no failure, no entry in the runs list. Silence that looks exactly like success.
- **It fires from anywhere in the message, including prose.** The commit that *documented* this trap
  quoted the marker in its own body and thereby skipped itself, and then the tag on it too.

So: **describe a marker, never spell it.** Write "the skip marker" in a commit message. Spelling it
inside a *file* is fine — only commit messages are scanned.

The mechanism itself is now a job-level `if:` in `ci.yml` that names the bot's own goldens commit,
which skips that one workflow and leaves the release path alone. Do not reintroduce the marker there.

## 3. How to work

1. **Plan first** for anything bigger than a one-line change. Show a numbered plan; wait for "go."
2. Do one step, then stop and show the diff. Don't run a whole phase unattended.
3. **Prove it works** via CI — show the real result, never just claim it passes.
4. Push after each phase and tag a build (standing rule for this owner).

## 3b. ⭐ HARD RULE — how every session ENDS

**Every session ends with a short, plain-English wrap-up. Two lists, nothing else:**

```
DONE  — what changed, in words the owner can act on, plus the APK link
YOURS — what I need from him, each item a complete question with the context inside it
```

Rules for it, all binding:

- **No codes, ever.** Not `G4`, not `S8`, not `F3`, not `§6.2b`, not `v0.3.14`-as-an-identifier, not
  a file or class name. The owner does not read every line I print and will not go looking up a label.
  If a checklist row matters, **write the question out** instead of pointing at its number.
- **Each "yours" item stands alone.** It must make sense to someone who read nothing else in the
  session. "Is a row of squares to drag easier than an empty grid?" — not "please check item G4".
- **Say what is NOT done**, in the same plain words, especially anything blocking.
- **Short.** If it runs past roughly a screen, it is too long — the detail belongs in `docs/`.
- Assume he read **nothing** above the wrap-up. This applies even mid-session, whenever I hand
  something back.

Why this is a rule: I ended a session pointing him at "the question on the checklist as G4". That is
a label only I can resolve, so the question effectively went unasked.

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

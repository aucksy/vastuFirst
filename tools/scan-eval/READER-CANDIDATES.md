# Reader candidates — the running scoreboard

**The question this file answers: which AI reads a floor-plan photo best for the least money.**
It is the durable record of every reader head-to-head (the raw recordings under `out/live/` are
git-ignored and live only on the owner's machine — this file is what survives). Full narrative:
`docs/SCAN-PLAN-READING-PLAN.md` §3p. Method for every row, always the same, so rows stay
comparable across rounds:

- the app's exact prompt file and 1400px/q88 JPEG downscale (`scan-candidate.py` / `scan-live.py`)
- two runs per model per sheet (consistency counts), drawn through the real mapper mirror
  (`render-grid.mjs`), scored by `resemblance.mjs` against the hand-checked truth files in `truth/`
- ⚠ every scan needs the owner's approved COUNT first — CLAUDE.md §2c, hard rule

## Overall ranking (after round 2, 4 Aug 2026)

| rank | model | source | quality | same answer twice | ₹/scan (MEASURED) |
|---|---|---|---|---|---|
| **1 value** | `openai/gpt-5.6-luna` | OpenRouter | equals the quality king on every classic-2D sheet — BEST on the client's tower (15/16 · 100% · RIGHT ×2), branded-proof (97%) — but refuses furnished renders (NOT_2D ×2) and once dropped a corridor on the owner's flat | scores stable, drawings wobble cosmetically (1/7 identical) | **₹0.09** |
| **1 quality** | `gemini-3.1-pro-preview` (= gemini-pro-latest) | Google or OpenRouter | best all-round; the ONLY model that reads the furnished class (13/18 · 100% · RIGHT ×2) | scores identical 7/7 | ₹1.39 |
| 3 | `anthropic/claude-haiku-4.5` | OpenRouter | steadiest drawings (6/7 identical) but weak where it matters: tower 83% · biggest WRONG; branded page collapses to 69% (qwen's disease) | 6/7 | ₹0.61 |
| 4 | `gemini-3.6-flash` | Google or OpenRouter | near-pro, materially unstable twice | 1/7 | ₹2.18 (thinking tokens) |
| 5 | `openai/gpt-5-nano` | OpenRouter | loses 1–3 rooms on almost every sheet, flips biggest-room run to run, furnished refused | 0/7 | ₹0.23 |
| 6 | `qwen/qwen3.6-27b` (today's app reader) | Groq | loses 1–4 rooms on the two most important sheets; 74% on the branded page; refuses plan-007 | 4/5, but varies on the CLIENT's sheet | ₹0.21 |

**Verdict "cheapest yet best": `gpt-5.6-luna` as the default reader (9 paise, beats today's
reader on every sheet and costs LESS), with `gemini-3.1-pro-preview` as the escalation for the
furnished-render class once the triage gate is split — both through the ONE OpenRouter door.**

## Round 1 detail — per sheet (rooms placed of truth · arrangement · biggest-room)

| sheet | today (qwen3.6) | gemini-3.6-flash | gemini-pro-latest |
|---|---|---|---|
| towerEF-1854 (client) | 12–13/16 · 92–96% · flips | 14–15/16 · 99–100% · RIGHT | **15/16 · 99% · RIGHT, stable** |
| plan-010 (owner) | 14/15 · 92% · WRONG ×2 | 15/15 · 98–99% · RIGHT | **15/15 · 98% · RIGHT, stable** |
| greencourt-526 | 7/7 · 92% · WRONG | 7/7 · 97% · RIGHT | 7/7 · 92% · RIGHT |
| greencourt-336 clean | 7/7 · 94% · WRONG | 7/7 · 97% · WRONG¹ | 7/7 · 97% · WRONG¹ |
| greencourt-336 branded | 7/7 · **74%** | 7/7 · 97% | 7/7 · 97% |
| plan-006 (no sizes) | 10/10 · 100% · RIGHT | 10/10 · 100% · RIGHT | 10/10 · 100% · RIGHT |
| plan-007 (furnished render) | REFUSED | unstable (refused² / 13/18) | **13/18 · 100% · RIGHT ×2** |

¹ all-reader mapper nit: at grid scale the 336's lobby (truth-biggest) rounds below the bedroom.
² flash returned all 18 rooms and the TOO_MANY_ROOMS gate refused a fully-sized reply — gate bug,
noted in §3p, to fix before any switch.

## Round 2 detail — the cheap seats via OpenRouter (42 scans, ₹13 measured, 4 Aug 2026)

Per sheet (rooms placed of truth · arrangement · biggest-room), runs shown when they differ:

| sheet | gpt-5-nano | gpt-5.6-luna | claude-haiku-4.5 |
|---|---|---|---|
| towerEF-1854 (client) | 13–14/16 · 77–81% · flips | **15/16 · 100% · RIGHT ×2** | 14/16 · 83% · WRONG ×2 |
| plan-010 (owner) | 14/15 · 87–89% · flips | 14–15/15 · 91–97% · RIGHT | 14/15 · 94–95% · RIGHT |
| greencourt-526 | 6/7 · 92–96% · flips | **7/7 · 94–100% · RIGHT** | 7/7 · 92% · WRONG |
| greencourt-336 clean | 7/7 · 86–97% · WRONG¹ | 7/7 · 97% · WRONG¹ | 7/7 · 89% · RIGHT |
| greencourt-336 branded | 6–7/7 · 83–94% | **7/7 · 97% ×2** | 7/7 · **69%** ×2 |
| plan-006 (no sizes) | 9/10 · 93–95% | **10/10 · 100% · RIGHT ×2** | 10/10 · 100% · RIGHT ×2 |
| plan-007 (furnished) | returned 15 rooms, gate refused ×2² | model itself says NOT_2D ×2 | 14/18 · 97% ×2 |

¹ the all-reader 336 lobby-vs-bedroom rounding nit (see round 1).
² second sighting of the TOO_MANY_ROOMS gate refusing a fully-sized reply (first: flash r1, 18
rooms) — the gate bug is now reproducible from two models.

Notes: nano and luna burn hidden reasoning tokens (nano ~7k/scan — that is why its measured cost
is 3× list estimate); luna's reasoning is modest (~350/scan). Haiku's failure mode is qwen-like
branded-page arrangement damage, not room loss. Luna's one real slip across 14 runs: DRESS &
PASSAGE (a corridor) missing on one owner's-flat run.

## How to reproduce any row

```
python tools/scan-eval/scan-candidate.py "Documents/Sample Floor plans/<sheet>" --model=<id> --tag=r1   # PAID — approved count first
node tools/scan-eval/render-grid.mjs --only=X --reply=tools/scan-eval/out/live/<recording>.json
node tools/scan-eval/resemblance.mjs --truth=tools/scan-eval/truth/<plan>.json tools/scan-eval/out/render/<recording>.geom.json
python tools/scan-eval/render-png.py <recording>       # then LOOK at the png next to the sheet
```

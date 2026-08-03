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

## Overall ranking (after round 1, 3 Aug 2026)

| rank | model | source | quality (6 sheets) | same answer twice | ₹/scan (measured) |
|---|---|---|---|---|---|
| **1** | `gemini-3.1-pro-preview` (= gemini-pro-latest) | Google or OpenRouter | best on every sheet; reads the refused furnished class 13/18 · 100% ×2 | scores identical 7/7 | **₹1.4** |
| 2 | `gemini-3.6-flash` | Google or OpenRouter | near-pro, but materially unstable twice | 1/7 drawings identical | ₹2.2 (thinking tokens) |
| 3 | `qwen/qwen3.6-27b` (today's app reader) | Groq | loses 1–4 rooms on the two most important sheets; 74% on the branded page; refuses plan-007 | 4/5, but varies on the CLIENT's sheet | ₹0.21 |

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

## Round 2 — the cheap seats (OpenRouter), PENDING the owner's key

Goal: the cheapest model that holds round-1-winner quality. One source for everything —
OpenRouter also serves the round-1 winner at the same price. Approved: 42 scans (~₹15 est).
Blocked on: `OPENROUTER_API_KEY` in `.env` (line exists, empty).

| model | list price in/out $/M | est. ₹/scan | result |
|---|---|---|---|
| `openai/gpt-5-nano` | 0.05 / 0.40 | ~0.07 | *pending* |
| `openai/gpt-5.6-luna` | 0.10 / 0.60 | ~0.10 | *pending* |
| `anthropic/claude-haiku-4.5` | 1.00 / 5.00 | ~0.8–1.0 | *pending* |

## How to reproduce any row

```
python tools/scan-eval/scan-candidate.py "Documents/Sample Floor plans/<sheet>" --model=<id> --tag=r1   # PAID — approved count first
node tools/scan-eval/render-grid.mjs --only=X --reply=tools/scan-eval/out/live/<recording>.json
node tools/scan-eval/resemblance.mjs --truth=tools/scan-eval/truth/<plan>.json tools/scan-eval/out/render/<recording>.geom.json
python tools/scan-eval/render-png.py <recording>       # then LOOK at the png next to the sheet
```

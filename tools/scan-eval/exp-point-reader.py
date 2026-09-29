#!/usr/bin/env python
r"""
THE DIRECTION TRIAL'S READER ROUND (29 Sep 2026): can a CHEAPER model do the easier job — read each
room's printed name and size, and point at the name — well enough to become the default?

    python tools/scan-eval/exp-point-reader.py plan                        # free: the exact scan list + cost
    python tools/scan-eval/exp-point-reader.py run --approved=80            # PAID: the point arm only
    python tools/scan-eval/exp-point-reader.py run --approved=140 --box-arm # PAID: point arm + box arm
    python tools/scan-eval/exp-point-reader.py score                       # free: every recording vs the truth

TWO ARMS. The POINT arm runs the candidate prompt on every model. The optional BOX arm (--box-arm) runs
TODAY'S prompt on the cheaper models only, because the free measurement found today's reader's BOXES,
scored as drawn on the page, already get the direction right 95.5 % of the time on the hand-marked
rooms: the saving may come from a cheaper model alone, with no change of format at all.

The job is easier than today's on purpose. Today's prompt (v6) asks for a RECTANGLE per room, the one
thing every vision model does badly (40-70 %). The candidate prompt (prompt-v7-point.txt) asks for the
printed text and ONE POINT per room — the middle of its printed name — which is reading, the thing they
do well. Rule S1 still holds: nothing Vastu is asked; our own code turns the point into a direction.

WHAT `score` MEASURES, per model, against the rooms marked by hand (geometry only — the DIRECTION of
each point is scored by the real engine in the cloud, DirectionTrialMeasureTest, never here):
  found     hand-marked rooms the reply names (a room that never arrives cannot be corrected)
  in room   the point falls inside the hand-marked room with that name (truth-rooms.json sheets)
  off       how far the point is from the true room's middle, as a share of the home's diagonal
  sized     rooms whose printed size came back (the size rebuilds the footprint)
  same x2   do the two reads of one sheet agree (rooms named by both / rooms named by either)
  charged   what OpenRouter actually billed, in rupees, from the recordings

TODAY'S READER is scored the same way from its committed v6 recordings, using the middle of each box
as its point — free, no scan.

HARD RULE (CLAUDE.md 2c): every `run` scan is paid. `run` refuses unless --approved equals the exact
number `plan` prints, never re-scans a recording that already exists, and stops at the approved count.
"""
import glob, json, math, os, re, subprocess, sys, urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
LIVE = os.path.join(HERE, "out", "live")
SHEETS_DIR = os.path.join(ROOT, "Documents", "Sample Floor plans")
PROMPT = os.path.join(HERE, "prompt-v7-point.txt")
APP_PROMPT = os.path.join(ROOT, "shared", "src", "main", "resources", "scan", "plan-read-prompt.txt")
TAGS = ["pt1", "pt2"]      # two reads per model per sheet: one read cannot see the thin-read defect
BOX_TAGS = ["bx1", "bx2"]  # the box arm: today's prompt, the same two reads
USD_INR = 96.07        # open.er-api.com, 29 Sep 2026

# Every sheet with hand-marked truth — and nothing else, because a sheet we cannot score is money
# spent on nothing.
SHEETS = [
    "aipl-zen-residences-gurgaon_2bhk-1262sqft (1)", "greencourt-336-branded", "greencourt-336-clean",
    "greencourt-526", "hlv9vurn", "plan-006", "plan-007", "plan-010", "towerEF-1854", "floor-plan-1",
]

# (model id, why it is here). Prices are read LIVE from OpenRouter by `plan`, never typed in.
MODELS = [
    ("openai/gpt-5.6-luna", "today's model on the easier job: the control, and the 'one read not two' question"),
    ("openai/gpt-6-luna", "the newer luna, half today's price per token"),
    ("qwen/qwen3.8-flash", "a cheap Qwen: that family is trained to point at things on an image"),
    ("google/gemini-3.1-flash-lite", "Google's cheap tier: can it read the furnished render and end the Rs 2.7 escalation?"),
]

# The box arm: the cheaper candidates only — today's model on today's prompt is already recorded.
BOX_MODELS = [m for (m, _) in MODELS if m != "openai/gpt-5.6-luna"]

# Generous per-read token bounds for the cost ceiling. Measured on today's reader (v6, 39 reads):
# ~2,080 in and ~1,840 out including its hidden reasoning. The point reply is shorter, but a new
# model may reason more, so the ceiling assumes more of both.
CEIL_IN, CEIL_OUT = 3000, 2500


def sheet_file(stem):
    for ext in (".png", ".jpg", ".jpeg", ".webp", ".avif"):
        p = os.path.join(SHEETS_DIR, stem + ext)
        if os.path.exists(p):
            return p
    return None


def rec_path(stem, model, tag):
    return os.path.join(LIVE, "%s.%s.%s.json" % (stem, model.replace("/", "_"), tag))


def live_prices():
    with urllib.request.urlopen("https://openrouter.ai/api/v1/models", timeout=60) as r:
        data = json.loads(r.read())["data"]
    return {m["id"]: (float(m["pricing"]["prompt"]), float(m["pricing"]["completion"])) for m in data}


def todo(box_arm=False):
    """Every scan still to run, as (sheet, model, tag, prompt) — nothing already recorded is re-run."""
    jobs = [(s, m, t, PROMPT) for s in SHEETS for (m, _) in MODELS for t in TAGS]
    if box_arm:
        jobs += [(s, m, t, APP_PROMPT) for s in SHEETS for m in BOX_MODELS for t in BOX_TAGS]
    return [j for j in jobs if not os.path.exists(rec_path(j[0], j[1], j[2]))]


def cmd_plan():
    prices = live_prices()
    missing = [s for s in SHEETS if not sheet_file(s)]
    if missing:
        print("MISSING SHEETS (fix before running):", missing)
    whys = dict(MODELS)
    for box_arm in (False, True):
        jobs = todo(box_arm)
        total_ceiling = 0.0
        print("%s: %d scans" % ("POINT ARM + BOX ARM (--box-arm)" if box_arm else "POINT ARM ONLY", len(jobs)))
        for arm, prompt in (("point", PROMPT), ("box", APP_PROMPT)):
            for (m, _) in MODELS:
                n = sum(1 for j in jobs if j[1] == m and j[3] == prompt)
                if n == 0:
                    continue
                if m not in prices:
                    print("  %-5s %-30s NOT SERVED BY OPENROUTER TODAY - drop or replace it" % (arm, m))
                    continue
                pin, pout = prices[m]
                per = (CEIL_IN * pin + CEIL_OUT * pout) * USD_INR
                total_ceiling += n * per
                print("  %-5s %-30s %2d scans  at most Rs %.2f a read -> at most Rs %.1f   (%s)"
                      % (arm, m, n, per, n * per, whys[m] if arm == "point" else "today's prompt, cheaper model"))
        print("  at most Rs %.1f in total (list prices, generous token bounds)\n" % total_ceiling)
    print("Each read records what OpenRouter actually charged.")


def cmd_run(approved, box_arm):
    jobs = todo(box_arm)
    if approved != len(jobs):
        print("REFUSED: %d scans are listed and --approved=%d. The owner approves an exact count (CLAUDE.md 2c)."
              % (len(jobs), approved))
        return 1
    done = 0
    for (s, m, t, prompt) in jobs:
        if done >= approved:
            break
        img = sheet_file(s)
        print("[%d/%d] %s  %s  %s" % (done + 1, approved, s, m, t))
        subprocess.call([sys.executable, os.path.join(HERE, "scan-candidate.py"), img,
                         "--model=" + m, "--tag=" + t, "--prompt=" + prompt])
        done += 1  # a failed call that consumed a scan still counts as a scan
    print("ran %d scans" % done)
    return 0


# ------------------------------------------------------------------------------------------ score

def norm(s):
    return re.sub(r"[^A-Z0-9]+", " ", (s or "").upper()).strip()


def load_truth():
    """stem -> list of rooms {label, cx, cy, rect|None} in fractions of the SHEET."""
    out = {}
    rects = json.load(open(os.path.join(HERE, "truth-rooms.json"), encoding="utf-8"))
    for stem, rooms in rects.items():
        if stem.startswith("_"):
            continue
        out[stem] = [{"label": r["label"], "cx": r["x"] + r["w"] / 2, "cy": r["y"] + r["h"] / 2,
                      "rect": (r["x"], r["y"], r["w"], r["h"])} for r in rooms]
    paper_for = {"greencourt-336": "greencourt-336-clean"}
    for f in glob.glob(os.path.join(HERE, "truth", "*.json")):
        d = json.load(open(f, encoding="utf-8"))
        stem = paper_for.get(d["id"], d["id"])
        if stem in out:
            continue  # the hand-marked rectangles win where a sheet has both
        out[stem] = [{"label": r["name"], "cx": r["cx"], "cy": r["cy"], "rect": None} for r in d["rooms"]]
    return out


def points_of(rec, is_point_reply):
    """Every room in a recording as (label, size, page-x, page-y).

    A box reply (prompt v6) gives each room's top-left corner, so its point is the box's middle. A
    point reply (prompt v7-point) gives the point itself, even on the rooms where it also gives a
    rough w/h because no size is printed — those are drawn AROUND the point, never from it.
    """
    rep = rec.get("reply") or {}
    b = rep.get("building") or {"x": 0, "y": 0, "w": 1, "h": 1}
    pts = []
    for r in rep.get("rooms", []):
        if is_point_reply:
            x, y = r.get("x", 0), r.get("y", 0)
        else:
            x = r.get("x", 0) + (r.get("w") or 0) / 2
            y = r.get("y", 0) + (r.get("h") or 0) / 2
        pts.append((r.get("label", ""), r.get("size", ""), b["x"] + x * b["w"], b["y"] + y * b["h"]))
    return pts, rep.get("planType")


def match(truth, pts, aspect):
    """Greedy: same caption first (or one containing the other), nearest point wins a repeated caption."""
    cands = []
    for ti, t in enumerate(truth):
        for pi, p in enumerate(pts):
            a, b = norm(t["label"]), norm(p[0])
            if a and b and (a == b or a in b or b in a):
                d = math.hypot((p[2] - t["cx"]) * aspect, p[3] - t["cy"])
                cands.append((d, ti, pi))
    cands.sort()
    used_t, used_p, pairs = set(), set(), []
    for d, ti, pi in cands:
        if ti in used_t or pi in used_p:
            continue
        used_t.add(ti)
        used_p.add(pi)
        pairs.append((ti, pi))
    return pairs


def score_one(truth, rec, is_point_reply):
    aspect = rec["imageSize"][0] / float(rec["imageSize"][1])
    pts, ptype = points_of(rec, is_point_reply)
    pairs = match(truth, pts, aspect)
    xs = [t["cx"] * aspect for t in truth]
    ys = [t["cy"] for t in truth]
    diag = math.hypot(max(xs) - min(xs), max(ys) - min(ys)) or 1.0
    inroom = rects = 0
    offs = []
    for ti, pi in pairs:
        t, p = truth[ti], pts[pi]
        offs.append(math.hypot((p[2] - t["cx"]) * aspect, p[3] - t["cy"]) / diag)
        if t["rect"]:
            rects += 1
            x, y, w, h = t["rect"]
            if x <= p[2] <= x + w and y <= p[3] <= y + h:
                inroom += 1
    sized = sum(1 for p in pts if (p[1] or "").strip())
    cost = ((rec.get("usage") or {}).get("costUsd") or 0) * USD_INR
    return {"found": len(pairs), "of": len(truth), "inroom": inroom, "rects": rects, "offs": offs,
            "sized": sized, "rooms": len(pts), "labels": {norm(p[0]) for p in pts}, "cost": cost, "type": ptype}


def cmd_score():
    truth = load_truth()
    runs = [("today (gpt-5.6-luna, boxes, v6)", False, lambda s: [os.path.join(LIVE, s + ".openai_gpt-5.6-luna.v6.json")])]
    for (m, _) in MODELS:
        runs.append(("%s (points)" % m, True, lambda s, m=m: [rec_path(s, m, t) for t in TAGS]))
    for m in BOX_MODELS:
        runs.append(("%s (boxes, today's prompt)" % m, False, lambda s, m=m: [rec_path(s, m, t) for t in BOX_TAGS]))
    for name, is_point, paths in runs:
        found = of = inroom = rects = sized = rooms = n = 0
        offs, agree, cost, refused = [], [], 0.0, 0
        for s in SHEETS:
            recs = [json.load(open(p, encoding="utf-8")) for p in paths(s) if os.path.exists(p)]
            for r in recs:
                sc = score_one(truth[s], r, is_point)
                n += 1
                found += sc["found"]; of += sc["of"]; inroom += sc["inroom"]; rects += sc["rects"]
                sized += sc["sized"]; rooms += sc["rooms"]; offs += sc["offs"]; cost += sc["cost"]
                refused += sc["type"] != "2D_PLAN"
            if len(recs) == 2:
                a, b = score_one(truth[s], recs[0], is_point)["labels"], score_one(truth[s], recs[1], is_point)["labels"]
                agree.append(len(a & b) / float(len(a | b) or 1))
        if n == 0:
            print("%-44s no recordings yet" % name)
            continue
        offs.sort()
        med = offs[len(offs) // 2] if offs else float("nan")
        print("%-44s reads %2d | found %3d/%-3d | in room %3d/%-3d | off (median) %.3f | sized %3d/%-3d | "
              "same x2 %s | not-2D %d | charged Rs %.2f"
              % (name, n, found, of, inroom, rects, med, sized, rooms,
                 ("%.0f%%" % (100 * sum(agree) / len(agree))) if agree else "-", refused, cost))


if __name__ == "__main__":
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    opts = dict(a[2:].split("=", 1) for a in sys.argv[1:] if a.startswith("--") and "=" in a)
    if not args or args[0] not in ("plan", "run", "score"):
        print(__doc__)
        sys.exit(1)
    if args[0] == "plan":
        cmd_plan()
    elif args[0] == "run":
        sys.exit(cmd_run(int(opts.get("approved", "-1")), "--box-arm" in sys.argv))
    else:
        cmd_score()

#!/usr/bin/env python
r"""
Draw the review screen's ACTUAL room boxes over the plan — FREE, reads a recording, scans nothing.

WHY THIS EXISTS. On 15 Aug 2026 the owner photographed the "Check what we read" screen and said the
highlight box was plainly bigger than the room it named. That screen is the one place a customer is
asked to confirm we understood their home, so a box that does not sit on its room is the difference
between trust and "this thing is broken".

Reading the code alone cannot settle it: the drawn box is the end of a three-step chain, and each
step is defended by its own comment.

  1. page box   = building.xy + room.xy * building.wh   (prompt v4: room fractions are measured
                  inside the BUILDING, not the sheet, so drawing them on the sheet is wrong)
  2. tighten    = resize to the PRINTED proportions about the box's own centre, using a single
                  median scale fitted long-against-long over every dimensioned room
  3. draw       = printedBox if the tighten changed it, else the reader's own rectangle
                  (ScanReviewScreen.planRoomsOf)

This renders step 1 and step 3 side by side so the correction can be SEEN, not argued about.
  RED    = what the reader returned, composed onto the page (step 1)
  GREEN  = what the review screen actually draws (step 3)

    python tools/scan-eval/exp-review-boxes.py "<stem in out/live>"

Writes out/review-boxes/<stem>.png. Then OPEN IT AND LOOK — the render gate measures boxes, not
what a person would call correct.
"""
import json, os, re, sys
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
LIVE = os.path.join(HERE, "out", "live")
PLANS = os.path.join(ROOT, "Documents", "Sample Floor plans")
OUTDIR = os.path.join(HERE, "out", "review-boxes")

# MIRRORS ScanMapper. If these move there, move them here.
MIN_PRINTED_TO_FIT = 3
MAX_PRINTED_DISAGREEMENT = 4.0

FEET = re.compile(r"(\d+)\s*'\s*(\d+)?\s*\"?\s*[xX×]\s*(\d+)\s*'\s*(\d+)?\s*\"?")
MM = re.compile(r"(\d{3,5})\s*[xX×]\s*(\d{3,5})")


def printed_mm(size):
    """The printed caption as (width, depth) in mm — first number is the width, as the app does."""
    s = (size or "").strip()
    if not s:
        return None
    m = FEET.search(s)
    if m:
        w = int(m.group(1)) * 304.8 + int(m.group(2) or 0) * 25.4
        d = int(m.group(3)) * 304.8 + int(m.group(4) or 0) * 25.4
        return (w, d) if w > 0 and d > 0 else None
    m = MM.search(s)
    if m:
        return (float(m.group(1)), float(m.group(2)))
    return None


def median(xs):
    xs = sorted(xs)
    n = len(xs)
    return xs[n // 2] if n % 2 else (xs[n // 2 - 1] + xs[n // 2]) / 2.0


# MIRRORS ScanMapper.MAX_NUDGE / NUDGE_PASSES.
MAX_NUDGE = 0.05
NUDGE_PASSES = 24


def overlap_share(bs):
    """Overlapping area as a share of all box area. Real rooms never overlap, so this is error."""
    total = sum(b["w"] * b["h"] for b in bs) or 1.0
    hit = 0.0
    for i in range(len(bs)):
        for j in range(i + 1, len(bs)):
            dx = min(bs[i]["x"] + bs[i]["w"], bs[j]["x"] + bs[j]["w"]) - max(bs[i]["x"], bs[j]["x"])
            dy = min(bs[i]["y"] + bs[i]["h"], bs[j]["y"] + bs[j]["h"]) - max(bs[i]["y"], bs[j]["y"])
            if dx > 0 and dy > 0:
                hit += dx * dy
    return 100.0 * hit / total


def separate(bs, building):
    """Push overlapping boxes apart along the axis of LEAST penetration — the smallest move that
    fixes it — with every box leashed to MAX_NUDGE of where it was read. Never resizes.

    ⚠ THE BUILDING CLAMP IS NOT TIDINESS, it is the whole reason this ships. Measured on the owner's
    Gurgaon sheet, separating with a free leash drove overlap to 0 % and pushed 6.9 % of the box area
    OFF the building onto blank paper — his master toilet ended up hanging in the margin. A box on
    white space is obviously wrong to anyone looking; a slight overlap is subtle. Trading the subtle
    error for the obvious one is a loss even though the overlap number looks like a win.

    Clamped inside the building instead, over 134 recorded reads:
        overlap       6.1 % -> 4.4 %
        off-building  1.2 % -> 0.0 %, and it regresses on NOT ONE recording
    19 reads do end up with more overlap, and in nearly all of them the increase buys a larger
    off-building gain (one trades +8.5 % overlap for -14.2 % off-building). That is the trade a
    person looking at the screen would take every time.
    """
    home = [(b["x"], b["y"]) for b in bs]
    out = [dict(b) for b in bs]
    for _ in range(NUDGE_PASSES):
        moved = False
        for i in range(len(out)):
            for j in range(i + 1, len(out)):
                a, c = out[i], out[j]
                dx = min(a["x"] + a["w"], c["x"] + c["w"]) - max(a["x"], c["x"])
                dy = min(a["y"] + a["h"], c["y"] + c["h"]) - max(a["y"], c["y"])
                if dx <= 0 or dy <= 0:
                    continue
                if dx < dy:
                    s = dx / 2 * (1 if a["x"] < c["x"] else -1)
                    a["x"] -= s
                    c["x"] += s
                else:
                    s = dy / 2 * (1 if a["y"] < c["y"] else -1)
                    a["y"] -= s
                    c["y"] += s
                moved = True
        for k, b in enumerate(out):
            b["x"] = min(max(b["x"], home[k][0] - MAX_NUDGE), home[k][0] + MAX_NUDGE)
            b["y"] = min(max(b["y"], home[k][1] - MAX_NUDGE), home[k][1] + MAX_NUDGE)
            if b["w"] <= building["w"]:
                b["x"] = min(max(b["x"], building["x"]), building["x"] + building["w"] - b["w"])
            if b["h"] <= building["h"]:
                b["y"] = min(max(b["y"], building["y"]), building["y"] + building["h"] - b["h"])
        if not moved:
            break
    return out


def off_building_share(bs, building):
    """Box area sitting outside the building outline. A room cannot be off its own building."""
    total = sum(b["w"] * b["h"] for b in bs) or 1.0
    out = 0.0
    for b in bs:
        dx = max(0.0, min(b["x"] + b["w"], building["x"] + building["w"]) - max(b["x"], building["x"]))
        dy = max(0.0, min(b["y"] + b["h"], building["y"] + building["h"]) - max(b["y"], building["y"]))
        out += max(0.0, b["w"] * b["h"] - dx * dy)
    return 100.0 * out / total


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return 1
    stem = sys.argv[1]
    d = json.load(open(os.path.join(LIVE, stem + ".json"), encoding="utf-8"))
    reply = d["reply"]
    if isinstance(reply, str):
        reply = json.loads(reply)

    plan = None
    for f in os.listdir(PLANS):
        if os.path.splitext(f)[0] == stem:
            plan = os.path.join(PLANS, f)
            break
    if not plan:
        print("no plan image named %r in %s" % (stem, PLANS))
        return 1

    im = Image.open(plan).convert("RGB")
    W, H = im.size
    b = reply.get("building") or {"x": 0.0, "y": 0.0, "w": 1.0, "h": 1.0}
    rooms = reply.get("rooms", [])

    # Step 1 — compose each room onto the page.
    page = []
    for r in rooms:
        page.append({
            "label": r.get("label", ""),
            "size": r.get("size", ""),
            "x": b["x"] + r["x"] * b["w"],
            "y": b["y"] + r["y"] * b["h"],
            "w": r["w"] * b["w"],
            "h": r["h"] * b["h"],
        })

    # Step 2 — one median scale over every dimensioned room, fitted long-against-long.
    ratios = []
    for p in page:
        mm = printed_mm(p["size"])
        if not mm or p["w"] <= 0 or p["h"] <= 0:
            continue
        ratios.append(max(p["w"], p["h"]) / max(mm))
        ratios.append(min(p["w"], p["h"]) / min(mm))
    scale = median(ratios) if len(ratios) >= MIN_PRINTED_TO_FIT * 2 else None

    drawn = []
    for p in page:
        mm = printed_mm(p["size"])
        if scale is None or not mm:
            drawn.append(dict(p))
            continue
        w, h = min(mm[0] * scale, 1.0), min(mm[1] * scale, 1.0)   # printed order: width first
        grew = (w * h) / (p["w"] * p["h"])
        if grew > MAX_PRINTED_DISAGREEMENT or grew < 1.0 / MAX_PRINTED_DISAGREEMENT:
            drawn.append(dict(p))                                  # caption mis-read — keep the read
            continue
        cx, cy = p["x"] + p["w"] / 2, p["y"] + p["h"] / 2          # centre held, never clamped
        drawn.append({**p, "x": cx - w / 2, "y": cy - h / 2, "w": w, "h": h})

    # Step 4 — push overlapping boxes apart. Real rooms never overlap, so an overlap is always an
    # error; the only question is whether nudging is a smaller error than leaving it. Every box is
    # on a LEASH of MAX_NUDGE from where it was read, because the reader's centre is the one part of
    # its answer worth keeping — a box dragged across the plan is a worse lie than a box that
    # overlaps its neighbour.
    settled = separate(drawn, b)

    g = ImageDraw.Draw(im)
    for p, q, s in zip(page, drawn, settled):
        g.rectangle([p["x"] * W, p["y"] * H, (p["x"] + p["w"]) * W, (p["y"] + p["h"]) * H],
                    outline=(220, 40, 40), width=2)
        g.rectangle([q["x"] * W, q["y"] * H, (q["x"] + q["w"]) * W, (q["y"] + q["h"]) * H],
                    outline=(230, 150, 30), width=2)
        g.rectangle([s["x"] * W, s["y"] * H, (s["x"] + s["w"]) * W, (s["y"] + s["h"]) * H],
                    outline=(20, 150, 60), width=5)
        g.text((s["x"] * W + 5, s["y"] * H + 4), p["label"][:18], fill=(20, 90, 40))
    print("overlap between boxes : %.1f%% before nudging -> %.1f%% after"
          % (overlap_share(drawn), overlap_share(settled)))
    print("box area off building : %.1f%% before nudging -> %.1f%% after"
          % (off_building_share(drawn, b), off_building_share(settled, b)))

    os.makedirs(OUTDIR, exist_ok=True)
    out = os.path.join(OUTDIR, stem + ".png")
    im.save(out)
    print("scale fitted from %d dimensioned rooms" % (len(ratios) // 2))
    print("RED = reader's rectangle composed onto the page · GREEN = what the review screen draws")
    for p, q in zip(page, drawn):
        pa = (p["w"] * W) / (p["h"] * H)
        qa = (q["w"] * W) / (q["h"] * H)
        print("  %-16s printed %-16s read w/h %.2f -> drawn w/h %.2f  %s"
              % (p["label"][:16], p["size"][:16], pa, qa,
                 "corrected" if abs(pa - qa) > 0.01 else "unchanged"))
    print("wrote", out)
    return 0


if __name__ == "__main__":
    sys.exit(main())

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

    g = ImageDraw.Draw(im)
    for p, q in zip(page, drawn):
        g.rectangle([p["x"] * W, p["y"] * H, (p["x"] + p["w"]) * W, (p["y"] + p["h"]) * H],
                    outline=(220, 40, 40), width=2)
        g.rectangle([q["x"] * W, q["y"] * H, (q["x"] + q["w"]) * W, (q["y"] + q["h"]) * H],
                    outline=(20, 150, 60), width=4)
        g.text((q["x"] * W + 5, q["y"] * H + 4), p["label"][:18], fill=(20, 90, 40))

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

#!/usr/bin/env python
r"""
GROUND TRUTH for the review boxes: where each room ACTUALLY is on the sheet, marked by hand once.

WHY THIS EXISTS. Every number used to judge the review boxes so far is a proxy — how much the boxes
overlap each other, how much of them falls off the building. Proxies kept disagreeing with the
picture: a change that scored perfectly was visibly awful, and a change that was visibly right on the
owner's own sheet scored slightly worse across the corpus. A proxy cannot settle "does the box sit on
the room", because that question is about the room, and no proxy here mentions the room.

So the rooms are marked once, by eye, from the plan itself, in fractions of the whole sheet. Then a
drawing can be scored against them directly:

    covered  — how much of the real room the drawn box covers        (missing the room)
    spilled  — how much of the drawn box is NOT on that room         (covering the neighbours)
    iou      — the two together, one number, 100 % = exactly the room

    python tools/scan-eval/exp-truth-boxes.py grid <stem>   # a labelled 5 % grid, to read corners off
    python tools/scan-eval/exp-truth-boxes.py score <stem>  # every variant against the marked truth

⚠ The marks are a person's reading of a picture and are worth about a percent either way. They are
good enough to separate "on the room" from "half on the neighbour", which is the question, and not
good enough to rank two drawings that differ by a point.
"""
import json, os, sys
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
PLANS = os.path.join(ROOT, "Documents", "Sample Floor plans")
OUTDIR = os.path.join(HERE, "out", "truth")
TRUTH = os.path.join(HERE, "truth-rooms.json")


def plan_for(stem):
    for named in (stem, stem.split(".")[0], stem.replace("_live_", "")):
        for f in os.listdir(PLANS):
            if os.path.splitext(f)[0] == named:
                return os.path.join(PLANS, f)
    return None


def font(size):
    for f in (r"C:\Windows\Fonts\arialbd.ttf", r"C:\Windows\Fonts\arial.ttf"):
        try:
            return ImageFont.truetype(f, size)
        except Exception:
            pass
    return ImageFont.load_default()


def grid(stem):
    """Draw a labelled percentage grid over the plan so room corners can be read off it."""
    plan = plan_for(stem)
    if not plan:
        print("no plan image for %r" % stem)
        return 1
    im = Image.open(plan).convert("RGB")
    W, H = im.size
    g = ImageDraw.Draw(im)
    fs = max(14, W // 70)
    f = font(fs)
    for i in range(0, 101, 5):
        heavy = i % 10 == 0
        c = (200, 0, 0) if heavy else (255, 160, 160)
        g.line([(i / 100 * W, 0), (i / 100 * W, H)], fill=c, width=2 if heavy else 1)
        g.line([(0, i / 100 * H), (W, i / 100 * H)], fill=c, width=2 if heavy else 1)
        if heavy:
            g.text((i / 100 * W + 3, 3), str(i), fill=(200, 0, 0), font=f,
                   stroke_width=3, stroke_fill=(255, 255, 255))
            g.text((3, i / 100 * H + 3), str(i), fill=(200, 0, 0), font=f,
                   stroke_width=3, stroke_fill=(255, 255, 255))
    os.makedirs(OUTDIR, exist_ok=True)
    out = os.path.join(OUTDIR, stem + ".grid.png")
    im.save(out)
    print("wrote", out)
    return 0


def marked(stem):
    """The hand-marked rooms for one sheet, keyed by the label the reader gives them."""
    if not os.path.exists(TRUTH):
        return None
    all_ = json.load(open(TRUTH, encoding="utf-8"))
    for key in (stem, stem.split(".")[0], stem.replace("_live_", "")):
        if key in all_:
            return all_[key]
    return None


def draw_truth(stem):
    """The marks themselves over the plan — the FIRST thing to look at, before trusting any score."""
    plan, rooms = plan_for(stem), marked(stem)
    if not plan or not rooms:
        print("no plan image or no marks for %r" % stem)
        return 1
    im = Image.open(plan).convert("RGBA")
    W, H = im.size
    wash = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    g = ImageDraw.Draw(wash)
    for r in rooms:
        g.rectangle([r["x"] * W, r["y"] * H, (r["x"] + r["w"]) * W, (r["y"] + r["h"]) * H],
                    fill=(200, 0, 140, 46), outline=(200, 0, 140, 255), width=max(3, W // 300))
    im = Image.alpha_composite(im, wash).convert("RGB")
    g = ImageDraw.Draw(im)
    f = font(max(13, W // 55))
    for r in rooms:
        g.text(((r["x"] + r["w"] / 2) * W, (r["y"] + r["h"] / 2) * H), r["label"],
               fill=(0, 0, 0), font=f, anchor="mm", stroke_width=4, stroke_fill=(255, 255, 255))
    os.makedirs(OUTDIR, exist_ok=True)
    out = os.path.join(OUTDIR, stem + ".truth.png")
    im.save(out)
    print("wrote", out, "— LOOK AT IT before believing any score built on it")
    return 0


def inter(a, b):
    dx = max(0.0, min(a["x"] + a["w"], b["x"] + b["w"]) - max(a["x"], b["x"]))
    dy = max(0.0, min(a["y"] + a["h"], b["y"] + b["h"]) - max(a["y"], b["y"]))
    return dx * dy


def against_truth(boxes, rooms, per_room=False):
    """Match each drawn box to its marked room and score the pair.

    ⚠ The match is made on the READER's own rectangle, never on the drawing being judged. A sheet
    repeats captions — this one names three balconies — and matching on the drawing would let a
    drawing that wandered onto the wrong balcony be scored against the balcony it wandered onto.
    The reader's rectangle is identical for every drawing in the comparison, so all of them are
    asked about the same room.

    Returns (covered, spilled, iou, matched). A room the reader never reported is not counted
    against the drawing — that is a reading failure, a different fault with a different fix.
    """
    claimed, pairs = set(), []
    for idx, q in enumerate(boxes):
        want = str(q["label"]).upper().strip()
        rx, ry, rw, rh = q.get("read", (q["x"], q["y"], q["w"], q["h"]))
        rb = {"x": rx, "y": ry, "w": rw, "h": rh}
        best, hit = -1.0, None
        for j, r in enumerate(rooms):
            if j in claimed or r["label"].upper() != want:
                continue
            v = inter(rb, r)
            if v > best:
                best, hit = v, j
        if hit is None:
            continue
        claimed.add(hit)
        pairs.append((q, rooms[hit]))

    cov, spill, iou, rows = 0.0, 0.0, 0.0, []
    for q, r in pairs:
        i = inter(q, r)
        qa, ha = q["w"] * q["h"], r["w"] * r["h"]
        if qa <= 0 or ha <= 0:
            continue
        rows.append((r["label"], 100 * i / ha, 100 * (1 - i / qa), 100 * i / (qa + ha - i)))
        cov += i / ha
        spill += 1.0 - i / qa
        iou += i / (qa + ha - i)
    if not rows:
        return None
    n = len(rows)
    if per_room:
        return rows
    return 100 * cov / n, 100 * spill / n, 100 * iou / n, n


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        return 1
    cmd, stem = sys.argv[1], sys.argv[2]
    if cmd == "grid":
        return grid(stem)
    if cmd == "show":
        return draw_truth(stem)
    if cmd == "score":
        import importlib.util
        spec = importlib.util.spec_from_file_location("e", os.path.join(HERE, "exp-grid-boxes.py"))
        e = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(e)
        rooms = marked(stem)
        if not rooms:
            print("nothing marked for %r in %s" % (stem, TRUTH))
            return 1
        g = e.geom_of(stem)
        if not g:
            print("no PLACED grid geometry for %r" % stem)
            return 1
        sets, _ = e.boxes_for(g)
        print("%s — %d rooms marked by hand" % (stem, len(rooms)))
        print("  %-11s %8s %8s %8s" % ("", "on room", "spilled", "iou"))
        for k in e.VARIANTS:
            s = against_truth(sets[k], rooms)
            if s:
                print("  %-11s %7.1f%% %7.1f%% %7.1f%%   (%d matched)" % (k, s[0], s[1], s[2], s[3]))
        want = [a.split("=", 1)[1] for a in sys.argv[3:] if a.startswith("--rooms=")]
        for k in (want[0].split(",") if want else []):
            if k not in e.VARIANTS:
                continue
            print("\n  per room — %s" % k)
            for lab, cov, sp, iou in against_truth(sets[k], rooms, per_room=True):
                print("    %-16s on room %5.1f%%  spilled %5.1f%%  iou %5.1f%%" % (lab, cov, sp, iou))
        return 0
    print(__doc__)
    return 1


if __name__ == "__main__":
    sys.exit(main())

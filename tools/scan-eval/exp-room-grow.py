#!/usr/bin/env python
r"""
exp-room-grow.py - THE DECIDING EXPERIMENT: take a room's SHAPE from the photograph, not the model.

THE IDEA, in one line: the reader is trusted for the room's NAME and its ARRANGEMENT (both ~95 %)
and never for its EXTENT (40-70 %), so the extent is grown out of the picture instead.

  1. OCR the sheet and find the pixel box of each room's printed caption    (exp-caption-anchor.py)
  2. ink = luma < white - INK_DROP and saturation < SAT_MAX                 (WallSnap.kt's own test)
  3. ERASE every OCR word from the ink. This is the step the naive version
     was missing, and the reason it failed: a caption printed inside a box
     traps the fill inside its own label.                                    (text/graphics separation)
  4. close the ink by the sheet's measured wall thickness, so a door opening
     does not let one room leak into the next
  5. flood from the caption's centre - a point known to be inside the room,
     taken from the page and containing no model geometry at all
  6. leak gate: a fill covering too much of the home, or swallowing another
     room's anchor, is thrown away and that room keeps today's box
  7. score the result against the hand-marked rooms in truth-rooms.json

Run:  python tools/scan-eval/exp-room-grow.py            # the 5 hand-marked sheets
      python tools/scan-eval/exp-room-grow.py --only=floor-plan-1 --draw
      python tools/scan-eval/exp-room-grow.py --no-mask  # step 3 off, to prove it is load-bearing

Out:  out/room-grow/<id>.png - red = today's box, green = grown, grey = fell back to today's box,
      blue = hand-marked truth, orange dot = the caption the fill was seeded from.

⚠⚠ THE ANSWER, MEASURED 19 SEP 2026: THIS LOSES. DO NOT SHIP IT AND DO NOT RE-RUN IT BLIND.

Against the 5 hand-marked sheets (68 rooms), today's boxes versus the grown ones:

    average fit                          70 %  ->  61 %
    boxes drawn over a room they
    do not name (the owner's own
    complaint, counted at 40 %)             2  ->  37

Three different accept rules were measured and all three lose:
  · keep any non-leaking fill                            66 % -> 54 % on floor-plan-1
  · keep it only within 0.5x..1.6x of the reader's box   66 % -> 64 %  (throws away the good ones:
      the reader's box is the thing we do not trust, so a CORRECT fill is often half again its size)
  · keep it only where the implied pixels-per-metre
    agrees with the sheet's median                       66 % -> 58 %
Sealing door openings harder (close radius 0.6 -> 1.2 -> 2.0 x the wall thickness) moves the
drawn-over count 54 -> 37 -> 31 and never moves the fit past 62 %.

WHY, FROM THE PICTURES - and it is only visible in the pictures:
  · it is EXCELLENT on a room with four real walls. Every bedroom, bathroom and balcony on the
    owner's own sheet comes out sitting exactly on itself, better than what ships.
  · it leaks through DOORWAYS. A door is a gap in a wall; the fill walks through it and takes the
    next room with it.
  · an OPEN-PLAN space has no wall to stop at. The owner's lounge runs into his kitchen, so the
    "right" answer is genuinely ambiguous and the fill takes everything.
  · and a room whose fill is rejected keeps today's box, so a sheet ends up with a mixture of two
    kinds of box, which is worse than either kind alone.

WHAT WOULD HAVE TO CHANGE before this is worth another attempt: something that can tell a doorway
from an opening. That is the whole problem, it is what the learned parsers (CubiCasa5K, DeepFloorplan,
Raster2Seq) spend their model on, and none of them is licensed or small enough to ship here today.
A better OCR would NOT fix it - the captions are already found 73 % of the time and the failures
above are not caption failures.

WHAT SURVIVES AND IS WORTH KEEPING: exp-caption-anchor.py. Finding each room's printed name as a
PIXEL BOX on the page is a capability this repo did not have, it works, and it is the foundation any
future attempt starts from.
"""
import json, os, re, sys, importlib.util
from collections import deque

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
OUT = os.path.join(HERE, "out", "room-grow")

_spec = importlib.util.spec_from_file_location("ca", os.path.join(HERE, "exp-caption-anchor.py"))
ca = importlib.util.module_from_spec(_spec)
_argv, sys.argv = sys.argv, ["x"]
_spec.loader.exec_module(ca)
sys.argv = _argv

ARG = lambda n: next((a.split("=", 1)[1] for a in sys.argv[1:] if a.startswith("--%s=" % n)), None)
ONLY = ARG("only")
DRAW = "--draw" in sys.argv
NO_MASK = "--no-mask" in sys.argv

INK_DROP, SAT_MAX = 35, 40          # WallSnap.kt
LEAK_SHARE = 0.45                    # a fill bigger than this share of the home has gone through a door
MIN_FILL_SHARE = 0.25                # a fill this much smaller than today's box is trapped, not a room
# ⭐⭐ THE FILL IS JUDGED AGAINST THE SIZE THE PLAN PRINTS, NOT AGAINST THE READER'S BOX.
#
# The first guard tried was WallSnap's - keep the fill only if it is within 0.5x..1.6x of the
# reader's rectangle. It scored 64% against 66% for doing nothing, because it throws away exactly
# the fills worth having: the reader's box is the thing we do not trust, so a CORRECT fill is often
# half again its size, and the guard cannot tell that from a leak.
#
# The plan prints the answer. 85% of rooms across the corpus carry a printed size, transcribed at
# ~95%. A fill of a room 4.6 m wide implies a pixels-per-metre for the sheet; so does every other
# room's. The fills that AGREE with one another about the sheet's scale are the ones that found
# their own walls, and a fill that has run through a doorway disagrees with all of them at once.
# So: take the median implied scale over every anchored fill, then keep only the fills within
# SCALE_TOL of it. Nothing here uses a model rectangle.
SCALE_TOL = 0.35
TEXT_PAD = 2                         # pixels of margin when erasing a word
# How hard to seal a door opening, as a multiple of the sheet's own measured wall thickness.
CLOSE_K = float(next((a.split("=",1)[1] for a in __import__("sys").argv[1:]
                      if a.startswith("--close=")), 1.2))
# A fill stops at the INSIDE face of the wall. A room, as a person marks it and as the reader
# reports it, runs to the wall itself. WALL_OUT pushes each grown edge out by that share of the
# sheet's measured wall thickness, so the two describe the same thing.
WALL_OUT = float(next((a.split("=",1)[1] for a in __import__("sys").argv[1:]
                       if a.startswith("--wall-out=")), 1.0))


def planes(path):
    im = Image.open(path).convert("RGB")
    W, H = im.size
    px = im.load()
    luma = bytearray(W * H)
    sat = bytearray(W * H)
    hist = [0] * 256
    for y in range(H):
        base = y * W
        for x in range(W):
            r, g, b = px[x, y][:3]
            l = (r * 299 + g * 587 + b * 114) // 1000
            luma[base + x] = l
            sat[base + x] = max(r, g, b) - min(r, g, b)
            hist[l] += 1
    acc, target, white = 0, int(0.95 * W * H), 255
    for v in range(256):
        acc += hist[v]
        if acc >= target:
            white = v
            break
    return im, W, H, luma, sat, white


def ink_mask(W, H, luma, sat, white):
    lim = white - INK_DROP
    return bytearray(1 if (luma[i] < lim and sat[i] < SAT_MAX) else 0 for i in range(W * H))


def erase(mask, W, H, boxes):
    for x0, y0, x1, y1 in boxes:
        for y in range(max(0, int(y0) - TEXT_PAD), min(H, int(y1) + TEXT_PAD + 1)):
            b = y * W
            for x in range(max(0, int(x0) - TEXT_PAD), min(W, int(x1) + TEXT_PAD + 1)):
                mask[b + x] = 0


def wall_thickness(mask, W, H):
    runs = []
    step = max(1, H // 140)
    for y in range(int(H * 0.08), int(H * 0.92), step):
        b, run = y * W, 0
        for x in range(W):
            if mask[b + x]:
                run += 1
            else:
                if 2 <= run < W * 0.06:
                    runs.append(run)
                run = 0
    if not runs:
        return 3
    runs.sort()
    return max(2, min(30, runs[int(len(runs) * 0.75)]))


def close(mask, W, H, r):
    """dilate then erode, separably - seals door openings narrower than 2r."""
    def dil(src):
        a = bytearray(W * H)
        for y in range(H):
            b = y * W
            run = 0
            for x in range(W):
                if src[b + x]:
                    run = r + 1
                if run:
                    a[b + x] = 1
                    run -= 1
            run = 0
            for x in range(W - 1, -1, -1):
                if src[b + x]:
                    run = r + 1
                if run:
                    a[b + x] = 1
                    run -= 1
        out = bytearray(W * H)
        for x in range(W):
            run = 0
            for y in range(H):
                if a[y * W + x]:
                    run = r + 1
                if run:
                    out[y * W + x] = 1
                    run -= 1
            run = 0
            for y in range(H - 1, -1, -1):
                if a[y * W + x]:
                    run = r + 1
                if run:
                    out[y * W + x] = 1
                    run -= 1
        return out
    d = dil(mask)
    inv = bytearray(1 - v for v in d)
    e = dil(inv)
    return bytearray(1 - v for v in e)


def fill(mask, W, H, sx, sy, fr, cap):
    sx, sy = int(sx), int(sy)
    if not (fr[0] <= sx <= fr[2] and fr[1] <= sy <= fr[3]):
        return None
    i0 = sy * W + sx
    if mask[i0]:
        return None
    seen = bytearray(W * H)
    q = deque([i0])
    seen[i0] = 1
    area = 0
    x0 = x1 = sx
    y0 = y1 = sy
    while q:
        i = q.popleft()
        x = i % W
        y = i // W
        area += 1
        if x < x0: x0 = x
        if x > x1: x1 = x
        if y < y0: y0 = y
        if y > y1: y1 = y
        if area > cap:
            return {"leaked": True}
        if x > fr[0] and not mask[i - 1] and not seen[i - 1]: seen[i - 1] = 1; q.append(i - 1)
        if x < fr[2] and not mask[i + 1] and not seen[i + 1]: seen[i + 1] = 1; q.append(i + 1)
        if y > fr[1] and not mask[i - W] and not seen[i - W]: seen[i - W] = 1; q.append(i - W)
        if y < fr[3] and not mask[i + W] and not seen[i + W]: seen[i + W] = 1; q.append(i + W)
    return {"leaked": False, "area": area, "x0": x0, "y0": y0, "x1": x1, "y1": y1, "seen": seen}


PRINTED_M = re.compile(r"(\d+(?:\.\d+)?)\s*[Xx\u00d7]\s*(\d+(?:\.\d+)?)\s*m", re.I)


def printed_metres(size):
    """The metric pair a sheet prints beside a room - `4.6 X 3.3 m`. None when it prints none."""
    if not size:
        return None
    m = PRINTED_M.search(" ".join(str(size).split()))
    if not m:
        return None
    a, b = float(m.group(1)), float(m.group(2))
    return (a, b) if a > 0 and b > 0 else None


def implied_scale(px_w, px_h, metres):
    """Pixels per metre implied by this box being that room. Tried both ways round; the better wins."""
    a, b = metres
    best = None
    for pw, ph in ((a, b), (b, a)):
        if pw <= 0 or ph <= 0:
            continue
        sx, sy = px_w / pw, px_h / ph
        if sx <= 0 or sy <= 0:
            continue
        err = abs(sx - sy) / max(sx, sy)
        cand = (err, (sx + sy) / 2)
        if best is None or cand < best:
            best = cand
    return best[1] if best else None


def iou(a, b):
    w = min(a[2], b[2]) - max(a[0], b[0])
    h = min(a[3], b[3]) - max(a[1], b[1])
    if w <= 0 or h <= 0:
        return 0.0
    i = w * h
    return i / ((a[2] - a[0]) * (a[3] - a[1]) + (b[2] - b[0]) * (b[3] - b[1]) - i)


def run_sheet(stem, truth):
    img = ca.find_image(stem)
    rep = ca.find_reply(stem)
    if not img or not rep:
        return None
    reply = rep.get("reply") or rep
    rooms = reply.get("rooms") or []
    if not rooms:
        return None

    words, _ = ca.words_of(img)
    lines = ca.merge_stacked(ca.lines_of(words))
    im, W, H, luma, sat, white = planes(img)

    bld = ca.sane_building(reply.get("building"))
    if bld:
        lines = [ln for ln in lines
                 if (bld["x"] - .02) * W <= (ln["x0"] + ln["x1"]) / 2 <= (bld["x"] + bld["w"] + .02) * W
                 and (bld["y"] - .02) * H <= (ln["y0"] + ln["y1"]) / 2 <= (bld["y"] + bld["h"] + .02) * H]
    fr = ([int((bld["x"]) * W), int((bld["y"]) * H),
           min(W - 1, int((bld["x"] + bld["w"]) * W)), min(H - 1, int((bld["y"] + bld["h"]) * H))]
          if bld else [0, 0, W - 1, H - 1])
    cap = int((fr[2] - fr[0]) * (fr[3] - fr[1]) * LEAK_SHARE)

    # today's box, per room, in pixels
    boxes = []
    for r in rooms:
        p = ca.page_of(reply.get("building"), r)
        boxes.append([r["label"], p["x"] * W, p["y"] * H, (p["x"] + p["w"]) * W, (p["y"] + p["h"]) * H])

    # the anchor: the caption's own pixel centre, matched the way exp-caption-anchor does
    anchors, used = {}, set()
    for i, r in enumerate(rooms):
        bx = boxes[i]
        cx, cy = (bx[1] + bx[3]) / 2, (bx[2] + bx[4]) / 2
        cands = []
        for j, ln in enumerate(lines):
            s = ca.score(r["label"], ln)
            if s < 0.6:
                continue
            lx, ly = (ln["x0"] + ln["x1"]) / 2, (ln["y0"] + ln["y1"]) / 2
            inside = bx[1] <= lx <= bx[3] and bx[2] <= ly <= bx[4]
            cands.append((inside, round(s, 2), -((lx - cx) ** 2 + (ly - cy) ** 2) ** 0.5, j, lx, ly))
        cands.sort(reverse=True)
        pick = next((c for c in cands if c[3] not in used), None)
        if pick:
            used.add(pick[3])
            anchors[i] = (pick[4], pick[5])

    mask = ink_mask(W, H, luma, sat, white)
    if not NO_MASK:
        erase(mask, W, H, [(l["x0"], l["y0"], l["x1"], l["y1"]) for l in lines])
    t = wall_thickness(mask, W, H)
    mask = close(mask, W, H, max(1, int(round(t * CLOSE_K))))

    grown, anchored, leaked, cand = [], 0, 0, []
    for i, bx in enumerate(boxes):
        seeds = []
        if i in anchors:
            anchored += 1
            ax, ay = anchors[i]
            seeds = [(ax, ay), (ax, ay + t * 2), (ax, ay - t * 2), (ax - t * 3, ay), (ax + t * 3, ay)]
        else:
            w, h = bx[3] - bx[1], bx[4] - bx[2]
            seeds = [(bx[1] + w * fx, bx[2] + h * fy)
                     for fx in (0.3, 0.5, 0.7) for fy in (0.3, 0.5, 0.7)]
        best = None
        for sx, sy in seeds:
            f = fill(mask, W, H, sx, sy, fr, cap)
            if not f or f.get("leaked"):
                continue
            bad = False
            for j, (ax, ay) in anchors.items():
                if j == i:
                    continue
                if f["x0"] <= ax <= f["x1"] and f["y0"] <= ay <= f["y1"] and f["seen"][int(ay) * W + int(ax)]:
                    bad = True
                    break
            if bad:
                continue
            if best is None or f["area"] > best["area"]:
                best = f
        own = max(1.0, (bx[3] - bx[1]) * (bx[4] - bx[2]))
        if best and (best["x1"] - best["x0"]) * (best["y1"] - best["y0"]) >= own * MIN_FILL_SHARE:
            cand.append((i, best))
            o = t * WALL_OUT
            grown.append([bx[0], best["x0"] - o, best["y0"] - o, best["x1"] + o, best["y1"] + o, True])
        else:
            grown.append([bx[0], bx[1], bx[2], bx[3], bx[4], False])

    # ---- second pass: the fills that agree about the sheet's scale are the ones that are right ----
    scales = []
    for i, f in cand:
        m = printed_metres(rooms[i].get("size"))
        if not m:
            continue
        sc = implied_scale(f["x1"] - f["x0"], f["y1"] - f["y0"], m)
        if sc:
            scales.append(sc)
    med = sorted(scales)[len(scales) // 2] if scales else None
    for i, f in cand:
        m = printed_metres(rooms[i].get("size"))
        keep = True
        if med and m:
            sc = implied_scale(f["x1"] - f["x0"], f["y1"] - f["y0"], m)
            keep = bool(sc) and abs(sc - med) / med <= SCALE_TOL
        if not keep:
            bx = boxes[i]
            grown[i] = [bx[0], bx[1], bx[2], bx[3], bx[4], False]
    leaked = sum(1 for g in grown if not g[5])

    # score against the hand-marked rooms
    marks = truth.get(stem, [])
    used_m, r_iou, g_iou, n = set(), 0.0, 0.0, 0
    for m in marks:
        tb = (m["x"] * W, m["y"] * H, (m["x"] + m["w"]) * W, (m["y"] + m["h"]) * H)
        pick, bd = -1, None
        for i, bx in enumerate(boxes):
            if i in used_m or ca.norm(bx[0]) != ca.norm(m["label"]):
                continue
            d = ((bx[1] + bx[3]) / 2 - (tb[0] + tb[2]) / 2) ** 2 + ((bx[2] + bx[4]) / 2 - (tb[1] + tb[3]) / 2) ** 2
            if bd is None or d < bd:
                bd, pick = d, i
        if pick < 0:
            continue
        used_m.add(pick)
        r_iou += iou(tb, tuple(boxes[pick][1:5]))
        g_iou += iou(tb, tuple(grown[pick][1:5]))
        n += 1

    if DRAW:
        os.makedirs(OUT, exist_ok=True)
        d = ImageDraw.Draw(im)
        for bx in boxes:
            d.rectangle(bx[1:5], outline=(211, 47, 47), width=2)
        for g in grown:
            d.rectangle(g[1:5], outline=(27, 138, 58) if g[5] else (150, 150, 150), width=3)
        for m in marks:
            d.rectangle([m["x"] * W, m["y"] * H, (m["x"] + m["w"]) * W, (m["y"] + m["h"]) * H],
                        outline=(21, 101, 192), width=2)
        for (ax, ay) in anchors.values():
            d.ellipse([ax - 4, ay - 4, ax + 4, ay + 4], fill=(255, 140, 0))
        im.save(os.path.join(OUT, stem + ".png"))

    # ⭐ THE OWNER'S OWN COMPLAINT, counted: a box drawn over a room it does not name. Average fit
    # is one measure; this is the one he can see on the screen.
    def swallows(bxs):
        c = 0
        for a in bxs:
            for b in bxs:
                if a is b:
                    continue
                ar = max(1.0, (b[3] - b[1]) * (b[4] - b[2]))
                w = min(a[3], b[3]) - max(a[1], b[1])
                h = min(a[4], b[4]) - max(a[2], b[2])
                if w > 0 and h > 0 and (w * h) / ar > 0.40:
                    c += 1
        return c

    return {"rooms": len(rooms), "anchored": anchored, "fellback": leaked,
            "read": 100 * r_iou / n if n else 0, "grown": 100 * g_iou / n if n else 0, "n": n,
            "sw_read": swallows(boxes), "sw_grown": swallows([g[:5] for g in grown])}


def main():
    truth = json.load(open(os.path.join(HERE, "truth-rooms.json"), encoding="utf-8"))
    stems = [ONLY] if ONLY else [k for k in truth if k != "_README"]
    print("%-42s %6s %9s %9s %20s %16s" % ("sheet", "rooms", "anchored", "fell back",
                                            "IoU  today -> grown", "drawn-over"))
    print("-" * 108)
    tot_r = tot_g = tot_n = tot_sr = tot_sg = 0
    for stem in stems:
        r = run_sheet(stem, truth)
        if not r:
            print("%-42s (skipped)" % stem[:42])
            continue
        print("%-42s %6d %8d %9d %12.0f%% -> %3.0f%%   %8d -> %d"
              % (stem[:42], r["rooms"], r["anchored"], r["fellback"], r["read"], r["grown"],
                 r["sw_read"], r["sw_grown"]))
        tot_r += r["read"] * r["n"]; tot_g += r["grown"] * r["n"]; tot_n += r["n"]
        tot_sr += r["sw_read"]; tot_sg += r["sw_grown"]
    if tot_n:
        print("-" * 92)
        print("%-42s %26s %12.0f%% -> %3.0f%%   %8d -> %d"
              % ("TOTAL", "", tot_r / tot_n, tot_g / tot_n, tot_sr, tot_sg))
    if DRAW:
        print("\noverlays in", OUT, "- red = today, green = grown, grey = fell back, blue = truth, orange dot = caption anchor")


if __name__ == "__main__":
    sys.exit(main() or 0)

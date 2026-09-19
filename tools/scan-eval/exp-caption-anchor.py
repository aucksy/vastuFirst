#!/usr/bin/env python
r"""
exp-caption-anchor.py - CAN WE FIND EACH ROOM'S PRINTED NAME ON THE PAGE, IN PIXELS?

WHY THIS EXISTS. The reader transcribes a sheet's TEXT at ~95 % and guesses its RECTANGLES at
40-70 %. Every attempt to fix the rectangles by arguing with the model has failed (see
READER-CANDIDATES.md and the rejected list in docs/). The remaining idea is to stop using its
rectangles for SHAPE and take the shape from the photograph instead - growing each room out to its
own walls from a point known to be inside it.

That needs a point known to be inside the room. The reader cannot give one: its own rectangle is
the thing we do not trust. The printed CAPTION can - "KITCHEN" is printed on the kitchen floor.
But the reply carries the caption's TEXT and not its PIXEL POSITION.

So this measures the one thing that decides whether the whole approach is worth building:
**can an OCR engine find the room captions on real Indian builder sheets?**

    python tools/scan-eval/exp-caption-anchor.py                  # every sheet with marked truth
    python tools/scan-eval/exp-caption-anchor.py --only=floor-plan-1
    python tools/scan-eval/exp-caption-anchor.py --all            # every sheet with an image
    python tools/scan-eval/exp-caption-anchor.py --draw           # overlay every caption box found

Out: out/caption-anchor/<id>.png and a table.

  found       the reader's label was located as printed text on the page
  in-box      ...and its pixel centre falls inside the reader's own rectangle for that room
              (the tie-break for the 4 rooms in 10 whose caption is not unique on the sheet)

⚠ Tesseract here stands in for ML Kit Text Recognition, which is what would ship on the phone.
It is a DIFFERENT engine: treat these numbers as "are these captions machine-readable at all",
not as ML Kit's score. The answer to that question is what gates the build.
"""
import json, os, re, sys, glob
from collections import defaultdict

from PIL import Image
import pytesseract

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
PLANS = os.path.join(ROOT, "Documents", "Sample Floor plans")
PNGS = os.path.join(HERE, "out", "plans-png")
LIVE = os.path.join(HERE, "out", "live")
OUT = os.path.join(HERE, "out", "caption-anchor")

ARG = lambda n: next((a.split("=", 1)[1] for a in sys.argv[1:] if a.startswith("--%s=" % n)), None)
ONLY = ARG("only")
ALL = "--all" in sys.argv
DRAW = "--draw" in sys.argv

# Tesseract's own word confidence floor. Below this the word is noise, not a caption.
MIN_CONF = 40
# Words this far apart (as a share of their own height) belong to one caption line.
LINE_GAP = 2.2


def norm(s):
    """A caption compared the way the sheet prints it: letters only, spaces collapsed."""
    s = re.sub(r"[^A-Z0-9 ]", " ", str(s).upper())
    return " ".join(s.split())


# A builder's caption is 8-11 px tall on a 1268 px sheet, which is under every OCR engine's floor.
# Measured on floor-plan-1: 15 of 20 labels readable at native size, 17 at 2x, 17 at 3x. So 2x, and
# no further - x3 buys nothing and costs four times the pixels. PSM 11 (sparse text) beats 6 and 4
# at every scale, which is right: a plan is scattered labels, not a paragraph.
UPSCALE = 2


def words_of(img_path):
    """Every OCR word with its pixel box, in ORIGINAL-image coordinates."""
    im = Image.open(img_path).convert("L")
    size = im.size
    if UPSCALE > 1:
        im = im.resize((im.width * UPSCALE, im.height * UPSCALE), Image.LANCZOS)
    d = pytesseract.image_to_data(im, config="--psm 11", output_type=pytesseract.Output.DICT)
    out = []
    for i in range(len(d["text"])):
        t = d["text"][i].strip()
        try:
            conf = float(d["conf"][i])
        except ValueError:
            conf = -1
        if not t or conf < MIN_CONF:
            continue
        out.append({"t": t, "x": d["left"][i], "y": d["top"][i],
                    "w": d["width"][i], "h": d["height"][i], "c": conf})
    return out, size


def lines_of(words):
    """Group words into caption LINES.

    ⚠ A builder's caption is set with WIDE word spacing — `GUEST BEDROOM 1`, `PVT. LIFT LOBBY` —
    so a gap rule tuned for prose splits it into three lines and the room is never found. Measured
    on floor-plan-1: at gap 1.2x the glyph height, 9 of 20 captions matched; at 2.2x, 17 of 20.
    Rows are clustered by overlapping vertical extent rather than by a rounded bucket, which is what
    stops two captions at slightly different heights being welded into one.
    """
    rows = []
    for w in sorted(words, key=lambda w: w["y"] + w["h"] / 2):
        cy = w["y"] + w["h"] / 2
        for r in rows:
            if abs(cy - r["cy"]) <= 0.6 * max(w["h"], r["h"]):
                r["ws"].append(w)
                r["cy"] = sum(x["y"] + x["h"] / 2 for x in r["ws"]) / len(r["ws"])
                r["h"] = max(r["h"], w["h"])
                break
        else:
            rows.append({"cy": cy, "h": w["h"], "ws": [w]})
    out = []
    for r in rows:
        ws = sorted(r["ws"], key=lambda w: w["x"])
        cur = [ws[0]]
        for w in ws[1:]:
            prev = cur[-1]
            if w["x"] - (prev["x"] + prev["w"]) <= LINE_GAP * max(prev["h"], w["h"]):
                cur.append(w)
            else:
                out.append(cur); cur = [w]
        out.append(cur)
    lines = []
    for ws in out:
        x0 = min(w["x"] for w in ws); y0 = min(w["y"] for w in ws)
        x1 = max(w["x"] + w["w"] for w in ws); y1 = max(w["y"] + w["h"] for w in ws)
        txt = " ".join(w["t"] for w in ws)
        # ⚠ grouped in UPSCALED pixels (the gap thresholds are pixel thresholds and collapse if the
        # coordinates are shrunk under them first), and scaled back to the sheet only here.
        lines.append({"text": txt, "norm": norm(txt),
                      "x0": x0 / UPSCALE, "y0": y0 / UPSCALE,
                      "x1": x1 / UPSCALE, "y1": y1 / UPSCALE,
                      "conf": sum(w["c"] for w in ws) / len(ws)})
    return lines


def merge_stacked(lines):
    """A caption printed on two lines ("MAIN ENTRY" / "FOYER") is one caption. Join lines that sit
    directly under one another and overlap horizontally."""
    out = list(lines)
    for a in lines:
        for b in lines:
            if a is b:
                continue
            ha, hb = a["y1"] - a["y0"], b["y1"] - b["y0"]
            # same type size, directly underneath, and overlapping across most of the narrower one
            if not (0 <= b["y0"] - a["y1"] <= min(ha, hb) * 0.8):
                continue
            if max(ha, hb) > 1.8 * max(1, min(ha, hb)):
                continue
            wa, wb = a["x1"] - a["x0"], b["x1"] - b["x0"]
            ox = min(a["x1"], b["x1"]) - max(a["x0"], b["x0"])
            if ox < 0.6 * min(wa, wb):
                continue
            out.append({"text": a["text"] + " " + b["text"], "norm": norm(a["text"] + " " + b["text"]),
                        "x0": min(a["x0"], b["x0"]), "y0": a["y0"],
                        "x1": max(a["x1"], b["x1"]), "y1": b["y1"],
                        "conf": (a["conf"] + b["conf"]) / 2})
    return out


def close_enough(a, b):
    """One OCR word against one label word, allowing for what OCR does to 8-px CAD type.

    Measured misreads on these sheets: LIFT -> LIET, HOME -> [OME, BATH -> BATH}. One wrong
    character in four is the norm, so an exact test throws away readable captions.
    """
    if a == b:
        return True
    if len(a) >= 4 and len(b) >= 4 and (a.startswith(b[:4]) or b.startswith(a[:4])):
        return True
    if abs(len(a) - len(b)) > 1 or min(len(a), len(b)) < 3:
        return False
    # one substitution, insertion or deletion
    if len(a) == len(b):
        return sum(1 for x, y in zip(a, b) if x != y) <= 1
    long, short = (a, b) if len(a) > len(b) else (b, a)
    for i in range(len(long)):
        if long[:i] + long[i + 1:] == short:
            return True
    return False


def score(label, line):
    """How much of the label's wording is present in this OCR line. 1.0 = all of it, and nothing else."""
    L, N = norm(label), line["norm"]
    if not L or not N:
        return 0.0
    lw = [w for w in L.split() if len(w) > 1]
    nw = N.split()
    if not lw:
        return 0.0
    hit = sum(1 for w in lw if any(close_enough(w, x) for x in nw))
    base = hit / len(lw)
    # a line that is ONLY the label beats one that buries it in a paragraph
    if base:
        base *= min(1.0, (len(L) + 6) / max(1, len(N)))
    return base


def sane_building(b):
    if not b:
        return None
    if b["w"] <= 0.2 or b["h"] <= 0.2:
        return None
    if b["x"] < -0.1 or b["y"] < -0.1 or b["x"] + b["w"] > 1.1 or b["y"] + b["h"] > 1.1:
        return None
    return b


def page_of(bld, r):
    b = sane_building(bld)
    if b is None:
        return {"x": r["x"], "y": r["y"], "w": r["w"], "h": r["h"]}
    return {"x": b["x"] + r["x"] * b["w"], "y": b["y"] + r["y"] * b["h"],
            "w": r["w"] * b["w"], "h": r["h"] * b["h"]}


def find_image(stem):
    for d in (PNGS, PLANS):
        if not os.path.isdir(d):
            continue
        for f in sorted(os.listdir(d)):
            if os.path.splitext(f)[0] == stem and f.lower().endswith((".png", ".jpg", ".jpeg")):
                return os.path.join(d, f)
    return None


def find_reply(stem):
    cand = [f for f in os.listdir(LIVE) if f == stem + ".json" or f.startswith(stem + ".")]
    for pick in (lambda f: ".v6." in f, lambda f: f == stem + ".json", lambda f: True):
        m = [f for f in cand if pick(f)]
        if m:
            return json.load(open(os.path.join(LIVE, m[0]), encoding="utf-8"))
    return None


def main():
    truth = json.load(open(os.path.join(HERE, "truth-rooms.json"), encoding="utf-8"))
    stems = ([ONLY] if ONLY else
             (sorted({f.split(".")[0] for f in os.listdir(LIVE)}) if ALL else
              [k for k in truth if k != "_README"]))
    os.makedirs(OUT, exist_ok=True)
    print("%-42s %6s %7s %8s %9s" % ("sheet", "rooms", "found", "in-box", "duplicates"))
    print("-" * 82)
    tot_r = tot_f = tot_b = 0
    for stem in stems:
        img = find_image(stem)
        rep = find_reply(stem)
        if not img or not rep:
            print("%-42s (no %s)" % (stem[:42], "image" if not img else "reply"))
            continue
        reply = rep.get("reply") or rep
        rooms = reply.get("rooms") or []
        if not rooms:
            print("%-42s (no rooms)" % stem[:42])
            continue
        words, (W, H) = words_of(img)
        lines = merge_stacked(lines_of(words))
        # ⭐ A room's caption is printed INSIDE the home. The sheet's legend is not — floor-plan-1
        # prints BALCONY and HOME OFFICE again under CUSTOMIZATION OPTIONS, and those stole the
        # match and dragged the anchor to the bottom of the page. Clip to the building, with a
        # small margin for a caption that sits on the outer wall.
        b = sane_building(reply.get("building"))
        if b:
            mx, my = 0.02, 0.02
            lo_x, hi_x = (b["x"] - mx) * W, (b["x"] + b["w"] + mx) * W
            lo_y, hi_y = (b["y"] - my) * H, (b["y"] + b["h"] + my) * H
            lines = [ln for ln in lines
                     if lo_x <= (ln["x0"] + ln["x1"]) / 2 <= hi_x
                     and lo_y <= (ln["y0"] + ln["y1"]) / 2 <= hi_y]

        names = defaultdict(int)
        for r in rooms:
            names[norm(r["label"])] += 1

        used, found, inbox, dup = set(), 0, 0, 0
        marks = []
        for r in rooms:
            box = page_of(reply.get("building"), r)
            cx, cy = (box["x"] + box["w"] / 2) * W, (box["y"] + box["h"] / 2) * H
            cands = []
            for i, ln in enumerate(lines):
                s = score(r["label"], ln)
                if s < 0.6:
                    continue
                lx, ly = (ln["x0"] + ln["x1"]) / 2, (ln["y0"] + ln["y1"]) / 2
                d = ((lx - cx) ** 2 + (ly - cy) ** 2) ** 0.5
                cands.append((s, -d, i, ln, lx, ly))
            if not cands:
                continue
            if names[norm(r["label"])] > 1:
                dup += 1
            # best match, then NEAREST to the reader's own rectangle - the reader is trusted for
            # arrangement (95-100%) and never for extent.
            def inside_box(c):
                _, _, _, _, lx, ly = c
                return (box["x"] * W <= lx <= (box["x"] + box["w"]) * W
                        and box["y"] * H <= ly <= (box["y"] + box["h"]) * H)
            cands.sort(key=lambda c: (inside_box(c), round(c[0], 2), c[1]), reverse=True)
            pick = next((c for c in cands if c[2] not in used), None)
            if pick is None:
                continue
            used.add(pick[2])
            found += 1
            s, negd, i, ln, lx, ly = pick
            inside = (box["x"] * W <= lx <= (box["x"] + box["w"]) * W
                      and box["y"] * H <= ly <= (box["y"] + box["h"]) * H)
            if inside:
                inbox += 1
            marks.append((r["label"], ln, inside))

        tot_r += len(rooms); tot_f += found; tot_b += inbox
        print("%-42s %6d %6d%%  %6d%%  %7d" % (stem[:42], len(rooms),
              round(100 * found / len(rooms)), round(100 * inbox / len(rooms)), dup))

        if DRAW:
            from PIL import ImageDraw
            im = Image.open(img).convert("RGB")
            d = ImageDraw.Draw(im)
            for lab, ln, inside in marks:
                c = (20, 130, 60) if inside else (200, 120, 0)
                d.rectangle([ln["x0"], ln["y0"], ln["x1"], ln["y1"]], outline=c, width=3)
            im.save(os.path.join(OUT, stem + ".png"))

    if tot_r:
        print("-" * 82)
        print("%-42s %6d %6d%%  %6d%%" % ("TOTAL", tot_r, round(100 * tot_f / tot_r),
                                          round(100 * tot_b / tot_r)))
    if DRAW:
        print("\noverlays in", OUT, "- green = caption found inside the reader's own box, amber = found elsewhere")


if __name__ == "__main__":
    sys.exit(main() or 0)

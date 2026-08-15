#!/usr/bin/env python
r"""
EVERY WAY OF PLACING THE REVIEW BOX, drawn over the real plan and scored against it. FREE, scans
nothing.

⭐ WHAT THIS SETTLED, so nobody spends the day again. The owner photographed "Check what we read",
tapped a toilet and said the highlight did not match the room. Two hypotheses were tested here and
BOTH WERE WRONG:

  1. "The placement is the fault — take the position from the GRID, which already places his ten
     rooms with no overlap, and the size from the caption." Every version of it (`cell`, `hold`,
     `agree`, `guard` below) scores BETTER on overlap and WORSE on the rooms themselves. Against
     hand-marked rooms on his two sheets it loses 3 to 9 points of fit. The grid quantises to whole
     cells and trims for collisions, so its centre is not the room's centre. DO NOT RETRY IT.
  2. "The size correction works." It did not. It was the fault. See `exp-truth-boxes.py` — the
     caption-shaped box covered his toilet 56 % and Green Court's kitchen 51 %; the reader's own
     rectangle covers them 75 % and 95 %. Removed from the app on 15 Aug 2026; `caption` below is
     kept only so the comparison can be re-run.

The lesson the numbers taught, in one line: **boxes that stop overlapping EACH OTHER have not moved
onto their ROOMS.** Overlap and off-building are box-versus-box; only `exp-truth-boxes.py` asks about
the room. Prefer it for any judgement, and look at the picture before believing either.

    node tools/scan-eval/render-grid.mjs --only=X --live   # grid geometry for every recorded read
    python tools/scan-eval/exp-grid-boxes.py "<stem>"      # draw two variants over one plan
    python tools/scan-eval/exp-grid-boxes.py "<stem>" --show=caption,read,agree
    python tools/scan-eval/exp-grid-boxes.py --all         # box-versus-box numbers, whole corpus

Writes out/grid-boxes/<stem>.png, one panel per variant, plus the grid cells for reference.

How a grid cell becomes a place on the sheet, which is the one genuinely fiddly piece: the grid is
laid over `frame` — the bounding box of the reader's own rooms, in building coordinates — at
preCols x preRows cells, and the edge collapse then subtracted offC/offR.

    frame_x = frame.x + (col + offC) / preCols * frame.w      (and the same for rows)
    page_x  = building.x + frame_x * building.w               (ScanMapper.pageSource)
"""
import json, os, re, sys
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
LIVE = os.path.join(HERE, "out", "live")
GEOM = os.path.join(HERE, "out", "render")
PLANS = os.path.join(ROOT, "Documents", "Sample Floor plans")
OUTDIR = os.path.join(HERE, "out", "grid-boxes")

# MIRRORS ScanMapper. If these move there, move them here.
MIN_PRINTED_TO_FIT = 3
MAX_PRINTED_DISAGREEMENT = 4.0

# The ways of choosing WHERE the printed-size box goes. Every one draws the same size; they differ
# only in the centre they hold, which is the whole of the fault being fixed.
VARIANTS = {
    "caption": "the caption's size on the reader's centre (shipped 10-15 Aug, REMOVED)",
    "read": "TODAY - the reader's own rectangle, drawn as read",
    "readtight": "the reader's rectangle less the fixed margin it adds to every wall",
    "orient": "printed size, turned the way the READER drew the room",
    "cell": "on the centre of the room's grid cells",
    "cellclamp": "grid centre, slid inside the building",
    "shaped": "grid centre BEFORE the overlap trim",
    "hold": "the reader's centre, held inside its own grid cells",
    "holdclamp": "held inside its own grid cells, then inside the building",
    "agree": "the smallest move that makes the box agree with its own grid cells",
    "guard": "NEW - the same, only where the grid drew the room in its printed shape",
}
COLOUR = {"caption": (230, 120, 20), "read": (20, 150, 60), "readtight": (0, 140, 170),
          "orient": (190, 30, 90), "cell": (150, 60, 170), "cellclamp": (0, 140, 170),
          "shaped": (190, 30, 90), "hold": (120, 140, 40), "holdclamp": (0, 140, 170),
          "agree": (120, 140, 40), "guard": (60, 60, 60)}
SHOW = ("caption", "read")

# How far a room's CELLS may disagree in shape with the size its caption prints before the grid is
# treated as having no opinion about where that room is. Swept over the corpus: see --sweep.
GRID_SHAPE_TOLERANCE = float(os.environ.get("GRID_SHAPE_TOLERANCE", "2.0"))

# The margin the reader adds to every wall, in millimetres, from the 330-dimension study quoted in
# ScanMapper.tightenToPrinted. Set to 0 to turn the trim off; sweep it with the environment variable.
READER_MARGIN_MM = float(os.environ.get("READER_MARGIN_MM", "127"))


def trust_cell(cell, ext):
    """Did the grid draw this room in roughly the shape its own caption prints?

    Both sides are measured on the SHEET, not in cells, because a cell is not square — the grid is
    stretched onto the home's own proportions, so comparing cell counts would compare two different
    units and call a correct room wrong.
    """
    cw, ch = cell[2], cell[3]
    if cw <= 0 or ch <= 0 or ext[0] <= 0 or ext[1] <= 0:
        return False
    d = (cw / ch) / (ext[0] / ext[1])
    return 1.0 / GRID_SHAPE_TOLERANCE <= d <= GRID_SHAPE_TOLERANCE

# What every variant is compared AGAINST: the drawing this experiment set out to replace.
BASELINE = "caption"

# The reader the app actually ships. The corpus also holds reads from four cheaper models that were
# tried and not chosen, and their rectangles are much worse — judging a change to the DRAWING on
# replies the app will never receive lets a model we rejected outvote the one we sell.
SHIPPED = "openai_gpt-5.6-luna"

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


def off_building_share(bs, building):
    """Box area sitting outside the building outline. A room cannot be off its own building."""
    total = sum(b["w"] * b["h"] for b in bs) or 1.0
    out = 0.0
    for b in bs:
        dx = max(0.0, min(b["x"] + b["w"], building["x"] + building["w"]) - max(b["x"], building["x"]))
        dy = max(0.0, min(b["y"] + b["h"], building["y"] + building["h"]) - max(b["y"], building["y"]))
        out += max(0.0, b["w"] * b["h"] - dx * dy)
    return 100.0 * out / total


def worst_swallow(bs):
    """The single worst collision: how much of one room's box is eaten by ONE other room's box.

    This is the number the owner photographed — his toilet 39 % inside living/dining. A share of the
    TOTAL area can look small while one small room is nearly gone, and it is the small room the user
    taps to check.
    """
    worst, who = 0.0, ""
    for i in range(len(bs)):
        a = bs[i]
        area = a["w"] * a["h"]
        if area <= 0:
            continue
        for j in range(len(bs)):
            if i == j:
                continue
            c = bs[j]
            dx = min(a["x"] + a["w"], c["x"] + c["w"]) - max(a["x"], c["x"])
            dy = min(a["y"] + a["h"], c["y"] + c["h"]) - max(a["y"], c["y"])
            if dx > 0 and dy > 0 and (dx * dy) / area > worst:
                worst = (dx * dy) / area
                who = "%s %.0f%% inside %s" % (a["label"][:16], 100 * worst, c["label"][:16])
    return 100.0 * worst, who


def geom_of(stem):
    p = os.path.join(GEOM, stem + ".geom.json")
    if not os.path.exists(p):
        return None
    g = json.load(open(p, encoding="utf-8"))
    return g if g.get("kind") == "placed" and g.get("frame") else None


def boxes_for(g):
    """Today's boxes and the grid-placed ones, for one plan, in page (sheet) coordinates.

    Returns (today, grid, building) — each a list aligned with g["rooms"].
    """
    b = g.get("building") or {"x": 0.0, "y": 0.0, "w": 1.0, "h": 1.0}
    # ScanMapper.pageSource sanity: a mad building box is ignored and the room passes through.
    sane = (b["w"] > 0.05 and b["h"] > 0.05 and b["w"] <= 1.0 and b["h"] <= 1.0
            and b["x"] >= -0.02 and b["y"] >= -0.02
            and b["x"] + b["w"] <= 1.05 and b["y"] + b["h"] <= 1.05)
    if not sane:
        b = {"x": 0.0, "y": 0.0, "w": 1.0, "h": 1.0}

    page = []
    for r in g["rooms"]:
        rd = r["read"]
        page.append({
            "label": r["label"], "printed": r.get("printed"),
            "x": b["x"] + rd["x"] * b["w"], "y": b["y"] + rd["y"] * b["h"],
            "w": rd["w"] * b["w"], "h": rd["h"] * b["h"],
        })

    # ScanMapper.tightenToPrinted — one median scale over every sized room, fitted long-against-long.
    ratios = []
    for p in page:
        mm = p["printed"]
        if not mm or p["w"] <= 0 or p["h"] <= 0:
            continue
        ratios.append(max(p["w"], p["h"]) / max(mm["w"], mm["h"]))
        ratios.append(min(p["w"], p["h"]) / min(mm["w"], mm["h"]))
    scale = median(ratios) if len(ratios) >= MIN_PRINTED_TO_FIT * 2 else None

    def printed_extent(p):
        """The size the caption prints, at the sheet's own scale — or None to keep the read size."""
        mm = p["printed"]
        if scale is None or not mm:
            return None
        w, h = min(mm["w"] * scale, 1.0), min(mm["h"] * scale, 1.0)   # printed order: width first
        if w <= 0 or h <= 0:
            return None
        grew = (w * h) / (p["w"] * p["h"])
        if grew > MAX_PRINTED_DISAGREEMENT or grew < 1.0 / MAX_PRINTED_DISAGREEMENT:
            return None                                               # caption mis-read: keep the read
        return w, h

    fr, pc, pr = g["frame"], g["preCols"] or g["cols"], g["preRows"] or g["rows"]

    def on_page(rc):
        """A grid rectangle, in cells, read back as a rectangle on the sheet.

        The grid is laid over `frame` at preCols x preRows and the edge collapse then subtracted
        offC/offR; undoing both is what makes a cell mean a place again. `frame` itself is in
        BUILDING coordinates, so it composes onto the page exactly as a room does.
        """
        x = b["x"] + (fr["x"] + (rc["col"] + g["offC"]) / pc * fr["w"]) * b["w"]
        y = b["y"] + (fr["y"] + (rc["row"] + g["offR"]) / pr * fr["h"]) * b["h"]
        return (x, y, rc["w"] / pc * fr["w"] * b["w"], rc["h"] / pr * fr["h"] * b["h"])

    def clamp_in(x, y, w, h, box):
        """Slide a box inside `box` without resizing it. Centred if it is simply too big to fit."""
        x = min(max(x, box["x"]), box["x"] + box["w"] - w) if w <= box["w"] \
            else box["x"] + (box["w"] - w) / 2
        y = min(max(y, box["y"]), box["y"] + box["h"] - h) if h <= box["h"] \
            else box["y"] + (box["h"] - h) / 2
        return x, y

    def agree_with(x, y, w, h, box):
        """The SMALLEST move that makes a box and its cells agree — and no move at all if they
        already do.

        Two cases, and the second is the one that matters. Where the box fits inside the cells, it
        is slid inside them. Where it is bigger than the cells — which happens constantly, because
        the overlap resolver TRIMS a room's cells and the printed size does not shrink with them —
        it is slid until it CONTAINS them instead. Centring it in the trimmed cells was the first
        attempt and it is a worse rule: it throws away a centre that was fine and moves the box by
        half of whatever the trimmer happened to take.
        """
        def axis(v, size, lo, span):
            a, bb = (lo, lo + span - size) if size <= span else (lo + span - size, lo)
            return min(max(v, a), bb)
        return (axis(x, w, box["x"], box["w"]), axis(y, h, box["y"], box["h"]))

    out = {k: [] for k in VARIANTS}
    for p, r in zip(page, g["rooms"]):
        ext = printed_extent(p)
        w, h = ext if ext else (p["w"], p["h"])
        cell = on_page(r["rect"])
        shaped = on_page(r["shaped"] or r["rect"])
        # `read` is the reader's own page rectangle, kept so a scorer can decide WHICH real room a
        # drawing is talking about using something identical across every drawing being compared.
        common = {**p, "w": w, "h": h, "cell": cell,
                  "read": (p["x"], p["y"], p["w"], p["h"])}
        cellbox = {"x": cell[0], "y": cell[1], "w": cell[2], "h": cell[3]}
        rx, ry = p["x"] + p["w"] / 2 - w / 2, p["y"] + p["h"] / 2 - h / 2   # the reader's own centre

        # today — the printed size held on the reader's centre, exactly what the app draws now.
        out["caption"].append({**common, "x": rx, "y": ry})
        # read — no correction whatsoever: the reader's rectangle composed onto the page. This is
        # the control. Without it there is no way to tell whether the size pass helps a room.
        out["read"].append({**common, "x": p["x"], "y": p["y"], "w": p["w"], "h": p["h"]})
        # readtight — the same, less the CONSTANT margin the reader adds to every wall. Measured
        # over 330 room dimensions: about five inches per wall whatever the wall's length, which is
        # why it disfigures small rooms and barely touches big ones. Taken off in real units through
        # the sheet's own scale, so it means the same thing on every plan.
        m = (READER_MARGIN_MM * scale) if scale else 0.0
        tw, th = max(p["w"] - 2 * m, p["w"] * 0.3), max(p["h"] - 2 * m, p["h"] * 0.3)
        out["readtight"].append({**common, "w": tw, "h": th,
                                 "x": p["x"] + p["w"] / 2 - tw / 2,
                                 "y": p["y"] + p["h"] / 2 - th / 2})
        # orient — ⭐ the caption's two NUMBERS, but the reader's own sense of which way the room
        # runs. The app takes the order from the caption ("the first number is the width"), which is
        # right on most rooms and inverts the ones whose sheet prints depth first.
        if ext:
            lo, hi = min(ext), max(ext)
            ow, oh = (hi, lo) if p["w"] >= p["h"] else (lo, hi)
        else:
            ow, oh = p["w"], p["h"]
        out["orient"].append({**common, "w": ow, "h": oh,
                              "x": p["x"] + p["w"] / 2 - ow / 2,
                              "y": p["y"] + p["h"] / 2 - oh / 2})
        # cell — the same size on the centre of the cells the grid gave the room.
        out["cell"].append({**common, "x": cell[0] + cell[2] / 2 - w / 2,
                            "y": cell[1] + cell[3] / 2 - h / 2})
        # cellclamp — the same, then slid inside the building outline the reader gives us.
        cx, cy = clamp_in(cell[0] + cell[2] / 2 - w / 2, cell[1] + cell[3] / 2 - h / 2, w, h, b)
        out["cellclamp"].append({**common, "x": cx, "y": cy})
        # shaped — the room's cells BEFORE the overlap trim cut it, which is where the plan put it.
        sx, sy = clamp_in(shaped[0] + shaped[2] / 2 - w / 2, shaped[1] + shaped[3] / 2 - h / 2, w, h, b)
        out["shaped"].append({**common, "x": sx, "y": sy})
        # hold — ⭐ keep the reader's own centre, but never let the box leave its own grid cells.
        # The reader's centre is precise when it is right and gross when it is wrong; the cells say
        # which region the room is in. So correct only what is outside the cells and touch nothing
        # that is already inside them.
        hx, hy = clamp_in(rx, ry, w, h, cellbox)
        out["hold"].append({**common, "x": hx, "y": hy})
        # holdclamp — the same, then slid inside the building. A box can be pushed off the sheet by
        # the cell clamp alone when its printed size is larger than the cells it was given, and a
        # box on blank paper is the one error every person spots instantly.
        hx, hy = clamp_in(hx, hy, w, h, b)
        out["holdclamp"].append({**common, "x": hx, "y": hy})
        # agree — the smallest move that makes the box and its own cells agree, then inside the
        # building. Nothing moves at all where the reader was already right.
        ax, ay = agree_with(rx, ry, w, h, cellbox)
        ax, ay = clamp_in(ax, ay, w, h, b)
        out["agree"].append({**common, "x": ax, "y": ay})
        # guard — ⭐ the same, but ONLY where the grid drew the room in the shape its own caption
        # prints. The grid is not uniformly good: where a room could not be fitted honestly it gets
        # a sliver of cells (a lobby printed almost square came out one cell wide and five tall),
        # and dragging the box onto a sliver is a bigger lie than the centre it replaced. A cell
        # rectangle that disagrees with the caption is not evidence of position either, so where
        # that happens nothing moves and the box stays exactly where the app draws it today.
        ok = ext is not None and trust_cell(cell, ext)
        gx, gy = (ax, ay) if ok else clamp_in(rx, ry, w, h, b)
        out["guard"].append({**common, "x": gx, "y": gy, "moved": ok})
    return out, b


def score(stem):
    g = geom_of(stem)
    if not g:
        return None
    sets, b = boxes_for(g)
    row = {"stem": stem, "rooms": len(sets[BASELINE])}
    for k, bs in sets.items():
        row["ov_" + k] = overlap_share(bs)
        row["off_" + k] = off_building_share(bs, b)
        row["sw_" + k] = worst_swallow(bs)[0]
    return row


def font(size):
    for f in (r"C:\Windows\Fonts\arialbd.ttf", r"C:\Windows\Fonts\arial.ttf"):
        try:
            return ImageFont.truetype(f, size)
        except Exception:
            pass
    return ImageFont.load_default()


def draw_panel(plan, boxes, title, colour, crop, key="box"):
    """One plan with one set of boxes drawn over it, TRANSLUCENT so overlaps are visible as they are.

    An outline-only drawing hid the very thing being judged: two boxes crossing looked identical to
    one box in the right place. A wash of colour makes a collision dark and a gap white.
    """
    im = Image.open(plan).convert("RGB")
    W, H = im.size
    wash = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    g = ImageDraw.Draw(wash)
    for q in boxes:
        x, y, w, h = (q["cell"] if key == "cell" else (q["x"], q["y"], q["w"], q["h"]))
        g.rectangle([x * W, y * H, (x + w) * W, (y + h) * H],
                    fill=colour + (52,), outline=colour + (255,), width=max(3, W // 260))
    im = Image.alpha_composite(im.convert("RGBA"), wash).convert("RGB")
    g = ImageDraw.Draw(im)
    fs = max(12, W // 46)
    fnt = font(fs)
    for q in boxes:
        x, y, w, h = (q["cell"] if key == "cell" else (q["x"], q["y"], q["w"], q["h"]))
        t = q["label"][:16]
        g.text(((x + w / 2) * W, (y + h / 2) * H), t, fill=(0, 0, 0), font=fnt, anchor="mm",
               stroke_width=max(2, fs // 6), stroke_fill=(255, 255, 255))
    im = im.crop(crop)
    W2 = im.width
    bar = Image.new("RGB", (W2, int(fs * 2.2)), (255, 255, 255))
    ImageDraw.Draw(bar).text((8, fs // 3), title, fill=colour, font=font(int(fs * 1.25)))
    out = Image.new("RGB", (W2, im.height + bar.height), (255, 255, 255))
    out.paste(bar, (0, 0))
    out.paste(im, (0, bar.height))
    return out


def render(stem, want=None):
    g = geom_of(stem)
    if not g:
        print("no PLACED grid geometry for %r — run render-grid.mjs on it first" % stem)
        return 1
    # A recording is named <sheet>.<model>.<round>, so fall back to the part before the first dot —
    # every model read the same sheet, and the sheet is what gets drawn on.
    plan = None
    for named in (stem, stem.split(".")[0], stem.replace("_live_", "")):
        for f in os.listdir(PLANS):
            if os.path.splitext(f)[0] == named:
                plan = os.path.join(PLANS, f)
                break
        if plan:
            break
    if not plan:
        print("no plan image named %r in %s" % (stem, PLANS))
        return 1

    sets, b = boxes_for(g)
    # Crop to the home with a small margin — a builder's sheet is mostly logo and blank paper, and
    # the rooms are what is being judged.
    with Image.open(plan) as probe:
        W, H = probe.size
    m = 0.06
    crop = (max(0, int((b["x"] - m) * W)), max(0, int((b["y"] - m) * H)),
            min(W, int((b["x"] + b["w"] + m) * W)), min(H, int((b["y"] + b["h"] + m) * H)))

    asked = (want.split(",") if want else SHOW)
    show = [k for k in asked if k in VARIANTS]
    panels = [draw_panel(plan, sets[k], VARIANTS[k], COLOUR[k], crop) for k in show]
    # `cells` is opt-in: it answers "what does the SCORE see", which is a different question from
    # "does the box sit on the room", and it clutters a picture meant to be handed to someone.
    if "cells" in asked:
        panels.append(draw_panel(plan, sets["cell"], "the GRID cells themselves (what the score sees)",
                                 (40, 90, 190), crop, key="cell"))
    gap = 24
    sheet = Image.new("RGB", (sum(p.width for p in panels) + gap * (len(panels) - 1),
                              max(p.height for p in panels)), (255, 255, 255))
    x = 0
    for p in panels:
        sheet.paste(p, (x, 0))
        x += p.width + gap
    os.makedirs(OUTDIR, exist_ok=True)
    out = os.path.join(OUTDIR, stem + ".png")
    sheet.save(out)

    print("%s — %d rooms" % (stem, len(sets[BASELINE])))
    print("  %-11s %8s %8s %8s   %s" % ("", "overlap", "off-bld", "worst", "worst collision"))
    for k in VARIANTS:
        sw, who = worst_swallow(sets[k])
        print("  %-11s %7.1f%% %7.1f%% %7.1f%%   %s"
              % (k, overlap_share(sets[k]), off_building_share(sets[k], b), sw, who or "none"))
    print("wrote", out)
    return 0


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return 1
    if sys.argv[1] == "--all":
        rows = []
        for f in sorted(os.listdir(GEOM)):
            if f.endswith(".geom.json"):
                s = score(f[: -len(".geom.json")])
                if s:
                    rows.append(s)
        if not rows:
            print("no PLACED grids in %s — run render-grid.mjs --only=X first" % GEOM)
            return 1
        pools = [("every recorded read", rows),
                 ("the reader we ship", [r for r in rows if SHIPPED in r["stem"]
                                         or "." not in r["stem"]])]
        for pool, rs in pools:
            n = len(rs)
            print("\n" + "=" * 78)
            print("%d reads — %s" % (n, pool))
            for m, name in (("ov", "boxes on top of each other"),
                            ("off", "box area off the building"),
                            ("sw", "worst single collision")):
                print("\n%s — average, and how many reads each way vs today" % name)
                for k in VARIANTS:
                    avg = sum(r[m + "_" + k] for r in rs) / n
                    if k == BASELINE:
                        print("    %-11s %5.1f%%" % (k, avg))
                        continue
                    better = sum(1 for r in rs if r[m + "_" + k] < r[m + "_" + BASELINE] - 1e-9)
                    worse = sum(1 for r in rs if r[m + "_" + k] > r[m + "_" + BASELINE] + 1e-9)
                    print("    %-11s %5.1f%%   better on %3d, worse on %3d" % (k, avg, better, worse))
        print()
        best = sys.argv[2] if len(sys.argv) > 2 else "holdclamp"
        print("\nworst regressions of %r by overlap — LOOK AT THESE:" % best)
        for r in sorted(rows, key=lambda r: r["ov_" + BASELINE] - r["ov_" + best])[:8]:
            print("  %-52s %5.1f%% -> %5.1f%%" % (r["stem"][:52], r["ov_" + BASELINE], r["ov_" + best]))
        return 0
    want = next((a.split("=", 1)[1] for a in sys.argv[2:] if a.startswith("--show=")), None)
    return render(sys.argv[1], want)


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""exp-frame.py — does the drawing grid belong to the PICTURE or to the HOME?

The owner scanned a builder sheet whose left third is a logo and a title block. The home landed in
the right-hand corner of the grid at about half size, and the small toilet came out one cell wide,
too narrow to print its own name.

Both symptoms have one cause. `ScanMapper.snap` maps the reader's coordinates — which are against
the whole PICTURE — straight onto the grid, and `reshapeToPrinted` then derives its scale from the
cells those rectangles already used (`cellTotal / printedTotal`). So the printed sizes only
REDISTRIBUTE area between rooms; they never set it. Whatever fraction of the sheet the home occupies
is the fraction of the grid it gets.

Re-framing onto the rooms' own bounding box was built and REVERTED in v0.3.21 for two stated
reasons. This measures both of them, plus what the change would actually buy:

  (a) "every room's size depends on every other room's — one stray rectangle over a title block
      inflates the frame and shrinks the whole home"   -> measured here as LEAVE-ONE-OUT frame
      inflation across the 30 real replies.
  (b) "it removed the fuzz suite's `no-clamp` bite, because after re-framing nothing can be out of
      bounds by construction"                           -> addressed by CLAMPING THE FRAME to the
      unit square, which this script asserts directly.

Every table is parsed out of the Kotlin so the mirror cannot drift from the source — the same
discipline the label resolver's mirror already learned the hard way.

Usage:  python exp-frame.py [--json]
"""
from __future__ import annotations

import json
import math
import re
import sys
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
LABELS_KT = ROOT / "shared/src/main/kotlin/com/vastufirst/shared/scan/RoomLabels.kt"
RECTS = Path(__file__).resolve().parent / "out/real-plans-rects.json"

MIN_GRID, MAX_GRID = 4, 10
MAX_TRUSTED_ROOMS = 12
UNIFORM_AREA_VARIATION = 0.15
UNIFORM_MIN_ROOMS = 4
CONTAINMENT_FRACTION = 0.90
MIN_PLACED_ROOMS = 2
MIN_PLACED_FRACTION = 0.6
COVERAGE_SAMPLES = 140

# ---------------------------------------------------------------- the Kotlin tables, parsed


def _block(src: str, name: str) -> list[str]:
    m = re.search(rf"private val {name} = (?:setOf|listOf)\((.*?)\n    \)", src, re.S)
    if not m:
        m = re.search(rf"private val {name} = (?:setOf|listOf)\((.*?)\)", src, re.S)
    return re.findall(r'"([^"]*)"', m.group(1))


def load_tables() -> dict:
    src = LABELS_KT.read_text(encoding="utf-8")
    exact: dict[str, str] = {}
    for m in re.finditer(r"put\(\s*RoomType\.(\w+)\s*,(.*?)\)", src, re.S):
        for name in re.findall(r'"([^"]*)"', m.group(2)):
            exact[name] = m.group(1)
    tables = {
        "EXACT": exact,
        "DROP_EXACT": set(_block(src, "DROP_EXACT")),
        "DROP_CONTAINS": _block(src, "DROP_CONTAINS"),
        "LOOSE_EXCLUDED": set(_block(src, "LOOSE_EXCLUDED")),
        "AMBIGUOUS": set(_block(src, "AMBIGUOUS")),
        "DESCRIPTOR_WORDS": set(_block(src, "DESCRIPTOR_WORDS")),
        "UNIT_WORDS": set(_block(src, "UNIT_WORDS")),
    }
    contains = [(k, v) for k, v in exact.items() if k not in tables["LOOSE_EXCLUDED"]]
    contains.sort(key=lambda kv: (-len(kv[0]), kv[0]))
    tables["CONTAINS"] = contains
    return tables


T = load_tables()

DIMENSION_X = re.compile(r"(?<=[0-9])[ ]*X[ ]*(?=[0-9])")
FEET_INCH_MARKS = re.compile(r"['\"‘’“”′″]")
PARENTHETICAL = re.compile(r"\([^)]*\)")
NON_ALPHANUMERIC = re.compile(r"[^0-9A-Z]")
MEASUREMENT_TOKEN = re.compile(r"^[0-9]+(MM|CM|M|FT|SQFT|SQM)?$")


def clean(raw: str) -> str:
    s = raw.upper()
    s = PARENTHETICAL.sub(" ", s)
    s = FEET_INCH_MARKS.sub(" ", s)
    s = s.replace(".", "")
    s = DIMENSION_X.sub(" ", s)
    s = NON_ALPHANUMERIC.sub(" ", s)
    return " ".join(
        w for w in s.split(" ")
        if w and not MEASUREMENT_TOKEN.match(w) and w not in T["DESCRIPTOR_WORDS"]
    )


def living_by_name(c: str) -> bool:
    if c in T["EXACT"]:
        return T["EXACT"][c] == "LIVING"
    if c in T["DROP_EXACT"] or any(d in c for d in T["DROP_CONTAINS"]):
        return False
    for k, v in T["CONTAINS"]:
        if k in c:
            return v == "LIVING"
    return False


def context_of(labels: list[str]) -> bool:
    return any(clean(r) not in T["AMBIGUOUS"] and living_by_name(clean(r)) for r in labels)


def resolve(raw: str, has_living: bool):
    """-> ('room', type, loose) | ('drop',) | ('unknown',)"""
    c = clean(raw)
    if not c:
        return ("unknown",)
    if c in T["AMBIGUOUS"]:
        return ("room", "CORRIDOR" if has_living else "LIVING", True)
    if c in T["EXACT"]:
        return ("room", T["EXACT"][c], False)
    if c in T["DROP_EXACT"] or any(d in c for d in T["DROP_CONTAINS"]):
        return ("drop",)
    for k, v in T["CONTAINS"]:
        if k in c:
            t = ("CORRIDOR" if has_living else "LIVING") if k in T["AMBIGUOUS"] else v
            return ("room", t, True)
    return ("unknown",)


def is_unit_label(raw: str) -> bool:
    w = [x for x in clean(raw).split(" ") if x]
    if not w or w[0] not in T["UNIT_WORDS"]:
        return False
    return len(w) == 1 or (len(w) == 2 and len(w[1]) <= 2)


# ---------------------------------------------------------------- RoomDimensions, mirrored

MM_PER_FOOT = 304.8
FRACTIONS = {"½": 0.5, "¼": 0.25, "¾": 0.75, "⅓": 1 / 3, "⅔": 2 / 3, "⅛": 0.125}
FEET_INCHES = r"(\d+)\s*['′’]\s*-?\s*(\d+)?\s*([½¼¾⅓⅔⅛])?\s*[\"″”]?"
PAIR_FEET_INCHES = re.compile(FEET_INCHES + r"\s*[X×]\s*" + FEET_INCHES)
PAIR_PLAIN = re.compile(r"(?<![\d.'\"′″])(\d{2,5})\s*(?:MM|CM)?\s*[X×]\s*(\d{2,5})\s*(?:MM|CM)?(?!\d)")
FEET_IF_UNDER = 100


def _fi(f, i, fr):
    return float(f) + ((float(i) if i else 0.0) + (FRACTIONS.get(fr, 0.0) if fr else 0.0)) / 12.0


def parse_printed(label: str):
    """-> (widthMm, depthMm) or None"""
    s = label.upper()
    m = PAIR_FEET_INCHES.search(s)
    if m:
        a = _fi(m.group(1), m.group(2), m.group(3))
        b = _fi(m.group(4), m.group(5), m.group(6))
        return (a * MM_PER_FOOT, b * MM_PER_FOOT) if a > 0 and b > 0 else None
    m = PAIR_PLAIN.search(s)
    if m:
        a, b = float(m.group(1)), float(m.group(2))
        if a <= 0 or b <= 0:
            return None
        if a < FEET_IF_UNDER and b < FEET_IF_UNDER:
            return (a * MM_PER_FOOT, b * MM_PER_FOOT)
        return (a, b)
    return None


# ---------------------------------------------------------------- ScanMapper, mirrored


@dataclass
class Box:
    label: str
    x: float
    y: float
    w: float
    h: float
    confidence: float


@dataclass
class Rect:
    col: int
    row: int
    w: int
    h: int

    @property
    def right(self):
        return self.col + self.w

    @property
    def bottom(self):
        return self.row + self.h

    @property
    def area(self):
        return self.w * self.h


def overlaps(a: Rect, b: Rect) -> bool:
    return a.col < b.right and b.col < a.right and a.row < b.bottom and b.row < a.bottom


def sanitise(b: dict):
    v = [b.get(k) for k in ("x", "y", "w", "h")]
    if any(x is None or not isinstance(x, (int, float)) or not math.isfinite(x) for x in v):
        return None
    x, y, w, h = v
    if w <= 0 or h <= 0:
        return None
    x0, y0 = min(max(x, 0.0), 1.0), min(max(y, 0.0), 1.0)
    x1, y1 = min(max(x + w, 0.0), 1.0), min(max(y + h, 0.0), 1.0)
    if x1 <= x0 or y1 <= y0:
        return None
    c = b.get("confidence", 0.0)
    c = min(max(c, 0.0), 1.0) if isinstance(c, (int, float)) and math.isfinite(c) else 0.0
    return Box(str(b.get("label", "")), x0, y0, x1 - x0, y1 - y0, c)


def contained_in(inner: Box, outer: Box) -> bool:
    ia, oa = inner.w * inner.h, outer.w * outer.h
    if ia <= 0 or ia >= oa:
        return False
    ix = max(0.0, min(inner.x + inner.w, outer.x + outer.w) - max(inner.x, outer.x))
    iy = max(0.0, min(inner.y + inner.h, outer.y + outer.h) - max(inner.y, outer.y))
    return (ix * iy) / ia >= CONTAINMENT_FRACTION


def grid_for(aspect):
    if aspect is None or not math.isfinite(aspect) or aspect <= 0:
        return MAX_GRID, MAX_GRID
    clamp = lambda v: min(max(int(round(v)), MIN_GRID), MAX_GRID)
    return (MAX_GRID, clamp(MAX_GRID / aspect)) if aspect >= 1 else (clamp(MAX_GRID * aspect), MAX_GRID)


def area_variation(boxes):
    if not boxes:
        return 0.0
    areas = [b.w * b.h for b in boxes]
    mean = sum(areas) / len(areas)
    if mean <= 0:
        return 0.0
    var = sum((a - mean) ** 2 for a in areas) / len(areas)
    return math.sqrt(var) / mean


def snap_edges(l, r, t, b, cols, rows, legacy=False):
    """A room may round SMALL, never away — mirrors ScanMapper.snap.
    legacy=True reproduces the SHIPPED behaviour: both edges may round together and the room is lost."""
    if legacy:
        lf = min(max(int(round(l)), 0), cols); rf = min(max(int(round(r)), 0), cols)
        tf = min(max(int(round(t)), 0), rows); bf = min(max(int(round(b)), 0), rows)
        return None if (rf <= lf or bf <= tf) else Rect(lf, tf, rf - lf, bf - tf)
    left = min(max(int(round(l)), 0), cols - 1)
    right = min(max(int(round(r)), left + 1), cols)
    top = min(max(int(round(t)), 0), rows - 1)
    bottom = min(max(int(round(b)), top + 1), rows)
    return Rect(left, top, right - left, bottom - top)


def reshape_to_printed(snapped, cols, rows):
    """snapped: list of (label, Rect, printed) -> list of (label, Rect, printed)

The scale is whatever the reader's own rectangles already used, so the printed sizes
    REDISTRIBUTE area rather than setting it. A separate "fill the grid" scale was measured and
    rejected — once the frame is the HOME it changes nothing. See ScanMapper.
    """
    cell_total = sum(r.area for (_, r, p) in snapped if p)
    printed_total = sum(p[0] * p[1] for (_, _, p) in snapped if p)
    if cell_total <= 0 or printed_total <= 0:
        return snapped
    per_mm2 = cell_total / printed_total
    out = []
    for (lab, rect, p) in snapped:
        if not p:
            out.append((lab, rect, p))
            continue
        target = p[0] * p[1] * per_mm2
        ratio = p[0] / p[1]
        if not math.isfinite(target) or target <= 0 or not math.isfinite(ratio) or ratio <= 0:
            out.append((lab, rect, p))
            continue
        wf, hf = math.sqrt(target * ratio), math.sqrt(target / ratio)
        fit = min(1.0, cols / wf, rows / hf)
        wf, hf = wf * fit, hf * fit
        w = min(max(int(round(wf)), 1), cols)
        h = min(max(int(round(hf)), 1), rows)
        col = min(max(rect.col, 0), cols - w)
        row = min(max(rect.row, 0), rows - h)
        out.append((lab, Rect(col, row, w, h), p))
    return out


def retract(r: Rect, o: Rect):
    opts = []
    if o.right > r.col and o.right < r.right:
        opts.append(Rect(o.right, r.row, r.right - o.right, r.h))
    if o.col > r.col and o.col < r.right:
        opts.append(Rect(r.col, r.row, o.col - r.col, r.h))
    if o.bottom > r.row and o.bottom < r.bottom:
        opts.append(Rect(r.col, o.bottom, r.w, r.bottom - o.bottom))
    if o.row > r.row and o.row < r.bottom:
        opts.append(Rect(r.col, r.row, r.w, o.row - r.row))
    return max(opts, key=lambda x: x.area) if opts else None


def trim_against(cand: Rect, blockers):
    r, guard = cand, cand.area + 4
    while guard > 0:
        guard -= 1
        hit = next((b for b in blockers if overlaps(b, r)), None)
        if hit is None:
            return r
        r = retract(r, hit)
        if r is None:
            return None
    return None


def resolve_overlaps(items, legacy=False, **kwargs):
    """items: list of (label, conf, printedRect, asReadRect) -> (placed, trimmed, dropped)"""
    rescued = kwargs.get("rescued", frozenset())
    order = sorted(items, key=lambda it: ((1 if it[0] in rescued else 0, -it[1], -it[2].area, it[0]) if legacy
                                          else (0 if it[0] in rescued else 1, it[2].area, -it[1], it[0])))
    placed, trimmed, dropped = [], 0, []
    for (lab, conf, rect, as_read) in order:
        blockers = [p[1] for p in placed]
        got = trim_against(rect, blockers)
        if got is None:
            got = trim_against(as_read, blockers)
        if got is None:
            one = Rect(rect.col, rect.row, 1, 1)
            got = None if legacy else (one if not any(overlaps(bk, one) for bk in blockers) else None)
        if got is None:
            dropped.append(lab)
            continue
        if got.col != rect.col or got.row != rect.row or got.w != rect.w or got.h != rect.h:
            trimmed += 1
        placed.append((lab, got))
    return placed, trimmed, dropped


def place_with_rescue(items, legacy=False):
    """⭐ a room lost outright goes to the FRONT and the placement is redone — ScanMapper's
    rescue loop. Terminates: the rescued set only grows."""
    rescued = frozenset()
    placed, trimmed, lost = resolve_overlaps(items, legacy, rescued=rescued)
    for _ in range(len(items) + 1):
        if legacy or not lost or set(lost) <= rescued:
            break
        rescued = rescued | set(lost)
        placed, trimmed, lost = resolve_overlaps(items, legacy, rescued=rescued)
    return placed, trimmed, len(lost)


# ---------------------------------------------------------------- the two framings


def run(reply: dict, img_w: int, img_h: int, mode: str):
    """mode = 'picture' (today) | 'home' (candidate). Returns a result dict."""
    raw = reply.get("rooms") or []
    clean_boxes = [b for b in (sanitise(r) for r in raw) if b]

    ptype = reply.get("planType", "UNKNOWN")
    if ptype in ("3D_RENDER", "NOT_A_PLAN"):
        return {"outcome": "refused", "reason": ptype}
    if sum(1 for b in clean_boxes if is_unit_label(b.label)) >= 2:
        return {"outcome": "refused", "reason": "MULTI_UNIT"}
    if reply.get("unreadable") or not reply.get("hasRoomLabels", True):
        return {"outcome": "refused", "reason": "NO_LABELS"}
    if not clean_boxes:
        return {"outcome": "refused", "reason": "NO_ROOMS"}

    has_living = context_of([b.label for b in clean_boxes])
    typed = []
    unknown = 0
    for b in clean_boxes:
        m = resolve(b.label, has_living)
        if m[0] == "room":
            typed.append(b)
        elif m[0] == "unknown":
            unknown += 1
    rooms = [c for c in typed if not any(o is not c and contained_in(c, o) for o in typed)]
    if not rooms:
        return {"outcome": "refused", "reason": "NO_LABELS" if unknown else "NO_ROOMS"}

    variation = area_variation(clean_boxes)
    if len(rooms) >= UNIFORM_MIN_ROOMS and variation < UNIFORM_AREA_VARIATION:
        return {"outcome": "assisted", "reason": "UNIFORM_BOXES", "rooms": len(rooms)}
    if len(rooms) > MAX_TRUSTED_ROOMS:
        return {"outcome": "assisted", "reason": "TOO_MANY_ROOMS", "rooms": len(rooms)}

    aspect = (img_w / img_h) if img_h else 1.0

    if mode == "picture":
        cols, rows = grid_for(aspect)
        def edges(b):
            return (b.x * cols, (b.x + b.w) * cols, b.y * rows, (b.y + b.h) * rows)
    else:
        # ⭐ The frame is the rooms' own bounding box, CLAMPED TO THE PICTURE. The clamp is what keeps
        # the fuzz suite's `no-clamp` injection biting: an un-clamped box still falls outside a frame
        # that can never exceed the unit square, so it still snaps off-grid.
        x0 = max(0.0, min(b.x for b in rooms))
        y0 = max(0.0, min(b.y for b in rooms))
        x1 = min(1.0, max(b.x + b.w for b in rooms))
        y1 = min(1.0, max(b.y + b.h for b in rooms))
        pw, ph = (x1 - x0) * img_w, (y1 - y0) * img_h
        if pw <= 0 or ph <= 0:
            return {"outcome": "degenerate-frame"}
        cols, rows = grid_for(pw / ph)
        s = min(cols / pw, rows / ph)          # uniform: proportions survive
        ox, oy = (cols - pw * s) / 2, (rows - ph * s) / 2
        def edges(b, x0=x0, y0=y0, s=s, ox=ox, oy=oy):
            l = (b.x - x0) * img_w * s + ox
            r = (b.x + b.w - x0) * img_w * s + ox
            t = (b.y - y0) * img_h * s + oy
            bt = (b.y + b.h - y0) * img_h * s + oy
            return (l, r, t, bt)

    legacy = mode == "picture"
    snapped, degenerate = [], 0
    for b in rooms:
        l, r, t, bt = edges(b)
        rect = snap_edges(l, r, t, bt, cols, rows, legacy)
        if rect is None:
            degenerate += 1
        else:
            snapped.append((b, rect, parse_printed(b.label)))

    as_read = {id(b): rect for (b, rect, _) in snapped}

    shaped = reshape_to_printed([(b.label, rect, p) for (b, rect, p) in snapped], cols, rows)
    items = [(lab, snapped[i][0].confidence, rect, as_read[id(snapped[i][0])])
             for i, (lab, rect, _) in enumerate(shaped)]
    placed, trimmed, dropped = place_with_rescue(items, legacy)

    if len(placed) < MIN_PLACED_ROOMS or len(placed) < len(rooms) * MIN_PLACED_FRACTION:
        return {"outcome": "assisted", "reason": "TOO_FEW_PLACED", "rooms": len(rooms)}

    rects = [r for (_, r) in placed]
    bx0 = min(r.col for r in rects); bx1 = max(r.right for r in rects)
    by0 = min(r.row for r in rects); by1 = max(r.bottom for r in rects)
    return {
        "outcome": "placed",
        "cols": cols, "rows": rows,
        "rooms": len(rooms),
        "placed": len(placed),
        "dropped": dropped + degenerate,
        "trimmed": trimmed,
        "fill": (bx1 - bx0) * (by1 - by0) / (cols * rows),
        "tiled": sum(r.area for r in rects) / (cols * rows),
        "min_cells": min(r.area for r in rects),
        "thin": sum(1 for r in rects if r.w < 2),      # too narrow to print a room name
        "dimensioned": sum(1 for (_, _, p) in shaped if p),
        "rects": [(lab, r.col, r.row, r.w, r.h) for (lab, r) in placed],
    }


def frame_fragility(reply):
    """Objection (a): how much does ONE stray rectangle inflate the frame?"""
    boxes = [b for b in (sanitise(r) for r in (reply.get("rooms") or [])) if b]
    has_living = context_of([b.label for b in boxes])
    rooms = [b for b in boxes if resolve(b.label, has_living)[0] == "room"]
    rooms = [c for c in rooms if not any(o is not c and contained_in(c, o) for o in rooms)]
    if len(rooms) < 3:
        return None
    def bbox_area(bs):
        return (max(b.x + b.w for b in bs) - min(b.x for b in bs)) * \
               (max(b.y + b.h for b in bs) - min(b.y for b in bs))
    full = bbox_area(rooms)
    best = min(bbox_area([b for b in rooms if b is not drop]) for drop in rooms)
    return full / best if best > 0 else None


# ---------------------------------------------------------------- the owner's own sheet
#
# Green Court, Sector 90 Gurgaon — "UNIT PLAN CATEGORY III", 526 sq ft carpet. The sheet devotes its
# left ~40 % to a logo, the category text and the room legend; the home sits in the right-hand
# portion. Rectangles below are the BUILDING's true geometry read off the sheet, i.e. what a PERFECT
# reader would return. That is deliberate: it isolates the framing defect from any reader error, and
# it is the case the owner is looking at.
OWNER_SHEET = {
    "size": [1000, 1000],
    "reply": {
        "planType": "2D_PLAN", "hasRoomLabels": True, "unreadable": False, "planConfidence": 0.95,
        "rooms": [
            {"label": 'BALCONY 5\'-0" WIDE',        "x": .425, "y": .105, "w": .520, "h": .065, "confidence": .9},
            {"label": 'BED ROOM 10\'-9½"X11\'-0"',  "x": .425, "y": .175, "w": .265, "h": .325, "confidence": .95},
            {"label": 'TOILET 8\'-2"X5\'-0"',       "x": .700, "y": .175, "w": .170, "h": .155, "confidence": .9},
            {"label": 'KITCHEN 8\'-2"X7\'-10"',     "x": .740, "y": .390, "w": .140, "h": .140, "confidence": .9},
            {"label": 'W.C 4\'-11"X6\'-4½"',        "x": .430, "y": .505, "w": .170, "h": .135, "confidence": .85},
            {"label": 'BED ROOM 10\'-6"X10\'-0"',   "x": .425, "y": .650, "w": .225, "h": .255, "confidence": .95},
            {"label": 'LOBBY 10\'-3½"X14\'-10½"',   "x": .650, "y": .530, "w": .295, "h": .400, "confidence": .9},
        ],
    },
}


def owner_report():
    w, h = OWNER_SHEET["size"]
    print("\n" + "=" * 104)
    print("THE OWNER'S SHEET — Green Court Category III, 526 sq ft carpet")
    print("=" * 104)
    for mode, title in (("picture", "TODAY  (grid = the picture)"), ("home", "CANDIDATE  (grid = the home)")):
        r = run(OWNER_SHEET["reply"], w, h, mode)
        print(f"\n{title}   grid {r['cols']}x{r['rows']}   "
              f"home fills {r['fill']*100:.0f}% of it   rooms cover {r['tiled']*100:.0f}%   "
              f"trimmed {r['trimmed']}  dropped {r['dropped']}")
        for (lab, c, ro, cw, ch) in sorted(r["rects"], key=lambda t: (t[2], t[1])):
            p = parse_printed(lab)
            true_ratio = f"{p[0]/p[1]:.2f}" if p else "  - "
            warn = "  <-- too narrow to print its name" if cw < 2 else ""
            print(f"    {lab:<26} {cw}x{ch} cells at ({c},{ro})   "
                  f"printed w/d {true_ratio}  drawn w/d {cw/ch:.2f}{warn}")
        # what one cell is worth in real feet, which is the number the owner can sanity-check
        dims = [parse_printed(l) for (l, *_rest) in r["rects"]]
        cells = sum(cw * ch for (lab, _c, _ro, cw, ch), p in zip(r["rects"], dims) if p)
        sqft = sum((p[0] * p[1]) / (304.8 ** 2) for p in dims if p)
        if cells:
            print(f"    -> one cell = {sqft/cells:5.2f} sq ft, so the whole grid reads as a "
                  f"{math.sqrt(sqft/cells)*r['cols']:.0f}ft x {math.sqrt(sqft/cells)*r['rows']:.0f}ft plot "
                  f"for a home that is about 21ft x 27ft")


def main():
    data = json.loads(RECTS.read_text(encoding="utf-8"))
    rows = []
    for rec in data:
        reply = rec.get("reply") or {}
        w, h = rec.get("size", [1, 1])
        a = run(reply, w, h, "picture")
        b = run(reply, w, h, "home")
        rows.append((rec["file"], a, b, frame_fragility(reply)))

    if "--json" in sys.argv:
        print(json.dumps([{"file": f, "picture": a, "home": b, "fragility": g} for f, a, b, g in rows], indent=1))
        return

    print(f"{'plan':<16}{'outcome':<10}{'grid':>7}  {'fill%':>12}  {'tiled%':>12}  "
          f"{'min cells':>11}  {'1-wide':>8}  {'trim':>7}  {'drop':>7}")
    print("-" * 104)
    placed = [(f, a, b) for f, a, b, _ in rows if a["outcome"] == "placed" and b["outcome"] == "placed"]
    for f, a, b in placed:
        print(f"{f:<16}{'placed':<10}{a['cols']}x{a['rows']}>{b['cols']}x{b['rows']:<2}  "
              f"{a['fill']*100:5.0f} > {b['fill']*100:4.0f}  "
              f"{a['tiled']*100:5.0f} > {b['tiled']*100:4.0f}  "
              f"{a['min_cells']:5d} > {b['min_cells']:3d}  "
              f"{a['thin']:3d} > {b['thin']:2d}  "
              f"{a['trimmed']:3d} > {b['trimmed']:2d}  "
              f"{a['dropped']:3d} > {b['dropped']:2d}")

    if placed:
        n = len(placed)
        print("-" * 104)
        print(f"{'MEAN':<16}{'':<10}{'':>7}  "
              f"{sum(a['fill'] for _, a, _ in placed)/n*100:5.0f} > {sum(b['fill'] for _, _, b in placed)/n*100:4.0f}  "
              f"{sum(a['tiled'] for _, a, _ in placed)/n*100:5.0f} > {sum(b['tiled'] for _, _, b in placed)/n*100:4.0f}  "
              f"{sum(a['min_cells'] for _, a, _ in placed)/n:5.1f} > {sum(b['min_cells'] for _, _, b in placed)/n:3.1f}  "
              f"{sum(a['thin'] for _, a, _ in placed):3d} > {sum(b['thin'] for _, _, b in placed):2d}  "
              f"{sum(a['trimmed'] for _, a, _ in placed):3d} > {sum(b['trimmed'] for _, _, b in placed):2d}  "
              f"{sum(a['dropped'] for _, a, _ in placed):3d} > {sum(b['dropped'] for _, _, b in placed):2d}")

    changed = [(f, a["outcome"], b["outcome"]) for f, a, b, _ in rows if a["outcome"] != b["outcome"]]
    print(f"\noutcome changes (placed/assisted/refused): {changed if changed else 'none'}")

    frag = sorted([(g, f) for f, _, _, g in rows if g], reverse=True)
    print("\nOBJECTION (a) — frame inflation caused by ONE stray rectangle")
    print("  bbox area / smallest bbox with any single room removed; 1.00 = no single room matters")
    for g, f in frag[:8]:
        print(f"    {f:<16}{g:6.2f}x")
    if frag:
        print(f"    {'median':<16}{sorted(g for g, _ in frag)[len(frag)//2]:6.2f}x   "
              f"over {len(frag)} plans, worst {frag[0][0]:.2f}x")

    owner_report()


if __name__ == "__main__":
    main()

#!/usr/bin/env python
"""
Candidate discriminators for "is this read's GEOMETRY usable?", computed from the captured
rectangles in out/real-plans-rects.json.

The point: coverage was chosen as the gate before anyone had looked at a single real placement, and
the first three overlays already break it -- plan-006 (10 rooms) and plan-002 (24 rooms) have
IDENTICAL coverage 0.421 with opposite placement quality, and plan-005 has the corpus's HIGHEST
coverage 0.760 with its dining room, kitchen and puja all in the wrong place.

So print every cheap signal we can compute without ground truth, next to the eye verdict, and see
which one actually separates good from bad.

Usage:  python exp-place-stats.py
"""
import json, os, statistics

HERE = os.path.dirname(os.path.abspath(__file__))
RECTS = os.path.join(HERE, "out", "real-plans-rects.json")
VERDICTS = os.path.join(HERE, "out", "place-verdicts.json")   # written by hand after looking


def rects_of(reply):
    out = []
    for r in reply.get("rooms", []):
        try:
            x, y, w, h = float(r["x"]), float(r["y"]), float(r["w"]), float(r["h"])
        except Exception:
            continue
        if w > 0 and h > 0:
            out.append((x, y, w, h))
    return out


def coverage(rects, n=140):
    if not rects:
        return 0.0
    hit = 0
    for gy in range(n):
        py = (gy + .5) / n
        for gx in range(n):
            px = (gx + .5) / n
            for r in rects:
                if r[0] <= px < r[0] + r[2] and r[1] <= py < r[1] + r[3]:
                    hit += 1
                    break
    return hit / (n * n)


def overlap_fraction(rects):
    """Sum of areas / union area. 1.0 = a clean tiling; >1 means rectangles sit on each other.

    A real floor plan's rooms do not overlap. So the more the model's rectangles pile up, the less
    it was reading the drawing -- and unlike coverage this needs no assumption about walls,
    corridors or shafts, which is exactly what made coverage unusable on real homes."""
    if not rects:
        return 0.0
    total = sum(r[2] * r[3] for r in rects)
    uni = coverage(rects)
    return total / uni if uni > 0 else 0.0


def grid_snappiness(rects):
    """How many edges land on a suspiciously round number. Invented layouts snap to 0.05."""
    edges = []
    for x, y, w, h in rects:
        edges += [x, y, x + w, y + h]
    if not edges:
        return 0.0
    snapped = sum(1 for e in edges if abs(e * 20 - round(e * 20)) < 1e-6)
    return snapped / len(edges)


def main():
    data = json.load(open(RECTS, encoding="utf-8"))
    verdicts = json.load(open(VERDICTS, encoding="utf-8")) if os.path.exists(VERDICTS) else {}

    rows = []
    print("%-16s %-11s %5s %8s %8s %7s %7s  %s" %
          ("FILE", "TYPE", "rooms", "coverage", "overlap", "areaCV", "snap", "EYE"))
    for rec in data:
        reply = rec["reply"]
        if reply.get("planType") != "2D_PLAN":
            continue
        rects = rects_of(reply)
        if not rects:
            continue
        areas = [r[2] * r[3] for r in rects]
        mean = statistics.fmean(areas)
        cv = (statistics.pstdev(areas) / mean) if mean > 0 else 0.0
        cov = coverage(rects)
        ov = overlap_fraction(rects)
        snap = grid_snappiness(rects)
        eye = verdicts.get(rec["file"], "?")
        rows.append((rec["file"], len(rects), cov, ov, cv, snap, eye))
        print("%-16s %-11s %5d %8.3f %8.2f %7.3f %7.2f  %s" %
              (rec["file"], reply.get("planType"), len(rects), cov, ov, cv, snap, eye))

    judged = [r for r in rows if r[6] in ("good", "bad", "mixed")]
    if not judged:
        print("\nNo eye verdicts yet -- write out/place-verdicts.json as {file: good|mixed|bad}.")
        return

    print("\n--- separation by eye verdict ---")
    for name, idx in (("rooms", 1), ("coverage", 2), ("overlap", 3), ("areaCV", 4), ("snap", 5)):
        line = "%-9s" % name
        for v in ("good", "mixed", "bad"):
            vals = [r[idx] for r in judged if r[6] == v]
            line += "  %s n=%d med=%.3f range %.3f-%.3f" % (
                v, len(vals), statistics.median(vals), min(vals), max(vals)) if vals else "  %s n=0" % v
        print(line)


if __name__ == "__main__":
    main()

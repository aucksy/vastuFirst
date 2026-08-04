#!/usr/bin/env python
r"""
Grade the tint-box experiment (FREE - reads recorded v4 replies, scans nothing).

For every out/live/<stem>.<model>.v4*.json:
  - compose each room onto the page: page = building.xy + room.xy * building.wh
  - old-style page box (what the app draws today) = room fractions on the full page
  - metrics per sheet, old vs new:
      centre-err : mean distance from paper-truth centres (truth sheets only)
      area-share : sum of box areas / page area (rooms can never fill a page)
      overlap    : box-on-box overlap share (real rooms never overlap)
      bbox-sane  : building box present, inside the page, not near-full-page
  - overlays in out/tint-v4/: orange = today's drawing, blue = composed v4, green = truth

    python tools/scan-eval/exp-tint-v4-grade.py
"""
import json, os, glob
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
LIVE = os.path.join(HERE, "out", "live")
TRUTH = os.path.join(HERE, "truth")
PLANS = os.path.join(ROOT, "Documents", "Sample Floor plans")
OUTDIR = os.path.join(HERE, "out", "tint-v4")
os.makedirs(OUTDIR, exist_ok=True)

def truth_for(stem):
    for cand in (stem, stem.replace("-clean", "").replace("-branded", "")):
        p = os.path.join(TRUTH, cand + ".json")
        if os.path.exists(p):
            return {t["name"].upper(): (t["cx"], t["cy"])
                    for t in json.load(open(p, encoding="utf-8"))["rooms"]}
    return {}

def image_for(stem):
    for f in os.listdir(PLANS):
        if f.rsplit(".", 1)[0] == stem:
            return os.path.join(PLANS, f)
    return None

def main():
    files = sorted(glob.glob(os.path.join(LIVE, "*.v4*.json")))
    if not files:
        print("no v4 recordings yet - run exp-tint-v4.py (after the owner's yes)")
        return 1
    print(f"{'sheet.model.tag':52} {'frame':6} {'area':>5} {'ovlp':>5} {'c-err':>6}")
    for f in files:
        rec = json.load(open(f, encoding="utf-8"))
        stem = rec["file"].rsplit(".", 1)[0]
        reply = rec.get("reply") or {}
        rooms = [r for r in reply.get("rooms", []) if all(k in r for k in "xywh")]
        if not rooms:
            print(os.path.basename(f), "-> no rooms (refused or empty)")
            continue
        b = reply.get("building") or {}
        has_b = all(k in b for k in "xywh") and 0.05 < b.get("w", 0) <= 1 and 0.05 < b.get("h", 0) <= 1
        tcent = truth_for(stem)
        name = os.path.basename(f).replace(".json", "")

        for frame in (["page", "comp"] if has_b else ["page"]):
            boxes, labeled = [], []
            for r in rooms:
                if frame == "comp":
                    x, y = b["x"] + r["x"] * b["w"], b["y"] + r["y"] * b["h"]
                    w, h = r["w"] * b["w"], r["h"] * b["h"]
                else:
                    x, y, w, h = r["x"], r["y"], r["w"], r["h"]
                boxes.append((x, y, w, h))
                labeled.append((str(r.get("label", "")).upper(), (x, y, w, h)))
            area = sum(w * h for (_, _, w, h) in boxes)
            ov = 0.0
            for i in range(len(boxes)):
                for j in range(i + 1, len(boxes)):
                    a2, b2 = boxes[i], boxes[j]
                    ox = max(0, min(a2[0]+a2[2], b2[0]+b2[2]) - max(a2[0], b2[0]))
                    oy = max(0, min(a2[1]+a2[3], b2[1]+b2[3]) - max(a2[1], b2[1]))
                    ov += ox * oy
            errs = []
            for lab, (x, y, w, h) in labeled:
                if lab in tcent:
                    cx, cy = tcent[lab]
                    errs.append(((x+w/2-cx)**2 + (y+h/2-cy)**2) ** 0.5)
            cerr = sum(errs)/len(errs) if errs else None
            print(f"{name:52} {frame:6} {area:5.2f} {ov/area if area else 0:5.0%} "
                  f"{cerr:6.3f}" if cerr is not None else
                  f"{name:52} {frame:6} {area:5.2f} {ov/area if area else 0:5.0%}    n/a")

        img_p = image_for(stem)
        if img_p and has_b:
            im = Image.open(img_p).convert("RGB")
            d = ImageDraw.Draw(im, "RGBA")
            W, H = im.size
            for r in rooms:
                x, y, w, h = r["x"]*W, r["y"]*H, r["w"]*W, r["h"]*H
                d.rectangle([x, y, x+w, y+h], outline=(230, 110, 40, 220), width=3)
            for r in rooms:
                x = (b["x"] + r["x"]*b["w"]) * W; y = (b["y"] + r["y"]*b["h"]) * H
                w = r["w"]*b["w"]*W; h = r["h"]*b["h"]*H
                d.rectangle([x, y, x+w, y+h], outline=(40, 90, 220, 255), width=4)
            for (cx, cy) in truth_for(stem).values():
                d.ellipse([cx*W-7, cy*H-7, cx*W+7, cy*H+7], fill=(20, 150, 60, 255))
            d.rectangle([b["x"]*W, b["y"]*H, (b["x"]+b["w"])*W, (b["y"]+b["h"])*H],
                        outline=(20, 150, 60, 200), width=2)
            im.save(os.path.join(OUTDIR, f"{name}.png"))
    print("\nRead: 'page' rows = how the app draws TODAY; 'comp' rows = composed through the")
    print("building box. Success = comp area-share well under page's, overlap ~0, centre-err")
    print("smaller than page. Overlays: orange today, blue composed, green truth + building box.")
    print("Overlays in:", OUTDIR)
    return 0

if __name__ == "__main__":
    raise SystemExit(main())

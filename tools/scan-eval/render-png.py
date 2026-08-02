#!/usr/bin/env python
"""
Draw a mapper result (a .geom.json written by render-grid.mjs) as a PNG — the headless viewer.

render-grid.mjs runs the real mapper mirror and dumps geometry; this only DRAWS. Keeping the
drawing in Python/PIL means an assistant session with no visible browser can still LOOK at what
a scan produced, next to the plan image if one is given.

Usage:  python render-png.py <id-or-substring> [more ids...]     # from out/render/*.geom.json
        python render-png.py --all
"""
import json, os, sys, glob
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
REN = os.path.join(HERE, "out", "render")

PALETTE = {
    "BEDROOM": ("#dcd5ec", "#5b4ba6"), "GUEST_BEDROOM": ("#dcd5ec", "#5b4ba6"),
    "MASTER_BEDROOM": ("#e0d3c2", "#7a5c34"),
    "TOILET": ("#efd2cb", "#c0392b"), "BATHROOM": ("#efd2cb", "#c0392b"),
    "KITCHEN": ("#f6dcc0", "#d97e2c"),
    "LIVING": ("#cfe3dc", "#1e6f64"), "DINING": ("#d7e8e2", "#1e6f64"),
    "BALCONY": ("#e6d9ee", "#8e44ad"),
    "ENTRANCE": ("#dcdcd2", "#6b6b5e"), "CORRIDOR": ("#dcdcd2", "#6b6b5e"),
    "STAIRCASE": ("#dcdcd2", "#6b6b5e"), "UTILITY": ("#dfe0d5", "#6b6b5e"),
    "STORE": ("#dfe0d5", "#6b6b5e"), "GARAGE": ("#dfe0d5", "#6b6b5e"),
    "POOJA": ("#f2e4bd", "#a8842c"), "STUDY": ("#cdd9e8", "#33567d"),
    "BASEMENT": ("#dcdcd2", "#6b6b5e"), "COURTYARD": ("#d9e6cf", "#4a7a34"),
}
CELL, PAD = 64, 40


def font(size):
    for name in ("segoeui.ttf", "arial.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            pass
    return ImageFont.load_default()


def draw_one(path):
    g = json.load(open(path, encoding="utf-8"))
    out_path = path.replace(".geom.json", ".png")
    if g["kind"] != "placed":
        im = Image.new("RGB", (760, 90), "#f4f1e8")
        d = ImageDraw.Draw(im)
        d.text((14, 18), f"{g['id']} - {g['kind'].upper()} ({g.get('reason')})", fill="#7a2f22", font=font(20))
        names = " / ".join(r["label"] for r in g["rooms"])[:110]
        d.text((14, 52), names, fill="#555555", font=font(14))
        im.save(out_path)
        return out_path
    cols, rows = g["cols"], g["rows"]
    W, H = cols * CELL + PAD * 2, rows * CELL + PAD * 2
    im = Image.new("RGB", (W, H), "#f4f1e8")
    d = ImageDraw.Draw(im)
    px = lambda c: PAD + c * CELL
    py = lambda r: PAD + r * CELL
    rooms = [r for r in g["rooms"] if r["rect"]]
    held = {(c, w) for r in rooms for c in range(r["rect"]["col"], r["rect"]["col"] + r["rect"]["w"])
            for w in range(r["rect"]["row"], r["rect"]["row"] + r["rect"]["h"])}
    if rooms:
        min_c = min(r["rect"]["col"] for r in rooms); max_c = max(r["rect"]["col"] + r["rect"]["w"] for r in rooms)
        min_r = min(r["rect"]["row"] for r in rooms); max_r = max(r["rect"]["row"] + r["rect"]["h"] for r in rooms)
        for c in range(min_c, max_c):
            for w in range(min_r, max_r):
                if (c, w) not in held:
                    d.rectangle([px(c) + 3, py(w) + 3, px(c + 1) - 3, py(w + 1) - 3],
                                fill="#e9e3d0", outline="#c9bfa0")
    for c in range(cols + 1):
        d.line([px(c), py(0), px(c), py(rows)], fill="#d5cfbc")
    for r in range(rows + 1):
        d.line([px(0), py(r), px(cols), py(r)], fill="#d5cfbc")
    small = font(13)
    for r in rooms:
        fill, stroke = PALETTE.get(r["type"], ("#e0e0e0", "#666666"))
        rc = r["rect"]
        x0, y0 = px(rc["col"]) + 3, py(rc["row"]) + 3
        x1, y1 = px(rc["col"] + rc["w"]) - 3, py(rc["row"] + rc["h"]) - 3
        d.rounded_rectangle([x0, y0, x1, y1], radius=9, fill=fill, outline=stroke, width=2)
        label = r["label"].split("(")[0].strip()
        label = "".join(ch for ch in label if not ch.isdigit()).strip(" -._") or r["type"]
        fs = max(11, min(17, int((x1 - x0) / (0.62 * max(1, len(label))))))
        f = font(fs)
        tw = d.textlength(label, font=f)
        cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
        d.text((cx - tw / 2, cy - fs / 2 - (7 if r["printed"] and (y1 - y0) > 46 else 0)), label, fill="#222222", font=f)
        if r["printed"] and (y1 - y0) > 46:
            note = f"{r['printed']['w']/1000:.2f}x{r['printed']['h']/1000:.2f}m -> {rc['w']}x{rc['h']}"
            nw = d.textlength(note, font=small)
            d.text((cx - nw / 2, cy + fs / 2 - 2), note, fill="#555555", font=small)
    head = font(15)
    d.text((W / 2 - d.textlength("N O R T H", font=head) / 2, 10), "N O R T H", fill="#666666", font=head)
    d.text((PAD, H - 26), "WEST", fill="#666666", font=head)
    d.text((W / 2 - d.textlength("SOUTH", font=head) / 2, H - 26), "SOUTH", fill="#666666", font=head)
    d.text((W - PAD - d.textlength("EAST", font=head), H - 26), "EAST", fill="#666666", font=head)
    im.save(out_path)
    return out_path


def main():
    args = sys.argv[1:]
    files = sorted(glob.glob(os.path.join(REN, "*.geom.json")))
    if not args:
        print("usage: python render-png.py <id-substring>... | --all"); return
    if args != ["--all"]:
        files = [f for f in files if any(a in os.path.basename(f) for a in args)]
    for f in files:
        print(draw_one(f))


if __name__ == "__main__":
    main()

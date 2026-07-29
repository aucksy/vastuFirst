#!/usr/bin/env python
"""
EXPERIMENT E1 — is asking for the ZONE more reliable than asking for the RECTANGLE?

Why this is the decisive question for VastuFirst specifically: the engine scores a room
by which of the nine sectors it occupies. It does not care about exact coordinates. So if
the model can name a room's sector reliably even when its rectangle is wrong, we should ask
for the sector and stop pretending to precision we do not have.

Tested on the one fixture with exact ground truth, in both forms:
  - the clean render, where rectangles scored IoU 0.79
  - the phone photo, where rectangles collapsed to IoU 0.37

If zones survive the photo where rectangles did not, that settles the architecture.

Usage: python exp-zones.py
"""
import base64, json, os, sys, importlib.util

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("ev", os.path.join(HERE, "run-eval.py"))
ev = importlib.util.module_from_spec(spec); spec.loader.exec_module(ev)

SECTORS = ["NW", "N", "NE", "W", "C", "E", "SW", "S", "SE"]

PROMPT = """You are reading a home's floor plan. North is at the TOP of the image.

Divide the building's outer wall into a 3x3 grid of nine sectors:

    NW   N   NE
    W    C   E
    SW   S   SE

For each labelled room, say which sector its CENTRE falls in.

Return ONLY JSON:
{"rooms":[{"label":"<text exactly as printed>","sector":"NW|N|NE|W|C|E|SW|S|SE"}],
 "planConfidence":<0..1>}

Do not report doors, windows, furniture or dimension text as rooms.
"""


def true_sector(r):
    """Dominant sector of a ground-truth room, from its centre."""
    cx, cy = r["x"] + r["w"] / 2.0, r["y"] + r["h"] / 2.0
    col = 0 if cx < 1 / 3.0 else (1 if cx < 2 / 3.0 else 2)
    row = 0 if cy < 1 / 3.0 else (1 if cy < 2 / 3.0 else 2)
    return SECTORS[row * 3 + col]


def sector_from_rect(r):
    try:
        cx, cy = float(r["x"]) + float(r["w"]) / 2.0, float(r["y"]) + float(r["h"]) / 2.0
    except Exception:
        return None
    col = 0 if cx < 1 / 3.0 else (1 if cx < 2 / 3.0 else 2)
    row = 0 if cy < 1 / 3.0 else (1 if cy < 2 / 3.0 else 2)
    return SECTORS[row * 3 + col]


def norm(s):
    return "".join(c for c in str(s).upper() if c.isalnum())


def main():
    key = ev.load_env().get("GROQ_API_KEY")
    truth = json.load(open(os.path.join(HERE, "fixtures", "plan-01.json"), encoding="utf-8"))
    tmap = {norm(r["label"]): true_sector(r) for r in truth["rooms"]}

    for fid, ext, mime in (("plan-01", ".png", "image/png"),
                           ("plan-01-photo", ".jpg", "image/jpeg")):
        path = os.path.join(HERE, "fixtures", fid + ext)
        b64 = base64.b64encode(open(path, "rb").read()).decode()
        ev.PROMPT = PROMPT
        try:
            res = ev.call_groq(key, "qwen/qwen3.6-27b", b64, "none", mime)
            got = json.loads(res["choices"][0]["message"]["content"])
        except Exception as e:
            print("%s: ERROR %s" % (fid, str(e)[:70])); continue

        hit = miss = 0
        print("\n=== %s ===  (asking for SECTOR)" % fid)
        print("%-17s %-8s %-8s %s" % ("ROOM", "SAID", "TRUE", ""))
        for r in got.get("rooms", []):
            k = norm(r.get("label"))
            if k not in tmap:
                continue
            said, t = str(r.get("sector", "?")).upper(), tmap[k]
            ok = said == t
            hit += ok; miss += (not ok)
            print("%-17s %-8s %-8s %s" % (str(r.get("label"))[:17], said, t, "ok" if ok else "WRONG"))
        n = hit + miss
        print("sector accuracy: %d/%d = %.0f%%" % (hit, n, 100.0 * hit / n) if n else "no matches")

        # compare with the sector implied by the earlier RECTANGLE run
        raw = os.path.join(HERE, "out", "%s.qwen_qwen3.6-27b.json" % fid)
        if os.path.exists(raw):
            prev = json.load(open(raw, encoding="utf-8"))["reply"].get("rooms", [])
            rh = rm = 0
            for r in prev:
                k = norm(r.get("label"))
                if k not in tmap:
                    continue
                s = sector_from_rect(r)
                rh += (s == tmap[k]); rm += (s != tmap[k])
            if rh + rm:
                print("same rooms via RECTANGLES: %d/%d = %.0f%%" % (rh, rh + rm, 100.0 * rh / (rh + rm)))


if __name__ == "__main__":
    main()

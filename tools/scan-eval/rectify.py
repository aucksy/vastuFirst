#!/usr/bin/env python
"""
Two-pass experiment: ask the model for the four corners of the building's outer wall in
the photo, rectify the image to a square-on rectangle ourselves, then re-run extraction.

Hypothesis under test: the photo run's rectangles collapsed (IoU 0.79 -> 0.37) because of
perspective skew, not because the model cannot read the plan. Labels survived intact, which
points at geometry rather than comprehension. If rectifying restores IoU, the product answer
is "straighten before extracting" and the fix is arithmetic, not a better model.

Usage: python rectify.py plan-01-photo
"""
import base64, json, os, sys
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from importlib import import_module

ev = import_module("run-eval") if os.path.exists("run-eval.py") else None
HERE = os.path.dirname(os.path.abspath(__file__))

CORNER_PROMPT = """This is a photograph of an architectural floor plan, taken at an angle.

Find the OUTER WALL of the building — the single thick rectangle enclosing all the rooms.
Ignore the page edge, the title block, the north arrow and the desk behind it.

Return ONLY JSON:
{"corners":{"tl":[x,y],"tr":[x,y],"br":[x,y],"bl":[x,y]},"confidence":<0..1>}

Each x,y is a fraction of the IMAGE width/height (0..1), where 0,0 is the image's
top-left. tl/tr/br/bl are the outer wall's top-left, top-right, bottom-right and
bottom-left corners as they appear in the photo.
"""


def main():
    fid = sys.argv[1] if len(sys.argv) > 1 else "plan-01-photo"
    import importlib.util
    spec = importlib.util.spec_from_file_location("ev", os.path.join(HERE, "run-eval.py"))
    ev = importlib.util.module_from_spec(spec); spec.loader.exec_module(ev)

    key = ev.load_env().get("GROQ_API_KEY")
    model = "qwen/qwen3.6-27b"
    src = os.path.join(HERE, "fixtures", fid + ".jpg")
    im = Image.open(src).convert("RGB")
    W, H = im.size

    b64 = base64.b64encode(open(src, "rb").read()).decode()
    ev.PROMPT = CORNER_PROMPT
    res = ev.call_groq(key, model, b64, "none", "image/jpeg")
    got = json.loads(res["choices"][0]["message"]["content"])
    c = got["corners"]
    print("corners (image fractions):", json.dumps(c))
    print("corner-pass tokens:", res.get("usage", {}).get("total_tokens"))

    pts = [(c[k][0] * W, c[k][1] * H) for k in ("tl", "tr", "br", "bl")]
    ow = int(max(_d(pts[0], pts[1]), _d(pts[3], pts[2])))
    oh = int(max(_d(pts[0], pts[3]), _d(pts[1], pts[2])))
    coeffs = _persp([(0, 0), (ow, 0), (ow, oh), (0, oh)], pts)
    out = im.transform((ow, oh), Image.PERSPECTIVE, coeffs, Image.BICUBIC)
    dst = os.path.join(HERE, "fixtures", fid + "-rect.png")
    out.save(dst)
    print("wrote %s  %dx%d" % (dst, ow, oh))


def _d(a, b):
    return ((a[0] - b[0]) ** 2 + (a[1] - b[1]) ** 2) ** 0.5


def _persp(src, dst):
    import numpy as np
    m = []
    for (sx, sy), (dx, dy) in zip(src, dst):
        m.append([dx, dy, 1, 0, 0, 0, -sx * dx, -sx * dy])
        m.append([0, 0, 0, dx, dy, 1, -sy * dx, -sy * dy])
    return np.linalg.solve(np.array(m, float), np.array(src, float).reshape(8)).tolist()


if __name__ == "__main__":
    main()

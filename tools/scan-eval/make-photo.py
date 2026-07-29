#!/usr/bin/env python
"""
Turn a clean rendered fixture into something that looks like a phone photo of a printed
plan: perspective skew, rotation, uneven lighting, blur, sensor noise and JPEG artefacts.

The clean render is the easy case. Users will photograph a printout on a table, and the
published benchmarks that report ~51% accuracy are measured on real drawings, not clean
vector renders. This makes the gap measurable instead of assumed.

Usage: python make-photo.py plan-01
"""
import os, sys, random
from PIL import Image, ImageFilter, ImageEnhance

HERE = os.path.dirname(os.path.abspath(__file__))


def main():
    fid = sys.argv[1] if len(sys.argv) > 1 else "plan-01"
    random.seed(7)  # deterministic: the same "photo" every run
    src = os.path.join(HERE, "fixtures", fid + ".png")
    dst = os.path.join(HERE, "fixtures", fid + "-photo.jpg")

    im = Image.open(src).convert("RGB")
    w, h = im.size

    # 1. paper tint + margin, as if shot on a desk
    pad = int(w * 0.08)
    canvas = Image.new("RGB", (w + pad * 2, h + pad * 2), (168, 162, 152))
    canvas.paste(im, (pad, pad))
    im = canvas
    w, h = im.size

    # 2. perspective — camera held off-square. Maps the 4 corners inward unevenly.
    dx, dy = w * 0.035, h * 0.022
    coeffs = _perspective(
        [(0, 0), (w, 0), (w, h), (0, h)],
        [(dx, dy * 0.6), (w - dx * 0.4, 0), (w - dx * 0.2, h - dy), (dx * 0.7, h - dy * 0.3)],
    )
    im = im.transform((w, h), Image.PERSPECTIVE, coeffs, Image.BICUBIC, fillcolor=(168, 162, 152))

    # 3. slight rotation
    im = im.rotate(-2.4, resample=Image.BICUBIC, expand=False, fillcolor=(168, 162, 152))

    # 4. uneven lighting — a soft diagonal gradient, brighter top-left
    grad = Image.new("L", (w, h))
    px = grad.load()
    for y in range(0, h, 2):
        for x in range(0, w, 2):
            v = 255 - int(90 * ((x / w) * 0.6 + (y / h) * 0.4))
            px[x, y] = v
            if x + 1 < w: px[x + 1, y] = v
            if y + 1 < h:
                px[x, y + 1] = v
                if x + 1 < w: px[x + 1, y + 1] = v
    im = Image.composite(im, Image.new("RGB", (w, h), (30, 28, 26)), grad.point(lambda v: 60 + v * 0.76))

    # 5. focus + sensor noise
    im = im.filter(ImageFilter.GaussianBlur(0.7))
    im = ImageEnhance.Contrast(im).enhance(0.92)
    px = im.load()
    for _ in range(int(w * h * 0.02)):
        x, y = random.randrange(w), random.randrange(h)
        r, g, b = px[x, y]
        n = random.randint(-16, 16)
        px[x, y] = (max(0, min(255, r + n)), max(0, min(255, g + n)), max(0, min(255, b + n)))

    # 6. downscale to a typical upload size, then JPEG
    target = 1280
    if max(im.size) > target:
        s = target / max(im.size)
        im = im.resize((int(w * s), int(h * s)), Image.LANCZOS)
    im.save(dst, "JPEG", quality=72)
    print("wrote %s  %dx%d  %.0f KB" % (dst, im.size[0], im.size[1], os.path.getsize(dst) / 1024))


def _perspective(src, dst):
    """Solve the 8 transform coefficients mapping dst -> src (PIL's convention)."""
    import numpy as np
    m = []
    for (sx, sy), (dx, dy) in zip(src, dst):
        m.append([dx, dy, 1, 0, 0, 0, -sx * dx, -sx * dy])
        m.append([0, 0, 0, dx, dy, 1, -sy * dx, -sy * dy])
    A = np.array(m, dtype=float)
    B = np.array(src, dtype=float).reshape(8)
    return np.linalg.solve(A, B).tolist()


if __name__ == "__main__":
    main()

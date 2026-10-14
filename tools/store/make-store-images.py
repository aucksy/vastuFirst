"""
Store listing pictures, made from the app's OWN rendered screens (the CI goldens) — never a mock-up.

    python tools/store/make-store-images.py

Writes docs/store-listing/play/*.png:
  - phone-1..6.png   1080 x 1920 (Google Play: each side 320-3840 px, long side at most twice the
                     short side — a raw golden is 824 x 1830, which is 2.2:1 and would be refused)
  - feature-graphic.png  1024 x 500 (Play's required banner), cut from the website's share picture

Re-run it whenever a screen it uses changes. The caption under each picture is the listing's claim
for that screen; keep it true to what the screen shows.
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
GOLDENS = ROOT / "app/src/androidUnitTest/roborazzi"
FONTS = ROOT / "designsystem/src/commonMain/composeResources/font"
OUT = ROOT / "docs/store-listing/play"

PAPER = (248, 246, 240)
INK = (31, 36, 33)
MUTED = (110, 112, 104)
BORDER = (226, 222, 210)

# (golden, caption line 1, caption line 2, small note or None)
SHOTS = [
    ("addhome", "Draw it, photograph it,", "or upload your floor plan", None),
    ("scan-placed", "We read the rooms", "straight off your plan", "Sample plan"),
    ("report", "A clear score, and", "where to start", None),
    ("report-disputes-revealed", "Where the schools disagree,", "you see both sides", None),
    ("home", "Keep every home", "on your phone", None),
    ("legal", "Every rule says", "where it comes from", None),
]


def font(name: str, size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(str(FONTS / name), size)


def rounded(im: Image.Image, radius: int) -> Image.Image:
    mask = Image.new("L", im.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, im.size[0] - 1, im.size[1] - 1], radius, fill=255)
    out = Image.new("RGBA", im.size)
    out.paste(im, (0, 0), mask)
    return out


def phone_card(golden: str, line1: str, line2: str, note: str | None) -> Image.Image:
    W, H = 1080, 1920
    card = Image.new("RGB", (W, H), PAPER)
    d = ImageDraw.Draw(card)
    title = font("marcellus_regular.ttf", 64)
    for i, line in enumerate((line1, line2)):
        w = d.textlength(line, font=title)
        d.text(((W - w) / 2, 110 + i * 82), line, font=title, fill=INK)

    shot = Image.open(GOLDENS / golden / f"{golden}__baseline.png").convert("RGB")
    target_w = 820
    scale = target_w / shot.width
    shot = shot.resize((target_w, round(shot.height * scale)), Image.LANCZOS)
    top = 330
    room = H - top - 60
    shot = shot.crop((0, 0, target_w, min(shot.height, room)))
    framed = Image.new("RGB", (shot.width + 6, shot.height + 6), BORDER)
    framed.paste(shot, (3, 3))
    framed = rounded(framed, 36)
    card.paste(framed, ((W - framed.width) // 2, top), framed)

    if note:
        small = font("dm_mono_regular.ttf", 30)
        w = d.textlength(note, font=small)
        x, y = (W - w) / 2, top - 52
        d.text((x, y), note, font=small, fill=MUTED)
    return card


SAGE = (122, 158, 126)
GOLD = (201, 162, 39)


def brand_mark(size: int) -> Image.Image:
    """The app's mark (Website/public/favicon.svg), drawn at `size`: a 3x3 grid, a North arrow, a gold centre."""
    s = size / 40
    im = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle([4 * s, 4 * s, 36 * s, 36 * s], radius=3.5 * s, outline=SAGE, width=max(2, round(1.6 * s)))
    faint = SAGE + (128,)
    for v in (14.7, 25.3):
        d.line([v * s, 4 * s, v * s, 36 * s], fill=faint, width=max(1, round(0.6 * s)))
        d.line([4 * s, v * s, 36 * s, v * s], fill=faint, width=max(1, round(0.6 * s)))
    d.polygon([(20 * s, 6.5 * s), (23.6 * s, 17.4 * s), (20 * s, 15.1 * s), (16.4 * s, 17.4 * s)], fill=SAGE)
    r = 3.1 * s
    d.ellipse([20 * s - r, 20 * s - r, 20 * s + r, 20 * s + r], fill=GOLD)
    return im


def feature_graphic() -> Image.Image:
    """
    Play's 1024 x 500 banner. ⚠ NOT cut from the website's share picture: that one says "India's No. 1
    Vastu App", and Play's metadata policy refuses ranking claims ("No. 1", "best") in store graphics.
    Only claims the app itself makes: what it does, and that every rule is sourced.
    """
    W, H = 1024, 500
    g = Image.new("RGB", (W, H), PAPER)
    d = ImageDraw.Draw(g)
    # A quiet compass on the right, as on the website.
    cx, cy = 820, 250
    for r, a in ((210, 70), (150, 50), (90, 40)):
        mix = tuple(round(p + (q - p) * a / 255) for p, q in zip(PAPER, SAGE))
        d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=mix, width=2)
    mark = brand_mark(220)
    g.paste(mark, (cx - 110, cy - 110), mark)
    name = font("marcellus_regular.ttf", 92)
    d.text((64, 120), "Vastu", font=name, fill=INK)
    d.text((64 + d.textlength("Vastu", font=name), 120), "First", font=name, fill=SAGE)
    body = font("dm_sans_regular.ttf", 34)
    d.text((66, 250), "A room-by-room Vastu reading", font=body, fill=INK)
    d.text((66, 294), "of your own floor plan", font=body, fill=INK)
    small = font("dm_mono_regular.ttf", 22)
    d.text((66, 380), "EVERY RULE SOURCED  ·  NO SIGN-UP", font=small, fill=MUTED)
    return g


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for i, (golden, l1, l2, note) in enumerate(SHOTS, start=1):
        phone_card(golden, l1, l2, note).save(OUT / f"phone-{i}.png", optimize=True)
    feature_graphic().save(OUT / "feature-graphic.png", optimize=True)
    print(f"wrote {len(SHOTS)} phone pictures and the feature graphic to {OUT}")


if __name__ == "__main__":
    main()

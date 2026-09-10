"""plans-to-png.py — PNG copies of the sheet images the corpus runner cannot decode itself.

WHY: wall-snap.mjs reads pictures with jimp, which knows PNG and JPEG only. Of the 39 sheets in
Documents/Sample Floor plans, 14 are WebP, 2 are AVIF, and one (floor-plan-1.jpg) is a WebP wearing
a .jpg name — 17 recordings the runner could not check, and one that crashed it. This writes a
lossless PNG of every such sheet into tools/scan-eval/out/plans-png/ (git-ignored), which the runner
searches first. Real PNG and JPEG sheets are left alone: the runner reads those in place, so the
pixels it sees are the pixels the phone would decode.

Run:  python tools/scan-eval/plans-to-png.py            # default folder
      python tools/scan-eval/plans-to-png.py <folder>   # another folder of sheet images
Needs Pillow with WebP and AVIF support (pip install pillow).
"""
import sys
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
SRC = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "Documents" / "Sample Floor plans"
OUT = HERE / "out" / "plans-png"
OUT.mkdir(parents=True, exist_ok=True)


def real_kind(path: Path) -> str:
    head = path.read_bytes()[:12]
    if head[:2] == b"\x89P":
        return "png"
    if head[:2] == b"\xff\xd8":
        return "jpeg"
    if head[:4] == b"RIFF" and head[8:12] == b"WEBP":
        return "webp"
    if head[4:8] == b"ftyp":
        return "avif"
    return "?"


done, kept, failed = [], [], []
for f in sorted(SRC.iterdir()):
    if not f.is_file():
        continue
    kind = real_kind(f)
    if kind in ("png", "jpeg"):
        kept.append(f.name)
        continue
    target = OUT / (f.stem + ".png")
    try:
        with Image.open(f) as im:
            im.convert("RGB").save(target, "PNG")
        done.append(f"{f.name} ({kind}) -> {target.name}")
    except Exception as e:  # noqa: BLE001 — report and carry on; one bad file must not stop the rest
        failed.append(f"{f.name} ({kind}): {e}")

print(f"converted {len(done)}, left in place {len(kept)}, failed {len(failed)}  ->  {OUT}")
for line in done:
    print("  " + line)
for line in failed:
    print("  FAILED " + line)

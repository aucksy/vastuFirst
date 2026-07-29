#!/usr/bin/env python
r"""
STEP 2 of 2 — downloads the floor plans whose URLs step 1 collected.

RUN:   python fetch-plans.py
       (from anywhere; it finds floorplan-urls.txt in your Downloads folder)

Saves into  D:\Apps\VastuFirst\Documents\Sample Floor plans

Quality filter matters here. We need to read the ROOM NAMES printed on each
plan, so anything too small to hold legible text is worse than useless — it
would quietly drag the evaluation numbers down and make the model look worse
than it is. Images under MIN_SIDE px on the short edge are rejected, and so are
logos, icons and banners that happen to sit in the results.

Nothing here is committed to the repo: the target folder is git-ignored,
because these are other people's copyrighted images used as private test input.
"""
import hashlib
import io
import os
import sys
import urllib.request
import urllib.error

DEST = r"D:\Apps\VastuFirst\Documents\Sample Floor plans"
MAX_IMAGES = 50
MIN_SIDE = 600          # short edge, px — below this the room labels are unreadable
MIN_BYTES = 20 * 1024   # skip icons/sprites
TIMEOUT = 25

UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")


def find_url_file():
    if len(sys.argv) > 1:
        return sys.argv[1]
    home = os.path.expanduser("~")
    for d in (os.path.join(home, "Downloads"), home, os.getcwd()):
        p = os.path.join(d, "floorplan-urls.txt")
        if os.path.exists(p):
            return p
    return None


def fetch(url):
    """Return image bytes, or None. Sends a Referer — many hosts refuse hotlinks."""
    from urllib.parse import urlsplit
    origin = "{0.scheme}://{0.netloc}/".format(urlsplit(url))
    req = urllib.request.Request(url, headers={
        "User-Agent": UA,
        "Referer": origin,
        "Accept": "image/avif,image/webp,image/apng,image/*,*/*;q=0.8",
    })
    with urllib.request.urlopen(req, timeout=TIMEOUT) as r:
        ctype = (r.headers.get("Content-Type") or "").lower()
        if "image" not in ctype:
            raise ValueError("not an image (%s)" % ctype[:30])
        return r.read()


def main():
    src = find_url_file()
    if not src:
        print("Could not find floorplan-urls.txt.")
        print("Run step 1 (collect-urls.js) in the browser console first,")
        print("or pass the path:  python fetch-plans.py C:\\path\\to\\floorplan-urls.txt")
        return 1

    try:
        from PIL import Image
    except ImportError:
        print("Pillow is needed for the size check.  pip install Pillow")
        return 1

    urls = [l.strip() for l in open(src, encoding="utf-8") if l.strip().startswith("http")]
    if not urls:
        print("No URLs in " + src)
        return 1

    os.makedirs(DEST, exist_ok=True)
    print("reading  %s  (%d urls)" % (src, len(urls)))
    print("saving   %s\n" % DEST)

    seen_hashes, kept, tried = set(), 0, 0
    skipped_small = skipped_dupe = failed = 0

    for url in urls:
        if kept >= MAX_IMAGES:
            break
        tried += 1
        short = url.split("/")[-1][:44] or url[:44]
        try:
            data = fetch(url)
        except (urllib.error.HTTPError, urllib.error.URLError, ValueError, OSError) as e:
            failed += 1
            print("  skip  %-46s %s" % (short, str(e)[:34]))
            continue

        if len(data) < MIN_BYTES:
            skipped_small += 1
            print("  small %-46s %d KB" % (short, len(data) // 1024))
            continue

        h = hashlib.sha1(data).hexdigest()
        if h in seen_hashes:
            skipped_dupe += 1
            continue
        seen_hashes.add(h)

        try:
            im = Image.open(io.BytesIO(data))
            im.load()
            w, hgt = im.size
        except Exception as e:
            failed += 1
            print("  bad   %-46s %s" % (short, str(e)[:34]))
            continue

        if min(w, hgt) < MIN_SIDE:
            skipped_small += 1
            print("  small %-46s %dx%d" % (short, w, hgt))
            continue

        kept += 1
        ext = (im.format or "JPEG").lower().replace("jpeg", "jpg")
        out = os.path.join(DEST, "plan-%03d.%s" % (kept, ext))
        with open(out, "wb") as f:
            f.write(data)
        print("  ok    %-46s %dx%d -> %s" % (short, w, hgt, os.path.basename(out)))

    print("\n%d saved  ·  %d too small  ·  %d duplicates  ·  %d failed  ·  %d tried"
          % (kept, skipped_small, skipped_dupe, failed, tried))
    print("in: %s" % DEST)
    if kept < 15:
        print("\nFewer than 15 usable plans. Scroll further down the Google results and")
        print("re-run step 1 — more results load as you scroll.")
    return 0


if __name__ == "__main__":
    sys.exit(main())

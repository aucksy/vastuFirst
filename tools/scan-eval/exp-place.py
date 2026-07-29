#!/usr/bin/env python
"""
E3 — does the model actually PLACE rooms correctly on real plans?

WHY THIS EXISTS: every claim about placement so far rests on one synthetic fixture I drew myself
(8/8 rooms right on a clean render, 2/8 on a skewed photo). On the 30 real plans, `batch-real.py`
recorded how many rooms, their names and a coverage number -- but it threw the RECTANGLES away, so
nobody has ever checked whether the model puts real rooms in the right place.

That gap matters because the app's Placed-vs-Assisted gate uses coverage as a PROXY for geometric
accuracy, and on real plans coverage is confounded: a home with corridors, walls, ducts and a
stairwell legitimately has rooms that do not tile its footprint. Low coverage might mean bad
rectangles or might mean a normal house.

So: re-run the same 30 plans with the same prompt, KEEP the rectangles, and draw them over the
original image so they can be judged by eye. Output is `out/overlay/*.png` -- the point of this
script is the pictures, not the numbers.

Usage:  python exp-place.py [limit]
"""
import base64, importlib.util, json, os, sys, time, urllib.error

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(os.path.dirname(os.path.dirname(HERE)), "VastuFirst", "Documents", "Sample Floor plans")
if not os.path.isdir(SRC):
    SRC = r"D:\Apps\VastuFirst\Documents\Sample Floor plans"
OUT = os.path.join(HERE, "out", "real-plans-rects.json")
OVERLAY = os.path.join(HERE, "out", "overlay")

# Reuse the harness verbatim: same prompt, same downscale, same call. A different prompt would make
# these results incomparable with the numbers already in the plan doc.
_argv = list(sys.argv)                 # batch-real reads sys.argv in its own main(); restore ours after
spec = importlib.util.spec_from_file_location("br", os.path.join(HERE, "batch-real.py"))
br = importlib.util.module_from_spec(spec)
sys.argv = [sys.argv[0], "0"]          # stop batch-real's main() from running on import
spec.loader.exec_module(br)
sys.argv = _argv
ev = br.ev

PALETTE = [
    (220, 50, 47), (38, 139, 210), (133, 153, 0), (211, 54, 130), (181, 137, 0),
    (42, 161, 152), (108, 113, 196), (203, 75, 22), (0, 137, 123), (156, 39, 176),
]


def draw_overlay(path, reply, dest):
    """The prepped image with every returned rectangle drawn on it, labelled and numbered."""
    from PIL import Image, ImageDraw
    data, size = br.prep(path)
    import io
    im = Image.open(io.BytesIO(data)).convert("RGB")
    w, h = im.size
    d = ImageDraw.Draw(im, "RGBA")
    for i, r in enumerate(reply.get("rooms", [])):
        try:
            x, y, rw, rh = float(r["x"]), float(r["y"]), float(r["w"]), float(r["h"])
        except Exception:
            continue
        c = PALETTE[i % len(PALETTE)]
        box = [x * w, y * h, (x + rw) * w, (y + rh) * h]
        if box[2] <= box[0] or box[3] <= box[1]:
            continue
        d.rectangle(box, outline=c + (255,), width=3)
        d.rectangle([box[0], box[1], box[0] + 26, box[1] + 18], fill=c + (230,))
        d.text((box[0] + 8, box[1] + 4), str(i + 1), fill=(255, 255, 255))
        d.text((box[0] + 32, box[1] + 4), str(r.get("label", "?"))[:26], fill=c + (255,))
    im.save(dest)


def main():
    limit = int(sys.argv[1]) if len(sys.argv) > 1 and sys.argv[1] != "0" else 999
    key = ev.load_env().get("GROQ_API_KEY")
    if not key:
        print("No GROQ_API_KEY in .env"); return
    ev.PROMPT = br.PROMPT
    model = "qwen/qwen3.6-27b"

    os.makedirs(OVERLAY, exist_ok=True)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    results = json.load(open(OUT, encoding="utf-8")) if os.path.exists(OUT) else []
    done = {r["file"] for r in results}

    files = sorted(f for f in os.listdir(SRC)
                   if f.lower().split(".")[-1] in ("jpg", "jpeg", "png", "webp", "avif"))[:limit]

    for f in files:
        if f in done:
            continue
        path = os.path.join(SRC, f)
        try:
            data, size = br.prep(path)
        except Exception as e:
            print("%-14s PREP FAILED %s" % (f, str(e)[:40])); continue

        b64 = base64.b64encode(data).decode()
        got, usage, err = None, {}, None
        # ⚠ 8 000 tokens/MINUTE is the binding limit -- about three scans a minute across everything
        # using this key. A fixed 4x35s budget is not enough to walk a 30-plan corpus: the first run
        # captured 10 and gave up on the rest. Read the reset the API actually reports and wait that
        # long, and be patient rather than clever -- this is the same limit that makes the free tier
        # unable to serve a launched product (plan doc s3f).
        for attempt in range(20):
            try:
                res = ev.call_groq(key, model, b64, "none", "image/jpeg")
                usage = res.get("usage", {})
                got = json.loads(res["choices"][0]["message"]["content"])
                break
            except urllib.error.HTTPError as e:
                if e.code == 429:
                    wait = 40
                    for hdr in ("retry-after", "x-ratelimit-reset-tokens", "x-ratelimit-reset-requests"):
                        v = e.headers.get(hdr) if e.headers else None
                        if not v:
                            continue
                        try:
                            wait = max(wait, int(float(str(v).rstrip("smh"))) + 5)
                        except Exception:
                            pass
                        break
                    print("   429 on %s, waiting %ss (attempt %d)" % (f, wait, attempt + 1), flush=True)
                    time.sleep(min(wait, 90))
                    continue
                err = "HTTP %s" % e.code; break
            except Exception as e:
                err = str(e)[:60]; break

        if got is None:
            print("%-14s ERROR %s" % (f, err)); continue

        rec = {"file": f, "size": list(size), "reply": got, "usage": usage}
        results.append(rec)
        json.dump(results, open(OUT, "w", encoding="utf-8"), indent=1)

        if got.get("planType") == "2D_PLAN" and got.get("rooms"):
            dest = os.path.join(OVERLAY, os.path.splitext(f)[0] + ".png")
            try:
                draw_overlay(path, got, dest)
            except Exception as e:
                print("   overlay failed: %s" % str(e)[:50])
        print("%-14s %-11s rooms=%2d" % (f, got.get("planType"), len(got.get("rooms", []))))

    print("\n%d plans captured -> %s" % (len(results), OUT))
    print("overlays -> %s" % OVERLAY)


if __name__ == "__main__":
    main()

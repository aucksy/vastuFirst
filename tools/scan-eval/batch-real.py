#!/usr/bin/env python
r"""
Run the real downloaded plans through the model and record what comes back.

Two questions per plan, because the first real image revealed a case the
synthetic fixture could never show:

  1. WHAT KIND of image is this? A large share of "floor plans" online are 3D
     isometric marketing renders with the labels floating outside the building
     on leader lines. There is no top-down rectangle to extract from those, so
     the app must recognise and reject them rather than produce nonsense.
  2. If it IS a 2D plan, where are the rooms?

Also handles what the app itself will have to handle: webp/avif inputs, and
downscaling before upload (token cost scales with resolution).

Paced for Groq's free tier: 8000 tokens/minute, ~2600 per scan, so ~2.5 calls
a minute. Results are written after every plan, so a run that is interrupted
still leaves usable data.

Usage: python batch-real.py [limit]
"""
import base64, io, json, os, sys, time, urllib.error, importlib.util

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = r"D:\Apps\VastuFirst\Documents\Sample Floor plans"
OUT = os.path.join(HERE, "out", "real-plans.json")
MAX_EDGE = 1400
PACE = 26.0  # seconds between calls, to stay under 8000 TPM

spec = importlib.util.spec_from_file_location("ev", os.path.join(HERE, "run-eval.py"))
ev = importlib.util.module_from_spec(spec); spec.loader.exec_module(ev)

PROMPT = """You are looking at an image that is supposed to be a home's floor plan.

FIRST decide what kind of image it is:
  "2D_PLAN"    - a flat, top-down architectural drawing. The correct kind.
  "3D_RENDER"  - an isometric/perspective marketing render, often with furniture,
                 shadows, and room labels outside the building on leader lines.
  "NOT_A_PLAN" - anything else (elevation, photo, brochure page, site map).

THEN, only if it is "2D_PLAN", locate the rooms.

Return ONLY JSON:
{"planType":"2D_PLAN|3D_RENDER|NOT_A_PLAN",
 "hasRoomLabels":<true|false>,
 "northMarked":<true|false>,
 "rooms":[{"label":"<text exactly as printed>","x":<0..1>,"y":<0..1>,"w":<0..1>,"h":<0..1>,"confidence":<0..1>}],
 "planConfidence":<0..1>}

Coordinates are fractions of the building's OUTER WALL, origin at its top-left.
x,y = the room's top-left corner; w,h = its width and height. Ignore the page
margin, title block, branding and the north arrow.
Work from the printed ROOM LABELS and the walls around each one.
Do NOT report doors, windows, furniture or dimension text as rooms.
If planType is not "2D_PLAN", return an empty rooms list.
"""


def prep(path):
    """Downscale and convert to JPEG — the app will do exactly this before upload."""
    from PIL import Image
    im = Image.open(path)
    im = im.convert("RGB")
    w, h = im.size
    if max(w, h) > MAX_EDGE:
        s = MAX_EDGE / max(w, h)
        im = im.resize((int(w * s), int(h * s)), Image.LANCZOS)
    buf = io.BytesIO()
    im.save(buf, "JPEG", quality=88)
    return buf.getvalue(), im.size


def coverage(rooms, n=140):
    if not rooms:
        return 0.0
    hit = 0
    for gy in range(n):
        for gx in range(n):
            px, py = (gx + .5) / n, (gy + .5) / n
            for r in rooms:
                if r[0] <= px < r[0] + r[2] and r[1] <= py < r[1] + r[3]:
                    hit += 1
                    break
    return hit / (n * n)


def main():
    limit = int(sys.argv[1]) if len(sys.argv) > 1 else 999
    key = ev.load_env().get("GROQ_API_KEY")
    ev.PROMPT = PROMPT
    model = "qwen/qwen3.6-27b"

    files = sorted(f for f in os.listdir(SRC)
                   if f.lower().split(".")[-1] in ("jpg", "jpeg", "png", "webp", "avif"))[:limit]
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    results = []
    if os.path.exists(OUT):
        results = json.load(open(OUT, encoding="utf-8"))
    done = {r["file"] for r in results}

    print("%-14s %-11s %-7s %-6s %6s %8s  %s" %
          ("FILE", "TYPE", "labels", "north", "rooms", "coverage", "TOKENS"))
    for i, f in enumerate(files):
        if f in done:
            continue
        try:
            data, size = prep(os.path.join(SRC, f))
        except Exception as e:
            print("%-14s  PREP FAILED %s" % (f, str(e)[:40])); continue

        b64 = base64.b64encode(data).decode()
        got, usage, err = None, {}, None
        for attempt in range(3):
            try:
                res = ev.call_groq(key, model, b64, "none", "image/jpeg")
                usage = res.get("usage", {})
                got = json.loads(res["choices"][0]["message"]["content"])
                break
            except urllib.error.HTTPError as e:
                if e.code == 429:
                    time.sleep(35); continue
                err = "HTTP %s" % e.code; break
            except Exception as e:
                err = str(e)[:50]; break

        if got is None:
            print("%-14s  ERROR %s" % (f, err)); continue

        rooms = []
        for r in got.get("rooms", []):
            try:
                rooms.append((float(r["x"]), float(r["y"]), float(r["w"]), float(r["h"])))
            except Exception:
                pass
        cov = coverage(rooms)
        rec = {"file": f, "size": list(size), "planType": got.get("planType"),
               "hasRoomLabels": got.get("hasRoomLabels"), "northMarked": got.get("northMarked"),
               "nRooms": len(rooms), "coverage": round(cov, 3),
               "planConfidence": got.get("planConfidence"),
               "labels": [str(r.get("label", "?")) for r in got.get("rooms", [])],
               "usage": usage}
        results.append(rec)
        json.dump(results, open(OUT, "w", encoding="utf-8"), indent=1)

        print("%-14s %-11s %-7s %-6s %6d %7.0f%%  %s" % (
            f[:14], str(got.get("planType"))[:11], str(got.get("hasRoomLabels")),
            str(got.get("northMarked")), len(rooms), cov * 100,
            usage.get("total_tokens")))

        if i < len(files) - 1:
            time.sleep(PACE)

    # summary
    from collections import Counter
    types = Counter(r["planType"] for r in results)
    print("\n== %d plans ==" % len(results))
    for k, v in types.most_common():
        print("  %-12s %d" % (k, v))
    twod = [r for r in results if r["planType"] == "2D_PLAN" and r["nRooms"]]
    if twod:
        covs = sorted(r["coverage"] for r in twod)
        print("\n2D plans with rooms: %d" % len(twod))
        print("  coverage  min %.0f%%  median %.0f%%  max %.0f%%" %
              (covs[0] * 100, covs[len(covs) // 2] * 100, covs[-1] * 100))
        tk = [r["usage"].get("total_tokens", 0) for r in twod]
        print("  tokens    mean %d  ->  Rs %.2f per scan" %
              (sum(tk) / len(tk), sum(tk) / len(tk) / 1e6 * 1.0 * 88 * 0.5))
    print("\nwrote %s" % OUT)


if __name__ == "__main__":
    main()

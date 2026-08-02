#!/usr/bin/env python
"""Re-read ONE plan with the SHIPPED prompt, twice, and show what the model actually returns.

Point: the recorded reply for plan-010 (the owner's own sheet) has every coordinate on a 0.05
lattice, three distinct room sizes for fifteen rooms, and a box running 25% off the bottom of the
page. That is the fabrication signature, not a measurement. This confirms it live rather than
trusting a file recorded three days ago.
"""
import base64, importlib.util, io, json, os, sys, time

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = r"D:\Apps\VastuFirst\Documents\Sample Floor plans"
spec = importlib.util.spec_from_file_location("ev", os.path.join(HERE, "run-eval.py"))
ev = importlib.util.module_from_spec(spec); spec.loader.exec_module(ev)

PROMPT = open(os.path.join(
    r"D:\Apps\VastuFirst\shared\src\main\resources\scan", "plan-read-prompt.txt"), encoding="utf-8").read()
MAX_EDGE = 1400


def prep(path):
    from PIL import Image
    im = Image.open(path).convert("RGB")
    w, h = im.size
    if max(w, h) > MAX_EDGE:
        s = MAX_EDGE / max(w, h)
        im = im.resize((int(w * s), int(h * s)), Image.LANCZOS)
    buf = io.BytesIO(); im.save(buf, "JPEG", quality=88)
    return buf.getvalue(), im.size


def lattice(vals, step=0.05, tol=1e-6):
    return sum(1 for v in vals if abs(v / step - round(v / step)) < 1e-3) / max(1, len(vals))


def report(tag, got, size):
    rooms = got.get("rooms", [])
    xs = [r["x"] for r in rooms]; ys = [r["y"] for r in rooms]
    ws = [r["w"] for r in rooms]; hs = [r["h"] for r in rooms]
    areas = sorted({round(w * h, 5) for w, h in zip(ws, hs)})
    off = [r["label"] for r in rooms if r["x"] + r["w"] > 1.0001 or r["y"] + r["h"] > 1.0001]
    mean = sum(w * h for w, h in zip(ws, hs)) / max(1, len(rooms))
    var = sum((w * h - mean) ** 2 for w, h in zip(ws, hs)) / max(1, len(rooms))
    print(f"\n--- {tag} --- planType={got.get('planType')} rooms={len(rooms)} "
          f"conf={got.get('planConfidence')}")
    print(f"    distinct x: {sorted(set(xs))}")
    print(f"    distinct y: {sorted(set(ys))}")
    print(f"    distinct w: {sorted(set(ws))}   distinct h: {sorted(set(hs))}")
    print(f"    distinct AREAS: {len(areas)} for {len(rooms)} rooms -> {areas}")
    print(f"    on a 0.05 lattice: {lattice(xs + ys + ws + hs)*100:.0f}% of all coordinates")
    print(f"    area variation (uniform-box detector fires below 0.15): "
          f"{(var ** .5 / mean if mean else 0):.3f}")
    print(f"    boxes running OFF the unit square: {off or 'none'}")


def main():
    f = sys.argv[1] if len(sys.argv) > 1 else "plan-010.webp"
    n = int(sys.argv[2]) if len(sys.argv) > 2 else 2
    key = ev.load_env().get("GROQ_API_KEY")
    ev.PROMPT = PROMPT
    data, size = prep(os.path.join(SRC, f))
    b64 = base64.b64encode(data).decode()
    out = []
    for i in range(n):
        res = ev.call_groq(key, "qwen/qwen3.6-27b", b64, "none", "image/jpeg")
        got = json.loads(res["choices"][0]["message"]["content"])
        out.append(got)
        report(f"{f} live run {i+1}  ({res.get('usage',{}).get('total_tokens')} tokens)", got, size)
        if i < n - 1:
            time.sleep(28)
    json.dump({"file": f, "size": list(size), "runs": out},
              open(os.path.join(HERE, "out", "repro-%s.json" % f.split(".")[0]), "w", encoding="utf-8"), indent=1)
    print("\nwrote out/repro-%s.json" % f.split(".")[0])


main()

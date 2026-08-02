#!/usr/bin/env python
r"""E7b — the SHIPPED prompt plus ONE new field, and nothing else.

exp-printed.py bundled two changes (a size field AND a paragraph telling the model not to round its
coordinates). The anti-rounding half did nothing except push more boxes off the page, so it is
dropped. This isolates the half that worked: ask for the size the plan prints, keep every other word
identical, and check that room count and coordinates do not get worse.
"""
import base64, importlib.util, io, json, os, re, sys, time

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = r"D:\Apps\VastuFirst\Documents\Sample Floor plans"
spec = importlib.util.spec_from_file_location("ev", os.path.join(HERE, "run-eval.py"))
ev = importlib.util.module_from_spec(spec); spec.loader.exec_module(ev)
SHIPPED = open(r"D:\Apps\VastuFirst\shared\src\main\resources\scan\plan-read-prompt.txt",
               encoding="utf-8").read()

V3 = SHIPPED.replace(
    ' "rooms":[{"label":"<text exactly as printed>",'
    '"x":<0..1>,"y":<0..1>,"w":<0..1>,"h":<0..1>,"confidence":<0..1>}],',
    ' "rooms":[{"label":"<room name exactly as printed>",\n'
    '           "size":"<the dimensions printed with that room, copied exactly, e.g.\n'
    '                    3.72m X 4.50m or 12\'-2\\" x 14\'-9\\" or 2950X4200 — \\"\\" if none is printed>",\n'
    '           "x":<0..1>,"y":<0..1>,"w":<0..1>,"h":<0..1>,"confidence":<0..1>}],'
).replace(
    'Work from the printed ROOM LABELS and the walls around each one.',
    'Work from the printed ROOM LABELS and the walls around each one.\n'
    'Most Indian plans print each room\'s dimensions beside its name. Copy that text into "size"\n'
    'character for character. Do not convert it, do not calculate it, and never invent one — leave\n'
    '"size" empty if the drawing does not print it.'
)
assert V3 != SHIPPED and '"size"' in V3, "prompt edit did not apply"
MAX_EDGE = 1400
DIM = re.compile(r"\d[\d.,'\u2019\u201d\"\u00bd\u00bc\u00be\s/-]*[xX\u00d7]\s*\d")


def prep(path):
    from PIL import Image
    im = Image.open(path).convert("RGB")
    w, h = im.size
    if max(w, h) > MAX_EDGE:
        s = MAX_EDGE / max(w, h)
        im = im.resize((int(w * s), int(h * s)), Image.LANCZOS)
    buf = io.BytesIO(); im.save(buf, "JPEG", quality=88)
    return buf.getvalue(), im.size


def main():
    plans = sys.argv[1:] or ["plan-010.webp", "plan-014.jpg", "plan-009.webp", "plan-019.jpg",
                             "plan-008.webp", "plan-006.webp", "plan-022.png", "plan-020.jpg",
                             "plan-002.webp", "plan-031.jpg"]
    key = ev.load_env().get("GROQ_API_KEY")
    ev.PROMPT = V3
    out = []
    print("%-16s %-11s %6s %8s %8s %6s" % ("plan", "type", "rooms", "w/ size", "off-page", "tokens"))
    print("-" * 60)
    for f in plans:
        data, size = prep(os.path.join(SRC, f))
        b64 = base64.b64encode(data).decode()
        got = None
        for a in range(3):
            try:
                res = ev.call_groq(key, "qwen/qwen3.6-27b", b64, "none", "image/jpeg")
                got = json.loads(res["choices"][0]["message"]["content"]); break
            except Exception as e:
                if a == 2: print("%-16s ERROR %s" % (f, str(e)[:40]))
                else: time.sleep(35)
        if got is None: continue
        rooms = got.get("rooms", [])
        sized = sum(1 for r in rooms if DIM.search(str(r.get("size") or "")))
        off = sum(1 for r in rooms if float(r.get("x", 0)) + float(r.get("w", 0)) > 1.0001
                  or float(r.get("y", 0)) + float(r.get("h", 0)) > 1.0001)
        print("%-16s %-11s %6d %8d %8d %6s" % (f, str(got.get("planType"))[:11], len(rooms), sized,
                                               off, res.get("usage", {}).get("total_tokens")))
        out.append({"file": f, "size": list(size), "V3": got,
                    "usage": res.get("usage", {})})
        json.dump(out, open(os.path.join(HERE, "out", "exp-size-field.json"), "w", encoding="utf-8"), indent=1)
        time.sleep(26)
    json.dump({"prompt": V3}, open(os.path.join(HERE, "out", "prompt-v3.json"), "w", encoding="utf-8"), indent=1)
    print("\nwrote out/exp-size-field.json")


main()

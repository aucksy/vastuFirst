#!/usr/bin/env python
"""
EXPERIMENT E-LEGEND — can the model resolve a numbered plan?

Owner asked to support plans where rooms are numbered on the drawing with a key
printed alongside ("AI can handle that right"). plan-018 is exactly that case: the
first pass returned labels "1, 2, 3 ... 15", which passes a naive has-labels check
while carrying no usable room names.

This asks explicitly for the legend to be resolved, and reports both the raw number
and the name it resolved to, so a wrong pairing is visible rather than hidden.

Usage: python exp-legend.py [file]
"""
import base64, io, json, os, sys, importlib.util

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = r"D:\Apps\VastuFirst\Documents\Sample Floor plans"
spec = importlib.util.spec_from_file_location("ev", os.path.join(HERE, "run-eval.py"))
ev = importlib.util.module_from_spec(spec); spec.loader.exec_module(ev)

PROMPT = """You are reading a flat, top-down architectural floor plan.

Some plans do not print room names inside the rooms. Instead each room carries a
NUMBER, and a key or legend printed at the side maps each number to a room name.

First, look for such a key. Then for every numbered space on the drawing, report the
number AND the name the key gives for it.

Return ONLY JSON:
{"hasLegend":<true|false>,
 "legend":{"<number>":"<name from the key>"},
 "rooms":[{"number":"<number as printed, or null>",
           "label":"<the resolved room name>",
           "x":<0..1>,"y":<0..1>,"w":<0..1>,"h":<0..1>}],
 "planConfidence":<0..1>}

Coordinates are fractions of the building's OUTER WALL, origin top-left.
If there is no key, set hasLegend false and report whatever names are printed.
Do not report doors, windows, furniture or dimension text as rooms.
"""


def prep(path, max_edge=1400):
    from PIL import Image
    im = Image.open(path).convert("RGB")
    w, h = im.size
    if max(w, h) > max_edge:
        s = max_edge / max(w, h)
        im = im.resize((int(w * s), int(h * s)), Image.LANCZOS)
    b = io.BytesIO(); im.save(b, "JPEG", quality=88)
    return b.getvalue()


def main():
    f = sys.argv[1] if len(sys.argv) > 1 else "plan-018.avif"
    key = ev.load_env().get("GROQ_API_KEY")
    ev.PROMPT = PROMPT
    b64 = base64.b64encode(prep(os.path.join(SRC, f))).decode()
    try:
        res = ev.call_groq(key, "qwen/qwen3.6-27b", b64, "none", "image/jpeg")
        got = json.loads(res["choices"][0]["message"]["content"])
    except Exception as e:
        print("ERROR %s" % str(e)[:120]); return 1

    print("file: %s" % f)
    print("hasLegend: %s   confidence: %s" % (got.get("hasLegend"), got.get("planConfidence")))
    leg = got.get("legend") or {}
    print("\nlegend read (%d entries):" % len(leg))
    for k in sorted(leg, key=lambda x: (len(str(x)), str(x))):
        print("   %-4s -> %s" % (k, leg[k]))
    print("\nrooms (%d):" % len(got.get("rooms", [])))
    for r in got.get("rooms", []):
        print("   %-4s %-22s  x%.2f y%.2f w%.2f h%.2f" % (
            str(r.get("number")), str(r.get("label"))[:22],
            float(r.get("x", 0)), float(r.get("y", 0)),
            float(r.get("w", 0)), float(r.get("h", 0))))
    print("\ntokens: %s" % res.get("usage", {}).get("total_tokens"))
    return 0


if __name__ == "__main__":
    sys.exit(main())

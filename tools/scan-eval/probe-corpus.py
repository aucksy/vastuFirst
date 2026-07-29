#!/usr/bin/env python
"""
Probe real plans that have NO ground truth, to test the SAFETY path rather than accuracy.

ROBIN plans carry no room labels — rooms are identified by furniture symbols alone. That is
the opposite of the case the prompt targets ("read the printed labels"), which makes it the
right corpus for one specific question:

    when the model cannot really read a plan, does it SAY SO, or does it invent rooms
    with high confidence?

Given that planConfidence was 0.95 on both a 100% and a 25% read (plan doc s3e finding 2),
the honest expectation is that it invents. This measures it instead of assuming it.

Usage: python probe-corpus.py [n]
"""
import base64, glob, itertools, json, os, sys, importlib.util

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("ev", os.path.join(HERE, "run-eval.py"))
ev = importlib.util.module_from_spec(spec); spec.loader.exec_module(ev)


def coverage(rooms, n=160):
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
    want = int(sys.argv[1]) if len(sys.argv) > 1 else 6
    key = ev.load_env().get("GROQ_API_KEY")
    model = "qwen/qwen3.6-27b"

    files = []
    for d in ("Dataset_3rooms", "Dataset_4rooms", "Dataset_5rooms"):
        files += sorted(glob.glob(os.path.join(HERE, "corpus", "ROBIN", d, "*.jpg")))[:want // 3 or 1]
    files = files[:want]

    print("%-26s %6s %7s %9s %9s  %s" % ("PLAN", "rooms", "unread", "selfconf", "coverage", "LABELS RETURNED"))
    n_claimed = n_unread = 0
    for f in files:
        b64 = base64.b64encode(open(f, "rb").read()).decode()
        try:
            res = ev.call_groq(key, model, b64, "none", "image/jpeg")
            got = json.loads(res["choices"][0]["message"]["content"])
        except Exception as e:
            print("%-26s  ERROR %s" % (os.path.basename(f), str(e)[:60])); continue

        rooms = []
        labs = []
        for r in got.get("rooms", []):
            try:
                rooms.append((float(r["x"]), float(r["y"]), float(r["w"]), float(r["h"])))
                labs.append(str(r.get("label", "?")))
            except Exception:
                pass
        unread = bool(got.get("unreadable"))
        if unread:
            n_unread += 1
        if rooms and not unread:
            n_claimed += 1
        print("%-26s %6d %7s %9.2f %8.0f%%  %s" % (
            os.path.basename(f), len(rooms), "YES" if unread else "no",
            float(got.get("planConfidence") or 0), coverage(rooms) * 100,
            ", ".join(labs[:5])[:58]))

    print("\n%d/%d plans: model claimed readable rooms on an UNLABELLED plan" % (n_claimed, len(files)))
    print("%d/%d plans: model correctly flagged unreadable" % (n_unread, len(files)))


if __name__ == "__main__":
    main()

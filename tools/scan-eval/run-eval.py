#!/usr/bin/env python
"""
Scan-eval harness — score a vision model against a fixture with known ground truth.

Usage:  python run-eval.py <fixture-id> [model]

Reads GROQ_API_KEY (or OPENROUTER_API_KEY) from ../../.env. Prints the model's rooms,
the IoU against ground truth, a label-accuracy score, and the REAL token usage so the
per-scan cost estimate in docs/SCAN-PLAN-READING-PLAN.md can be checked against fact
rather than arithmetic.

Deliberately does NOT ask the model for doors: door detection benchmarks at 39% and the
front door is the highest-weighted input the engine scores (see plan doc s3c).
"""
import base64, json, os, sys, urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))

PROMPT = """You are reading an architectural floor plan of a home.

Return ONLY JSON matching this shape:
{"rooms":[{"label":"<text exactly as printed on the plan>",
           "x":<0..1>,"y":<0..1>,"w":<0..1>,"h":<0..1>,
           "confidence":<0..1>}],
 "planConfidence":<0..1>,
 "unreadable":<true|false>}

Rules:
- Coordinates are fractions of the OUTER WALL of the building, origin at its TOP-LEFT.
  x,y = the room's top-left corner. w,h = its width and height. Ignore the page margin,
  the title block and the north arrow.
- Work from the printed ROOM LABELS and the wall lines around each one. Every labelled
  space is a room, including balconies, toilets and pooja rooms.
- Do NOT report doors, windows, furniture or dimension text as rooms.
- confidence: how sure you are of THAT room's rectangle.
- If the plan has no readable room labels, set unreadable=true and return an empty list.
"""


def load_env():
    env = {}
    p = os.path.join(ROOT, ".env")
    if os.path.exists(p):
        for line in open(p, encoding="utf-8"):
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                k, v = line.split("=", 1)
                env[k.strip()] = v.strip()
    return env


def call_groq(key, model, b64, reasoning, mime="image/png"):
    body = {
        "model": model,
        "messages": [{"role": "user", "content": [
            {"type": "text", "text": PROMPT},
            {"type": "image_url", "image_url": {"url": "data:%s;base64,%s" % (mime, b64)}},
        ]}],
        "response_format": {"type": "json_object"},
        "temperature": 0,
    }
    if reasoning:
        body["reasoning_effort"] = reasoning
    req = urllib.request.Request(
        "https://api.groq.com/openai/v1/chat/completions",
        data=json.dumps(body).encode(),
        headers={
            "Authorization": "Bearer " + key,
            "Content-Type": "application/json",
            # Cloudflare fronts the API and 403s urllib's default UA with error 1010.
            "User-Agent": "curl/8.4.0",
        },
    )
    with urllib.request.urlopen(req, timeout=180) as r:
        return json.loads(r.read())


def iou(a, b):
    ax2, ay2 = a["x"] + a["w"], a["y"] + a["h"]
    bx2, by2 = b["x"] + b["w"], b["y"] + b["h"]
    ix = max(0.0, min(ax2, bx2) - max(a["x"], b["x"]))
    iy = max(0.0, min(ay2, by2) - max(a["y"], b["y"]))
    inter = ix * iy
    union = a["w"] * a["h"] + b["w"] * b["h"] - inter
    return inter / union if union > 0 else 0.0


def norm(s):
    return "".join(c for c in s.upper() if c.isalnum())


def main():
    fid = sys.argv[1] if len(sys.argv) > 1 else "plan-01"
    model = sys.argv[2] if len(sys.argv) > 2 else "qwen/qwen3.6-27b"
    reasoning = os.environ.get("REASONING", "none")

    env = load_env()
    key = env.get("GROQ_API_KEY")
    if not key:
        print("no GROQ_API_KEY in .env"); return 1

    # a "-photo" variant scores against its clean parent's ground truth
    truth_id = fid
    for suffix in ("-photo", "-jpeg", "-rect"):
        if truth_id.endswith(suffix):
            truth_id = truth_id[: -len(suffix)]
    truth = json.load(open(os.path.join(HERE, "fixtures", truth_id + ".json"), encoding="utf-8"))

    img_path, mime = None, "image/png"
    for ext, m in ((".png", "image/png"), (".jpg", "image/jpeg")):
        p = os.path.join(HERE, "fixtures", fid + ext)
        if os.path.exists(p):
            img_path, mime = p, m
            break
    if not img_path:
        print("no image for fixture " + fid); return 1

    png = open(img_path, "rb").read()
    b64 = base64.b64encode(png).decode()
    print("fixture %s  image %.0f KB (%s)  model %s  reasoning=%s\n"
          % (fid, len(png) / 1024, mime, model, reasoning))

    try:
        res = call_groq(key, model, b64, reasoning, mime)
    except urllib.error.HTTPError as e:
        print("HTTP %s\n%s" % (e.code, e.read().decode()[:800])); return 1

    usage = res.get("usage", {})
    raw = res["choices"][0]["message"]["content"]
    try:
        got = json.loads(raw)
    except Exception:
        print("NON-JSON REPLY:\n" + raw[:1500]); return 1

    out = os.path.join(HERE, "out")
    os.makedirs(out, exist_ok=True)
    with open(os.path.join(out, "%s.%s.json" % (fid, model.replace("/", "_"))), "w", encoding="utf-8") as f:
        json.dump({"fixture": fid, "model": model, "usage": usage, "reply": got}, f, indent=2)

    rooms = got.get("rooms", [])
    print("model returned %d rooms (truth has %d) · planConfidence=%s · unreadable=%s\n"
          % (len(rooms), len(truth["rooms"]), got.get("planConfidence"), got.get("unreadable")))

    # greedy match each truth room to its best-IoU prediction
    used, matched = set(), 0
    print("%-17s %-17s %6s  %s" % ("TRUTH", "MATCHED TO", "IoU", "VERDICT"))
    total_iou = 0.0
    for t in truth["rooms"]:
        best, bi = None, -1.0
        for i, r in enumerate(rooms):
            if i in used:
                continue
            try:
                v = iou(t, {k: float(r[k]) for k in ("x", "y", "w", "h")})
            except Exception:
                continue
            if v > bi:
                best, bi = i, v
        lab = rooms[best].get("label", "?") if best is not None else "-"
        ok_lab = best is not None and norm(t["label"]) == norm(lab)
        hit = bi >= 0.5
        if best is not None and hit:
            used.add(best); matched += 1
        total_iou += max(bi, 0.0)
        print("%-17s %-17s %6.2f  %s%s" % (
            t["label"][:17], str(lab)[:17], max(bi, 0.0),
            "MATCH" if hit else "MISS",
            "" if ok_lab else "  (label differs)"))

    n = len(truth["rooms"])
    print("\nrooms found (IoU>=0.5): %d/%d = %.0f%%   mean IoU: %.2f"
          % (matched, n, 100.0 * matched / n, total_iou / n))
    print("tokens: prompt=%s completion=%s total=%s"
          % (usage.get("prompt_tokens"), usage.get("completion_tokens"), usage.get("total_tokens")))
    pin, pout = 0.285, 1.99
    cost = usage.get("prompt_tokens", 0) / 1e6 * pin + usage.get("completion_tokens", 0) / 1e6 * pout
    print("cost this call: $%.5f  =  Rs %.3f  (at Rs 88/$)" % (cost, cost * 88))
    return 0


if __name__ == "__main__":
    sys.exit(main())

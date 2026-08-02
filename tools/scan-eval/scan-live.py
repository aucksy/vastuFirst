#!/usr/bin/env python
r"""
Scan ONE plan image with the app's OWN reader — same prompt file, same model, same settings —
and record the reply where render-grid.mjs can draw it.

This is the "see it yourself" loop (owner request, 2 Aug 2026): when the owner reports a bad
grid for a plan, a session reproduces it end to end without his phone:

    python tools/scan-eval/scan-live.py "path\or\URL\to\plan.png"
    node tools/scan-eval/render-grid.mjs --only=X --reply=tools/scan-eval/out/live/<name>.json
    python tools/scan-eval/render-png.py <name>          # then LOOK at the png next to the plan

Faithful to the app (GroqPlanReader): prompt text from shared/src/main/resources/scan/
plan-read-prompt.txt, model/effort/temperature/UA from reader-config.json, image downscaled to
1400px JPEG q88 before upload exactly as the app does. Costs one real API call (~Rs 0.21).

Replies land in out/live/ — kept OUT of out/*.json, which are the frozen corpus recordings.
"""
import base64, io, json, os, re, sys, urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
RES = os.path.join(ROOT, "shared", "src", "main", "resources", "scan")
OUT = os.path.join(HERE, "out", "live")
MAX_EDGE, JPEG_Q = 1400, 88


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


def fetch(src):
    if re.match(r"^https?://", src):
        req = urllib.request.Request(src, headers={"User-Agent": (
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")})
        with urllib.request.urlopen(req, timeout=30) as r:
            data = r.read()
        name = re.sub(r"[^\w.-]+", "_", src.rstrip("/").split("/")[-1]) or "plan"
        return data, name
    return open(src, "rb").read(), os.path.basename(src)


def prep(raw):
    from PIL import Image
    im = Image.open(io.BytesIO(raw)).convert("RGB")
    w, h = im.size
    if max(w, h) > MAX_EDGE:
        s = MAX_EDGE / max(w, h)
        im = im.resize((int(w * s), int(h * s)), Image.LANCZOS)
    buf = io.BytesIO()
    im.save(buf, "JPEG", quality=JPEG_Q)
    return buf.getvalue(), im.size


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return 1
    src = sys.argv[1]
    cfg = json.load(open(os.path.join(RES, "reader-config.json"), encoding="utf-8"))
    # promptResource is a classpath path ("/scan/plan-read-prompt.txt"); RES is that folder on disk.
    prompt = open(os.path.join(RES, os.path.basename(cfg["promptResource"])), encoding="utf-8").read()
    key = load_env().get("GROQ_API_KEY")
    if not key:
        print("no GROQ_API_KEY in .env")
        return 1

    raw, name = fetch(src)
    jpeg, size = prep(raw)
    stem = re.sub(r"\.(png|jpg|jpeg|webp|avif|gif)$", "", name, flags=re.I)
    print("scanning %s  %dx%d  %.0f KB upload  model %s" % (name, size[0], size[1], len(jpeg) / 1024, cfg["model"]))

    body = {
        "model": cfg["model"],
        "messages": [{"role": "user", "content": [
            {"type": "text", "text": prompt},
            {"type": "image_url", "image_url": {"url": "data:image/jpeg;base64," + base64.b64encode(jpeg).decode()}},
        ]}],
        "response_format": {"type": "json_object"},
        "temperature": cfg.get("temperature", 0),
    }
    if cfg.get("reasoningEffort"):
        body["reasoning_effort"] = cfg["reasoningEffort"]
    req = urllib.request.Request(cfg["endpoint"], data=json.dumps(body).encode(), headers={
        "Authorization": "Bearer " + key,
        "Content-Type": "application/json",
        "User-Agent": cfg.get("userAgent", "curl/8.4.0"),
    })
    with urllib.request.urlopen(req, timeout=180) as r:
        res = json.loads(r.read())
    reply = json.loads(res["choices"][0]["message"]["content"])
    usage = res.get("usage", {})

    os.makedirs(OUT, exist_ok=True)
    out_path = os.path.join(OUT, stem + ".json")
    json.dump({"file": name, "imageSize": list(size), "prompt": "v3", "reply": reply},
              open(out_path, "w", encoding="utf-8"), indent=1)
    print("planType=%s rooms=%d tokens=%s" % (
        reply.get("planType"), len(reply.get("rooms", [])), usage.get("total_tokens")))
    for room in reply.get("rooms", []):
        print("  %-24s size=%-22s @ %.2f,%.2f %.2fx%.2f" % (
            room.get("label", "?")[:24], room.get("size", ""), room.get("x", -1),
            room.get("y", -1), room.get("w", -1), room.get("h", -1)))
    print("wrote", out_path)
    return 0


if __name__ == "__main__":
    sys.exit(main())

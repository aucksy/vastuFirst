#!/usr/bin/env python
r"""
Scan ONE plan image with a CANDIDATE reader under the app's exact conditions —
the SAME prompt file and the SAME 1400px/q88 JPEG downscale as scan-live.py — so the only
variable in a head-to-head against the app's current reader is the model itself.

    python tools/scan-eval/scan-candidate.py <image path or URL> [--model=gemini-3.6-flash] [--tag=r1]

Two providers, routed by the model id:
  no slash   -> Google's Gemini API           (GEMINI_API_KEY),   e.g. gemini-3.6-flash
  with slash -> OpenRouter, OpenAI-compatible (OPENROUTER_API_KEY), e.g. anthropic/claude-haiku-4.5

Replies land in out/live/<stem>.<model>.<tag>.json — the live class, safe to re-record,
never the frozen corpus. The file shape matches scan-live.py ({file,imageSize,prompt,reply})
so render-grid.mjs --reply and resemblance.mjs work unchanged; usage/modelVersion ride
along for the cost column of the scoreboard.

⚠ HARD RULE (CLAUDE.md 2c): every invocation is one paid image scan. The owner approves
the exact scan count BEFORE any batch runs.
"""
import base64, hashlib, json, os, re, sys, urllib.error, urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
OUT = os.path.join(HERE, "out", "live")
ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent"

sys.path.insert(0, HERE)
from importlib import import_module
scan_live = import_module("scan-live")  # reuse load_env / fetch / prep — same downscale, same .env


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    opts = dict(a[2:].split("=", 1) for a in sys.argv[1:] if a.startswith("--") and "=" in a)
    if not args:
        print(__doc__)
        return 1
    model = opts.get("model", "gemini-3.6-flash")
    tag = opts.get("tag", "r1")

    # --prompt=<path> swaps the prompt file for a candidate variant (e.g. the v4 building-box
    # experiment). Default stays the app's live prompt so head-to-heads measure the model alone.
    prompt_path = opts.get(
        "prompt",
        os.path.join(ROOT, "shared", "src", "main", "resources", "scan", "plan-read-prompt.txt"),
    )
    prompt = open(prompt_path, encoding="utf-8").read()
    openrouter = "/" in model
    key_name = "OPENROUTER_API_KEY" if openrouter else "GEMINI_API_KEY"
    key = scan_live.load_env().get(key_name)
    if not key:
        print("no %s in .env" % key_name)
        return 1

    raw, name = scan_live.fetch(args[0])
    jpeg, size = scan_live.prep(raw)
    stem = re.sub(r"\.(png|jpg|jpeg|webp|avif|gif)$", "", name, flags=re.I)
    print("scanning %s  %dx%d  %.0f KB upload  model %s  tag %s" % (name, size[0], size[1], len(jpeg) / 1024, model, tag))

    b64 = base64.b64encode(jpeg).decode()
    if openrouter:
        url = "https://openrouter.ai/api/v1/chat/completions"
        body = {
            "model": model,
            "messages": [{"role": "user", "content": [
                {"type": "text", "text": prompt},
                {"type": "image_url", "image_url": {"url": "data:image/jpeg;base64," + b64}},
            ]}],
            "response_format": {"type": "json_object"},
            "temperature": 0.0,
            # ⭐ Ask OpenRouter for what it CHARGED (29 Sep 2026). The scoreboard's rupee column used
            # to be worked out from token counts times a price read once, and prices move: today's
            # reader was listed at ~Rs 0.09 a read in August and is ~Rs 0.25 at today's list price.
            # With this, every recording carries the real charge and nothing has to be estimated.
            "usage": {"include": True},
        }
        headers = {"Authorization": "Bearer " + key, "Content-Type": "application/json"}
    else:
        url = ENDPOINT % model
        body = {
            "contents": [{"parts": [
                {"text": prompt},
                {"inline_data": {"mime_type": "image/jpeg", "data": b64}},
            ]}],
            "generationConfig": {"temperature": 0.0, "response_mime_type": "application/json"},
        }
        headers = {"x-goog-api-key": key, "Content-Type": "application/json"}

    req = urllib.request.Request(url, data=json.dumps(body).encode(), headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=300) as r:
            res = json.loads(r.read())
    except urllib.error.HTTPError as e:
        print("HTTP %s: %s" % (e.code, e.read().decode()[:800]))
        return 1

    raw_text = None
    if openrouter:
        if "choices" not in res:
            print("no choices in reply: %s" % json.dumps(res)[:500])
            return 1
        text = res["choices"][0]["message"]["content"] or ""
        text = re.sub(r"^```(json)?|```$", "", text.strip(), flags=re.M)
        # ⚠ A paid read is never thrown away because its wrapping was odd (29 Sep 2026, before the
        # direction trial's 140-scan round on four models this tool had never met). A reply that is not
        # bare JSON is cut to its outermost {...}; one that still does not parse is KEPT, raw, with an
        # empty reply, so it is counted, scored as finding nothing, and can be read by eye later.
        try:
            reply = json.loads(text)
        except ValueError:
            start, end = text.find("{"), text.rfind("}")
            try:
                reply = json.loads(text[start:end + 1]) if 0 <= start < end else None
            except ValueError:
                reply = None
            if reply is None:
                print("reply is not JSON; kept raw: %r" % text[:300])
                reply = {"planType": "UNREADABLE", "rooms": []}
                raw_text = text
        u = res.get("usage", {})
        usage = {"promptTokenCount": u.get("prompt_tokens"), "candidatesTokenCount": u.get("completion_tokens"),
                 "thoughtsTokenCount": (u.get("completion_tokens_details") or {}).get("reasoning_tokens", 0),
                 # US dollars actually charged for this read (present because the request asked).
                 "costUsd": u.get("cost")}
        model_version = res.get("model", model)
    else:
        parts = res["candidates"][0]["content"]["parts"]
        text = "".join(p.get("text", "") for p in parts if not p.get("thought"))
        reply = json.loads(text)
        usage = res.get("usageMetadata", {})
        model_version = res.get("modelVersion", model)

    os.makedirs(OUT, exist_ok=True)
    out_path = os.path.join(OUT, "%s.%s.%s.json" % (stem, model.replace("/", "_"), tag))
    # ⚠ Was a hard-coded "v3" here too — see the note in scan-live.py. A fingerprint of the words
    # actually sent, so a recording can never claim a prompt it did not use.
    prompt_id = "sha1:" + hashlib.sha1(prompt.encode("utf-8")).hexdigest()[:12]
    rec = {"file": name, "imageSize": list(size), "prompt": prompt_id, "model": model,
           "modelVersion": model_version, "usage": usage, "reply": reply}
    if raw_text is not None:
        rec["rawText"] = raw_text
    json.dump(rec, open(out_path, "w", encoding="utf-8"), indent=1)
    print("planType=%s rooms=%d tokens: in=%s out=%s (thoughts=%s) charged=$%s" % (
        reply.get("planType"), len(reply.get("rooms", [])),
        usage.get("promptTokenCount"), usage.get("candidatesTokenCount"), usage.get("thoughtsTokenCount", 0),
        usage.get("costUsd")))
    for room in reply.get("rooms", []):
        # A point-only reply (prompt v7-point, the direction trial) carries no w/h.
        print("  %-24s size=%-22s @ %.2f,%.2f %.2fx%.2f" % (
            room.get("label", "?")[:24], room.get("size", ""), room.get("x", -1),
            room.get("y", -1), room.get("w", 0) or 0, room.get("h", 0) or 0))
    print("wrote", out_path)
    return 0


if __name__ == "__main__":
    sys.exit(main())

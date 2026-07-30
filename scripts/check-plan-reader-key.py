#!/usr/bin/env python
"""Check that the plan-reading key in this build actually works, and that the model still exists.

WHY: the key reaches the APK from a build secret, so nothing about a green build previously said
whether that key is accepted. A rotated, revoked or mistyped key produces an APK that compiles, passes
every test, renders identically — and then tells every user "we couldn't read your plan just now",
forever. Same shape as the missing INTERNET permission and the random signing key: fine everywhere
except on a phone.

It also checks the pinned model is still served, which is not hypothetical. Both Llama 4 vision models
were shut down mid-2026 — Scout twelve days before this feature was designed — and a retired model id
fails on the first real call with a message no user can act on.

Deliberately forgiving about the things that are not our fault:
  * no key at all      -> PASS (a keyless build is a supported state; the app says it cannot read)
  * network / 5xx      -> PASS with a warning (Groq's uptime is not our build gate)
  * 401 / 403          -> FAIL (the key in this build would be rejected on every scan)
  * model not listed   -> FAIL (the config pins a model this account cannot reach)

Costs nothing: it lists models, it does not read a plan, so no tokens are spent.

Usage: python scripts/check-plan-reader-key.py
       GROQ_API_KEY from the environment, or from a local .env when running on a developer machine.
"""
import json
import os
import re
import sys
import urllib.error
import urllib.request

MODELS_URL = "https://api.groq.com/openai/v1/models"
CONFIG = "shared/src/main/resources/scan/reader-config.json"


def key_from_env():
    key = (os.environ.get("GROQ_API_KEY") or "").strip()
    if key:
        return key, "the build environment"
    # Developer machine: the git-ignored .env is where the key lives locally.
    try:
        for line in open(".env", encoding="utf-8"):
            line = line.strip()
            if line.startswith("GROQ_API_KEY=") and len(line) > 13:
                return line.split("=", 1)[1].strip(), "the local .env"
    except OSError:
        pass
    return "", "nowhere"


def configured():
    text = open(CONFIG, encoding="utf-8").read()
    model = re.search(r'"model"\s*:\s*"([^"]+)"', text)
    agent = re.search(r'"userAgent"\s*:\s*"([^"]+)"', text)
    if not model or not agent:
        raise ValueError("%s names no model or no userAgent" % CONFIG)
    return model.group(1), agent.group(1)


def main():
    key, source = key_from_env()
    if not key:
        print("Plan-reader key check SKIPPED: no key in this build.")
        print("  That is a supported state — the app will say it cannot read plans rather than")
        print("  pretending to. Set GROQ_API_KEY to ship a build that can read.")
        return 0

    model, user_agent = configured()
    print("  key from %s (%d chars, value never printed)" % (source, len(key)))
    print("  config pins model %s" % model)

    request = urllib.request.Request(
        MODELS_URL,
        headers={
            "Authorization": "Bearer " + key,
            # Cloudflare fronts this API and blocks an unrecognised client with its own 403, which
            # would otherwise look exactly like a rejected key.
            "User-Agent": user_agent,
            "Accept": "application/json",
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            payload = json.loads(response.read())
    except urllib.error.HTTPError as exc:
        if exc.code in (401, 403):
            print("")
            print("PLAN-READER KEY CHECK FAILED: the service rejected this key (HTTP %s)." % exc.code)
            print("  An APK built with it would tell every user 'we couldn't read your plan just now'")
            print("  on every attempt. Update the GROQ_API_KEY secret (Settings -> Secrets and")
            print("  variables -> Actions) and re-run, or unset it to ship an honest keyless build.")
            return 1
        print("  ⚠ the service answered HTTP %s — treating as a service problem, not a build one" % exc.code)
        return 0
    except Exception as exc:
        print("  ⚠ could not reach the service (%s) — not failing the build for their uptime"
              % type(exc).__name__)
        return 0

    served = sorted(m.get("id", "") for m in payload.get("data", []))
    if model not in served:
        print("")
        print("PLAN-READER KEY CHECK FAILED: the pinned model is not served to this account.")
        print("  pinned:    %s" % model)
        print("  available: %s" % ", ".join(served) or "(none)")
        print("  Both Llama 4 vision models were retired mid-2026; this is how that arrives. Pick a")
        print("  served vision model in %s — it is data, so this needs no code change." % CONFIG)
        return 1

    print("Plan-reader key check passed: the key is accepted and %s is still served." % model)
    return 0


if __name__ == "__main__":
    sys.exit(main())

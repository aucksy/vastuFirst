/**
 * VastuFirst plan-reader relay — the AI key lives HERE, never in the app.
 *
 * Why it exists (iPhone plan risk 4; delivery item A10, 14 Oct 2026): until now every APK carried the
 * OpenRouter key, and the release page is public, so anyone could take the key out of an APK and
 * spend from the owner's account with no ceiling. Both app stores need this fixed before launch.
 *
 * What it does: the app posts the SAME OpenAI-shaped request it always sent (GroqWire.requestBody) to
 * this relay; the relay checks it, adds the key, forwards it to OpenRouter and hands the answer back.
 *
 * What it refuses (so the relay itself cannot be used to run up the bill):
 *   - anything but POST /v1/chat/completions                       -> 404
 *   - a request without the app's header                            -> 403  (a filter, not a secret)
 *   - a body over MAX_BODY_BYTES                                    -> 413
 *   - a model not on ALLOWED_MODELS (the reader-config pair)        -> 400
 *   - more than PER_IP_PER_MINUTE requests a minute from one address -> 429
 *   - more than DAILY_CAP requests in a UTC day, across everyone    -> 429
 *     (both counted exactly by the Limiter Durable Object below)
 * A 429 is what the app already maps to "busy, try again later".
 *
 * Settings are vars in wrangler.toml; the key is a secret (`wrangler secret put OPENROUTER_API_KEY`).
 * Nothing here is logged about the picture. The daily counter keeps a number; the per-address
 * counts live in memory for a minute and are never written down.
 */

const UPSTREAM = "https://openrouter.ai/api/v1/chat/completions";

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (url.pathname === "/health") {
      return json(200, { ok: true, keyPresent: Boolean(env.OPENROUTER_API_KEY) });
    }
    if (request.method !== "POST" || url.pathname !== "/v1/chat/completions") {
      return json(404, { error: "not found" });
    }
    if (!request.headers.get("X-VastuFirst-App")) {
      return json(403, { error: "unknown client" });
    }
    if (!env.OPENROUTER_API_KEY) {
      return json(503, { error: "reader not configured" });
    }

    const maxBytes = Number(env.MAX_BODY_BYTES || 6_000_000);
    const declared = Number(request.headers.get("Content-Length") || 0);
    if (declared > maxBytes) return json(413, { error: "too large" });
    const text = await request.text();
    if (text.length > maxBytes) return json(413, { error: "too large" });

    let body;
    try {
      body = JSON.parse(text);
    } catch {
      return json(400, { error: "not json" });
    }
    const allowed = String(env.ALLOWED_MODELS || "").split(",").map((s) => s.trim()).filter(Boolean);
    if (!allowed.includes(body.model)) return json(400, { error: "model not allowed" });

    // Both limits are counted by ONE Durable Object, which handles requests one at a time, so the
    // counts are exact. (Tried first and measured on 14 Oct 2026: the Workers rate-limiting binding let
    // 23 requests through in a minute against a limit of 6, and a KV counter recorded 5 of 24 — KV is
    // eventually consistent, so a burst reads the same old number and writes it back.)
    const ip = request.headers.get("CF-Connecting-IP") || "unknown";
    const limiter = env.LIMITER.get(env.LIMITER.idFromName("global"));
    const verdict = await (await limiter.fetch("https://limiter/take", {
      method: "POST",
      body: JSON.stringify({
        ip,
        perMinute: Number(env.PER_IP_PER_MINUTE || 6),
        dailyCap: Number(env.DAILY_CAP || 300),
      }),
    })).json();
    if (!verdict.ok) {
      return json(429, { error: verdict.reason }, { "Retry-After": verdict.reason === "daily limit reached" ? "3600" : "60" });
    }

    const upstream = await fetch(UPSTREAM, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${env.OPENROUTER_API_KEY}`,
        "Content-Type": "application/json",
        Accept: "application/json",
        "HTTP-Referer": "https://www.vastufirst.com",
        "X-Title": "VastuFirst",
      },
      body: text,
    });
    const headers = { "Content-Type": upstream.headers.get("Content-Type") || "application/json" };
    const retry = upstream.headers.get("Retry-After");
    if (retry) headers["Retry-After"] = retry;
    return new Response(upstream.body, { status: upstream.status, headers });
  },
};

function json(status, value, extra = {}) {
  return new Response(JSON.stringify(value), {
    status,
    headers: { "Content-Type": "application/json", ...extra },
  });
}

/**
 * The exact counter. One instance ("global"); Cloudflare runs its requests one at a time.
 * - The day's total is stored (survives the object being moved or restarted).
 * - Per-address counts are kept in memory for the current minute only; a restart forgets them,
 *   which can only ever let a few extra requests through, never block a real user.
 */
export class Limiter {
  constructor(state) {
    this.state = state;
    this.minute = -1;
    this.perIp = new Map();
  }

  async fetch(request) {
    const { ip, perMinute, dailyCap } = await request.json();
    const now = Date.now();
    const minute = Math.floor(now / 60000);
    if (minute !== this.minute) {
      this.minute = minute;
      this.perIp.clear();
    }
    const mine = (this.perIp.get(ip) || 0) + 1;
    if (mine > perMinute) return Response.json({ ok: false, reason: "slow down" });

    const day = new Date(now).toISOString().slice(0, 10);
    const key = `day:${day}`;
    const used = (await this.state.storage.get(key)) || 0;
    if (used >= dailyCap) return Response.json({ ok: false, reason: "daily limit reached", used });

    this.perIp.set(ip, mine);
    await this.state.storage.put(key, used + 1);
    return Response.json({ ok: true, used: used + 1 });
  }
}

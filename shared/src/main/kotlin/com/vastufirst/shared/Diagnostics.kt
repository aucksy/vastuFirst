package com.vastufirst.shared

/**
 * Production graceful-degradation model. The engine must NEVER crash and never surface a scary
 * dead-end ("irregular", "not assessed", "cannot analyze") to the user — it always returns a
 * usable [Analysis], recording behind the scenes what it had to recover from.
 */
enum class AnalysisQuality {
    /** Clean input, full-confidence analysis. */
    OK,

    /** Analysed successfully, but the engine recovered from an imperfect plan (dropped a
     *  degenerate room, rebuilt a missing outline from the rooms, used a best-fit footprint for a
     *  self-touching outline, …). The score is still valid; the app may gently prompt a cleanup. */
    DEGRADED,

    /** Not enough usable geometry to score meaningfully (e.g. no footprint AND no rooms). The
     *  app should guide the user to add their plan — framed as onboarding, never as an error. */
    INSUFFICIENT,
}

enum class NoteLevel { INFO, WARNING }

/**
 * A structured, plain-language note about the analysis. Codes are stable for the app/telemetry;
 * [message] is already user-safe (no jargon, no alarm) so a screen can show it directly.
 */
data class AnalysisNote(
    val code: String,
    val message: String,
    val level: NoteLevel = NoteLevel.INFO,
)

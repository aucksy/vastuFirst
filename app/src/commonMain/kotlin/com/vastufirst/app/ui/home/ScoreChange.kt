package com.vastufirst.app.ui.home

import com.vastufirst.data.SavedPlan

/**
 * ⭐ TELLING SOMEONE THEIR SCORE MOVED — and why — instead of letting it move behind their back.
 *
 * A saved home stores the rule version it was scored under. When we change a Vastu ruling, every
 * home saved under the old rules is re-run and the owner is shown the old number, the new number and
 * the reason, in one card, before anything is written back.
 *
 * ⚠ The reason this exists at all: on 1 August 2026 the owner ruled on where a prayer room belongs,
 * which started scoring a room the app had always skipped. Anyone with a saved home could have
 * opened the app to find a different number and no explanation. A score that changes on its own is
 * the difference between an honest product and a spooky one — and this is a product whose entire
 * claim is that it does not make Vastu up.
 *
 * ⭐ A home whose number did NOT move is still listed. The rules moved, and the reading in their
 * report moved with them, so staying silent because the arithmetic happened to land on the same
 * number would be luck dressed up as integrity. The card says "unchanged" in words instead.
 */
data class ScoreChange(
    val id: String,
    val name: String,
    val oldScore: Int,
    val newScore: Int,
) {
    val moved: Boolean get() = oldScore != newScore
}

/**
 * Everything the card needs: the plain-words reason from the rule data itself, and one line per
 * affected home. Never built without a reason — see [scoreChangesFor].
 */
data class ScoreChangeNotice(
    val reason: String,
    val changes: List<ScoreChange>,
) {
    val movedCount: Int get() = changes.count { it.moved }
}

/**
 * The homes saved under an older ruleset, re-scored.
 *
 * Pure, so it is unit-tested without a database or an Android runtime: [rescore] is the engine, and
 * the caller supplies it.
 *
 * ⭐ Returns null — i.e. shows the user NOTHING — when the rule data carries no explanation. A
 * "your score changed" card with no reason under it is worse than saying nothing: it tells someone
 * their number moved and then refuses to say why. If the sentence is missing, the honest behaviour
 * is to leave the old number on screen until a build ships that can explain itself.
 */
fun scoreChangesFor(
    plans: List<SavedPlan>,
    currentVersion: String,
    reason: String?,
    rescore: (SavedPlan) -> Int?,
): ScoreChangeNotice? {
    if (reason.isNullOrBlank()) return null
    val stale = plans.filter { it.ruleSetVersion != currentVersion }
    if (stale.isEmpty()) return null

    val changes = stale.mapNotNull { plan ->
        // A home this build cannot re-run is left completely alone — old score, old version, no
        // claim about it. It will be picked up by a build that can read it.
        val fresh = rescore(plan) ?: return@mapNotNull null
        ScoreChange(id = plan.id, name = plan.name, oldScore = plan.score, newScore = fresh)
    }
    return if (changes.isEmpty()) null else ScoreChangeNotice(reason = reason, changes = changes)
}

/** One home's line in the card, in the reader's words. Pure, so the wording is unit-tested.
 *  The number pair is joined with no-break spaces (audit C12): at 200 % font "6.8 → 7.1" used to
 *  wrap after the arrow, stranding the new number alone on the next line. */
fun scoreChangeLine(change: ScoreChange, out: (Int) -> String): String =
    if (change.moved) "${change.name}: ${out(change.oldScore)} → ${out(change.newScore)}"
    else "${change.name}: ${out(change.newScore)} — unchanged"

/** The card's heading, which must never overstate what happened. */
fun scoreChangeTitle(notice: ScoreChangeNotice): String = when {
    notice.movedCount == 0 -> "We changed a rule — your score is unchanged"
    notice.movedCount == notice.changes.size && notice.changes.size == 1 -> "Your score has changed"
    else -> "We changed a rule — some scores moved"
}

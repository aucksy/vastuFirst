package com.vastufirst.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vastufirst.data.PlanRepository
import com.vastufirst.data.SavedDraft
import com.vastufirst.data.SavedPlans
import com.vastufirst.engine.VastuEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Backs the saved-plans list. Reads the DB as a cold flow; the UI reacts to changes.
 *
 *  Carries a count of rows that could not be read alongside the homes that could — a home that
 *  vanishes without a word looks exactly like a home the app deleted by itself. */
class HomeViewModel(
    private val repo: PlanRepository,
    /** The app's single engine, from DI — not a second copy, which would re-parse the rule data. */
    private val engine: VastuEngine,
) : ViewModel() {

    val plans: StateFlow<SavedPlans> = repo.observePlans()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SavedPlans())

    /**
     * ⭐ The homes that were started and never finished (v0.6.6).
     *
     * They are LISTED, not restored. The app used to bring the leftover work back by itself the
     * moment anyone entered the drawing flow — so choosing "draw it on a grid" or "upload a plan"
     * handed back a half-finished home nobody had asked for. Putting them here makes resuming a
     * thing the user chooses and makes starting fresh mean what it says.
     */
    val drafts: StateFlow<List<SavedDraft>> = repo.observeDrafts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * ⭐ The homes whose score was computed under an OLDER set of rules, re-run under today's.
     *
     * Nothing is written back until the user has read the card and tapped through it. Until then the
     * list still shows the number they last saw, so the app never changes a saved score behind
     * somebody's back and then tells them about it afterwards.
     */
    val scoreChanges: StateFlow<ScoreChangeNotice?> = repo.observePlans()
        .map { saved ->
            scoreChangesFor(
                plans = saved.plans,
                currentVersion = engine.ruleSetVersion(),
                reason = engine.ruleSetChangeNote(),
                // Total on purpose: a home this build cannot re-run is dropped from the card
                // entirely rather than reported with a made-up number.
                //
                // ⚠ AND A HOME THE ENGINE CANNOT ACTUALLY JUDGE COUNTS AS ONE IT COULD NOT RE-RUN.
                // `analyze` never throws — it answers an unscoreable plan with score 0 and quality
                // INSUFFICIENT — so catching only the exception let a 0 through as if it were a real
                // reading. Nothing hits this today, because a home saved from this app always has
                // enough on it to score. The day anyone tightens what counts as readable, this card
                // would have told somebody their home went from 7.1 to 0.0 and then written that 0
                // to disk when they tapped to acknowledge it. Left as a live trap it is a number
                // this product cannot defend; one extra condition removes it.
                rescore = { plan ->
                    runCatching { engine.analyze(plan.plan) }.getOrNull()
                        ?.takeIf { it.quality != com.vastufirst.shared.AnalysisQuality.INSUFFICIENT }
                        ?.score
                },
            )
        }
        // ⚠ Off the main thread. Re-scoring is a full engine run per saved home, and it would
        // otherwise land on the UI thread — the saved-homes list is the first screen a returning
        // user sees, and it must not stutter to tell them about a rule change.
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The user has read what changed. Write the new numbers back, so the card does not return. */
    fun acknowledgeScoreChanges(notice: ScoreChangeNotice) {
        viewModelScope.launch {
            val version = engine.ruleSetVersion()
            notice.changes.forEach { repo.setRescored(it.id, it.newScore, version) }
        }
    }

    fun deleteAll() { viewModelScope.launch { repo.deleteAll() } }

    /** Throw away one unfinished home. Confirmed on screen first — it cannot be undone. */
    fun deleteDraft(id: String) { viewModelScope.launch { repo.clearDraft(id) } }

    /**
     * Remove ONE home this build cannot read (audit B7 — the only alternative was Settings →
     * delete ALL data). Confirmed on screen first, and the confirmation says the true price: the
     * row was being kept so a later build could rescue it, and removing it ends that chance.
     */
    fun removeUnreadable(id: String) { viewModelScope.launch { repo.delete(id) } }

    /**
     * ⭐ Delete ONE finished home (17 Aug 2026). Reached only by pressing and holding its row and
     * then answering the question — and when that home has been paid for, the question says so,
     * because one payment unlocks one home and deleting it ends that report.
     */
    fun deletePlan(id: String) { viewModelScope.launch { repo.delete(id) } }

    /** Rename a saved home (blank is ignored by the repository). The list updates via its flow. */
    fun rename(id: String, name: String) { viewModelScope.launch { repo.rename(id, name) } }
}

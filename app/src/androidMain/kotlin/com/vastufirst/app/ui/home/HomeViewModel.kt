package com.vastufirst.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vastufirst.data.PlanRepository
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
                rescore = { plan -> runCatching { engine.analyze(plan.plan).score }.getOrNull() },
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

    /** Rename a saved home (blank is ignored by the repository). The list updates via its flow. */
    fun rename(id: String, name: String) { viewModelScope.launch { repo.rename(id, name) } }
}

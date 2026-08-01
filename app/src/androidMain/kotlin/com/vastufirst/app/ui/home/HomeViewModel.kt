package com.vastufirst.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vastufirst.data.PlanRepository
import com.vastufirst.data.SavedPlans
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Backs the saved-plans list. Reads the DB as a cold flow; the UI reacts to changes.
 *
 *  Carries a count of rows that could not be read alongside the homes that could — a home that
 *  vanishes without a word looks exactly like a home the app deleted by itself. */
class HomeViewModel(private val repo: PlanRepository) : ViewModel() {
    val plans: StateFlow<SavedPlans> = repo.observePlans()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SavedPlans())

    fun deleteAll() { viewModelScope.launch { repo.deleteAll() } }

    /** Rename a saved home (blank is ignored by the repository). The list updates via its flow. */
    fun rename(id: String, name: String) { viewModelScope.launch { repo.rename(id, name) } }
}

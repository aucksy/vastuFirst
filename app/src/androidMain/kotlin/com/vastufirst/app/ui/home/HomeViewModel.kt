package com.vastufirst.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vastufirst.data.PlanRepository
import com.vastufirst.data.SavedPlan
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Backs the saved-plans list. Reads the DB as a cold flow; the UI reacts to changes. */
class HomeViewModel(private val repo: PlanRepository) : ViewModel() {
    val plans: StateFlow<List<SavedPlan>> = repo.observePlans()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun deleteAll() { viewModelScope.launch { repo.deleteAll() } }

    /** Rename a saved home (blank is ignored by the repository). The list updates via its flow. */
    fun rename(id: String, name: String) { viewModelScope.launch { repo.rename(id, name) } }
}

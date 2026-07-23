package com.vastufirst.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vastufirst.data.PlanRepository
import com.vastufirst.data.SavedPlan
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Backs the saved-plans list. Reads the DB as a cold flow; the UI reacts to changes. */
class HomeViewModel(repo: PlanRepository) : ViewModel() {
    val plans: StateFlow<List<SavedPlan>> = repo.observePlans()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

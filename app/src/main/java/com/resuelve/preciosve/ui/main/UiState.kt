package com.resuelve.preciosve.ui.main

import com.resuelve.preciosve.domain.model.ComparisonGroup

sealed interface UiState {
    data object Idle : UiState
    data object Loading : UiState
    data class Success(val groups: List<ComparisonGroup>) : UiState
    data object Empty : UiState
    data class Error(val message: String) : UiState
}

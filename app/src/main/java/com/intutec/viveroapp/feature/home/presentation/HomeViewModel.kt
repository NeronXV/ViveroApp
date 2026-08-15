package com.intutec.viveroapp.feature.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intutec.viveroapp.core.common.UiState
import com.intutec.viveroapp.feature.home.domain.model.Dashboard
import com.intutec.viveroapp.feature.home.domain.usecase.GetDashboardUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getDashboard: GetDashboardUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState<Dashboard>>(UiState.Loading)
    val uiState: StateFlow<UiState<Dashboard>> = _uiState.asStateFlow()

    init {
        loadDashboard()
    }

    fun retry() = loadDashboard()

    private fun loadDashboard() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            _uiState.value = getDashboard().fold(
                onSuccess = { dashboard ->
                    if (dashboard.modules.isEmpty()) {
                        UiState.Empty("No hay funciones disponibles para este perfil.")
                    } else {
                        UiState.Success(dashboard)
                    }
                },
                onFailure = { error ->
                    UiState.Error(error.message ?: "No pudimos cargar el inicio.")
                },
            )
        }
    }
}

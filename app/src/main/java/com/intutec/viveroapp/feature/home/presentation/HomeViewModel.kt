package com.intutec.viveroapp.feature.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intutec.viveroapp.core.common.UiState
import com.intutec.viveroapp.feature.cart.domain.model.Cart
import com.intutec.viveroapp.feature.cart.domain.repository.CartRepository
import com.intutec.viveroapp.feature.home.domain.model.Dashboard
import com.intutec.viveroapp.feature.home.domain.usecase.GetDashboardUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getDashboard: GetDashboardUseCase,
    private val cartRepository: CartRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState<HomeContent>>(UiState.Loading)
    val uiState: StateFlow<UiState<HomeContent>> = _uiState.asStateFlow()

    init {
        loadDashboard()
    }

    fun retry() = loadDashboard()

    private fun loadDashboard() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val dashboard = getDashboard().getOrElse { error ->
                _uiState.value = UiState.Error(error.message ?: "No pudimos cargar el inicio.")
                return@launch
            }
            cartRepository.observeCart()
                .catch { error ->
                    _uiState.value = UiState.Error(error.message ?: "No pudimos cargar el carrito.")
                }
                .collect { cart -> _uiState.value = UiState.Success(HomeContent(dashboard, cart)) }
        }
    }
}

data class HomeContent(
    val dashboard: Dashboard,
    val cart: Cart,
)

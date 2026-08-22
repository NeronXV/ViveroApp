package com.intutec.viveroapp.feature.cashier.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intutec.viveroapp.core.session.SessionStore
import com.intutec.viveroapp.feature.cashier.domain.model.CashierOrderDetail
import com.intutec.viveroapp.feature.cashier.domain.model.CashierOrderSummary
import com.intutec.viveroapp.feature.cashier.domain.usecase.GetCashierOrderDetailUseCase
import com.intutec.viveroapp.feature.cashier.domain.usecase.GetPendingCashierOrdersUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

sealed interface CashierQueueUiState {
    data object Loading : CashierQueueUiState
    data class Content(
        val branchName: String,
        val orders: List<CashierOrderSummary>,
        val isRefreshing: Boolean = false,
        val refreshError: String? = null,
    ) : CashierQueueUiState
    data class Empty(
        val branchName: String,
        val isRefreshing: Boolean = false,
        val refreshError: String? = null,
    ) : CashierQueueUiState
    data class Error(val message: String) : CashierQueueUiState
}

@HiltViewModel
class CashierQueueViewModel @Inject constructor(
    private val getPendingOrders: GetPendingCashierOrdersUseCase,
    private val sessionStore: SessionStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow<CashierQueueUiState>(CashierQueueUiState.Loading)
    val uiState: StateFlow<CashierQueueUiState> = _uiState.asStateFlow()
    private val refreshMutex = Mutex()
    private var pollingJob: Job? = null

    fun onVisible() {
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch {
            reload(showInitialLoading = true)
            while (isActive) {
                delay(POLL_INTERVAL_MILLIS)
                reload(showInitialLoading = false)
            }
        }
    }

    fun onHidden() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun refresh() {
        viewModelScope.launch { reload(showInitialLoading = false) }
    }

    internal suspend fun loadOnce() = reload(showInitialLoading = true)

    private suspend fun reload(showInitialLoading: Boolean) = refreshMutex.withLock {
        if (showInitialLoading && (_uiState.value is CashierQueueUiState.Loading || _uiState.value is CashierQueueUiState.Error)) {
            _uiState.value = CashierQueueUiState.Loading
        } else {
            _uiState.update { state -> state.withRefreshing(true) }
        }

        val branchName = sessionStore.session.value?.branchName ?: "Sucursal asignada"
        getPendingOrders().fold(
            onSuccess = { orders ->
                _uiState.value = if (orders.isEmpty()) {
                    CashierQueueUiState.Empty(branchName)
                } else {
                    CashierQueueUiState.Content(branchName, orders)
                }
            },
            onFailure = {
                val message = "No pudimos actualizar las comandas. Comprueba tu conexión e intenta nuevamente."
                _uiState.update { state ->
                    when (state) {
                        is CashierQueueUiState.Content -> state.copy(isRefreshing = false, refreshError = message)
                        is CashierQueueUiState.Empty -> state.copy(isRefreshing = false, refreshError = message)
                        else -> CashierQueueUiState.Error(message)
                    }
                }
            },
        )
    }

    override fun onCleared() {
        pollingJob?.cancel()
        super.onCleared()
    }

    private fun CashierQueueUiState.withRefreshing(value: Boolean): CashierQueueUiState = when (this) {
        is CashierQueueUiState.Content -> copy(isRefreshing = value, refreshError = null)
        is CashierQueueUiState.Empty -> copy(isRefreshing = value, refreshError = null)
        else -> this
    }

    private companion object {
        const val POLL_INTERVAL_MILLIS = 30_000L
    }
}

sealed interface CashierDetailUiState {
    data object Loading : CashierDetailUiState
    data class Content(
        val order: CashierOrderDetail,
        val isRefreshing: Boolean = false,
        val refreshError: String? = null,
    ) : CashierDetailUiState
    data class Error(val message: String) : CashierDetailUiState
}

@HiltViewModel
class CashierDetailViewModel @Inject constructor(
    private val getOrderDetail: GetCashierOrderDetailUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow<CashierDetailUiState>(CashierDetailUiState.Loading)
    val uiState: StateFlow<CashierDetailUiState> = _uiState.asStateFlow()
    private var orderId: String? = null

    fun load(orderId: String) {
        if (this.orderId == orderId && _uiState.value is CashierDetailUiState.Content) return
        this.orderId = orderId
        reload(showLoading = true)
    }

    fun refresh() = reload(showLoading = false)

    private fun reload(showLoading: Boolean) {
        val currentOrderId = orderId ?: return
        viewModelScope.launch {
            if (showLoading) {
                _uiState.value = CashierDetailUiState.Loading
            } else {
                _uiState.update { state ->
                    if (state is CashierDetailUiState.Content) {
                        state.copy(isRefreshing = true, refreshError = null)
                    } else {
                        state
                    }
                }
            }
            getOrderDetail(currentOrderId).fold(
                onSuccess = { _uiState.value = CashierDetailUiState.Content(it) },
                onFailure = {
                    val message = "No pudimos actualizar la comanda. Comprueba tu conexión e intenta nuevamente."
                    _uiState.update { state ->
                        if (state is CashierDetailUiState.Content) {
                            state.copy(isRefreshing = false, refreshError = message)
                        } else {
                            CashierDetailUiState.Error(message)
                        }
                    }
                },
            )
        }
    }
}

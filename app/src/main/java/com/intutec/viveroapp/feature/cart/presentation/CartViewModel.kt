package com.intutec.viveroapp.feature.cart.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intutec.viveroapp.core.session.SessionMode
import com.intutec.viveroapp.core.session.SessionStore
import com.intutec.viveroapp.feature.cart.domain.model.Cart
import com.intutec.viveroapp.feature.cart.domain.model.CartCustomer
import com.intutec.viveroapp.feature.cart.domain.model.SaleTicket
import com.intutec.viveroapp.feature.cart.domain.repository.CartRepository
import com.intutec.viveroapp.feature.cart.domain.usecase.ChangeCartQuantityUseCase
import com.intutec.viveroapp.feature.cart.domain.usecase.SaleSubmissionException
import com.intutec.viveroapp.feature.cart.domain.usecase.SendCartToCashierUseCase
import com.intutec.viveroapp.feature.customer.domain.model.Customer
import com.intutec.viveroapp.feature.customer.domain.repository.CustomerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CartUiState(
    val cart: Cart = Cart(),
    val loading: Boolean = true,
    val working: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val sentTicket: SaleTicket? = null,
    val isDemo: Boolean = false,
    // Customer search state
    val showCustomerSearch: Boolean = false,
    val customerQuery: String = "",
    val searchingCustomers: Boolean = false,
    val customerResults: List<Customer> = emptyList(),
    val customerSearchError: String? = null,
)

@HiltViewModel
class CartViewModel @Inject constructor(
    private val repository: CartRepository,
    private val customerRepository: CustomerRepository,
    private val sessionStore: SessionStore,
    private val changeQuantity: ChangeCartQuantityUseCase,
    private val sendCartToCashier: SendCartToCashierUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CartUiState())
    val uiState = _uiState.asStateFlow()
    private var searchJob: Job? = null

    init {
        val session = sessionStore.session.value
        _uiState.update { it.copy(isDemo = session?.mode == SessionMode.DEMO) }

        viewModelScope.launch {
            repository.observeCart()
                .catch { error -> _uiState.update { it.copy(loading = false, error = error.message ?: "No pudimos abrir el carrito.") } }
                .collect { cart -> _uiState.update { it.copy(cart = cart, loading = false) } }
        }
    }

    fun increment(productId: String) {
        val item = _uiState.value.cart.items.firstOrNull { it.productId == productId } ?: return
        runAction { changeQuantity(productId, item.quantity + 1) }
    }

    fun decrement(productId: String) {
        val item = _uiState.value.cart.items.firstOrNull { it.productId == productId } ?: return
        if (item.quantity == 1) remove(productId) else runAction { changeQuantity(productId, item.quantity - 1) }
    }

    fun remove(productId: String) = runAction { repository.removeProduct(productId) }

    fun openCustomerSearch() {
        if (_uiState.value.isDemo) {
            associateDemoCustomer()
        } else {
            _uiState.update { it.copy(showCustomerSearch = true, customerQuery = "", customerResults = emptyList(), customerSearchError = null) }
        }
    }

    fun closeCustomerSearch() {
        searchJob?.cancel()
        _uiState.update { it.copy(showCustomerSearch = false) }
    }

    fun updateCustomerQuery(query: String) {
        _uiState.update { it.copy(customerQuery = query, customerSearchError = null) }
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _uiState.update { it.copy(customerResults = emptyList(), searchingCustomers = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            _uiState.update { it.copy(searchingCustomers = true) }
            customerRepository.searchCustomers(query.trim())
                .onSuccess { results -> _uiState.update { it.copy(customerResults = results, searchingCustomers = false) } }
                .onFailure { error -> _uiState.update { it.copy(customerSearchError = error.message ?: "Error al buscar clientes.", searchingCustomers = false) } }
        }
    }

    fun associateCustomer(customer: Customer) {
        runAction { repository.associateCustomer(CartCustomer(customer.id, customer.fullName)) }
        closeCustomerSearch()
    }

    fun associateDemoCustomer() = runAction {
        repository.associateCustomer(CartCustomer("demo-customer", "Ana Torres"))
    }

    fun removeCustomer() = runAction { repository.associateCustomer(null) }

    fun saveDraft() = runAction(successMessage = "Borrador guardado en este dispositivo.") { repository.saveDraft() }

    fun cancelCart() = runAction(successMessage = "Carrito cancelado.") { repository.cancelCart() }

    fun sendToCashier() {
        if (_uiState.value.working) return
        _uiState.update { it.copy(working = true, error = null) }
        viewModelScope.launch {
            sendCartToCashier().fold(
                onSuccess = { ticket -> _uiState.update { it.copy(working = false, sentTicket = ticket) } },
                onFailure = { error ->
                    val pendingTicket = (error as? SaleSubmissionException)?.ticket
                    _uiState.update {
                        it.copy(
                            working = false,
                            sentTicket = pendingTicket,
                            error = error.message ?: "No pudimos enviar la orden.",
                        )
                    }
                },
            )
        }
    }

    fun retrySaleSync() {
        val ticket = _uiState.value.sentTicket ?: return
        if (_uiState.value.working) return
        _uiState.update { it.copy(working = true, error = null) }
        viewModelScope.launch {
            sendCartToCashier.retry(ticket.id).fold(
                onSuccess = { synced -> _uiState.update { it.copy(working = false, sentTicket = synced) } },
                onFailure = { error ->
                    val current = (error as? SaleSubmissionException)?.ticket ?: ticket
                    _uiState.update {
                        it.copy(
                            working = false,
                            sentTicket = current,
                            error = error.message ?: "No pudimos reintentar el envío.",
                        )
                    }
                },
            )
        }
    }

    fun startNewCart() = _uiState.update { it.copy(sentTicket = null, message = null, error = null) }
    fun clearNotice() = _uiState.update { it.copy(message = null, error = null) }

    private fun runAction(successMessage: String? = null, action: suspend () -> Result<Unit>) {
        if (_uiState.value.working) return
        _uiState.update { it.copy(working = true, error = null) }
        viewModelScope.launch {
            action().fold(
                onSuccess = { _uiState.update { it.copy(working = false, message = successMessage) } },
                onFailure = { error -> _uiState.update { it.copy(working = false, error = error.message ?: "No pudimos completar la operación.") } },
            )
        }
    }
}

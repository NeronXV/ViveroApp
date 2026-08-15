package com.intutec.viveroapp.feature.cart.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intutec.viveroapp.core.session.SessionStore
import com.intutec.viveroapp.feature.cart.domain.model.Cart
import com.intutec.viveroapp.feature.cart.domain.model.CartCustomer
import com.intutec.viveroapp.feature.cart.domain.model.SaleTicket
import com.intutec.viveroapp.feature.cart.domain.repository.CartRepository
import com.intutec.viveroapp.feature.cart.domain.usecase.ChangeCartQuantityUseCase
import com.intutec.viveroapp.feature.cart.domain.usecase.SendCartToCashierUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
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
)

@HiltViewModel
class CartViewModel @Inject constructor(
    private val repository: CartRepository,
    private val changeQuantity: ChangeCartQuantityUseCase,
    private val sendToCashier: SendCartToCashierUseCase,
    private val sessionStore: SessionStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CartUiState())
    val uiState = _uiState.asStateFlow()

    init {
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

    fun associateDemoCustomer() = runAction {
        repository.associateCustomer(CartCustomer("demo-customer", "Ana Torres", "DUL-00128"))
    }

    fun removeCustomer() = runAction { repository.associateCustomer(null) }

    fun saveDraft() = runAction(successMessage = "Borrador guardado en este dispositivo.") { repository.saveDraft() }

    fun cancelCart() = runAction(successMessage = "Carrito cancelado.") { repository.cancelCart() }

    fun sendToCashier() {
        if (_uiState.value.working) return
        val userId = sessionStore.session.value?.userId.orEmpty()
        _uiState.update { it.copy(working = true, error = null) }
        viewModelScope.launch {
            sendToCashier(userId).fold(
                onSuccess = { ticket -> _uiState.update { it.copy(working = false, sentTicket = ticket) } },
                onFailure = { error -> _uiState.update { it.copy(working = false, error = error.message ?: "No pudimos enviar la orden.") } },
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

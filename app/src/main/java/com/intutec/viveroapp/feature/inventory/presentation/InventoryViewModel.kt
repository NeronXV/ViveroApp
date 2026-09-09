package com.intutec.viveroapp.feature.inventory.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intutec.viveroapp.core.security.AppPermission
import com.intutec.viveroapp.core.session.SessionStore
import com.intutec.viveroapp.feature.inventory.domain.model.InventoryItem
import com.intutec.viveroapp.feature.inventory.domain.model.InventoryMovement
import com.intutec.viveroapp.feature.inventory.domain.repository.InventoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class InventoryAction { RECEPTION, COUNT }

data class InventoryUiState(
    val isLoading: Boolean = true,
    val items: List<InventoryItem> = emptyList(),
    val query: String = "",
    val error: String? = null,
    val notice: String? = null,
    val selectedItem: InventoryItem? = null,
    val action: InventoryAction? = null,
    val quantityInput: String = "",
    val detailInput: String = "",
    val isSubmitting: Boolean = false,
    val history: List<InventoryMovement>? = null,
    val isHistoryLoading: Boolean = false,
    val branchName: String = "",
    val canManageProducts: Boolean = false,
) {
    val visibleItems: List<InventoryItem>
        get() = query.trim().lowercase().let { term ->
            if (term.isEmpty()) items else items.filter {
                it.productName.lowercase().contains(term) || it.productCode.lowercase().contains(term)
            }
        }
}

@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val repository: InventoryRepository,
    private val sessionStore: SessionStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(InventoryUiState())
    val uiState: StateFlow<InventoryUiState> = _uiState.asStateFlow()
    private var pending: PendingOperation? = null

    init {
        refreshBranch()
        load()
    }

    private fun refreshBranch() {
        val session = sessionStore.session.value
        _uiState.update {
            it.copy(
                branchName = session?.branch?.name ?: session?.branchName ?: "",
                canManageProducts = session?.hasCapability(AppPermission.MANAGE_PRODUCTS) == true,
            )
        }
    }

    fun load() = viewModelScope.launch {
        refreshBranch()
        _uiState.update { it.copy(isLoading = true, error = null) }
        repository.getDashboard().fold(
            onSuccess = { items -> _uiState.update { it.copy(isLoading = false, items = items) } },
            onFailure = { error ->
                val msg = error.userMessage()
                val needsMigration = msg.contains("INVENTORY", ignoreCase = true) || msg.contains("supabase", ignoreCase = true)
                val friendly = if (needsMigration) "$msg Entorno necesita migración de inventario." else msg
                _uiState.update { it.copy(isLoading = false, error = friendly) }
            },
        )
    }

    fun updateQuery(value: String) = _uiState.update { it.copy(query = value) }

    fun selectProduct(productId: String, actionName: String? = null) {
        val items = _uiState.value.items
        val item = items.singleOrNull {
            it.productId == productId || it.productCode.equals(productId, ignoreCase = true)
        }
        val action = when (actionName) {
            "COUNT" -> InventoryAction.COUNT
            "RECEPTION" -> InventoryAction.RECEPTION
            else -> null
        }
        if (item != null) {
            _uiState.update {
                it.copy(
                    query = item.productCode,
                    selectedItem = if (action != null) item else null,
                    action = action,
                    quantityInput = if (action == InventoryAction.COUNT) item.totalQuantity.toString() else "",
                    detailInput = "",
                )
            }
        } else {
            _uiState.update { it.copy(query = productId) }
        }
    }

    fun openAction(item: InventoryItem, action: InventoryAction) {
        _uiState.update {
            it.copy(
                selectedItem = item,
                action = action,
                quantityInput = if (action == InventoryAction.COUNT) item.totalQuantity.toString() else "",
                detailInput = "",
                notice = null,
            )
        }
    }

    fun updateQuantity(value: String) {
        if (value.all(Char::isDigit) && value.length <= 9) _uiState.update { it.copy(quantityInput = value) }
    }

    fun updateDetail(value: String) {
        if (value.length <= 240) _uiState.update { it.copy(detailInput = value) }
    }

    fun closeDialog() {
        if (!_uiState.value.isSubmitting) _uiState.update { it.copy(selectedItem = null, action = null) }
    }

    fun submit() {
        val state = _uiState.value
        val item = state.selectedItem ?: return
        val action = state.action ?: return
        val quantity = state.quantityInput.toIntOrNull()
        val detail = state.detailInput.trim()
        val validation = when {
            quantity == null -> "Escribe una cantidad válida."
            action == InventoryAction.RECEPTION && quantity <= 0 -> "La recepción debe ser mayor que cero."
            action == InventoryAction.COUNT && quantity < 0 -> "El conteo no puede ser negativo."
            action == InventoryAction.COUNT && detail.length < 3 -> "Escribe el motivo del conteo."
            else -> null
        }
        if (validation != null) {
            _uiState.update { it.copy(error = validation) }
            return
        }
        val request = PendingOperation(
            item.productId,
            action,
            checkNotNull(quantity),
            detail,
            pending?.key ?: UUID.randomUUID().toString(),
        )
        if (!request.samePayload(pending)) pending = request.copy(key = UUID.randomUUID().toString())
        val operation = checkNotNull(pending)
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            val result = when (operation.action) {
                InventoryAction.RECEPTION -> repository.recordReception(
                    operation.productId, operation.quantity, operation.detail.ifBlank { null }, operation.key,
                )
                InventoryAction.COUNT -> repository.reconcileCount(
                    operation.productId, operation.quantity, operation.detail, operation.key,
                )
            }
            result.fold(
                onSuccess = { response ->
                    pending = null
                    val verb = if (operation.action == InventoryAction.RECEPTION) "Recepción registrada" else "Conteo conciliado"
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            selectedItem = null,
                            action = null,
                            notice = "$verb. Existencia: ${response.totalQuantity}.",
                        )
                    }
                    load()
                },
                onFailure = { error ->
                    _uiState.update { it.copy(isSubmitting = false, error = error.userMessage()) }
                },
            )
        }
    }

    fun openHistory(item: InventoryItem) = viewModelScope.launch {
        _uiState.update { it.copy(selectedItem = item, history = null, isHistoryLoading = true, error = null) }
        repository.getHistory(item.productId).fold(
            onSuccess = { history -> _uiState.update { it.copy(history = history, isHistoryLoading = false) } },
            onFailure = { error ->
                _uiState.update { it.copy(selectedItem = null, isHistoryLoading = false, error = error.userMessage()) }
            },
        )
    }

    fun closeHistory() = _uiState.update { it.copy(selectedItem = null, history = null) }

    fun dismissMessage() = _uiState.update { it.copy(error = null, notice = null) }
}

private data class PendingOperation(
    val productId: String,
    val action: InventoryAction,
    val quantity: Int,
    val detail: String,
    val key: String,
) {
    fun samePayload(other: PendingOperation?): Boolean = other != null &&
        productId == other.productId && action == other.action && quantity == other.quantity && detail == other.detail
}

private fun Throwable.userMessage(): String = message?.takeIf(String::isNotBlank)
    ?: "No pudimos completar la operación. Intenta nuevamente."

package com.intutec.viveroapp.feature.scanner.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intutec.viveroapp.feature.catalog.domain.model.Product
import com.intutec.viveroapp.feature.scanner.domain.model.ScanFormat
import com.intutec.viveroapp.feature.scanner.domain.usecase.FindProductByCodeUseCase
import com.intutec.viveroapp.feature.cart.domain.usecase.AddProductToCartUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ScanResultState {
    data object Ready : ScanResultState
    data class Searching(val code: String, val format: ScanFormat) : ScanResultState
    data class Found(val code: String, val format: ScanFormat, val product: Product) : ScanResultState
    data class NotFound(val code: String, val format: ScanFormat) : ScanResultState
    data class Error(val message: String) : ScanResultState
}

data class ScannerUiState(
    val result: ScanResultState = ScanResultState.Ready,
    val resetKey: Int = 0,
    val canCreateSales: Boolean = false,
    val canManageInventory: Boolean = false,
)

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val findProductByCode: FindProductByCodeUseCase,
    private val addProductToCart: AddProductToCartUseCase,
    private val sessionStore: com.intutec.viveroapp.core.session.SessionStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState = _uiState.asStateFlow()
    private val _notices = MutableSharedFlow<String>()
    val notices = _notices.asSharedFlow()
    private var lookupJob: Job? = null

    init {
        viewModelScope.launch {
            sessionStore.session.collect { session ->
                val canSales = session?.canOperateAtBranch(com.intutec.viveroapp.core.security.AppPermission.CREATE_SALES) == true ||
                    session?.hasCapability(com.intutec.viveroapp.core.security.AppPermission.CREATE_SALES) == true
                val canInv = session?.canOperateAtBranch(com.intutec.viveroapp.core.security.AppPermission.MANAGE_INVENTORY) == true
                _uiState.update {
                    it.copy(
                        canCreateSales = canSales,
                        canManageInventory = canInv,
                    )
                }
            }
        }
    }

    fun onCodeDetected(code: String, format: ScanFormat) {
        if (_uiState.value.result != ScanResultState.Ready) return
        val trimmed = code.trim()
        _uiState.update { it.copy(result = ScanResultState.Searching(trimmed, format)) }
        lookupJob = viewModelScope.launch {
            val result = findProductByCode(trimmed)
            currentCoroutineContext().ensureActive()
            result.fold(
                onSuccess = { product ->
                    _uiState.update {
                        it.copy(
                            result = if (product != null) {
                                ScanResultState.Found(trimmed, format, product)
                            } else {
                                ScanResultState.NotFound(trimmed, format)
                            },
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(result = ScanResultState.Error(error.message ?: "No pudimos consultar el producto."))
                    }
                },
            )
        }
    }

    fun scanAgain() {
        lookupJob?.cancel()
        lookupJob = null
        _uiState.update {
            it.copy(result = ScanResultState.Ready, resetKey = it.resetKey + 1)
        }
    }

    fun onScannerFailure(message: String) {
        if (_uiState.value.result == ScanResultState.Ready) {
            _uiState.update { it.copy(result = ScanResultState.Error(message)) }
        }
    }

    fun addCurrentProductToCart() {
        val product = (_uiState.value.result as? ScanResultState.Found)?.product ?: return
        viewModelScope.launch {
            addProductToCart(product).fold(
                onSuccess = { _notices.emit("${product.commonName} se agregó al carrito.") },
                onFailure = { _notices.emit(it.message ?: "No pudimos agregar el producto.") },
            )
        }
    }
}

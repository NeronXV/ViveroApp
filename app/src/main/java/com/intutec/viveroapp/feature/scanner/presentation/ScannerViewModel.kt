package com.intutec.viveroapp.feature.scanner.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intutec.viveroapp.feature.catalog.domain.model.Product
import com.intutec.viveroapp.feature.scanner.domain.model.ScanFormat
import com.intutec.viveroapp.feature.scanner.domain.usecase.FindProductByCodeUseCase
import com.intutec.viveroapp.feature.cart.domain.usecase.AddProductToCartUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
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
)

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val findProductByCode: FindProductByCodeUseCase,
    private val addProductToCart: AddProductToCartUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState = _uiState.asStateFlow()
    private val _notices = MutableSharedFlow<String>()
    val notices = _notices.asSharedFlow()

    fun onCodeDetected(code: String, format: ScanFormat) {
        if (_uiState.value.result != ScanResultState.Ready) return
        _uiState.update { it.copy(result = ScanResultState.Searching(code.trim(), format)) }
        viewModelScope.launch {
            findProductByCode(code).fold(
                onSuccess = { product ->
                    _uiState.update {
                        it.copy(
                            result = if (product != null) {
                                ScanResultState.Found(code.trim(), format, product)
                            } else {
                                ScanResultState.NotFound(code.trim(), format)
                            },
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(result = ScanResultState.Error(error.message ?: "No pudimos consultar el producto.")) }
                },
            )
        }
    }

    fun scanAgain() = _uiState.update {
        it.copy(result = ScanResultState.Ready, resetKey = it.resetKey + 1)
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

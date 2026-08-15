package com.intutec.viveroapp.feature.catalog.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intutec.viveroapp.core.common.UiState
import com.intutec.viveroapp.feature.catalog.domain.model.Product
import com.intutec.viveroapp.feature.catalog.domain.usecase.GetProductUseCase
import com.intutec.viveroapp.feature.cart.domain.usecase.AddProductToCartUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject

@HiltViewModel
class ProductDetailViewModel @Inject constructor(
    private val getProduct: GetProductUseCase,
    private val addProductToCart: AddProductToCartUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState<Product>>(UiState.Loading)
    val uiState: StateFlow<UiState<Product>> = _uiState.asStateFlow()
    private var currentProductId: String? = null
    private val _notices = MutableSharedFlow<String>()
    val notices = _notices.asSharedFlow()

    fun load(productId: String, force: Boolean = false) {
        if (!force && currentProductId == productId && _uiState.value !is UiState.Error) return
        currentProductId = productId
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            _uiState.value = getProduct(productId).fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it.message ?: "No pudimos cargar el producto.") },
            )
        }
    }

    fun retry() = currentProductId?.let { load(it, force = true) }

    fun addCurrentProductToCart() {
        val product = (_uiState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            addProductToCart(product).fold(
                onSuccess = { _notices.emit("${product.commonName} se agregó al carrito.") },
                onFailure = { _notices.emit(it.message ?: "No pudimos agregar el producto.") },
            )
        }
    }
}

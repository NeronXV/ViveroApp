package com.intutec.viveroapp.feature.catalog.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intutec.viveroapp.feature.catalog.domain.model.Category
import com.intutec.viveroapp.feature.catalog.domain.model.CatalogSnapshot
import com.intutec.viveroapp.feature.catalog.domain.model.Product
import com.intutec.viveroapp.feature.catalog.domain.usecase.FilterProductsUseCase
import com.intutec.viveroapp.feature.catalog.domain.usecase.ObserveCatalogUseCase
import com.intutec.viveroapp.feature.cart.domain.usecase.AddProductToCartUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface CatalogUiState {
    data object Loading : CatalogUiState
    data class Content(
        val products: List<Product>,
        val categories: List<Category>,
        val query: String,
        val selectedCategoryId: String?,
        val availableOnly: Boolean,
    ) : CatalogUiState
    data class Empty(
        val categories: List<Category>,
        val query: String,
        val selectedCategoryId: String?,
        val availableOnly: Boolean,
        val catalogIsEmpty: Boolean,
    ) : CatalogUiState
    data class Error(
        val message: String,
        val diagnosticCause: CatalogDiagnosticCause,
    ) : CatalogUiState
}

enum class CatalogDiagnosticCause {
    REMOTE_REQUEST,
    INVALID_REMOTE_DATA,
    UNEXPECTED,
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CatalogViewModel @Inject constructor(
    observeCatalog: ObserveCatalogUseCase,
    filterProducts: FilterProductsUseCase,
    private val addProductToCart: AddProductToCartUseCase,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val categoryId = MutableStateFlow<String?>(null)
    private val availableOnly = MutableStateFlow(true)
    private val refresh = MutableStateFlow(0)
    private val _notices = MutableSharedFlow<String>()
    val notices = _notices.asSharedFlow()

    private val catalog: Flow<Result<CatalogSnapshot>> = refresh.flatMapLatest {
        observeCatalog()
            .map<CatalogSnapshot, Result<CatalogSnapshot>> { Result.success(it) }
            .catch { emit(Result.failure(it)) }
    }

    val uiState = combine(catalog, query, categoryId, availableOnly) { catalogResult, currentQuery, currentCategory, onlyAvailable ->
        catalogResult.fold(
            onSuccess = { snapshot ->
                val filtered = filterProducts(snapshot.products, currentQuery, currentCategory, onlyAvailable)
                if (filtered.isEmpty()) {
                    CatalogUiState.Empty(
                        categories = snapshot.categories,
                        query = currentQuery,
                        selectedCategoryId = currentCategory,
                        availableOnly = onlyAvailable,
                        catalogIsEmpty = snapshot.products.isEmpty(),
                    )
                } else {
                    CatalogUiState.Content(filtered, snapshot.categories, currentQuery, currentCategory, onlyAvailable)
                }
            },
            onFailure = Throwable::toCatalogErrorState,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CatalogUiState.Loading)

    fun onQueryChanged(value: String) = query.update { value }
    fun onCategorySelected(value: String?) = categoryId.update { value }
    fun onAvailableOnlyChanged(value: Boolean) = availableOnly.update { value }
    fun retry() = refresh.update { it + 1 }

    fun addToCart(product: Product) {
        viewModelScope.launch {
            addProductToCart(product).fold(
                onSuccess = { _notices.emit("${product.commonName} se agregó al carrito.") },
                onFailure = { _notices.emit(it.message ?: "No pudimos agregar el producto.") },
            )
        }
    }
}

internal fun Throwable.toCatalogErrorState() = CatalogUiState.Error(
    message = "No pudimos cargar el catálogo. Intenta nuevamente",
    diagnosticCause = toCatalogDiagnosticCause(),
)

private fun Throwable.toCatalogDiagnosticCause(): CatalogDiagnosticCause = when (this) {
    is IllegalArgumentException,
    is IllegalStateException,
    -> CatalogDiagnosticCause.INVALID_REMOTE_DATA
    else -> if (this::class.qualifiedName.orEmpty().startsWith("io.github.jan.supabase")) {
        CatalogDiagnosticCause.REMOTE_REQUEST
    } else {
        CatalogDiagnosticCause.UNEXPECTED
    }
}

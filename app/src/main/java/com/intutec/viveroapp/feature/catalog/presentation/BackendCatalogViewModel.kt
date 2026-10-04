package com.intutec.viveroapp.feature.catalog.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intutec.viveroapp.core.session.*
import com.intutec.viveroapp.feature.cart.domain.repository.BackendCartRepository
import com.intutec.viveroapp.feature.catalog.domain.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BackendCatalogUiState(val enabled: Boolean = false, val loading: Boolean = false, val working: Boolean = false,
    val query: String = "", val category: Long? = null, val products: List<BackendCatalogProduct> = emptyList(),
    val categories: List<BackendCategory> = emptyList(), val next: Long? = null, val nextCategory: Long? = null,
    val canSell: Boolean = false, val canScan: Boolean = false, val error: String? = null, val message: String? = null)
@HiltViewModel
class BackendCatalogViewModel @Inject constructor(
    private val sessions: SessionStore, private val remote: BackendCatalogGateway,
    private val cart: BackendCartRepository, @param:Named("backendApiOrigin") val imageOrigin: String,
) : ViewModel() {
    private val _state = MutableStateFlow(BackendCatalogUiState())
    val state = _state.asStateFlow()
    private var generation = 0L
    private var sessionGeneration = 0L
    private var request: Job? = null
    private var mutation: Job? = null
    init { viewModelScope.launch { sessions.backend.collectLatest { current ->
        val rev = ++generation; val sessionRev = ++sessionGeneration; request?.cancel(); mutation?.cancel()
        val token = current.authorizedSession("VIEW_CATALOG")?.token
        _state.value = BackendCatalogUiState(enabled = token != null, loading = token != null,
            canSell = current.authorizedSession("CREATE_SALES", true) != null,
            canScan = current.authorizedSession("SCAN_PRODUCTS") != null)
        if (token != null) {
            try {
                val categories = remote.categories(token, 20)
                if (sessionRev == sessionGeneration) _state.update { it.copy(categories = categories.items, nextCategory = categories.nextAfterId) }
                val page = remote.products(token, 20)
                if (rev == generation) _state.update { it.copy(products = page.items, next = page.nextAfterId, loading = false) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (rev == generation) _state.update { it.copy(loading = false, error = "No se pudo consultar el catálogo. Reintenta o actualiza tus permisos.") } }
        }
    } } }
    fun search(query: String) { if (query.length <= 80) { _state.update { it.copy(query = query) }; load(debounce = true) } }
    fun category(id: Long?) { _state.update { it.copy(category = id) }; load() }
    fun retry() = load()
    fun more() { if (_state.value.next != null && !_state.value.loading) load(more = true) }
    private fun load(more: Boolean = false, debounce: Boolean = false) {
        val token = sessions.backend.value.authorizedSession("VIEW_CATALOG")?.token ?: return
        request?.cancel(); val rev = ++generation
        val before = _state.value
        _state.update { it.copy(loading = true, error = null, message = null, products = if (more) it.products else emptyList(), next = if (more) it.next else null) }
        request = viewModelScope.launch {
            try {
                if (debounce) delay(300)
                val page = remote.products(token, 20, if (more) before.next else null, before.query, before.category)
                if (rev == generation) _state.update { it.copy(products = (if (more) before.products else emptyList()) + page.items, next = page.nextAfterId, loading = false) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (rev == generation) _state.update { it.copy(loading = false, error = "No se pudo consultar el catálogo.") } }
        }
    }
    fun moreCategories() {
        val before = _state.value
        if (before.nextCategory == null || before.loading || request?.isActive == true) return
        val token = sessions.backend.value.authorizedSession("VIEW_CATALOG")?.token ?: return
        val rev = generation
        _state.update { it.copy(loading = true, error = null) }
        request = viewModelScope.launch {
            try {
                val page = remote.categories(token, 20, before.nextCategory)
                if (rev == generation) _state.update { it.copy(categories = before.categories + page.items, nextCategory = page.nextAfterId, loading = false) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (rev == generation) _state.update { it.copy(loading = false, error = "No se pudieron consultar las categorías.") } }
        }
    }
    fun scan(code: String) {
        val token = sessions.backend.value.authorizedSession("SCAN_PRODUCTS")?.token ?: return
        request?.cancel(); val rev = ++generation
        _state.update { it.copy(loading = true, products = emptyList(), next = null, error = null) }
        request = viewModelScope.launch {
            try {
                val product = remote.scan(token, code)
                if (rev == generation) _state.update { it.copy(loading = false, products = listOfNotNull(product), message = if (product == null) "No se encontró ese código." else null) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (rev == generation) _state.update { it.copy(loading = false, error = "No se pudo consultar el código; no se agregó ningún producto.") } }
        }
    }
    fun add(product: BackendCatalogProduct) {
        if (!_state.value.canSell || mutation?.isActive == true) return
        val rev = sessionGeneration
        _state.update { it.copy(working = true, error = null, message = null) }
        mutation = viewModelScope.launch {
            try { cart.add(product); if (rev == sessionGeneration) _state.update { it.copy(message = "Producto agregado a tu comanda.") } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (rev == sessionGeneration) _state.update { it.copy(error = "No se confirmó la edición del carrito. Revísalo antes de continuar.") } }
            finally { if (rev == sessionGeneration) _state.update { it.copy(working = false) } }
        }
    }
}

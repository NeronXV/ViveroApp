package com.intutec.viveroapp.feature.cart.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intutec.viveroapp.core.session.*
import com.intutec.viveroapp.feature.cart.domain.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BackendCartUiState(val enabled: Boolean = false, val loading: Boolean = false, val working: Boolean = false,
    val cart: BackendCartSnapshot? = null, val quote: BackendSaleQuote? = null, val message: String? = null, val error: String? = null)
@HiltViewModel
class BackendCartViewModel @Inject constructor(private val sessions: SessionStore, private val carts: BackendCartRepository,
    private val emitter: BackendSaleEmitter) : ViewModel() {
    private val _state = MutableStateFlow(BackendCartUiState())
    val state = _state.asStateFlow()
    private var revision = 0L
    private var operation: Job? = null
    private var prepared: PreparedBackendSale? = null
    init { viewModelScope.launch { sessions.backend.collectLatest { context ->
        val generation = ++revision; operation?.cancel(); prepared = null
        val session = context.authorizedSession("CREATE_SALES", true)
        _state.value = BackendCartUiState(enabled = session != null, loading = session != null)
        if (session != null) {
            try { carts.observe(BackendSaleIdentity(session.userId, requireNotNull(context.context?.branch).id)).collect { cart ->
                if (generation == revision) {
                    if (prepared?.cart?.let { it.id != cart?.id || it.revision != cart.revision } == true) { prepared = null }
                    _state.update { it.copy(cart = cart, loading = false, quote = prepared?.quote) }
                }
            } } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (generation == revision) _state.update { it.copy(loading = false, error = "No se pudo leer el carrito guardado.") } }
        }
    } } }
    fun quantity(id: Long, value: Int) = act { carts.quantity(id, value) }
    fun remove(id: Long) = act { carts.remove(id) }
    fun clear() = act { carts.clear() }
    fun dismissQuote() { if (!_state.value.working) { prepared = null; _state.update { it.copy(quote = null) } } }
    fun quote() {
        val snapshot = _state.value.cart ?: return
        if (snapshot.items.isEmpty()) return
        act { current ->
            val result = emitter.quoteCart(snapshot)
            if (current()) { prepared = result; _state.update { it.copy(quote = result.quote) } }
        }
    }
    fun send() {
        val confirmed = prepared ?: return
        act { current ->
            try {
                val result = emitter.send(confirmed)
                if (current()) _state.update { it.copy(message = when (val outcome = result.outcome) {
                    BackendSaleEmissionOutcome.Synced -> "Venta confirmada y enviada a caja. Consulta su folio en ventas guardadas."
                    BackendSaleEmissionOutcome.Retired -> "El intento quedó cerrado."
                    BackendSaleEmissionOutcome.AlreadyClaimed -> "El intento ya está siendo atendido. Consulta ventas guardadas."
                    BackendSaleEmissionOutcome.SessionUnavailable -> "El intento se conserva; vuelve a consultar tu sesión."
                    is BackendSaleEmissionOutcome.Pending -> outcome.message
                    is BackendSaleEmissionOutcome.Failed -> outcome.message
                }) }
            } finally { if (current()) { prepared = null; _state.update { it.copy(quote = null) } } }
        }
    }
    private fun act(action: suspend (() -> Boolean) -> Unit) {
        if (!_state.value.enabled || _state.value.loading || operation?.isActive == true) return
        val generation = revision
        _state.update { it.copy(working = true, error = null, message = null) }
        operation = viewModelScope.launch {
            try { action { generation == revision } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (generation == revision) _state.update { it.copy(error = "No se confirmó la operación. Revisa el carrito y los intentos guardados antes de reenviar.") } }
            finally { if (generation == revision) _state.update { it.copy(working = false) } }
        }
    }
}

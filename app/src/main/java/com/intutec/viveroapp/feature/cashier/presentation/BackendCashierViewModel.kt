package com.intutec.viveroapp.feature.cashier.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intutec.viveroapp.core.session.*
import com.intutec.viveroapp.feature.cashier.domain.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BackendCashierUiState(val enabled: Boolean = false, val loading: Boolean = false, val working: Boolean = false,
    val sales: List<BackendCashierSale> = emptyList(), val next: Long? = null, val pending: List<BackendPendingPayment> = emptyList(),
    val receipt: BackendPaymentReceipt? = null, val error: String? = null, val message: String? = null)
@HiltViewModel
class BackendCashierViewModel @Inject constructor(private val sessions: SessionStore, private val remote: BackendCashierGateway,
    private val payments: BackendCashierPaymentRepository) : ViewModel() {
    private val _state = MutableStateFlow(BackendCashierUiState())
    val state = _state.asStateFlow()
    private var generation = 0L
    private var operation: Job? = null
    init { viewModelScope.launch { sessions.backend.collectLatest { current ->
        val revision = ++generation; operation?.cancel()
        _state.value = BackendCashierUiState(enabled = current.authorizedSession("OPERATE_CASHIER", true) != null)
        if (_state.value.enabled) load(revision)
    } } }
    private suspend fun load(revision: Long, more: Boolean = false) {
        val token = sessions.backend.value.authorizedSession("OPERATE_CASHIER", true)?.token ?: return
        val before = _state.value
        _state.update { it.copy(loading = true) }
        try {
            val attempts = payments.pending()
            val page = remote.list(token, if (more) before.next else null)
            if (revision == generation) _state.update { it.copy(loading = false, sales = (if (more) before.sales else emptyList()) + page.items, next = page.next, pending = attempts) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { if (revision == generation) _state.update { it.copy(loading = false, error = "No se pudo actualizar Caja. Consulta antes de operar.") } }
    }
    fun refresh(more: Boolean = false) = act { revision -> load(revision, more) }
    fun pay(sale: Long, method: String, amount: String, reference: String) = act { revision ->
        val received = if (method == "CASH") checkNotNull(amount.parseMxnCents()) { "Escribe el efectivo con hasta dos decimales." } else null
        val result = payments.start(sale, method, received, if (method == "CASH") null else reference.trim().ifBlank { null })
        if (revision == generation) _state.update { it.copy(receipt = result) }
        load(revision)
    }
    fun recover(id: Long) = act { revision ->
        val result = payments.recover(id)
        if (revision == generation) _state.update { it.copy(receipt = result) }
        load(revision)
    }
    fun retry(id: Long) = act { revision ->
        val result = payments.retry(id)
        if (revision == generation) _state.update { it.copy(receipt = result) }
        load(revision)
    }
    fun retire(id: Long) = act { revision ->
        val result = payments.retire(id)
        if (revision == generation) _state.update { it.copy(receipt = (result as? BackendPaymentRetirement.Committed)?.receipt,
            message = if (result == BackendPaymentRetirement.Retired) "El servidor cerró la clave sin registrar un pago. El historial del intento se conserva." else "El pago ya estaba confirmado; se conserva su comprobante.") }
        load(revision)
    }
    private fun act(action: suspend (Long) -> Unit) {
        if (!_state.value.enabled || _state.value.loading || operation?.isActive == true) return
        val revision = generation
        _state.update { it.copy(working = true, error = null, message = null) }
        operation = viewModelScope.launch {
            try { action(revision) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (revision == generation) {
                    _state.update { it.copy(error = "No se confirmó la operación. Conserva el intento y actualiza Caja antes de cobrar otra vez.") }
                    load(revision)
                }
            } finally { if (revision == generation) _state.update { it.copy(working = false) } }
        }
    }
}

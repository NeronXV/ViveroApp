package com.intutec.viveroapp.feature.cart.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intutec.viveroapp.core.session.BackendAccessStatus
import com.intutec.viveroapp.core.session.SessionStore
import com.intutec.viveroapp.feature.cart.domain.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class BackendPendingSalesUiState(
    val enabled: Boolean = false, val loading: Boolean = false, val working: Boolean = false,
    val entries: List<BackendSaleJournalEntry> = emptyList(), val error: String? = null, val message: String? = null,
)
@HiltViewModel
class BackendPendingSalesViewModel @Inject constructor(
    private val sessions: SessionStore,
    private val emitter: Provider<BackendSaleEmitter>,
) : ViewModel() {
    private val _state = MutableStateFlow(BackendPendingSalesUiState())
    val state = _state.asStateFlow()
    private var revision = 0L
    private var operation: Job? = null
    init {
        viewModelScope.launch {
            sessions.backend.collectLatest { current ->
                val generation = ++revision
                operation?.cancel()
                val session = current.session
                val enabled = session != null && session.expiresAtMillis > System.currentTimeMillis() &&
                    current.accessStatus == BackendAccessStatus.READY && current.context?.user?.id == session.userId &&
                    current.context.canOperate("CREATE_SALES")
                _state.value = BackendPendingSalesUiState(enabled = enabled, loading = enabled)
                if (enabled) {
                    load(generation)
                    delay((requireNotNull(session).expiresAtMillis - System.currentTimeMillis()).coerceAtLeast(0))
                    sessions.expireBackend(System.currentTimeMillis())
                }
            }
        }
    }
    private suspend fun load(generation: Long) {
        try {
            val rows = emitter.get().journal()
            if (generation == revision) _state.value = _state.value.copy(entries = rows, loading = false, error = null)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            if (generation == revision) _state.value = _state.value.copy(entries = emptyList(), loading = false, error = "No fue posible consultar los intentos guardados.")
        }
    }
    fun refresh() {
        if (!_state.value.enabled || _state.value.loading || operation?.isActive == true) return
        val generation = revision
        _state.value = _state.value.copy(loading = true, error = null, message = null)
        operation = viewModelScope.launch { load(generation) }
    }
    private enum class Action { CHECK, RETRY, RETIRE }
    fun checkResult(id: Long) = act(id, Action.CHECK)
    fun retry(id: Long) = act(id, Action.RETRY)
    fun retire(id: Long) = act(id, Action.RETIRE)
    private fun act(id: Long, action: Action) {
        if (!_state.value.enabled || _state.value.loading || operation?.isActive == true ||
            _state.value.entries.none { it.localId == id && it.state in setOf("PENDING", "UNCERTAIN") }) return
        val generation = revision
        _state.value = _state.value.copy(working = true, error = null, message = null)
        operation = viewModelScope.launch {
            try {
                val result = when (action) {
                    Action.CHECK -> emitter.get().checkResult(id)
                    Action.RETRY -> emitter.get().resume(id)
                    Action.RETIRE -> emitter.get().retire(id)
                }
                if (generation == revision) {
                    val message = when (val outcome = result.outcome) {
                        BackendSaleEmissionOutcome.Synced -> "Resultado confirmado y guardado."
                        BackendSaleEmissionOutcome.Retired -> "Intento cerrado sin crear una venta. Puedes solicitar una nueva cotización."
                        BackendSaleEmissionOutcome.AlreadyClaimed -> "El intento ya está siendo atendido o fue confirmado."
                        BackendSaleEmissionOutcome.SessionUnavailable -> "La sesión ya no permite consultar este intento."
                        is BackendSaleEmissionOutcome.Pending -> outcome.message
                        is BackendSaleEmissionOutcome.Failed -> outcome.message
                    }
                    _state.value = _state.value.copy(message = message)
                    load(generation)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (generation == revision) _state.value = _state.value.copy(error = "No se confirmó el resultado. El intento se conserva.")
            } finally {
                if (generation == revision) _state.value = _state.value.copy(working = false)
            }
        }
    }
}

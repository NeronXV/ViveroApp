package com.intutec.viveroapp.feature.mysales.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.intutec.viveroapp.core.session.*
import com.intutec.viveroapp.feature.cart.domain.repository.BackendSaleIdentity
import com.intutec.viveroapp.feature.mysales.domain.repository.*
import com.intutec.viveroapp.navigation.BackendHistoryRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BackendHistoryUiState(val kind: BackendHistoryKind, val hasList: Boolean, val enabled: Boolean = false, val loading: Boolean = false,
    val entries: List<BackendHistoryEntry> = emptyList(), val next: Long? = null, val document: BackendSaleDocument? = null, val error: String? = null)
@HiltViewModel
class BackendHistoryViewModel internal constructor(private val route: BackendHistoryRoute, private val sessions: SessionStore, private val remote: BackendHistoryGateway) : ViewModel() {
    @Inject constructor(saved: SavedStateHandle, sessions: SessionStore, remote: BackendHistoryGateway) : this(saved.toRoute<BackendHistoryRoute>(), sessions, remote)
    private val kind = BackendHistoryKind.valueOf(route.kind)
    private val _state = MutableStateFlow(BackendHistoryUiState(kind, route.id == null && kind != BackendHistoryKind.QUEUE))
    val state = _state.asStateFlow()
    private var generation = 0L
    private var operation: Job? = null
    private fun access(): Pair<String, BackendSaleIdentity>? {
        val current = sessions.backend.value
        val session = current.authorizedSession(if (kind == BackendHistoryKind.SALES) "VIEW_OWN_SALES" else "OPERATE_CASHIER", true) ?: return null
        if (kind == BackendHistoryKind.SALES && current.authorizedSession("CREATE_SALES", true) == null) return null
        return session.token to BackendSaleIdentity(session.userId, requireNotNull(current.context?.branch).id)
    }
    init { viewModelScope.launch { sessions.backend.collect {
        generation++; operation?.cancel()
        _state.value = BackendHistoryUiState(kind, route.id == null && kind != BackendHistoryKind.QUEUE, enabled = access() != null)
        if (_state.value.enabled) { if (route.id != null) detail(route.id) else refresh() }
    } } }
    fun refresh(more: Boolean = false) {
        if (!_state.value.hasList || (more && _state.value.next == null)) return
        val before = _state.value
        run { token, identity ->
            val page = remote.list(token, identity, kind, if (more) before.next else null)
            val publish: (BackendHistoryUiState) -> BackendHistoryUiState = { it.copy(entries = (if (more) before.entries else emptyList()) + page.items, next = page.next, document = null) }
            publish
        }
    }
    fun detail(id: Long) = run { token, identity ->
        val document = remote.detail(token, identity, kind, id)
        val publish: (BackendHistoryUiState) -> BackendHistoryUiState = { it.copy(document = document) }
        publish
    }
    fun closeDetail() { operation?.cancel(); generation++; _state.update { it.copy(document = null, loading = false, error = null) } }
    fun retry() { if (route.id != null) detail(route.id) else _state.value.document?.let { detail(if (kind == BackendHistoryKind.PAYMENTS) requireNotNull(it.payment).id else it.sale.id) } ?: refresh() }
    private fun run(action: suspend (String, BackendSaleIdentity) -> ((BackendHistoryUiState) -> BackendHistoryUiState)) {
        val who = access() ?: return
        if (operation?.isActive == true) return
        val revision = generation
        _state.update { it.copy(loading = true, error = null) }
        operation = viewModelScope.launch {
            try {
                // Never publish an old account's document even if a transport ignores cancellation.
                val job = kotlinx.coroutines.currentCoroutineContext()[Job]
                val publish = action(who.first, who.second)
                if (revision != generation || job?.isActive != true || access() != who) return@launch
                _state.update(publish)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (revision == generation) _state.update { it.copy(error = "No se pudo consultar el documento. Actualiza tus permisos e inténtalo de nuevo.") } }
            finally { if (revision == generation) _state.update { it.copy(loading = false) } }
        }
    }
}

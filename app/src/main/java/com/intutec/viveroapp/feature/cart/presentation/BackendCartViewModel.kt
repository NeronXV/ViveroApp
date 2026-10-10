package com.intutec.viveroapp.feature.cart.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intutec.viveroapp.core.session.*
import com.intutec.viveroapp.feature.cart.domain.repository.*
import com.intutec.viveroapp.feature.catalog.domain.repository.BackendCatalogGateway
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BackendCartUiState(val enabled: Boolean = false, val loading: Boolean = false, val working: Boolean = false,
    val cart: BackendCartSnapshot? = null, val quote: BackendSaleQuote? = null, val message: String? = null, val error: String? = null,
    val branchName: String = "Sin sucursal", val canViewHistory: Boolean = false,
    val journalReady: Boolean = false, val journal: List<BackendSaleJournalEntry> = emptyList(),
    val sent: BackendSaleJournalEntry? = null, val photos: Map<Long, String> = emptyMap(), val pendingIds: List<Long> = emptyList()) {
    val unresolved: List<BackendSaleJournalEntry> get() = journal.filter { it.state in setOf("PENDING", "UNCERTAIN", "SYNCING") }
    val hasUnresolved: Boolean get() = pendingIds.isNotEmpty() || unresolved.isNotEmpty()
}
@HiltViewModel
class BackendCartViewModel @Inject constructor(private val sessions: SessionStore, private val carts: BackendCartRepository,
    private val emitter: BackendSaleEmitter, private val catalog: BackendCatalogGateway? = null,
    @param:Named("backendApiOrigin") private val imageOrigin: String = "") : ViewModel() {
    private val _state = MutableStateFlow(BackendCartUiState())
    val state = _state.asStateFlow()
    private var revision = 0L
    private var operation: Job? = null
    private var photosJob: Job? = null
    private var photoIds: Set<Long> = emptySet()
    private var prepared: PreparedBackendSale? = null
    init { viewModelScope.launch { sessions.backend.collectLatest { context ->
        val generation = ++revision; operation?.cancel(); photosJob?.cancel(); photoIds = emptySet(); prepared = null
        val session = context.authorizedSession("CREATE_SALES", true)
        _state.value = BackendCartUiState(enabled = session != null, loading = session != null,
            branchName = context.context?.branch?.name ?: "Sin sucursal",
            canViewHistory = session != null && context.authorizedSession("VIEW_OWN_SALES", true) != null)
        if (session != null) {
            try {
                loadJournal(generation)
                carts.observe(BackendSaleIdentity(session.userId, requireNotNull(context.context?.branch).id)).collect { cart ->
                if (generation == revision) {
                    if (prepared?.cart?.let { it.id != cart?.id || it.revision != cart.revision } == true) { prepared = null }
                    _state.update { it.copy(cart = cart, loading = false, quote = prepared?.quote) }
                    loadPhotos(cart, generation)
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
        if (snapshot.items.isEmpty() || !_state.value.journalReady || _state.value.hasUnresolved) return
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
                if (current()) {
                    loadJournal(revision)
                    if (current() && result.outcome == BackendSaleEmissionOutcome.Synced) _state.update {
                        it.copy(sent = it.journal.firstOrNull { row -> row.localId == result.localId && row.receipt != null })
                    }
                }
            } finally { if (current()) { prepared = null; _state.update { it.copy(quote = null) } } }
        }
    }
    fun newSale() { if (!_state.value.working && !_state.value.hasUnresolved) _state.update { it.copy(sent = null, message = null) } }
    fun checkResult(localId: Long) = act { current ->
        val result = emitter.checkResult(localId)
        if (current()) {
            loadJournal(revision)
            if (current() && result.outcome == BackendSaleEmissionOutcome.Synced) _state.update {
                it.copy(sent = it.journal.firstOrNull { row -> row.localId == localId && row.receipt != null })
            }
        }
    }
    fun refreshJournal() = act { current -> if (current()) loadJournal(revision) }
    private suspend fun loadJournal(generation: Long) {
        try {
            val rows = emitter.journal()
            val pending = emitter.pending()
            if (generation == revision) _state.update { previous -> previous.copy(journal = rows, pendingIds = pending, journalReady = true,
                sent = previous.sent ?: rows.firstOrNull { it.localId in previous.pendingIds && it.state == "SYNCED" && it.receipt != null }) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { if (generation == revision) _state.update { it.copy(journalReady = false,
            error = "No pudimos comprobar las ventas guardadas. Conserva tus datos y consulta el resultado antes de enviar otra venta.") } }
    }
    // Optional read-only presentation data. Never replace the saved prices or quantities.
    private fun loadPhotos(cart: BackendCartSnapshot?, generation: Long) {
        val source = catalog ?: return
        val token = sessions.backend.value.authorizedSession("VIEW_CATALOG")?.token ?: return
        val ids = cart?.items.orEmpty().map { it.productId }.toSet()
        if (ids == photoIds) return
        photoIds = ids
        photosJob?.cancel()
        photosJob = viewModelScope.launch {
            for (row in cart?.items.orEmpty()) {
                if (row.productId in _state.value.photos) continue
                try {
                    val product = source.products(token, 100, search = row.name.take(80)).items.firstOrNull { it.id == row.productId }
                    val path = product?.image?.path ?: continue
                    if (generation == revision) _state.update { it.copy(photos = it.photos + (row.productId to imageOrigin + path)) }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { /* A missing photo must not prevent editing or sending a sale. */ }
            }
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

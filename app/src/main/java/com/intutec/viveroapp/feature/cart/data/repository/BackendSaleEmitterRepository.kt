package com.intutec.viveroapp.feature.cart.data.repository

import com.intutec.viveroapp.core.session.BackendAccessStatus
import com.intutec.viveroapp.core.session.SessionStore
import com.intutec.viveroapp.feature.cart.domain.repository.*
import com.intutec.viveroapp.feature.cart.sync.BackendPendingSaleSynchronizer
import com.intutec.viveroapp.feature.cart.sync.BackendSaleOutboxStore
import com.intutec.viveroapp.feature.cart.sync.SaleSyncOutcome
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

@Singleton
class BackendSaleEmitterRepository @Inject constructor(
    private val remote: BackendSaleGateway,
    private val outbox: BackendSaleOutboxStore,
    private val synchronizer: BackendPendingSaleSynchronizer,
    private val sessions: SessionStore,
) : BackendSaleEmitter {
    private val operation = Mutex()
    private data class Access(val token: String, val identity: BackendSaleIdentity)
    private fun access(): Access {
        sessions.expireBackend(System.currentTimeMillis())
        val current = sessions.backend.value
        val session = checkNotNull(current.session) { "Inicia sesión en el backend." }
        val context = checkNotNull(current.context) { "Consulta los permisos antes de enviar." }
        check(current.accessStatus == BackendAccessStatus.READY && context.user.id == session.userId && context.canOperate("CREATE_SALES")) { "No tienes acceso para enviar ventas." }
        return Access(session.token, BackendSaleIdentity(session.userId, requireNotNull(context.branch).id))
    }
    private suspend fun <T> exclusive(action: suspend () -> T): T {
        check(operation.tryLock()) { "Hay una operación de venta en curso." }
        return try { action() } finally { operation.unlock() }
    }
    override suspend fun quote(items: List<BackendSaleLine>): PreparedBackendSale = exclusive { prepare(items) }
    override suspend fun quoteCart(cart: BackendCartSnapshot): PreparedBackendSale = exclusive {
        require(cart.id > 0 && cart.revision > 0)
        check(cart.identity == access().identity) { "El carrito pertenece a otra cuenta o sucursal." }
        val prepared = prepare(cart.saleLines())
        PreparedBackendSale(prepared.quote, prepared.attempt, cart.copy(items = Collections.unmodifiableList(cart.items.toList())))
    }
    private suspend fun prepare(items: List<BackendSaleLine>): PreparedBackendSale {
        val current = access()
        // Validate/copy the caller's mutable list before the first suspension.
        val lines = BackendSaleAttempt.restore(current.identity, "0".repeat(64), items, 1).items
        check(outbox.pending(current.identity).isEmpty()) { "Recupera la venta pendiente antes de preparar otra." }
        check(access() == current) { "La sesión cambió antes de cotizar." }
        val quote = remote.quote(current.token, current.identity, lines)
        check(access() == current) { "La sesión cambió durante la cotización." }
        check(quote.branchId == current.identity.branchId && quote.items.map { BackendSaleLine(it.productId, it.quantity) }.sortedBy { it.productId } == lines)
        val snapshot = quote.copy(items = Collections.unmodifiableList(quote.items.toList()))
        return PreparedBackendSale(snapshot, BackendSaleAttempt.create(current.identity, lines, quote.totalCents))
    }
    override suspend fun send(prepared: PreparedBackendSale): BackendSaleEmission = exclusive {
        val current = access()
        check(prepared.attempt.identity == current.identity) { "La cotización pertenece a otra cuenta o sucursal." }
        val pending = outbox.pending(current.identity)
        check(pending.all { it == prepared.localId }) { "Recupera la venta pendiente antes de enviar otra." }
        check(access() == current) { "La sesión cambió antes de guardar la venta." }
        val id = prepared.localId ?: withContext(NonCancellable) {
            // Bounded local transaction only: cancellation cannot lose the committed ID.
            (prepared.cart?.let { outbox.enqueueFromCart(prepared.attempt, it) } ?: outbox.enqueue(prepared.attempt)).also { prepared.localId = it }
        }
        val saved = checkNotNull(outbox.load(id)) { "No se confirmó el almacenamiento local del intento." }
        check(saved.attempt.key == prepared.attempt.key && saved.attempt.identity == prepared.attempt.identity &&
            saved.attempt.items == prepared.attempt.items && saved.attempt.expectedTotalCents == prepared.attempt.expectedTotalCents) { "El intento guardado no coincide; requiere revisión." }
        check(access() == current) { "La sesión cambió; el intento quedó guardado para recuperación." }
        BackendSaleEmission(id, synchronize(id))
    }
    override suspend fun resume(localId: Long): BackendSaleEmission = exclusive {
        require(localId > 0)
        val current = access()
        val saved = checkNotNull(outbox.load(localId)) { "El intento local no existe." }
        check(saved.attempt.identity == current.identity) { "El intento pertenece a otra cuenta o sucursal." }
        BackendSaleEmission(localId, synchronize(localId))
    }
    override suspend fun pending(): List<Long> {
        val current = access()
        val ids = outbox.pending(current.identity)
        check(access() == current) { "La sesión cambió durante la restauración." }
        return Collections.unmodifiableList(ids.toList())
    }
    override suspend fun journal(): List<BackendSaleJournalEntry> {
        val current = access()
        val rows = outbox.journal(current.identity)
        check(access() == current) { "La sesión cambió durante la consulta." }
        return Collections.unmodifiableList(rows.toList())
    }
    override suspend fun checkResult(localId: Long): BackendSaleEmission = exclusive {
        require(localId > 0)
        val current = access()
        val saved = checkNotNull(outbox.load(localId)) { "El intento local no existe." }
        check(saved.attempt.identity == current.identity) { "El intento pertenece a otra cuenta o sucursal." }
        BackendSaleEmission(localId, synchronize(localId, recoveryOnly = true))
    }
    override suspend fun retire(localId: Long): BackendSaleEmission = exclusive {
        require(localId > 0)
        access()
        BackendSaleEmission(localId, synchronizer.retire(localId))
    }
    private suspend fun synchronize(id: Long, recoveryOnly: Boolean = false): BackendSaleEmissionOutcome = when (val outcome = synchronizer.synchronize(id, recoveryOnly)) {
        SaleSyncOutcome.Synced -> BackendSaleEmissionOutcome.Synced
        SaleSyncOutcome.AlreadyClaimed -> BackendSaleEmissionOutcome.AlreadyClaimed
        SaleSyncOutcome.SessionUnavailable -> BackendSaleEmissionOutcome.SessionUnavailable
        is SaleSyncOutcome.Pending -> BackendSaleEmissionOutcome.Pending(outcome.message)
        is SaleSyncOutcome.Failed -> BackendSaleEmissionOutcome.Failed(outcome.message)
    }
}

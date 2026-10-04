package com.intutec.viveroapp.feature.cashier.data.repository

import com.intutec.viveroapp.core.session.*
import com.intutec.viveroapp.feature.cart.domain.repository.BackendSaleIdentity
import com.intutec.viveroapp.feature.cashier.data.local.*
import com.intutec.viveroapp.feature.cashier.domain.repository.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

@Singleton
class BackendCashierPayments @Inject constructor(private val sessions: SessionStore, private val dao: BackendPaymentAttemptDao,
    private val remote: BackendCashierGateway) : BackendCashierPaymentRepository {
    private val mutex = Mutex()
    private data class Access(val token: String, val identity: BackendSaleIdentity)
    private fun access(): Access {
        sessions.expireBackend(System.currentTimeMillis())
        val state = sessions.backend.value
        val session = checkNotNull(state.authorizedSession("OPERATE_CASHIER", true)) { "Actualiza tu sesión y permisos de Caja." }
        return Access(session.token, BackendSaleIdentity(session.userId, requireNotNull(state.context?.branch).id))
    }
    override suspend fun pending(): List<BackendPendingPayment> { val who = access(); val rows = dao.pending(who.identity.userId, who.identity.branchId); check(access() == who); return rows.map { BackendPendingPayment(it.id, it.saleId, it.state) } }
    override suspend fun start(sale: Long, method: String, received: Long?, reference: String?): BackendPaymentReceipt = exclusive {
        val who = access(); check(dao.pending(who.identity.userId, who.identity.branchId).isEmpty()) { "Recupera el pago pendiente antes de iniciar otro." }
        require(method in setOf("CASH", "CARD", "TRANSFER"))
        require(if (method == "CASH") received != null && received in 1..9007199254740991L && reference == null else received == null)
        val normalizedReference = reference?.trim()?.ifBlank { null }
        require(method != "TRANSFER" || normalizedReference != null)
        normalizedReference?.let {
            require(it.codePointCount(0, it.length) <= 120 && it.none { ch -> ch.code < 32 || ch.code in 127..159 })
            if (method == "CARD") require(it.codePointCount(0, it.length) <= 64 && !Regex("^[0-9]{3,4}$").matches(it) && !Regex("^[0-9]{13,19}$").matches(it.filter(Char::isDigit)))
        }
        val claim = remote.claim(who.token, who.identity, sale)
        check(claim.identity == who.identity && claim.saleId == sale && Regex("^[a-f0-9]{64}$").matches(claim.token))
        check(access() == who) { "La sesión cambió antes de guardar el cobro." }
        val body = buildJsonObject { put("claim_token", claim.token); put("method", method); put("amount_received_cents", received?.let(::JsonPrimitive) ?: JsonNull); put("reference", normalizedReference?.let(::JsonPrimitive) ?: JsonNull) }.toString()
        val key = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it.toInt() and 255) }
        val id = withContext(NonCancellable) { dao.enqueue(BackendPaymentAttemptEntity(key = key, actorId = who.identity.userId, branchId = who.identity.branchId, saleId = sale, body = body)) }
        settle(id, who, recovery = false)
    }
    override suspend fun recover(id: Long): BackendPaymentReceipt = exclusive { settle(id, access(), recovery = true) }
    override suspend fun retry(id: Long): BackendPaymentReceipt = exclusive { settle(id, access(), recovery = true, retryMissing = true) }
    override suspend fun retire(id: Long): BackendPaymentRetirement = exclusive {
        val who = access(); val row = checkNotNull(dao.load(id))
        check(row.actorId == who.identity.userId && row.branchId == who.identity.branchId)
        check(dao.claim(id) == 1) { "Este intento ya está siendo atendido." }
        try {
            check(access() == who)
            val result = remote.retire(who.token, who.identity, row.saleId, row.key, row.body)
            when (result) {
                BackendPaymentRetirement.Retired -> check(dao.retire(id) == 1)
                is BackendPaymentRetirement.Committed -> check(dao.complete(id, receiptJson(result.receipt)) == 1)
            }
            result
        } catch (cancelled: CancellationException) { withContext(NonCancellable) { dao.uncertain(id) }; throw cancelled }
        catch (_: Exception) { dao.uncertain(id); error("El servidor no confirmó el cierre. El intento sigue pendiente.") }
    }
    private fun receiptJson(receipt: BackendPaymentReceipt) = buildJsonObject {
        put("id", receipt.id); put("sale_id", receipt.saleId); put("folio", receipt.folio)
        put("due_cents", receipt.dueCents); put("received_cents", receipt.receivedCents); put("change_cents", receipt.changeCents)
    }.toString()
    private suspend fun settle(id: Long, who: Access, recovery: Boolean, retryMissing: Boolean = false): BackendPaymentReceipt {
        val row = checkNotNull(dao.load(id)); check(row.actorId == who.identity.userId && row.branchId == who.identity.branchId)
        check(dao.claim(id) == 1) { "Este intento ya está siendo atendido." }
        return try {
            check(access() == who)
            // Explicit retry first observes the result; only an exact missing-result response
            // permits replaying the original immutable body/key. No replacement claim or key.
            val receipt = if (recovery) try { remote.recover(who.token, who.identity, row.saleId, row.key, row.body) }
                catch (missing: BackendPaymentNotFound) {
                    if (!retryMissing) throw missing
                    check(access() == who)
                    remote.pay(who.token, who.identity, row.saleId, row.key, row.body)
                }
                else remote.pay(who.token, who.identity, row.saleId, row.key, row.body)
            check(dao.complete(id, receiptJson(receipt)) == 1)
            receipt
        } catch (cancelled: CancellationException) { withContext(NonCancellable) { dao.uncertain(id) }; throw cancelled }
        catch (_: Exception) { dao.uncertain(id); error("No se confirmó el pago. El intento se conserva; consulta su resultado antes de cobrar de nuevo.") }
    }
    private suspend fun <T> exclusive(action: suspend () -> T): T {
        check(mutex.tryLock()) { "Hay una operación de Caja en curso." }
        return try { action() } finally { mutex.unlock() }
    }
}

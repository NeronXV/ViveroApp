package com.intutec.viveroapp.feature.cart.sync

import com.intutec.viveroapp.core.session.BackendAccessStatus
import com.intutec.viveroapp.core.session.SessionStore
import com.intutec.viveroapp.feature.cart.domain.repository.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

@Singleton
class BackendPendingSaleSynchronizer @Inject constructor(
    private val store: BackendSaleOutboxStore,
    private val remote: BackendSaleGateway,
    private val sessions: SessionStore,
) {
    suspend fun retire(id: Long): BackendSaleEmissionOutcome {
        sessions.expireBackend(System.currentTimeMillis())
        val current = sessions.backend.value
        val session = current.session ?: return BackendSaleEmissionOutcome.SessionUnavailable
        val context = current.context ?: return BackendSaleEmissionOutcome.SessionUnavailable
        if (current.accessStatus != BackendAccessStatus.READY || !context.canOperate("CREATE_SALES") || context.user.id != session.userId)
            return BackendSaleEmissionOutcome.SessionUnavailable
        val identity = BackendSaleIdentity(session.userId, requireNotNull(context.branch).id)
        val saved = try { store.load(id) } catch (_: IllegalArgumentException) {
            return BackendSaleEmissionOutcome.Failed("El intento guardado requiere revisión.")
        } ?: return BackendSaleEmissionOutcome.Failed("El intento local no existe.")
        if (saved.attempt.identity != identity) return BackendSaleEmissionOutcome.SessionUnavailable
        if (saved.state == "RETIRED") return BackendSaleEmissionOutcome.Retired
        if (!store.claim(id)) return BackendSaleEmissionOutcome.AlreadyClaimed
        return try {
            if (!canSend(session.token, identity)) {
                store.uncertain(id, "Sesión cambiada; conserva el intento para recuperarlo.")
                return BackendSaleEmissionOutcome.SessionUnavailable
            }
            // Server serializes retirement with submit. A 404 from recover is never enough.
            when (val result = remote.retire(session.token, saved.attempt)) {
                BackendSaleRetirement.Retired -> {
                    check(store.retire(id))
                    BackendSaleEmissionOutcome.Retired
                }
                is BackendSaleRetirement.Committed -> {
                    check(result.receipt.identity == identity && result.receipt.totalCents == saved.attempt.expectedTotalCents)
                    check(store.complete(id, result.receipt))
                    BackendSaleEmissionOutcome.Synced
                }
            }
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) { store.uncertain(id, "Cierre interrumpido; consulta o vuelve a cerrar el mismo intento.") }
            throw cancelled
        } catch (_: Exception) {
            val message = "No se confirmó el cierre. El intento se conserva; consulta o vuelve a cerrar el mismo intento."
            store.uncertain(id, message)
            BackendSaleEmissionOutcome.Pending(message)
        }
    }
    suspend fun synchronize(id: Long, recoveryOnly: Boolean = false): SaleSyncOutcome {
        sessions.expireBackend(System.currentTimeMillis())
        val current = sessions.backend.value
        val session = current.session ?: return SaleSyncOutcome.SessionUnavailable
        val context = current.context ?: return SaleSyncOutcome.SessionUnavailable
        if (current.accessStatus != BackendAccessStatus.READY || !context.canOperate("CREATE_SALES") || context.user.id != session.userId) return SaleSyncOutcome.SessionUnavailable
        val identity = BackendSaleIdentity(session.userId, requireNotNull(context.branch).id)
        val stored = try { store.load(id) } catch (_: IllegalArgumentException) { return SaleSyncOutcome.Failed("El intento guardado requiere revisión.") }
            ?: return SaleSyncOutcome.Failed("El intento local no existe.")
        if (stored.attempt.identity != identity) return SaleSyncOutcome.SessionUnavailable
        if (!store.claim(id)) return SaleSyncOutcome.AlreadyClaimed
        return try {
            if (!canSend(session.token, identity)) {
                store.uncertain(id, "Sesión cambiada; conserva el intento para recuperarlo.")
                return SaleSyncOutcome.SessionUnavailable
            }
            // Recovery precedes every retry, using the same key and payload.
            val receipt = if (recoveryOnly || stored.state == "UNCERTAIN") {
                try { remote.recover(session.token, stored.attempt) }
                catch (error: BackendSaleException) {
                    if (!recoveryOnly && error.status == 404 && error.code == "SALE_NOT_FOUND") {
                        if (!canSend(session.token, identity)) throw BackendSaleException(401, "SESSION_CHANGED")
                        remote.submit(session.token, stored.attempt)
                    } else throw error
                }
            } else remote.submit(session.token, stored.attempt)
            check(receipt.identity == identity && receipt.totalCents == stored.attempt.expectedTotalCents)
            check(store.complete(id, receipt))
            SaleSyncOutcome.Synced
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) { store.uncertain(id, "Envío interrumpido; recuperar antes de reenviar.") }
            throw cancelled
        } catch (error: BackendSaleException) {
            val message = if (recoveryOnly && error.status == 404 && error.code == "SALE_NOT_FOUND")
                "No se encontró un resultado confirmado. Conserva el intento; puedes reintentar su envío original."
                else error.message.orEmpty()
            store.uncertain(id, message)
            SaleSyncOutcome.Pending(message)
        } catch (_: Exception) {
            val message = "No se confirmó la venta; conserva el intento para recuperarlo."
            store.uncertain(id, message)
            SaleSyncOutcome.Pending(message)
        }
    }
    private fun canSend(token: String, identity: BackendSaleIdentity): Boolean {
        sessions.expireBackend(System.currentTimeMillis())
        val current = sessions.backend.value
        return current.session?.token == token && current.session.userId == identity.userId &&
            current.context?.user?.id == identity.userId && current.context.branch?.id == identity.branchId &&
            current.accessStatus == BackendAccessStatus.READY && current.context.canOperate("CREATE_SALES")
    }
}

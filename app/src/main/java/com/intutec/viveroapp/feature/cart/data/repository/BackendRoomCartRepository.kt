package com.intutec.viveroapp.feature.cart.data.repository

import com.intutec.viveroapp.core.session.BackendAccessStatus
import com.intutec.viveroapp.core.session.SessionStore
import com.intutec.viveroapp.feature.cart.data.local.BackendCartDao
import com.intutec.viveroapp.feature.cart.domain.repository.*
import com.intutec.viveroapp.feature.catalog.domain.repository.BackendCatalogProduct
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class BackendRoomCartRepository @Inject constructor(private val dao: BackendCartDao, private val sessions: SessionStore) : BackendCartRepository {
    private val mutation = Mutex()
    private fun identity(): BackendSaleIdentity {
        sessions.expireBackend(System.currentTimeMillis())
        val state = sessions.backend.value
        val session = checkNotNull(state.session) { "Inicia sesión antes de editar la comanda." }
        val context = checkNotNull(state.context) { "Consulta los permisos antes de editar." }
        check(state.accessStatus == BackendAccessStatus.READY && context.user.id == session.userId && context.canOperate("CREATE_SALES")) {
            "La sesión no permite editar comandas."
        }
        return BackendSaleIdentity(session.userId, requireNotNull(context.branch).id)
    }
    override fun observe(identity: BackendSaleIdentity) = dao.observe(identity.userId, identity.branchId).map { saved ->
        saved?.let {
            check(it.cart.actorId == identity.userId && it.cart.branchId == identity.branchId)
            BackendCartSnapshot(it.cart.id, it.cart.revision, identity, java.util.Collections.unmodifiableList(it.items.sortedBy { row -> row.productId }.map { row ->
                BackendCartLine(row.productId, row.name, row.unit, row.priceCents, row.quantity)
            }))
        }
    }
    override suspend fun add(product: BackendCatalogProduct) = mutation.withLock {
        val who = identity()
        require(product.id in 1..MAX_BACKEND_ID && product.effectivePriceCents in 0..MAX_BACKEND_CENTS)
        dao.add(who.userId, who.branchId, product.id, product.commonName, product.unit, product.effectivePriceCents)
    }
    override suspend fun quantity(productId: Long, quantity: Int) = mutation.withLock {
        val who = identity(); dao.quantity(who.userId, who.branchId, productId, quantity)
    }
    override suspend fun remove(productId: Long) = mutation.withLock {
        val who = identity(); dao.remove(who.userId, who.branchId, productId)
    }
    override suspend fun clear() = mutation.withLock { val who = identity(); dao.clear(who.userId, who.branchId) }
}

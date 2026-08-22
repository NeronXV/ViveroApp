package com.intutec.viveroapp.feature.cashier.domain.usecase

import com.intutec.viveroapp.core.security.AppPermission
import com.intutec.viveroapp.core.session.SessionMode
import com.intutec.viveroapp.core.session.SessionStore
import com.intutec.viveroapp.feature.cashier.domain.model.CashierOrderDetail
import com.intutec.viveroapp.feature.cashier.domain.model.CashierOrderSummary
import com.intutec.viveroapp.feature.cashier.domain.repository.CashierRepository
import javax.inject.Inject

class GetPendingCashierOrdersUseCase @Inject constructor(
    private val repository: CashierRepository,
    private val sessionStore: SessionStore,
) {
    suspend operator fun invoke(): Result<List<CashierOrderSummary>> =
        cashierBranchId(sessionStore).fold(
            onSuccess = { repository.getPendingOrders(it) },
            onFailure = { Result.failure(it) },
        )
}

class GetCashierOrderDetailUseCase @Inject constructor(
    private val repository: CashierRepository,
    private val sessionStore: SessionStore,
) {
    suspend operator fun invoke(orderId: String): Result<CashierOrderDetail> =
        cashierBranchId(sessionStore).fold(
            onSuccess = { repository.getPendingOrder(it, orderId) },
            onFailure = { Result.failure(it) },
        )
}

private fun cashierBranchId(sessionStore: SessionStore): Result<String> = runCatching {
    val session = checkNotNull(sessionStore.session.value) { "La sesión ya no está disponible." }
    check(session.mode == SessionMode.REMOTE) { "Caja requiere una sesión remota." }
    check(session.canOperateAtBranch(AppPermission.OPERATE_CASHIER)) {
        "La cuenta no puede operar Caja en una sucursal activa."
    }
    checkNotNull(session.branchId) { "Caja requiere una sucursal asignada." }
}

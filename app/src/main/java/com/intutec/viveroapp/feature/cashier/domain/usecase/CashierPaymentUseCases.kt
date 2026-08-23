package com.intutec.viveroapp.feature.cashier.domain.usecase

import com.intutec.viveroapp.core.security.AppPermission
import com.intutec.viveroapp.core.session.SessionMode
import com.intutec.viveroapp.core.session.SessionStore
import com.intutec.viveroapp.core.session.UserSession
import com.intutec.viveroapp.feature.cashier.domain.model.CashierOrderDetail
import com.intutec.viveroapp.feature.cashier.domain.model.CashierPaymentAttempt
import com.intutec.viveroapp.feature.cashier.domain.model.CashierPaymentInput
import com.intutec.viveroapp.feature.cashier.domain.model.CashierPaymentResult
import com.intutec.viveroapp.feature.cashier.domain.repository.CashierPaymentRepository
import javax.inject.Inject

class RestoreCashierPaymentAttemptUseCase @Inject constructor(
    private val repository: CashierPaymentRepository,
    private val sessionStore: SessionStore,
) {
    suspend operator fun invoke(order: CashierOrderDetail): Result<CashierPaymentAttempt?> = runCatching {
        requireCashierSession(sessionStore).requireOrderBranch(order)
        repository.restore(order.summary.id)
    }
}

class ClaimCashierPaymentUseCase @Inject constructor(
    private val repository: CashierPaymentRepository,
    private val sessionStore: SessionStore,
) {
    suspend operator fun invoke(order: CashierOrderDetail): Result<CashierPaymentAttempt> = runCatching {
        val session = requireCashierSession(sessionStore).requireOrderBranch(order)
        repository.claim(order, session.userId)
    }
}

class RenewCashierPaymentClaimUseCase @Inject constructor(
    private val repository: CashierPaymentRepository,
    private val sessionStore: SessionStore,
) {
    suspend operator fun invoke(order: CashierOrderDetail): Result<CashierPaymentAttempt> = runCatching {
        requireCashierSession(sessionStore).requireOrderBranch(order)
        repository.renew(order.summary.id)
    }
}

class ReleaseCashierPaymentClaimUseCase @Inject constructor(
    private val repository: CashierPaymentRepository,
    private val sessionStore: SessionStore,
) {
    suspend operator fun invoke(order: CashierOrderDetail): Result<Unit> = runCatching {
        requireCashierSession(sessionStore).requireOrderBranch(order)
        repository.release(order.summary.id)
    }
}

class ConfirmCashierPaymentUseCase @Inject constructor(
    private val repository: CashierPaymentRepository,
    private val sessionStore: SessionStore,
) {
    suspend operator fun invoke(
        order: CashierOrderDetail,
        input: CashierPaymentInput?,
        retryUncertain: Boolean = false,
    ): Result<CashierPaymentResult> = runCatching {
        val session = requireCashierSession(sessionStore).requireOrderBranch(order)
        repository.confirm(order, session.userId, input, retryUncertain)
    }
}

private fun requireCashierSession(store: SessionStore): UserSession {
    val session = checkNotNull(store.session.value) { "La sesión ya no está disponible." }
    check(session.mode == SessionMode.REMOTE && session.canOperateAtBranch(AppPermission.OPERATE_CASHIER)) {
        "La cuenta no puede operar Caja en una sucursal activa."
    }
    return session
}

private fun UserSession.requireOrderBranch(order: CashierOrderDetail): UserSession {
    check(branchId == order.summary.branchId) { "La comanda no pertenece a la sucursal activa." }
    return this
}

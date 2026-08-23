package com.intutec.viveroapp.feature.cashier.domain.repository

import com.intutec.viveroapp.feature.cashier.domain.model.CashierOrderDetail
import com.intutec.viveroapp.feature.cashier.domain.model.CashierPaymentAttempt
import com.intutec.viveroapp.feature.cashier.domain.model.CashierPaymentInput
import com.intutec.viveroapp.feature.cashier.domain.model.CashierPaymentResult

interface CashierPaymentRepository {
    suspend fun restore(saleId: String): CashierPaymentAttempt?
    suspend fun claim(order: CashierOrderDetail, cashierId: String): CashierPaymentAttempt
    suspend fun renew(saleId: String): CashierPaymentAttempt
    suspend fun release(saleId: String)
    suspend fun confirm(
        order: CashierOrderDetail,
        cashierId: String,
        input: CashierPaymentInput?,
        retryUncertain: Boolean,
    ): CashierPaymentResult
}

package com.intutec.viveroapp.feature.cashier.domain.repository

import com.intutec.viveroapp.feature.cashier.domain.model.CashierOrderDetail
import com.intutec.viveroapp.feature.cashier.domain.model.CashierOrderSummary

interface CashierRepository {
    suspend fun getPendingOrders(branchId: String): Result<List<CashierOrderSummary>>
    suspend fun getPendingOrder(branchId: String, orderId: String): Result<CashierOrderDetail>
}

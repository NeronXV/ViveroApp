package com.intutec.viveroapp.feature.cashier.data.remote

interface CashierRemoteDataSource {
    suspend fun loadPendingOrders(branchId: String): List<RemoteCashierOrderDto>
    suspend fun loadPendingOrder(branchId: String, orderId: String): List<RemoteCashierOrderDto>
}

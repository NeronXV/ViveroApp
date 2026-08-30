package com.intutec.viveroapp.feature.reports.data.remote

import java.time.LocalDate

interface ReportsRemoteDataSource {
    suspend fun getDailySales(
        branchId: String?,
        startDate: LocalDate?,
        endDate: LocalDate?,
    ): String // Returns JSON string from RPC

    suspend fun getTopProducts(
        branchId: String?,
        limit: Int,
    ): String // Returns JSON string from RPC
}

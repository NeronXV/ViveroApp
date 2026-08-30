package com.intutec.viveroapp.feature.reports.domain.repository

import com.intutec.viveroapp.feature.reports.domain.model.DailySales
import com.intutec.viveroapp.feature.reports.domain.model.TopProduct
import java.time.LocalDate

interface ReportsRepository {
    suspend fun getDailySales(
        branchId: String? = null,
        startDate: LocalDate? = null,
        endDate: LocalDate? = null,
    ): Result<List<DailySales>>

    suspend fun getTopProducts(
        branchId: String? = null,
        limit: Int = 10,
    ): Result<List<TopProduct>>
}

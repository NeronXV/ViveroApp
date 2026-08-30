package com.intutec.viveroapp.feature.reports.domain.model

import java.time.LocalDate

data class DailySales(
    val branchId: String,
    val branchName: String,
    val day: LocalDate,
    val salesCount: Int,
    val revenueCents: Long,
    val discountCents: Long,
)

data class TopProduct(
    val productId: String,
    val productName: String,
    val productCode: String,
    val totalQuantity: Double, // Safe decimal handling
    val totalRevenueCents: Long,
)

data class ReportsSummary(
    val dailySales: List<DailySales>,
    val topProducts: List<TopProduct>,
)

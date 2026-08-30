package com.intutec.viveroapp.feature.reports.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class RemoteDailySalesDto(
    val branchId: String,
    val branchName: String,
    val day: String,
    val salesCount: Int,
    val revenueCents: Long,
    val discountCents: Long,
)

@Serializable
data class RemoteTopProductDto(
    val productId: String,
    val productName: String,
    val productCode: String,
    val totalQuantity: Double,
    val totalRevenueCents: Long,
)

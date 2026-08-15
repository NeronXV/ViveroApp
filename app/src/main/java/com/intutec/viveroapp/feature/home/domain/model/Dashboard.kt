package com.intutec.viveroapp.feature.home.domain.model

import com.intutec.viveroapp.core.model.UserRole

data class Dashboard(
    val userName: String,
    val role: UserRole,
    val branchName: String,
    val pendingTickets: Int,
    val lowStockProducts: Int,
    val activePromotions: Int,
    val modules: List<DashboardModule>,
)

data class DashboardModule(
    val id: String,
    val title: String,
    val description: String,
    val enabled: Boolean,
)

package com.intutec.viveroapp.feature.home.domain.model

import com.intutec.viveroapp.core.model.UserRole
import com.intutec.viveroapp.core.session.SessionMode

data class Dashboard(
    val userName: String,
    val role: UserRole,
    val branchName: String,
    val sessionMode: SessionMode,
    val canCreateSales: Boolean,
    val modules: List<DashboardModule>,
)

data class DashboardModule(
    val id: String,
    val title: String,
    val description: String,
    val enabled: Boolean,
)

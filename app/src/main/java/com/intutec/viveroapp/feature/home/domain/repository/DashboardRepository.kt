package com.intutec.viveroapp.feature.home.domain.repository

import com.intutec.viveroapp.feature.home.domain.model.Dashboard

interface DashboardRepository {
    suspend fun getDashboard(): Result<Dashboard>
}

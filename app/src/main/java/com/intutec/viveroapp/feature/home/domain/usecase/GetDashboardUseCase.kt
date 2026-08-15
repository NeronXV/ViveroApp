package com.intutec.viveroapp.feature.home.domain.usecase

import com.intutec.viveroapp.feature.home.domain.model.Dashboard
import com.intutec.viveroapp.feature.home.domain.repository.DashboardRepository
import javax.inject.Inject

class GetDashboardUseCase @Inject constructor(
    private val repository: DashboardRepository,
) {
    suspend operator fun invoke(): Result<Dashboard> = repository.getDashboard()
}

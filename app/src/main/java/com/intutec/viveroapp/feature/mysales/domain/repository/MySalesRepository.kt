package com.intutec.viveroapp.feature.mysales.domain.repository

import com.intutec.viveroapp.feature.mysales.domain.model.MySalesPage
import java.time.Instant

interface MySalesRepository {
    suspend fun getMyRecentSales(
        limit: Int = 20,
        afterCreatedAt: Instant? = null,
        afterId: String? = null,
    ): Result<MySalesPage>
}

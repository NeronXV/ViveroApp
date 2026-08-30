package com.intutec.viveroapp.feature.mysales.data.remote

import java.time.Instant

interface MySalesRemoteDataSource {
    suspend fun getMyRecentSales(
        limit: Int,
        afterCreatedAt: Instant?,
        afterId: String?,
    ): String
}

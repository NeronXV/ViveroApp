package com.intutec.viveroapp.feature.mysales.data.remote

import com.intutec.viveroapp.core.network.SupabaseProvider
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import javax.inject.Inject

class SupabaseMySalesRemoteDataSource @Inject constructor(
    private val supabaseProvider: SupabaseProvider,
) : MySalesRemoteDataSource {

    override suspend fun getMyRecentSales(
        limit: Int,
        afterCreatedAt: Instant?,
        afterId: String?,
    ): String {
        val client = checkNotNull(supabaseProvider.client) { "Supabase no está configurado." }
        val response = client.postgrest.rpc(
            function = "get_my_recent_sales",
            parameters = buildJsonObject {
                put("p_limit", limit)
                put("p_after_created_at", afterCreatedAt?.toString())
                put("p_after_id", afterId)
            }
        )
        return response.data
    }
}

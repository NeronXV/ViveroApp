package com.intutec.viveroapp.feature.customer.data.remote

import com.intutec.viveroapp.core.network.SupabaseProvider
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

class SupabaseCustomerRemoteDataSource @Inject constructor(
    private val supabaseProvider: SupabaseProvider,
) : CustomerRemoteDataSource {
    override suspend fun searchCustomers(query: String, limit: Int): String {
        val client = checkNotNull(supabaseProvider.client) { "Supabase no está configurado." }
        val response = client.postgrest.rpc(
            function = "search_customers",
            parameters = buildJsonObject {
                put("p_query", query)
                put("p_limit", limit)
            }
        )
        return response.data
    }
}

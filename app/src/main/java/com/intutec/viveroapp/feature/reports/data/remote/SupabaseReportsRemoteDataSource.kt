package com.intutec.viveroapp.feature.reports.data.remote

import com.intutec.viveroapp.core.network.SupabaseProvider
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class SupabaseReportsRemoteDataSource @Inject constructor(
    private val supabaseProvider: SupabaseProvider,
) : ReportsRemoteDataSource {

    override suspend fun getDailySales(
        branchId: String?,
        startDate: LocalDate?,
        endDate: LocalDate?,
    ): String {
        val client = checkNotNull(supabaseProvider.client) { "Supabase no está configurado." }
        val response = client.postgrest.rpc(
            function = "get_report_daily_sales",
            parameters = buildJsonObject {
                put("p_branch_id", branchId)
                put("p_start_date", startDate?.format(DateTimeFormatter.ISO_LOCAL_DATE))
                put("p_end_date", endDate?.format(DateTimeFormatter.ISO_LOCAL_DATE))
            }
        )
        return response.data
    }

    override suspend fun getTopProducts(branchId: String?, limit: Int): String {
        val client = checkNotNull(supabaseProvider.client) { "Supabase no está configurado." }
        val response = client.postgrest.rpc(
            function = "get_report_top_products",
            parameters = buildJsonObject {
                put("p_branch_id", branchId)
                put("p_limit", limit)
            }
        )
        return response.data
    }
}

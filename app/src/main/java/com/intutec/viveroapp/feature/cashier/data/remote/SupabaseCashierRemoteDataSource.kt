package com.intutec.viveroapp.feature.cashier.data.remote

import com.intutec.viveroapp.core.network.SupabaseProvider
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject

class SupabaseCashierRemoteDataSource @Inject constructor(
    private val supabaseProvider: SupabaseProvider,
) : CashierRemoteDataSource {
    override suspend fun loadPendingOrders(branchId: String): List<RemoteCashierOrderDto> =
        requireClient().from("sales").select(columns = CASHIER_ORDER_COLUMNS) {
            filter {
                eq("branch_id", branchId)
                eq("status", CASHIER_STATUS)
            }
            order("created_at", Order.ASCENDING)
        }.decodeList()

    override suspend fun loadPendingOrder(branchId: String, orderId: String): List<RemoteCashierOrderDto> =
        requireClient().from("sales").select(columns = CASHIER_ORDER_COLUMNS) {
            filter {
                eq("id", orderId)
                eq("branch_id", branchId)
                eq("status", CASHIER_STATUS)
            }
            limit(2)
        }.decodeList()

    private fun requireClient() = checkNotNull(supabaseProvider.client) {
        "Supabase no está configurado para Caja."
    }

    internal companion object {
        const val CASHIER_STATUS = "SENT_TO_CASHIER"
        val CASHIER_ORDER_COLUMNS = Columns.raw(
            "id,folio,branch_id,created_by,subtotal_cents,discount_cents,total_cents," +
                "status,created_at,updated_at," +
                "items:sale_items(id,sale_id,product_id,product_name,internal_code,quantity," +
                "list_price_cents,unit_price_cents,discount_cents,line_total_cents,promotion_name)",
        )
    }
}

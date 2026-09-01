package com.intutec.viveroapp.feature.catalog.data.remote

import com.intutec.viveroapp.core.network.SupabaseProvider
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.json.Json
import javax.inject.Inject

class SupabaseCatalogRemoteDataSource @Inject constructor(
    private val supabaseProvider: SupabaseProvider,
) : CatalogRemoteDataSource {
    override suspend fun loadVisibleCategories(): List<RemoteCategoryDto> =
        requireClient().from("categories").select(
            columns = Columns.raw("id,name,is_active"),
        ) {
            filter { eq("is_active", true) }
        }.decodeList()

    override suspend fun loadActiveProducts(): List<RemoteProductDto> =
        requireClient().from("products").select(
            columns = PRODUCT_COLUMNS,
        ) {
            filter { eq("is_active", true) }
        }.decodeList()

    override suspend fun loadCatalogPricing(): RemoteCatalogPricingDto {
        val response = requireClient().postgrest.rpc("get_catalog_pricing")
        return pricingJson.decodeFromString(response.data)
    }

    override suspend fun loadMyBranchInventory(): RemoteBranchCatalogInventoryDto? = try {
        val response = requireClient().postgrest.rpc("get_my_branch_catalog_inventory")
        inventoryJson.decodeFromString(response.data)
    } catch (error: PostgrestRestException) {
        if (error.statusCode == 404 || error.code == "PGRST202") null else throw error
    }

    private fun requireClient() = checkNotNull(supabaseProvider.client) {
        "Supabase no está configurado para el catálogo remoto."
    }

    private companion object {
        val inventoryJson = Json { ignoreUnknownKeys = false }
        val pricingJson = Json { ignoreUnknownKeys = false }
        val PRODUCT_COLUMNS = Columns.raw(
            "id,internal_code,barcode,common_name,scientific_name,description,category_id," +
                "price_cents,wholesale_price_cents,unit,minimum_stock,watering_advice," +
                "light_type,recommended_climate,is_active,created_at,updated_at," +
                "images:product_images(id,product_id,storage_path,alt_text,sort_order,is_primary)",
        )
    }
}

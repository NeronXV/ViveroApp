package com.intutec.viveroapp.feature.catalog.data.remote

import com.intutec.viveroapp.core.network.SupabaseProvider
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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

    override suspend fun findActiveProductByCode(code: String): RemoteProductScanDto? = try {
        val response = requireClient().postgrest.rpc(
            function = "get_product_by_scan_code",
            parameters = buildJsonObject { put("p_code", code) },
        )
        scanJson.decodeFromString(response.data)
    } catch (error: PostgrestRestException) {
        val msg = error.message.orEmpty()
        when {
            error.statusCode == 404 || error.code == "PGRST202" -> throw IllegalStateException(
                "El servicio de consulta por QR todavía no está disponible en este entorno. Aplica la migración correspondiente y vuelve a intentar.",
            )
            msg.contains("PRODUCT_SCAN_UNAUTHORIZED") || error.code == "42501" -> throw IllegalStateException(
                "Tu cuenta no tiene permiso para consultar productos.",
            )
            msg.contains("PRODUCT_SCAN_CODE_INVALID") || error.code == "22023" -> throw IllegalArgumentException(
                "El código escaneado no es válido. Verifica el formato e intenta nuevamente.",
            )
            msg.contains("PRODUCT_SCAN_CODE_AMBIGUOUS") || error.code == "P0001" -> throw IllegalStateException(
                "Existe más de un producto activo asociado a este código.",
            )
            else -> throw error
        }
    } catch (error: Exception) {
        if (error is java.io.IOException ||
            error.message?.contains("Unable to resolve host") == true ||
            error.message?.contains("Failed to connect") == true ||
            error.message?.contains("network", ignoreCase = true) == true
        ) {
            throw IllegalStateException("No pudimos consultar el producto. Revisa tu conexión e intenta nuevamente.", error)
        }
        throw error
    }

    private fun requireClient() = checkNotNull(supabaseProvider.client) {
        "Supabase no está configurado para el catálogo remoto."
    }

    private companion object {
        val inventoryJson = Json { ignoreUnknownKeys = false }
        val pricingJson = Json { ignoreUnknownKeys = false }
        val scanJson = Json { ignoreUnknownKeys = false }
        val PRODUCT_COLUMNS = Columns.raw(
            "id,internal_code,barcode,common_name,scientific_name,description,category_id," +
                "price_cents,wholesale_price_cents,unit,minimum_stock,watering_advice," +
                "light_type,recommended_climate,is_active,created_at,updated_at," +
                "images:product_images(id,product_id,storage_path,alt_text,sort_order,is_primary)",
        )
    }
}

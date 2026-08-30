package com.intutec.viveroapp.feature.catalog.data.remote

import com.intutec.viveroapp.core.network.SupabaseProvider
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject

class SupabaseCatalogAdminRemoteDataSource @Inject constructor(
    private val supabaseProvider: SupabaseProvider,
) : CatalogAdminRemoteDataSource {

    override suspend fun loadCategoriesRaw(): List<RemoteCategoryDto> =
        requireClient().from("categories").select(columns = Columns.raw("id,name,is_active")) {
            filter { eq("is_active", true) }
        }.decodeList()

    override suspend fun upsertCategory(name: String, description: String?): RemoteCategoryDto {
        val client = requireClient()
        val response = client.postgrest.rpc(
            function = "upsert_category",
            parameters = JsonObject(mapOf(
                "p_name" to JsonPrimitive(name.trim()),
                "p_description" to (description?.trim()?.takeIf(String::isNotEmpty)?.let(::JsonPrimitive) ?: JsonNull),
                "p_is_active" to JsonPrimitive(true),
            )),
        )
        return adminJson.decodeFromString(response.data)
    }

    override suspend fun upsertProduct(
        id: String?,
        internalCode: String,
        barcode: String?,
        commonName: String,
        scientificName: String?,
        description: String,
        categoryId: String,
        priceCents: Long,
        wholesalePriceCents: Long?,
        unit: String,
        minimumStock: Double,
        wateringAdvice: String,
        lightType: String,
        recommendedClimate: String,
        isActive: Boolean,
    ): String {
        val client = requireClient()
        val response = client.postgrest.rpc(
            function = "upsert_product",
            parameters = JsonObject(mapOf(
                "p_id" to (id?.let(::JsonPrimitive) ?: JsonNull),
                "p_internal_code" to JsonPrimitive(internalCode.trim()),
                "p_barcode" to (barcode?.trim()?.takeIf(String::isNotEmpty)?.let(::JsonPrimitive) ?: JsonNull),
                "p_common_name" to JsonPrimitive(commonName.trim()),
                "p_scientific_name" to (scientificName?.trim()?.takeIf(String::isNotEmpty)?.let(::JsonPrimitive) ?: JsonNull),
                "p_description" to JsonPrimitive(description.trim()),
                "p_category_id" to JsonPrimitive(categoryId),
                "p_price_cents" to JsonPrimitive(priceCents),
                "p_wholesale_price_cents" to (wholesalePriceCents?.let(::JsonPrimitive) ?: JsonNull),
                "p_unit" to JsonPrimitive(unit.trim()),
                "p_minimum_stock" to JsonPrimitive(minimumStock),
                "p_watering_advice" to JsonPrimitive(wateringAdvice.trim()),
                "p_light_type" to JsonPrimitive(lightType.trim()),
                "p_recommended_climate" to JsonPrimitive(recommendedClimate.trim()),
                "p_is_active" to JsonPrimitive(isActive),
            )),
        )
        return response.data
    }

    private fun requireClient() = checkNotNull(supabaseProvider.client) {
        "Supabase no está configurado para catálogo."
    }

    private companion object {
        val adminJson = Json { ignoreUnknownKeys = true }
    }
}

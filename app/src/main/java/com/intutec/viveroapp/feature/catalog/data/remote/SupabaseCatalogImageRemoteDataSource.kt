package com.intutec.viveroapp.feature.catalog.data.remote

import com.intutec.viveroapp.core.network.SupabaseProvider
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import javax.inject.Inject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class SupabaseCatalogImageRemoteDataSource @Inject constructor(
    private val supabaseProvider: SupabaseProvider,
) : CatalogImageRemoteDataSource {

    override suspend fun uploadImage(productId: String, imageId: String, bytes: ByteArray, mimeType: String): String {
        val client = requireClient()
        val path = "${productId}/${imageId}.jpg"
        // bucket catalog-images, upsert true for idempotent retry
        client.storage.from("catalog-images").upload(path, bytes) {
            upsert = true
        }
        return path
    }

    override suspend fun insertProductImage(productId: String, imageId: String, storagePath: String): String {
        val client = requireClient()
        // direct insert via postgrest, RLS requires MANAGE_PRODUCTS
        // Use from insert and select to get id
        client.from("product_images").insert(
            JsonObject(mapOf(
                "id" to JsonPrimitive(imageId),
                "product_id" to JsonPrimitive(productId),
                "storage_path" to JsonPrimitive(storagePath),
                "sort_order" to JsonPrimitive(0),
                "is_primary" to JsonPrimitive(false),
            )),
        )
        return imageId
    }

    override suspend fun setPrimaryImage(imageId: String) {
        val client = requireClient()
        client.postgrest.rpc(
            function = "set_product_image_primary",
            parameters = JsonObject(mapOf("p_image_id" to JsonPrimitive(imageId))),
        )
    }

    override suspend fun deleteProductImage(imageId: String) {
        val client = requireClient()
        client.from("product_images").delete { filter { eq("id", imageId) } }
    }

    private fun requireClient() = checkNotNull(supabaseProvider.client) { "Supabase no configurado" }
}

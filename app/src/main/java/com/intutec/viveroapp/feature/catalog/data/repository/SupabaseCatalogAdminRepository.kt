package com.intutec.viveroapp.feature.catalog.data.repository

import com.intutec.viveroapp.feature.catalog.data.remote.CatalogAdminRemoteDataSource
import com.intutec.viveroapp.feature.catalog.data.remote.CatalogImageRemoteDataSource
import com.intutec.viveroapp.feature.catalog.data.remote.RemoteCategoryDto
import com.intutec.viveroapp.feature.catalog.domain.model.Category
import com.intutec.viveroapp.feature.catalog.domain.model.Product
import com.intutec.viveroapp.feature.catalog.domain.repository.CatalogAdminRepository
import com.intutec.viveroapp.feature.catalog.domain.repository.ProductUpsertRequest
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

class SupabaseCatalogAdminRepository @Inject constructor(
    private val remote: CatalogAdminRemoteDataSource,
    private val imageRemote: CatalogImageRemoteDataSource,
) : CatalogAdminRepository {

    override suspend fun uploadAndRegisterImage(productId: String, imageId: String, bytes: ByteArray, mimeType: String): Result<String> = runCatching {
        productId.requireUuid("product id")
        imageId.requireUuid("image id")
        val path = imageRemote.uploadImage(productId, imageId, bytes, mimeType)
        try {
            imageRemote.insertProductImage(productId, imageId, path)
        } catch (e: Exception) {
            // Si registro falla, no duplicar archivo en retry con mismo path (upsert true ya maneja), propagar error para reintento
            throw e
        }
        imageRemote.setPrimaryImage(imageId)
        path
    }

    override suspend fun setPrimaryImage(imageId: String): Result<Unit> = runCatching {
        imageRemote.setPrimaryImage(imageId.requireUuid("image id"))
    }

    override suspend fun loadCategories(): Result<List<Category>> = runCatching {
        remote.loadCategoriesRaw()
            .map(RemoteCategoryDto::toDomain)
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, Category::name).thenBy(Category::id))
            .also { check(it.map(Category::id).distinct().size == it.size) { "Categorías duplicadas." } }
    }

    override suspend fun upsertCategory(name: String, description: String?): Result<Category> = runCatching {
        val trimmed = name.trim()
        require(trimmed.length in 2..100) { "El nombre de categoría debe tener 2 a 100 caracteres." }
        remote.upsertCategory(trimmed, description).toDomain()
    }

    override suspend fun upsertProduct(request: ProductUpsertRequest): Result<Product> = runCatching {
        validate(request)
        val json = remote.upsertProduct(
            id = request.id?.requireUuid("product id"),
            internalCode = request.internalCode.trim(),
            barcode = request.barcode?.trim()?.takeIf(String::isNotEmpty),
            commonName = request.commonName.trim(),
            scientificName = request.scientificName?.trim()?.takeIf(String::isNotEmpty),
            description = request.description.trim(),
            categoryId = request.categoryId.requireUuid("category id"),
            priceCents = request.priceCents,
            wholesalePriceCents = request.wholesalePriceCents,
            unit = request.unit.trim(),
            minimumStock = request.minimumStock.toDouble(),
            wateringAdvice = request.wateringAdvice.trim(),
            lightType = request.lightType.trim(),
            recommendedClimate = request.recommendedClimate.trim(),
            isActive = request.isActive,
        )
        parseProductJson(json, request)
    }

    private fun validate(r: ProductUpsertRequest) {
        val code = r.internalCode.trim()
        require(code.length in 2..40) { "El código interno debe tener 2 a 40 caracteres." }
        require(r.commonName.trim().length in 2..160) { "El nombre común debe tener 2 a 160 caracteres." }
        require(r.categoryId.isNotBlank()) { "Selecciona una categoría." }
        r.categoryId.requireUuid("category id")
        r.id?.requireUuid("product id")
        require(r.priceCents >= 0) { "El precio no puede ser negativo." }
        require(r.wholesalePriceCents == null || r.wholesalePriceCents >= 0) { "El precio de mayoreo no puede ser negativo." }
        require(r.unit.trim() in VALID_UNITS) { "Selecciona una unidad válida." }
        require(r.minimumStock >= 0) { "La existencia mínima no puede ser negativa." }
    }

    private fun parseProductJson(json: String, request: ProductUpsertRequest): Product {
        // upsert_product returns public.products row as JSON object
        val element = adminJson.parseToJsonElement(json)
        val obj = element.jsonObject
        // tolerate wrapped array or object
        val actual = when {
            obj.containsKey("id") -> obj
            else -> element.jsonObject
        }
        val id = (actual["id"]?.jsonPrimitive?.content ?: request.id ?: error("Respuesta sin id")).requireUuid("product id")
        val categoryId = (actual["category_id"]?.jsonPrimitive?.content ?: request.categoryId).requireUuid("category id")
        return Product(
            id = id,
            internalCode = actual["internal_code"]?.jsonPrimitive?.content ?: request.internalCode.trim().uppercase(),
            barcode = actual["barcode"]?.takeIf { it.jsonPrimitive.content.isNotBlank() }?.jsonPrimitive?.content ?: request.barcode?.trim()?.takeIf(String::isNotEmpty),
            commonName = actual["common_name"]?.jsonPrimitive?.content ?: request.commonName.trim(),
            scientificName = actual["scientific_name"]?.jsonPrimitive?.content?.takeIf(String::isNotEmpty) ?: request.scientificName?.trim()?.takeIf(String::isNotEmpty),
            description = actual["description"]?.jsonPrimitive?.content ?: request.description.trim(),
            category = Category(id = categoryId, name = "Categoría"),
            priceCents = actual["price_cents"]?.jsonPrimitive?.longOrNull ?: request.priceCents,
            wholesalePriceCents = actual["wholesale_price_cents"]?.jsonPrimitive?.longOrNull ?: request.wholesalePriceCents,
            unit = actual["unit"]?.jsonPrimitive?.content ?: request.unit.trim(),
            stockAvailable = 0,
            minimumStock = (actual["minimum_stock"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: request.minimumStock.toDouble()).toInt(),
            imageKey = "",
            wateringAdvice = actual["watering_advice"]?.jsonPrimitive?.content ?: request.wateringAdvice,
            lightType = actual["light_type"]?.jsonPrimitive?.content ?: request.lightType,
            recommendedClimate = actual["recommended_climate"]?.jsonPrimitive?.content ?: request.recommendedClimate,
            isActive = actual["is_active"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: request.isActive,
            promotion = null,
            createdAt = actual["created_at"]?.jsonPrimitive?.content?.let { it.parseTimestampOrNow() } ?: Instant.now(),
            updatedAt = actual["updated_at"]?.jsonPrimitive?.content?.let { it.parseTimestampOrNow() } ?: Instant.now(),
            stockKnown = false,
            images = emptyList(),
        )
    }

    private fun String.parseTimestampOrNow(): Instant = try {
        OffsetDateTime.parse(this, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant()
    } catch (_: Exception) { Instant.now() }

    private companion object {
        val VALID_UNITS = setOf("pieza", "maceta", "charola", "bolsa", "kg")
        val adminJson = Json { ignoreUnknownKeys = true }
    }
}

private fun RemoteCategoryDto.toDomain(): Category {
    check(isActive) { "Categoría inactiva." }
    val normalized = name.trim()
    check(normalized.isNotEmpty()) { "Categoría sin nombre." }
    return Category(id = id.requireUuid("category id"), name = normalized)
}

private fun String.requireUuid(field: String): String = try {
    UUID.fromString(this).toString()
} catch (_: IllegalArgumentException) {
    throw IllegalStateException("El campo $field no es un UUID válido.")
}

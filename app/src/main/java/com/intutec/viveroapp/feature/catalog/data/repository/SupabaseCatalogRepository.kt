package com.intutec.viveroapp.feature.catalog.data.repository

import com.intutec.viveroapp.feature.catalog.data.remote.CatalogRemoteDataSource
import com.intutec.viveroapp.feature.catalog.data.remote.RemoteCategoryDto
import com.intutec.viveroapp.feature.catalog.data.remote.RemoteProductDto
import com.intutec.viveroapp.feature.catalog.data.remote.RemoteProductImageDto
import com.intutec.viveroapp.feature.catalog.domain.model.CatalogSnapshot
import com.intutec.viveroapp.feature.catalog.domain.model.Category
import com.intutec.viveroapp.feature.catalog.domain.model.Product
import com.intutec.viveroapp.feature.catalog.domain.model.ProductImage
import com.intutec.viveroapp.feature.catalog.domain.repository.CatalogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class SupabaseCatalogRepository @Inject constructor(
    private val remote: CatalogRemoteDataSource,
) : CatalogRepository {
    override fun observeCatalog(): Flow<CatalogSnapshot> = flow {
        emit(loadSnapshot())
    }

    override suspend fun getProduct(productId: String): Result<Product> = runCatching {
        val canonicalId = productId.requireUuid("product id")
        loadSnapshot().products.singleOrNull { it.id == canonicalId }
            ?: error("No encontramos este producto activo.")
    }

    override suspend fun findProductByCode(code: String): Result<Product?> = runCatching {
        val normalized = code.trim()
        require(normalized.isNotEmpty()) { "El código no puede estar vacío." }
        val matches = loadSnapshot().products.filter {
            it.barcode.equals(normalized, ignoreCase = true) ||
                it.internalCode.equals(normalized, ignoreCase = true)
        }
        check(matches.size <= 1) { "El código remoto identifica más de un producto." }
        matches.singleOrNull()
    }

    private suspend fun loadSnapshot(): CatalogSnapshot {
        val categories = remote.loadVisibleCategories()
            .map(RemoteCategoryDto::toDomain)
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, Category::name).thenBy(Category::id))
        val categoriesById = categories.associateBy(Category::id)
        check(categoriesById.size == categories.size) { "El catálogo remoto contiene categorías duplicadas." }

        val products = remote.loadActiveProducts()
            .map { it.toDomain(categoriesById) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, Product::commonName).thenBy(Product::id))
        check(products.map(Product::id).distinct().size == products.size) {
            "El catálogo remoto contiene productos duplicados."
        }
        return CatalogSnapshot(categories = categories, products = products)
    }
}

private fun RemoteCategoryDto.toDomain(): Category {
    check(isActive) { "El catálogo remoto devolvió una categoría inactiva." }
    val normalizedName = name.trim()
    check(normalizedName.isNotEmpty()) { "El catálogo remoto contiene una categoría sin nombre." }
    return Category(id = id.requireUuid("category id"), name = normalizedName)
}

private fun RemoteProductDto.toDomain(categoriesById: Map<String, Category>): Product {
    check(isActive) { "El catálogo remoto devolvió un producto inactivo." }
    val canonicalId = id.requireUuid("product id")
    val canonicalCategoryId = categoryId.requireUuid("product category id")
    val category = categoriesById[canonicalCategoryId]
        ?: error("El producto remoto no pertenece a una categoría activa visible.")
    check(internalCode.isNotBlank() && commonName.isNotBlank() && unit.isNotBlank()) {
        "El catálogo remoto contiene un producto incompleto."
    }
    check(priceCents >= 0 && (wholesalePriceCents == null || wholesalePriceCents >= 0)) {
        "El catálogo remoto contiene un precio inválido."
    }
    check(minimumStock.isFinite() && minimumStock >= 0 && minimumStock <= Int.MAX_VALUE) {
        "El catálogo remoto contiene un mínimo de inventario inválido."
    }

    val mappedImages = images
        .map { it.toDomain(canonicalId) }
        .sortedWith(
            compareByDescending<ProductImage> { it.isPrimary }
                .thenBy(ProductImage::sortOrder)
                .thenBy(ProductImage::id),
        )
    check(mappedImages.map(ProductImage::id).distinct().size == mappedImages.size) {
        "El producto remoto contiene imágenes duplicadas."
    }

    return Product(
        id = canonicalId,
        internalCode = internalCode.trim(),
        barcode = barcode?.trim()?.takeIf(String::isNotEmpty),
        commonName = commonName.trim(),
        scientificName = scientificName?.trim()?.takeIf(String::isNotEmpty),
        description = description,
        category = category,
        priceCents = priceCents,
        wholesalePriceCents = wholesalePriceCents,
        unit = unit.trim(),
        stockAvailable = 0,
        minimumStock = minimumStock.toInt(),
        imageKey = "",
        wateringAdvice = wateringAdvice,
        lightType = lightType,
        recommendedClimate = recommendedClimate,
        isActive = true,
        promotion = null,
        createdAt = Instant.parse(createdAt),
        updatedAt = Instant.parse(updatedAt),
        stockKnown = false,
        images = mappedImages,
    )
}

private fun RemoteProductImageDto.toDomain(expectedProductId: String): ProductImage {
    val canonicalId = id.requireUuid("image id")
    val canonicalProductId = productId.requireUuid("image product id")
    check(canonicalProductId == expectedProductId) { "Una imagen remota pertenece a otro producto." }
    val normalizedPath = storagePath.trim()
    check(normalizedPath.isNotEmpty() && sortOrder >= 0) { "El catálogo remoto contiene una imagen inválida." }
    return ProductImage(
        id = canonicalId,
        productId = canonicalProductId,
        storagePath = normalizedPath,
        altText = altText?.trim()?.takeIf(String::isNotEmpty),
        sortOrder = sortOrder,
        isPrimary = isPrimary,
    )
}

private fun String.requireUuid(field: String): String = try {
    UUID.fromString(this).toString()
} catch (_: IllegalArgumentException) {
    throw IllegalStateException("El catálogo remoto contiene un $field inválido.")
}

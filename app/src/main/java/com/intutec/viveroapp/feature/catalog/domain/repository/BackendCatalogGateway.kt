package com.intutec.viveroapp.feature.catalog.domain.repository

data class BackendCategory(val id: Long, val name: String, val description: String)
data class BackendCatalogImage(val id: Long, val path: String, val altText: String)
data class BackendCatalogPromotion(val id: Long, val name: String, val discountPercent: String)
data class BackendCatalogProduct(
    val id: Long, val internalCode: String, val barcode: String?, val commonName: String,
    val scientificName: String?, val description: String, val categoryId: Long,
    val priceCents: Long, val effectivePriceCents: Long, val unit: String,
    val wateringAdvice: String, val lightType: String, val recommendedClimate: String,
    val image: BackendCatalogImage?, val promotion: BackendCatalogPromotion?,
)
data class BackendCatalogPage<T>(val items: List<T>, val nextAfterId: Long?)

// Read-only official API contract. No UUID conversion or fabricated stock.
interface BackendCatalogGateway {
    suspend fun categories(token: String, limit: Int = 50, afterId: Long? = null): BackendCatalogPage<BackendCategory>
    suspend fun products(token: String, limit: Int = 50, afterId: Long? = null, search: String = "", categoryId: Long? = null): BackendCatalogPage<BackendCatalogProduct>
    suspend fun scan(token: String, code: String): BackendCatalogProduct?
}

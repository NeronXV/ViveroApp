package com.intutec.viveroapp.feature.catalog.data.repository

import com.intutec.viveroapp.feature.catalog.domain.model.Category
import com.intutec.viveroapp.feature.catalog.domain.model.Product
import com.intutec.viveroapp.feature.catalog.domain.model.ProductPromotion
import com.intutec.viveroapp.feature.catalog.domain.repository.CatalogRepository
import com.intutec.viveroapp.core.network.SupabaseProvider
import com.intutec.viveroapp.feature.catalog.data.remote.RemoteProductDto
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Instant
import javax.inject.Inject

class FakeCatalogRepository @Inject constructor(
    private val supabaseProvider: SupabaseProvider,
) : CatalogRepository {
    private val products = demoProducts()

    override fun observeProducts(): Flow<List<Product>> = flow {
        delay(450)
        emit(products)
    }

    override suspend fun getProduct(productId: String): Result<Product> = runCatching {
        delay(250)
        products.firstOrNull { it.id == productId } ?: error("No encontramos este producto.")
    }

    override suspend fun findProductByCode(code: String): Result<Product?> = runCatching {
        delay(350)
        val normalized = code.trim()
        products.firstOrNull {
            it.barcode.equals(normalized, ignoreCase = true) ||
                it.internalCode.equals(normalized, ignoreCase = true)
        } ?: findRemoteProduct(normalized)
    }

    private suspend fun findRemoteProduct(code: String): Product? {
        val client = supabaseProvider.client ?: return null
        val columns = Columns.raw(
            "id,internal_code,barcode,common_name,scientific_name,description," +
                "category:categories(id,name),price_cents,wholesale_price_cents,unit," +
                "minimum_stock,watering_advice,light_type,recommended_climate,is_active,created_at,updated_at",
        )
        val byBarcode = client.from("products").select(columns) {
            filter { eq("barcode", code); eq("is_active", true) }
        }.decodeList<RemoteProductDto>().firstOrNull()
        val remote = byBarcode ?: client.from("products").select(columns) {
            filter { eq("internal_code", code); eq("is_active", true) }
        }.decodeList<RemoteProductDto>().firstOrNull()
        return remote?.toDomain()
    }

    private fun demoProducts(): List<Product> {
        val interior = Category("interior", "Interior")
        val aromatic = Category("aromaticas", "Aromáticas")
        val succulent = Category("suculentas", "Suculentas")
        val exterior = Category("exterior", "Exterior")
        val now = Instant.parse("2026-08-08T12:00:00Z")
        return listOf(
            product("monstera", "PL-001", "750100000001", "Monstera deliciosa", "Monstera deliciosa", interior, 58900, 14, "monstera", "Riega cuando la capa superior esté seca.", "Luz indirecta brillante", "Templado y húmedo", now, ProductPromotion("Verde de temporada", 52900)),
            product("lavanda", "PL-014", "750100000014", "Lavanda", "Lavandula angustifolia", aromatic, 18900, 28, "lavender", "Riego moderado; evita encharcar.", "Sol directo", "Seco y templado", now),
            product("echeveria", "PL-022", "750100000022", "Echeveria azul", "Echeveria elegans", succulent, 12900, 41, "echeveria", "Riego profundo y espaciado.", "Luz intensa", "Cálido y seco", now, ProductPromotion("Especial suculentas", 9900)),
            product("romero", "PL-031", "750100000031", "Romero", "Salvia rosmarinus", aromatic, 15900, 0, "lavender", "Riega cuando el sustrato casi seque.", "Sol directo", "Mediterráneo", now),
            product("ficus", "PL-008", "750100000008", "Ficus lyrata", "Ficus lyrata", interior, 74900, 6, "monstera", "Riego semanal sin encharcar.", "Luz indirecta abundante", "Cálido", now),
            product("agave", "PL-045", "750100000045", "Agave azul", "Agave tequilana", exterior, 34900, 19, "echeveria", "Riego escaso.", "Sol directo", "Cálido y semiseco", now),
        )
    }

    private fun product(
        id: String,
        code: String,
        barcode: String,
        commonName: String,
        scientificName: String,
        category: Category,
        priceCents: Long,
        stock: Int,
        imageKey: String,
        watering: String,
        light: String,
        climate: String,
        now: Instant,
        promotion: ProductPromotion? = null,
    ) = Product(
        id = id,
        internalCode = code,
        barcode = barcode,
        commonName = commonName,
        scientificName = scientificName,
        description = "Ejemplar seleccionado en vivero, listo para integrarse a espacios residenciales o proyectos de paisajismo.",
        category = category,
        priceCents = priceCents,
        wholesalePriceCents = (priceCents * 85) / 100,
        unit = "pieza",
        stockAvailable = stock,
        minimumStock = 5,
        imageKey = imageKey,
        wateringAdvice = watering,
        lightType = light,
        recommendedClimate = climate,
        isActive = true,
        promotion = promotion,
        createdAt = now,
        updatedAt = now,
    )
}

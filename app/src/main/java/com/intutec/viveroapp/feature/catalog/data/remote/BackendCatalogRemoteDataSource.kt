package com.intutec.viveroapp.feature.catalog.data.remote

import com.intutec.viveroapp.core.network.BackendApiResponse
import com.intutec.viveroapp.core.network.BackendApiTransport
import com.intutec.viveroapp.feature.catalog.domain.repository.*
import java.math.BigDecimal
import java.util.Collections
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*

class BackendCatalogException(val status: Int, val code: String? = null) : IllegalStateException(
    when (status) {
        401 -> "La sesión no es válida."
        403 -> "No tienes permiso para consultar este producto."
        409 -> "El código coincide con varios productos."
        else -> "No fue posible consultar el catálogo."
    },
)

class BackendCatalogRemoteDataSource @Inject constructor(private val transport: BackendApiTransport) : BackendCatalogGateway {
    override suspend fun categories(token: String, limit: Int, afterId: Long?): BackendCatalogPage<BackendCategory> {
        val query = query(limit, afterId)
        val response = call { transport.get("/api/v1/categories", validToken(token), query) }
        return parse { page(response, limit, afterId) { row ->
            exact(row, "id", "name", "description", "is_active")
            check(bool(row, "is_active"))
            BackendCategory(id(row, "id"), text(row, "name", false), text(row, "description"))
        } }
    }
    override suspend fun products(token: String, limit: Int, afterId: Long?, search: String, categoryId: Long?): BackendCatalogPage<BackendCatalogProduct> {
        val query = query(limit, afterId).toMutableMap()
        val term = search.trim()
        require(term.length <= 80 && clean(term) && wellFormed(term)) { "Búsqueda no válida." }
        if (term.isNotEmpty()) query["search"] = term
        categoryId?.let { requireId(it); query["category_id"] = it.toString() }
        val response = call { transport.get("/api/v1/products", validToken(token), query) }
        return parse { page(response, limit, afterId, ::product).also { page ->
            check(categoryId == null || page.items.all { it.categoryId == categoryId })
        } }
    }
    override suspend fun scan(token: String, code: String): BackendCatalogProduct? {
        val trimmed = code.trim()
        require(clean(code) && wellFormed(code) && trimmed.codePointCount(0, trimmed.length) in 2..128) { "Código no válido." }
        val response = call { transport.post("/api/v1/products/scan", validToken(token), emptyMap(), buildJsonObject { put("code", trimmed) }.toString()) }
        return parse {
            exact(response, "schema_version", "item")
            check(number(response, "schema_version") == 1L)
            if (response["item"] == JsonNull) null else product(response["item"] as JsonObject)
        }
    }
    private fun product(row: JsonObject): BackendCatalogProduct {
        exact(row, "id", "internal_code", "barcode", "common_name", "scientific_name", "description", "category_id", "price_cents", "effective_price_cents", "unit", "is_active", "watering_advice", "light_type", "recommended_climate", "image", "active_promotion")
        check(bool(row, "is_active"))
        val price = cents(row, "price_cents")
        val effective = cents(row, "effective_price_cents"); check(effective <= price)
        val image = if (row["image"] == JsonNull) null else {
            val value = row["image"] as JsonObject; exact(value, "id", "url", "alt_text")
            val imageId = id(value, "id"); val path = text(value, "url", false)
            check(path == "/api/v1/images/$imageId")
            BackendCatalogImage(imageId, path, text(value, "alt_text"))
        }
        val promotion = if (row["active_promotion"] == JsonNull) null else {
            val value = row["active_promotion"] as JsonObject; exact(value, "id", "name", "discount_percent")
            val percent = value["discount_percent"] as JsonPrimitive
            check(!percent.isString && Regex("^[0-9]+(?:\\.[0-9]{1,2})?$").matches(percent.content))
            check(BigDecimal(percent.content) in BigDecimal.ZERO..BigDecimal(100))
            BackendCatalogPromotion(id(value, "id"), text(value, "name", false), percent.content)
        }
        check(if (promotion == null) effective == price else effective < price)
        val unit = text(row, "unit", false); check(unit in setOf("pieza", "maceta", "charola", "bolsa", "kg"))
        return BackendCatalogProduct(id(row, "id"), text(row, "internal_code", false), nullableText(row, "barcode"), text(row, "common_name", false),
            nullableText(row, "scientific_name"), text(row, "description"), id(row, "category_id"), price, effective, unit,
            text(row, "watering_advice"), text(row, "light_type"), text(row, "recommended_climate"), image, promotion)
    }
    private fun <T> page(row: JsonObject, limit: Int, afterId: Long?, decode: (JsonObject) -> T): BackendCatalogPage<T> {
        exact(row, "items", "next_after_id")
        val raw = row["items"] as JsonArray; check(raw.size <= limit)
        val ids = raw.map { id(it as JsonObject, "id") }
        check(ids.zipWithNext().all { (left, right) -> left < right } && ids.all { it > (afterId ?: 0) })
        val next = if (row["next_after_id"] == JsonNull) null else id(row, "next_after_id")
        check(next == null || (raw.size == limit && next == ids.lastOrNull()))
        return BackendCatalogPage(Collections.unmodifiableList(raw.map { decode(it as JsonObject) }), next)
    }
    private fun query(limit: Int, afterId: Long?): Map<String, String> {
        require(limit in 1..100)
        return buildMap { put("limit", limit.toString()); afterId?.let { requireId(it); put("after_id", it.toString()) } }
    }
    private suspend fun call(action: suspend () -> BackendApiResponse): JsonObject {
        val response = try { action() } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { throw BackendCatalogException(0) }
        if (response.status !in 200..299) throw BackendCatalogException(response.status)
        return parse { Json.parseToJsonElement(response.body) as JsonObject }
    }
    private fun validToken(value: String): String { require(Regex("^[A-Za-z0-9_-]{43}$").matches(value)); return value }
    private fun <T> parse(action: () -> T): T = try { action() } catch (_: Exception) { throw BackendCatalogException(502) }
    private fun exact(row: JsonObject, vararg fields: String) { check(row.keys == fields.toSet()) }
    private fun number(row: JsonObject, name: String): Long { val v = row[name] as JsonPrimitive; check(!v.isString); return checkNotNull(v.longOrNull) }
    private fun id(row: JsonObject, name: String): Long = number(row, name).also(::requireId)
    private fun requireId(value: Long) { require(value in 1..4294967295L) }
    private fun cents(row: JsonObject, name: String): Long = number(row, name).also { check(it in 0..9007199254740991L) }
    private fun bool(row: JsonObject, name: String): Boolean { val v = row[name] as JsonPrimitive; check(!v.isString); return checkNotNull(v.booleanOrNull) }
    private fun text(row: JsonObject, name: String, empty: Boolean = true): String {
        val v = row[name] as JsonPrimitive
        check(v.isString && (empty || v.content.isNotBlank()) && wellFormed(v.content))
        return v.content
    }
    private fun nullableText(row: JsonObject, name: String): String? = if (row[name] == JsonNull) null else text(row, name)
    private fun clean(value: String) = value.none { it.code < 32 || it.code in 127..159 }
    private fun wellFormed(value: String): Boolean {
        var index = 0
        while (index < value.length) {
            val char = value[index++]
            if (char.isHighSurrogate()) { if (index >= value.length || !value[index++].isLowSurrogate()) return false }
            else if (char.isLowSurrogate()) return false
        }
        return true
    }
}

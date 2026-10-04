package com.intutec.viveroapp.feature.mysales.data.remote

import com.intutec.viveroapp.core.network.BackendApiTransport
import com.intutec.viveroapp.feature.cart.domain.repository.*
import com.intutec.viveroapp.feature.mysales.domain.repository.*
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*

class BackendHistoryRemoteDataSource @Inject constructor(private val transport: BackendApiTransport) : BackendHistoryGateway {
    private val statuses = setOf("DRAFT", "SENT_TO_CASHIER", "PAYMENT_PENDING", "PAID", "CANCELLED", "DELIVERED")
    private val methods = setOf("CASH", "CARD", "TRANSFER")
    override suspend fun list(token: String, identity: BackendSaleIdentity, kind: BackendHistoryKind, before: Long?): BackendHistoryPage {
        require(kind != BackendHistoryKind.QUEUE)
        val row = get(token, path(kind), buildMap { put("limit", "20"); before?.let { require(it in 1..MAX_BACKEND_ID); put("before_id", it.toString()) } })
        return checked {
            exact(row, "schema_version", "items", "next_before_id"); check(n(row, "schema_version") == 1L)
            val items = row["items"]!!.jsonArray.map { raw ->
                val r = raw.jsonObject
                if (kind == BackendHistoryKind.SALES) {
                    val sale = sale(r, identity, own = true)
                    BackendHistoryEntry(sale.id, sale.id, sale.folio, sale.totalCents, sale.status, date(r, "created_at"))
                } else {
                    exact(r, "id", "sale_id", "folio", "method", "amount_due_cents", "created_at")
                    BackendHistoryEntry(id(r, "id"), id(r, "sale_id"), text(r, "folio"), n(r, "amount_due_cents", 1), method(r), date(r, "created_at"))
                }
            }
            check(items.size <= 20 && items.zipWithNext().all { (a,b) -> a.id > b.id } && items.all { it.id < (before ?: Long.MAX_VALUE) })
            val next = if (row["next_before_id"] == JsonNull) null else id(row, "next_before_id")
            check(next == null || (items.size == 20 && items.last().id == next))
            BackendHistoryPage(items.toList(), next)
        }
    }
    override suspend fun detail(token: String, identity: BackendSaleIdentity, kind: BackendHistoryKind, id: Long): BackendSaleDocument {
        require(id in 1..MAX_BACKEND_ID)
        val row = get(token, path(kind) + "/$id")
        return checked {
            exact(row, *(when (kind) {
                BackendHistoryKind.SALES -> arrayOf("schema_version", "sale", "items", "history")
                BackendHistoryKind.PAYMENTS -> arrayOf("schema_version", "sale", "items", "payment", "branch", "refund")
                BackendHistoryKind.QUEUE -> arrayOf("schema_version", "sale", "items")
            }))
            check(n(row, "schema_version") == 1L)
            val rawSale = row["sale"]!!.jsonObject
            val sale = sale(rawSale, identity, own = kind == BackendHistoryKind.SALES)
            check(kind == BackendHistoryKind.PAYMENTS || sale.id == id)
            val lines = row["items"]!!.jsonArray.map { raw -> line(raw.jsonObject, kind == BackendHistoryKind.SALES) }
            check(lines.isNotEmpty() && lines.map { it.id }.distinct().size == lines.size && lines.zipWithNext().all { (a,b) -> a.id < b.id })
            check(lines.fold(BigInteger.ZERO) { sum, l -> sum + BigInteger.valueOf(l.totalCents) } == BigInteger.valueOf(if (rawSale["web_order_id"] != null && rawSale["web_order_id"] != JsonNull) sale.totalCents else sale.subtotalCents))
            if (kind == BackendHistoryKind.SALES) {
                val events = row["history"]!!.jsonArray.map { raw ->
                    val r = raw.jsonObject; exact(r, "previous_status", "new_status", "observation", "created_at")
                    val previous = nullableText(r, "previous_status"); check(previous == null || previous in statuses)
                    BackendHistoricalEvent(previous, text(r, "new_status").also { check(it in statuses) }, text(r, "observation"), date(r, "created_at"))
                }
                BackendSaleDocument(sale, lines, events)
            } else if (kind == BackendHistoryKind.QUEUE) BackendSaleDocument(sale, lines)
            else {
                val p = row["payment"]!!.jsonObject
                exact(p, "id", "sale_id", "cashier_id", "branch_id", "method", "amount_due_cents", "amount_received_cents", "change_cents", "reference", "created_at")
                check(id(p, "id") == id && id(p, "sale_id") == sale.id && id(p, "cashier_id") == identity.userId && id(p, "branch_id") == identity.branchId)
                check(sale.status in setOf("PAID", "DELIVERED") && n(p, "amount_due_cents", 1) == sale.totalCents)
                val received = n(p, "amount_received_cents", 1); val change = n(p, "change_cents"); val method = method(p); val reference = nullableText(p, "reference")
                check(received >= sale.totalCents && received - sale.totalCents == change)
                check(if (method == "CASH") reference == null else change == 0L && (method != "TRANSFER" || reference != null))
                val b = row["branch"]!!.jsonObject; exact(b, "id", "name"); check(id(b, "id") == identity.branchId)
                val refund = if (row["refund"] == JsonNull) null else row["refund"]!!.jsonObject.let { r ->
                    exact(r, "id", "amount_cents", "method"); check(n(r, "amount_cents", 1) == sale.totalCents)
                    BackendHistoricalRefund(id(r, "id"), n(r, "amount_cents", 1), method(r))
                }
                BackendSaleDocument(sale, lines, payment = BackendHistoricalPayment(id, method, received, change, reference, date(p, "created_at")), refund = refund, branchName = text(b, "name"))
            }
        }
    }
    private fun path(kind: BackendHistoryKind) = when (kind) {
        BackendHistoryKind.SALES -> "/api/v1/sales"
        BackendHistoryKind.PAYMENTS -> "/api/v1/cashier/receipts"
        BackendHistoryKind.QUEUE -> "/api/v1/cashier/sales"
    }
    private fun sale(r: JsonObject, who: BackendSaleIdentity, own: Boolean): BackendSaleSnapshot {
        exact(r, *(if (own) arrayOf("id", "folio", "branch_id", "created_by", "status", "subtotal_cents", "discount_cents", "total_cents", "created_at")
            else arrayOf("id", "folio", "branch_id", "web_order_id", "status", "subtotal_cents", "discount_cents", "total_cents")))
        check(id(r, "branch_id") == who.branchId)
        if (own) { check(id(r, "created_by") == who.userId); date(r, "created_at") }
        else if (r["web_order_id"] != JsonNull) id(r, "web_order_id")
        val status = text(r, "status").also { check(it in statuses) }
        val subtotal = n(r, "subtotal_cents", 1); val discount = n(r, "discount_cents"); val total = n(r, "total_cents", 1)
        check(subtotal - discount == total)
        return BackendSaleSnapshot(id(r, "id"), text(r, "folio"), who.branchId, status, subtotal, discount, total)
    }
    private fun line(r: JsonObject, own: Boolean): BackendHistoricalLine {
        val keys = arrayOf("id", "product_id", "product_name", "internal_code", "quantity", "list_price_cents", "unit_price_cents", "line_total_cents")
        exact(r, *(if (own) keys + arrayOf("promotion_id", "promotion_name") else keys))
        val quantity = r["quantity"]!!.jsonPrimitive.content
        check(Regex("^[0-9]{1,11}(?:\\.[0-9]{1,3})?$").matches(quantity))
        val q = BigDecimal(quantity); check(q > BigDecimal.ZERO && q <= BigDecimal("99999999999.999"))
        val unit = n(r, "unit_price_cents"); val total = n(r, "line_total_cents")
        check(BigDecimal.valueOf(unit).multiply(q).compareTo(BigDecimal.valueOf(total)) == 0)
        val list = if (r["list_price_cents"] == JsonNull) null else n(r, "list_price_cents").also { check(unit <= it) }
        var promotion: String? = null
        if (own) {
            if (r["promotion_id"] == JsonNull) check(r["promotion_name"] == JsonNull)
            else { id(r, "promotion_id"); promotion = text(r, "promotion_name") }
        }
        return BackendHistoricalLine(id(r, "id"), id(r, "product_id"), text(r, "product_name"), nullableText(r, "internal_code"), quantity, list, unit, total, promotion)
    }
    private suspend fun get(token: String, path: String, query: Map<String, String> = emptyMap()): JsonObject = try {
        val response = transport.get(path, token, query); check(response.status in 200..299)
        Json.parseToJsonElement(response.body).jsonObject
    } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { error("No se pudo consultar el historial autorizado.") }
    private fun <T> checked(action: () -> T): T = try { action() } catch (_: Exception) { error("Comprobante incompatible; no se modifica ningún registro.") }
    private fun exact(row: JsonObject, vararg keys: String) { check(row.keys == keys.toSet()) }
    private fun n(r: JsonObject, key: String, min: Long = 0): Long { val v = r[key]!!.jsonPrimitive; check(!v.isString); return checkNotNull(v.longOrNull).also { check(it in min..MAX_BACKEND_CENTS) } }
    private fun id(r: JsonObject, key: String) = n(r, key, 1).also { check(it <= MAX_BACKEND_ID) }
    private fun text(r: JsonObject, key: String) = r[key]!!.jsonPrimitive.also { check(it.isString && it.content.isNotBlank()) }.content
    private fun nullableText(r: JsonObject, key: String) = if (r[key] == JsonNull) null else text(r, key)
    private fun date(r: JsonObject, key: String) = text(r, key).also { Instant.parse(it) }
    private fun method(r: JsonObject) = text(r, "method").also { check(it in methods) }
}

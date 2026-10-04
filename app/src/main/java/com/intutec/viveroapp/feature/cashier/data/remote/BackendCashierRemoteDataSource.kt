package com.intutec.viveroapp.feature.cashier.data.remote

import com.intutec.viveroapp.core.network.BackendApiTransport
import com.intutec.viveroapp.feature.cart.domain.repository.*
import com.intutec.viveroapp.feature.cashier.domain.repository.*
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*

class BackendCashierRemoteDataSource @Inject constructor(private val transport: BackendApiTransport) : BackendCashierGateway {
    override suspend fun list(token: String, before: Long?): BackendCashierPage {
        val row = request { transport.get("/api/v1/cashier/sales", token, buildMap {
            put("limit", "20"); before?.let { require(it in 1..MAX_BACKEND_ID); put("before_id", it.toString()) }
        }) }
        return checked {
            exact(row, "schema_version", "items", "next_before_id"); check(n(row, "schema_version") == 1L)
            val items = row["items"]!!.jsonArray.map { raw ->
                val s = raw.jsonObject; exact(s, "id", "folio", "created_by", "status", "total_cents", "created_at")
                check(s["status"] == JsonPrimitive("SENT_TO_CASHIER")); id(s, "created_by")
                BackendCashierSale(id(s, "id"), text(s, "folio"), n(s, "total_cents", 1))
            }
            check(items.size <= 20 && items.zipWithNext().all { (a,b) -> a.id > b.id } && items.all { it.id < (before ?: Long.MAX_VALUE) })
            val next = if (row["next_before_id"] == JsonNull) null else id(row, "next_before_id")
            check(next == null || (items.size == 20 && next == items.last().id))
            BackendCashierPage(items, next)
        }
    }
    override suspend fun claim(token: String, identity: BackendSaleIdentity, sale: Long): BackendCashierClaim {
        val row = post(token, identity, sale, "claim", "{\"claim_token\":null}")
        return checked {
            exact(row, "schema_version", "sale_id", "branch_id", "cashier_id", "claim_token", "created_at", "expires_at", "server_time", "renewed")
            check(n(row, "schema_version") == 1L && id(row, "sale_id") == sale && id(row, "branch_id") == identity.branchId && id(row, "cashier_id") == identity.userId)
            val key = text(row, "claim_token"); check(Regex("^[a-f0-9]{64}$").matches(key))
            check(java.time.Instant.parse(text(row, "expires_at")) > java.time.Instant.parse(text(row, "server_time")))
            BackendCashierClaim(sale, identity, key)
        }
    }
    override suspend fun pay(token: String, identity: BackendSaleIdentity, sale: Long, key: String, body: String) =
        receipt(post(token, identity, sale, "payments", body, key), identity, sale, body)
    override suspend fun recover(token: String, identity: BackendSaleIdentity, sale: Long, key: String, body: String) =
        receipt(post(token, identity, sale, "payment-result", "{}", key), identity, sale, body)
    override suspend fun retire(token: String, identity: BackendSaleIdentity, sale: Long, key: String, body: String): BackendPaymentRetirement {
        val row = post(token, identity, sale, "payment-retire", "{}", key)
        return checked {
            exact(row, "schema_version", "status", "receipt"); check(n(row, "schema_version") == 1L)
            when (text(row, "status")) {
                "RETIRED" -> { check(row["receipt"] == JsonNull); BackendPaymentRetirement.Retired }
                "COMMITTED" -> BackendPaymentRetirement.Committed(receipt(row["receipt"]!!.jsonObject, identity, sale, body))
                else -> error("Unknown retirement result")
            }
        }
    }
    private suspend fun post(token: String, who: BackendSaleIdentity, sale: Long, action: String, body: String, key: String? = null): JsonObject {
        require(sale in 1..MAX_BACKEND_ID && (key == null || Regex("^[a-f0-9]{64}$").matches(key)))
        return request { transport.post("/api/v1/cashier/sales/$sale/$action", token, buildMap {
            put("X-Expected-Actor-Id", who.userId.toString()); put("X-Expected-Branch-Id", who.branchId.toString())
            key?.let { put("Idempotency-Key", it) }
        }, body) }
    }
    private fun receipt(row: JsonObject, who: BackendSaleIdentity, saleId: Long, original: String): BackendPaymentReceipt = checked {
        exact(row, "schema_version", "idempotent_replay", "sale", "payment"); check(n(row, "schema_version") == 1L)
        check(!row["idempotent_replay"]!!.jsonPrimitive.isString && row["idempotent_replay"]!!.jsonPrimitive.booleanOrNull != null)
        val s = row["sale"]!!.jsonObject; val p = row["payment"]!!.jsonObject; val input = Json.parseToJsonElement(original).jsonObject
        exact(s, "id", "folio", "branch_id", "web_order_id", "status", "subtotal_cents", "discount_cents", "total_cents")
        exact(p, "id", "sale_id", "branch_id", "cashier_id", "claim_id", "method", "amount_due_cents", "requested_amount_received_cents", "amount_received_cents", "change_cents", "reference", "created_at")
        check(id(s, "id") == saleId && id(p, "sale_id") == saleId && id(s, "branch_id") == who.branchId && id(p, "branch_id") == who.branchId && id(p, "cashier_id") == who.userId)
        id(p, "claim_id"); java.time.Instant.parse(text(p, "created_at"))
        check(s["status"]!!.jsonPrimitive.content in setOf("PAID", "DELIVERED") && p["method"] == input["method"] && p["requested_amount_received_cents"] == input["amount_received_cents"] && p["reference"] == input["reference"])
        val due = n(p, "amount_due_cents", 1); val received = n(p, "amount_received_cents", 1); val change = n(p, "change_cents")
        check(n(s, "total_cents", 1) == due && n(s, "subtotal_cents", 1) - n(s, "discount_cents") == due && received >= due && received - due == change)
        BackendPaymentReceipt(id(p, "id"), saleId, text(s, "folio"), due, received, change)
    }
    private suspend fun request(action: suspend () -> com.intutec.viveroapp.core.network.BackendApiResponse): JsonObject = try {
        val r = action()
        if (r.status == 404 && Json.parseToJsonElement(r.body).jsonObject == buildJsonObject { put("error", "PAYMENT_NOT_FOUND") }) throw BackendPaymentNotFound()
        check(r.status in 200..299); Json.parseToJsonElement(r.body).jsonObject
    } catch (cancelled: CancellationException) { throw cancelled } catch (missing: BackendPaymentNotFound) { throw missing }
    catch (_: Exception) { error("No se confirmó la operación de Caja. Conserva el intento y consulta su resultado.") }
    private fun <T> checked(action: () -> T): T = try { action() } catch (_: Exception) { error("Respuesta de Caja incompatible; el intento se conserva.") }
    private fun exact(row: JsonObject, vararg keys: String) { check(row.keys == keys.toSet()) }
    private fun n(row: JsonObject, key: String, min: Long = 0): Long { val v = row[key]!!.jsonPrimitive; check(!v.isString); return checkNotNull(v.longOrNull).also { check(it in min..MAX_BACKEND_CENTS) } }
    private fun id(row: JsonObject, key: String) = n(row, key, 1).also { check(it <= MAX_BACKEND_ID) }
    private fun text(row: JsonObject, key: String): String { val v = row[key]!!.jsonPrimitive; check(v.isString && v.content.isNotBlank()); return v.content }
}

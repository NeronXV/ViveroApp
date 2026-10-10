package com.intutec.viveroapp.feature.cart.data.remote

import com.intutec.viveroapp.core.network.BackendApiTransport
import com.intutec.viveroapp.feature.cart.domain.repository.BackendSaleAttempt
import com.intutec.viveroapp.feature.cart.domain.repository.BackendSaleException
import com.intutec.viveroapp.feature.cart.domain.repository.BackendSaleGateway
import com.intutec.viveroapp.feature.cart.domain.repository.BackendSaleIdentity
import com.intutec.viveroapp.feature.cart.domain.repository.BackendSaleLine
import com.intutec.viveroapp.feature.cart.domain.repository.BackendSaleQuote
import com.intutec.viveroapp.feature.cart.domain.repository.BackendSaleQuotedLine
import com.intutec.viveroapp.feature.cart.domain.repository.BackendSaleReceipt
import com.intutec.viveroapp.feature.cart.domain.repository.BackendSaleRetirement
import com.intutec.viveroapp.feature.cart.domain.repository.MAX_BACKEND_CENTS
import com.intutec.viveroapp.feature.cart.domain.repository.MAX_BACKEND_ID
import com.intutec.viveroapp.feature.cart.domain.repository.isBackendSaleFolio
import java.math.BigInteger
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*

class BackendSaleRemoteDataSource @Inject constructor(private val transport: BackendApiTransport) : BackendSaleGateway {
    private fun lines(items: List<BackendSaleLine>): JsonArray {
        require(items.size in 1..25 && items.map { it.productId }.distinct().size == items.size)
        return JsonArray(items.sortedBy { it.productId }.map { buildJsonObject {
            put("product_id", it.productId); put("quantity", it.quantity)
        } })
    }
    private suspend fun call(token: String, identity: BackendSaleIdentity, path: String, body: JsonObject, key: String? = null): JsonObject {
        require(Regex("^[A-Za-z0-9_-]{43}$").matches(token))
        val writing = path in setOf("/api/v1/sales", "/api/v1/sales/retire")
        val response = try {
            transport.post(path, token, buildMap {
                put("X-Expected-Actor-Id", identity.userId.toString())
                put("X-Expected-Branch-Id", identity.branchId.toString())
                if (key != null) put("Idempotency-Key", key)
            }, body.toString())
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { throw BackendSaleException(0, "CONNECTION_FAILED", writing) }
        val result = try { Json.parseToJsonElement(response.body) as JsonObject }
        catch (_: Exception) { throw BackendSaleException(502, "INCOMPATIBLE_RESPONSE", writing) }
        if (response.status !in 200..299) {
            val code = (result["error"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                ?.takeIf { Regex("^[A-Z_]{3,64}$").matches(it) } ?: "REQUEST_FAILED"
            throw BackendSaleException(response.status, code, writing && response.status >= 500)
        }
        return result
    }
    override suspend fun quote(token: String, identity: BackendSaleIdentity, items: List<BackendSaleLine>): BackendSaleQuote {
        val result = call(token, identity, "/api/v1/sales/quote", buildJsonObject { put("items", lines(items)) })
        return checked(false) {
            exact(result, "schema_version", "branch_id", "subtotal_cents", "discount_cents", "total_cents", "items")
            check(number(result, "schema_version") == 1L && number(result, "branch_id") == identity.branchId)
            val quoted = (result["items"] as JsonArray).map { value ->
                val r = value as JsonObject
                exact(r, "product_id", "product_name", "internal_code", "quantity", "list_price_cents", "unit_price_cents", "promotion_id", "promotion_name", "line_total_cents")
                val productId = number(r, "product_id", 1, MAX_BACKEND_ID); val quantity = number(r, "quantity", 1, 100000).toInt()
                val list = number(r, "list_price_cents"); val unit = number(r, "unit_price_cents"); val total = number(r, "line_total_cents")
                check(unit <= list && BigInteger.valueOf(unit) * BigInteger.valueOf(quantity.toLong()) == BigInteger.valueOf(total))
                if (r["promotion_id"] == JsonNull) check(r["promotion_name"] == JsonNull)
                else { number(r, "promotion_id", 1, MAX_BACKEND_ID); text(r, "promotion_name"); check(unit < list) }
                BackendSaleQuotedLine(productId, quantity, text(r, "product_name"), text(r, "internal_code"), list, unit, total)
            }
            check(quoted.map { BackendSaleLine(it.productId, it.quantity) }.sortedBy { it.productId } == items.sortedBy { it.productId })
            val subtotal = number(result, "subtotal_cents"); val discount = number(result, "discount_cents"); val total = number(result, "total_cents", 1)
            check(BigInteger.valueOf(subtotal) - BigInteger.valueOf(discount) == BigInteger.valueOf(total))
            check(quoted.fold(BigInteger.ZERO) { sum, line -> sum + BigInteger.valueOf(line.lineTotalCents) } == BigInteger.valueOf(total))
            check(quoted.fold(BigInteger.ZERO) { sum, line -> sum + BigInteger.valueOf(line.listPriceCents) * BigInteger.valueOf(line.quantity.toLong()) } == BigInteger.valueOf(subtotal))
            BackendSaleQuote(identity.branchId, subtotal, discount, total, quoted)
        }
    }
    override suspend fun submit(token: String, attempt: BackendSaleAttempt): BackendSaleReceipt {
        val result = call(token, attempt.identity, "/api/v1/sales", buildJsonObject {
            put("items", lines(attempt.items)); put("expected_total_cents", attempt.expectedTotalCents)
        }, attempt.key)
        return checked(true) { receipt(result, attempt) }
    }
    override suspend fun recover(token: String, attempt: BackendSaleAttempt): BackendSaleReceipt =
        checked(false) { receipt(call(token, attempt.identity, "/api/v1/sales/recover", buildJsonObject {}, attempt.key), attempt) }

    override suspend fun retire(token: String, attempt: BackendSaleAttempt): BackendSaleRetirement = checked(true) {
        val result = call(token, attempt.identity, "/api/v1/sales/retire", buildJsonObject {}, attempt.key)
        exact(result, "schema_version", "status", "sale")
        check(number(result, "schema_version") == 1L)
        when (text(result, "status")) {
            "RETIRED" -> { check(result["sale"] == JsonNull); BackendSaleRetirement.Retired }
            "COMMITTED" -> BackendSaleRetirement.Committed(receipt(result["sale"] as JsonObject, attempt))
            else -> error("Incompatible retirement status")
        }
    }

    private suspend fun <T> checked(writing: Boolean, action: suspend () -> T): T = try { action() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: BackendSaleException) { throw error }
        catch (_: Exception) { throw BackendSaleException(502, "INCOMPATIBLE_RESPONSE", writing) }
    private fun receipt(r: JsonObject, attempt: BackendSaleAttempt): BackendSaleReceipt {
        exact(r, "schema_version", "id", "folio", "branch_id", "created_by", "status", "subtotal_cents", "discount_cents", "total_cents", "created_at", "idempotent_replay")
        check(number(r, "schema_version") == 1L && number(r, "created_by") == attempt.identity.userId && number(r, "branch_id") == attempt.identity.branchId)
        check(number(r, "discount_cents") == 0L && number(r, "subtotal_cents", 1) == attempt.expectedTotalCents && number(r, "total_cents", 1) == attempt.expectedTotalCents)
        val status = text(r, "status"); val folio = text(r, "folio"); val date = text(r, "created_at")
        check(status in setOf("SENT_TO_CASHIER", "PAYMENT_PENDING", "PAID", "CANCELLED", "DELIVERED"))
        check(isBackendSaleFolio(folio) && Regex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z$").matches(date))
        check(Instant.parse(date).toString() == date)
        val replay = r["idempotent_replay"] as JsonPrimitive
        check(!replay.isString && replay.booleanOrNull != null)
        return BackendSaleReceipt(number(r, "id", 1, MAX_BACKEND_ID), folio, attempt.identity, status, attempt.expectedTotalCents, date, replay.boolean)
    }
    private fun exact(r: JsonObject, vararg keys: String) { check(r.keys == keys.toSet()) }
    private fun number(r: JsonObject, key: String, min: Long = 0, max: Long = MAX_BACKEND_CENTS): Long {
        val value = r[key] as JsonPrimitive
        check(!value.isString)
        return checkNotNull(value.longOrNull).also { check(it in min..max) }
    }
    private fun text(r: JsonObject, key: String): String {
        val value = r[key] as JsonPrimitive
        check(value.isString && value.content.isNotBlank() && value.content.none { it.code < 32 || it.code in 127..159 })
        return value.content
    }
}

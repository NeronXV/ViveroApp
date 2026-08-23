package com.intutec.viveroapp.feature.cashier.data.remote

import com.intutec.viveroapp.core.network.SupabaseProvider
import com.intutec.viveroapp.feature.cashier.domain.model.CashierPaymentException
import com.intutec.viveroapp.feature.cashier.domain.model.CashierPaymentFailureCode
import com.intutec.viveroapp.feature.cashier.domain.model.CashierPaymentInput
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject

class SupabaseCashierPaymentRemoteDataSource @Inject constructor(
    private val supabaseProvider: SupabaseProvider,
) : CashierPaymentRemoteDataSource {
    override suspend fun claim(saleId: String, claimToken: String?): RemoteCashierClaimDto =
        call("claim_sale_for_payment", JsonObject(mapOf(
            "p_sale_id" to JsonPrimitive(saleId),
            "p_claim_token" to (claimToken?.let(::JsonPrimitive) ?: JsonNull),
        )))

    override suspend fun release(saleId: String, claimToken: String): RemoteCashierClaimReleaseDto =
        call("release_sale_payment_claim", JsonObject(mapOf(
            "p_sale_id" to JsonPrimitive(saleId),
            "p_claim_token" to JsonPrimitive(claimToken),
        )))

    override suspend fun confirm(
        saleId: String,
        claimToken: String,
        idempotencyKey: String,
        input: CashierPaymentInput,
    ): RemoteCashierPaymentResultDto = call(
        "confirm_sale_payment",
        JsonObject(mapOf(
            "p_sale_id" to JsonPrimitive(saleId),
            "p_claim_token" to JsonPrimitive(claimToken),
            "p_idempotency_key" to JsonPrimitive(idempotencyKey),
            "p_method" to JsonPrimitive(input.method.name),
            "p_amount_received_cents" to (input.amountReceivedCents?.let(::JsonPrimitive) ?: JsonNull),
            "p_reference" to (input.reference?.let(::JsonPrimitive) ?: JsonNull),
        )),
    )

    private suspend inline fun <reified T> call(function: String, parameters: JsonObject): T {
        val client = checkNotNull(supabaseProvider.client) { "Supabase no está configurado para Caja." }
        return try {
            val response = client.postgrest.rpc(function = function, parameters = parameters)
            paymentJson.decodeFromString(response.data)
        } catch (error: PostgrestRestException) {
            throw error.toPaymentException()
        } catch (error: CashierPaymentException) {
            throw error
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            throw CashierPaymentException(
                CashierPaymentFailureCode.TEMPORARY,
                responseMayBeCommitted = function == "confirm_sale_payment",
                message = "No pudimos comunicarnos con Caja. Comprueba tu conexión.",
            )
        }
    }

    private companion object {
        val paymentJson = Json { ignoreUnknownKeys = true }
    }
}

private fun PostgrestRestException.toPaymentException(): CashierPaymentException {
    val stableCode = CashierPaymentFailureCode.entries.firstOrNull { code ->
        message?.contains(code.name, ignoreCase = false) == true
    }
    if (stableCode != null) {
        return CashierPaymentException(stableCode, false, stableCode.userMessage())
    }
    val temporary = statusCode == 408 || statusCode == 429 || statusCode >= 500
    return CashierPaymentException(
        if (temporary) CashierPaymentFailureCode.TEMPORARY else CashierPaymentFailureCode.RESPONSE_INVALID,
        responseMayBeCommitted = temporary,
        message = if (temporary) {
            "No pudimos confirmar la respuesta de Caja."
        } else {
            "Caja rechazó la operación. Actualiza la comanda e intenta nuevamente."
        },
    )
}

internal fun CashierPaymentFailureCode.userMessage(): String = when (this) {
    CashierPaymentFailureCode.CASHIER_UNAUTHORIZED -> "Tu sesión ya no puede operar Caja en esta sucursal."
    CashierPaymentFailureCode.SALE_UNAVAILABLE,
    CashierPaymentFailureCode.CLAIM_UNAVAILABLE -> "Otra caja tomó esta comanda o ya no está disponible."
    CashierPaymentFailureCode.SALE_STATUS_INVALID,
    CashierPaymentFailureCode.SALE_ALREADY_PAID -> "La comanda ya fue cobrada o cambió de estado."
    CashierPaymentFailureCode.CLAIM_EXPIRED,
    CashierPaymentFailureCode.CLAIM_NOT_OWNED,
    CashierPaymentFailureCode.CLAIM_REQUIRED -> "La reserva de cobro venció o ya no te pertenece."
    CashierPaymentFailureCode.CASH_AMOUNT_INSUFFICIENT -> "El importe recibido es menor al total."
    CashierPaymentFailureCode.TRANSFER_REFERENCE_REQUIRED -> "Ingresa la referencia de la transferencia."
    CashierPaymentFailureCode.PAYMENT_DATA_INVALID,
    CashierPaymentFailureCode.PAYMENT_METHOD_INVALID -> "Revisa los datos del cobro antes de continuar."
    CashierPaymentFailureCode.IDEMPOTENCY_CONFLICT -> "El intento guardado no coincide con la operación enviada."
    CashierPaymentFailureCode.IDEMPOTENCY_KEY_INVALID -> "El identificador seguro del intento no es válido."
    CashierPaymentFailureCode.SALE_TOTAL_INVALID -> "El total de la comanda no es válido para cobrar."
    CashierPaymentFailureCode.RESPONSE_UNKNOWN,
    CashierPaymentFailureCode.RESPONSE_INVALID,
    CashierPaymentFailureCode.TEMPORARY -> "No pudimos confirmar la respuesta de Caja."
}

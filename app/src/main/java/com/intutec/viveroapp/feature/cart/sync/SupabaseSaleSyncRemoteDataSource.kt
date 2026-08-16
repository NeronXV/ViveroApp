package com.intutec.viveroapp.feature.cart.sync

import com.intutec.viveroapp.core.network.SupabaseProvider
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

class SupabaseSaleSyncRemoteDataSource @Inject constructor(
    private val supabaseProvider: SupabaseProvider,
) : SaleSyncRemoteDataSource {
    override suspend fun submitSale(request: SaleSyncRequest): SaleSyncResponse {
        val client = checkNotNull(supabaseProvider.client) { "Supabase no está configurado." }
        return try {
            val result = client.postgrest.rpc(
                function = "submit_sale_to_cashier",
                parameters = request.toRpcParameters(),
            )
            decodeSaleSyncResponse(result.data)
        } catch (error: PostgrestRestException) {
            throw classifyPostgrestError(error)
        } catch (error: SaleSyncRemoteException) {
            throw error
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            throw SaleSyncRemoteException(
                SaleSyncFailureType.TEMPORARY,
                "No se pudo confirmar la comanda por un problema temporal.",
            )
        }
    }

    private fun classifyPostgrestError(error: PostgrestRestException): SaleSyncRemoteException {
        val status = error.statusCode
        val isTemporary = status >= 500 || status == 408 || status == 429
        val message = when {
            isTemporary -> "El servidor no pudo confirmar la comanda temporalmente."
            error.code == "22023" -> "La comanda contiene productos o cantidades no válidos."
            error.code == "42501" -> "La cuenta no tiene permiso para enviar esta comanda."
            else -> "La comanda fue rechazada por el servidor."
        }
        return SaleSyncRemoteException(
            if (isTemporary) SaleSyncFailureType.TEMPORARY else SaleSyncFailureType.PERMANENT,
            message,
        )
    }
}

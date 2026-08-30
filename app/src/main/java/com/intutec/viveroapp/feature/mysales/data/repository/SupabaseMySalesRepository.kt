package com.intutec.viveroapp.feature.mysales.data.repository

import com.intutec.viveroapp.feature.mysales.data.remote.MySalesRemoteDataSource
import com.intutec.viveroapp.feature.mysales.data.remote.RemoteMySaleDto
import com.intutec.viveroapp.feature.mysales.data.remote.RemoteMySalesResponseDto
import com.intutec.viveroapp.feature.mysales.domain.model.MySale
import com.intutec.viveroapp.feature.mysales.domain.model.MySaleStatus
import com.intutec.viveroapp.feature.mysales.domain.model.MySalesPage
import com.intutec.viveroapp.feature.mysales.domain.repository.MySalesRepository
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class SupabaseMySalesRepository @Inject constructor(
    private val remote: MySalesRemoteDataSource,
) : MySalesRepository {

    override suspend fun getMyRecentSales(
        limit: Int,
        afterCreatedAt: Instant?,
        afterId: String?,
    ): Result<MySalesPage> {
        try {
            require(limit in 1..50) { "El límite debe estar entre 1 y 50." }
            require((afterCreatedAt == null) == (afterId == null)) { "El cursor debe ser completo o nulo." }
            if (afterId != null) requireCanonicalUuid(afterId, "ID de cursor inválido.")

            val jsonResponse = remote.getMyRecentSales(limit, afterCreatedAt, afterId)
            val responseDto = json.decodeFromString<RemoteMySalesResponseDto>(jsonResponse)
            
            if (responseDto.schemaVersion != 1) {
                throw IllegalStateException("Versión de contrato de ventas no compatible.")
            }

            return Result.success(responseDto.toDomain(requestedLimit = limit))
        } catch (e: CancellationException) {
            throw e
        } catch (e: PostgrestRestException) {
            return Result.failure(Exception(classifyError(e)))
        } catch (e: Throwable) {
            return Result.failure(e)
        }
    }

    private fun RemoteMySalesResponseDto.toDomain(requestedLimit: Int): MySalesPage {
        val serverTimeInstant = parseInstant(serverTime, "Hora del servidor inválida.")

        if (page.limit !in 1..50 || page.limit != requestedLimit) {
            throw IllegalStateException("Límite de página remoto incompatible con la solicitud.")
        }
        if (items.size > requestedLimit) {
            throw IllegalStateException("La respuesta excede el límite de ventas solicitado.")
        }

        if (page.hasMore && page.nextCursor == null) {
            throw IllegalStateException("Cursor ausente para página incompleta.")
        }
        if (!page.hasMore && page.nextCursor != null) {
            throw IllegalStateException("Cursor presente para página final.")
        }

        val domainItems = items.map { it.toDomain(serverTimeInstant) }
        
        if (domainItems.map { it.id }.distinct().size != domainItems.size) {
            throw IllegalStateException("La respuesta contiene IDs de venta duplicados.")
        }
        domainItems.zipWithNext().forEach { (previous, current) ->
            val isOutOfOrder = previous.createdAt.isBefore(current.createdAt) ||
                (previous.createdAt == current.createdAt && previous.id.lowercase() <= current.id.lowercase())
            if (isOutOfOrder) {
                throw IllegalStateException("Las ventas no respetan el orden estable del contrato.")
            }
        }

        var cursorCreatedAt: Instant? = null
        var cursorId: String? = null
        if (page.hasMore && page.nextCursor != null) {
            val last = domainItems.lastOrNull() ?: throw IllegalStateException("Página vacía con hasMore=true.")
            val cursorTime = parseInstant(page.nextCursor.createdAt, "Timestamp de cursor inválido.")
            requireCanonicalUuid(page.nextCursor.id, "ID de cursor remoto inválido.")
            if (cursorTime != last.createdAt || page.nextCursor.id != last.id) {
                throw IllegalStateException("Cursor remoto incoherente con el contenido.")
            }
            cursorCreatedAt = cursorTime
            cursorId = page.nextCursor.id
        }

        return MySalesPage(
            items = domainItems,
            hasMore = page.hasMore,
            nextCursorCreatedAt = cursorCreatedAt,
            nextCursorId = cursorId
        )
    }

    private fun RemoteMySaleDto.toDomain(serverTime: Instant): MySale {
        requireCanonicalUuid(id, "ID de venta inválido.")
        if (!folio.matches(Regex("^VD-[0-9]{6}-[A-Z0-9]{6}$"))) {
            throw IllegalStateException("Formato de folio inválido: $folio")
        }

        val status = try {
            MySaleStatus.valueOf(status)
        } catch (_: IllegalArgumentException) {
            throw IllegalStateException("Estado de venta desconocido: $status")
        }
        
        val createdAtInstant = parseInstant(createdAt, "Fecha de creación inválida en venta $folio")
        val updatedAtInstant = parseInstant(updatedAt, "Fecha de actualización inválida en venta $folio")
        val paidAtInstant = paidAt?.let {
            parseInstant(it, "Fecha de pago inválida en venta $folio")
        }

        if (updatedAtInstant.isBefore(createdAtInstant)) {
            throw IllegalStateException("Venta $folio con actualización anterior a su creación.")
        }

        val requiresPaymentDate = status == MySaleStatus.PAID || status == MySaleStatus.DELIVERED
        if (requiresPaymentDate != (paidAtInstant != null)) {
            throw IllegalStateException("El estado de la venta no coincide con su fecha de pago.")
        }

        if (createdAtInstant.isAfter(serverTime) || updatedAtInstant.isAfter(serverTime) || paidAtInstant?.isAfter(serverTime) == true) {
            throw IllegalStateException("Venta $folio con fechas posteriores a la hora del servidor.")
        }

        if (totalCents != subtotalCents - discountCents) {
            throw IllegalStateException("Los totales de la venta $folio son inconsistentes.")
        }

        if (subtotalCents < 0 || discountCents < 0 || totalCents < 0) {
             throw IllegalStateException("La venta $folio contiene importes negativos.")
        }
        if (discountCents > subtotalCents) {
            throw IllegalStateException("Descuento mayor al subtotal en venta $folio.")
        }

        if (itemCount <= 0 || totalQuantity <= 0) {
            throw IllegalStateException("Venta $folio sin partidas o cantidades registradas.")
        }

        return MySale(
            id = id,
            folio = folio,
            status = status,
            createdAt = createdAtInstant,
            updatedAt = updatedAtInstant,
            subtotalCents = subtotalCents,
            discountCents = discountCents,
            totalCents = totalCents,
            itemCount = itemCount,
            totalQuantity = totalQuantity,
            paidAt = paidAtInstant
        )
    }

    private fun requireCanonicalUuid(value: String, message: String) {
        val parsed = runCatching { UUID.fromString(value) }.getOrNull()
        if (parsed == null || !parsed.toString().equals(value, ignoreCase = true)) {
            throw IllegalStateException(message)
        }
    }

    private fun parseInstant(value: String, message: String): Instant = try {
        Instant.parse(value)
    } catch (_: Exception) {
        throw IllegalStateException(message)
    }

    private fun classifyError(e: PostgrestRestException): String = when {
        e.message?.contains("MY_SALES_UNAUTHORIZED", ignoreCase = false) == true ->
            "No tienes autorización para consultar tus comandas."
        e.message?.contains("MY_SALES_QUERY_INVALID", ignoreCase = false) == true ->
            "La consulta de comandas no es válida."
        else -> "Ocurrió un error al cargar tus ventas recientes."
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = false }
    }
}

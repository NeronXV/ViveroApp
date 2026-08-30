package com.intutec.viveroapp.feature.reports.data.repository

import com.intutec.viveroapp.feature.reports.data.remote.RemoteDailySalesDto
import com.intutec.viveroapp.feature.reports.data.remote.RemoteTopProductDto
import com.intutec.viveroapp.feature.reports.data.remote.ReportsRemoteDataSource
import com.intutec.viveroapp.feature.reports.domain.model.DailySales
import com.intutec.viveroapp.feature.reports.domain.model.TopProduct
import com.intutec.viveroapp.feature.reports.domain.repository.ReportsRepository
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

class SupabaseReportsRepository @Inject constructor(
    private val remote: ReportsRemoteDataSource,
) : ReportsRepository {

    override suspend fun getDailySales(
        branchId: String?,
        startDate: LocalDate?,
        endDate: LocalDate?,
    ): Result<List<DailySales>> = runCatching {
        try {
            val jsonResponse = remote.getDailySales(branchId, startDate, endDate)
            val dtos = json.decodeFromString<List<RemoteDailySalesDto>>(jsonResponse)
            dtos.map { it.toDomain() }
        } catch (e: PostgrestRestException) {
            throw Exception(classifyError(e))
        }
    }

    override suspend fun getTopProducts(
        branchId: String?,
        limit: Int,
    ): Result<List<TopProduct>> = runCatching {
        try {
            val jsonResponse = remote.getTopProducts(branchId, limit)
            val dtos = json.decodeFromString<List<RemoteTopProductDto>>(jsonResponse)
            dtos.map { it.toDomain() }
        } catch (e: PostgrestRestException) {
            throw Exception(classifyError(e))
        }
    }

    private fun RemoteDailySalesDto.toDomain(): DailySales {
        requireCanonicalUuid(branchId, "ID de sucursal inválido en reporte.")
        return DailySales(
            branchId = branchId,
            branchName = branchName,
            day = LocalDate.parse(day.take(10)),
            salesCount = salesCount,
            revenueCents = revenueCents,
            discountCents = discountCents
        )
    }

    private fun RemoteTopProductDto.toDomain(): TopProduct {
        requireCanonicalUuid(productId, "ID de producto inválido en reporte.")
        return TopProduct(
            productId = productId,
            productName = productName,
            productCode = productCode,
            totalQuantity = totalQuantity,
            totalRevenueCents = totalRevenueCents
        )
    }

    private fun requireCanonicalUuid(value: String, message: String) {
        val parsed = runCatching { UUID.fromString(value) }.getOrNull()
        if (parsed == null || !parsed.toString().equals(value, ignoreCase = true)) {
            throw IllegalStateException(message)
        }
    }

    private fun classifyError(e: PostgrestRestException): String = when (e.code) {
        "42501" -> "No tienes permiso para ver reportes."
        "P0001" -> e.message ?: "Error de negocio en el reporte."
        else -> "Ocurrió un error al cargar el reporte."
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

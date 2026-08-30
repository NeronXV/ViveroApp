package com.intutec.viveroapp.feature.customer.data.repository

import com.intutec.viveroapp.feature.customer.data.remote.CustomerRemoteDataSource
import com.intutec.viveroapp.feature.customer.data.remote.RemoteCustomerDto
import com.intutec.viveroapp.feature.customer.domain.model.Customer
import com.intutec.viveroapp.feature.customer.domain.repository.CustomerRepository
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject

class SupabaseCustomerRepository @Inject constructor(
    private val remote: CustomerRemoteDataSource,
) : CustomerRepository {

    override suspend fun searchCustomers(query: String, limit: Int): Result<List<Customer>> = runCatching {
        try {
            val jsonResponse = remote.searchCustomers(query, limit)
            val dtos = json.decodeFromString<List<RemoteCustomerDto>>(jsonResponse)
            dtos.map { it.toDomain() }
        } catch (e: PostgrestRestException) {
            throw Exception(classifyError(e))
        }
    }

    private fun RemoteCustomerDto.toDomain(): Customer {
        requireCanonicalUuid(id, "El servidor devolvió un ID de cliente inválido.")
        require(fullName.isNotBlank()) { "El servidor devolvió un nombre de cliente vacío." }
        return Customer(
            id = id,
            fullName = fullName,
            email = email?.takeIf { it.isNotBlank() },
            phone = phone?.takeIf { it.isNotBlank() }
        )
    }

    private fun requireCanonicalUuid(value: String, message: String) {
        val parsed = runCatching { UUID.fromString(value) }.getOrNull()
        if (parsed == null || !parsed.toString().equals(value, ignoreCase = true)) {
            throw IllegalStateException(message)
        }
    }

    private fun classifyError(e: PostgrestRestException): String = when (e.code) {
        "42501" -> "No tienes permiso para buscar clientes."
        "22023" -> "La búsqueda no es válida."
        else -> "Ocurrió un error al buscar clientes."
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

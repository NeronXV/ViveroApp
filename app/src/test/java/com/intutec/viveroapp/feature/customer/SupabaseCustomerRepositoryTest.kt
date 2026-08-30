package com.intutec.viveroapp.feature.customer

import com.intutec.viveroapp.feature.customer.data.remote.CustomerRemoteDataSource
import com.intutec.viveroapp.feature.customer.data.repository.SupabaseCustomerRepository
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class SupabaseCustomerRepositoryTest {
    
    @Test
    fun `parses valid customer list correctly`() = runTest {
        val customerId = UUID.randomUUID().toString()
        val jsonResponse = """
            [
                {
                    "id": "$customerId",
                    "fullName": "Juan Perez",
                    "email": "juan@example.com",
                    "phone": "5551234567"
                }
            ]
        """.trimIndent()

        val remote = FakeCustomerRemoteDataSource(jsonResponse)
        val repository = SupabaseCustomerRepository(remote)

        val result = repository.searchCustomers("juan")
        
        assertTrue(result.isSuccess)
        val customers = result.getOrThrow()
        assertEquals(1, customers.size)
        assertEquals(customerId, customers[0].id)
        assertEquals("Juan Perez", customers[0].fullName)
        assertEquals("juan@example.com", customers[0].email)
        assertEquals("5551234567", customers[0].phone)
    }

    @Test
    fun `rejects invalid UUID in response`() = runTest {
        val jsonResponse = """
            [
                {
                    "id": "not-a-uuid",
                    "fullName": "Juan Perez"
                }
            ]
        """.trimIndent()

        val remote = FakeCustomerRemoteDataSource(jsonResponse)
        val repository = SupabaseCustomerRepository(remote)

        val result = repository.searchCustomers("juan")
        assertTrue(result.isFailure)
        assertEquals("El servidor devolvió un ID de cliente inválido.", result.exceptionOrNull()?.message)
    }

    @Test
    fun `rejects missing fullName in response`() = runTest {
        val jsonResponse = """
            [
                {
                    "id": "${UUID.randomUUID()}",
                    "email": "juan@example.com"
                }
            ]
        """.trimIndent()

        val remote = FakeCustomerRemoteDataSource(jsonResponse)
        val repository = SupabaseCustomerRepository(remote)

        val result = repository.searchCustomers("juan")
        assertTrue(result.isFailure)
    }

    private class FakeCustomerRemoteDataSource(val response: String) : CustomerRemoteDataSource {
        override suspend fun searchCustomers(query: String, limit: Int): String = response
    }
}

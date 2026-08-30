package com.intutec.viveroapp.feature.customer.domain.repository

import com.intutec.viveroapp.feature.customer.domain.model.Customer

interface CustomerRepository {
    suspend fun searchCustomers(query: String, limit: Int = 20): Result<List<Customer>>
}

package com.intutec.viveroapp.feature.customer.data.remote

interface CustomerRemoteDataSource {
    suspend fun searchCustomers(query: String, limit: Int): String
}

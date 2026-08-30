package com.intutec.viveroapp.feature.customer.domain.model

data class Customer(
    val id: String,
    val fullName: String,
    val email: String? = null,
    val phone: String? = null,
)

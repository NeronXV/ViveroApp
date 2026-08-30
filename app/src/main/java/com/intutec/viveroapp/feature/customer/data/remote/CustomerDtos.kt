package com.intutec.viveroapp.feature.customer.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RemoteCustomerDto(
    val id: String,
    @SerialName("fullName") val fullName: String,
    val email: String? = null,
    val phone: String? = null,
)

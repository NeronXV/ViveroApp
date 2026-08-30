package com.intutec.viveroapp.feature.mysales.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class RemoteMySaleDto(
    val id: String,
    val folio: String,
    val status: String,
    val createdAt: String,
    val updatedAt: String,
    val subtotalCents: Long,
    val discountCents: Long,
    val totalCents: Long,
    val itemCount: Int,
    val totalQuantity: Int,
    val paidAt: String? = null,
)

@Serializable
data class RemoteMySalesResponseDto(
    val schemaVersion: Int,
    val items: List<RemoteMySaleDto>,
    val page: RemotePageDto,
    val serverTime: String,
)

@Serializable
data class RemotePageDto(
    val limit: Int,
    val hasMore: Boolean,
    val nextCursor: RemoteCursorDto? = null,
)

@Serializable
data class RemoteCursorDto(
    val createdAt: String,
    val id: String,
)

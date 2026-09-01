package com.intutec.viveroapp.feature.cashier.data.repository

import com.intutec.viveroapp.feature.cashier.data.remote.CashierRemoteDataSource
import com.intutec.viveroapp.feature.cashier.data.remote.RemoteCashierOrderDto
import com.intutec.viveroapp.feature.cashier.data.remote.RemoteCashierOrderItemDto
import com.intutec.viveroapp.feature.cashier.domain.model.CashierOrderDetail
import com.intutec.viveroapp.feature.cashier.domain.model.CashierOrderItem
import com.intutec.viveroapp.feature.cashier.domain.model.CashierOrderStatus
import com.intutec.viveroapp.feature.cashier.domain.model.CashierOrderSummary
import com.intutec.viveroapp.feature.cashier.domain.repository.CashierRepository
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.UUID
import javax.inject.Inject

class SupabaseCashierRepository @Inject constructor(
    private val remote: CashierRemoteDataSource,
) : CashierRepository {
    override suspend fun getPendingOrders(branchId: String): Result<List<CashierOrderSummary>> = runCatching {
        val canonicalBranchId = branchId.requireUuid("sucursal")
        remote.loadPendingOrders(canonicalBranchId)
            .map { it.toDomain(canonicalBranchId).summary }
            .also { orders ->
                check(orders.map(CashierOrderSummary::id).distinct().size == orders.size) {
                    "Caja recibió comandas duplicadas."
                }
            }
            .sortedWith(compareBy(CashierOrderSummary::createdAt).thenBy(CashierOrderSummary::id))
    }

    override suspend fun getPendingOrder(branchId: String, orderId: String): Result<CashierOrderDetail> = runCatching {
        val canonicalBranchId = branchId.requireUuid("sucursal")
        val canonicalOrderId = orderId.requireUuid("comanda")
        val records = remote.loadPendingOrder(canonicalBranchId, canonicalOrderId)
        check(records.size <= 1) { "Caja recibió una comanda ambigua." }
        records.singleOrNull()?.toDomain(canonicalBranchId)
            ?: error("La comanda ya no está disponible en la bandeja.")
    }
}

private fun RemoteCashierOrderDto.toDomain(expectedBranchId: String): CashierOrderDetail {
    val canonicalId = id.requireUuid("identificador de comanda")
    val canonicalBranchId = branchId.requireUuid("sucursal de comanda")
    check(canonicalBranchId == expectedBranchId) { "Caja recibió una comanda de otra sucursal." }
    val canonicalCreator = createdBy.requireUuid("creador de comanda")
    val parsedStatus = runCatching { CashierOrderStatus.valueOf(status) }
        .getOrElse { error("Caja recibió un estado no permitido.") }
    check(folio.matches(FOLIO_PATTERN)) { "Caja recibió un folio inválido." }
    check(subtotalCents >= 0 && discountCents >= 0 && discountCents <= subtotalCents) {
        "Caja recibió importes inválidos."
    }
    check(totalCents == subtotalCents - discountCents) { "Caja recibió un total inconsistente." }
    check(items.isNotEmpty()) { "Caja recibió una comanda sin partidas." }

    val mappedItems = items.map { it.toDomain(canonicalId) }
    check(mappedItems.map(CashierOrderItem::id).distinct().size == mappedItems.size) {
        "Caja recibió partidas duplicadas."
    }
    val pricedLineSubtotal = mappedItems.sumExact(CashierOrderItem::lineTotalCents)
    check(pricedLineSubtotal == subtotalCents) {
        "Caja recibió partidas que no coinciden con el total del servidor."
    }

    return CashierOrderDetail(
        summary = CashierOrderSummary(
            id = canonicalId,
            folio = folio,
            branchId = canonicalBranchId,
            createdBy = canonicalCreator,
            createdAt = createdAt.parseTimestamp("fecha de creación"),
            updatedAt = updatedAt.parseTimestamp("fecha de actualización"),
            status = parsedStatus,
            productCount = mappedItems.size,
            unitCount = mappedItems.sumOf(CashierOrderItem::quantity),
            totalCents = totalCents,
        ),
        subtotalCents = subtotalCents,
        discountCents = discountCents,
        items = mappedItems.sortedWith(compareBy(CashierOrderItem::productName).thenBy(CashierOrderItem::id)),
    )
}

private fun RemoteCashierOrderItemDto.toDomain(expectedSaleId: String): CashierOrderItem {
    val canonicalId = id.requireUuid("identificador de partida")
    val canonicalSaleId = saleId.requireUuid("comanda de partida")
    check(canonicalSaleId == expectedSaleId) { "Caja recibió una partida de otra comanda." }
    val canonicalProductId = productId.requireUuid("producto de partida")
    check(internalCode.isNotBlank() && productName.isNotBlank() && quantity > 0) {
        "Caja recibió una partida incompleta."
    }
    check(listPriceCents >= 0 && unitPriceCents >= 0 && unitPriceCents <= listPriceCents) {
        "Caja recibió un precio de partida inválido."
    }
    val expectedDiscount = Math.multiplyExact(listPriceCents - unitPriceCents, quantity.toLong())
    val expectedTotal = Math.multiplyExact(unitPriceCents, quantity.toLong())
    check(discountCents == expectedDiscount && lineTotalCents == expectedTotal) {
        "Caja recibió una partida con importes inconsistentes."
    }
    return CashierOrderItem(
        id = canonicalId,
        productId = canonicalProductId,
        internalCode = internalCode.trim(),
        productName = productName.trim(),
        quantity = quantity,
        listPriceCents = listPriceCents,
        unitPriceCents = unitPriceCents,
        discountCents = discountCents,
        lineTotalCents = lineTotalCents,
        promotionName = promotionName?.trim()?.takeIf(String::isNotEmpty),
    )
}

private inline fun <T> Iterable<T>.sumExact(value: (T) -> Long): Long =
    fold(0L) { total, item -> Math.addExact(total, value(item)) }

private fun String.requireUuid(field: String): String = try {
    UUID.fromString(this).toString()
} catch (_: IllegalArgumentException) {
    throw IllegalStateException("Caja recibió un $field inválido.")
}

private fun String.parseTimestamp(field: String): Instant = try {
    OffsetDateTime.parse(this, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant()
} catch (_: DateTimeParseException) {
    throw IllegalStateException("Caja recibió una $field inválida.")
}

private val FOLIO_PATTERN = Regex("^VD-[0-9]{6}-[A-Z0-9]{6}$")

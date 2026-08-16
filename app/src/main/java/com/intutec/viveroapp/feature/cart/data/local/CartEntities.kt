package com.intutec.viveroapp.feature.cart.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "cart_drafts")
data class CartHeaderEntity(
    @PrimaryKey val id: String,
    @ColumnInfo("customer_id") val customerId: String?,
    @ColumnInfo("customer_name") val customerName: String?,
    @ColumnInfo("member_number") val memberNumber: String?,
    @ColumnInfo("updated_at_epoch_ms") val updatedAtEpochMs: Long,
)

@Entity(
    tableName = "cart_items",
    primaryKeys = ["cart_id", "product_id"],
    foreignKeys = [
        ForeignKey(
            entity = CartHeaderEntity::class,
            parentColumns = ["id"],
            childColumns = ["cart_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("cart_id")],
)
data class CartItemEntity(
    @ColumnInfo("cart_id") val cartId: String,
    @ColumnInfo("product_id") val productId: String,
    @ColumnInfo("internal_code") val internalCode: String,
    val name: String,
    @ColumnInfo("image_key") val imageKey: String,
    val unit: String,
    @ColumnInfo("list_price_cents") val listPriceCents: Long,
    @ColumnInfo("unit_price_cents") val unitPriceCents: Long,
    val quantity: Int,
    @ColumnInfo("stock_available") val stockAvailable: Int,
    @ColumnInfo(name = "stock_known", defaultValue = "1") val stockKnown: Boolean,
    @ColumnInfo("promotion_name") val promotionName: String?,
)

data class CartWithItems(
    @Embedded val header: CartHeaderEntity,
    @Relation(parentColumn = "id", entityColumn = "cart_id") val items: List<CartItemEntity>,
)

@Entity(
    tableName = "sales",
    indices = [
        Index(value = ["folio"], unique = true),
        Index(value = ["sync_state", "created_at_epoch_ms"]),
    ],
)
data class SaleEntity(
    @PrimaryKey val id: String,
    val folio: String,
    @ColumnInfo("customer_id") val customerId: String?,
    @ColumnInfo("customer_name") val customerName: String?,
    @ColumnInfo("member_number") val memberNumber: String?,
    @ColumnInfo("subtotal_cents") val subtotalCents: Long,
    @ColumnInfo("discount_cents") val discountCents: Long,
    @ColumnInfo("total_cents") val totalCents: Long,
    val status: String,
    @ColumnInfo("created_by") val createdBy: String,
    @ColumnInfo("branch_id") val branchId: String?,
    @ColumnInfo("created_at_epoch_ms") val createdAtEpochMs: Long,
    @ColumnInfo("sync_pending") val syncPending: Boolean,
    @ColumnInfo(name = "sync_state", defaultValue = "'PENDING'") val syncState: String,
    @ColumnInfo(name = "sync_attempt_count", defaultValue = "0") val syncAttemptCount: Int,
    @ColumnInfo("sync_last_error") val syncLastError: String?,
    @ColumnInfo("sync_last_attempt_at_epoch_ms") val syncLastAttemptAtEpochMs: Long?,
)

@Entity(
    tableName = "sale_items",
    primaryKeys = ["sale_id", "product_id"],
    foreignKeys = [ForeignKey(entity = SaleEntity::class, parentColumns = ["id"], childColumns = ["sale_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sale_id")],
)
data class SaleItemEntity(
    @ColumnInfo("sale_id") val saleId: String,
    @ColumnInfo("product_id") val productId: String,
    @ColumnInfo("internal_code") val internalCode: String,
    val name: String,
    @ColumnInfo("image_key") val imageKey: String,
    val unit: String,
    @ColumnInfo("list_price_cents") val listPriceCents: Long,
    @ColumnInfo("unit_price_cents") val unitPriceCents: Long,
    val quantity: Int,
    @ColumnInfo("stock_available") val stockAvailable: Int,
    @ColumnInfo(name = "stock_known", defaultValue = "1") val stockKnown: Boolean,
    @ColumnInfo("promotion_name") val promotionName: String?,
)

data class SaleWithItems(
    @Embedded val sale: SaleEntity,
    @Relation(parentColumn = "id", entityColumn = "sale_id") val items: List<SaleItemEntity>,
)

@Entity(
    tableName = "sale_status_history",
    foreignKeys = [ForeignKey(entity = SaleEntity::class, parentColumns = ["id"], childColumns = ["sale_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sale_id")],
)
data class SaleStatusHistoryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo("sale_id") val saleId: String,
    @ColumnInfo("previous_status") val previousStatus: String?,
    @ColumnInfo("new_status") val newStatus: String,
    @ColumnInfo("user_id") val userId: String,
    @ColumnInfo("changed_at_epoch_ms") val changedAtEpochMs: Long,
    val note: String?,
)

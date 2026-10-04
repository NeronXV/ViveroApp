package com.intutec.viveroapp.feature.cashier.data.local

import androidx.room.*

@Entity(tableName = "backend_payment_attempts", indices = [Index(value = ["attempt_key"], unique = true)])
data class BackendPaymentAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo("attempt_key") val key: String,
    @ColumnInfo("actor_id") val actorId: Long,
    @ColumnInfo("branch_id") val branchId: Long,
    @ColumnInfo("sale_id") val saleId: Long,
    val body: String,
    val state: String = "PENDING",
    val receipt: String? = null,
) { override fun toString() = "BackendPaymentAttempt(id=$id, saleId=$saleId, state=$state, request=<redacted>)" }
@Dao
abstract class BackendPaymentAttemptDao {
    @Insert protected abstract suspend fun insert(row: BackendPaymentAttemptEntity): Long
    @Query("SELECT * FROM backend_payment_attempts WHERE id = :id") abstract suspend fun load(id: Long): BackendPaymentAttemptEntity?
    @Query("SELECT * FROM backend_payment_attempts WHERE actor_id = :actor AND branch_id = :branch AND state IN ('PENDING','UNCERTAIN','SYNCING') ORDER BY id")
    abstract suspend fun pending(actor: Long, branch: Long): List<BackendPaymentAttemptEntity>
    @Transaction
    open suspend fun enqueue(row: BackendPaymentAttemptEntity): Long {
        check(pending(row.actorId, row.branchId).isEmpty()) { "Consulta el pago pendiente antes de iniciar otro." }
        return insert(row)
    }
    @Query("UPDATE backend_payment_attempts SET state = 'SYNCING' WHERE id = :id AND state IN ('PENDING','UNCERTAIN')")
    abstract suspend fun claim(id: Long): Int
    @Query("UPDATE backend_payment_attempts SET state = 'UNCERTAIN' WHERE id = :id AND state = 'SYNCING'")
    abstract suspend fun uncertain(id: Long): Int
    @Query("UPDATE backend_payment_attempts SET state = 'SUCCEEDED', receipt = :receipt WHERE id = :id AND state = 'SYNCING'")
    abstract suspend fun complete(id: Long, receipt: String): Int
    @Query("UPDATE backend_payment_attempts SET state = 'RETIRED' WHERE id = :id AND state = 'SYNCING'")
    abstract suspend fun retire(id: Long): Int
}

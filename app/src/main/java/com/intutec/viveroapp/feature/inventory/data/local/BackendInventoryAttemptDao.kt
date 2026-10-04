package com.intutec.viveroapp.feature.inventory.data.local

import androidx.room.*

@Entity(tableName="backend_inventory_attempts",indices=[Index(value=["attempt_key"],unique=true)])
data class BackendInventoryAttemptEntity(
    @PrimaryKey(autoGenerate=true) val id:Long=0,
    @ColumnInfo("actor_id") val actorId:Long,
    @ColumnInfo("branch_id") val branchId:Long,
    @ColumnInfo("product_id") val productId:Long,
    val action:String,
    val quantity:Long,
    val notes:String?,
    @ColumnInfo("attempt_key") val key:String,
    val state:String="PENDING",
    @ColumnInfo("server_id") val serverId:Long?=null,
) { override fun toString()="BackendInventoryAttemptEntity(id=$id, action=$action, request=<redacted>)" }
@Dao
abstract class BackendInventoryAttemptDao {
    @Insert protected abstract suspend fun insert(row:BackendInventoryAttemptEntity):Long
    @Query("SELECT * FROM backend_inventory_attempts WHERE id=:id") abstract suspend fun load(id:Long):BackendInventoryAttemptEntity?
    @Query("SELECT * FROM backend_inventory_attempts WHERE actor_id=:actor AND branch_id=:branch AND state IN ('PENDING','UNCERTAIN','SYNCING') ORDER BY id")
    abstract suspend fun pending(actor:Long,branch:Long):List<BackendInventoryAttemptEntity>
    @Transaction open suspend fun enqueue(row:BackendInventoryAttemptEntity):Long {
        check(pending(row.actorId,row.branchId).isEmpty()) {"Consulta la operación pendiente antes de iniciar otra."};return insert(row)
    }
    @Query("UPDATE backend_inventory_attempts SET state='SYNCING' WHERE id=:id AND state IN ('PENDING','UNCERTAIN')") abstract suspend fun claim(id:Long):Int
    @Query("UPDATE backend_inventory_attempts SET state='UNCERTAIN' WHERE id=:id AND state='SYNCING'") abstract suspend fun uncertain(id:Long):Int
    @Query("UPDATE backend_inventory_attempts SET state='SUCCEEDED', server_id=:server WHERE id=:id AND state='SYNCING'") abstract suspend fun complete(id:Long,server:Long):Int
}

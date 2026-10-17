package com.intutec.viveroapp.feature.plants.data.local

import androidx.room.*

@Entity(tableName="plant_work", primaryKeys=["actorId","branchId"])
data class PlantWorkEntity(val actorId: Long, val branchId: Long, val form: String?, val attempt: String?)
@Dao
interface PlantWorkDao {
    @Query("SELECT * FROM plant_work WHERE actorId=:actor AND branchId=:branch")
    suspend fun get(actor:Long,branch:Long):PlantWorkEntity?
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun put(value:PlantWorkEntity)
}

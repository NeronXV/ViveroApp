package com.intutec.viveroapp.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.intutec.viveroapp.feature.cart.data.local.CartDao
import com.intutec.viveroapp.feature.cart.data.local.CartHeaderEntity
import com.intutec.viveroapp.feature.cart.data.local.CartItemEntity
import com.intutec.viveroapp.feature.cart.data.local.SaleEntity
import com.intutec.viveroapp.feature.cart.data.local.SaleItemEntity
import com.intutec.viveroapp.feature.cart.data.local.SaleStatusHistoryEntity

@Database(
    entities = [
        CartHeaderEntity::class,
        CartItemEntity::class,
        SaleEntity::class,
        SaleItemEntity::class,
        SaleStatusHistoryEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class ViveroDatabase : RoomDatabase() {
    abstract fun cartDao(): CartDao
}

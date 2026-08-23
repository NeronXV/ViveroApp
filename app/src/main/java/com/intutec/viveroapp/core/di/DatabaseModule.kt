package com.intutec.viveroapp.core.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.intutec.viveroapp.core.database.ViveroDatabase
import com.intutec.viveroapp.feature.cart.data.local.CartDao
import com.intutec.viveroapp.feature.cashier.data.local.CashierPaymentAttemptDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ViveroDatabase =
        Room.databaseBuilder(context, ViveroDatabase::class.java, "vivero.db")
            .addMigrations(ViveroDatabase.MIGRATION_1_2, ViveroDatabase.MIGRATION_2_3)
            .addCallback(
                object : RoomDatabase.Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        db.execSQL(ViveroDatabase.RECOVER_INTERRUPTED_SYNC_SQL)
                        db.execSQL(ViveroDatabase.RECOVER_INTERRUPTED_PAYMENT_SQL)
                        db.execSQL(ViveroDatabase.RECONCILE_SUCCEEDED_PAYMENT_SALES_SQL)
                        db.execSQL(ViveroDatabase.CLEANUP_TERMINAL_PAYMENT_ATTEMPTS_SQL)
                    }
                },
            )
            .build()

    @Provides
    fun provideCartDao(database: ViveroDatabase): CartDao = database.cartDao()

    @Provides
    fun provideCashierPaymentAttemptDao(database: ViveroDatabase): CashierPaymentAttemptDao =
        database.cashierPaymentAttemptDao()
}

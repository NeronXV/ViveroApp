package com.intutec.viveroapp.core.di

import android.content.Context
import androidx.room.Room
import com.intutec.viveroapp.core.database.ViveroDatabase
import com.intutec.viveroapp.feature.cart.data.local.CartDao
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
        Room.databaseBuilder(context, ViveroDatabase::class.java, "vivero.db").build()

    @Provides
    fun provideCartDao(database: ViveroDatabase): CartDao = database.cartDao()
}

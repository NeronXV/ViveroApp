package com.intutec.viveroapp.core.di

import com.intutec.viveroapp.feature.home.data.repository.FakeDashboardRepository
import com.intutec.viveroapp.feature.home.domain.repository.DashboardRepository
import com.intutec.viveroapp.feature.auth.data.repository.AuthRepositoryImpl
import com.intutec.viveroapp.feature.auth.data.remote.AuthRemoteDataSource
import com.intutec.viveroapp.feature.auth.data.remote.SupabaseAuthRemoteDataSource
import com.intutec.viveroapp.feature.auth.domain.repository.AuthRepository
import com.intutec.viveroapp.feature.catalog.data.repository.SessionCatalogRepository
import com.intutec.viveroapp.feature.catalog.data.remote.CatalogRemoteDataSource
import com.intutec.viveroapp.feature.catalog.data.remote.SupabaseCatalogRemoteDataSource
import com.intutec.viveroapp.feature.catalog.domain.repository.CatalogRepository
import com.intutec.viveroapp.feature.cart.data.repository.RoomCartRepository
import com.intutec.viveroapp.feature.cart.domain.repository.CartRepository
import com.intutec.viveroapp.feature.cart.sync.RoomSaleOutboxStore
import com.intutec.viveroapp.feature.cart.sync.SaleOutboxStore
import com.intutec.viveroapp.feature.cart.sync.SaleSyncRemoteDataSource
import com.intutec.viveroapp.feature.cart.sync.SupabaseSaleSyncRemoteDataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindAuthRemoteDataSource(
        implementation: SupabaseAuthRemoteDataSource,
    ): AuthRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindCatalogRemoteDataSource(
        implementation: SupabaseCatalogRemoteDataSource,
    ): CatalogRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindCartRepository(implementation: RoomCartRepository): CartRepository

    @Binds
    @Singleton
    abstract fun bindSaleOutboxStore(implementation: RoomSaleOutboxStore): SaleOutboxStore

    @Binds
    @Singleton
    abstract fun bindSaleSyncRemoteDataSource(
        implementation: SupabaseSaleSyncRemoteDataSource,
    ): SaleSyncRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindCatalogRepository(implementation: SessionCatalogRepository): CatalogRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(implementation: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindDashboardRepository(
        implementation: FakeDashboardRepository,
    ): DashboardRepository
}

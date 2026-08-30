package com.intutec.viveroapp.core.di

import com.intutec.viveroapp.feature.home.data.repository.SessionDashboardRepository
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
import com.intutec.viveroapp.feature.cashier.data.remote.CashierRemoteDataSource
import com.intutec.viveroapp.feature.cashier.data.remote.SupabaseCashierRemoteDataSource
import com.intutec.viveroapp.feature.cashier.data.repository.SupabaseCashierRepository
import com.intutec.viveroapp.feature.cashier.domain.repository.CashierRepository
import com.intutec.viveroapp.feature.cashier.data.local.CashierPaymentAttemptStore
import com.intutec.viveroapp.feature.cashier.data.local.RoomCashierPaymentAttemptStore
import com.intutec.viveroapp.feature.cashier.data.remote.CashierPaymentRemoteDataSource
import com.intutec.viveroapp.feature.cashier.data.remote.SupabaseCashierPaymentRemoteDataSource
import com.intutec.viveroapp.feature.cashier.data.repository.SupabaseCashierPaymentRepository
import com.intutec.viveroapp.feature.cashier.domain.repository.CashierPaymentRepository
import com.intutec.viveroapp.feature.inventory.data.repository.SupabaseInventoryRepository
import com.intutec.viveroapp.feature.inventory.domain.repository.InventoryRepository
import com.intutec.viveroapp.feature.customer.data.repository.SupabaseCustomerRepository
import com.intutec.viveroapp.feature.customer.domain.repository.CustomerRepository
import com.intutec.viveroapp.feature.customer.data.remote.CustomerRemoteDataSource
import com.intutec.viveroapp.feature.customer.data.remote.SupabaseCustomerRemoteDataSource
import com.intutec.viveroapp.feature.reports.data.repository.SupabaseReportsRepository
import com.intutec.viveroapp.feature.reports.domain.repository.ReportsRepository
import com.intutec.viveroapp.feature.reports.data.remote.ReportsRemoteDataSource
import com.intutec.viveroapp.feature.reports.data.remote.SupabaseReportsRemoteDataSource
import com.intutec.viveroapp.feature.mysales.data.remote.MySalesRemoteDataSource
import com.intutec.viveroapp.feature.mysales.data.remote.SupabaseMySalesRemoteDataSource
import com.intutec.viveroapp.feature.mysales.data.repository.SupabaseMySalesRepository
import com.intutec.viveroapp.feature.mysales.domain.repository.MySalesRepository
import com.intutec.viveroapp.feature.catalog.data.remote.CatalogAdminRemoteDataSource
import com.intutec.viveroapp.feature.catalog.data.remote.CatalogImageRemoteDataSource
import com.intutec.viveroapp.feature.catalog.data.remote.SupabaseCatalogAdminRemoteDataSource
import com.intutec.viveroapp.feature.catalog.data.remote.SupabaseCatalogImageRemoteDataSource
import com.intutec.viveroapp.feature.catalog.data.repository.SupabaseCatalogAdminRepository
import com.intutec.viveroapp.feature.catalog.domain.repository.CatalogAdminRepository
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
        implementation: SessionDashboardRepository,
    ): DashboardRepository

    @Binds
    @Singleton
    abstract fun bindCashierRemoteDataSource(
        implementation: SupabaseCashierRemoteDataSource,
    ): CashierRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindCashierRepository(
        implementation: SupabaseCashierRepository,
    ): CashierRepository

    @Binds
    @Singleton
    abstract fun bindCashierPaymentAttemptStore(
        implementation: RoomCashierPaymentAttemptStore,
    ): CashierPaymentAttemptStore

    @Binds
    @Singleton
    abstract fun bindCashierPaymentRemoteDataSource(
        implementation: SupabaseCashierPaymentRemoteDataSource,
    ): CashierPaymentRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindCashierPaymentRepository(
        implementation: SupabaseCashierPaymentRepository,
    ): CashierPaymentRepository

    @Binds
    @Singleton
    abstract fun bindInventoryRepository(
        implementation: SupabaseInventoryRepository,
    ): InventoryRepository

    @Binds
    @Singleton
    abstract fun bindReportsRemoteDataSource(
        implementation: SupabaseReportsRemoteDataSource,
    ): ReportsRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindCustomerRemoteDataSource(
        implementation: SupabaseCustomerRemoteDataSource,
    ): CustomerRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindCustomerRepository(
        implementation: SupabaseCustomerRepository,
    ): CustomerRepository

    @Binds
    @Singleton
    abstract fun bindReportsRepository(
        implementation: SupabaseReportsRepository,
    ): ReportsRepository

    @Binds
    @Singleton
    abstract fun bindMySalesRemoteDataSource(
        implementation: SupabaseMySalesRemoteDataSource,
    ): MySalesRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindMySalesRepository(
        implementation: SupabaseMySalesRepository,
    ): MySalesRepository

    @Binds
    @Singleton
    abstract fun bindCatalogAdminRemoteDataSource(
        implementation: SupabaseCatalogAdminRemoteDataSource,
    ): CatalogAdminRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindCatalogImageRemoteDataSource(
        implementation: SupabaseCatalogImageRemoteDataSource,
    ): CatalogImageRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindCatalogAdminRepository(
        implementation: SupabaseCatalogAdminRepository,
    ): CatalogAdminRepository
}

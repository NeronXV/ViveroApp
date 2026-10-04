package com.intutec.viveroapp.core.di

import com.intutec.viveroapp.BuildConfig
import com.intutec.viveroapp.core.network.BackendApiTransport
import com.intutec.viveroapp.core.network.KtorBackendApiTransport
import com.intutec.viveroapp.core.network.backendApiOrigin
import com.intutec.viveroapp.feature.cart.data.remote.BackendSaleRemoteDataSource
import com.intutec.viveroapp.feature.cart.domain.repository.BackendSaleGateway
import com.intutec.viveroapp.core.session.SessionStore
import com.intutec.viveroapp.feature.auth.data.remote.ApiBackendAuthRemoteDataSource
import com.intutec.viveroapp.feature.auth.data.remote.BackendAuthRemoteDataSource
import com.intutec.viveroapp.feature.auth.data.repository.BackendAuthRepository
import com.intutec.viveroapp.feature.auth.domain.repository.BackendAuthGateway
import com.intutec.viveroapp.feature.catalog.data.remote.BackendCatalogRemoteDataSource
import com.intutec.viveroapp.feature.catalog.domain.repository.BackendCatalogGateway
import com.intutec.viveroapp.feature.cart.sync.BackendSaleOutboxStore
import com.intutec.viveroapp.feature.cart.sync.RoomBackendSaleOutboxStore
import com.intutec.viveroapp.feature.cart.data.repository.BackendSaleEmitterRepository
import com.intutec.viveroapp.feature.cart.domain.repository.BackendSaleEmitter
import com.intutec.viveroapp.feature.cart.domain.repository.BackendCartRepository
import com.intutec.viveroapp.feature.cart.data.repository.BackendRoomCartRepository
import com.intutec.viveroapp.feature.cashier.data.remote.BackendCashierRemoteDataSource
import com.intutec.viveroapp.feature.cashier.domain.repository.BackendCashierGateway
import com.intutec.viveroapp.feature.cashier.domain.repository.BackendCashierPaymentRepository
import com.intutec.viveroapp.feature.cashier.data.repository.BackendCashierPayments
import com.intutec.viveroapp.feature.mysales.data.remote.BackendHistoryRemoteDataSource
import com.intutec.viveroapp.feature.mysales.domain.repository.BackendHistoryGateway
import com.intutec.viveroapp.feature.inventory.data.remote.BackendInventoryRemoteDataSource
import com.intutec.viveroapp.feature.inventory.data.repository.BackendRoomInventoryOperations
import com.intutec.viveroapp.feature.inventory.domain.repository.BackendInventoryGateway
import com.intutec.viveroapp.feature.inventory.domain.repository.BackendInventoryOperations
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BackendNetworkModule {
    @Provides @Singleton @Named("backendApi")
    fun client(): HttpClient = HttpClient(OkHttp) {
        engine { config { retryOnConnectionFailure(false) } }
        followRedirects = false
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = 15000
            connectTimeoutMillis = 8000
            socketTimeoutMillis = 15000
        }
    }
    @Provides @Named("backendApiOrigin")
    fun origin(): String = backendApiOrigin(BuildConfig.BACKEND_API_URL, BuildConfig.DEBUG)
    @Provides @Singleton
    fun transport(value: KtorBackendApiTransport): BackendApiTransport = value
    @Provides @Singleton
    fun sales(value: BackendSaleRemoteDataSource): BackendSaleGateway = value
    @Provides @Singleton
    fun catalog(value: BackendCatalogRemoteDataSource): BackendCatalogGateway = value
    @Provides @Singleton
    fun backendOutbox(value: RoomBackendSaleOutboxStore): BackendSaleOutboxStore = value
    @Provides @Singleton
    fun backendEmitter(value: BackendSaleEmitterRepository): BackendSaleEmitter = value
    @Provides @Singleton
    fun backendCart(value: BackendRoomCartRepository): BackendCartRepository = value
    @Provides @Singleton
    fun backendCashier(value: BackendCashierRemoteDataSource): BackendCashierGateway = value
    @Provides @Singleton
    fun backendPayments(value: BackendCashierPayments): BackendCashierPaymentRepository = value
    @Provides @Singleton
    fun backendHistory(value: BackendHistoryRemoteDataSource): BackendHistoryGateway = value
    @Provides @Singleton
    fun backendInventory(value: BackendInventoryRemoteDataSource): BackendInventoryGateway = value
    @Provides @Singleton
    fun backendInventoryOperations(value: BackendRoomInventoryOperations): BackendInventoryOperations = value
    @Provides @Singleton
    fun backendAuthRemote(value: ApiBackendAuthRemoteDataSource): BackendAuthRemoteDataSource = value
    @Provides @Singleton
    fun backendAuth(remote: BackendAuthRemoteDataSource, store: SessionStore): BackendAuthGateway = BackendAuthRepository(remote, store)
}

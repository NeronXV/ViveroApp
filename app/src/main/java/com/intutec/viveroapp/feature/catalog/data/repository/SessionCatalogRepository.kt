package com.intutec.viveroapp.feature.catalog.data.repository

import com.intutec.viveroapp.core.session.SessionMode
import com.intutec.viveroapp.core.session.SessionStore
import com.intutec.viveroapp.feature.catalog.domain.model.CatalogSnapshot
import com.intutec.viveroapp.feature.catalog.domain.model.Product
import com.intutec.viveroapp.feature.catalog.domain.repository.CatalogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class SessionCatalogRepository @Inject constructor(
    private val sessionStore: SessionStore,
    private val remote: SupabaseCatalogRepository,
    private val demo: FakeCatalogRepository,
) : CatalogRepository {
    override fun observeCatalog(): Flow<CatalogSnapshot> = flow {
        emitAll(repositoryForCurrentSession().observeCatalog())
    }

    override suspend fun getProduct(productId: String): Result<Product> =
        repositoryForCurrentSession().getProduct(productId)

    override suspend fun findProductByCode(code: String): Result<Product?> =
        repositoryForCurrentSession().findProductByCode(code)

    private fun repositoryForCurrentSession(): CatalogRepository = when (sessionStore.session.value?.mode) {
        SessionMode.REMOTE -> remote
        SessionMode.DEMO -> demo
        null -> error("No hay una sesión activa para cargar el catálogo.")
    }
}

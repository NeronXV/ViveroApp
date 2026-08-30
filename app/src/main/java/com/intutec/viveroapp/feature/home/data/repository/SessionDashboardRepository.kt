package com.intutec.viveroapp.feature.home.data.repository

import com.intutec.viveroapp.core.security.AppPermission
import com.intutec.viveroapp.core.session.SessionStore
import com.intutec.viveroapp.feature.home.domain.model.Dashboard
import com.intutec.viveroapp.feature.home.domain.model.DashboardModule
import com.intutec.viveroapp.feature.home.domain.repository.DashboardRepository
import javax.inject.Inject

class SessionDashboardRepository @Inject constructor(
    private val sessionStore: SessionStore,
) : DashboardRepository {
    override suspend fun getDashboard(): Result<Dashboard> = runCatching {
        val session = checkNotNull(sessionStore.session.value) { "La sesión ya no está disponible." }
        val canCreateSales = session.canOperateAtBranch(AppPermission.CREATE_SALES)
        Dashboard(
            userName = session.fullName,
            role = session.role,
            branchName = session.branchName,
            sessionMode = session.mode,
            canCreateSales = canCreateSales,
            modules = buildList {
                if (session.hasCapability(AppPermission.VIEW_CATALOG)) {
                    add(DashboardModule("catalog", "Catálogo", "Consulta productos y categorías", true))
                }
                if (canCreateSales) {
                    add(DashboardModule("cart", "Carrito actual", "Retoma la comanda guardada", true))
                }
                if (session.canOperateAtBranch(AppPermission.OPERATE_CASHIER)) {
                    add(DashboardModule("cashier", "Caja", "Atiende comandas de esta sucursal", true))
                }
                if (session.canOperateAtBranch(AppPermission.MANAGE_INVENTORY)) {
                    add(DashboardModule("inventory", "Inventario", "Recibe mercancía y concilia conteos", true))
                }
                if (session.hasCapability(AppPermission.VIEW_REPORTS)) {
                    add(DashboardModule("reports", "Reportes", "Analiza ventas y productos", true))
                }
                if (session.hasCapability(AppPermission.VIEW_OWN_SALES)) {
                    add(DashboardModule("mysales", "Mis comandas", "Consulta tus ventas recientes", true))
                }
            },
        )
    }
}

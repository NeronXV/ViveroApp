package com.intutec.viveroapp.feature.home.data.repository

import com.intutec.viveroapp.core.model.UserRole
import com.intutec.viveroapp.core.security.AppPermission
import com.intutec.viveroapp.core.session.SessionStore
import com.intutec.viveroapp.core.session.UserSession
import com.intutec.viveroapp.feature.home.domain.model.Dashboard
import com.intutec.viveroapp.feature.home.domain.model.DashboardModule
import com.intutec.viveroapp.feature.home.domain.repository.DashboardRepository
import kotlinx.coroutines.delay
import javax.inject.Inject

class FakeDashboardRepository @Inject constructor(
    private val sessionStore: SessionStore,
) : DashboardRepository {
    override suspend fun getDashboard(): Result<Dashboard> = runCatching {
        delay(350)
        val session = checkNotNull(sessionStore.session.value) { "La sesión ya no está disponible." }
        Dashboard(
            userName = session.fullName,
            role = session.role,
            branchName = session.branchName,
            pendingTickets = 3,
            lowStockProducts = 7,
            activePromotions = 2,
            modules = modulesFor(session),
        )
    }

    private fun modulesFor(session: UserSession): List<DashboardModule> = when (session.role) {
        UserRole.SALES -> listOf(
            module("catalog", "Catálogo", "Consulta plantas y existencias", session.hasCapability(AppPermission.VIEW_CATALOG)),
            module("scanner", "Escáner", "Identifica plantas por código"),
            module("cart", "Carrito actual", "Prepara una nueva venta", session.canOperateAtBranch(AppPermission.CREATE_SALES)),
            module("tickets", "Mis tickets", "Revisa órdenes enviadas"),
        )
        UserRole.CASHIER -> listOf(
            module("cashier", "Inicio de caja", "Abre y controla tu turno"),
            module("pending", "Tickets pendientes", "Órdenes listas para cobrar"),
            module("shift_sales", "Ventas del turno", "Consulta tu actividad"),
        )
        UserRole.INVENTORY -> listOf(
            module("inventory", "Existencias", "Stock por producto y ubicación"),
            module("scanner", "Escáner", "Localiza un producto"),
            module("movement", "Nuevo movimiento", "Registra entradas y salidas"),
            module("alerts", "Alertas de stock", "Productos bajo el mínimo"),
        )
        UserRole.MANAGER -> listOf(
            module("reports", "Resumen", "Indicadores de la operación"),
            module("sales", "Ventas", "Desempeño por periodo"),
            module("inventory", "Inventario", "Existencias y movimientos"),
            module("promotions", "Promociones", "Campañas activas y programadas"),
        )
        UserRole.ADMIN -> listOf(
            module("reports", "Resumen", "Estado general del vivero"),
            module("users", "Usuarios", "Roles, accesos y sucursales"),
            module("inventory", "Inventario", "Control operativo"),
            module("settings", "Configuración", "Preferencias del sistema"),
        )
        UserRole.OWNER -> listOf(
            module("catalog", "Catálogo", "Consulta productos y categorías", session.hasCapability(AppPermission.VIEW_CATALOG)),
            module("reports", "Resumen ejecutivo", "Indicadores generales"),
            module("sales", "Ventas", "Resultados y tendencias"),
            module("inventory", "Inventario", "Valor y disponibilidad"),
            module("promotions", "Promociones", "Resultados de campañas"),
        )
    }

    private fun module(id: String, title: String, description: String, enabled: Boolean = false) =
        DashboardModule(id, title, description, enabled)
}

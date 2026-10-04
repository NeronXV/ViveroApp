package com.intutec.viveroapp.feature.home.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.intutec.viveroapp.core.session.*
import com.intutec.viveroapp.feature.cart.presentation.BackendCartViewModel
import com.intutec.viveroapp.feature.home.domain.model.Dashboard
import com.intutec.viveroapp.feature.home.domain.model.DashboardModule
import com.intutec.viveroapp.feature.inventory.domain.repository.inventorySession
import com.intutec.viveroapp.feature.auth.presentation.BackendPasswordChangeState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation

internal fun backendDashboard(session: BackendSessionState, webAvailable: Boolean): Dashboard? {
    val context = session.context ?: return null
    val identity = session.session ?: return null
    if (session.status != BackendAuthStatus.AUTHENTICATED || session.accessStatus != BackendAccessStatus.READY ||
        identity.expiresAtMillis <= System.currentTimeMillis() || identity.userId != context.user.id ||
        context.accessState != "ACTIVE") return null
    val role = context.role?.name ?: return null
    val selling = session.authorizedSession("CREATE_SALES", true) != null
    val modules = buildList {
        if (session.authorizedSession("VIEW_CATALOG") != null)
            add(DashboardModule("catalog", "Catálogo", "Explora nuestra colección botánica", true))
        if (selling) add(DashboardModule("cart", "Carrito actual", "Retoma tu comanda guardada", true))
        if (selling && session.authorizedSession("VIEW_OWN_SALES", true) != null)
            add(DashboardModule("mysales", "Mis ventas", "Folios y seguimiento de tus ventas", true))
        if (session.authorizedSession("OPERATE_CASHIER", true) != null)
            add(DashboardModule("cashier", "Caja", "Comandas, pagos y comprobantes", true))
        if (session.inventorySession() != null)
            add(DashboardModule("inventory", "Inventario", "Existencias de tu sucursal", true))
        if (webAvailable && session.authorizedSession("VIEW_REPORTS", true) != null)
            add(DashboardModule("reports", "Reportes", "Consulta el portal de administración", true))
        if (webAvailable && session.authorizedSession("MANAGE_PRODUCTS", true) != null)
            add(DashboardModule("products", "Productos", "Gestiona el catálogo desde el portal", true))
        if (webAvailable && session.authorizedSession("MANAGE_USERS", true) != null)
            add(DashboardModule("staff", "Equipo", "Accesos y personal en el portal", true))
    }
    return Dashboard(context.user.fullName, role, context.branch?.name ?: "Sin sucursal",
        SessionMode.REMOTE, selling, modules)
}

@Composable
fun BackendHomeScreen(
    session: BackendSessionState,
    onCatalog: () -> Unit, onCart: () -> Unit, onCashier: () -> Unit,
    onInventory: () -> Unit, onHistory: () -> Unit, onWeb: () -> Unit,
    webAvailable: Boolean, onRefresh: () -> Unit, onSignOut: () -> Unit,
    passwordChange: BackendPasswordChangeState,
    onChangePassword: (String, String) -> Unit,
    onClearPasswordChange: () -> Unit,
    cartViewModel: BackendCartViewModel = hiltViewModel(),
) {
    val cartState by cartViewModel.state.collectAsStateWithLifecycle()
    var profile by remember { mutableStateOf(false) }
    var changingPassword by remember { mutableStateOf(false) }
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    fun clearFields() { current = ""; next = ""; confirmation = "" }
    LaunchedEffect(session.session?.userId) { clearFields(); changingPassword = false }
    val dashboard = backendDashboard(session, webAvailable)
    if (dashboard != null) {
        val lines = cartState.cart?.items.orEmpty()
        HomeDashboard(dashboard, HomeCartSummary(lines.sumOf { it.quantity },
            lines.fold(0L) { total, item -> Math.addExact(total, Math.multiplyExact(item.priceCents, item.quantity.toLong())) }),
            onCatalog, onCart, onCashier, onInventory, onWeb, onHistory,
            { profile = true }, onWeb, onWeb)
    } else {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Tu espacio de trabajo", style = MaterialTheme.typography.headlineMedium)
            Text("Necesitas un rol activo para ver tus módulos. Tus datos guardados se conservan.")
            Button(onRefresh) { Text("Actualizar permisos") }
            TextButton(onSignOut) { Text("Cerrar sesión") }
        }
    }
    if (profile) AlertDialog(onDismissRequest = { profile = false },
        title = { Text("Mi cuenta") }, text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(session.context?.user?.fullName.orEmpty(), style = MaterialTheme.typography.titleLarge)
            Text("${session.context?.role?.displayName ?: "Sin rol"} · ${session.context?.branch?.name ?: "Sin sucursal"}")
            Button({ profile = false; clearFields(); onClearPasswordChange(); changingPassword = true }) { Text("Cambiar contraseña") }
            OutlinedButton(onWeb, enabled = webAvailable) { Text("Abrir portal Web") }
            TextButton({ profile = false; onRefresh() }) { Text("Actualizar permisos") }
            TextButton({ profile = false; onSignOut() }) { Text("Cerrar sesión") }
        } }, confirmButton = { TextButton({ profile = false }) { Text("Volver") } })
    if (changingPassword) {
        val valid = current.codePointCount(0, current.length) in 1..128 &&
            next.codePointCount(0, next.length) in 6..128 && next == confirmation && next != current &&
            !current.contains('\u0000') && !next.contains('\u0000') && !next.startsWith("replace-with-")
        val close = { if (!passwordChange.working) { clearFields(); onClearPasswordChange(); changingPassword = false } }
        AlertDialog(onDismissRequest = close, title = { Text("Cambiar contraseña") },
            text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Usa una contraseña de 6 a 128 caracteres. Al guardar se cerrarán las sesiones de tu cuenta.")
                listOf("Contraseña actual" to current, "Nueva contraseña" to next, "Confirmar nueva contraseña" to confirmation).forEachIndexed { index, (label, value) ->
                    OutlinedTextField(value, { updated ->
                        if (updated.codePointCount(0, updated.length) <= 128) when (index) { 0 -> current = updated; 1 -> next = updated; else -> confirmation = updated }
                    }, label = { Text(label) }, singleLine = true, enabled = !passwordChange.working,
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                }
                if (confirmation.isNotEmpty() && next != confirmation) Text("Las contraseñas no coinciden.", color = MaterialTheme.colorScheme.error)
                passwordChange.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (passwordChange.working) LinearProgressIndicator(Modifier.fillMaxWidth())
            } }, confirmButton = { TextButton({ onChangePassword(current, next); clearFields() }, enabled = valid && !passwordChange.working) { Text("Guardar contraseña") } },
            dismissButton = { TextButton(close, enabled = !passwordChange.working) { Text("Cancelar") } })
    }
}

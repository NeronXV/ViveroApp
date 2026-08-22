package com.intutec.viveroapp.feature.home.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.LocalFlorist
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.intutec.viveroapp.core.common.UiState
import com.intutec.viveroapp.core.common.asMxn
import com.intutec.viveroapp.core.designsystem.DulcineaWordmark
import com.intutec.viveroapp.core.model.UserRole
import com.intutec.viveroapp.core.session.SessionMode
import com.intutec.viveroapp.feature.cart.domain.model.Cart
import com.intutec.viveroapp.feature.cart.domain.model.CartItem
import com.intutec.viveroapp.feature.home.domain.model.Dashboard
import com.intutec.viveroapp.feature.home.domain.model.DashboardModule
import com.intutec.viveroapp.ui.theme.ViveroAppTheme
import java.time.Instant

private val Forest = Color(0xFF234D3C)
private val Sage = Color(0xFF789B78)
private val PaleLeaf = Color(0xFFDDE9DB)
private val Cream = Color(0xFFF7F2E8)
private val Terracotta = Color(0xFFC97754)
private val TerracottaStrong = Color(0xFFA84F32)
private val Ink = Color(0xFF24312B)
private val Paper = Color(0xFFFFFCF6)
private val Muted = Color(0xFF5F6F67)

@Composable
fun HomeScreenRoute(
    onCatalogClick: () -> Unit,
    onCartClick: () -> Unit,
    onProfileClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        state = state,
        onRetry = viewModel::retry,
        onCatalogClick = onCatalogClick,
        onCartClick = onCartClick,
        onProfileClick = onProfileClick,
    )
}

internal fun onHomeModuleClick(
    module: DashboardModule,
    onCatalogClick: () -> Unit,
    onCartClick: () -> Unit,
) {
    when (module.id) {
        "catalog" -> onCatalogClick()
        "cart" -> onCartClick()
    }
}

@Composable
fun HomeScreen(
    state: UiState<HomeContent>,
    onRetry: () -> Unit,
    onCatalogClick: () -> Unit,
    onCartClick: () -> Unit,
    onProfileClick: () -> Unit,
) {
    Scaffold(containerColor = Cream) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                UiState.Loading -> LoadingContent()
                is UiState.Empty -> MessageContent(state.message, onRetry)
                is UiState.Error -> MessageContent(state.message, onRetry)
                is UiState.Success -> DashboardContent(
                    content = state.data,
                    onCatalogClick = onCatalogClick,
                    onCartClick = onCartClick,
                    onProfileClick = onProfileClick,
                )
            }
        }
    }
}

@Composable
private fun DashboardContent(
    content: HomeContent,
    onCatalogClick: () -> Unit,
    onCartClick: () -> Unit,
    onProfileClick: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val expanded = maxWidth >= 720.dp
        val horizontalPadding = if (expanded) 32.dp else 16.dp
        val sectionSpacing = if (expanded) 26.dp else 16.dp
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = horizontalPadding,
                end = horizontalPadding,
                top = if (expanded) 24.dp else 10.dp,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(sectionSpacing),
        ) {
            item { HomeHeader(content.dashboard, expanded, onProfileClick) }
            if (content.dashboard.canCreateSales) {
                item {
                    PrimarySaleAction(
                        cart = content.cart,
                        expanded = expanded,
                        onCatalogClick = onCatalogClick,
                        onCartClick = onCartClick,
                    )
                }
            }
            if (content.dashboard.modules.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(if (expanded) 16.dp else 10.dp)) {
                        SectionTitle("Accesos rápidos", "Funciones disponibles")
                        QuickAccesses(
                            modules = content.dashboard.modules,
                            cart = content.cart,
                            expanded = expanded,
                            onModuleClick = { module -> onHomeModuleClick(module, onCatalogClick, onCartClick) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(dashboard: Dashboard, expanded: Boolean, onProfileClick: () -> Unit) {
    if (expanded) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.weight(1f),
            ) {
                BrandLockup(compact = false)
                Greeting(dashboard, expanded = true, modifier = Modifier.weight(1f))
            }
            HeaderStatus(dashboard.sessionMode, onProfileClick, showProfile = true)
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                BrandLockup(compact = true)
                IconButton(onClick = onProfileClick, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.AccountCircle, "Abrir perfil", tint = Forest)
                }
            }
            Greeting(dashboard, expanded = false)
            HeaderStatus(dashboard.sessionMode, onProfileClick, showProfile = false)
        }
    }
}

@Composable
private fun BrandLockup(compact: Boolean) {
    Surface(shape = RoundedCornerShape(if (compact) 15.dp else 18.dp), color = Paper) {
        Box(
            modifier = Modifier
                .width(if (compact) 138.dp else 170.dp)
                .height(if (compact) 46.dp else 58.dp),
            contentAlignment = Alignment.Center,
        ) {
            DulcineaWordmark(Modifier.scale(if (compact) .84f else 1f))
        }
    }
}

@Composable
private fun Greeting(dashboard: Dashboard, expanded: Boolean, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text("Buen día,", style = MaterialTheme.typography.bodyMedium, color = Muted)
        Text(
            dashboard.userName,
            style = if (expanded) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
            color = Forest,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "${dashboard.role.displayName}  ·  ${dashboard.branchName}",
            style = MaterialTheme.typography.labelMedium,
            color = Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HeaderStatus(sessionMode: SessionMode, onProfileClick: () -> Unit, showProfile: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(color = PaleLeaf, shape = CircleShape) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Icon(Icons.Outlined.VerifiedUser, null, Modifier.size(16.dp), tint = Forest)
                Text(
                    if (sessionMode == SessionMode.REMOTE) "Sesión remota activa" else "Modo demo activo",
                    style = MaterialTheme.typography.labelMedium,
                    color = Forest,
                )
            }
        }
        if (showProfile) {
            IconButton(onClick = onProfileClick, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Outlined.AccountCircle, "Abrir perfil", tint = Forest)
            }
        }
    }
}

@Composable
private fun PrimarySaleAction(
    cart: Cart,
    expanded: Boolean,
    onCatalogClick: () -> Unit,
    onCartClick: () -> Unit,
) {
    val hasItems = cart.items.isNotEmpty()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (expanded) 190.dp else 174.dp)
            .testTag("home_primary_action"),
        color = if (hasItems) Color(0xFF315B49) else Forest,
        contentColor = Color.White,
        shape = RoundedCornerShape(if (expanded) 28.dp else 24.dp),
        shadowElevation = 2.dp,
    ) {
        Box {
            OrganicLeaves(Modifier.matchParentSize())
            Column(
                modifier = Modifier.padding(if (expanded) 26.dp else 20.dp),
                verticalArrangement = Arrangement.spacedBy(if (expanded) 12.dp else 9.dp),
            ) {
                Text(
                    if (hasItems) "Venta en curso" else "Punto de venta",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = .76f),
                )
                Text(
                    if (hasItems) "Continuar venta" else "Nueva venta",
                    style = if (expanded) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (hasItems) {
                        "${cart.itemCount} ${if (cart.itemCount == 1) "artículo" else "artículos"}  ·  ${cart.totalCents.asMxn()}"
                    } else {
                        "Inicia una comanda con el catálogo de la sucursal."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = .84f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Button(
                    onClick = if (hasItems) onCartClick else onCatalogClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (hasItems) TerracottaStrong else PaleLeaf,
                        contentColor = if (hasItems) Color.White else Forest,
                    ),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Icon(if (hasItems) Icons.Outlined.ShoppingCart else Icons.Outlined.PointOfSale, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (hasItems) "Abrir carrito" else "Comenzar")
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun OrganicLeaves(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawCircle(Color.White.copy(alpha = .06f), size.minDimension * .42f, Offset(size.width * .94f, size.height * .1f))
        drawOval(
            color = Sage.copy(alpha = .22f),
            topLeft = Offset(size.width * .68f, size.height * .58f),
            size = Size(size.width * .38f, size.height * .34f),
        )
        drawOval(
            color = Terracotta.copy(alpha = .18f),
            topLeft = Offset(size.width * .82f, size.height * .32f),
            size = Size(size.width * .22f, size.height * .24f),
        )
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = Forest)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}

@Composable
private fun QuickAccesses(
    modules: List<DashboardModule>,
    cart: Cart,
    expanded: Boolean,
    onModuleClick: (DashboardModule) -> Unit,
) {
    GridRows(modules, if (expanded) 2 else 1, 12.dp) { module ->
        QuickAccessCard(
            module = module,
            description = when (module.id) {
                "cart" -> if (cart.items.isEmpty()) {
                    "Sin productos agregados"
                } else {
                    "${cart.itemCount} ${if (cart.itemCount == 1) "artículo" else "artículos"} · ${cart.totalCents.asMxn()}"
                }
                else -> module.description
            },
            onClick = { onModuleClick(module) },
        )
    }
}

@Composable
private fun QuickAccessCard(module: DashboardModule, description: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 116.dp)
            .testTag("home_module_${module.id}"),
        colors = CardDefaults.cardColors(containerColor = Paper),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, PaleLeaf),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(color = PaleLeaf, shape = RoundedCornerShape(14.dp)) {
                Icon(module.icon(), null, Modifier.padding(10.dp).size(24.dp), tint = Forest)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(module.title, style = MaterialTheme.typography.titleMedium, color = Ink, maxLines = 1)
                Text(description, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 2)
            }
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(20.dp), tint = Forest)
        }
    }
}

private fun DashboardModule.icon(): ImageVector = when (id) {
    "catalog" -> Icons.Outlined.LocalFlorist
    "cart" -> Icons.Outlined.ShoppingCart
    else -> Icons.Outlined.LocalFlorist
}

@Composable
private fun <T> GridRows(items: List<T>, columns: Int, gap: Dp, content: @Composable (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.testTag("home_quick_accesses")) {
        items.chunked(columns).forEach { rowItems ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                rowItems.forEach { item -> Box(Modifier.weight(1f)) { content(item) } }
                repeat(columns - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(color = Forest)
        Text("Preparando tu espacio de trabajo…", color = Ink)
    }
}

@Composable
private fun MessageContent(message: String, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(24.dp),
    ) {
        Text(message, style = MaterialTheme.typography.titleMedium, color = Ink)
        Button(onClick = onRetry) { Text("Reintentar") }
    }
}

private fun previewDashboard(
    role: UserRole,
    canCreateSales: Boolean,
    userName: String = "Nombre disponible",
) = Dashboard(
    userName = userName,
    role = role,
    branchName = "Sucursal Centro",
    sessionMode = SessionMode.REMOTE,
    canCreateSales = canCreateSales,
    modules = buildList {
        add(DashboardModule("catalog", "Catálogo", "Consulta productos y categorías", true))
        if (canCreateSales) add(DashboardModule("cart", "Carrito actual", "Retoma la comanda guardada", true))
    },
)

private fun previewCart(hasItems: Boolean) = if (!hasItems) {
    Cart(updatedAt = Instant.EPOCH)
} else {
    Cart(
        items = listOf(
            CartItem(
                productId = "00000000-0000-4000-8000-000000000001",
                internalCode = "PREVIEW-001",
                name = "Producto de preview",
                imageKey = "",
                unit = "pieza",
                listPriceCents = 10_000,
                unitPriceCents = 10_000,
                quantity = 2,
                stockAvailable = 0,
                stockKnown = false,
            ),
        ),
        updatedAt = Instant.EPOCH,
    )
}

@Composable
private fun HomePreview(dashboard: Dashboard, cart: Cart) {
    ViveroAppTheme(darkTheme = false) {
        HomeScreen(
            state = UiState.Success(HomeContent(dashboard, cart)),
            onRetry = {},
            onCatalogClick = {},
            onCartClick = {},
            onProfileClick = {},
        )
    }
}

@Preview(name = "OWNER sin carrito", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun OwnerEmptyPreview() = HomePreview(previewDashboard(UserRole.OWNER, true), previewCart(false))

@Preview(name = "OWNER con carrito", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun OwnerCartPreview() = HomePreview(previewDashboard(UserRole.OWNER, true), previewCart(true))

@Preview(name = "SALES con carrito", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun SalesCartPreview() = HomePreview(previewDashboard(UserRole.SALES, true), previewCart(true))

@Preview(name = "Sin CREATE_SALES", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun NoSalesPreview() = HomePreview(previewDashboard(UserRole.CASHIER, false), previewCart(false))

@Preview(name = "Nombre extenso", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun LongNamePreview() = HomePreview(
    previewDashboard(UserRole.OWNER, true, "Nombre compuesto considerablemente extenso"),
    previewCart(false),
)

@Preview(name = "Tablet vertical", showBackground = true, widthDp = 800, heightDp = 1280)
@Composable private fun TabletPortraitPreview() = HomePreview(previewDashboard(UserRole.OWNER, true), previewCart(false))

@Preview(name = "Tablet horizontal", showBackground = true, widthDp = 1280, heightDp = 800)
@Composable private fun TabletLandscapePreview() = HomePreview(previewDashboard(UserRole.OWNER, true), previewCart(false))

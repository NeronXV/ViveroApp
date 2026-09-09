package com.intutec.viveroapp.feature.staff.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.intutec.viveroapp.core.model.UserRole
import com.intutec.viveroapp.core.designsystem.StatusPill
import com.intutec.viveroapp.core.designsystem.ViveroSectionIntro
import com.intutec.viveroapp.core.designsystem.ViveroTopAppBar
import com.intutec.viveroapp.feature.staff.domain.model.StaffBranch
import com.intutec.viveroapp.feature.staff.domain.model.StaffMember

@Composable
fun StaffScreenRoute(
    onBack: () -> Unit,
    viewModel: StaffViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    StaffScreen(
        state = state,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onRole = viewModel::assignRole,
        onBranch = viewModel::assignBranch,
        onActive = viewModel::setActive,
        onMessageShown = viewModel::clearMessage,
    )
}

@Composable
private fun StaffScreen(
    state: StaffUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRole: (StaffMember, UserRole) -> Unit,
    onBranch: (StaffMember, StaffBranch) -> Unit,
    onActive: (StaffMember, Boolean) -> Unit,
    onMessageShown: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    var roleTarget by remember { mutableStateOf<StaffMember?>(null) }
    var branchTarget by remember { mutableStateOf<StaffMember?>(null) }
    var activeTarget by remember { mutableStateOf<StaffMember?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); onMessageShown() }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            ViveroTopAppBar(title = "Personal", onBack = onBack, eyebrow = "EQUIPO Y ACCESOS") {
                TextButton(onClick = onRefresh, enabled = !state.operationInProgress) { Text("Actualizar") }
            }
        },
    ) { padding ->
        when {
            state.loading && state.members.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { CircularProgressIndicator(); Text("Cargando personal…", Modifier.padding(top = 12.dp)) }
            state.error != null && state.members.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { Text(state.error); Button(onClick = onRefresh, modifier = Modifier.padding(top = 12.dp)) { Text("Reintentar") } }
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    ViveroSectionIntro(
                        title = "Tu equipo",
                        subtitle = "Administra los roles, sucursales y accesos de cada integrante.",
                        eyebrow = "PERSONAL AUTORIZADO",
                    )
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
                }
                items(state.members, key = StaffMember::id) { member ->
                    StaffCard(
                        member = member,
                        busy = state.operationInProgress,
                        canAssignRoles = state.canAssignRoles,
                        canManageTarget = state.actorRole == UserRole.OWNER || member.role != UserRole.OWNER,
                        onRole = { roleTarget = member },
                        onBranch = { branchTarget = member },
                        onActive = { activeTarget = member },
                    )
                }
            }
        }
    }

    roleTarget?.let { member -> ChoiceDialog(
        title = "Rol de ${member.fullName}",
        choices = UserRole.entries.filter { state.actorRole == UserRole.OWNER || it != UserRole.OWNER }.map { it.displayName to it },
        onDismiss = { roleTarget = null },
        onSelect = { roleTarget = null; onRole(member, it) },
    ) }
    branchTarget?.let { member -> ChoiceDialog(
        title = "Sucursal de ${member.fullName}",
        choices = state.branches.map { "${it.name} (${it.code})" to it },
        onDismiss = { branchTarget = null },
        onSelect = { branchTarget = null; onBranch(member, it) },
    ) }
    activeTarget?.let { member -> AlertDialog(
        onDismissRequest = { activeTarget = null },
        title = { Text(if (member.isActive) "Desactivar personal" else "Reactivar personal") },
        text = { Text("¿Confirmas el cambio para ${member.fullName}?") },
        confirmButton = { TextButton(onClick = { activeTarget = null; onActive(member, !member.isActive) }) { Text("Confirmar") } },
        dismissButton = { TextButton(onClick = { activeTarget = null }) { Text("Cancelar") } },
    ) }
}

@Composable
private fun StaffCard(
    member: StaffMember,
    busy: Boolean,
    canAssignRoles: Boolean,
    canManageTarget: Boolean,
    onRole: () -> Unit,
    onBranch: () -> Unit,
    onActive: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(44.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        member.fullName.staffInitials(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(member.fullName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${member.role?.displayName ?: "Sin rol"} · ${member.branch?.name ?: "Sin sucursal"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusPill(
                    text = if (member.isActive) "Activo" else "Inactivo",
                    containerColor = if (member.isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                    contentColor = if (member.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
            if (canManageTarget) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canAssignRoles) OutlinedButton(onClick = onRole, enabled = !busy && member.isActive, modifier = Modifier.weight(1f)) { Text("Rol") }
                OutlinedButton(onClick = onBranch, enabled = !busy && member.isActive, modifier = Modifier.weight(1f)) { Text("Sucursal") }
                OutlinedButton(onClick = onActive, enabled = !busy, modifier = Modifier.weight(1f)) { Text(if (member.isActive) "Desactivar" else "Reactivar") }
            }
        }
    }
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    choices: List<Pair<String, T>>,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = MaterialTheme.colorScheme.primary) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(choices) { (label, value) -> TextButton(onClick = { onSelect(value) }, modifier = Modifier.fillMaxWidth()) { Text(label) } }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

private fun String.staffInitials(): String = trim()
    .split(Regex("\\s+"))
    .take(2)
    .mapNotNull { it.firstOrNull()?.uppercase() }
    .joinToString("")

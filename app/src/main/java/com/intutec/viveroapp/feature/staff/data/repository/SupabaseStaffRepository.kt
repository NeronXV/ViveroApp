package com.intutec.viveroapp.feature.staff.data.repository

import com.intutec.viveroapp.core.model.UserRole
import com.intutec.viveroapp.core.network.SupabaseProvider
import com.intutec.viveroapp.feature.staff.data.remote.RemoteAdminBranchDto
import com.intutec.viveroapp.feature.staff.data.remote.RemoteBranchPageDto
import com.intutec.viveroapp.feature.staff.data.remote.RemoteStaffBranchDto
import com.intutec.viveroapp.feature.staff.data.remote.RemoteStaffMemberDto
import com.intutec.viveroapp.feature.staff.data.remote.RemoteStaffPageDto
import com.intutec.viveroapp.feature.staff.domain.model.StaffBranch
import com.intutec.viveroapp.feature.staff.domain.model.StaffDirectory
import com.intutec.viveroapp.feature.staff.domain.model.StaffMember
import com.intutec.viveroapp.feature.staff.domain.repository.StaffRepository
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.UUID
import javax.inject.Inject

class SupabaseStaffRepository @Inject constructor(
    private val supabaseProvider: SupabaseProvider,
) : StaffRepository {
    override suspend fun getDirectory(): Result<StaffDirectory> = staffCall {
        StaffDirectory(loadAllStaff(), loadAllBranches().filter(StaffBranch::isActive))
    }

    override suspend fun assignRole(userId: String, role: UserRole): Result<Unit> = staffCall {
        rpcVoid("assign_user_role", JsonObject(mapOf(
            "p_user_id" to JsonPrimitive(userId.requireUuid()),
            "p_role_name" to JsonPrimitive(role.name),
        )))
    }

    override suspend fun assignBranch(userId: String, branchId: String): Result<Unit> = staffCall {
        rpcVoid("assign_user_branch", JsonObject(mapOf(
            "p_user_id" to JsonPrimitive(userId.requireUuid()),
            "p_branch_id" to JsonPrimitive(branchId.requireUuid()),
        )))
    }

    override suspend fun setActive(userId: String, active: Boolean): Result<Unit> = staffCall {
        rpcVoid("set_user_active", JsonObject(mapOf(
            "p_user_id" to JsonPrimitive(userId.requireUuid()),
            "p_is_active" to JsonPrimitive(active),
        )))
    }

    private suspend fun loadAllStaff(): List<StaffMember> {
        val result = mutableListOf<StaffMember>()
        var afterName: String? = null
        var afterId: String? = null
        do {
            val page: RemoteStaffPageDto = rpc("get_admin_staff", JsonObject(mapOf(
                "p_limit" to JsonPrimitive(PAGE_SIZE),
                "p_after_full_name" to afterName.jsonValue(),
                "p_after_id" to afterId.jsonValue(),
                "p_search" to JsonNull,
                "p_branch_id" to JsonNull,
                "p_include_inactive" to JsonPrimitive(true),
            )))
            requireVersion(page.schemaVersion)
            result += page.items.map(RemoteStaffMemberDto::toDomain)
            val cursor = page.page.nextCursor
            check(!page.page.hasMore || cursor != null) { "Personal devolvió una página incompleta." }
            afterName = cursor?.fullName
            afterId = cursor?.id
        } while (page.page.hasMore)
        check(result.map(StaffMember::id).distinct().size == result.size) { "Personal devolvió usuarios duplicados." }
        return result
    }

    private suspend fun loadAllBranches(): List<StaffBranch> {
        val result = mutableListOf<StaffBranch>()
        var afterCode: String? = null
        var afterId: String? = null
        do {
            val page: RemoteBranchPageDto = rpc("get_admin_branches", JsonObject(mapOf(
                "p_limit" to JsonPrimitive(PAGE_SIZE),
                "p_after_code" to afterCode.jsonValue(),
                "p_after_id" to afterId.jsonValue(),
                "p_include_inactive" to JsonPrimitive(true),
            )))
            requireVersion(page.schemaVersion)
            result += page.items.map(RemoteAdminBranchDto::toDomain)
            val cursor = page.page.nextCursor
            check(!page.page.hasMore || cursor != null) { "Sucursales devolvió una página incompleta." }
            afterCode = cursor?.code
            afterId = cursor?.id
        } while (page.page.hasMore)
        return result
    }

    private suspend inline fun <reified T> rpc(function: String, parameters: JsonObject): T {
        val client = checkNotNull(supabaseProvider.client) { "Supabase no está configurado para Personal." }
        return json.decodeFromString(client.postgrest.rpc(function, parameters).data)
    }

    private suspend fun rpcVoid(function: String, parameters: JsonObject) {
        val client = checkNotNull(supabaseProvider.client) { "Supabase no está configurado para Personal." }
        client.postgrest.rpc(function, parameters)
    }

    private companion object {
        const val PAGE_SIZE = 100
        val json = Json { ignoreUnknownKeys = true }
    }
}

private suspend inline fun <T> staffCall(crossinline block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (error: CancellationException) {
    throw error
} catch (error: PostgrestRestException) {
    Result.failure(IllegalStateException(error.staffMessage()))
} catch (error: IllegalArgumentException) {
    Result.failure(error)
} catch (_: Throwable) {
    Result.failure(IllegalStateException("No pudimos completar la operación de personal. Intenta nuevamente."))
}

private fun PostgrestRestException.staffMessage(): String = when (message) {
    "ADMIN cannot grant or modify OWNER" -> "Un administrador no puede modificar propietarios."
    "The last OWNER cannot be reassigned" -> "No se puede reasignar al último propietario."
    "ADMIN cannot modify OWNER status" -> "Un administrador no puede cambiar el estado de un propietario."
    "The last active OWNER cannot be deactivated" -> "No se puede desactivar al último propietario activo."
    else -> if (code == "42501" || code == "P0001") "No tienes permiso para administrar personal." else "No pudimos completar la operación de personal."
}

private fun requireVersion(version: Int) = check(version == 1) { "Personal devolvió una versión no compatible." }
private fun String?.jsonValue() = this?.let(::JsonPrimitive) ?: JsonNull
private fun String.requireUuid(): String = UUID.fromString(this).toString()

private fun RemoteStaffMemberDto.toDomain() = StaffMember(
    id = id.requireUuid(),
    fullName = fullName.trim().also { require(it.isNotEmpty()) },
    isActive = isActive,
    branch = branch?.toDomain(),
    role = role?.name?.let { name -> UserRole.entries.firstOrNull { it.name == name } }
        .also { parsed -> check(role == null || parsed != null) { "Personal devolvió un rol desconocido." } },
)

private fun RemoteStaffBranchDto.toDomain() = StaffBranch(id.requireUuid(), code.trim(), name.trim(), isActive)
private fun RemoteAdminBranchDto.toDomain() = StaffBranch(id.requireUuid(), code.trim(), name.trim(), isActive)

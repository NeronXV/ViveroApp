package com.intutec.viveroapp.core.model

enum class UserRole(val displayName: String) {
    WORKER("Trabajador"),
    CASHIER("Cajero"),
    INVENTORY("Inventario"),
    MANAGER("Gerente"),
    ADMIN("Administrador"),
    OWNER("Propietario"),
}

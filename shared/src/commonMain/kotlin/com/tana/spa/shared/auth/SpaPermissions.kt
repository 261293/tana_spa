package com.tana.spa.shared.auth

enum class SpaPermission {
    MANAGE_BOOKINGS,

    MANAGE_SERVICES,

    MANAGE_THERAPISTS,

    OPERATE_SYSTEM,

    MANAGE_ACCOUNTS,
}

object SpaRoles {
    const val CUSTOMER = "customer"
    const val STAFF = "staff"
    const val OWNER = "owner"
    const val ADMIN = "admin"

    fun permissionsOf(role: String): Set<SpaPermission> = when (role) {
        ADMIN -> SpaPermission.entries.toSet()

        OWNER -> setOf(
            SpaPermission.MANAGE_BOOKINGS,
            SpaPermission.MANAGE_SERVICES,
            SpaPermission.MANAGE_THERAPISTS,
            SpaPermission.MANAGE_ACCOUNTS,
        )

        STAFF -> setOf(SpaPermission.MANAGE_BOOKINGS)

        else -> emptySet()
    }

    fun has(role: String, permission: SpaPermission): Boolean = permission in permissionsOf(role)

    fun creatableBy(role: String): Set<String> = when (role) {
        ADMIN -> setOf(ADMIN, OWNER, STAFF, CUSTOMER)
        OWNER -> setOf(STAFF, CUSTOMER)
        else -> emptySet()
    }

    fun canCreate(role: String, target: String): Boolean = target in creatableBy(role)

    fun canChangeRole(actor: String, from: String, to: String): Boolean = canCreate(actor, from) && canCreate(actor, to)
}

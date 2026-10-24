package com.tana.spa.shared.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpaPermissionsTest {

    @Test
    fun anAdministratorMayDoEverything() {
        assertEquals(SpaPermission.entries.toSet(), SpaRoles.permissionsOf(SpaRoles.ADMIN))
    }

    @Test
    fun theOwnerRunsTheSpaButDoesNotOperateTheSystem() {
        val owner = SpaRoles.permissionsOf(SpaRoles.OWNER)

        assertTrue(SpaPermission.MANAGE_BOOKINGS in owner)
        assertTrue(SpaPermission.MANAGE_SERVICES in owner)
        assertTrue(SpaPermission.MANAGE_THERAPISTS in owner)
        assertTrue(SpaPermission.MANAGE_ACCOUNTS in owner)
        assertFalse(SpaPermission.OPERATE_SYSTEM in owner)
    }

    @Test
    fun staffWorkTheQueueAndNothingThatOutlivesTheShift() {
        assertEquals(setOf(SpaPermission.MANAGE_BOOKINGS), SpaRoles.permissionsOf(SpaRoles.STAFF))
    }

    @Test
    fun aCustomerHasNoPrivilegeAtAll() {
        assertEquals(emptySet(), SpaRoles.permissionsOf(SpaRoles.CUSTOMER))
    }

    @Test
    fun anUnknownRoleIsTreatedAsHavingNothing() {
        assertEquals(emptySet(), SpaRoles.permissionsOf("sysadmin"))
        assertFalse(SpaRoles.has("", SpaPermission.MANAGE_BOOKINGS))
    }

    @Test
    fun theOwnerMayTakeOnStaffButNotMintAnotherOwner() {
        assertTrue(SpaRoles.canCreate(SpaRoles.OWNER, SpaRoles.STAFF))
        assertTrue(SpaRoles.canCreate(SpaRoles.OWNER, SpaRoles.CUSTOMER))
        assertFalse(SpaRoles.canCreate(SpaRoles.OWNER, SpaRoles.OWNER))
        assertFalse(SpaRoles.canCreate(SpaRoles.OWNER, SpaRoles.ADMIN))
    }

    @Test
    fun onlyAnAdministratorMayCreateOwnersAndOtherAdministrators() {
        assertTrue(SpaRoles.canCreate(SpaRoles.ADMIN, SpaRoles.OWNER))
        assertTrue(SpaRoles.canCreate(SpaRoles.ADMIN, SpaRoles.ADMIN))
    }

    @Test
    fun staffAndCustomersMayCreateNobody() {
        assertEquals(emptySet(), SpaRoles.creatableBy(SpaRoles.STAFF))
        assertEquals(emptySet(), SpaRoles.creatableBy(SpaRoles.CUSTOMER))
    }

    @Test
    fun everyCreatableRoleIsOneTheSystemKnows() {
        val known = setOf(SpaRoles.CUSTOMER, SpaRoles.STAFF, SpaRoles.OWNER, SpaRoles.ADMIN)

        for (role in known) {
            assertTrue(SpaRoles.creatableBy(role).all { it in known }, "$role may create an unknown role")
        }
    }

    @Test
    fun theOwnerMovesPeopleBetweenStaffAndCustomer() {
        assertTrue(SpaRoles.canChangeRole(SpaRoles.OWNER, SpaRoles.CUSTOMER, SpaRoles.STAFF))
        assertTrue(SpaRoles.canChangeRole(SpaRoles.OWNER, SpaRoles.STAFF, SpaRoles.CUSTOMER))
    }

    @Test
    fun theOwnerCannotReachAnOwnerOrAnAdministrator() {
        assertFalse(SpaRoles.canChangeRole(SpaRoles.OWNER, SpaRoles.ADMIN, SpaRoles.CUSTOMER))
        assertFalse(SpaRoles.canChangeRole(SpaRoles.OWNER, SpaRoles.OWNER, SpaRoles.STAFF))
        assertFalse(SpaRoles.canChangeRole(SpaRoles.OWNER, SpaRoles.STAFF, SpaRoles.OWNER))
    }

    @Test
    fun anAdministratorMovesAnybodyAnywhere() {
        val known = listOf(SpaRoles.CUSTOMER, SpaRoles.STAFF, SpaRoles.OWNER, SpaRoles.ADMIN)

        for (from in known) {
            for (to in known) {
                assertTrue(SpaRoles.canChangeRole(SpaRoles.ADMIN, from, to), "$from -> $to")
            }
        }
    }

    @Test
    fun staffAndCustomersMoveNobody() {
        assertFalse(SpaRoles.canChangeRole(SpaRoles.STAFF, SpaRoles.CUSTOMER, SpaRoles.STAFF))
        assertFalse(SpaRoles.canChangeRole(SpaRoles.CUSTOMER, SpaRoles.CUSTOMER, SpaRoles.STAFF))
    }

    @Test
    fun onlyAnAdministratorCanUnmakeAnAdministratorWhichIsWhyTheLastOneSurvives() {
        val demoters = listOf(SpaRoles.CUSTOMER, SpaRoles.STAFF, SpaRoles.OWNER)

        for (actor in demoters) {
            assertFalse(SpaRoles.canChangeRole(actor, SpaRoles.ADMIN, SpaRoles.CUSTOMER), actor)
        }
        assertTrue(SpaRoles.canChangeRole(SpaRoles.ADMIN, SpaRoles.ADMIN, SpaRoles.CUSTOMER))
    }
}

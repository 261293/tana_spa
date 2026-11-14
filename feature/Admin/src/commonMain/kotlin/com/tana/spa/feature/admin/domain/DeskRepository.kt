package com.tana.spa.feature.admin.domain

import com.tana.spa.shared.auth.CreateAccountRequest
import com.tana.spa.shared.auth.CreatedAccountDto
import com.tana.spa.shared.booking.CreateProductRequest
import com.tana.spa.shared.booking.CreateServiceRequest
import com.tana.spa.shared.booking.CreateTherapistRequest
import com.tana.spa.shared.booking.DeclineReasonDto
import com.tana.spa.shared.booking.ProductDto
import com.tana.spa.shared.booking.ServiceDto
import com.tana.spa.shared.booking.TherapistDto

/**
 * The owner's side of the booking endpoints. Every call needs an owner session and throws
 * [DeskRefused] when the server says no, so a refusal reaches the screen as a message rather than
 * as a silently unchanged list.
 */
interface DeskRepository {
    suspend fun queue(): DeskQueue

    /**
     * [force] confirms a booking that overlaps another; without it the server refuses the clash.
     * A non-null [therapistSlug] assigns that therapist first.
     */
    suspend fun confirm(id: String, force: Boolean = false, therapistSlug: String? = null): DeskRequest

    /** Moves a waiting or confirmed booking to another therapist, who must be free then. */
    suspend fun assign(id: String, therapistSlug: String): DeskRequest

    suspend fun decline(id: String, reason: DeclineReasonDto, alternative: String?): DeskRequest

    suspend fun recordVisit(id: String, attended: Boolean): DeskRequest

    /** Adds a treatment to the menu. The server generates its slug and guests see it at once. */
    suspend fun createService(request: CreateServiceRequest): ServiceDto

    suspend fun createTherapist(request: CreateTherapistRequest): TherapistDto

    /** Puts a product on the shelf every role sees on Home. */
    suspend fun createProduct(request: CreateProductRequest): ProductDto

    suspend fun team(): List<DeskTherapist>

    /** The signed-in account's own confirmed visits that have not ended, soonest first. */
    suspend fun myShifts(): List<DeskRequest>

    /** Links the account with [email] to the therapist, so their shifts reach it; a null [email] unlinks. */
    suspend fun link(therapistSlug: String, email: String?): DeskTherapist

    suspend fun createAccount(request: CreateAccountRequest): CreatedAccountDto
}

/** [code] is one of `BookingErrorCodes`, or null when the server sent no machine-readable code. */
class DeskRefused(val status: Int, val code: String?) : Exception("The desk call was refused with $status ($code)")

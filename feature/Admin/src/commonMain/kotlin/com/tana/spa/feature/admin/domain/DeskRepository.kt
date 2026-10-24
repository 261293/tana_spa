package com.tana.spa.feature.admin.domain

import com.tana.spa.shared.auth.CreateAccountRequest
import com.tana.spa.shared.auth.CreatedAccountDto
import com.tana.spa.shared.booking.CreateServiceRequest
import com.tana.spa.shared.booking.CreateTherapistRequest
import com.tana.spa.shared.booking.DeclineReasonDto
import com.tana.spa.shared.booking.ServiceDto
import com.tana.spa.shared.booking.TherapistDto

/**
 * The owner's side of the booking endpoints. Every call needs an owner session and throws
 * [DeskRefused] when the server says no, so a refusal reaches the screen as a message rather than
 * as a silently unchanged list.
 */
interface DeskRepository {
    suspend fun queue(): DeskQueue

    /** [force] confirms a booking that overlaps another; without it the server refuses the clash. */
    suspend fun confirm(id: String, force: Boolean = false): DeskRequest

    suspend fun decline(id: String, reason: DeclineReasonDto, alternative: String?): DeskRequest

    suspend fun recordVisit(id: String, attended: Boolean): DeskRequest

    /** Adds a treatment to the menu. The server generates its slug and guests see it at once. */
    suspend fun createService(request: CreateServiceRequest): ServiceDto

    suspend fun createTherapist(request: CreateTherapistRequest): TherapistDto

    suspend fun createAccount(request: CreateAccountRequest): CreatedAccountDto
}

/** [code] is one of `BookingErrorCodes`, or null when the server sent no machine-readable code. */
class DeskRefused(val status: Int, val code: String?) : Exception("The desk call was refused with $status ($code)")

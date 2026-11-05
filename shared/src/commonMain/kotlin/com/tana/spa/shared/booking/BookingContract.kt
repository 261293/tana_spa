package com.tana.spa.shared.booking

import kotlinx.serialization.Serializable

/** House rules both sides have to agree on: the app withholds an action the server would refuse. */
object BookingRules {
    /** A guest may cancel until this long before the start; after that the spa has to be phoned. */
    const val CANCEL_WINDOW_MINUTES = 120

    /** How long before a visit its reminder is written to the notification centre. */
    const val REMINDER_LEAD_HOURS = 24
}

object BookingErrorCodes {
    const val UNKNOWN_SERVICE = "unknown_service"
    const val UNKNOWN_THERAPIST = "unknown_therapist"

    /** Nobody is free then: a named therapist is busy, or no therapist at all is free for "any". */
    const val SLOT_TAKEN = "slot_taken"
    const val SLOT_IN_PAST = "slot_in_past"
    const val INVALID_CODE = "invalid_code"
    const val UNKNOWN_BOOKING = "unknown_booking"
    const val ALREADY_DECIDED = "already_decided"

    /** The booking belongs to another guest; it reads as not found to the caller. */
    const val NOT_YOUR_BOOKING = "not_your_booking"

    /** Cancel, complete or no-show asked of a booking whose status does not allow it. */
    const val INVALID_TRANSITION = "invalid_transition"

    /** Inside the cancellation window: the guest has to phone the spa. */
    const val TOO_LATE_TO_CANCEL = "too_late_to_cancel"

    /** A visit cannot be recorded before it was due to start. */
    const val VISIT_NOT_STARTED = "visit_not_started"

    /** A confirm that would double-book the therapist; retry with `force` to override. */
    const val OVERLAPS_ANOTHER = "overlaps_another"
    const val DUPLICATE_CODE = "duplicate_code"
}

@Serializable
enum class ServiceCategoryDto { MASSAGE, FACIAL, BODY }

@Serializable
data class ServiceDto(
    val slug: String,
    val name: String,
    val minutes: Int,
    val price: Int,
    val category: ServiceCategoryDto,
    val includes: String,
)

@Serializable
enum class TherapistAccentDto { MOSS, DUSK, AMBER }

@Serializable
data class TherapistDto(
    val slug: String,
    val name: String,
    val focus: String,
    val accent: TherapistAccentDto,
    /** The next free slot; null when the diary is full. */
    val nextSlot: SlotMomentDto?,
)

/** [todayEpochDay] is the day it is at the spa, which is what "today" means on a booking screen. */
@Serializable
data class CatalogResponse(val services: List<ServiceDto>, val therapists: List<TherapistDto>, val todayEpochDay: Int)

/** A moment in the house's own time zone: [hour] is 14.5 for half past two, which is what a clock face needs. */
@Serializable
data class SlotMomentDto(val dayEpochDay: Int, val hour: Double)

@Serializable
data class SlotDto(val startAtEpochMillis: Long, val startHour: Double, val available: Boolean)

@Serializable
data class AvailabilityResponse(val dayEpochDay: Int, val slots: List<SlotDto>)

/** A null [therapistSlug] is "any therapist": the server assigns one who is free at that time. */
@Serializable
data class CreateBookingRequest(
    val serviceSlug: String,
    val therapistSlug: String? = null,
    val startAtEpochMillis: Long,
    val code: String? = null,
    val note: String? = null,
)

@Serializable
enum class BookingStatusDto { PENDING, CONFIRMED, DECLINED, COMPLETED, CANCELLED, NO_SHOW }

@Serializable
enum class DeclineReasonDto { OVERLAP, THERAPIST_UNAVAILABLE, NO_ROOM }

/** Money is whole dong; times are epoch milliseconds, UTC. */
@Serializable
data class BookingDto(
    val id: String,
    val reference: String,
    val guestName: String,
    val guestPhone: String,
    val service: ServiceDto,
    val therapist: TherapistDto,
    val startAtEpochMillis: Long,
    val endAtEpochMillis: Long,
    /** The start in the house's own time zone: 14.5 is 2:30 PM, which is what a day grid measures. */
    val startHour: Double,
    /** The day the start falls on in the house's zone; with [startHour] it words a date the reader can read. */
    val startDayEpochDay: Int,
    val status: BookingStatusDto,
    val subtotal: Int,
    val discount: Int,
    val total: Int,
    val code: String?,
    val note: String?,
    val createdAtEpochMillis: Long,
    val declineReason: DeclineReasonDto? = null,
    val alternativeOffer: String? = null,
    /** Set on the staff queue: why confirming this one needs a second look. */
    val conflict: String? = null,
)

@Serializable
data class BookingsResponse(val bookings: List<BookingDto>)

@Serializable
data class StaffQueueResponse(
    val pending: List<BookingDto>,
    /** Everything already on the books for [dayEpochDay], which the schedule draws. */
    val scheduled: List<BookingDto>,
    val dayEpochDay: Int,
)

/** The owner adds a treatment to the menu; the server generates the slug. */
@Serializable
data class CreateServiceRequest(
    val name: String,
    val description: String,
    val minutes: Int,
    val price: Int,
    val category: ServiceCategoryDto,
)

@Serializable
data class CreateTherapistRequest(val name: String, val focus: String, val accent: TherapistAccentDto = TherapistAccentDto.MOSS)

@Serializable
data class ConfirmBookingRequest(
    /** True confirms a booking that overlaps another; the desk has decided it is fine. */
    val force: Boolean = false,
)

@Serializable
data class DeclineBookingRequest(val reason: DeclineReasonDto, val alternativeOffer: String? = null)

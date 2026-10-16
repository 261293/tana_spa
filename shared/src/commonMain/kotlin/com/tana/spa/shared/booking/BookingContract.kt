package com.tana.spa.shared.booking

import kotlinx.serialization.Serializable

/**
 * The booking contract, compiled into both the app and the server. Change a path, a field or an
 * error code here and both sides move together.
 *
 * Money is a whole unit of the catalogue's currency (the design prices in dollars). Times are
 * epoch milliseconds, UTC, as everywhere else in this API.
 */
object BookingApi {
    const val BASE = "/api/v1/spa"

    const val CATALOG = "$BASE/catalog"
    const val OFFERS = "$BASE/offers"
    const val OFFER_CHECK = "$BASE/offers/check"
    const val AVAILABILITY = "$BASE/availability"
    const val BOOKINGS = "$BASE/bookings"

    /** The guest's own booking: only the account that made it may cancel it. */
    fun cancel(bookingId: String): String = "$BOOKINGS/$bookingId/cancel"

    /** Run the spa: the owner's queue, the decisions and the menu. Refused for every other role. */
    const val OWNER_BASE = "/api/v1/owner/spa"
    const val OWNER_BOOKINGS = "$OWNER_BASE/bookings"
    const val OWNER_SERVICES = "$OWNER_BASE/services"
    const val OWNER_THERAPISTS = "$OWNER_BASE/therapists"

    fun confirm(bookingId: String): String = "$OWNER_BOOKINGS/$bookingId/confirm"

    fun decline(bookingId: String): String = "$OWNER_BOOKINGS/$bookingId/decline"

    fun complete(bookingId: String): String = "$OWNER_BOOKINGS/$bookingId/complete"

    fun noShow(bookingId: String): String = "$OWNER_BOOKINGS/$bookingId/no-show"

    /** Operate the system: discount codes stay with the administrator, like the feature flags. */
    const val ADMIN_BASE = "/api/v1/admin/spa"
    const val ADMIN_CODES = "$ADMIN_BASE/codes"

    /** The treatment the times are asked for: a longer one needs more of the diary free. */
    const val QUERY_SERVICE = "service"

    /** `?therapist=mai&day=20376` (epoch day, in the house's own time zone). */
    const val QUERY_THERAPIST = "therapist"
    const val QUERY_DAY = "day"
}

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
    /** The next free slot, already worded ("Today 2:30 PM"); null when the diary is full. */
    val nextSlot: String?,
)

@Serializable
data class CatalogResponse(val services: List<ServiceDto>, val therapists: List<TherapistDto>)

@Serializable
data class OfferDto(
    val code: String,
    val title: String,
    val terms: String,
    val percentOff: Int?,
    val amountOff: Int?,
    val minSpend: Int?,
    /** Empty means every treatment qualifies. */
    val eligible: List<ServiceCategoryDto>,
    val validUntil: String?,
)

@Serializable
data class OffersResponse(val offers: List<OfferDto>)

@Serializable
data class OfferCheckRequest(val code: String, val serviceSlug: String?)

/**
 * [discount] is what this code takes off [serviceSlug]; it is 0 and [offer] null when the code was
 * refused, and [reason] is one of [BookingErrorCodes] worded by the client.
 */
@Serializable
data class OfferCheckResponse(val accepted: Boolean, val offer: OfferDto? = null, val discount: Int = 0, val reason: String? = null)

@Serializable
data class SlotDto(val startAtEpochMillis: Long, val label: String, val available: Boolean)

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
    /** The start already worded in the house's zone ("Wed Oct 15, 2:30 PM"), for lists and notices. */
    val startLabel: String,
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

@Serializable
enum class CodeStateDto { LIVE, SCHEDULED, EXPIRED, DISABLED }

@Serializable
data class AdminCodeDto(
    val code: String,
    val offer: String,
    val eligible: String,
    val starts: String,
    val ends: String?,
    val used: Int,
    val limit: Int?,
    val state: CodeStateDto,
    val note: String? = null,
)

@Serializable
data class AdminCodesResponse(val codes: List<AdminCodeDto>)

@Serializable
data class CreateCodeRequest(
    val code: String,
    val title: String,
    val percentOff: Int? = null,
    val amountOff: Int? = null,
    val minSpend: Int? = null,
    val eligible: List<ServiceCategoryDto> = emptyList(),
    val startsAtEpochMillis: Long? = null,
    val endsAtEpochMillis: Long? = null,
    val usageLimit: Int? = null,
)

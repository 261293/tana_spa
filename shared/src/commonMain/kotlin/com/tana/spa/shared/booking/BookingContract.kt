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

    const val ADMIN_BASE = "/api/v1/admin/spa"
    const val ADMIN_BOOKINGS = "$ADMIN_BASE/bookings"
    const val ADMIN_CODES = "$ADMIN_BASE/codes"

    /** `?therapist=mai&day=20376` (epoch day, in the house's own time zone). */
    const val QUERY_THERAPIST = "therapist"
    const val QUERY_DAY = "day"
}

object BookingErrorCodes {
    const val UNKNOWN_SERVICE = "unknown_service"
    const val UNKNOWN_THERAPIST = "unknown_therapist"
    const val SLOT_TAKEN = "slot_taken"
    const val SLOT_IN_PAST = "slot_in_past"
    const val INVALID_CODE = "invalid_code"
    const val UNKNOWN_BOOKING = "unknown_booking"
    const val ALREADY_DECIDED = "already_decided"

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

@Serializable
data class CreateBookingRequest(
    val serviceSlug: String,
    val therapistSlug: String,
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

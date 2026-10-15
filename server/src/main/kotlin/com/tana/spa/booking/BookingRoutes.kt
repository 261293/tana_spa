package com.tana.spa.booking

import com.tana.spa.admin.requireAdmin
import com.tana.spa.admin.requireOwner
import com.tana.spa.admin.respondApiError
import com.tana.spa.auth.AuthService
import com.tana.spa.auth.JWT_AUTH
import com.tana.spa.auth.User
import com.tana.spa.promotions.PromotionService
import com.tana.spa.shared.auth.AuthErrorCodes
import com.tana.spa.shared.booking.AdminCodeDto
import com.tana.spa.shared.booking.AdminCodesResponse
import com.tana.spa.shared.booking.AvailabilityResponse
import com.tana.spa.shared.booking.BookingApi
import com.tana.spa.shared.booking.BookingDto
import com.tana.spa.shared.booking.BookingErrorCodes
import com.tana.spa.shared.booking.BookingStatusDto
import com.tana.spa.shared.booking.BookingsResponse
import com.tana.spa.shared.booking.CatalogResponse
import com.tana.spa.shared.booking.CodeStateDto
import com.tana.spa.shared.booking.ConfirmBookingRequest
import com.tana.spa.shared.booking.CreateBookingRequest
import com.tana.spa.shared.booking.CreateCodeRequest
import com.tana.spa.shared.booking.CreateServiceRequest
import com.tana.spa.shared.booking.CreateTherapistRequest
import com.tana.spa.shared.booking.DeclineBookingRequest
import com.tana.spa.shared.booking.DeclineReasonDto
import com.tana.spa.shared.booking.OfferCheckRequest
import com.tana.spa.shared.booking.OfferCheckResponse
import com.tana.spa.shared.booking.OfferDto
import com.tana.spa.shared.booking.OffersResponse
import com.tana.spa.shared.booking.ServiceCategoryDto
import com.tana.spa.shared.booking.ServiceDto
import com.tana.spa.shared.booking.SlotDto
import com.tana.spa.shared.booking.SpaValidation
import com.tana.spa.shared.booking.StaffQueueResponse
import com.tana.spa.shared.booking.TherapistAccentDto
import com.tana.spa.shared.booking.TherapistDto
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val VALID_UNTIL: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
private val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)

/** Install after `authModule`: it owns ContentNegotiation, CORS, StatusPages and the JWT realm. */
fun Application.bookingModule(service: BookingService, auth: AuthService, promotions: PromotionService) {
    routing {
        // The menu and the offers are what a visitor sees before signing in.
        get(BookingApi.CATALOG) {
            val nextSlots = service.nextFreeSlots()
            call.respond(
                CatalogResponse(
                    services = service.services().map { it.toDto() },
                    therapists = service.therapists().map { it.toDto(nextSlots[it.slug]) },
                ),
            )
        }

        get(BookingApi.OFFERS) {
            call.respond(OffersResponse(service.offers().map { it.toDto() }))
        }

        post(BookingApi.OFFER_CHECK) {
            val request = call.receive<OfferCheckRequest>()
            val verdict = service.checkCode(request.code, request.serviceSlug)
            call.respond(
                OfferCheckResponse(
                    accepted = verdict.refusal == null,
                    offer = verdict.voucher?.takeIf { verdict.refusal == null }?.toDto(),
                    discount = verdict.discount.toInt(),
                    reason = verdict.refusal,
                ),
            )
        }

        get(BookingApi.AVAILABILITY) {
            val day = call.request.queryParameters[BookingApi.QUERY_DAY]?.toIntOrNull()
                ?: service.epochDayOf(Instant.now())
            val slots = service.availability(call.request.queryParameters[BookingApi.QUERY_THERAPIST], service.dayFromEpochDay(day))
            call.respond(
                AvailabilityResponse(
                    dayEpochDay = day,
                    slots = slots.map { (start, free) ->
                        SlotDto(start.toEpochMilli(), service.slotLabel(start), free)
                    },
                ),
            )
        }

        authenticate(JWT_AUTH) {
            route(BookingApi.BOOKINGS) {
                get {
                    val user = call.signedInUser(auth) ?: return@get
                    call.respond(BookingsResponse(service.bookingsOf(user.id).map { it.toDto(service) }))
                }

                post {
                    val user = call.signedInUser(auth) ?: return@post
                    val request = call.receive<CreateBookingRequest>()
                    val outcome = service.create(
                        userId = user.id,
                        guestName = user.displayName,
                        guestPhone = user.phone ?: UNKNOWN_PHONE,
                        serviceSlug = request.serviceSlug,
                        therapistSlug = request.therapistSlug,
                        startAt = Instant.ofEpochMilli(request.startAtEpochMillis),
                        code = request.code,
                        note = request.note,
                    )
                    when (outcome) {
                        is BookingOutcome.Created -> call.respond(HttpStatusCode.Created, outcome.booking.toDto(service))
                        is BookingOutcome.Decided -> call.respond(outcome.booking.toDto(service))
                        is BookingOutcome.Refused -> call.respondRefusal(outcome)
                    }
                }

                post("/{id}/cancel") {
                    val user = call.signedInUser(auth) ?: return@post
                    val id = call.bookingId() ?: return@post
                    call.respondOutcome(service.cancel(id, user.id), service)
                }
            }

            route(BookingApi.OWNER_BOOKINGS) {
                get {
                    call.requireOwner(auth) ?: return@get
                    val now = Instant.now()
                    val today = service.dayFromEpochDay(service.epochDayOf(now))
                    call.respond(
                        StaffQueueResponse(
                            pending = service.pending().map { it.toDto(service, conflict = service.conflictFor(it)) },
                            scheduled = service.scheduledOn(today).map { it.toDto(service) },
                            dayEpochDay = service.epochDayOf(now),
                        ),
                    )
                }

                post("/{id}/confirm") {
                    val owner = call.requireOwner(auth) ?: return@post
                    val id = call.bookingId() ?: return@post
                    val force = runCatching { call.receive<ConfirmBookingRequest>() }.getOrNull()?.force ?: false
                    call.respondOutcome(service.confirm(id, owner.id, force), service)
                }

                post("/{id}/decline") {
                    val owner = call.requireOwner(auth) ?: return@post
                    val id = call.bookingId() ?: return@post
                    val request = call.receive<DeclineBookingRequest>()
                    call.respondOutcome(service.decline(id, owner.id, request.reason, request.alternativeOffer), service)
                }

                post("/{id}/complete") {
                    val owner = call.requireOwner(auth) ?: return@post
                    val id = call.bookingId() ?: return@post
                    call.respondOutcome(service.recordVisit(id, owner.id, attended = true), service)
                }

                post("/{id}/no-show") {
                    val owner = call.requireOwner(auth) ?: return@post
                    val id = call.bookingId() ?: return@post
                    call.respondOutcome(service.recordVisit(id, owner.id, attended = false), service)
                }
            }

            post(BookingApi.OWNER_SERVICES) {
                call.requireOwner(auth) ?: return@post
                val request = call.receive<CreateServiceRequest>()
                SpaValidation.firstInvalidField(request)?.let { return@post call.respondInvalidField(it) }
                val created = service.createService(request)
                    ?: return@post call.respondApiError(
                        HttpStatusCode.Conflict,
                        BookingErrorCodes.UNKNOWN_SERVICE,
                        "That treatment could not be added",
                    )
                call.respond(HttpStatusCode.Created, created.toDto())
            }

            post(BookingApi.OWNER_THERAPISTS) {
                call.requireOwner(auth) ?: return@post
                val request = call.receive<CreateTherapistRequest>()
                SpaValidation.firstInvalidField(request)?.let { return@post call.respondInvalidField(it) }
                val created = service.createTherapist(request)
                    ?: return@post call.respondApiError(
                        HttpStatusCode.Conflict,
                        BookingErrorCodes.UNKNOWN_THERAPIST,
                        "That therapist could not be added",
                    )
                call.respond(HttpStatusCode.Created, created.toDto(nextSlot = null))
            }

            route(BookingApi.ADMIN_CODES) {
                get {
                    call.requireAdmin(auth) ?: return@get
                    call.respond(AdminCodesResponse(service.allCodes().map { it.toAdminDto() }))
                }

                post {
                    call.requireAdmin(auth) ?: return@post
                    val request = call.receive<CreateCodeRequest>()
                    val percent = request.percentOff
                    val amount = request.amountOff
                    if ((percent == null) == (amount == null)) {
                        return@post call.respondApiError(
                            HttpStatusCode.BadRequest,
                            AuthErrorCodes.INVALID_REQUEST,
                            "A code is either a percentage or an amount, not both",
                        )
                    }
                    val created = service.createCode(
                        NewVoucher(
                            code = request.code.trim().uppercase(),
                            title = request.title.trim(),
                            percent = percent != null,
                            value = (percent ?: amount ?: 0).toLong(),
                            minOrder = (request.minSpend ?: 0).toLong(),
                            startsAt = request.startsAtEpochMillis?.let(Instant::ofEpochMilli) ?: Instant.now(),
                            endsAt = request.endsAtEpochMillis?.let(Instant::ofEpochMilli),
                            usageLimit = request.usageLimit,
                            eligibleCategories = request.eligible.map { it.name.lowercase() },
                        ),
                    )
                    if (created) {
                        promotions.announce(request.code)
                        call.respond(HttpStatusCode.Created, AdminCodesResponse(service.allCodes().map { it.toAdminDto() }))
                    } else {
                        call.respondApiError(
                            HttpStatusCode.Conflict,
                            BookingErrorCodes.DUPLICATE_CODE,
                            "That code already exists",
                        )
                    }
                }

                post("/{code}/disable") {
                    call.requireAdmin(auth) ?: return@post
                    val code = call.parameters["code"].orEmpty()
                    if (service.disableCode(code)) {
                        call.respond(AdminCodesResponse(service.allCodes().map { it.toAdminDto() }))
                    } else {
                        call.respondApiError(
                            HttpStatusCode.NotFound,
                            BookingErrorCodes.INVALID_CODE,
                            "No live code with that name",
                        )
                    }
                }
            }
        }
    }
}

private const val UNKNOWN_PHONE = "+10000000000"

private suspend fun ApplicationCall.bookingId(): String? = parameters["id"]
    ?: run {
        respondApiError(HttpStatusCode.BadRequest, AuthErrorCodes.INVALID_REQUEST, "A booking id is required")
        null
    }

private suspend fun ApplicationCall.respondInvalidField(field: String) =
    respondApiError(HttpStatusCode.BadRequest, AuthErrorCodes.INVALID_REQUEST, "The $field is not valid", field)

private suspend fun ApplicationCall.respondOutcome(outcome: BookingOutcome, service: BookingService) = when (outcome) {
    is BookingOutcome.Created -> respond(outcome.booking.toDto(service))
    is BookingOutcome.Decided -> respond(outcome.booking.toDto(service))
    is BookingOutcome.Refused -> respondRefusal(outcome)
}

private suspend fun io.ktor.server.application.ApplicationCall.signedInUser(auth: AuthService): User? {
    val user = principal<JWTPrincipal>()?.payload?.subject?.let { auth.findUser(it) }
    if (user == null) {
        respondApiError(HttpStatusCode.Unauthorized, AuthErrorCodes.UNAUTHORIZED, "Authentication required")
    }
    return user
}

private suspend fun io.ktor.server.application.ApplicationCall.respondRefusal(refused: BookingOutcome.Refused) {
    val status = when (refused.code) {
        // Someone else's booking reads as absent: the caller learns nothing about it either way.
        BookingErrorCodes.UNKNOWN_BOOKING, BookingErrorCodes.NOT_YOUR_BOOKING -> HttpStatusCode.NotFound

        BookingErrorCodes.SLOT_TAKEN, BookingErrorCodes.OVERLAPS_ANOTHER,
        BookingErrorCodes.ALREADY_DECIDED, BookingErrorCodes.DUPLICATE_CODE,
        BookingErrorCodes.INVALID_TRANSITION, BookingErrorCodes.TOO_LATE_TO_CANCEL,
        BookingErrorCodes.VISIT_NOT_STARTED,
        -> HttpStatusCode.Conflict

        else -> HttpStatusCode.BadRequest
    }
    respondApiError(status, refused.code, refused.detail ?: refused.code.replace('_', ' '))
}

private fun ServiceRow.toDto() = ServiceDto(
    slug = slug,
    name = name,
    minutes = minutes,
    price = price.toInt(),
    category = categorySlug.toCategoryDto(),
    includes = description,
)

private fun String.toCategoryDto(): ServiceCategoryDto = when (lowercase()) {
    "facial" -> ServiceCategoryDto.FACIAL
    "body" -> ServiceCategoryDto.BODY
    else -> ServiceCategoryDto.MASSAGE
}

private fun TherapistRow.toDto(nextSlot: String?) = TherapistDto(
    slug = slug,
    name = name,
    focus = focus,
    accent = when (accent) {
        "dusk" -> TherapistAccentDto.DUSK
        "amber" -> TherapistAccentDto.AMBER
        else -> TherapistAccentDto.MOSS
    },
    nextSlot = nextSlot,
)

private fun VoucherRow.toDto() = OfferDto(
    code = code,
    title = title,
    terms = description.orEmpty(),
    percentOff = value.toInt().takeIf { percent },
    amountOff = value.toInt().takeIf { !percent },
    minSpend = minOrder.toInt().takeIf { it > 0 },
    eligible = eligibleCategories.map { it.toCategoryDto() },
    validUntil = endsAt?.let { LocalDate.ofInstant(it, java.time.ZoneOffset.UTC).format(VALID_UNTIL) },
)

private fun VoucherRow.toAdminDto(): AdminCodeDto {
    val now = Instant.now()
    val state = when {
        !active -> CodeStateDto.DISABLED
        endsAt != null && endsAt <= now -> CodeStateDto.EXPIRED
        startsAt > now -> CodeStateDto.SCHEDULED
        else -> CodeStateDto.LIVE
    }
    return AdminCodeDto(
        code = code,
        offer = if (percent) "$value% off" else "$$value off",
        eligible = if (eligibleCategories.isEmpty()) {
            "All treatments"
        } else {
            eligibleCategories.joinToString(" - ") { it.replaceFirstChar(Char::uppercase) }
        },
        starts = LocalDate.ofInstant(startsAt, java.time.ZoneOffset.UTC).format(SHORT_DATE),
        ends = endsAt?.let { LocalDate.ofInstant(it, java.time.ZoneOffset.UTC).format(SHORT_DATE) },
        used = usedCount,
        limit = usageLimit,
        state = state,
    )
}

private fun BookingRow.toDto(service: BookingService, conflict: BookingRow? = null) = BookingDto(
    id = id,
    reference = reference,
    guestName = guestName,
    guestPhone = guestPhone,
    service = this.service.toDto(),
    therapist = therapist?.toDto(null) ?: TherapistDto("", "", "", TherapistAccentDto.MOSS, null),
    startAtEpochMillis = startAt.toEpochMilli(),
    endAtEpochMillis = endAt.toEpochMilli(),
    startHour = service.hourOfDay(startAt),
    startLabel = service.dayAndTimeLabel(startAt),
    status = when (status) {
        "confirmed" -> BookingStatusDto.CONFIRMED
        "declined" -> BookingStatusDto.DECLINED
        "completed" -> BookingStatusDto.COMPLETED
        "cancelled" -> BookingStatusDto.CANCELLED
        "no_show" -> BookingStatusDto.NO_SHOW
        else -> BookingStatusDto.PENDING
    },
    subtotal = subtotal.toInt(),
    discount = discount.toInt(),
    total = total.toInt(),
    code = code,
    note = note,
    createdAtEpochMillis = createdAt.toEpochMilli(),
    declineReason = when (declineReason) {
        "overlap" -> DeclineReasonDto.OVERLAP
        "therapist_unavailable" -> DeclineReasonDto.THERAPIST_UNAVAILABLE
        "no_room" -> DeclineReasonDto.NO_ROOM
        else -> null
    },
    alternativeOffer = alternativeOffer,
    conflict = conflict?.let {
        "Overlaps ${therapist?.name.orEmpty()} ${service.slotLabel(it.startAt)} with ${it.reference}"
    },
)

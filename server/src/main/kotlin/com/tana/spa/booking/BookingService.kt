package com.tana.spa.booking

import com.tana.spa.notifications.NotificationService
import com.tana.spa.notifications.SendNotificationResult
import com.tana.spa.shared.booking.BookingErrorCodes
import com.tana.spa.shared.booking.BookingRules
import com.tana.spa.shared.booking.CreateServiceRequest
import com.tana.spa.shared.booking.CreateTherapistRequest
import com.tana.spa.shared.booking.DeclineReasonDto
import com.tana.spa.shared.booking.SlotMomentDto
import com.tana.spa.shared.booking.SpaValidation
import com.tana.spa.shared.notifications.NotificationData
import com.tana.spa.shared.notifications.NotificationSendRequest
import com.tana.spa.shared.notifications.NotificationType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The times the house offers, in its own time zone. The diary is these eight slots a day. */
private val SLOT_TIMES = listOf(
    LocalTime.of(9, 0),
    LocalTime.of(10, 0),
    LocalTime.of(11, 30),
    LocalTime.of(13, 0),
    LocalTime.of(14, 30),
    LocalTime.of(16, 0),
    LocalTime.of(17, 30),
    LocalTime.of(19, 0),
)

private const val BOOKING_HORIZON_DAYS = 60L
private val SLOT_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
private val DAY_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE MMM d", Locale.US)

sealed interface BookingOutcome {
    data class Created(val booking: BookingRow) : BookingOutcome

    data class Decided(val booking: BookingRow) : BookingOutcome

    /** [code] is one of [BookingErrorCodes]; [detail] is for the log, not for the guest. */
    data class Refused(val code: String, val detail: String? = null) : BookingOutcome
}

data class PromoVerdict(val voucher: VoucherRow?, val discount: Long, val refusal: String?)

/**
 * The rules the front desk would otherwise have to hold in its head:
 * a code only counts against a treatment it covers, a therapist cannot be in two rooms at once,
 * and a decision can be taken once.
 *
 * @param zone the house's own time zone; a slot is 2:30 PM there, whatever the guest's device says.
 * @param turnaround the gap a therapist needs between two treatments; see [tooClose].
 */
class BookingService(
    private val repository: BookingRepository,
    private val notifications: NotificationService,
    private val zone: ZoneId = houseZone(),
    private val clock: () -> Instant = Instant::now,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val turnaround: Duration = DEFAULT_TURNAROUND,
) {
    private val log = LoggerFactory.getLogger(BookingService::class.java)

    suspend fun services(): List<ServiceRow> = withContext(io) { repository.services() }

    suspend fun therapists(): List<TherapistRow> = withContext(io) { repository.therapists() }

    /** The next slot each therapist still has free, for the catalogue; the client words it. */
    suspend fun nextFreeSlots(): Map<String, SlotMomentDto?> = withContext(io) {
        val today = LocalDate.ofInstant(clock(), zone)
        repository.therapists().associate { therapist ->
            therapist.slug to generateSequence(today) { it.plusDays(1) }
                .take(DAYS_SCANNED_FOR_NEXT_SLOT)
                .flatMap { day -> openSlots(therapist.id, day).asSequence() }
                .firstOrNull()
                ?.let { SlotMomentDto(epochDayOf(it), hourOfDay(it)) }
        }
    }

    suspend fun offers(): List<VoucherRow> = withContext(io) {
        val now = clock()
        repository.vouchers().filter { it.active && it.startsAt <= now && (it.endsAt == null || it.endsAt > now) }
    }

    suspend fun allCodes(): List<VoucherRow> = withContext(io) { repository.vouchers() }

    /**
     * A code is only ever judged together with the treatment it would be spent on: eligibility and
     * the minimum spend are properties of that pairing, not of the code alone.
     */
    suspend fun checkCode(rawCode: String, serviceSlug: String?): PromoVerdict = withContext(io) {
        val code = rawCode.trim().uppercase()
        if (code.isEmpty()) return@withContext PromoVerdict(null, 0, BookingErrorCodes.INVALID_CODE)
        val voucher = repository.voucherByCode(code)
            ?: return@withContext PromoVerdict(null, 0, BookingErrorCodes.INVALID_CODE)
        val service = serviceSlug?.let { repository.serviceBySlug(it) }
        verdictFor(voucher, service)
    }

    private fun verdictFor(voucher: VoucherRow, service: ServiceRow?): PromoVerdict {
        val now = clock()
        val live = voucher.active && voucher.startsAt <= now && (voucher.endsAt == null || voucher.endsAt > now)
        val spent = voucher.usageLimit != null && voucher.usedCount >= voucher.usageLimit
        if (!live || spent) return PromoVerdict(null, 0, BookingErrorCodes.INVALID_CODE)
        if (service == null) return PromoVerdict(voucher, 0, BookingErrorCodes.UNKNOWN_SERVICE)
        if (voucher.eligibleCategories.isNotEmpty() && service.categorySlug !in voucher.eligibleCategories) {
            return PromoVerdict(voucher, 0, BookingErrorCodes.INVALID_CODE)
        }
        if (service.price < voucher.minOrder) return PromoVerdict(voucher, 0, BookingErrorCodes.INVALID_CODE)
        val discount = if (voucher.percent) {
            // Rounded to the nearest whole unit, never past the price itself.
            ((service.price * voucher.value + HALF_UNIT) / PERCENT).coerceAtMost(service.price)
        } else {
            voucher.value.coerceAtMost(service.price)
        }
        return PromoVerdict(voucher, discount, null)
    }

    /**
     * Every slot of [day], with the ones already held marked unavailable. A null [therapistSlug]
     * is "any therapist": the slot is open while one of them is still free.
     *
     * [serviceSlug] is the treatment being chosen, and it decides as much as the start does: a
     * 90-minute treatment at 10:00 runs into an 11:30 booking, so the answer has to refuse the
     * 10:00 slot that a shorter treatment could still have. Without it only the start is judged,
     * and [create] may then refuse a slot this said was open.
     */
    suspend fun availability(therapistSlug: String?, day: LocalDate, serviceSlug: String? = null): List<Pair<Instant, Boolean>> =
        withContext(io) {
            val therapist = therapistSlug?.let { repository.therapistBySlug(it) }
            val diaries = if (therapist != null) listOf(therapist.id) else repository.therapists().map { it.id }
            val heldByTherapist = diaries.associateWith { id -> held(id, day) }
            val minutes = serviceSlug?.let { repository.serviceBySlug(it) }?.minutes ?: 0
            val now = clock()
            SLOT_TIMES.map { time ->
                val start = day.atTime(time).atZone(zone).toInstant()
                val end = start.plus(Duration.ofMinutes(minutes.toLong()))
                val free = start > now && heldByTherapist.values.any { diary -> diary.none { tooClose(start, end, it) } }
                start to free
            }
        }

    fun slotLabel(start: Instant): String = LocalTime.ofInstant(start, zone).format(SLOT_LABEL)

    /** 14.5 for 2:30 PM in the house's zone: the number a day grid positions a block with. */
    fun hourOfDay(start: Instant): Double = LocalTime.ofInstant(start, zone).let { it.hour + it.minute / MINUTES_PER_HOUR }

    fun epochDayOf(instant: Instant): Int = LocalDate.ofInstant(instant, zone).toEpochDay().toInt()

    fun dayFromEpochDay(epochDay: Int): LocalDate = LocalDate.ofEpochDay(epochDay.toLong())

    suspend fun bookingsOf(userId: String): List<BookingRow> = withContext(io) { repository.bookingsOf(userId) }

    suspend fun pending(): List<BookingRow> = withContext(io) { repository.pendingBookings() }

    suspend fun scheduledOn(day: LocalDate): List<BookingRow> = withContext(io) {
        repository.bookingsFor(null, day.atStartOfDay(zone).toInstant(), day.plusDays(1).atStartOfDay(zone).toInstant())
    }

    /** The overlap the staff screen warns about: another holding booking in the same hands. */
    suspend fun conflictFor(booking: BookingRow): BookingRow? = withContext(io) { overlapping(booking) }

    /** A null [therapistSlug] is "any therapist": whoever is free at that time takes the guest. */
    suspend fun create(
        userId: String,
        guestName: String,
        guestPhone: String,
        serviceSlug: String,
        therapistSlug: String?,
        startAt: Instant,
        code: String?,
        note: String?,
    ): BookingOutcome = withContext(io) {
        val service = repository.serviceBySlug(serviceSlug)
            ?: return@withContext BookingOutcome.Refused(BookingErrorCodes.UNKNOWN_SERVICE, serviceSlug)
        val therapist = if (therapistSlug == null) {
            null
        } else {
            repository.therapistBySlug(therapistSlug)
                ?: return@withContext BookingOutcome.Refused(BookingErrorCodes.UNKNOWN_THERAPIST, therapistSlug)
        }

        val now = clock()
        if (startAt <= now) return@withContext BookingOutcome.Refused(BookingErrorCodes.SLOT_IN_PAST)
        if (startAt > now.plus(Duration.ofDays(BOOKING_HORIZON_DAYS))) {
            return@withContext BookingOutcome.Refused(BookingErrorCodes.SLOT_IN_PAST, "beyond the horizon")
        }
        val day = LocalDate.ofInstant(startAt, zone)
        if (SLOT_TIMES.none { day.atTime(it).atZone(zone).toInstant() == startAt }) {
            return@withContext BookingOutcome.Refused(BookingErrorCodes.SLOT_TAKEN, "not a slot the house offers")
        }
        val endAt = startAt.plus(Duration.ofMinutes(service.minutes.toLong()))
        // "Any therapist" is resolved here, not at confirmation: the no-overlap rule only holds
        // if the booking already names the hands it occupies.
        val assigned = if (therapist != null) {
            therapist.takeIf { isFree(it, day, startAt, endAt) }
                ?: return@withContext BookingOutcome.Refused(BookingErrorCodes.SLOT_TAKEN)
        } else {
            repository.therapists().firstOrNull { isFree(it, day, startAt, endAt) }
                ?: return@withContext BookingOutcome.Refused(BookingErrorCodes.SLOT_TAKEN, "nobody is free")
        }

        // An unusable code must not silently become a full-price booking.
        val verdict = code?.takeIf { it.isNotBlank() }?.let { raw ->
            val voucher = repository.voucherByCode(raw.trim().uppercase())
                ?: return@withContext BookingOutcome.Refused(BookingErrorCodes.INVALID_CODE)
            verdictFor(voucher, service).also {
                if (it.refusal != null) return@withContext BookingOutcome.Refused(BookingErrorCodes.INVALID_CODE)
            }
        }

        val booking = repository.insertBooking(
            NewBooking(
                userId = userId,
                serviceId = service.id,
                therapistId = assigned.id,
                startAt = startAt,
                endAt = endAt,
                guestName = guestName,
                guestPhone = guestPhone,
                note = note?.trim()?.takeIf { it.isNotEmpty() },
                subtotal = service.price,
                discount = verdict?.discount ?: 0,
                voucherId = verdict?.voucher?.id,
            ),
        )
        log.info("booking {} requested by user {} ({} with {})", booking.reference, userId, service.slug, assigned.slug)
        BookingOutcome.Created(booking)
    }

    suspend fun confirm(id: String, decidedBy: String, force: Boolean): BookingOutcome {
        val booking = withContext(io) { repository.booking(id) }
            ?: return BookingOutcome.Refused(BookingErrorCodes.UNKNOWN_BOOKING)
        if (booking.status != "pending") return BookingOutcome.Refused(BookingErrorCodes.ALREADY_DECIDED)

        val clash = withContext(io) { overlapping(booking) }
        // The desk may still confirm a clash, but never by accident.
        if (clash != null && !force) {
            return BookingOutcome.Refused(BookingErrorCodes.OVERLAPS_ANOTHER, clash.reference)
        }
        if (!withContext(io) { repository.decide(id, "confirmed", decidedBy, null, null) }) {
            return BookingOutcome.Refused(BookingErrorCodes.ALREADY_DECIDED)
        }
        val confirmed = withContext(io) { repository.booking(id) } ?: booking
        log.info("booking {} confirmed by {}", confirmed.reference, decidedBy)
        notify(
            confirmed,
            title = "Booking confirmed",
            body = "${confirmed.service.name} with ${confirmed.therapist?.name.orEmpty()} " +
                "on ${dayAndTime(confirmed.startAt)}",
        )
        return BookingOutcome.Decided(confirmed)
    }

    suspend fun decline(id: String, decidedBy: String, reason: DeclineReasonDto, alternativeOffer: String?): BookingOutcome {
        val booking = withContext(io) { repository.booking(id) }
            ?: return BookingOutcome.Refused(BookingErrorCodes.UNKNOWN_BOOKING)
        if (booking.status != "pending") return BookingOutcome.Refused(BookingErrorCodes.ALREADY_DECIDED)

        val stored = reason.name.lowercase()
        val offer = alternativeOffer?.trim()?.takeIf { it.isNotEmpty() }
        if (!withContext(io) { repository.decide(id, "declined", decidedBy, stored, offer) }) {
            return BookingOutcome.Refused(BookingErrorCodes.ALREADY_DECIDED)
        }
        val declined = withContext(io) { repository.booking(id) } ?: booking
        log.info("booking {} declined by {} ({})", declined.reference, decidedBy, stored)
        notify(
            declined,
            title = "Time unavailable",
            body = buildString {
                append(reasonWord(reason, declined))
                offer?.let { append(" We can offer $it.") }
            },
        )
        return BookingOutcome.Decided(declined)
    }

    /**
     * The guest calls off their own visit. Refused once the start is less than
     * [BookingRules.CANCEL_WINDOW_MINUTES] away: the spa has given the hour to them and needs
     * the phone call.
     */
    suspend fun cancel(id: String, userId: String): BookingOutcome {
        val booking = withContext(io) { repository.booking(id) }
            ?: return BookingOutcome.Refused(BookingErrorCodes.UNKNOWN_BOOKING)
        if (booking.userId != userId) return BookingOutcome.Refused(BookingErrorCodes.NOT_YOUR_BOOKING)
        if (booking.status !in CANCELLABLE) return BookingOutcome.Refused(BookingErrorCodes.INVALID_TRANSITION, booking.status)
        if (booking.startAt < clock().plus(CANCEL_WINDOW)) {
            return BookingOutcome.Refused(BookingErrorCodes.TOO_LATE_TO_CANCEL)
        }
        if (!withContext(io) { repository.transition(id, CANCELLABLE, "cancelled", null) }) {
            return BookingOutcome.Refused(BookingErrorCodes.INVALID_TRANSITION)
        }
        val cancelled = withContext(io) { repository.booking(id) } ?: booking
        log.info("booking {} cancelled by its guest", cancelled.reference)
        return BookingOutcome.Decided(cancelled)
    }

    /** The owner records how a confirmed visit went. Neither can be recorded before it starts. */
    suspend fun recordVisit(id: String, decidedBy: String, attended: Boolean): BookingOutcome {
        val booking = withContext(io) { repository.booking(id) }
            ?: return BookingOutcome.Refused(BookingErrorCodes.UNKNOWN_BOOKING)
        if (booking.status != "confirmed") return BookingOutcome.Refused(BookingErrorCodes.INVALID_TRANSITION, booking.status)
        if (clock() < booking.startAt) return BookingOutcome.Refused(BookingErrorCodes.VISIT_NOT_STARTED)

        val status = if (attended) "completed" else "no_show"
        if (!withContext(io) { repository.transition(id, listOf("confirmed"), status, decidedBy) }) {
            return BookingOutcome.Refused(BookingErrorCodes.INVALID_TRANSITION)
        }
        val recorded = withContext(io) { repository.booking(id) } ?: booking
        log.info("booking {} recorded as {} by {}", recorded.reference, status, decidedBy)
        return BookingOutcome.Decided(recorded)
    }

    /**
     * Adds a treatment to the menu. The slug comes from the name and is made unique here, so two
     * treatments may share a name without the owner having to invent an id.
     */
    suspend fun createService(request: CreateServiceRequest): ServiceRow? = withContext(io) {
        val base = SpaValidation.slugify(request.name)
        uniqueSlug(base) { slug ->
            repository.insertService(
                NewService(
                    slug = slug,
                    name = request.name.trim(),
                    categorySlug = request.category.name.lowercase(),
                    description = request.description.trim(),
                    price = request.price.toLong(),
                    minutes = request.minutes,
                ),
            )
        }?.also { log.info("service {} added to the menu", it.slug) }
    }

    suspend fun createTherapist(request: CreateTherapistRequest): TherapistRow? = withContext(io) {
        uniqueSlug(SpaValidation.slugify(request.name)) { slug ->
            repository.insertTherapist(
                NewTherapist(
                    slug = slug,
                    name = request.name.trim(),
                    focus = request.focus.trim(),
                    accent = request.accent.name.lowercase(),
                ),
            )
        }?.also { log.info("therapist {} joined the team", it.slug) }
    }

    /**
     * Writes the reminder for every confirmed visit starting within [BookingRules.REMINDER_LEAD_HOURS].
     * The claim is part of the same update, so running this twice (or on two instances) still
     * reminds each guest once.
     *
     * @return how many reminders were written.
     */
    suspend fun sendDueReminders(): Int {
        val now = clock()
        val due = withContext(io) { repository.claimRemindable(now, now.plus(REMINDER_LEAD)) }
        val written = due.count { booking ->
            val outcome = notify(
                booking,
                title = "Tomorrow at the spa",
                body = "${booking.service.name} with ${booking.therapist?.name.orEmpty()} on ${dayAndTime(booking.startAt)}",
            )
            // Notifications are off for this guest, so nothing was stored and the claim would
            // otherwise have spent the one reminder this visit gets.
            if (outcome is SendNotificationResult.FeatureDisabled) {
                withContext(io) { repository.releaseReminder(booking.id) }
                false
            } else {
                true
            }
        }
        if (written > 0) log.info("wrote {} appointment reminders", written)
        return written
    }

    /** Tries "jade-facial", then "jade-facial-2": a name the owner reuses must not fail to save. */
    private fun <T> uniqueSlug(base: String, insert: (String) -> T?): T? {
        if (base.isEmpty()) return null
        return generateSequence(1) { it + 1 }
            .take(SLUG_ATTEMPTS)
            .map { attempt -> insert(if (attempt == 1) base else "$base-$attempt") }
            .firstOrNull { it != null }
    }

    /** @return false when that code already exists; codes are never silently overwritten. */
    suspend fun createCode(voucher: NewVoucher): Boolean = withContext(io) {
        val created = repository.voucherByCode(voucher.code) == null && repository.insertVoucher(voucher)
        if (created) log.info("discount code {} published", voucher.code)
        created
    }

    suspend fun disableCode(code: String): Boolean = withContext(io) { repository.disableVoucher(code) }

    /** A guest hears about a decision through the notification centre, not by refreshing. */
    private suspend fun notify(booking: BookingRow, title: String, body: String): SendNotificationResult = notifications.send(
        NotificationSendRequest(
            userId = booking.userId,
            type = NotificationType.BOOKING.wire,
            title = title,
            body = body,
            data = mapOf(
                NotificationData.BOOKING_ID to booking.id,
                NotificationData.BOOKING_REFERENCE to booking.reference,
            ),
        ),
    )

    private fun isFree(therapist: TherapistRow, day: LocalDate, startAt: Instant, endAt: Instant): Boolean =
        held(therapist.id, day).none { tooClose(startAt, endAt, it) }

    private fun held(therapistId: String?, day: LocalDate): List<BookingRow> = repository.bookingsFor(
        therapistId,
        day.atStartOfDay(zone).toInstant(),
        day.plusDays(1).atStartOfDay(zone).toInstant(),
    )

    private fun openSlots(therapistId: String, day: LocalDate): List<Instant> {
        val taken = held(therapistId, day)
        val now = clock()
        return SLOT_TIMES.mapNotNull { time ->
            val start = day.atTime(time).atZone(zone).toInstant()
            start.takeIf { it > now && taken.none { held -> start >= held.startAt && start < held.endAt.plus(turnaround) } }
        }
    }

    private fun overlapping(booking: BookingRow): BookingRow? {
        val therapistId = booking.therapist?.id ?: return null
        return repository.bookingsFor(
            therapistId,
            booking.startAt.minus(Duration.ofHours(MAX_TREATMENT_HOURS)),
            booking.endAt.plus(turnaround),
        ).firstOrNull { it.id != booking.id && tooClose(booking.startAt, booking.endAt, it) }
    }

    /**
     * Two treatments in the same hands must be [turnaround] apart in both directions: the room has
     * to be reset and the therapist cannot walk straight out of one treatment into the next.
     */
    private fun tooClose(startAt: Instant, endAt: Instant, held: BookingRow): Boolean =
        startAt < held.endAt.plus(turnaround) && endAt.plus(turnaround) > held.startAt

    /** The English wording the server's own notices use; screens word a date themselves. */
    private fun dayAndTime(instant: Instant): String = "${LocalDate.ofInstant(instant, zone).format(DAY_LABEL)}, ${slotLabel(instant)}"

    private fun reasonWord(reason: DeclineReasonDto, booking: BookingRow): String = when (reason) {
        DeclineReasonDto.OVERLAP ->
            "${booking.therapist?.name.orEmpty()} is already booked at ${slotLabel(booking.startAt)}."

        DeclineReasonDto.THERAPIST_UNAVAILABLE ->
            "${booking.therapist?.name.orEmpty()} is not available at ${slotLabel(booking.startAt)}."

        DeclineReasonDto.NO_ROOM -> "No treatment room is free at ${slotLabel(booking.startAt)}."
    }

    private companion object {
        const val PERCENT = 100L
        const val MINUTES_PER_HOUR = 60.0
        const val HALF_UNIT = 50L
        const val DAYS_SCANNED_FOR_NEXT_SLOT = 14
        const val MAX_TREATMENT_HOURS = 4L
        const val SLUG_ATTEMPTS = 20
        val DEFAULT_TURNAROUND: Duration = Duration.ofMinutes(15)
        val CANCEL_WINDOW: Duration = Duration.ofMinutes(BookingRules.CANCEL_WINDOW_MINUTES.toLong())
        val REMINDER_LEAD: Duration = Duration.ofHours(BookingRules.REMINDER_LEAD_HOURS.toLong())
        val CANCELLABLE = listOf("pending", "confirmed")

        /** `SPA_TIMEZONE`, falling back to the house's own: the design's spa is on Valencia Street. */
        fun houseZone(): ZoneId = System.getenv("SPA_TIMEZONE")
            ?.takeIf { it.isNotBlank() }
            ?.let(ZoneId::of)
            ?: ZoneId.of("America/Los_Angeles")
    }
}

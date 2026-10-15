package com.tana.spa.shared.spa

/**
 * The treatment menu, the team and the live offers.
 *
 * The demo catalogue the design was drawn against, and the shape the UI speaks. The guest booking
 * flow now reads its treatments and therapists from the catalogue endpoint; the offers, the open
 * times and the staff console still read the sample data here.
 */

enum class ServiceCategory(val label: String) {
    MASSAGE("Massage"),
    FACIAL("Facial"),
    BODY("Body"),
}

data class Service(
    val id: String,
    val name: String,
    val minutes: Int,
    val priceUsd: Int,
    val category: ServiceCategory,
    val includes: String,
)

data class Therapist(
    val id: String,
    val name: String,
    val focus: String,
    /** Already worded ("Today 2:30 PM"); null when the diary is full. */
    val nextSlot: String?,
    /** "First available" is a scheduling choice, not a person, so it gets no initials. */
    val anyAvailable: Boolean = false,
)

data class Promo(
    val code: String,
    val title: String,
    val percentOff: Int? = null,
    val amountOffUsd: Int? = null,
    val minSpendUsd: Int? = null,
    /** Empty means every treatment qualifies. */
    val eligible: Set<ServiceCategory> = emptySet(),
    val validUntil: String,
    val terms: String,
)

enum class RequestStatus { PENDING, CONFIRMED, DECLINED }

data class BookingRequest(
    val id: String,
    val guest: String,
    val phone: String,
    val serviceId: String,
    val therapistId: String,
    /** Start of the appointment as an hour of the day, e.g. 14.5 for 2:30 PM. */
    val startHour: Double,
    val requested: String,
    val status: RequestStatus,
    val code: String? = null,
    val discountUsd: Int? = null,
    val note: String? = null,
    val conflict: String? = null,
)

data class ScheduledBooking(val id: String, val guest: String, val serviceId: String, val therapistId: String, val startHour: Double)

enum class AdminCodeState(val label: String) {
    LIVE("Live"),
    SCHEDULED("Scheduled"),
    EXPIRED("Expired"),
    DISABLED("Disabled"),
}

data class AdminCode(
    val code: String,
    val offer: String,
    val eligible: String,
    val starts: String,
    val ends: String,
    val used: Int,
    /** Null means the code has no usage cap. */
    val limit: Int?,
    val state: AdminCodeState,
    val note: String? = null,
)

object SpaCatalog {
    val services = listOf(
        Service("stone", "Warm Stone Massage", 90, 165, ServiceCategory.MASSAGE, "Basalt stones, full-body pressure work, scalp finish"),
        Service("jade-facial", "Jade Roller Facial", 60, 120, ServiceCategory.FACIAL, "Double cleanse, enzyme mask, cooled jade rolling"),
        Service("deep", "Deep Tissue Massage", 75, 140, ServiceCategory.MASSAGE, "Targeted back, neck and shoulder release"),
        Service("scrub", "Matcha Body Polish", 50, 98, ServiceCategory.BODY, "Green tea salt scrub, rinse, hydrating balm"),
        Service("herbal", "Herbal Compress Ritual", 75, 150, ServiceCategory.BODY, "Steamed lemongrass compresses, light oil massage"),
    )

    val therapists = listOf(
        Therapist("mai", "Mai Tran", "Stone & deep tissue", "Today 2:30 PM"),
        Therapist("lena", "Lena Okafor", "Facials & skin", "Today 4:00 PM"),
        Therapist("tomas", "Tomas Ruiz", "Sports & recovery", "Tomorrow 10:00 AM"),
        Therapist("any", "First available", "Fastest confirmation", "Today 1:00 PM", anyAvailable = true),
    )

    /** The three therapists with a column on the staff schedule; "first available" is not one. */
    val staff = therapists.filterNot { it.anyAvailable }

    val promos = listOf(
        Promo(
            code = "JADE20",
            title = "20% off any facial",
            percentOff = 20,
            eligible = setOf(ServiceCategory.FACIAL),
            validUntil = "Oct 31, 2026",
            terms = "Facials only. One use per guest.",
        ),
        Promo(
            code = "MIDWEEK15",
            title = "$15 off weekday massages",
            amountOffUsd = 15,
            minSpendUsd = 120,
            eligible = setOf(ServiceCategory.MASSAGE),
            validUntil = "Nov 15, 2026",
            terms = "Tue-Thu appointments. Minimum spend $120.",
        ),
        Promo(
            code = "FIRSTVISIT",
            title = "10% off your first booking",
            percentOff = 10,
            validUntil = "Dec 31, 2026",
            terms = "New guests. Any treatment.",
        ),
    )

    /** The code a new account is welcomed with; the sign-up screen hands it to the booking flow. */
    const val NEW_GUEST_CODE = "FIRSTVISIT"

    val slotTimes = listOf("9:00 AM", "10:00 AM", "11:30 AM", "1:00 PM", "2:30 PM", "4:00 PM", "5:30 PM", "7:00 PM")

    /** The day the design was drawn against; the demo catalogue is anchored to it. */
    val today = SpaDate(2026, 10, 15)

    val requests = listOf(
        BookingRequest(
            id = "BK-2417", guest = "Hannah Pham", phone = "(415) 555-0142", serviceId = "stone", therapistId = "mai",
            startHour = 14.5, requested = "9 min ago", status = RequestStatus.PENDING, code = "MIDWEEK15",
            discountUsd = 15, note = "Lower back sensitivity, medium pressure please.",
        ),
        BookingRequest(
            id = "BK-2416", guest = "Diego Alvarez", phone = "(415) 555-0178", serviceId = "deep", therapistId = "tomas",
            startHour = 11.0, requested = "24 min ago", status = RequestStatus.PENDING,
            conflict = "Overlaps Tomas 11:00-12:15 with BK-2402",
        ),
        BookingRequest(
            id = "BK-2415", guest = "Priya Natarajan", phone = "(415) 555-0119", serviceId = "jade-facial",
            therapistId = "lena", startHour = 16.0, requested = "41 min ago", status = RequestStatus.PENDING,
            code = "JADE20", discountUsd = 24,
        ),
        BookingRequest(
            id = "BK-2413", guest = "Grace Kim", phone = "(415) 555-0163", serviceId = "herbal", therapistId = "mai",
            startHour = 17.5, requested = "1 h ago", status = RequestStatus.PENDING,
            note = "First visit. Prefers unscented oil.",
        ),
        BookingRequest(
            id = "BK-2411",
            guest = "Owen Becker",
            phone = "(415) 555-0107",
            serviceId = "scrub",
            therapistId = "lena",
            startHour = 13.0,
            requested = "2 h ago",
            status = RequestStatus.PENDING,
        ),
    )

    val scheduled = listOf(
        ScheduledBooking("BK-2398", "R. Silva", "deep", "mai", 9.0),
        ScheduledBooking("BK-2402", "J. Moreau", "deep", "tomas", 10.5),
        ScheduledBooking("BK-2404", "A. Haddad", "jade-facial", "lena", 10.0),
        ScheduledBooking("BK-2406", "M. Ito", "stone", "mai", 11.5),
        ScheduledBooking("BK-2408", "S. Brooks", "stone", "tomas", 14.0),
        ScheduledBooking("BK-2409", "L. Nguyen", "herbal", "lena", 18.0),
    )

    val adminCodes = listOf(
        AdminCode("JADE20", "20% off", "Facials", "Oct 1", "Oct 31", 184, 250, AdminCodeState.LIVE, "Ends in 16 days"),
        AdminCode("MIDWEEK15", "$15 off", "Massages - Tue-Thu - min $120", "Oct 6", "Nov 15", 62, null, AdminCodeState.LIVE),
        AdminCode("FIRSTVISIT", "10% off", "All treatments - new guests", "Jan 1", "Dec 31", 412, null, AdminCodeState.LIVE),
        AdminCode("WINTERSTEAM", "$25 off", "Herbal Compress Ritual", "Dec 1", "Jan 31", 0, 120, AdminCodeState.SCHEDULED),
        AdminCode("SUMMERGLOW", "15% off", "Facials - Body", "Jun 1", "Aug 31", 297, 300, AdminCodeState.EXPIRED),
        AdminCode(
            "STAFF50", "50% off", "All treatments", "Mar 1", "Sep 30", 38, null, AdminCodeState.DISABLED,
            "Disabled Sep 30 - shared publicly",
        ),
    )

    fun service(id: String): Service? = services.firstOrNull { it.id == id }

    fun therapist(id: String): Therapist? = therapists.firstOrNull { it.id == id }

    /** Deterministic "already booked" slots, so the demo looks like a real diary. */
    fun isSlotTaken(date: SpaDate, slotIndex: Int): Boolean = (date.day * 7 + slotIndex * 3) % 5 == 0

    /** The house is closed on Mondays, and the demo marks every eleventh day as fully booked. */
    fun isDayFull(date: SpaDate): Boolean = date.weekdayIndex == 0 || date.day % 11 == 0
}

/** "2:30 PM" from an hour-of-day such as 14.5. */
fun formatHour(hour: Double): String {
    val whole = hour.toInt()
    val minutes = ((hour - whole) * 60).toInt()
    val suffix = if (whole >= 12) "PM" else "AM"
    val hour12 = if (whole % 12 == 0) 12 else whole % 12
    return "$hour12:${minutes.toString().padStart(2, '0')} $suffix"
}

fun initialsOf(name: String): String = name.split(" ").mapNotNull { it.firstOrNull() }.joinToString("").take(2).uppercase()

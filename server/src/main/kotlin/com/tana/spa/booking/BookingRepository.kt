package com.tana.spa.booking

import java.time.Instant

/** A treatment on the menu. [price] is a whole unit of the catalogue's currency. */
data class ServiceRow(
    val id: String,
    val slug: String,
    val name: String,
    val categorySlug: String,
    val description: String,
    val price: Long,
    val minutes: Int,
)

data class TherapistRow(val id: String, val slug: String, val name: String, val focus: String, val accent: String)

/** A `vouchers` row, read as a discount code. [endsAt] null means it never expires. */
data class VoucherRow(
    val id: String,
    val code: String,
    val title: String,
    val description: String?,
    val percent: Boolean,
    val value: Long,
    val minOrder: Long,
    val startsAt: Instant,
    val endsAt: Instant?,
    val usageLimit: Int?,
    val usedCount: Int,
    val active: Boolean,
    val eligibleCategories: List<String>,
)

data class BookingRow(
    val id: String,
    val reference: String,
    val userId: String,
    val service: ServiceRow,
    val therapist: TherapistRow?,
    val startAt: Instant,
    val endAt: Instant,
    val status: String,
    val guestName: String,
    val guestPhone: String,
    val note: String?,
    val subtotal: Long,
    val discount: Long,
    val total: Long,
    val code: String?,
    val createdAt: Instant,
    val declineReason: String?,
    val alternativeOffer: String?,
)

data class NewBooking(
    val userId: String,
    val serviceId: String,
    val therapistId: String,
    val startAt: Instant,
    val endAt: Instant,
    val guestName: String,
    val guestPhone: String,
    val note: String?,
    val subtotal: Long,
    val discount: Long,
    val voucherId: String?,
)

data class NewVoucher(
    val code: String,
    val title: String,
    val percent: Boolean,
    val value: Long,
    val minOrder: Long,
    val startsAt: Instant,
    val endsAt: Instant?,
    val usageLimit: Int?,
    val eligibleCategories: List<String>,
)

/**
 * Every call blocks on JDBC; the service layer moves them to `Dispatchers.IO`.
 *
 * Schema: `V2__catalog.sql`, `V3__commerce.sql` and `V9__spa_booking.sql`.
 */
interface BookingRepository {
    fun services(): List<ServiceRow>

    fun serviceBySlug(slug: String): ServiceRow?

    fun therapists(): List<TherapistRow>

    fun therapistBySlug(slug: String): TherapistRow?

    fun vouchers(): List<VoucherRow>

    fun voucherByCode(code: String): VoucherRow?

    fun insertVoucher(voucher: NewVoucher): Boolean

    fun disableVoucher(code: String): Boolean

    /**
     * Records that [userId] now holds [voucherId].
     *
     * @return false when the guest already held it, so one code is never announced to them twice.
     */
    fun grantVoucher(userId: String, voucherId: String, source: String): Boolean

    /** Bookings that hold a therapist's time: pending and confirmed, within [from] until [to]. */
    fun bookingsFor(therapistId: String?, from: Instant, to: Instant): List<BookingRow>

    fun bookingsOf(userId: String): List<BookingRow>

    fun pendingBookings(): List<BookingRow>

    fun booking(id: String): BookingRow?

    fun insertBooking(booking: NewBooking): BookingRow

    /** @return false when the row was no longer pending, so a double decision cannot land. */
    fun decide(id: String, status: String, decidedBy: String, declineReason: String?, alternativeOffer: String?): Boolean
}

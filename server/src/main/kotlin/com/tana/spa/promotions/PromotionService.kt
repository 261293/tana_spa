package com.tana.spa.promotions

import com.tana.spa.auth.UserRepository
import com.tana.spa.booking.BookingRepository
import com.tana.spa.booking.VoucherRow
import com.tana.spa.notifications.NotificationService
import com.tana.spa.shared.notifications.NotificationSendRequest
import com.tana.spa.shared.notifications.NotificationType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Matches `promotions_valid_until` on the client, so a code reads the same in both places. */
private val VALID_UNTIL: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

/** `user_vouchers.source` values; the column's CHECK constraint rejects anything else. */
object GrantSource {
    const val CAMPAIGN = "campaign"
    const val GIFT = "gift"
}

/**
 * Tells a guest about a discount code they can use: the welcome code a new account is given, and
 * every code the desk publishes afterwards.
 *
 * A grant is recorded in `user_vouchers` before the guest is told, and a repeated grant is silent,
 * so the same code never produces two notifications for one person.
 */
class PromotionService(
    private val vouchers: BookingRepository,
    private val users: UserRepository,
    private val notifications: NotificationService,
    private val clock: () -> Instant = Instant::now,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val log = LoggerFactory.getLogger(PromotionService::class.java)

    /**
     * Gives [code] to one guest and notifies them.
     *
     * @return false when the code is unknown, no longer live, or the guest already held it.
     */
    suspend fun grant(userId: String, code: String, source: String = GrantSource.CAMPAIGN): Boolean {
        val voucher = liveVoucher(code) ?: return false
        if (!withContext(io) { vouchers.grantVoucher(userId, voucher.id, source) }) return false
        log.info("code {} granted to user {} ({})", voucher.code, userId, source)
        notify(userId, "Welcome gift", voucher)
        return true
    }

    /**
     * Tells every active account about a newly published [code].
     *
     * @return how many guests were notified.
     */
    suspend fun announce(code: String): Int {
        val voucher = liveVoucher(code) ?: return 0
        val audience = withContext(io) { users.activeIds(ANNOUNCEMENT_LIMIT) }
        audience.forEach { notify(it, "New offer", voucher) }
        log.info("code {} announced to {} guests", voucher.code, audience.size)
        return audience.size
    }

    private suspend fun liveVoucher(code: String): VoucherRow? {
        val trimmed = code.trim().uppercase()
        if (trimmed.isEmpty()) return null
        val voucher = withContext(io) { vouchers.voucherByCode(trimmed) } ?: return null
        val now = clock()
        val live = voucher.active && voucher.startsAt <= now && (voucher.endsAt == null || voucher.endsAt > now)
        return voucher.takeIf { live }
    }

    private suspend fun notify(userId: String, title: String, voucher: VoucherRow) {
        notifications.send(
            NotificationSendRequest(
                userId = userId,
                type = NotificationType.PROMOTION.wire,
                title = title,
                body = body(voucher),
                data = mapOf("code" to voucher.code),
            ),
        )
    }

    /** A code with no end date simply has no validity sentence; it never expires. */
    private fun body(voucher: VoucherRow): String = buildString {
        append(voucher.title.trimEnd('.'))
        append(". Use ")
        append(voucher.code)
        append('.')
        voucher.endsAt?.let { append(" Valid until ${LocalDate.ofInstant(it, ZoneOffset.UTC).format(VALID_UNTIL)}.") }
    }

    private companion object {
        /**
         * An announcement writes one row per guest, so it is capped rather than unbounded: a
         * larger audience needs a background job, not a request thread.
         */
        const val ANNOUNCEMENT_LIMIT = 5_000
    }
}

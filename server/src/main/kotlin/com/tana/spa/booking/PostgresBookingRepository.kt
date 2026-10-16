package com.tana.spa.booking

import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource

/** Schema: `V2__catalog.sql`, `V3__commerce.sql`, `V9__spa_booking.sql`. */
class PostgresBookingRepository(private val dataSource: DataSource) : BookingRepository {

    override fun services(): List<ServiceRow> = query(
        "$SERVICE_SELECT WHERE s.is_active ORDER BY s.sort_order",
    ) { it.readService() }

    override fun serviceBySlug(slug: String): ServiceRow? = query(
        "$SERVICE_SELECT WHERE s.slug = ? AND s.is_active",
        slug,
    ) { it.readService() }.firstOrNull()

    override fun therapists(): List<TherapistRow> = query(
        "SELECT id, slug, name, focus, focus_vi, accent FROM therapists WHERE is_active ORDER BY sort_order",
    ) { it.readTherapist() }

    override fun therapistBySlug(slug: String): TherapistRow? = query(
        "SELECT id, slug, name, focus, focus_vi, accent FROM therapists WHERE slug = ? AND is_active",
        slug,
    ) { it.readTherapist() }.firstOrNull()

    override fun vouchers(): List<VoucherRow> = query("$VOUCHER_SELECT ORDER BY starts_at DESC") { it.readVoucher() }

    override fun voucherByCode(code: String): VoucherRow? = query("$VOUCHER_SELECT WHERE code = ?", code) { it.readVoucher() }.firstOrNull()

    override fun insertVoucher(voucher: NewVoucher): Boolean = dataSource.connection.use { c ->
        c.prepareStatement(
            """INSERT INTO vouchers (code, title, discount_type, discount_value, min_order_vnd, applies_to,
                                     starts_at, ends_at, usage_limit, eligible_categories)
               VALUES (?, ?, ?, ?, ?, 'service', ?, ?, ?, ?)
               ON CONFLICT (code) DO NOTHING""",
        ).use { ps ->
            ps.setString(1, voucher.code)
            ps.setString(2, voucher.title)
            ps.setString(3, if (voucher.percent) "percent" else "fixed")
            ps.setLong(4, voucher.value)
            ps.setLong(5, voucher.minOrder)
            ps.setTimestamp(6, Timestamp.from(voucher.startsAt))
            ps.setTimestamp(7, voucher.endsAt?.let(Timestamp::from))
            ps.setObject(8, voucher.usageLimit)
            ps.setArray(9, c.categoriesArray(voucher.eligibleCategories))
            ps.executeUpdate() == 1
        }
    }

    override fun disableVoucher(code: String): Boolean = dataSource.connection.use { c ->
        c.prepareStatement("UPDATE vouchers SET is_active = false WHERE code = ? AND is_active").use { ps ->
            ps.setString(1, code)
            ps.executeUpdate() == 1
        }
    }

    override fun grantVoucher(userId: String, voucherId: String, source: String): Boolean = dataSource.connection.use { c ->
        // The primary key (user_id, voucher_id) is what makes a repeated grant a no-op.
        c.prepareStatement(
            """INSERT INTO user_vouchers (user_id, voucher_id, source)
                   VALUES (?::uuid, ?::uuid, ?)
                   ON CONFLICT DO NOTHING""",
        ).use { ps ->
            ps.setString(1, userId)
            ps.setString(2, voucherId)
            ps.setString(3, source)
            ps.executeUpdate() == 1
        }
    }

    override fun bookingsFor(therapistId: String?, from: Instant, to: Instant): List<BookingRow> {
        val sql = buildString {
            append(BOOKING_SELECT)
            append(" WHERE b.status IN ('pending', 'confirmed') AND b.start_at >= ? AND b.start_at < ?")
            if (therapistId != null) append(" AND b.therapist_id = ?::uuid")
            append(" ORDER BY b.start_at")
        }
        return dataSource.connection.use { c ->
            c.prepareStatement(sql).use { ps ->
                ps.setTimestamp(1, Timestamp.from(from))
                ps.setTimestamp(2, Timestamp.from(to))
                if (therapistId != null) ps.setString(3, therapistId)
                ps.executeQuery().use { rs -> rs.readAll { it.readBooking() } }
            }
        }
    }

    override fun bookingsOf(userId: String): List<BookingRow> = query(
        "$BOOKING_SELECT WHERE b.user_id = ?::uuid ORDER BY b.start_at DESC LIMIT 50",
        userId,
    ) { it.readBooking() }

    override fun pendingBookings(): List<BookingRow> = query(
        "$BOOKING_SELECT WHERE b.status = 'pending' ORDER BY b.created_at DESC",
    ) { it.readBooking() }

    override fun booking(id: String): BookingRow? = query("$BOOKING_SELECT WHERE b.id = ?::uuid", id) { it.readBooking() }.firstOrNull()

    override fun insertBooking(booking: NewBooking): BookingRow = dataSource.connection.use { c ->
        val id = c.prepareStatement(
            """INSERT INTO bookings (user_id, branch_id, therapist_id, status, start_at, end_at,
                                     customer_name, customer_phone, note, subtotal_vnd, discount_vnd,
                                     total_vnd, voucher_id)
               VALUES (?::uuid, (SELECT id FROM branches WHERE is_active ORDER BY created_at LIMIT 1),
                       ?::uuid, 'pending', ?, ?, ?, ?, ?, ?, ?, ?, ?::uuid)
               RETURNING id""",
        ).use { ps ->
            ps.setString(1, booking.userId)
            ps.setString(2, booking.therapistId)
            ps.setTimestamp(3, Timestamp.from(booking.startAt))
            ps.setTimestamp(4, Timestamp.from(booking.endAt))
            ps.setString(5, booking.guestName)
            ps.setString(6, booking.guestPhone)
            ps.setString(7, booking.note)
            ps.setLong(8, booking.subtotal)
            ps.setLong(9, booking.discount)
            ps.setLong(10, booking.subtotal - booking.discount)
            ps.setString(11, booking.voucherId)
            ps.executeQuery().use { rs ->
                rs.next()
                rs.getString("id")
            }
        }
        // The booking's line keeps the price and duration as they were on the day.
        c.prepareStatement(
            """INSERT INTO booking_items (booking_id, service_id, service_name, unit_price_vnd, duration_minutes)
               SELECT ?::uuid, s.id, s.name, s.price_vnd, s.duration_minutes FROM services s WHERE s.id = ?::uuid""",
        ).use { ps ->
            ps.setString(1, id)
            ps.setString(2, booking.serviceId)
            ps.executeUpdate()
        }
        checkNotNull(booking(id)) { "booking $id vanished between insert and read" }
    }

    override fun decide(id: String, status: String, decidedBy: String, declineReason: String?, alternativeOffer: String?): Boolean =
        dataSource.connection.use { c ->
            // `AND status = 'pending'` is the lock: two desks pressing confirm leaves one update.
            c.prepareStatement(
                """UPDATE bookings
               SET status = ?, decided_at = now(), decided_by = ?::uuid,
                   decline_reason = ?, alternative_offer = ?, updated_at = now()
               WHERE id = ?::uuid AND status = 'pending'""",
            ).use { ps ->
                ps.setString(1, status)
                ps.setString(2, decidedBy)
                ps.setString(3, declineReason)
                ps.setString(4, alternativeOffer)
                ps.setString(5, id)
                ps.executeUpdate() == 1
            }
        }

    override fun transition(id: String, from: List<String>, to: String, decidedBy: String?): Boolean = dataSource.connection.use { c ->
        // The status list is part of the WHERE, so the update itself is the lock.
        val allowed = from.joinToString(", ") { "?" }
        c.prepareStatement(
            """UPDATE bookings
                   SET status = ?, decided_at = now(), decided_by = COALESCE(?::uuid, decided_by),
                       cancelled_at = CASE WHEN ? = 'cancelled' THEN now() ELSE cancelled_at END,
                       updated_at = now()
                   WHERE id = ?::uuid AND status IN ($allowed)""",
        ).use { ps ->
            ps.setString(1, to)
            ps.setString(2, decidedBy)
            // `bookings_cancel_consistent`: a cancelled row has to carry when it was cancelled.
            ps.setString(3, to)
            ps.setString(4, id)
            from.forEachIndexed { index, status -> ps.setString(5 + index, status) }
            ps.executeUpdate() == 1
        }
    }

    override fun insertService(service: NewService): ServiceRow? = dataSource.connection.use { c ->
        val inserted = c.prepareStatement(
            """INSERT INTO services (category_id, name, slug, description, price_vnd, duration_minutes, sort_order)
               SELECT cat.id, ?, ?, ?, ?, ?, COALESCE((SELECT max(sort_order) + 1 FROM services), 1)
               FROM service_categories cat WHERE cat.slug = ?
               ON CONFLICT (slug) DO NOTHING""",
        ).use { ps ->
            ps.setString(1, service.name)
            ps.setString(2, service.slug)
            ps.setString(3, service.description)
            ps.setLong(4, service.price)
            ps.setInt(5, service.minutes)
            ps.setString(6, service.categorySlug)
            ps.executeUpdate() == 1
        }
        if (inserted) serviceBySlug(service.slug) else null
    }

    override fun insertTherapist(therapist: NewTherapist): TherapistRow? = dataSource.connection.use { c ->
        val inserted = c.prepareStatement(
            """INSERT INTO therapists (slug, name, focus, accent, sort_order)
               VALUES (?, ?, ?, ?, COALESCE((SELECT max(sort_order) + 1 FROM therapists), 1))
               ON CONFLICT (slug) DO NOTHING""",
        ).use { ps ->
            ps.setString(1, therapist.slug)
            ps.setString(2, therapist.name)
            ps.setString(3, therapist.focus)
            ps.setString(4, therapist.accent)
            ps.executeUpdate() == 1
        }
        if (inserted) therapistBySlug(therapist.slug) else null
    }

    override fun claimRemindable(now: Instant, before: Instant): List<BookingRow> {
        val claimed = dataSource.connection.use { c ->
            c.prepareStatement(
                """UPDATE bookings SET reminder_sent_at = now(), updated_at = now()
                   WHERE status = 'confirmed' AND reminder_sent_at IS NULL
                     AND start_at > ? AND start_at <= ?
                   RETURNING id""",
            ).use { ps ->
                ps.setTimestamp(1, Timestamp.from(now))
                ps.setTimestamp(2, Timestamp.from(before))
                ps.executeQuery().use { rs -> rs.readAll { it.getString("id") } }
            }
        }
        return claimed.mapNotNull { booking(it) }
    }

    override fun releaseReminder(id: String) {
        dataSource.connection.use { c ->
            c.prepareStatement("UPDATE bookings SET reminder_sent_at = NULL, updated_at = now() WHERE id = ?::uuid").use { ps ->
                ps.setString(1, id)
                ps.executeUpdate()
            }
        }
    }

    private fun <T> query(sql: String, vararg args: String, read: (ResultSet) -> T): List<T> = dataSource.connection.use { c ->
        c.prepareStatement(sql).use { ps ->
            args.forEachIndexed { index, value -> ps.setString(index + 1, value) }
            ps.executeQuery().use { rs -> rs.readAll(read) }
        }
    }

    private fun <T> ResultSet.readAll(read: (ResultSet) -> T): List<T> = buildList {
        while (next()) add(read(this@readAll))
    }

    private fun Connection.categoriesArray(values: List<String>) =
        if (values.isEmpty()) null else createArrayOf("text", values.toTypedArray())

    private fun ResultSet.readService() = ServiceRow(
        id = getString("service_id"),
        slug = getString("service_slug"),
        name = getString("service_name"),
        categorySlug = getString("category_slug"),
        description = getString("description").orEmpty(),
        price = getLong("price_vnd"),
        minutes = getInt("duration_minutes"),
        nameVi = getString("service_name_vi"),
        descriptionVi = getString("service_description_vi"),
    )

    private fun ResultSet.readTherapist() = TherapistRow(
        id = getString("id"),
        slug = getString("slug"),
        name = getString("name"),
        focus = getString("focus"),
        accent = getString("accent"),
        focusVi = getString("focus_vi"),
    )

    private fun ResultSet.readVoucher() = VoucherRow(
        id = getString("id"),
        code = getString("code"),
        title = getString("title"),
        description = getString("description"),
        percent = getString("discount_type") == "percent",
        value = getLong("discount_value"),
        minOrder = getLong("min_order_vnd"),
        startsAt = getTimestamp("starts_at").toInstant(),
        endsAt = getTimestamp("ends_at")?.toInstant(),
        usageLimit = getObject("usage_limit") as? Int,
        usedCount = getInt("used_count"),
        active = getBoolean("is_active"),
        eligibleCategories = (getArray("eligible_categories")?.array as? Array<*>)
            ?.filterIsInstance<String>()
            .orEmpty(),
    )

    private fun ResultSet.readBooking(): BookingRow {
        val therapistId = getString("therapist_id")
        return BookingRow(
            id = getString("id"),
            reference = "BK-" + getLong("booking_no"),
            userId = getString("user_id"),
            service = readService(),
            therapist = if (therapistId == null) {
                null
            } else {
                TherapistRow(
                    id = therapistId,
                    slug = getString("therapist_slug"),
                    name = getString("therapist_name"),
                    focus = getString("therapist_focus"),
                    focusVi = getString("therapist_focus_vi"),
                    accent = getString("therapist_accent"),
                )
            },
            startAt = getTimestamp("start_at").toInstant(),
            endAt = getTimestamp("end_at").toInstant(),
            status = getString("status"),
            guestName = getString("customer_name"),
            guestPhone = getString("customer_phone"),
            note = getString("note"),
            subtotal = getLong("subtotal_vnd"),
            discount = getLong("discount_vnd"),
            total = getLong("total_vnd"),
            code = getString("voucher_code"),
            createdAt = getTimestamp("created_at").toInstant(),
            declineReason = getString("decline_reason"),
            alternativeOffer = getString("alternative_offer"),
        )
    }

    private companion object {
        // The Vietnamese columns travel with the base ones so one reader serves both languages.
        const val SERVICE_SELECT = """
            SELECT s.id AS service_id, s.slug AS service_slug, s.name AS service_name,
                   c.slug AS category_slug, s.description, s.price_vnd, s.duration_minutes,
                   s.name_vi AS service_name_vi, s.description_vi AS service_description_vi
            FROM services s JOIN service_categories c ON c.id = s.category_id
        """

        const val VOUCHER_SELECT = """
            SELECT id, code, title, description, discount_type, discount_value, min_order_vnd,
                   starts_at, ends_at, usage_limit, used_count, is_active, eligible_categories
            FROM vouchers
        """

        /** A booking carries its treatment and its therapist; the item row holds the day's price. */
        const val BOOKING_SELECT = """
            SELECT b.id, b.booking_no, b.user_id, b.start_at, b.end_at, b.status, b.customer_name,
                   b.customer_phone, b.note, b.subtotal_vnd, b.discount_vnd, b.total_vnd,
                   b.created_at, b.decline_reason, b.alternative_offer,
                   b.therapist_id, t.slug AS therapist_slug, t.name AS therapist_name,
                   t.focus AS therapist_focus, t.focus_vi AS therapist_focus_vi, t.accent AS therapist_accent,
                   s.id AS service_id, s.slug AS service_slug, s.name AS service_name,
                   c.slug AS category_slug, s.description, s.price_vnd, s.duration_minutes,
                   s.name_vi AS service_name_vi, s.description_vi AS service_description_vi,
                   v.code AS voucher_code
            FROM bookings b
            JOIN booking_items bi ON bi.booking_id = b.id
            JOIN services s ON s.id = bi.service_id
            JOIN service_categories c ON c.id = s.category_id
            LEFT JOIN therapists t ON t.id = b.therapist_id
            LEFT JOIN vouchers v ON v.id = b.voucher_id
        """
    }
}

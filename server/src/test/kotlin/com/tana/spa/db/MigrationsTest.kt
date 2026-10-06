package com.tana.spa.db

import org.postgresql.util.PSQLException
import java.sql.Connection
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Applies V1..V5 to a throw-away PostgreSQL and checks that the constraints in the schema
 * really reject bad data, since those rules are the last line of defence behind the app code.
 */
class MigrationsTest {

    private companion object {
        const val UNIQUE_VIOLATION = "23505"
        const val FK_VIOLATION = "23503"
        const val CHECK_VIOLATION = "23514"

        val dataSource: DataSource get() = TestDatabase.dataSource
    }

    // Returns Unit on purpose: JUnit 4 rejects @Test methods that return a value.
    private fun db(block: (Connection) -> Unit) = dataSource.connection.use(block)

    private fun Connection.exec(sql: String, vararg params: Any?) {
        prepareStatement(sql).use { ps ->
            params.forEachIndexed { i, p -> ps.setObject(i + 1, p) }
            ps.execute()
        }
    }

    private fun Connection.queryUuid(sql: String, vararg params: Any?): UUID = prepareStatement(sql).use { ps ->
        params.forEachIndexed { i, p -> ps.setObject(i + 1, p) }
        ps.executeQuery().use { rs ->
            rs.next()
            rs.getObject(1, UUID::class.java)
        }
    }

    private fun Connection.queryLong(sql: String, vararg params: Any?): Long = prepareStatement(sql).use { ps ->
        params.forEachIndexed { i, p -> ps.setObject(i + 1, p) }
        ps.executeQuery().use { rs ->
            rs.next()
            rs.getLong(1)
        }
    }

    private fun assertViolation(state: String, block: () -> Unit) {
        val e = assertFailsWith<PSQLException> { block() }
        assertEquals(state, e.sqlState, "unexpected SQLSTATE: ${e.message}")
    }

    private fun email() = "u${UUID.randomUUID().toString().take(8)}@example.com"

    private fun Connection.newUser(email: String = email()): UUID =
        queryUuid("INSERT INTO users (email, full_name) VALUES (?, 'Lan') RETURNING id", email)

    private fun Connection.newBranch(): UUID =
        queryUuid("INSERT INTO branches (name, address_line, city) VALUES ('Q1', '1 Le Loi', 'HCM') RETURNING id")

    @Test
    fun allMigrationsApplyAndCreateTheExpectedTables() = db { c ->
        val tables = buildSet {
            c.createStatement().executeQuery(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
            ).use { rs -> while (rs.next()) add(rs.getString(1)) }
        }
        val expected = setOf(
            "users", "auth_identities", "refresh_tokens", "user_addresses",
            "branches", "service_categories", "services", "combo_items", "branch_services",
            "products", "product_variants", "product_images", "favorites", "banners",
            "vouchers", "user_vouchers", "carts", "cart_items", "orders", "order_items",
            "bookings", "booking_items", "payments", "voucher_redemptions",
            "feedback", "notifications", "phone_verifications", "v_user_history",
        )
        assertTrue(tables.containsAll(expected), "missing: ${expected - tables}")
    }

    @Test
    fun emailMustBeLowercaseAndUniqueAmongActiveUsers() = db { c ->
        val email = email()
        val first = c.newUser(email)

        assertViolation(UNIQUE_VIOLATION) { c.newUser(email) }
        assertViolation(CHECK_VIOLATION) { c.newUser("Upper.${email()}") }

        // Soft-deleting frees the address for a new registration.
        c.exec("UPDATE users SET deleted_at = now() WHERE id = ?", first)
        c.newUser(email)
    }

    @Test
    fun phoneFormatIsEnforced() = db { c ->
        c.exec("INSERT INTO users (email, full_name, phone) VALUES (?, 'A', '0333433969')", email())
        assertViolation(CHECK_VIOLATION) {
            c.exec("INSERT INTO users (email, full_name, phone) VALUES (?, 'A', 'call-me')", email())
        }
    }

    @Test
    fun updatedAtIsMaintainedByATrigger() = db { c ->
        val id = c.newUser()
        c.exec("UPDATE users SET updated_at = now() - interval '1 day' WHERE id = ?", id)
        // the trigger overrides the stale value on the next real update
        c.exec("UPDATE users SET full_name = 'Lan 2' WHERE id = ?", id)
        val age = c.queryLong(
            "SELECT extract(epoch FROM (now() - updated_at))::bigint FROM users WHERE id = ?",
            id,
        )
        assertTrue(age < 60, "updated_at was not refreshed (age=${age}s)")
    }

    @Test
    fun onlyOneDefaultAddressPerUser() = db { c ->
        val user = c.newUser()
        fun address(default: Boolean) = c.exec(
            """INSERT INTO user_addresses (user_id, recipient_name, phone, address_line, city, is_default)
               VALUES (?, 'Lan', '0333433969', '26 Duong 34', 'HCM', ?)""",
            user,
            default,
        )
        address(true)
        address(false)
        assertViolation(UNIQUE_VIOLATION) { address(true) }
    }

    @Test
    fun serviceSearchIgnoresVietnameseAccents() = db { c ->
        val category = c.queryUuid("INSERT INTO service_categories (name, slug) VALUES ('Da', ?) RETURNING id", "da-${UUID.randomUUID()}")
        c.exec(
            """INSERT INTO services (category_id, name, slug, price_vnd, duration_minutes)
               VALUES (?, 'Chăm sóc da chuyên sâu', ?, 399000, 60)""",
            category,
            "cs-${UUID.randomUUID()}",
        )
        val hits = c.queryLong(
            "SELECT count(*) FROM services WHERE app_unaccent(lower(name)) LIKE '%' || app_unaccent(lower(?)) || '%'",
            "CHAM SOC DA",
        )
        assertEquals(1, hits)
        // đ has its own base letter in Vietnamese; make sure it folds too
        assertEquals("dang nhap", c.queryString("SELECT app_unaccent('đăng nhập')"))
    }

    private fun Connection.queryString(sql: String): String = createStatement().executeQuery(sql).use { rs ->
        rs.next()
        rs.getString(1)
    }

    @Test
    fun stockAndVariantOptionsAreProtected() = db { c ->
        val product = c.queryUuid("INSERT INTO products (name, slug) VALUES ('Sua rua mat', ?) RETURNING id", "p-${UUID.randomUUID()}")
        fun variant(sku: String, size: String?, stock: Int = 5) = c.exec(
            "INSERT INTO product_variants (product_id, sku, size_label, price_vnd, stock_qty) VALUES (?, ?, ?, 198000, ?)",
            product,
            sku,
            size,
            stock,
        )
        variant("SKU-${UUID.randomUUID()}", "100ML")
        assertViolation(UNIQUE_VIOLATION) { variant("SKU-${UUID.randomUUID()}", "100ML") }
        assertViolation(CHECK_VIOLATION) { variant("SKU-${UUID.randomUUID()}", "50ML", stock = -1) }
        // NULL size must still be unique, which is why the index coalesces
        variant("SKU-${UUID.randomUUID()}", null)
        assertViolation(UNIQUE_VIOLATION) { variant("SKU-${UUID.randomUUID()}", null) }
    }

    @Test
    fun cartQuantityMustBePositive() = db { c ->
        val user = c.newUser()
        val product = c.queryUuid("INSERT INTO products (name, slug) VALUES ('P', ?) RETURNING id", "p-${UUID.randomUUID()}")
        val variant = c.queryUuid(
            "INSERT INTO product_variants (product_id, sku, price_vnd) VALUES (?, ?, 1000) RETURNING id",
            product,
            "SKU-${UUID.randomUUID()}",
        )
        c.exec("INSERT INTO carts (user_id) VALUES (?)", user)
        c.exec("INSERT INTO cart_items (user_id, variant_id, quantity) VALUES (?, ?, 2)", user, variant)
        assertViolation(UNIQUE_VIOLATION) {
            c.exec("INSERT INTO cart_items (user_id, variant_id, quantity) VALUES (?, ?, 1)", user, variant)
        }
        assertViolation(CHECK_VIOLATION) { c.exec("UPDATE cart_items SET quantity = 0 WHERE user_id = ?", user) }
    }

    @Test
    fun voucherRulesAreEnforced() = db { c ->
        fun voucher(type: String, value: Long, code: String = "V${UUID.randomUUID().toString().take(8).uppercase()}") = c.exec(
            "INSERT INTO vouchers (code, title, discount_type, discount_value) VALUES (?, 't', ?, ?)",
            code,
            type,
            value,
        )
        voucher("percent", 10)
        voucher("fixed", 50_000)
        assertViolation(CHECK_VIOLATION) { voucher("percent", 150) }
        assertViolation(CHECK_VIOLATION) { voucher("fixed", 0) }
        assertViolation(CHECK_VIOLATION) { voucher("fixed", 1000, code = "lowercase") }
    }

    private fun Connection.newOrder(user: UUID, subtotal: Long, discount: Long, tax: Long, total: Long) = exec(
        """INSERT INTO orders (user_id, recipient_name, recipient_phone, shipping_address,
                               subtotal_vnd, discount_vnd, tax_vnd, total_vnd)
           VALUES (?, 'Lan', '0333433969', '26 Duong 34', ?, ?, ?, ?)""",
        user,
        subtotal,
        discount,
        tax,
        total,
    )

    @Test
    fun orderTotalMustEqualItsParts() = db { c ->
        val user = c.newUser()
        c.newOrder(user, subtotal = 594_000, discount = 0, tax = 0, total = 594_000)
        c.newOrder(user, subtotal = 594_000, discount = 94_000, tax = 0, total = 500_000)
        assertViolation(CHECK_VIOLATION) { c.newOrder(user, 594_000, 0, 0, total = 1) }
        assertViolation(CHECK_VIOLATION) { c.newOrder(user, 100, discount = 200, tax = 0, total = -100) }
    }

    @Test
    fun orderItemLineTotalIsComputedAndHistoryViewMergesOrdersAndBookings() = db { c ->
        val user = c.newUser()
        c.newOrder(user, 594_000, 0, 0, 594_000)
        val order = c.queryUuid("SELECT id FROM orders WHERE user_id = ?", user)
        c.exec(
            "INSERT INTO order_items (order_id, product_name, unit_price_vnd, quantity) VALUES (?, 'Sua rua mat', 198000, 3)",
            order,
        )
        assertEquals(594_000, c.queryLong("SELECT line_total_vnd FROM order_items WHERE order_id = ?", order))

        val branch = c.newBranch()
        c.exec(
            """INSERT INTO bookings (user_id, branch_id, start_at, end_at, customer_name, customer_phone,
                                     subtotal_vnd, total_vnd)
               VALUES (?, ?, now() + interval '1 day', now() + interval '1 day 1 hour', 'Lan', '0333433969', 399000, 399000)""",
            user,
            branch,
        )
        assertEquals(2, c.queryLong("SELECT count(*) FROM v_user_history WHERE user_id = ?", user))
        assertEquals(1, c.queryLong("SELECT count(*) FROM v_user_history WHERE user_id = ? AND kind = 'booking'", user))
    }

    @Test
    fun bookingMustEndAfterItStarts() = db { c ->
        val user = c.newUser()
        val branch = c.newBranch()
        assertViolation(CHECK_VIOLATION) {
            c.exec(
                """INSERT INTO bookings (user_id, branch_id, start_at, end_at, customer_name, customer_phone,
                                         subtotal_vnd, total_vnd)
                   VALUES (?, ?, now() + interval '2 hour', now() + interval '1 hour', 'Lan', '0333433969', 0, 0)""",
                user,
                branch,
            )
        }
    }

    @Test
    fun usersWithHistoryCannotBeHardDeleted() = db { c ->
        val user = c.newUser()
        c.newOrder(user, 1000, 0, 0, 1000)
        assertViolation(FK_VIOLATION) { c.exec("DELETE FROM users WHERE id = ?", user) }
    }

    @Test
    fun paymentMustTargetExactlyOneOfOrderOrBooking() = db { c ->
        assertViolation(CHECK_VIOLATION) {
            c.exec("INSERT INTO payments (method, amount_vnd) VALUES ('cod', 1000)")
        }
    }

    @Test
    fun feedbackRatingAndSingleReviewPerOrder() = db { c ->
        val user = c.newUser()
        c.newOrder(user, 1000, 0, 0, 1000)
        val order = c.queryUuid("SELECT id FROM orders WHERE user_id = ?", user)
        c.exec("INSERT INTO feedback (user_id, order_id, rating) VALUES (?, ?, 5)", user, order)
        assertViolation(UNIQUE_VIOLATION) { c.exec("INSERT INTO feedback (user_id, order_id, rating) VALUES (?, ?, 4)", user, order) }
        assertViolation(CHECK_VIOLATION) { c.exec("INSERT INTO feedback (user_id, rating) VALUES (?, 6)", user) }
    }

    @Test
    fun refreshTokenHashMustBeSha256Sized() = db { c ->
        val user = c.newUser()
        fun token(bytes: ByteArray) = c.exec(
            "INSERT INTO refresh_tokens (user_id, family_id, token_hash, expires_at) VALUES (?, gen_random_uuid(), ?, now() + interval '30 day')",
            user,
            bytes,
        )
        token(ByteArray(32) { it.toByte() })
        assertViolation(CHECK_VIOLATION) { token(ByteArray(5)) }
    }
}

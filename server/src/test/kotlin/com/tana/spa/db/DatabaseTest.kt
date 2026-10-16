package com.tana.spa.db

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The production startup path: DatabaseConfig -> Hikari pool -> Flyway, against a real PostgreSQL. */
class DatabaseTest {

    @Test
    fun connectOpensAPoolAndAppliesEveryMigration() {
        EmbeddedPostgres.start().use { pg ->
            val config = DatabaseConfig(jdbcUrl = pg.getJdbcUrl("postgres", "postgres"), username = "postgres", password = null)

            Database.connect(config).use { pool ->
                pool.connection.use { c ->
                    val applied = c.createStatement().executeQuery("SELECT count(*) FROM flyway_schema_history WHERE success").use { rs ->
                        rs.next()
                        rs.getInt(1)
                    }
                    assertEquals(10, applied)
                    // A table from the last migration proves the whole chain ran in order.
                    c.createStatement().executeQuery("SELECT count(*) FROM therapists").use { rs -> assertTrue(rs.next()) }
                }
                assertEquals("tana-spa", pool.poolName)
            }
        }
    }

    @Test
    fun startingTwiceIsSafeBecauseMigrationsAreIdempotent() {
        EmbeddedPostgres.start().use { pg ->
            val config = DatabaseConfig(pg.getJdbcUrl("postgres", "postgres"), "postgres", null)
            Database.connect(config).close()
            Database.connect(config).use { pool ->
                pool.connection.use { c ->
                    val applied = c.createStatement().executeQuery("SELECT count(*) FROM flyway_schema_history").use { rs ->
                        rs.next()
                        rs.getInt(1)
                    }
                    assertEquals(10, applied, "a second start must not re-run migrations")
                }
            }
        }
    }

    @Test
    fun migrationsCanBeSkippedWhenRunAsADeployStep() {
        EmbeddedPostgres.start().use { pg ->
            val config = DatabaseConfig(pg.getJdbcUrl("postgres", "postgres"), "postgres", null, migrateOnStart = false)
            Database.connect(config).use { pool ->
                pool.connection.use { c ->
                    val users = c.createStatement().executeQuery("SELECT to_regclass('public.users') IS NULL").use { rs ->
                        rs.next()
                        rs.getBoolean(1)
                    }
                    assertTrue(users, "no schema should exist when migration is disabled")
                }
            }
        }
    }

    @Test
    fun anUnreachableDatabaseFailsFastInsteadOfStartingBroken() {
        val config = DatabaseConfig("jdbc:postgresql://127.0.0.1:1/none?connectTimeout=2", "u", "p")
        assertFailsWith<Exception> { Database.connect(config.copy()).close() }
    }

    @Test
    fun configRejectsNonPostgresUrlsAndAbsurdPoolSizes() {
        assertFailsWith<IllegalArgumentException> { DatabaseConfig("postgres://u:p@host/db", null, null) }
        assertFailsWith<IllegalArgumentException> { DatabaseConfig("jdbc:mysql://host/db", null, null) }
        assertFailsWith<IllegalArgumentException> { DatabaseConfig("jdbc:postgresql://h/db", null, null, maxPoolSize = 0) }
    }

    @Test
    fun environmentParsingAndSecretRedaction() {
        assertNull(DatabaseConfig.fromEnvironment(emptyMap()))
        assertNull(DatabaseConfig.fromEnvironment(mapOf("DATABASE_URL" to "  ")))

        val config = assertNotNull(
            DatabaseConfig.fromEnvironment(
                mapOf(
                    "DATABASE_URL" to "jdbc:postgresql://db:5432/tana?sslmode=require&password=inline-secret",
                    "DATABASE_USER" to "app",
                    "DATABASE_PASSWORD" to "hunter2",
                    "DATABASE_POOL_SIZE" to "25",
                    "DATABASE_MIGRATE" to "FALSE",
                ),
            ),
        )
        assertEquals(25, config.maxPoolSize)
        assertFalse(config.migrateOnStart)
        // Neither the password nor query-string secrets may ever reach a log line.
        val text = config.toString()
        assertFalse("hunter2" in text)
        assertFalse("inline-secret" in text)
        assertTrue("db:5432/tana" in text)
    }
}

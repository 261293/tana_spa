package com.tana.spa.db

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import org.flywaydb.core.Flyway
import javax.sql.DataSource

/**
 * One throw-away PostgreSQL shared by every database test in the JVM, migrated once.
 * Tests isolate themselves with unique emails/phones instead of paying for a server per test.
 */
object TestDatabase {
    val dataSource: DataSource by lazy {
        val pg = EmbeddedPostgres.start()
        Runtime.getRuntime().addShutdownHook(Thread { pg.close() })
        val ds = pg.postgresDatabase
        val result = Flyway.configure()
            .dataSource(ds)
            .locations("classpath:db/migration")
            .load()
            .migrate()
        check(result.success) { "migration failed" }
        check(result.migrationsExecuted == 5) { "expected 5 migrations, ran ${result.migrationsExecuted}" }
        ds
    }
}

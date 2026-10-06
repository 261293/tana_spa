package com.tana.spa.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import javax.sql.DataSource

/**
 * Connection settings, read from the environment so no credential lives in source control.
 *
 *  - `DATABASE_URL`      JDBC URL, e.g. `jdbc:postgresql://localhost:5432/tana_spa`  (unset = in-memory mode)
 *  - `DATABASE_USER`     role the app connects as
 *  - `DATABASE_PASSWORD` its password
 *  - `DATABASE_POOL_SIZE` max pooled connections (default 10)
 *  - `DATABASE_MIGRATE`  `false` to skip running Flyway at startup (when migrations run as a deploy step)
 */
data class DatabaseConfig(
    val jdbcUrl: String,
    val username: String?,
    val password: String?,
    val maxPoolSize: Int = 10,
    val migrateOnStart: Boolean = true,
) {
    init {
        require(jdbcUrl.startsWith("jdbc:postgresql:")) {
            "DATABASE_URL must be a JDBC URL starting with 'jdbc:postgresql:' (got '${jdbcUrl.substringBefore('?')}')"
        }
        require(maxPoolSize in 1..200) { "DATABASE_POOL_SIZE must be between 1 and 200" }
    }

    override fun toString() = "DatabaseConfig(jdbcUrl=${jdbcUrl.substringBefore('?')}, username=$username, password=***)"

    companion object {
        /** @return null when `DATABASE_URL` is not set. */
        fun fromEnvironment(env: Map<String, String> = System.getenv()): DatabaseConfig? {
            val url = env["DATABASE_URL"]?.takeIf { it.isNotBlank() } ?: return null
            return DatabaseConfig(
                jdbcUrl = url,
                username = env["DATABASE_USER"],
                password = env["DATABASE_PASSWORD"],
                maxPoolSize = env["DATABASE_POOL_SIZE"]?.toIntOrNull() ?: 10,
                migrateOnStart = !env["DATABASE_MIGRATE"].equals("false", ignoreCase = true),
            )
        }
    }
}

object Database {
    private val log = LoggerFactory.getLogger(Database::class.java)

    /** Opens the pool (failing fast if the database is unreachable) and applies pending migrations. */
    fun connect(config: DatabaseConfig): HikariDataSource {
        val pool = HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = config.jdbcUrl
                username = config.username
                password = config.password
                maximumPoolSize = config.maxPoolSize
                poolName = "tana-spa"
                connectionTimeout = 10_000
            },
        )
        try {
            if (config.migrateOnStart) migrate(pool)
        } catch (e: Exception) {
            pool.close()
            throw e
        }
        log.info("Connected to PostgreSQL ({})", config)
        return pool
    }

    fun migrate(dataSource: DataSource) {
        val result = Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate()
        log.info("Database migrations applied: {} (schema version {})", result.migrationsExecuted, result.targetSchemaVersion)
    }
}

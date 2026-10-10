package com.tana.spa

import com.tana.spa.admin.adminModule
import com.tana.spa.auth.AuthConfig
import com.tana.spa.auth.AuthServices
import com.tana.spa.auth.InMemoryUserRepository
import com.tana.spa.auth.Pbkdf2PasswordHasher
import com.tana.spa.auth.PostgresUserRepository
import com.tana.spa.auth.UserRepository
import com.tana.spa.auth.authModule
import com.tana.spa.auth.otp.InMemoryPhoneVerificationRepository
import com.tana.spa.auth.otp.OtpConfig
import com.tana.spa.auth.otp.PhoneVerificationProvider
import com.tana.spa.auth.otp.PhoneVerificationRepository
import com.tana.spa.auth.otp.PostgresPhoneVerificationRepository
import com.tana.spa.auth.seedAdmins
import com.tana.spa.db.Database
import com.tana.spa.db.DatabaseConfig
import com.tana.spa.demo.demoRoutes
import com.tana.spa.featureflags.FeatureFlagRepository
import com.tana.spa.featureflags.InMemoryFeatureFlagRepository
import com.tana.spa.featureflags.PostgresFeatureFlagRepository
import com.tana.spa.featureflags.featureFlagModule
import com.tana.spa.shared.Constants
import com.tana.spa.shared.featureflags.ConfigurableFeatureFlags
import com.tana.spa.shared.featureflags.describe
import com.tana.spa.shared.sayHello
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory
import javax.sql.DataSource

private val log = LoggerFactory.getLogger("com.tana.spa.Main")

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: Constants.SERVER_PORT
    val dataSource = DatabaseConfig.fromEnvironment()?.let(Database::connect)
    val users: UserRepository = if (dataSource != null) {
        PostgresUserRepository(dataSource)
    } else {
        // Silently losing every account on restart is a data-loss bug in production, not a default.
        check(System.getenv("APP_ENV") != "production") { "DATABASE_URL must be set when APP_ENV=production" }
        log.warn("DATABASE_URL is not set: using the in-memory user store. Accounts are lost on restart.")
        InMemoryUserRepository()
    }
    val verifications: PhoneVerificationRepository =
        dataSource?.let { PostgresPhoneVerificationRepository(it) } ?: InMemoryPhoneVerificationRepository()
    val otpConfig = OtpConfig.fromEnvironment()
    val flagDefaults = ConfigurableFeatureFlags.fromEnvironment(System.getenv())
    val flagRepository: FeatureFlagRepository =
        dataSource?.let { PostgresFeatureFlagRepository(it) } ?: InMemoryFeatureFlagRepository()
    // Built here, not inside AuthServices, so the same hasher seeds the administrators.
    val hasher = Pbkdf2PasswordHasher()
    val auth = AuthServices.create(
        config = AuthConfig.fromEnvironment(),
        users = users,
        hasher = hasher,
        verifications = verifications,
        otpProvider = PhoneVerificationProvider.fromEnvironment(),
        otpConfig = otpConfig,
        flagRepository = flagRepository,
        flagDefaults = flagDefaults,
    )
    seedAdmins(users, hasher)

    log.info(
        "Starting server on port {} (env={}, store={})",
        port,
        System.getenv("APP_ENV") ?: "unset",
        if (dataSource != null) "postgres" else "in-memory",
    )
    // Only the defaults: stored states change while the server runs. Read those from /admin.
    log.info("Feature flag deploy defaults: {}", flagDefaults.describe())
    embeddedServer(Netty, port = port, host = "0.0.0.0") {
        closeOnStop(dataSource)
        module(auth)
    }.start(wait = true)
}

private fun Application.closeOnStop(dataSource: DataSource?) {
    val pool = dataSource as? AutoCloseable ?: return
    monitor.subscribe(ApplicationStopped) { pool.close() }
}

/**
 * @param authServices injectable so tests can run with a fast hasher and fixed secrets.
 */
fun Application.module(authServices: AuthServices = AuthServices.create(AuthConfig.fromEnvironment())) {
    authModule(authServices)
    // After authModule: it installs the plugins and the JWT realm that both of these use.
    featureFlagModule(authServices.featureFlags, authServices.tokens, authServices.authService)
    adminModule(authServices.authService)
    demoRoutes()
    routing {
        get("/") {
            call.respondText(sayHello("Ktor"))
        }
    }
}

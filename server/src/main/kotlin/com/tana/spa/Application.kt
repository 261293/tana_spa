package com.tana.spa

import com.tana.spa.admin.accountsModule
import com.tana.spa.admin.adminModule
import com.tana.spa.appupdate.AppUpdateRepository
import com.tana.spa.appupdate.InMemoryAppUpdateRepository
import com.tana.spa.appupdate.PostgresAppUpdateRepository
import com.tana.spa.appupdate.appUpdateModule
import com.tana.spa.auth.AuthConfig
import com.tana.spa.auth.AuthServices
import com.tana.spa.auth.InMemoryUserRepository
import com.tana.spa.auth.Pbkdf2PasswordHasher
import com.tana.spa.auth.PostgresUserRepository
import com.tana.spa.auth.SeedAccounts
import com.tana.spa.auth.UserRepository
import com.tana.spa.auth.authModule
import com.tana.spa.auth.otp.InMemoryPhoneVerificationRepository
import com.tana.spa.auth.otp.OtpConfig
import com.tana.spa.auth.otp.PhoneVerificationProvider
import com.tana.spa.auth.otp.PhoneVerificationRepository
import com.tana.spa.auth.otp.PostgresPhoneVerificationRepository
import com.tana.spa.auth.seedAccounts
import com.tana.spa.booking.BookingService
import com.tana.spa.booking.PostgresBookingRepository
import com.tana.spa.booking.bookingModule
import com.tana.spa.db.Database
import com.tana.spa.db.DatabaseConfig
import com.tana.spa.demo.demoRoutes
import com.tana.spa.featureflags.FeatureFlagRepository
import com.tana.spa.featureflags.InMemoryFeatureFlagRepository
import com.tana.spa.featureflags.PostgresFeatureFlagRepository
import com.tana.spa.featureflags.featureFlagModule
import com.tana.spa.notifications.InMemoryNotificationRepository
import com.tana.spa.notifications.NotificationRepository
import com.tana.spa.notifications.PostgresNotificationRepository
import com.tana.spa.notifications.notificationModule
import com.tana.spa.notifications.push.PushSender
import com.tana.spa.promotions.PromotionService
import com.tana.spa.shared.Constants
import com.tana.spa.shared.featureflags.ConfigurableFeatureFlags
import com.tana.spa.shared.featureflags.describe
import com.tana.spa.shared.sayHello
import com.tana.spa.shared.spa.SpaCatalog
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import javax.sql.DataSource
import kotlin.time.Duration.Companion.hours

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
    val appUpdateRepository: AppUpdateRepository =
        dataSource?.let { PostgresAppUpdateRepository(it) } ?: InMemoryAppUpdateRepository()
    val notificationRepository: NotificationRepository =
        dataSource?.let { PostgresNotificationRepository(it) } ?: InMemoryNotificationRepository()
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
        appUpdateRepository = appUpdateRepository,
        notificationRepository = notificationRepository,
        pushSender = PushSender.fromEnvironment(),
    )
    seedAccounts(users, hasher, SeedAccounts.fromEnvironment().accounts)
    // Booking reads a catalogue and writes appointments, so it has no in-memory twin: without a
    // database its endpoints are simply absent, and the log says so.
    val spa = dataSource?.let {
        val vouchers = PostgresBookingRepository(it)
        SpaServices(
            booking = BookingService(vouchers, auth.notifications),
            promotions = PromotionService(vouchers, users, auth.notifications),
        )
    }
    if (spa == null) log.warn("DATABASE_URL is not set: the booking, offers and staff endpoints are off.")

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
        module(auth, spa)
        spa?.let { remindAhead(it.booking) }
    }.start(wait = true)
}

/**
 * The appointment reminders. One sweep an hour is enough for a reminder that is only ever "about
 * a day ahead", and the claim inside [BookingService.sendDueReminders] keeps it to one per
 * booking however often this runs.
 */
private fun Application.remindAhead(booking: BookingService) {
    val sweeps = launch {
        while (isActive) {
            runCatching { booking.sendDueReminders() }
                .onFailure { if (it is CancellationException) throw it else log.warn("reminder sweep failed", it) }
            delay(REMINDER_SWEEP)
        }
    }
    monitor.subscribe(ApplicationStopped) { sweeps.cancel() }
}

private val REMINDER_SWEEP = 1.hours

private fun Application.closeOnStop(dataSource: DataSource?) {
    val pool = dataSource as? AutoCloseable ?: return
    monitor.subscribe(ApplicationStopped) { pool.close() }
}

/**
 * The endpoints that cannot work without PostgreSQL. They are absent together, so one nullable
 * carries both rather than two that must always agree.
 */
class SpaServices(val booking: BookingService, val promotions: PromotionService)

/**
 * @param authServices injectable so tests can run with a fast hasher and fixed secrets.
 */
fun Application.module(authServices: AuthServices = AuthServices.create(AuthConfig.fromEnvironment()), spa: SpaServices? = null) {
    authModule(authServices) { userId -> spa?.promotions?.grant(userId, SpaCatalog.NEW_GUEST_CODE) }
    // After authModule: it installs the plugins and the JWT realm that both of these use.
    featureFlagModule(authServices.featureFlags, authServices.tokens, authServices.authService)
    appUpdateModule(authServices.appUpdate, authServices.featureFlags, authServices.authService)
    notificationModule(authServices.notifications, authServices.featureFlags, authServices.authService)
    spa?.let { bookingModule(it.booking, authServices.authService, it.promotions) }
    adminModule(authServices.authService)
    accountsModule(authServices.authService)
    demoRoutes()
    routing {
        get("/") {
            call.respondText(sayHello("Ktor"))
        }
    }
}

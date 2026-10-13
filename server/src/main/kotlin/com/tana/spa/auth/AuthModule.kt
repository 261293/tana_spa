package com.tana.spa.auth

import com.tana.spa.appupdate.AppUpdateRepository
import com.tana.spa.appupdate.AppUpdateService
import com.tana.spa.appupdate.InMemoryAppUpdateRepository
import com.tana.spa.auth.otp.DevPhoneVerificationProvider
import com.tana.spa.auth.otp.InMemoryPhoneVerificationRepository
import com.tana.spa.auth.otp.OtpConfig
import com.tana.spa.auth.otp.PhoneVerificationProvider
import com.tana.spa.auth.otp.PhoneVerificationRepository
import com.tana.spa.auth.otp.PhoneVerificationService
import com.tana.spa.auth.otp.SendResult
import com.tana.spa.auth.otp.VerifyResult
import com.tana.spa.featureflags.FeatureFlagRepository
import com.tana.spa.featureflags.FeatureFlagService
import com.tana.spa.featureflags.InMemoryFeatureFlagRepository
import com.tana.spa.notifications.InMemoryNotificationRepository
import com.tana.spa.notifications.NotificationRepository
import com.tana.spa.notifications.NotificationService
import com.tana.spa.notifications.push.DevPushSender
import com.tana.spa.notifications.push.PushSender
import com.tana.spa.shared.auth.AuthApi
import com.tana.spa.shared.auth.AuthErrorCodes
import com.tana.spa.shared.auth.AuthResponse
import com.tana.spa.shared.auth.ErrorResponse
import com.tana.spa.shared.auth.LoginRequest
import com.tana.spa.shared.auth.PhoneOtpSendResponse
import com.tana.spa.shared.auth.PhoneOtpVerifyRequest
import com.tana.spa.shared.auth.RegisterRequest
import com.tana.spa.shared.auth.UserDto
import com.tana.spa.shared.featureflags.FeatureFlag
import com.tana.spa.shared.featureflags.FeatureFlags
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.callid.generate
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.util.AttributeKey
import kotlinx.serialization.SerializationException
import org.slf4j.LoggerFactory
import java.net.URI
import kotlin.time.Duration.Companion.minutes

/** Everything the auth routes need, bundled so tests can substitute pieces. */
class AuthServices(
    val config: AuthConfig,
    val authService: AuthService,
    val tokens: TokenService,
    val phoneVerification: PhoneVerificationService,
    val featureFlags: FeatureFlagService,
    val appUpdate: AppUpdateService,
    val notifications: NotificationService,
) {
    companion object {
        fun create(
            config: AuthConfig,
            users: UserRepository = InMemoryUserRepository(),
            hasher: PasswordHasher = Pbkdf2PasswordHasher(),
            verifications: PhoneVerificationRepository = InMemoryPhoneVerificationRepository(),
            otpProvider: PhoneVerificationProvider = DevPhoneVerificationProvider(),
            otpConfig: OtpConfig = OtpConfig(),
            flagRepository: FeatureFlagRepository = InMemoryFeatureFlagRepository(),
            flagDefaults: FeatureFlags = FeatureFlags.defaults,
            appUpdateRepository: AppUpdateRepository = InMemoryAppUpdateRepository(),
            notificationRepository: NotificationRepository = InMemoryNotificationRepository(),
            pushSender: PushSender = DevPushSender(),
        ): AuthServices {
            // Built once: the notification service resolves the same flags as the routes do.
            val featureFlags = FeatureFlagService(flagRepository, users, flagDefaults)
            return AuthServices(
                config = config,
                authService = AuthService(users, hasher, defaultPhoneRegion = otpConfig.defaultPhoneRegion),
                tokens = TokenService(config),
                phoneVerification = PhoneVerificationService(users, verifications, otpProvider, otpConfig),
                featureFlags = featureFlags,
                appUpdate = AppUpdateService(appUpdateRepository),
                notifications = NotificationService(notificationRepository, users, pushSender, featureFlags),
            )
        }
    }
}

internal const val JWT_AUTH = "auth-jwt"
private val LOGIN_LIMIT = RateLimitName("login")
private val OTP_LIMIT = RateLimitName("otp")
private const val OTP_REQUESTS_PER_MINUTE = 10
private val log = LoggerFactory.getLogger("com.tana.spa.auth")

/** Sent by the apps on every request (core:network `RequestTracing`); echoed back and put in every log line. */
const val REQUEST_ID_HEADER = "X-Request-Id"
const val SESSION_ID_HEADER = "X-Session-Id"
private val SAFE_ID = Regex("^[A-Za-z0-9_-]{1,64}$")

fun Application.authModule(services: AuthServices) {
    // A client-supplied id is only trusted if it is short and made of safe characters, so it cannot be
    // used to forge or inject extra log lines. Otherwise the server generates one.
    install(CallId) {
        retrieve { call -> call.request.headers[REQUEST_ID_HEADER]?.takeIf { SAFE_ID.matches(it) } }
        generate(length = 12)
        replyToHeader(REQUEST_ID_HEADER)
    }

    install(CallLogging) {
        callIdMdc("requestId")
        mdc("sessionId") { call -> call.request.headers[SESSION_ID_HEADER]?.takeIf { SAFE_ID.matches(it) } }
    }

    install(ContentNegotiation) {
        json()
    }

    install(CORS) {
        services.config.allowedOrigins.forEach { origin ->
            val uri = URI(origin)
            allowHost(
                host = if (uri.port == -1) uri.host else "${uri.host}:${uri.port}",
                schemes = listOf(uri.scheme),
            )
        }
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        // Browser clients send these tracing headers (which forces a preflight) and read the id back.
        allowHeader(REQUEST_ID_HEADER)
        allowHeader(SESSION_ID_HEADER)
        exposeHeader(REQUEST_ID_HEADER)
    }

    if (services.config.trustProxy) install(XForwardedHeaders)

    install(RateLimit) {
        // Brute-force throttles. Without `requestKey` Ktor uses ONE shared bucket for all clients,
        // so a single attacker could lock everybody out; key by client address instead.
        // The 429 body is produced by StatusPages.
        register(LOGIN_LIMIT) {
            rateLimiter(limit = services.config.loginRateLimitPerMinute, refillPeriod = 1.minutes)
            requestKey { call -> call.request.origin.remoteHost }
        }
        // Cheap first line of defence in front of the database-backed OTP limits.
        register(OTP_LIMIT) {
            rateLimiter(limit = OTP_REQUESTS_PER_MINUTE, refillPeriod = 1.minutes)
            requestKey { call -> call.request.origin.remoteHost }
        }
    }

    install(StatusPages) {
        // Only the exception type is logged: parser messages can quote the body (and so a password).
        exception<SerializationException> { call, cause ->
            log.warn("Malformed request body on {} ({})", call.request.path(), cause::class.simpleName)
            call.respondError(HttpStatusCode.BadRequest, AuthErrorCodes.INVALID_REQUEST, "Malformed request body")
        }
        exception<BadRequestException> { call, cause ->
            log.warn("Bad request on {} ({})", call.request.path(), cause::class.simpleName)
            call.respondError(HttpStatusCode.BadRequest, AuthErrorCodes.INVALID_REQUEST, "Malformed request body")
        }
        // Gives the rate-limit plugin's empty 429 a JSON body. Responses that already carry our own
        // error (with retryAfterSeconds / a specific code) are marked and must not be overwritten.
        status(HttpStatusCode.TooManyRequests) { call, _ ->
            if (!call.attributes.contains(ErrorBodyWritten)) {
                log.warn("Rate limited {} from {}", call.request.path(), call.request.origin.remoteHost)
                call.respondError(
                    HttpStatusCode.TooManyRequests,
                    AuthErrorCodes.TOO_MANY_REQUESTS,
                    "Too many attempts, try again later",
                )
            }
        }
        exception<Throwable> { call, cause ->
            // Never leak internals to the client; the detail goes to the server log only.
            log.error("Unhandled error", cause)
            call.respondError(HttpStatusCode.InternalServerError, AuthErrorCodes.INTERNAL, "Something went wrong")
        }
    }

    install(Authentication) {
        jwt(JWT_AUTH) {
            realm = "tana-spa"
            verifier(services.tokens.verifier)
            validate { credential ->
                credential.payload.subject
                    ?.takeIf { services.authService.findUser(it) != null }
                    ?.let { JWTPrincipal(credential.payload) }
            }
            challenge { _, _ ->
                log.info("Unauthenticated request to {} (missing, invalid or expired token)", call.request.path())
                call.respondError(HttpStatusCode.Unauthorized, AuthErrorCodes.UNAUTHORIZED, "Authentication required")
            }
        }
    }

    routing {
        route(AuthApi.BASE) {
            post("/register") {
                val body = call.receive<RegisterRequest>()
                val result = services.authService.register(body.displayName, body.email, body.phone, body.password)
                log.info("register -> {}{}", result::class.simpleName, (result as? AuthResult.Success)?.let { " user=${it.user.id}" } ?: "")
                when (result) {
                    is AuthResult.Success -> call.respond(HttpStatusCode.Created, services.authResponse(result.user))

                    AuthResult.EmailTaken -> call.respondError(
                        HttpStatusCode.Conflict,
                        AuthErrorCodes.EMAIL_TAKEN,
                        "Email is already registered",
                    )

                    AuthResult.PhoneTaken -> call.respondError(
                        HttpStatusCode.Conflict,
                        AuthErrorCodes.PHONE_TAKEN,
                        "Phone number is already registered",
                    )

                    else -> call.respondError(
                        HttpStatusCode.BadRequest,
                        AuthErrorCodes.INVALID_REQUEST,
                        "Invalid registration details",
                    )
                }
            }

            rateLimit(LOGIN_LIMIT) {
                post("/login") {
                    val body = call.receive<LoginRequest>()
                    val result = services.authService.login(body.email, body.password)
                    log.info(
                        "login -> {}{}",
                        result::class.simpleName,
                        (result as? AuthResult.Success)?.let {
                            " user=${it.user.id}"
                        } ?: "",
                    )
                    when (result) {
                        is AuthResult.Success -> call.respond(services.authResponse(result.user))

                        // One generic answer for unknown email and wrong password alike.
                        else -> call.respondError(
                            HttpStatusCode.Unauthorized,
                            AuthErrorCodes.INVALID_CREDENTIALS,
                            "Incorrect email or password",
                        )
                    }
                }
            }

            authenticate(JWT_AUTH) {
                get("/me") {
                    val id = call.principal<JWTPrincipal>()!!.payload.subject
                    val user = services.authService.findUser(id)
                    if (user == null) {
                        call.respondError(
                            HttpStatusCode.Unauthorized,
                            AuthErrorCodes.UNAUTHORIZED,
                            "Authentication required",
                        )
                    } else {
                        call.respond(user.toDto())
                    }
                }

                rateLimit(OTP_LIMIT) {
                    // The destination is never taken from the request: the code goes to the number
                    // already on the account. That is the main defence against SMS pumping.
                    post("/phone/otp/send") {
                        val userId = call.principal<JWTPrincipal>()!!.payload.subject
                        if (call.refuseIfDisabled(services.featureFlags, FeatureFlag.PHONE_OTP_VERIFICATION, userId)) {
                            return@post
                        }
                        val locale = call.request.headers[HttpHeaders.AcceptLanguage]
                            ?.substringBefore(',')?.substringBefore(';')?.trim()
                        val result = services.phoneVerification.send(userId, call.request.origin.remoteHost, locale)
                        log.info("otp send user={} -> {}", userId, result.describe())
                        when (result) {
                            is SendResult.Sent -> call.respond(
                                HttpStatusCode.Accepted,
                                PhoneOtpSendResponse(result.resendAfterSeconds, result.expiresInSeconds, result.maskedPhone),
                            )

                            SendResult.AlreadyVerified -> call.respondError(
                                HttpStatusCode.Conflict,
                                AuthErrorCodes.PHONE_ALREADY_VERIFIED,
                                "Phone number is already verified",
                            )

                            SendResult.PhoneClaimed -> call.respondError(
                                HttpStatusCode.Conflict,
                                AuthErrorCodes.PHONE_CLAIMED,
                                "This phone number is already verified on another account",
                            )

                            is SendResult.CountryNotSupported -> call.respondError(
                                HttpStatusCode.UnprocessableEntity,
                                AuthErrorCodes.PHONE_COUNTRY_UNSUPPORTED,
                                "Verification codes are not available for this country yet",
                            )

                            is SendResult.RateLimited -> {
                                call.response.header(HttpHeaders.RetryAfter, result.retryAfterSeconds.toString())
                                call.respondError(
                                    HttpStatusCode.TooManyRequests,
                                    AuthErrorCodes.TOO_MANY_REQUESTS,
                                    "Please wait before requesting another code",
                                    retryAfterSeconds = result.retryAfterSeconds,
                                )
                            }

                            SendResult.ProviderUnavailable -> call.respondError(
                                HttpStatusCode.ServiceUnavailable,
                                AuthErrorCodes.OTP_UNAVAILABLE,
                                "Could not send the code right now, please try again shortly",
                            )

                            SendResult.UserNotFound, SendResult.NoPhone -> call.respondError(
                                HttpStatusCode.BadRequest,
                                AuthErrorCodes.INVALID_REQUEST,
                                "No phone number on this account",
                            )
                        }
                    }

                    post("/phone/otp/verify") {
                        val userId = call.principal<JWTPrincipal>()!!.payload.subject
                        if (call.refuseIfDisabled(services.featureFlags, FeatureFlag.PHONE_OTP_VERIFICATION, userId)) {
                            return@post
                        }
                        val body = call.receive<PhoneOtpVerifyRequest>()
                        val result = services.phoneVerification.verify(userId, body.code)
                        log.info("otp verify user={} -> {}", userId, result.describe())
                        when (result) {
                            is VerifyResult.Verified -> call.respond(result.user.toDto())

                            is VerifyResult.InvalidCode -> call.respondError(
                                HttpStatusCode.BadRequest,
                                AuthErrorCodes.OTP_INVALID,
                                "Incorrect code",
                                attemptsRemaining = result.attemptsRemaining,
                            )

                            VerifyResult.Expired -> call.respondError(
                                HttpStatusCode.BadRequest,
                                AuthErrorCodes.OTP_EXPIRED,
                                "The code has expired, request a new one",
                            )

                            VerifyResult.TooManyAttempts -> call.respondError(
                                HttpStatusCode.TooManyRequests,
                                AuthErrorCodes.OTP_TOO_MANY_ATTEMPTS,
                                "Too many incorrect codes, request a new one",
                            )

                            VerifyResult.NotRequested -> call.respondError(
                                HttpStatusCode.NotFound,
                                AuthErrorCodes.OTP_NOT_REQUESTED,
                                "No code is waiting for this account",
                            )

                            VerifyResult.PhoneClaimed -> call.respondError(
                                HttpStatusCode.Conflict,
                                AuthErrorCodes.PHONE_CLAIMED,
                                "This phone number is already verified on another account",
                            )

                            VerifyResult.ProviderUnavailable -> call.respondError(
                                HttpStatusCode.ServiceUnavailable,
                                AuthErrorCodes.OTP_UNAVAILABLE,
                                "Could not check the code right now, please try again shortly",
                            )
                        }
                    }
                }
            }
        }
    }
}

// Class name plus the one number that explains a refusal; never the user, phone or code.
private fun SendResult.describe(): String = when (this) {
    is SendResult.RateLimited -> "RateLimited(retryAfter=${retryAfterSeconds}s)"
    is SendResult.CountryNotSupported -> "CountryNotSupported($region)"
    else -> this::class.simpleName ?: "SendResult"
}

private fun VerifyResult.describe(): String = when (this) {
    is VerifyResult.InvalidCode -> "InvalidCode(attemptsRemaining=$attemptsRemaining)"
    else -> this::class.simpleName ?: "VerifyResult"
}

private fun User.toDto() = UserDto(
    id = id,
    email = email,
    displayName = displayName,
    phone = phone,
    phoneVerified = phoneVerified,
    role = role.sql,
)

private fun AuthServices.authResponse(user: User) = AuthResponse(
    accessToken = tokens.issue(user),
    expiresInSeconds = config.tokenTtlSeconds,
    user = user.toDto(),
)

/**
 * @return true when the handler must stop. Inside the route rather than around it, so the path
 *   keeps existing: a client can tell "switched off" (503) from "wrong URL" (404).
 */
internal suspend fun ApplicationCall.refuseIfDisabled(flags: FeatureFlagService, flag: FeatureFlag, userId: String?): Boolean {
    if (flags.isEnabled(flag, userId)) return false
    log.info("{} refused: feature {} is switched off", request.path(), flag.key)
    respondError(
        HttpStatusCode.ServiceUnavailable,
        AuthErrorCodes.FEATURE_DISABLED,
        "This feature is temporarily unavailable",
    )
    return true
}

private suspend fun ApplicationCall.respondError(
    status: HttpStatusCode,
    code: String,
    message: String,
    retryAfterSeconds: Int? = null,
    attemptsRemaining: Int? = null,
) {
    attributes.put(ErrorBodyWritten, true)
    respond(status, ErrorResponse(code, message, retryAfterSeconds, attemptsRemaining))
}

/** Set once a handler has written its own error body, so generic status handlers leave it alone. */
private val ErrorBodyWritten = AttributeKey<Boolean>("tana.errorBodyWritten")

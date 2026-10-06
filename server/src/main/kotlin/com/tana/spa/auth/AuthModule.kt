package com.tana.spa.auth

import com.tana.spa.auth.otp.DevPhoneVerificationProvider
import com.tana.spa.auth.otp.InMemoryPhoneVerificationRepository
import com.tana.spa.auth.otp.OtpConfig
import com.tana.spa.auth.otp.PhoneVerificationProvider
import com.tana.spa.auth.otp.PhoneVerificationRepository
import com.tana.spa.auth.otp.PhoneVerificationService
import com.tana.spa.auth.otp.SendResult
import com.tana.spa.auth.otp.VerifyResult
import com.tana.spa.shared.auth.AuthApi
import com.tana.spa.shared.auth.AuthErrorCodes
import com.tana.spa.shared.auth.AuthResponse
import com.tana.spa.shared.auth.ErrorResponse
import com.tana.spa.shared.auth.LoginRequest
import com.tana.spa.shared.auth.PhoneOtpSendResponse
import com.tana.spa.shared.auth.PhoneOtpVerifyRequest
import com.tana.spa.shared.auth.RegisterRequest
import com.tana.spa.shared.auth.UserDto
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
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.plugins.statuspages.StatusPages
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
) {
    companion object {
        fun create(
            config: AuthConfig,
            users: UserRepository = InMemoryUserRepository(),
            hasher: PasswordHasher = Pbkdf2PasswordHasher(),
            verifications: PhoneVerificationRepository = InMemoryPhoneVerificationRepository(),
            otpProvider: PhoneVerificationProvider = DevPhoneVerificationProvider(),
            otpConfig: OtpConfig = OtpConfig(),
        ) = AuthServices(
            config = config,
            authService = AuthService(users, hasher, defaultPhoneRegion = otpConfig.defaultPhoneRegion),
            tokens = TokenService(config),
            phoneVerification = PhoneVerificationService(users, verifications, otpProvider, otpConfig),
        )
    }
}

private const val JWT_AUTH = "auth-jwt"
private val LOGIN_LIMIT = RateLimitName("login")
private val OTP_LIMIT = RateLimitName("otp")
private const val OTP_REQUESTS_PER_MINUTE = 10
private val log = LoggerFactory.getLogger("com.tana.spa.auth")

fun Application.authModule(services: AuthServices) {
    install(CallLogging)

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
        exception<SerializationException> { call, _ ->
            call.respondError(HttpStatusCode.BadRequest, AuthErrorCodes.INVALID_REQUEST, "Malformed request body")
        }
        exception<BadRequestException> { call, _ ->
            call.respondError(HttpStatusCode.BadRequest, AuthErrorCodes.INVALID_REQUEST, "Malformed request body")
        }
        // Gives the rate-limit plugin's empty 429 a JSON body. Responses that already carry our own
        // error (with retryAfterSeconds / a specific code) are marked and must not be overwritten.
        status(HttpStatusCode.TooManyRequests) { call, _ ->
            if (!call.attributes.contains(ErrorBodyWritten)) {
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
                call.respondError(HttpStatusCode.Unauthorized, AuthErrorCodes.UNAUTHORIZED, "Authentication required")
            }
        }
    }

    routing {
        route(AuthApi.BASE) {
            post("/register") {
                val body = call.receive<RegisterRequest>()
                val result = services.authService.register(body.displayName, body.email, body.phone, body.password)
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
                    when (val result = services.authService.login(body.email, body.password)) {
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
                        val locale = call.request.headers[HttpHeaders.AcceptLanguage]
                            ?.substringBefore(',')?.substringBefore(';')?.trim()
                        when (val result = services.phoneVerification.send(userId, call.request.origin.remoteHost, locale)) {
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
                        val body = call.receive<PhoneOtpVerifyRequest>()
                        when (val result = services.phoneVerification.verify(userId, body.code)) {
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

private fun User.toDto() = UserDto(
    id = id,
    email = email,
    displayName = displayName,
    phone = phone,
    phoneVerified = phoneVerified,
)

private fun AuthServices.authResponse(user: User) = AuthResponse(
    accessToken = tokens.issue(user),
    expiresInSeconds = config.tokenTtlSeconds,
    user = user.toDto(),
)

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

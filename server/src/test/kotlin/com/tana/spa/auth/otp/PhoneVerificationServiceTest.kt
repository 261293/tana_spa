package com.tana.spa.auth.otp

import com.tana.spa.auth.CreateUserResult
import com.tana.spa.auth.InMemoryUserRepository
import com.tana.spa.auth.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PhoneVerificationServiceTest {

    private class Clock(var now: Instant = Instant.parse("2026-10-06T08:00:00Z")) : () -> Instant {
        override fun invoke() = now
        fun advance(d: Duration) {
            now += d
        }
    }

    // JUnit 4 rejects @Test methods that return a value, which `= runBlocking { ... }` would.
    private fun blocking(context: CoroutineContext = EmptyCoroutineContext, block: suspend CoroutineScope.() -> Unit) {
        runBlocking(context, block)
    }

    private val clock = Clock()
    private val users = InMemoryUserRepository()
    private val verifications = InMemoryPhoneVerificationRepository()
    private val dev = DevPhoneVerificationProvider(clock = clock)

    private fun service(config: OtpConfig = OtpConfig(), provider: PhoneVerificationProvider = dev) =
        PhoneVerificationService(users, verifications, provider, config, clock, Dispatchers.Unconfined)

    private var seq = 0
    private fun newUser(phone: String? = null): User {
        val n = seq++ // always advance, so emails stay unique even when a phone is supplied
        val number = phone ?: "+84333433${100 + n}"
        return assertIs<CreateUserResult.Created>(users.create("u$n@example.com", "Lan", number, "h")).user
    }

    private fun codeFor(user: User) = assertNotNull(dev.peekCode(user.phone!!))
    private fun wrongCodeFor(user: User) = if (codeFor(user) == "000000") "111111" else "000000"

    @Test
    fun sendThenVerifyMarksThePhoneVerified() = blocking {
        val user = newUser()
        val svc = service()

        val sent = assertIs<SendResult.Sent>(svc.send(user.id, "203.0.113.5", "vi"))
        assertEquals(60, sent.resendAfterSeconds)
        assertEquals(600, sent.expiresInSeconds)
        assertTrue(sent.maskedPhone.startsWith("+84") && sent.maskedPhone.endsWith(user.phone!!.takeLast(3)))

        val verified = assertIs<VerifyResult.Verified>(svc.verify(user.id, " ${codeFor(user)} "))
        assertTrue(verified.user.phoneVerified)
        assertTrue(users.findById(user.id)!!.phoneVerified)
        assertEquals(SendResult.AlreadyVerified, svc.send(user.id, null, null))
    }

    @Test
    fun theCodeCannotBeReplayed() = blocking {
        val user = newUser()
        val svc = service()
        svc.send(user.id, null, null)
        val code = codeFor(user)
        assertIs<VerifyResult.Verified>(svc.verify(user.id, code))
        assertEquals(VerifyResult.NotRequested, svc.verify(user.id, code))
    }

    @Test
    fun wrongGuessesCountDownThenLockTheCodeOut() = blocking {
        val user = newUser()
        val svc = service()
        svc.send(user.id, null, null)
        val wrong = wrongCodeFor(user)

        assertEquals(VerifyResult.InvalidCode(4), svc.verify(user.id, wrong))
        assertEquals(VerifyResult.InvalidCode(3), svc.verify(user.id, wrong))
        assertEquals(VerifyResult.InvalidCode(2), svc.verify(user.id, wrong))
        assertEquals(VerifyResult.InvalidCode(1), svc.verify(user.id, wrong))
        assertEquals(VerifyResult.TooManyAttempts, svc.verify(user.id, wrong))
        // Locked out even for the right code: a new one must be requested.
        assertEquals(VerifyResult.NotRequested, svc.verify(user.id, codeFor(user)))
    }

    @Test
    fun aTypoIsNotAGuess() = blocking {
        val user = newUser()
        val svc = service()
        svc.send(user.id, null, null)

        assertEquals(VerifyResult.InvalidCode(5), svc.verify(user.id, "abc"))
        assertEquals(VerifyResult.InvalidCode(5), svc.verify(user.id, ""))
        assertIs<VerifyResult.Verified>(svc.verify(user.id, codeFor(user)))
    }

    @Test
    fun parallelGuessesCanNeverExceedTheAttemptCap() = blocking(Dispatchers.Default) {
        val user = newUser()
        val checks = AtomicInteger()
        val counting = object : PhoneVerificationProvider by dev {
            override fun check(phoneE164: String, code: String): Boolean {
                checks.incrementAndGet()
                return dev.check(phoneE164, code)
            }
        }
        val svc = PhoneVerificationService(users, verifications, counting, OtpConfig(), clock, Dispatchers.Default)
        svc.send(user.id, null, null)
        val wrong = wrongCodeFor(user)

        List(40) { async { svc.verify(user.id, wrong) } }.awaitAll()

        assertEquals(5, checks.get(), "the provider must be consulted at most maxCheckAttempts times")
    }

    @Test
    fun expiredCodesAreRefused() = blocking {
        val user = newUser()
        val svc = service()
        svc.send(user.id, null, null)
        val code = codeFor(user)

        clock.advance(Duration.ofMinutes(10))
        assertEquals(VerifyResult.Expired, svc.verify(user.id, code))
        assertEquals(VerifyResult.NotRequested, svc.verify(user.id, code))
    }

    @Test
    fun verifyWithoutRequestingACodeIsNotRequested() = blocking {
        assertEquals(VerifyResult.NotRequested, service().verify(newUser().id, "123456"))
    }

    @Test
    fun resendIsBlockedDuringTheCooldownThenReplacesTheOldCode() = blocking {
        val user = newUser()
        val svc = service()
        assertIs<SendResult.Sent>(svc.send(user.id, null, null))
        val first = codeFor(user)

        assertEquals(SendResult.RateLimited(60), svc.send(user.id, null, null))
        clock.advance(Duration.ofSeconds(30))
        assertEquals(SendResult.RateLimited(30), svc.send(user.id, null, null))

        clock.advance(Duration.ofSeconds(31))
        assertIs<SendResult.Sent>(svc.send(user.id, null, null))
        val second = codeFor(user)
        if (first != second) assertEquals(VerifyResult.InvalidCode(4), svc.verify(user.id, first))
        assertIs<VerifyResult.Verified>(svc.verify(user.id, second))
    }

    @Test
    fun perNumberHourlyCapStopsRepeatedSends() = blocking {
        val user = newUser()
        val svc = service(OtpConfig(resendCooldown = Duration.ZERO, maxSendsPerPhonePerHour = 3))
        repeat(3) { assertIs<SendResult.Sent>(svc.send(user.id, null, null)) }

        assertEquals(SendResult.RateLimited(3600), svc.send(user.id, null, null))
        clock.advance(Duration.ofMinutes(61))
        assertIs<SendResult.Sent>(svc.send(user.id, null, null))
    }

    @Test
    fun perUserDailyCapStopsRepeatedSends() = blocking {
        val user = newUser()
        val svc = service(OtpConfig(resendCooldown = Duration.ZERO, maxSendsPerUserPerDay = 2))
        repeat(2) {
            assertIs<SendResult.Sent>(svc.send(user.id, null, null))
            clock.advance(Duration.ofMinutes(61)) // keep clear of the hourly caps
        }
        assertEquals(SendResult.RateLimited(86_400), svc.send(user.id, null, null))
        clock.advance(Duration.ofHours(24))
        assertIs<SendResult.Sent>(svc.send(user.id, null, null))
    }

    @Test
    fun perIpCapStopsOneAddressFromSpammingManyAccounts() = blocking {
        val svc = service(OtpConfig(maxSendsPerIpPerHour = 2))
        val ip = "198.51.100.7"
        assertIs<SendResult.Sent>(svc.send(newUser().id, ip, null))
        assertIs<SendResult.Sent>(svc.send(newUser().id, ip, null))
        assertEquals(SendResult.RateLimited(3600), svc.send(newUser().id, ip, null))
        // A different address is unaffected.
        assertIs<SendResult.Sent>(svc.send(newUser().id, "198.51.100.8", null))
    }

    @Test
    fun countryPolicyCanAllowOrBlockRegions() = blocking {
        val vn = newUser()
        assertEquals(SendResult.CountryNotSupported("VN"), service(OtpConfig(blockedCountries = setOf("VN"))).send(vn.id, null, null))
        assertEquals(SendResult.CountryNotSupported("VN"), service(OtpConfig(allowedCountries = setOf("US"))).send(vn.id, null, null))
        assertIs<SendResult.Sent>(service(OtpConfig(allowedCountries = setOf("VN", "US"))).send(vn.id, null, null))
    }

    @Test
    fun everyCountryIsAllowedByDefault() = blocking {
        listOf("+16502530000", "+442070313000", "+61293744000", "+6565218000", "+81363849000").forEach { phone ->
            val user = newUser(phone)
            assertIs<SendResult.Sent>(service().send(user.id, null, null), "phone: $phone")
        }
    }

    @Test
    fun aNumberAlreadyVerifiedElsewhereIsNotTextedAgain() = blocking {
        val owner = newUser(phone = "+84333433999")
        val squatter = newUser(phone = "+84333433999")
        val svc = service()
        svc.send(owner.id, null, null)
        assertIs<VerifyResult.Verified>(svc.verify(owner.id, codeFor(owner)))

        // No SMS is spent on a number that can no longer be claimed.
        assertEquals(SendResult.PhoneClaimed, svc.send(squatter.id, null, null))
    }

    @Test
    fun theFirstVerifierWinsEvenIfBothHadACodeOutstanding() = blocking {
        val a = newUser(phone = "+84333433888")
        val b = newUser(phone = "+84333433888")
        val svc = service()
        svc.send(a.id, null, null)
        svc.send(b.id, null, null)
        val code = codeFor(b) // the number's owner holds the latest code
        users.markPhoneVerified(a.id, a.phone!!) // A wins the race

        assertEquals(VerifyResult.PhoneClaimed, svc.verify(b.id, code))
    }

    @Test
    fun aProviderOutageIsReportedAndStillCountsAgainstTheLimits() = blocking {
        val user = newUser()
        val failing = object : PhoneVerificationProvider {
            override val name = "failing"
            override fun start(phoneE164: String, locale: String?): String? = throw ProviderException("down")
            override fun check(phoneE164: String, code: String): Boolean = throw ProviderException("down")
        }
        val svc = service(provider = failing)

        assertEquals(SendResult.ProviderUnavailable, svc.send(user.id, null, null))
        // The failed attempt is on record, so an outage cannot be used to bypass the cooldown.
        assertEquals(SendResult.RateLimited(60), svc.send(user.id, null, null))
    }

    @Test
    fun aProviderOutageDuringCheckIsReported() = blocking {
        val user = newUser()
        var down = false
        val flaky = object : PhoneVerificationProvider by dev {
            override fun check(phoneE164: String, code: String): Boolean =
                if (down) throw ProviderException("down") else dev.check(phoneE164, code)
        }
        val svc = service(provider = flaky)
        svc.send(user.id, null, null)
        down = true
        assertEquals(VerifyResult.ProviderUnavailable, svc.verify(user.id, "123456"))
    }

    @Test
    fun unknownUsersAndAccountsWithoutAPhoneAreHandled() = blocking {
        assertEquals(SendResult.UserNotFound, service().send("00000000-0000-0000-0000-000000000000", null, null))
    }

    @Test
    fun junkIpAndLocaleAreIgnoredNotTrusted() = blocking {
        val user = newUser()
        // Hostnames / injection attempts in the address, and malformed locales, must not break or reach the provider.
        var seenLocale: String? = "unset"
        val spy = object : PhoneVerificationProvider by dev {
            override fun start(phoneE164: String, locale: String?): String? {
                seenLocale = locale
                return dev.start(phoneE164, locale)
            }
        }
        val svc = service(provider = spy)
        assertIs<SendResult.Sent>(svc.send(user.id, "evil.example.com'; DROP TABLE users;--", "vi\r\nX-Injected: 1"))
        assertEquals(null, seenLocale)
    }

    @Test
    fun wellFormedLocalesAreForwardedToTheProvider() = blocking {
        val user = newUser()
        var seenLocale: String? = null
        val spy = object : PhoneVerificationProvider by dev {
            override fun start(phoneE164: String, locale: String?): String? {
                seenLocale = locale
                return dev.start(phoneE164, locale)
            }
        }
        service(provider = spy).send(user.id, null, "vi")
        assertEquals("vi", seenLocale)
    }
}

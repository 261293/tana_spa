package com.tana.spa.booking

import com.tana.spa.SpaServices
import com.tana.spa.auth.AuthConfig
import com.tana.spa.auth.AuthServices
import com.tana.spa.auth.Pbkdf2PasswordHasher
import com.tana.spa.auth.PostgresUserRepository
import com.tana.spa.auth.SeedAccount
import com.tana.spa.auth.UserRole
import com.tana.spa.auth.seedAccounts
import com.tana.spa.db.TestDatabase
import com.tana.spa.module
import com.tana.spa.notifications.PostgresNotificationRepository
import com.tana.spa.promotions.PromotionService
import com.tana.spa.shared.auth.AuthApi
import com.tana.spa.shared.auth.AuthResponse
import com.tana.spa.shared.auth.ErrorResponse
import com.tana.spa.shared.auth.LoginRequest
import com.tana.spa.shared.auth.RegisterRequest
import com.tana.spa.shared.auth.UserDto
import com.tana.spa.shared.booking.BookingApi
import com.tana.spa.shared.booking.BookingDto
import com.tana.spa.shared.booking.BookingErrorCodes
import com.tana.spa.shared.booking.BookingStatusDto
import com.tana.spa.shared.booking.BookingsResponse
import com.tana.spa.shared.booking.CatalogResponse
import com.tana.spa.shared.booking.CreateBookingRequest
import com.tana.spa.shared.booking.CreateServiceRequest
import com.tana.spa.shared.booking.CreateTherapistRequest
import com.tana.spa.shared.booking.DeclineBookingRequest
import com.tana.spa.shared.booking.DeclineReasonDto
import com.tana.spa.shared.booking.ServiceCategoryDto
import com.tana.spa.shared.booking.ServiceDto
import com.tana.spa.shared.booking.SpaValidation
import com.tana.spa.shared.booking.StaffQueueResponse
import com.tana.spa.shared.booking.TherapistDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The owner's HTTP surface against the real schema: who may call it, and what each call does to
 * the booking. The clock is fixed so "two hours before the start" is a fact, not a race.
 */
class OwnerRoutesTest {

    private val zone: ZoneId = ZoneId.of("America/Los_Angeles")
    private val dataSource = TestDatabase.dataSource
    private val users = PostgresUserRepository(dataSource)
    private val hasher = Pbkdf2PasswordHasher(iterations = 1_000)

    private val owner = SeedAccount("owner-routes@tana.local", "Jade Bathhouse", "owner-password", UserRole.OWNER)
    private val admin = SeedAccount("admin-routes@tana.local", "Ops", "admin-password", UserRole.ADMIN)
    private val staff = SeedAccount("staff-routes@tana.local", "Linh", "staff-password", UserRole.STAFF)

    /** Its own day per test: the embedded database is shared, so the diary would be too. */
    private val day: LocalDate = LocalDate.now(zone).plusDays(DAYS_AHEAD.getAndIncrement().toLong())
    private var now: Instant = day.minusDays(2).atTime(9, 0).atZone(zone).toInstant()

    private fun slot(hour: Int, minute: Int = 0): Instant = day.atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant()

    private val auth: AuthServices by lazy {
        AuthServices.create(
            config = AuthConfig(jwtSecret = "x".repeat(40)),
            users = users,
            hasher = hasher,
            notificationRepository = PostgresNotificationRepository(dataSource),
        ).also { seedAccounts(users, hasher, listOf(owner, admin, staff)) }
    }

    private fun spa(): SpaServices {
        val repository = PostgresBookingRepository(dataSource)
        val booking = BookingService(
            repository = repository,
            notifications = auth.notifications,
            zone = zone,
            clock = { now },
        )
        return SpaServices(booking, PromotionService(repository, users, auth.notifications))
    }

    private fun ApplicationTestBuilder.jsonClient(): HttpClient = createClient { install(ContentNegotiation) { json() } }

    private var seq = (System.nanoTime() % 7_000_000L).toInt()

    private suspend fun HttpClient.signUpGuest(): String {
        val email = "g${UUID.randomUUID().toString().take(10)}@example.com"
        val phone = "039" + (1_000_000 + (seq++ % 9_000_000)).toString()
        val response = post(AuthApi.REGISTER) {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("Hannah Pham", email, phone, "password123"))
        }
        assertEquals(HttpStatusCode.Created, response.status)
        return response.body<AuthResponse>().accessToken
    }

    private suspend fun HttpClient.signIn(account: SeedAccount): AuthResponse = post(AuthApi.LOGIN) {
        contentType(ContentType.Application.Json)
        setBody(LoginRequest(account.email, account.password))
    }.body()

    private suspend fun HttpClient.book(token: String, at: Instant, therapist: String? = "mai"): HttpResponse = post(BookingApi.BOOKINGS) {
        bearerAuth(token)
        contentType(ContentType.Application.Json)
        setBody(CreateBookingRequest("warm-stone-massage", therapist, at.toEpochMilli()))
    }

    private suspend fun HttpClient.callQueueRoutes(token: String, bookingId: String): List<Pair<String, HttpResponse>> = listOf(
        "queue" to get(BookingApi.OWNER_BOOKINGS) { bearerAuth(token) },
        "confirm" to post(BookingApi.confirm(bookingId)) { bearerAuth(token) },
        "decline" to post(BookingApi.decline(bookingId)) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(DeclineBookingRequest(DeclineReasonDto.NO_ROOM))
        },
        "complete" to post(BookingApi.complete(bookingId)) { bearerAuth(token) },
        "no-show" to post(BookingApi.noShow(bookingId)) { bearerAuth(token) },
    )

    private suspend fun HttpClient.callMenuRoutes(token: String): List<Pair<String, HttpResponse>> = listOf(
        "create service" to post(BookingApi.OWNER_SERVICES) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(CreateServiceRequest("Reiki", "Hands on", 60, 100, ServiceCategoryDto.BODY))
        },
        "create therapist" to post(BookingApi.OWNER_THERAPISTS) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(CreateTherapistRequest("Noor", "Facials"))
        },
    )

    private suspend fun HttpClient.callOwnerRoutes(token: String, bookingId: String): List<Pair<String, HttpResponse>> =
        callQueueRoutes(token, bookingId) + callMenuRoutes(token)

    @Test
    fun every_owner_route_refuses_a_customer() = testApplication {
        application { module(auth, spa()) }
        val client = jsonClient()
        val guest = client.signUpGuest()
        val booking = client.book(guest, slot(9)).body<BookingDto>()

        for ((route, response) in client.callOwnerRoutes(guest, booking.id)) {
            assertEquals(HttpStatusCode.Forbidden, response.status, route)
        }
        // ... and the booking is untouched by all that refusing.
        assertEquals(BookingStatusDto.PENDING, client.booking(guest, booking.id).status)
    }

    @Test
    fun an_administrator_is_turned_away_from_nothing_the_owner_can_do() = testApplication {
        application { module(auth, spa()) }
        val client = jsonClient()
        val guest = client.signUpGuest()
        val booking = client.book(guest, slot(9)).body<BookingDto>()
        val token = client.signIn(admin).accessToken

        for ((route, response) in client.callOwnerRoutes(token, booking.id)) {
            assertNotEquals(HttpStatusCode.Forbidden, response.status, route)
        }
    }

    @Test
    fun staff_work_the_queue_but_cannot_touch_the_menu_or_the_roster() = testApplication {
        application { module(auth, spa()) }
        val client = jsonClient()
        val guest = client.signUpGuest()
        val booking = client.book(guest, slot(9)).body<BookingDto>()
        val token = client.signIn(staff).accessToken

        for ((route, response) in client.callQueueRoutes(token, booking.id)) {
            assertNotEquals(HttpStatusCode.Forbidden, response.status, route)
        }
        for ((route, response) in client.callMenuRoutes(token)) {
            assertEquals(HttpStatusCode.Forbidden, response.status, route)
        }
    }

    @Test
    fun public_sign_up_cannot_produce_an_owner() = testApplication {
        application { module(auth, spa()) }
        val client = jsonClient()
        val token = client.signUpGuest()

        val me = client.get(AuthApi.ME) { bearerAuth(token) }.body<UserDto>()

        assertEquals(UserRole.CUSTOMER.sql, me.role)
    }

    @Test
    fun the_owner_confirms_a_request_and_the_guest_sees_it() = testApplication {
        application { module(auth, spa()) }
        val client = jsonClient()
        val guest = client.signUpGuest()
        val booking = client.book(guest, slot(10)).body<BookingDto>()
        val ownerToken = client.signIn(owner).accessToken

        val queue = client.get(BookingApi.OWNER_BOOKINGS) { bearerAuth(ownerToken) }.body<StaffQueueResponse>()
        assertTrue(queue.pending.any { it.id == booking.id })

        val confirmed = client.post(BookingApi.confirm(booking.id)) { bearerAuth(ownerToken) }
        assertEquals(HttpStatusCode.OK, confirmed.status)
        assertEquals(BookingStatusDto.CONFIRMED, confirmed.body<BookingDto>().status)
        assertEquals(BookingStatusDto.CONFIRMED, client.booking(guest, booking.id).status)
    }

    @Test
    fun a_decline_carries_its_reason_to_the_guest_and_needs_one() = testApplication {
        application { module(auth, spa()) }
        val client = jsonClient()
        val guest = client.signUpGuest()
        val booking = client.book(guest, slot(11, 30)).body<BookingDto>()
        val ownerToken = client.signIn(owner).accessToken

        val reasonless = client.post(BookingApi.decline(booking.id)) {
            bearerAuth(ownerToken)
            setBody(TextContent("{}", ContentType.Application.Json))
        }
        assertEquals(HttpStatusCode.BadRequest, reasonless.status)

        val declined = client.post(BookingApi.decline(booking.id)) {
            bearerAuth(ownerToken)
            contentType(ContentType.Application.Json)
            setBody(DeclineBookingRequest(DeclineReasonDto.THERAPIST_UNAVAILABLE, alternativeOffer = "4:00 PM"))
        }
        assertEquals(HttpStatusCode.OK, declined.status)

        val seenByGuest = client.booking(guest, booking.id)
        assertEquals(BookingStatusDto.DECLINED, seenByGuest.status)
        assertEquals(DeclineReasonDto.THERAPIST_UNAVAILABLE, seenByGuest.declineReason)
        assertEquals("4:00 PM", seenByGuest.alternativeOffer)
    }

    @Test
    fun a_visit_is_recorded_only_after_it_was_due_to_start() = testApplication {
        application { module(auth, spa()) }
        val client = jsonClient()
        val guest = client.signUpGuest()
        val start = slot(13)
        val booking = client.book(guest, start).body<BookingDto>()
        val ownerToken = client.signIn(owner).accessToken
        client.post(BookingApi.confirm(booking.id)) { bearerAuth(ownerToken) }

        val early = client.post(BookingApi.complete(booking.id)) { bearerAuth(ownerToken) }
        assertEquals(HttpStatusCode.Conflict, early.status)
        assertEquals(BookingErrorCodes.VISIT_NOT_STARTED, early.body<ErrorResponse>().code)

        now = start.plus(Duration.ofMinutes(5))
        val recorded = client.post(BookingApi.complete(booking.id)) { bearerAuth(ownerToken) }
        assertEquals(BookingStatusDto.COMPLETED, recorded.body<BookingDto>().status)

        // A visit is recorded once: there is no completed-then-no-show.
        val again = client.post(BookingApi.noShow(booking.id)) { bearerAuth(ownerToken) }
        assertEquals(HttpStatusCode.Conflict, again.status)
        assertEquals(BookingErrorCodes.INVALID_TRANSITION, again.body<ErrorResponse>().code)
    }

    @Test
    fun a_no_show_is_recorded_after_the_start_time() = testApplication {
        application { module(auth, spa()) }
        val client = jsonClient()
        val guest = client.signUpGuest()
        val start = slot(14, 30)
        val booking = client.book(guest, start).body<BookingDto>()
        val ownerToken = client.signIn(owner).accessToken
        client.post(BookingApi.confirm(booking.id)) { bearerAuth(ownerToken) }

        now = start.plus(Duration.ofMinutes(20))
        val recorded = client.post(BookingApi.noShow(booking.id)) { bearerAuth(ownerToken) }

        assertEquals(BookingStatusDto.NO_SHOW, recorded.body<BookingDto>().status)
    }

    @Test
    fun a_guest_cancels_until_two_hours_before_the_start() = testApplication {
        application { module(auth, spa()) }
        val client = jsonClient()
        val guest = client.signUpGuest()
        val start = slot(16)
        val booking = client.book(guest, start).body<BookingDto>()

        now = start.minus(Duration.ofHours(1))
        val tooLate = client.post(BookingApi.cancel(booking.id)) { bearerAuth(guest) }
        assertEquals(HttpStatusCode.Conflict, tooLate.status)
        assertEquals(BookingErrorCodes.TOO_LATE_TO_CANCEL, tooLate.body<ErrorResponse>().code)

        now = start.minus(Duration.ofHours(3))
        val cancelled = client.post(BookingApi.cancel(booking.id)) { bearerAuth(guest) }
        assertEquals(BookingStatusDto.CANCELLED, cancelled.body<BookingDto>().status)
    }

    @Test
    fun a_guest_cannot_cancel_someone_elses_booking() = testApplication {
        application { module(auth, spa()) }
        val client = jsonClient()
        val mine = client.signUpGuest()
        val booking = client.book(mine, slot(17, 30)).body<BookingDto>()
        val stranger = client.signUpGuest()

        val refused = client.post(BookingApi.cancel(booking.id)) { bearerAuth(stranger) }

        assertEquals(HttpStatusCode.NotFound, refused.status)
        assertEquals(BookingStatusDto.PENDING, client.booking(mine, booking.id).status)
    }

    @Test
    fun a_treatment_and_a_therapist_the_owner_adds_reach_the_guest_catalogue() = testApplication {
        application { module(auth, spa()) }
        val client = jsonClient()
        val ownerToken = client.signIn(owner).accessToken
        val name = "Pine Ritual ${UUID.randomUUID().toString().take(6)}"

        val service = client.post(BookingApi.OWNER_SERVICES) {
            bearerAuth(ownerToken)
            contentType(ContentType.Application.Json)
            setBody(CreateServiceRequest(name, "Pine oil and steam", 75, 130, ServiceCategoryDto.BODY))
        }
        assertEquals(HttpStatusCode.Created, service.status)
        val created = service.body<ServiceDto>()
        assertEquals(SpaValidation.slugify(name), created.slug)

        val therapistName = "Noor ${UUID.randomUUID().toString().take(6)}"
        val therapist = client.post(BookingApi.OWNER_THERAPISTS) {
            bearerAuth(ownerToken)
            contentType(ContentType.Application.Json)
            setBody(CreateTherapistRequest(therapistName, "Body rituals"))
        }
        assertEquals(HttpStatusCode.Created, therapist.status)

        val catalogue = client.get(BookingApi.CATALOG).body<CatalogResponse>()
        assertTrue(catalogue.services.any { it.slug == created.slug && it.minutes == 75 && it.price == 130 })
        assertTrue(catalogue.therapists.any { it.slug == therapist.body<TherapistDto>().slug })
    }

    @Test
    fun a_treatment_is_refused_field_by_field() = testApplication {
        application { module(auth, spa()) }
        val client = jsonClient()
        val ownerToken = client.signIn(owner).accessToken

        val refused = client.post(BookingApi.OWNER_SERVICES) {
            bearerAuth(ownerToken)
            contentType(ContentType.Application.Json)
            setBody(CreateServiceRequest("Reiki", "Hands on", minutes = 50, price = 100, category = ServiceCategoryDto.BODY))
        }

        assertEquals(HttpStatusCode.BadRequest, refused.status)
        assertEquals(SpaValidation.FIELD_MINUTES, refused.body<ErrorResponse>().field)
    }

    @Test
    fun a_second_treatment_with_the_same_name_gets_its_own_slug() = testApplication {
        application { module(auth, spa()) }
        val client = jsonClient()
        val ownerToken = client.signIn(owner).accessToken
        val name = "Twin Ritual ${UUID.randomUUID().toString().take(6)}"

        suspend fun create() = client.post(BookingApi.OWNER_SERVICES) {
            bearerAuth(ownerToken)
            contentType(ContentType.Application.Json)
            setBody(CreateServiceRequest(name, "Same name", 60, 90, ServiceCategoryDto.BODY))
        }.body<ServiceDto>()

        assertEquals(SpaValidation.slugify(name), create().slug)
        assertEquals(SpaValidation.slugify(name) + "-2", create().slug)
    }

    @Test
    fun any_therapist_is_assigned_whoever_is_free_and_refused_when_nobody_is() = testApplication {
        application { module(auth, spa()) }
        val client = jsonClient()
        val guest = client.signUpGuest()
        val at = slot(19)

        val therapists = client.get(BookingApi.CATALOG).body<CatalogResponse>().therapists
        val assigned = client.book(guest, at, therapist = null)
        assertEquals(HttpStatusCode.Created, assigned.status)
        assertTrue(assigned.body<BookingDto>().therapist.slug in therapists.map { it.slug })

        // Fill every remaining diary at that time, then ask again.
        therapists.forEach { client.book(client.signUpGuest(), at, therapist = it.slug) }

        val refused = client.book(client.signUpGuest(), at, therapist = null)
        assertEquals(HttpStatusCode.Conflict, refused.status)
        assertEquals(BookingErrorCodes.SLOT_TAKEN, refused.body<ErrorResponse>().code)
    }

    private companion object {
        /** Shared across instances: JUnit builds one per test and they all share the database. */
        val DAYS_AHEAD = java.util.concurrent.atomic.AtomicInteger(40)
    }

    private suspend fun HttpClient.booking(token: String, id: String): BookingDto {
        val bookings = get(BookingApi.BOOKINGS) { bearerAuth(token) }.body<BookingsResponse>().bookings
        return assertNotNull(bookings.firstOrNull { it.id == id }, "booking $id is not in the guest's list")
    }

    @Test
    fun the_guests_own_list_holds_their_booking_and_nobody_elses() = testApplication {
        application { module(auth, spa()) }
        val client = jsonClient()
        val mine = client.signUpGuest()
        val booking = client.book(mine, slot(9)).body<BookingDto>()
        val stranger = client.signUpGuest()

        assertEquals(BookingStatusDto.PENDING, client.booking(mine, booking.id).status)
        assertNull(
            client.get(BookingApi.BOOKINGS) { bearerAuth(stranger) }.body<BookingsResponse>().bookings
                .firstOrNull { it.id == booking.id },
        )
    }
}

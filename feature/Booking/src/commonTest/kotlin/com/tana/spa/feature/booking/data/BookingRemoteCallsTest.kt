package com.tana.spa.feature.booking.data

import com.tana.spa.core.network.AccessTokenSource
import com.tana.spa.core.network.NetworkClient
import com.tana.spa.core.network.withDefaults
import com.tana.spa.feature.booking.domain.BookingAttempt
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private data class Sent(val call: String, val body: String, val authorization: String?)

private const val SERVICE =
    """{"slug":"signature","name":"Signature","minutes":90,"price":900000,"category":"MASSAGE","includes":"Oil"}"""
private const val THERAPIST = """{"slug":"mai","name":"Mai","focus":"Deep tissue","accent":"MOSS","nextSlot":null}"""
private const val BOOKING =
    """{"id":"b1","reference":"TS-1","guestName":"Lan","guestPhone":"+84912345678","service":$SERVICE,"therapist":$THERAPIST,""" +
        """"startAtEpochMillis":1000,"endAtEpochMillis":2000,"startHour":14.5,"startDayEpochDay":20000,"status":"PENDING",""" +
        """"subtotal":900000,"discount":0,"total":900000,"code":null,"note":null,"createdAtEpochMillis":500}"""

class BookingRemoteCallsTest {

    private val sent = mutableListOf<Sent>()

    private fun client(status: HttpStatusCode, reply: String): NetworkClient {
        val engine = MockEngine { request ->
            sent += Sent(
                "${request.method.value} ${request.url.encodedPath}",
                request.body.toByteArray().decodeToString(),
                request.headers[HttpHeaders.Authorization],
            )
            respond(reply, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }
        return NetworkClient(HttpClient(engine).withDefaults())
    }

    private fun bookings(status: HttpStatusCode = HttpStatusCode.OK, reply: String) =
        GuestBookingRepositoryImpl(client(status, reply), "http://api.test", AccessTokenSource { "a1" })

    @Test
    fun catalog_posts_to_spa_catalog() = runTest {
        val catalog = SpaCatalogRepositoryImpl(
            client(HttpStatusCode.OK, """{"services":[$SERVICE],"therapists":[$THERAPIST],"todayEpochDay":20000}"""),
            "http://api.test",
        ).catalog()

        assertEquals("POST /rest/v1/rpc/spa_catalog", sent.single().call)
        assertEquals("signature", catalog.services.single().id)
    }

    @Test
    fun availability_posts_the_function_parameters_as_the_body() = runTest {
        val slots = bookings(reply = """{"dayEpochDay":20000,"slots":[{"startAtEpochMillis":1000,"startHour":9.0,"available":true}]}""")
            .availability(20000, "mai", "signature")

        assertEquals(
            Sent("POST /rest/v1/rpc/spa_availability", """{"day":20000,"therapist":"mai","service":"signature"}""", null),
            sent.single(),
        )
        assertEquals(1, slots.size)
    }

    @Test
    fun availability_leaves_out_what_was_not_chosen() = runTest {
        bookings(reply = """{"dayEpochDay":20000,"slots":[]}""").availability(20000, null, null)

        assertEquals("""{"day":20000}""", sent.single().body)
    }

    @Test
    fun create_posts_the_request_to_spa_create_booking_and_any_2xx_is_created() = runTest {
        val attempt = bookings(reply = BOOKING).create("signature", null, 1000, "TET")

        assertEquals(
            Sent(
                "POST /rest/v1/rpc/spa_create_booking",
                """{"serviceSlug":"signature","startAtEpochMillis":1000,"code":"TET"}""",
                "Bearer a1",
            ),
            sent.single(),
        )
        assertIs<BookingAttempt.Done>(attempt)
    }

    @Test
    fun a_refusal_carries_the_server_code() = runTest {
        val refusing = bookings(HttpStatusCode.Conflict, """{"code":"slot_taken","message":"taken"}""")

        val attempt = refusing.create("signature", "mai", 1000, null)

        assertEquals(BookingAttempt.Refused("slot_taken"), attempt)
    }

    @Test
    fun mine_posts_to_spa_bookings() = runTest {
        val mine = bookings(reply = """{"bookings":[$BOOKING]}""").mine()

        assertEquals(Sent("POST /rest/v1/rpc/spa_bookings", "", "Bearer a1"), sent.single())
        assertEquals("b1", mine.single().id)
    }

    @Test
    fun cancel_posts_the_id_to_spa_cancel_booking() = runTest {
        bookings(reply = BOOKING).cancel("b1")

        assertEquals(Sent("POST /rest/v1/rpc/spa_cancel_booking", """{"id":"b1"}""", "Bearer a1"), sent.single())
    }
}

package com.tana.spa.feature.admin.data

import com.tana.spa.core.network.AccessTokenSource
import com.tana.spa.core.network.NetworkClient
import com.tana.spa.core.network.withDefaults
import com.tana.spa.feature.admin.domain.DeskRefused
import com.tana.spa.shared.auth.CreateAccountRequest
import com.tana.spa.shared.booking.CreateServiceRequest
import com.tana.spa.shared.booking.CreateTherapistRequest
import com.tana.spa.shared.booking.DeclineReasonDto
import com.tana.spa.shared.booking.ServiceCategoryDto
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
import kotlin.test.assertFailsWith

private data class Sent(val call: String, val body: String, val authorization: String?)

private const val SERVICE =
    """{"slug":"signature","name":"Signature","minutes":90,"price":900000,"category":"MASSAGE","includes":"Oil"}"""
private const val THERAPIST = """{"slug":"mai","name":"Mai","focus":"Deep tissue","accent":"MOSS","nextSlot":null}"""
private const val BOOKING =
    """{"id":"b1","reference":"TS-1","guestName":"Lan","guestPhone":"+84912345678","service":$SERVICE,"therapist":$THERAPIST,""" +
        """"startAtEpochMillis":1000,"endAtEpochMillis":2000,"startHour":14.5,"startDayEpochDay":20000,"status":"PENDING",""" +
        """"subtotal":900000,"discount":0,"total":900000,"code":null,"note":null,"createdAtEpochMillis":500}"""

class DeskRepositoryImplTest {

    private val sent = mutableListOf<Sent>()

    private fun desk(reply: String, status: HttpStatusCode = HttpStatusCode.OK): DeskRepositoryImpl {
        val engine = MockEngine { request ->
            sent += Sent(
                "${request.method.value} ${request.url.encodedPath}",
                request.body.toByteArray().decodeToString(),
                request.headers[HttpHeaders.Authorization],
            )
            respond(reply, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }
        return DeskRepositoryImpl(NetworkClient(HttpClient(engine).withDefaults()), "http://api.test", AccessTokenSource { "a1" })
    }

    @Test
    fun queue_posts_to_spa_staff_queue() = runTest {
        val queue = desk("""{"pending":[$BOOKING],"scheduled":[],"dayEpochDay":20000}""").queue()

        assertEquals(Sent("POST /rest/v1/rpc/spa_staff_queue", "", "Bearer a1"), sent.single())
        assertEquals("b1", queue.pending.single().id)
    }

    @Test
    fun confirm_posts_the_request_with_the_id() = runTest {
        desk(BOOKING).confirm("b1", force = true)

        assertEquals(Sent("POST /rest/v1/rpc/spa_confirm_booking", """{"force":true,"id":"b1"}""", "Bearer a1"), sent.single())
    }

    @Test
    fun decline_posts_the_request_with_the_id() = runTest {
        desk(BOOKING).decline("b1", DeclineReasonDto.NO_ROOM, "Tomorrow at 10")

        assertEquals(
            Sent(
                "POST /rest/v1/rpc/spa_decline_booking",
                """{"reason":"NO_ROOM","alternativeOffer":"Tomorrow at 10","id":"b1"}""",
                "Bearer a1",
            ),
            sent.single(),
        )
    }

    @Test
    fun a_visit_is_recorded_as_complete_or_no_show() = runTest {
        val desk = desk(BOOKING)

        desk.recordVisit("b1", attended = true)
        desk.recordVisit("b2", attended = false)

        assertEquals(
            listOf(
                Sent("POST /rest/v1/rpc/spa_complete_booking", """{"id":"b1"}""", "Bearer a1"),
                Sent("POST /rest/v1/rpc/spa_no_show_booking", """{"id":"b2"}""", "Bearer a1"),
            ),
            sent,
        )
    }

    @Test
    fun create_service_posts_the_request_to_spa_create_service() = runTest {
        desk(SERVICE).createService(CreateServiceRequest("Signature", "Oil", 90, 900000, ServiceCategoryDto.MASSAGE))

        assertEquals(
            Sent(
                "POST /rest/v1/rpc/spa_create_service",
                """{"name":"Signature","description":"Oil","minutes":90,"price":900000,"category":"MASSAGE"}""",
                "Bearer a1",
            ),
            sent.single(),
        )
    }

    @Test
    fun create_therapist_posts_the_request_to_spa_create_therapist() = runTest {
        desk(THERAPIST).createTherapist(CreateTherapistRequest("Mai", "Deep tissue"))

        assertEquals(Sent("POST /rest/v1/rpc/spa_create_therapist", """{"name":"Mai","focus":"Deep tissue"}""", "Bearer a1"), sent.single())
    }

    @Test
    fun create_account_posts_to_the_accounts_edge_function() = runTest {
        val created = desk("""{"id":"u2","displayName":"Hoa","email":"hoa@example.com","role":"staff"}""", HttpStatusCode.Created)
            .createAccount(CreateAccountRequest("Hoa", "hoa@example.com", "password123", "staff"))

        assertEquals(
            Sent(
                "POST /functions/v1/accounts",
                """{"displayName":"Hoa","email":"hoa@example.com","password":"password123","role":"staff"}""",
                "Bearer a1",
            ),
            sent.single(),
        )
        assertEquals("u2", created.id)
    }

    @Test
    fun a_refusal_carries_status_and_code() = runTest {
        val refused = assertFailsWith<DeskRefused> {
            desk("""{"code":"overlaps_another","message":"clash"}""", HttpStatusCode.Conflict).confirm("b1", force = false)
        }

        assertEquals(409 to "overlaps_another", refused.status to refused.code)
        assertEquals("""{"id":"b1"}""", sent.single().body)
    }
}

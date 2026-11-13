package com.tana.spa.feature.admin.data

import com.tana.spa.core.network.AccessTokenSource
import com.tana.spa.core.network.NetworkClient
import com.tana.spa.feature.admin.domain.DeskQueue
import com.tana.spa.feature.admin.domain.DeskRefused
import com.tana.spa.feature.admin.domain.DeskRepository
import com.tana.spa.feature.admin.domain.DeskRequest
import com.tana.spa.feature.admin.domain.DeskStatus
import com.tana.spa.feature.admin.domain.DeskTherapist
import com.tana.spa.shared.SupabasePaths
import com.tana.spa.shared.auth.CreateAccountRequest
import com.tana.spa.shared.auth.CreatedAccountDto
import com.tana.spa.shared.auth.ErrorResponse
import com.tana.spa.shared.booking.AssignTherapistRequest
import com.tana.spa.shared.booking.BookingDto
import com.tana.spa.shared.booking.BookingStatusDto
import com.tana.spa.shared.booking.ConfirmBookingRequest
import com.tana.spa.shared.booking.CreateProductRequest
import com.tana.spa.shared.booking.CreateServiceRequest
import com.tana.spa.shared.booking.CreateTherapistRequest
import com.tana.spa.shared.booking.DeclineBookingRequest
import com.tana.spa.shared.booking.DeclineReasonDto
import com.tana.spa.shared.booking.LinkTherapistRequest
import com.tana.spa.shared.booking.ProductDto
import com.tana.spa.shared.booking.ServiceDto
import com.tana.spa.shared.booking.ShiftsResponse
import com.tana.spa.shared.booking.StaffQueueResponse
import com.tana.spa.shared.booking.TeamResponse
import com.tana.spa.shared.booking.TherapistDto
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock

private const val MILLIS_PER_MINUTE = 60_000L

class DeskRepositoryImpl(private val networkClient: NetworkClient, private val baseUrl: String, private val tokens: AccessTokenSource) :
    DeskRepository {

    override suspend fun queue(): DeskQueue {
        val response = networkClient.httpClient.post(baseUrl + SupabasePaths.rpc("spa_staff_queue")) { authorize() }
        val body = response.requireSuccess().body<StaffQueueResponse>()
        val now = nowEpochMillis()
        return DeskQueue(
            pending = body.pending.map { it.toRequest(now) },
            scheduled = body.scheduled.map { it.toRequest(now) },
            dayEpochDay = body.dayEpochDay,
            team = body.therapists.map { it.toDesk() },
        )
    }

    override suspend fun confirm(id: String, force: Boolean, therapistSlug: String?): DeskRequest {
        val response = networkClient.httpClient.post(baseUrl + SupabasePaths.rpc("spa_confirm_booking")) {
            authorize()
            contentType(ContentType.Application.Json)
            setBody(withId(id, ConfirmBookingRequest(force, therapistSlug)))
        }
        return response.requireSuccess().body<BookingDto>().toRequest(nowEpochMillis())
    }

    override suspend fun assign(id: String, therapistSlug: String): DeskRequest {
        val response = networkClient.httpClient.post(baseUrl + SupabasePaths.rpc("spa_assign_therapist")) {
            authorize()
            contentType(ContentType.Application.Json)
            setBody(withId(id, AssignTherapistRequest(therapistSlug)))
        }
        return response.requireSuccess().body<BookingDto>().toRequest(nowEpochMillis())
    }

    override suspend fun decline(id: String, reason: DeclineReasonDto, alternative: String?): DeskRequest {
        val response = networkClient.httpClient.post(baseUrl + SupabasePaths.rpc("spa_decline_booking")) {
            authorize()
            contentType(ContentType.Application.Json)
            setBody(withId(id, DeclineBookingRequest(reason, alternative)))
        }
        return response.requireSuccess().body<BookingDto>().toRequest(nowEpochMillis())
    }

    override suspend fun recordVisit(id: String, attended: Boolean): DeskRequest {
        val function = if (attended) "spa_complete_booking" else "spa_no_show_booking"
        val response = networkClient.httpClient.post(baseUrl + SupabasePaths.rpc(function)) {
            authorize()
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("id", id) })
        }
        return response.requireSuccess().body<BookingDto>().toRequest(nowEpochMillis())
    }

    override suspend fun createService(request: CreateServiceRequest): ServiceDto {
        val response = networkClient.httpClient.post(baseUrl + SupabasePaths.rpc("spa_create_service")) {
            authorize()
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        return response.requireSuccess().body()
    }

    override suspend fun createProduct(request: CreateProductRequest): ProductDto {
        val response = networkClient.httpClient.post(baseUrl + SupabasePaths.rpc("spa_create_product")) {
            authorize()
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        return response.requireSuccess().body()
    }

    override suspend fun createTherapist(request: CreateTherapistRequest): TherapistDto {
        val response = networkClient.httpClient.post(baseUrl + SupabasePaths.rpc("spa_create_therapist")) {
            authorize()
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        return response.requireSuccess().body()
    }

    override suspend fun team(): List<DeskTherapist> {
        val response = networkClient.httpClient.post(baseUrl + SupabasePaths.rpc("spa_team")) { authorize() }
        return response.requireSuccess().body<TeamResponse>().therapists.map { it.toDesk() }
    }

    override suspend fun myShifts(): List<DeskRequest> {
        val response = networkClient.httpClient.post(baseUrl + SupabasePaths.rpc("spa_my_shifts")) { authorize() }
        val now = nowEpochMillis()
        return response.requireSuccess().body<ShiftsResponse>().shifts.map { it.toRequest(now) }
    }

    override suspend fun link(therapistSlug: String, email: String?): DeskTherapist {
        val response = networkClient.httpClient.post(baseUrl + SupabasePaths.rpc("spa_link_therapist")) {
            authorize()
            contentType(ContentType.Application.Json)
            setBody(LinkTherapistRequest(therapistSlug, email))
        }
        return response.requireSuccess().body<TherapistDto>().toDesk()
    }

    override suspend fun createAccount(request: CreateAccountRequest): CreatedAccountDto {
        val response = networkClient.httpClient.post(baseUrl + SupabasePaths.function("accounts")) {
            authorize()
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        return response.requireSuccess().body()
    }

    private fun nowEpochMillis(): Long = Clock.System.now().toEpochMilliseconds()

    private inline fun <reified T> withId(id: String, request: T) =
        JsonObject(Json.encodeToJsonElement(request).jsonObject + ("id" to JsonPrimitive(id)))

    private fun HttpRequestBuilder.authorize() {
        tokens.accessToken()?.let { bearerAuth(it) }
    }

    private suspend fun HttpResponse.requireSuccess(): HttpResponse {
        if (status.isSuccess()) return this
        // The server's code is what tells "confirm this clash with force" from "not your account".
        val code = runCatching { body<ErrorResponse>().code }.getOrNull()
        throw DeskRefused(status.value, code)
    }
}

private fun BookingDto.toRequest(nowEpochMillis: Long) = DeskRequest(
    id = id,
    reference = reference,
    guest = guestName,
    phone = guestPhone,
    serviceName = service.name,
    minutes = service.minutes,
    therapistSlug = therapist.slug,
    therapistName = therapist.name,
    startAtEpochMillis = startAtEpochMillis,
    startHour = startHour,
    startDayEpochDay = startDayEpochDay,
    status = status.toDeskStatus(),
    waitedMinutes = ((nowEpochMillis - createdAtEpochMillis) / MILLIS_PER_MINUTE).toInt().coerceAtLeast(0),
    total = total,
    discount = discount,
    code = code,
    note = note,
    conflict = conflict,
    declineReason = declineReason,
    freeTherapists = freeTherapists,
)

private fun TherapistDto.toDesk() = DeskTherapist(slug = slug, name = name, account = account)

private fun BookingStatusDto.toDeskStatus() = when (this) {
    BookingStatusDto.PENDING -> DeskStatus.PENDING
    BookingStatusDto.CONFIRMED -> DeskStatus.CONFIRMED
    BookingStatusDto.DECLINED -> DeskStatus.DECLINED
    BookingStatusDto.COMPLETED -> DeskStatus.COMPLETED
    BookingStatusDto.CANCELLED -> DeskStatus.CANCELLED
    BookingStatusDto.NO_SHOW -> DeskStatus.NO_SHOW
}

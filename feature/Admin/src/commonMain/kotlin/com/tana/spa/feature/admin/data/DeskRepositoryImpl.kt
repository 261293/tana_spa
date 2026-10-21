package com.tana.spa.feature.admin.data

import com.tana.spa.core.network.AccessTokenSource
import com.tana.spa.core.network.NetworkClient
import com.tana.spa.feature.admin.domain.DeskQueue
import com.tana.spa.feature.admin.domain.DeskRefused
import com.tana.spa.feature.admin.domain.DeskRepository
import com.tana.spa.feature.admin.domain.DeskRequest
import com.tana.spa.feature.admin.domain.DeskStatus
import com.tana.spa.shared.auth.AccountsApi
import com.tana.spa.shared.auth.CreateAccountRequest
import com.tana.spa.shared.auth.CreatedAccountDto
import com.tana.spa.shared.auth.ErrorResponse
import com.tana.spa.shared.booking.BookingApi
import com.tana.spa.shared.booking.BookingDto
import com.tana.spa.shared.booking.BookingStatusDto
import com.tana.spa.shared.booking.ConfirmBookingRequest
import com.tana.spa.shared.booking.CreateServiceRequest
import com.tana.spa.shared.booking.CreateTherapistRequest
import com.tana.spa.shared.booking.DeclineBookingRequest
import com.tana.spa.shared.booking.DeclineReasonDto
import com.tana.spa.shared.booking.ServiceDto
import com.tana.spa.shared.booking.StaffQueueResponse
import com.tana.spa.shared.booking.TherapistDto
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlin.time.Clock

private const val MILLIS_PER_MINUTE = 60_000L

class DeskRepositoryImpl(private val networkClient: NetworkClient, private val baseUrl: String, private val tokens: AccessTokenSource) :
    DeskRepository {

    override suspend fun queue(): DeskQueue {
        val response = networkClient.httpClient.get(baseUrl + BookingApi.OWNER_BOOKINGS) { authorize() }
        val body = response.requireSuccess().body<StaffQueueResponse>()
        val now = nowEpochMillis()
        return DeskQueue(
            pending = body.pending.map { it.toRequest(now) },
            scheduled = body.scheduled.map { it.toRequest(now) },
            dayEpochDay = body.dayEpochDay,
        )
    }

    override suspend fun confirm(id: String, force: Boolean): DeskRequest {
        val response = networkClient.httpClient.post(baseUrl + BookingApi.confirm(id)) {
            authorize()
            contentType(ContentType.Application.Json)
            setBody(ConfirmBookingRequest(force))
        }
        return response.requireSuccess().body<BookingDto>().toRequest(nowEpochMillis())
    }

    override suspend fun decline(id: String, reason: DeclineReasonDto, alternative: String?): DeskRequest {
        val response = networkClient.httpClient.post(baseUrl + BookingApi.decline(id)) {
            authorize()
            contentType(ContentType.Application.Json)
            setBody(DeclineBookingRequest(reason, alternative))
        }
        return response.requireSuccess().body<BookingDto>().toRequest(nowEpochMillis())
    }

    override suspend fun recordVisit(id: String, attended: Boolean): DeskRequest {
        val path = if (attended) BookingApi.complete(id) else BookingApi.noShow(id)
        val response = networkClient.httpClient.post(baseUrl + path) { authorize() }
        return response.requireSuccess().body<BookingDto>().toRequest(nowEpochMillis())
    }

    override suspend fun createService(request: CreateServiceRequest): ServiceDto {
        val response = networkClient.httpClient.post(baseUrl + BookingApi.OWNER_SERVICES) {
            authorize()
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        return response.requireSuccess().body()
    }

    override suspend fun createTherapist(request: CreateTherapistRequest): TherapistDto {
        val response = networkClient.httpClient.post(baseUrl + BookingApi.OWNER_THERAPISTS) {
            authorize()
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        return response.requireSuccess().body()
    }

    override suspend fun createAccount(request: CreateAccountRequest): CreatedAccountDto {
        val response = networkClient.httpClient.post(baseUrl + AccountsApi.BASE) {
            authorize()
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        return response.requireSuccess().body()
    }

    private fun nowEpochMillis(): Long = Clock.System.now().toEpochMilliseconds()

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
)

private fun BookingStatusDto.toDeskStatus() = when (this) {
    BookingStatusDto.PENDING -> DeskStatus.PENDING
    BookingStatusDto.CONFIRMED -> DeskStatus.CONFIRMED
    BookingStatusDto.DECLINED -> DeskStatus.DECLINED
    BookingStatusDto.COMPLETED -> DeskStatus.COMPLETED
    BookingStatusDto.CANCELLED -> DeskStatus.CANCELLED
    BookingStatusDto.NO_SHOW -> DeskStatus.NO_SHOW
}

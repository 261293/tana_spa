package com.tana.spa.feature.booking.data

import com.tana.spa.core.logging.AppLog
import com.tana.spa.core.network.AccessTokenSource
import com.tana.spa.core.network.NetworkClient
import com.tana.spa.feature.booking.domain.BookingAttempt
import com.tana.spa.feature.booking.domain.GuestBooking
import com.tana.spa.feature.booking.domain.GuestBookingRepository
import com.tana.spa.feature.booking.domain.Slot
import com.tana.spa.shared.SupabasePaths
import com.tana.spa.shared.auth.ErrorResponse
import com.tana.spa.shared.booking.AvailabilityResponse
import com.tana.spa.shared.booking.BookingDto
import com.tana.spa.shared.booking.BookingsResponse
import com.tana.spa.shared.booking.CreateBookingRequest
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val TAG = "GuestBookings"

class GuestBookingRepositoryImpl(
    private val networkClient: NetworkClient,
    private val baseUrl: String,
    private val tokens: AccessTokenSource,
) : GuestBookingRepository {

    override suspend fun availability(dayEpochDay: Int, therapistSlug: String?, serviceSlug: String?): List<Slot> {
        val response = networkClient.httpClient.post(baseUrl + SupabasePaths.rpc("spa_availability")) {
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonObject {
                    put("day", dayEpochDay)
                    therapistSlug?.let { put("therapist", it) }
                    serviceSlug?.let { put("service", it) }
                },
            )
        }
        check(response.status.isSuccess()) { "Availability request failed with ${response.status.value}" }
        return response.body<AvailabilityResponse>().slots.map { Slot(it.startAtEpochMillis, it.startHour, it.available) }
    }

    override suspend fun create(serviceSlug: String, therapistSlug: String?, startAtEpochMillis: Long, code: String?): BookingAttempt =
        attempt {
            networkClient.httpClient.post(baseUrl + SupabasePaths.rpc("spa_create_booking")) {
                authorize()
                contentType(ContentType.Application.Json)
                setBody(CreateBookingRequest(serviceSlug, therapistSlug, startAtEpochMillis, code))
            }
        }

    override suspend fun mine(): List<GuestBooking> {
        val response = networkClient.httpClient.post(baseUrl + SupabasePaths.rpc("spa_bookings")) { authorize() }
        check(response.status.isSuccess()) { "Bookings request failed with ${response.status.value}" }
        return response.body<BookingsResponse>().bookings.map { it.toGuestBooking() }
    }

    override suspend fun cancel(bookingId: String): BookingAttempt = attempt {
        networkClient.httpClient.post(baseUrl + SupabasePaths.rpc("spa_cancel_booking")) {
            authorize()
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("id", bookingId) })
        }
    }

    /** A refusal is an answer, not a failure: only a call that never arrived is [BookingAttempt.Offline]. */
    private suspend fun attempt(call: suspend () -> HttpResponse): BookingAttempt = try {
        val response = call()
        if (response.status.isSuccess()) {
            BookingAttempt.Done(response.body<BookingDto>().toGuestBooking())
        } else {
            BookingAttempt.Refused(runCatching { response.body<ErrorResponse>().code }.getOrNull())
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        // Ktor's wasm engine reports a failed fetch as kotlin.Error, which is not an Exception.
        AppLog.w(TAG, e) { "booking call failed (${e::class.simpleName})" }
        BookingAttempt.Offline
    }

    private fun HttpRequestBuilder.authorize() {
        tokens.accessToken()?.let { bearerAuth(it) }
    }
}

private fun BookingDto.toGuestBooking() = GuestBooking(
    id = id,
    reference = reference,
    serviceName = service.name,
    minutes = service.minutes,
    therapistName = therapist.name,
    startAtEpochMillis = startAtEpochMillis,
    startDayEpochDay = startDayEpochDay,
    startHour = startHour,
    status = status,
    total = total,
    discount = discount,
    code = code,
    declineReason = declineReason,
    alternativeOffer = alternativeOffer,
)

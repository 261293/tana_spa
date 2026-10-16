package com.tana.spa.feature.admin.domain

import com.tana.spa.shared.booking.DeclineReasonDto

enum class DeskStatus { PENDING, CONFIRMED, DECLINED, COMPLETED, CANCELLED, NO_SHOW }

/**
 * One booking as the desk reads it: everything the queue, the decision panel and the day's
 * schedule show, already worded in the house's own terms.
 *
 * [startHour] is the hour of the day the appointment starts (14.5 for 2:30 PM), which is what the
 * schedule column measures with; [startDayEpochDay] carries the day, because a request in the
 * queue can be for any day, not only today. [waitedMinutes] is how long the guest has been
 * waiting for an answer, counted when the queue was read.
 */
data class DeskRequest(
    val id: String,
    val reference: String,
    val guest: String,
    val phone: String,
    val serviceName: String,
    val minutes: Int,
    val therapistSlug: String,
    val therapistName: String,
    val startAtEpochMillis: Long,
    val startHour: Double,
    val startDayEpochDay: Int,
    val status: DeskStatus,
    val waitedMinutes: Int,
    val total: Int,
    val discount: Int,
    val code: String?,
    val note: String?,
    val conflict: String?,
    val declineReason: DeclineReasonDto?,
) {
    /** The desk is asked to chase anything a guest has waited this long for. */
    val stale: Boolean get() = status == DeskStatus.PENDING && waitedMinutes >= STALE_AFTER_MINUTES

    /** A visit can only be recorded once it was due to start; the server refuses it before then. */
    fun visitRecordable(nowEpochMillis: Long): Boolean = status == DeskStatus.CONFIRMED && nowEpochMillis >= startAtEpochMillis

    companion object {
        const val STALE_AFTER_MINUTES = 60
    }
}

/** The queue and the day behind it, as one read: they always belong to the same moment. */
data class DeskQueue(val pending: List<DeskRequest>, val scheduled: List<DeskRequest>, val dayEpochDay: Int)

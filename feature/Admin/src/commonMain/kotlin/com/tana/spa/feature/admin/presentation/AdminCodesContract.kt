package com.tana.spa.feature.admin.presentation

import com.tana.spa.shared.spa.AdminCode
import com.tana.spa.shared.spa.AdminCodeState
import com.tana.spa.shared.spa.SpaCatalog

enum class DiscountKind { PERCENT, AMOUNT }

enum class EligibleTreatments { ALL, MASSAGES, FACIALS, BODY }

/** The create form. Dates are the short strings the codes table shows ("Oct 20"). */
data class CodeDraft(
    val code: String = "AUTUMNSTEAM",
    val kind: DiscountKind = DiscountKind.PERCENT,
    val value: String = "15",
    val eligible: EligibleTreatments = EligibleTreatments.FACIALS,
    val starts: String = "Oct 20",
    val ends: String = "Nov 30",
    val limit: String = "150",
    val minimumSpend: String = "",
) {
    val numericValue: Int get() = value.toIntOrNull() ?: 0
    val offer: String get() = if (kind == DiscountKind.PERCENT) "$numericValue% off" else "$$numericValue off"
}

/** [filter] null is "All". */
data class AdminCodesState(
    val codes: List<AdminCode> = SpaCatalog.adminCodes,
    val filter: AdminCodeState? = null,
    val creating: Boolean = false,
    val draft: CodeDraft = CodeDraft(),
    val disabling: AdminCode? = null,
) {
    val shown: List<AdminCode> get() = codes.filter { filter == null || it.state == filter }
    val liveCount: Int get() = codes.count { it.state == AdminCodeState.LIVE }

    fun count(state: AdminCodeState): Int = codes.count { it.state == state }

    val duplicateCode: Boolean get() = codes.any { it.code == draft.code }
    val canPublish: Boolean get() = draft.code.length >= MIN_CODE_LENGTH && !duplicateCode && draft.numericValue > 0

    /** A live code for the same treatments, which guests cannot stack with the new one. */
    val overlappingCode: AdminCode?
        get() = codes.firstOrNull {
            it.state == AdminCodeState.LIVE && it.eligible.startsWith(draft.eligible.label)
        }

    private companion object {
        const val MIN_CODE_LENGTH = 4
    }
}

val EligibleTreatments.label: String
    get() = when (this) {
        EligibleTreatments.ALL -> "All treatments"
        EligibleTreatments.MASSAGES -> "Massages"
        EligibleTreatments.FACIALS -> "Facials"
        EligibleTreatments.BODY -> "Body treatments"
    }

sealed interface AdminCodesEvent {
    data class FilterChanged(val state: AdminCodeState?) : AdminCodesEvent
    data object CreateStarted : AdminCodesEvent
    data object CreateDismissed : AdminCodesEvent
    data class DraftChanged(val draft: CodeDraft) : AdminCodesEvent
    data object Publish : AdminCodesEvent
    data class DisableStarted(val code: AdminCode) : AdminCodesEvent
    data object DisableDismissed : AdminCodesEvent
    data object DisableConfirmed : AdminCodesEvent
}

sealed interface AdminCodesEffect {
    data class ShowPublished(val code: String, val starts: String) : AdminCodesEffect
    data class ShowDisabled(val code: String) : AdminCodesEffect
}

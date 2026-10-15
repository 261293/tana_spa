package com.tana.spa.feature.admin.presentation

import com.tana.spa.core.logging.AppLog
import com.tana.spa.core.mvi.MviViewModel
import com.tana.spa.shared.spa.AdminCode
import com.tana.spa.shared.spa.AdminCodeState

class AdminCodesViewModel : MviViewModel<AdminCodesState, AdminCodesEvent, AdminCodesEffect>(AdminCodesState()) {

    override fun handleEvent(event: AdminCodesEvent) = when (event) {
        is AdminCodesEvent.FilterChanged -> setState { copy(filter = event.state) }

        AdminCodesEvent.CreateStarted -> setState { copy(creating = true, draft = CodeDraft()) }

        AdminCodesEvent.CreateDismissed -> setState { copy(creating = false) }

        is AdminCodesEvent.DraftChanged -> setState { copy(draft = event.draft) }

        AdminCodesEvent.Publish -> {
            val draft = state.value.draft
            AppLog.i(logTag) { "code ${draft.code} published" }
            setState {
                // A published code is scheduled, never instantly live: it starts on its own date.
                copy(
                    codes = listOf(draft.toAdminCode()) + codes,
                    creating = false,
                )
            }
            sendEffect(AdminCodesEffect.ShowPublished(draft.code, draft.starts))
        }

        is AdminCodesEvent.DisableStarted -> setState { copy(disabling = event.code) }

        AdminCodesEvent.DisableDismissed -> setState { copy(disabling = null) }

        AdminCodesEvent.DisableConfirmed -> {
            val disabling = state.value.disabling
            if (disabling != null) {
                AppLog.i(logTag) { "code ${disabling.code} disabled" }
                setState {
                    copy(
                        codes = codes.map {
                            if (it.code == disabling.code) {
                                it.copy(state = AdminCodeState.DISABLED, note = DISABLED_TODAY)
                            } else {
                                it
                            }
                        },
                        disabling = null,
                    )
                }
                sendEffect(AdminCodesEffect.ShowDisabled(disabling.code))
            } else {
                setState { copy(disabling = null) }
            }
        }
    }

    private fun CodeDraft.toAdminCode() = AdminCode(
        code = code,
        offer = offer,
        eligible = eligible.label + if (minimumSpend.isNotBlank()) " - min $$minimumSpend" else "",
        starts = starts,
        ends = ends,
        used = 0,
        limit = limit.toIntOrNull(),
        state = AdminCodeState.SCHEDULED,
    )

    private companion object {
        /** Matched to `admin_disabled_note`; the table shows it next to the code. */
        const val DISABLED_TODAY = "Disabled today"
    }
}

package com.tana.spa.feature.admin.presentation

import androidx.lifecycle.viewModelScope
import com.tana.spa.core.logging.AppLog
import com.tana.spa.core.mvi.MviViewModel
import com.tana.spa.core.network.CallerRole
import com.tana.spa.feature.admin.domain.DeskRefused
import com.tana.spa.feature.admin.domain.DeskRepository
import com.tana.spa.shared.auth.AuthValidation
import com.tana.spa.shared.auth.CreateAccountRequest
import com.tana.spa.shared.auth.SpaRoles
import com.tana.spa.shared.booking.CreateServiceRequest
import com.tana.spa.shared.booking.CreateTherapistRequest
import com.tana.spa.shared.booking.SpaValidation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * The owner's two forms. They are checked with [SpaValidation], the same rules the routes apply,
 * so a form that passes here is not refused there for a reason the guest-facing wording cannot
 * explain.
 */
class AdminMenuViewModel(private val desk: DeskRepository, callerRole: CallerRole) :
    MviViewModel<AdminMenuState, AdminMenuEvent, AdminMenuEffect>(AdminMenuState()) {

    init {
        val role = callerRole.role()
        val creatable = SpaRoles.creatableBy(role)
        setState {
            copy(
                permissions = SpaRoles.permissionsOf(role),
                creatableRoles = ROLE_ORDER.filter { it in creatable },
                person = person.copy(role = ROLE_ORDER.firstOrNull { it in creatable } ?: SpaRoles.CUSTOMER),
            )
        }
    }

    override fun handleEvent(event: AdminMenuEvent) {
        when (event) {
            // Editing a field clears its own error: the owner is already fixing it.
            is AdminMenuEvent.ServiceNameChanged -> setState { copy(service = service.copy(name = event.value, nameError = null)) }

            is AdminMenuEvent.ServiceDescriptionChanged ->
                setState { copy(service = service.copy(description = event.value, descriptionError = null)) }

            is AdminMenuEvent.ServiceMinutesChanged ->
                setState { copy(service = service.copy(minutes = event.value.digits(), minutesError = null)) }

            is AdminMenuEvent.ServicePriceChanged ->
                setState { copy(service = service.copy(price = event.value.digits(), priceError = null)) }

            is AdminMenuEvent.ServiceCategoryChanged -> setState { copy(service = service.copy(category = event.category)) }

            AdminMenuEvent.SaveService -> saveService()

            is AdminMenuEvent.TherapistNameChanged ->
                setState { copy(therapist = therapist.copy(name = event.value, nameError = null)) }

            is AdminMenuEvent.TherapistFocusChanged ->
                setState { copy(therapist = therapist.copy(focus = event.value, focusError = null)) }

            AdminMenuEvent.SaveTherapist -> saveTherapist()

            is AdminMenuEvent.PersonNameChanged -> setState { copy(person = person.copy(name = event.value, nameError = null)) }

            is AdminMenuEvent.PersonEmailChanged ->
                setState { copy(person = person.copy(email = event.value, emailError = null)) }

            is AdminMenuEvent.PersonPasswordChanged ->
                setState { copy(person = person.copy(password = event.value, passwordError = null)) }

            is AdminMenuEvent.PersonRoleChanged -> setState { copy(person = person.copy(role = event.role)) }

            AdminMenuEvent.SavePerson -> savePerson()
        }
    }

    private fun saveService() {
        val form = state.value.service
        if (state.value.savingService) return
        val request = CreateServiceRequest(
            name = form.name.trim(),
            description = form.description.trim(),
            minutes = form.minutes.toIntOrNull() ?: 0,
            price = form.price.toIntOrNull() ?: 0,
            category = form.category,
        )
        val checked = form.copy(
            nameError = SpaValidation.validateName(request.name),
            descriptionError = SpaValidation.validateDescription(request.description),
            minutesError = SpaValidation.validateMinutes(request.minutes),
            priceError = SpaValidation.validatePrice(request.price),
        )
        setState { copy(service = checked) }
        if (SpaValidation.firstInvalidField(request) != null) {
            AppLog.i(logTag) { "new treatment rejected locally: ${SpaValidation.firstInvalidField(request)}" }
            return
        }

        setState { copy(savingService = true) }
        viewModelScope.launch {
            runCatchingCancellable { desk.createService(request) }
                .onSuccess { added ->
                    AppLog.i(logTag) { "treatment ${added.slug} added to the menu" }
                    setState { copy(service = ServiceForm(), savingService = false) }
                    sendEffect(AdminMenuEffect.ShowServiceAdded(added.name))
                }
                .onFailure { failure ->
                    setState { copy(savingService = false) }
                    sendEffect(AdminMenuEffect.ShowRefused((failure as? DeskRefused)?.code))
                }
        }
    }

    private fun saveTherapist() {
        val form = state.value.therapist
        if (state.value.savingTherapist) return
        val request = CreateTherapistRequest(name = form.name.trim(), focus = form.focus.trim(), accent = form.accent)
        val checked = form.copy(
            nameError = SpaValidation.validateName(request.name),
            focusError = SpaValidation.validateFocus(request.focus),
        )
        setState { copy(therapist = checked) }
        if (SpaValidation.firstInvalidField(request) != null) return

        setState { copy(savingTherapist = true) }
        viewModelScope.launch {
            runCatchingCancellable { desk.createTherapist(request) }
                .onSuccess { added ->
                    AppLog.i(logTag) { "therapist ${added.slug} joined the team" }
                    setState { copy(therapist = TherapistForm(), savingTherapist = false) }
                    sendEffect(AdminMenuEffect.ShowTherapistAdded(added.name))
                }
                .onFailure { failure ->
                    setState { copy(savingTherapist = false) }
                    sendEffect(AdminMenuEffect.ShowRefused((failure as? DeskRefused)?.code))
                }
        }
    }

    private fun savePerson() {
        val form = state.value.person
        if (state.value.savingPerson) return
        if (form.role !in state.value.creatableRoles) {
            AppLog.w(logTag) { "refusing to ask for a ${form.role} account, which this one may not create" }
            return
        }
        val request = CreateAccountRequest(
            displayName = form.name.trim(),
            email = form.email.trim(),
            password = form.password,
            role = form.role,
        )
        val checked = form.copy(
            nameError = AuthValidation.validateName(request.displayName),
            emailError = AuthValidation.validateEmail(request.email),
            passwordError = AuthValidation.validatePassword(request.password),
        )
        setState { copy(person = checked) }
        if (checked.nameError != null || checked.emailError != null || checked.passwordError != null) return

        setState { copy(savingPerson = true) }
        viewModelScope.launch {
            runCatchingCancellable { desk.createAccount(request) }
                .onSuccess { added ->
                    AppLog.i(logTag) { "took on a ${added.role}" }
                    setState { copy(person = PersonForm(role = form.role), savingPerson = false) }
                    sendEffect(AdminMenuEffect.ShowPersonAdded(added.displayName))
                }
                .onFailure { failure ->
                    setState { copy(savingPerson = false) }
                    sendEffect(AdminMenuEffect.ShowRefused((failure as? DeskRefused)?.code))
                }
        }
    }

    /** Durations and prices are whole numbers; anything else cannot be typed into them. */
    private fun String.digits(): String = filter { it.isDigit() }.take(MAX_DIGITS)

    private inline fun <T> runCatchingCancellable(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        // Ktor's wasm engine reports a failed fetch as kotlin.Error, which is not an Exception.
        AppLog.w(logTag, e) { "menu call failed (${e::class.simpleName})" }
        Result.failure(e)
    }

    private companion object {
        const val MAX_DIGITS = 6

        val ROLE_ORDER = listOf(SpaRoles.ADMIN, SpaRoles.OWNER, SpaRoles.STAFF, SpaRoles.CUSTOMER)
    }
}

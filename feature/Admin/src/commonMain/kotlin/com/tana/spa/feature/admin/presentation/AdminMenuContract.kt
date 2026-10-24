package com.tana.spa.feature.admin.presentation

import com.tana.spa.shared.auth.EmailError
import com.tana.spa.shared.auth.NameError
import com.tana.spa.shared.auth.PasswordError
import com.tana.spa.shared.auth.SpaPermission
import com.tana.spa.shared.auth.SpaRoles
import com.tana.spa.shared.booking.DurationError
import com.tana.spa.shared.booking.PriceError
import com.tana.spa.shared.booking.ServiceCategoryDto
import com.tana.spa.shared.booking.TextFieldError
import com.tana.spa.shared.booking.TherapistAccentDto

/** Errors are typed, not strings: the screen picks the wording, as everywhere else in this app. */
data class ServiceForm(
    val name: String = "",
    val description: String = "",
    val minutes: String = "",
    val price: String = "",
    val category: ServiceCategoryDto = ServiceCategoryDto.MASSAGE,
    val nameError: TextFieldError? = null,
    val descriptionError: TextFieldError? = null,
    val minutesError: DurationError? = null,
    val priceError: PriceError? = null,
)

data class TherapistForm(
    val name: String = "",
    val focus: String = "",
    val accent: TherapistAccentDto = TherapistAccentDto.MOSS,
    val nameError: TextFieldError? = null,
    val focusError: TextFieldError? = null,
)

data class PersonForm(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val role: String = SpaRoles.STAFF,
    val nameError: NameError? = null,
    val emailError: EmailError? = null,
    val passwordError: PasswordError? = null,
)

data class AdminMenuState(
    val service: ServiceForm = ServiceForm(),
    val therapist: TherapistForm = TherapistForm(),
    val person: PersonForm = PersonForm(),
    val savingService: Boolean = false,
    val savingTherapist: Boolean = false,
    val savingPerson: Boolean = false,
    val permissions: Set<SpaPermission> = emptySet(),
    val creatableRoles: List<String> = emptyList(),
)

sealed interface AdminMenuEvent {
    data class ServiceNameChanged(val value: String) : AdminMenuEvent
    data class ServiceDescriptionChanged(val value: String) : AdminMenuEvent
    data class ServiceMinutesChanged(val value: String) : AdminMenuEvent
    data class ServicePriceChanged(val value: String) : AdminMenuEvent
    data class ServiceCategoryChanged(val category: ServiceCategoryDto) : AdminMenuEvent
    data object SaveService : AdminMenuEvent

    data class TherapistNameChanged(val value: String) : AdminMenuEvent
    data class TherapistFocusChanged(val value: String) : AdminMenuEvent
    data object SaveTherapist : AdminMenuEvent

    data class PersonNameChanged(val value: String) : AdminMenuEvent
    data class PersonEmailChanged(val value: String) : AdminMenuEvent
    data class PersonPasswordChanged(val value: String) : AdminMenuEvent
    data class PersonRoleChanged(val role: String) : AdminMenuEvent
    data object SavePerson : AdminMenuEvent
}

sealed interface AdminMenuEffect {
    /** The guest catalogue has it from now on, which is what the owner is really waiting to hear. */
    data class ShowServiceAdded(val name: String) : AdminMenuEffect
    data class ShowTherapistAdded(val name: String) : AdminMenuEffect
    data class ShowPersonAdded(val name: String) : AdminMenuEffect
    data class ShowRefused(val code: String?) : AdminMenuEffect
}

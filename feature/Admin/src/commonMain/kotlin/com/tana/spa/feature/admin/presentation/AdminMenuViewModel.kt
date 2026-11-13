package com.tana.spa.feature.admin.presentation

import androidx.lifecycle.viewModelScope
import com.tana.spa.core.logging.AppLog
import com.tana.spa.core.mvi.MviViewModel
import com.tana.spa.core.network.CallerRole
import com.tana.spa.feature.admin.domain.DeskRefused
import com.tana.spa.feature.admin.domain.DeskRepository
import com.tana.spa.shared.auth.AuthValidation
import com.tana.spa.shared.auth.CreateAccountRequest
import com.tana.spa.shared.auth.SpaPermission
import com.tana.spa.shared.auth.SpaRoles
import com.tana.spa.shared.booking.CreateProductRequest
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
        if (SpaPermission.MANAGE_THERAPISTS in state.value.permissions) loadTeam()
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
                setState { copy(service = service.copy(price = event.value.digits(MAX_PRICE_DIGITS), priceError = null)) }

            is AdminMenuEvent.ServiceCategoryChanged -> setState { copy(service = service.copy(category = event.category)) }

            AdminMenuEvent.SaveService -> saveService()

            is AdminMenuEvent.ProductNameChanged -> setState { copy(product = product.copy(name = event.value, nameError = null)) }

            is AdminMenuEvent.ProductBrandChanged -> setState { copy(product = product.copy(brand = event.value, brandError = null)) }

            is AdminMenuEvent.ProductDescriptionChanged ->
                setState { copy(product = product.copy(description = event.value, descriptionError = null)) }

            is AdminMenuEvent.ProductPriceChanged ->
                setState { copy(product = product.copy(price = event.value.digits(MAX_PRICE_DIGITS), priceError = null)) }

            is AdminMenuEvent.ProductSizeChanged -> setState { copy(product = product.copy(size = event.value, sizeError = null)) }

            AdminMenuEvent.SaveProduct -> saveProduct()

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

            is AdminMenuEvent.AccountDraftChanged ->
                setState { copy(accountDrafts = accountDrafts + (event.therapistSlug to event.value)) }

            is AdminMenuEvent.LinkAccount -> link(event.therapistSlug)
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

    private fun saveProduct() {
        val form = state.value.product
        if (state.value.savingProduct) return
        val request = CreateProductRequest(
            name = form.name.trim(),
            brand = form.brand.trim(),
            description = form.description.trim(),
            price = form.price.toIntOrNull() ?: 0,
            size = form.size.trim(),
        )
        val checked = form.copy(
            nameError = SpaValidation.validateName(request.name),
            brandError = SpaValidation.validateLabel(request.brand, SpaValidation.MAX_BRAND_LENGTH),
            descriptionError = SpaValidation.validateDescription(request.description),
            priceError = SpaValidation.validatePrice(request.price),
            sizeError = SpaValidation.validateLabel(request.size, SpaValidation.MAX_SIZE_LENGTH),
        )
        setState { copy(product = checked) }
        if (SpaValidation.firstInvalidField(request) != null) {
            AppLog.i(logTag) { "new product rejected locally: ${SpaValidation.firstInvalidField(request)}" }
            return
        }

        setState { copy(savingProduct = true) }
        viewModelScope.launch {
            runCatchingCancellable { desk.createProduct(request) }
                .onSuccess { added ->
                    AppLog.i(logTag) { "product ${added.slug} put on the shelf" }
                    setState { copy(product = ProductForm(), savingProduct = false) }
                    sendEffect(AdminMenuEffect.ShowProductAdded(added.name))
                }
                .onFailure { failure ->
                    setState { copy(savingProduct = false) }
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
                    loadTeam()
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

    private fun loadTeam() {
        viewModelScope.launch {
            runCatchingCancellable { desk.team() }.onSuccess { team -> setState { copy(team = team) } }
        }
    }

    private fun link(slug: String) {
        val therapist = state.value.team.firstOrNull { it.slug == slug } ?: return
        if (state.value.linking != null) return
        val email = state.value.accountDraft(therapist).trim().ifEmpty { null }
        setState { copy(linking = slug) }
        viewModelScope.launch {
            runCatchingCancellable { desk.link(slug, email) }
                .onSuccess { linked ->
                    AppLog.i(logTag) { "therapist $slug ${if (linked.account == null) "unlinked" else "linked to an account"}" }
                    setState {
                        copy(team = team.map { if (it.slug == slug) linked else it }, accountDrafts = accountDrafts - slug, linking = null)
                    }
                    sendEffect(AdminMenuEffect.ShowLinked(linked.name, linked.account))
                }
                .onFailure { failure ->
                    setState { copy(linking = null) }
                    sendEffect(AdminMenuEffect.ShowLinkRefused((failure as? DeskRefused)?.code))
                }
        }
    }

    /** Durations and prices are whole numbers; anything else cannot be typed into them. */
    private fun String.digits(limit: Int = MAX_DIGITS): String = filter { it.isDigit() }.take(limit)

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
        const val MAX_PRICE_DIGITS = 9

        val ROLE_ORDER = listOf(SpaRoles.ADMIN, SpaRoles.OWNER, SpaRoles.STAFF, SpaRoles.CUSTOMER)
    }
}

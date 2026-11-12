package com.tana.spa.feature.admin

import com.tana.spa.core.network.CallerRole
import com.tana.spa.feature.admin.domain.DeskQueue
import com.tana.spa.feature.admin.domain.DeskRefused
import com.tana.spa.feature.admin.domain.DeskRepository
import com.tana.spa.feature.admin.domain.DeskRequest
import com.tana.spa.feature.admin.domain.DeskTherapist
import com.tana.spa.feature.admin.presentation.AdminMenuEffect
import com.tana.spa.feature.admin.presentation.AdminMenuEvent
import com.tana.spa.feature.admin.presentation.AdminMenuViewModel
import com.tana.spa.shared.auth.AccountErrorCodes
import com.tana.spa.shared.auth.CreateAccountRequest
import com.tana.spa.shared.auth.CreatedAccountDto
import com.tana.spa.shared.auth.SpaPermission
import com.tana.spa.shared.auth.SpaRoles
import com.tana.spa.shared.booking.CreateServiceRequest
import com.tana.spa.shared.booking.CreateTherapistRequest
import com.tana.spa.shared.booking.DeclineReasonDto
import com.tana.spa.shared.booking.DurationError
import com.tana.spa.shared.booking.ServiceCategoryDto
import com.tana.spa.shared.booking.ServiceDto
import com.tana.spa.shared.booking.SpaValidation
import com.tana.spa.shared.booking.TextFieldError
import com.tana.spa.shared.booking.TherapistAccentDto
import com.tana.spa.shared.booking.TherapistDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdminMenuViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private class FakeDesk : DeskRepository {
        val services = mutableListOf<CreateServiceRequest>()
        val therapists = mutableListOf<CreateTherapistRequest>()
        val accounts = mutableListOf<CreateAccountRequest>()
        var refusal: DeskRefused? = null

        override suspend fun queue() = DeskQueue(emptyList(), emptyList(), 0)
        override suspend fun confirm(id: String, force: Boolean, therapistSlug: String?): DeskRequest = error("not used")
        override suspend fun assign(id: String, therapistSlug: String): DeskRequest = error("not used")

        var team = listOf(DeskTherapist("mai", "Mai Tran"), DeskTherapist("lena", "Lena", "lena@tana.test"))
        val links = mutableListOf<Pair<String, String?>>()
        override suspend fun team() = team
        override suspend fun myShifts(): List<DeskRequest> = emptyList()

        override suspend fun link(therapistSlug: String, email: String?): DeskTherapist {
            links += therapistSlug to email
            refusal?.let { throw it }
            return team.first { it.slug == therapistSlug }.copy(account = email)
        }
        override suspend fun decline(id: String, reason: DeclineReasonDto, alternative: String?): DeskRequest = error("not used")
        override suspend fun recordVisit(id: String, attended: Boolean): DeskRequest = error("not used")

        override suspend fun createService(request: CreateServiceRequest): ServiceDto {
            services += request
            refusal?.let { throw it }
            return ServiceDto(
                slug = SpaValidation.slugify(request.name),
                name = request.name,
                minutes = request.minutes,
                price = request.price,
                category = request.category,
                includes = request.description,
            )
        }

        override suspend fun createTherapist(request: CreateTherapistRequest): TherapistDto {
            therapists += request
            refusal?.let { throw it }
            return TherapistDto(SpaValidation.slugify(request.name), request.name, request.focus, TherapistAccentDto.MOSS, null)
        }

        override suspend fun createAccount(request: CreateAccountRequest): CreatedAccountDto {
            accounts += request
            refusal?.let { throw it }
            return CreatedAccountDto(id = "u1", displayName = request.displayName, email = request.email, role = request.role)
        }
    }

    private val desk = FakeDesk()
    private fun viewModel(role: String = SpaRoles.OWNER) = AdminMenuViewModel(desk, CallerRole { role })

    private fun AdminMenuViewModel.fillService(
        name: String = "Pine Ritual",
        description: String = "Pine oil and steam",
        minutes: String = "75",
        price: String = "130",
    ) {
        onEvent(AdminMenuEvent.ServiceNameChanged(name))
        onEvent(AdminMenuEvent.ServiceDescriptionChanged(description))
        onEvent(AdminMenuEvent.ServiceMinutesChanged(minutes))
        onEvent(AdminMenuEvent.ServicePriceChanged(price))
    }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun a_complete_treatment_is_sent_and_the_form_is_cleared() = runTest(dispatcher) {
        val vm = viewModel()
        vm.fillService()
        vm.onEvent(AdminMenuEvent.ServiceCategoryChanged(ServiceCategoryDto.BODY))

        vm.onEvent(AdminMenuEvent.SaveService)
        testScheduler.advanceUntilIdle()

        assertEquals(
            listOf(CreateServiceRequest("Pine Ritual", "Pine oil and steam", 75, 130, ServiceCategoryDto.BODY)),
            desk.services.toList(),
        )
        assertEquals("", vm.state.value.service.name, "a saved form is ready for the next treatment")
        assertEquals(AdminMenuEffect.ShowServiceAdded("Pine Ritual"), vm.effects.first())
    }

    @Test
    fun an_incomplete_treatment_is_shown_field_by_field_and_never_sent() = runTest(dispatcher) {
        val vm = viewModel()
        vm.fillService(name = "  ", minutes = "50", price = "")

        vm.onEvent(AdminMenuEvent.SaveService)
        testScheduler.advanceUntilIdle()

        val form = vm.state.value.service
        assertEquals(TextFieldError.EMPTY, form.nameError)
        assertEquals(DurationError.NOT_A_MULTIPLE, form.minutesError)
        assertNull(form.priceError, "an empty price reads as free, which the spa allows")
        assertTrue(desk.services.isEmpty(), "nothing the server would refuse should leave the device")
    }

    @Test
    fun fixing_a_field_clears_its_own_message() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onEvent(AdminMenuEvent.SaveService)
        assertEquals(TextFieldError.EMPTY, vm.state.value.service.nameError)

        vm.onEvent(AdminMenuEvent.ServiceNameChanged("Pine Ritual"))

        assertNull(vm.state.value.service.nameError)
        assertEquals(TextFieldError.EMPTY, vm.state.value.service.descriptionError, "the others still stand")
    }

    @Test
    fun a_duration_or_price_only_takes_digits() = runTest(dispatcher) {
        val vm = viewModel()

        vm.onEvent(AdminMenuEvent.ServiceMinutesChanged("9o min"))
        vm.onEvent(AdminMenuEvent.ServicePriceChanged("4.125.000đ"))

        assertEquals("9", vm.state.value.service.minutes)
        assertEquals("4125000", vm.state.value.service.price)
    }

    @Test
    fun a_refusal_from_the_spa_is_said_out_loud_and_the_form_is_kept() = runTest(dispatcher) {
        val vm = viewModel()
        desk.refusal = DeskRefused(403, "forbidden")
        vm.fillService()

        vm.onEvent(AdminMenuEvent.SaveService)
        testScheduler.advanceUntilIdle()

        assertIs<AdminMenuEffect.ShowRefused>(vm.effects.first())
        assertEquals("Pine Ritual", vm.state.value.service.name, "the owner must not have to type it again")
        assertTrue(!vm.state.value.savingService)
    }

    @Test
    fun a_therapist_needs_a_name_and_a_focus_before_it_is_sent() = runTest(dispatcher) {
        val vm = viewModel()

        vm.onEvent(AdminMenuEvent.TherapistNameChanged("Noor"))
        vm.onEvent(AdminMenuEvent.SaveTherapist)
        testScheduler.advanceUntilIdle()
        assertEquals(TextFieldError.EMPTY, vm.state.value.therapist.focusError)
        assertTrue(desk.therapists.isEmpty())

        vm.onEvent(AdminMenuEvent.TherapistFocusChanged("Facials & skin"))
        vm.onEvent(AdminMenuEvent.SaveTherapist)
        testScheduler.advanceUntilIdle()

        assertEquals(listOf(CreateTherapistRequest("Noor", "Facials & skin")), desk.therapists.toList())
        assertEquals(AdminMenuEffect.ShowTherapistAdded("Noor"), vm.effects.first())
    }

    @Test
    fun the_owner_is_offered_staff_and_customer_but_never_owner() {
        val vm = viewModel(SpaRoles.OWNER)

        assertEquals(listOf(SpaRoles.STAFF, SpaRoles.CUSTOMER), vm.state.value.creatableRoles)
        assertEquals(SpaRoles.STAFF, vm.state.value.person.role)
    }

    @Test
    fun an_administrator_is_offered_every_role() {
        val vm = viewModel(SpaRoles.ADMIN)

        assertEquals(
            listOf(SpaRoles.ADMIN, SpaRoles.OWNER, SpaRoles.STAFF, SpaRoles.CUSTOMER),
            vm.state.value.creatableRoles,
        )
    }

    @Test
    fun staff_are_offered_no_forms_at_all_on_this_screen() {
        val vm = viewModel(SpaRoles.STAFF)

        assertTrue(vm.state.value.creatableRoles.isEmpty())
        assertTrue(SpaPermission.MANAGE_SERVICES !in vm.state.value.permissions)
        assertTrue(SpaPermission.MANAGE_THERAPISTS !in vm.state.value.permissions)
        assertTrue(SpaPermission.MANAGE_ACCOUNTS !in vm.state.value.permissions)
    }

    @Test
    fun taking_somebody_on_sends_the_form_and_clears_it() = runTest(dispatcher) {
        val vm = viewModel(SpaRoles.OWNER)
        vm.onEvent(AdminMenuEvent.PersonNameChanged("Linh Nguyen"))
        vm.onEvent(AdminMenuEvent.PersonEmailChanged("linh@tana.local"))
        vm.onEvent(AdminMenuEvent.PersonPasswordChanged("password123"))

        vm.onEvent(AdminMenuEvent.SavePerson)
        testScheduler.advanceUntilIdle()

        assertEquals(1, desk.accounts.size)
        assertEquals(SpaRoles.STAFF, desk.accounts.single().role)
        assertEquals("", vm.state.value.person.name)
    }

    @Test
    fun a_form_that_fails_the_shared_rules_is_never_sent() = runTest(dispatcher) {
        val vm = viewModel(SpaRoles.OWNER)
        vm.onEvent(AdminMenuEvent.PersonNameChanged("Linh Nguyen"))
        vm.onEvent(AdminMenuEvent.PersonEmailChanged("not-an-email"))
        vm.onEvent(AdminMenuEvent.PersonPasswordChanged("short"))

        vm.onEvent(AdminMenuEvent.SavePerson)
        testScheduler.advanceUntilIdle()

        assertTrue(desk.accounts.isEmpty())
        assertNotNull(vm.state.value.person.emailError)
        assertNotNull(vm.state.value.person.passwordError)
    }

    @Test
    fun an_owner_asking_for_an_owner_account_is_not_even_sent() = runTest(dispatcher) {
        val vm = viewModel(SpaRoles.OWNER)
        vm.onEvent(AdminMenuEvent.PersonNameChanged("Someone Else"))
        vm.onEvent(AdminMenuEvent.PersonEmailChanged("someone@tana.local"))
        vm.onEvent(AdminMenuEvent.PersonPasswordChanged("password123"))
        vm.onEvent(AdminMenuEvent.PersonRoleChanged(SpaRoles.OWNER))

        vm.onEvent(AdminMenuEvent.SavePerson)
        testScheduler.advanceUntilIdle()

        assertTrue(desk.accounts.isEmpty())
    }

    @Test
    fun linking_sends_the_trimmed_email_and_an_empty_field_unlinks() = runTest(dispatcher) {
        val vm = viewModel()
        testScheduler.advanceUntilIdle()
        assertEquals("lena@tana.test", vm.state.value.accountDraft(vm.state.value.team[1]), "the field starts on the linked account")

        vm.onEvent(AdminMenuEvent.AccountDraftChanged("mai", "  mai@tana.test "))
        vm.onEvent(AdminMenuEvent.LinkAccount("mai"))
        testScheduler.advanceUntilIdle()
        vm.onEvent(AdminMenuEvent.AccountDraftChanged("lena", " "))
        vm.onEvent(AdminMenuEvent.LinkAccount("lena"))
        testScheduler.advanceUntilIdle()

        assertEquals(listOf<Pair<String, String?>>("mai" to "mai@tana.test", "lena" to null), desk.links.toList())
        assertEquals(listOf("mai@tana.test", null), vm.state.value.team.map { it.account })
        assertEquals(AdminMenuEffect.ShowLinked("Mai Tran", "mai@tana.test"), vm.effects.first())
    }

    @Test
    fun a_refused_link_says_why_and_keeps_what_was_typed() = runTest(dispatcher) {
        val vm = viewModel()
        testScheduler.advanceUntilIdle()
        desk.refusal = DeskRefused(400, AccountErrorCodes.ROLE_NOT_ALLOWED)

        vm.onEvent(AdminMenuEvent.AccountDraftChanged("mai", "guest@tana.test"))
        vm.onEvent(AdminMenuEvent.LinkAccount("mai"))
        testScheduler.advanceUntilIdle()

        assertEquals(AdminMenuEffect.ShowLinkRefused(AccountErrorCodes.ROLE_NOT_ALLOWED), vm.effects.first())
        assertEquals("guest@tana.test", vm.state.value.accountDraft(vm.state.value.team[0]))
    }
}

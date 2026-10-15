package com.tana.spa.feature.admin

import com.tana.spa.feature.admin.domain.DeskQueue
import com.tana.spa.feature.admin.domain.DeskRefused
import com.tana.spa.feature.admin.domain.DeskRepository
import com.tana.spa.feature.admin.domain.DeskRequest
import com.tana.spa.feature.admin.presentation.AdminMenuEffect
import com.tana.spa.feature.admin.presentation.AdminMenuEvent
import com.tana.spa.feature.admin.presentation.AdminMenuViewModel
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdminMenuViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private class FakeDesk : DeskRepository {
        val services = mutableListOf<CreateServiceRequest>()
        val therapists = mutableListOf<CreateTherapistRequest>()
        var refusal: DeskRefused? = null

        override suspend fun queue() = DeskQueue(emptyList(), emptyList(), 0)
        override suspend fun confirm(id: String, force: Boolean): DeskRequest = error("not used")
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
    }

    private val desk = FakeDesk()
    private fun viewModel() = AdminMenuViewModel(desk)

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
        vm.onEvent(AdminMenuEvent.ServicePriceChanged("$165"))

        assertEquals("9", vm.state.value.service.minutes)
        assertEquals("165", vm.state.value.service.price)
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
}

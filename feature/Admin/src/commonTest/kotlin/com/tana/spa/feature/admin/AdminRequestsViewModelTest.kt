package com.tana.spa.feature.admin

import com.tana.spa.feature.admin.domain.DeskQueue
import com.tana.spa.feature.admin.domain.DeskRefused
import com.tana.spa.feature.admin.domain.DeskRepository
import com.tana.spa.feature.admin.domain.DeskRequest
import com.tana.spa.feature.admin.domain.DeskStatus
import com.tana.spa.feature.admin.presentation.AdminRequestsEffect
import com.tana.spa.feature.admin.presentation.AdminRequestsEvent
import com.tana.spa.feature.admin.presentation.AdminRequestsViewModel
import com.tana.spa.feature.admin.presentation.AlternativeOffer
import com.tana.spa.shared.auth.CreateAccountRequest
import com.tana.spa.shared.auth.CreatedAccountDto
import com.tana.spa.shared.booking.BookingErrorCodes
import com.tana.spa.shared.booking.CreateServiceRequest
import com.tana.spa.shared.booking.CreateTherapistRequest
import com.tana.spa.shared.booking.DeclineReasonDto
import com.tana.spa.shared.booking.ServiceDto
import com.tana.spa.shared.booking.TherapistDto
import com.tana.spa.shared.spa.SpaDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AdminRequestsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private fun request(
        id: String,
        guest: String = "Hannah Pham",
        status: DeskStatus = DeskStatus.PENDING,
        waitedMinutes: Int = 5,
        startAtEpochMillis: Long = 2_000,
        conflict: String? = null,
    ) = DeskRequest(
        id = id,
        reference = "BK-$id",
        guest = guest,
        phone = "+14155550142",
        serviceName = "Warm Stone Massage",
        minutes = 90,
        therapistSlug = "mai",
        therapistName = "Mai Tran",
        startAtEpochMillis = startAtEpochMillis,
        startHour = 14.5,
        startDayEpochDay = SpaDate(2026, 10, 21).epochDay,
        status = status,
        waitedMinutes = waitedMinutes,
        total = 165,
        discount = 0,
        code = null,
        note = null,
        conflict = conflict,
        declineReason = null,
    )

    private class FakeDesk(var queue: DeskQueue) : DeskRepository {
        var fail = false
        var refusal: DeskRefused? = null
        var confirmed = mutableListOf<Pair<String, Boolean>>()
        var declined = mutableListOf<Triple<String, DeclineReasonDto, String?>>()
        var visits = mutableListOf<Pair<String, Boolean>>()

        override suspend fun queue(): DeskQueue {
            if (fail) throw IllegalStateException("offline")
            return queue
        }

        override suspend fun confirm(id: String, force: Boolean): DeskRequest {
            confirmed += id to force
            refusal?.let { throw it }
            return decided(id, DeskStatus.CONFIRMED)
        }

        override suspend fun decline(id: String, reason: DeclineReasonDto, alternative: String?): DeskRequest {
            declined += Triple(id, reason, alternative)
            refusal?.let { throw it }
            return decided(id, DeskStatus.DECLINED).copy(declineReason = reason)
        }

        override suspend fun recordVisit(id: String, attended: Boolean): DeskRequest {
            visits += id to attended
            refusal?.let { throw it }
            return decided(id, if (attended) DeskStatus.COMPLETED else DeskStatus.NO_SHOW)
        }

        override suspend fun createService(request: CreateServiceRequest): ServiceDto = error("the queue screen never adds to the menu")

        override suspend fun createTherapist(request: CreateTherapistRequest): TherapistDto =
            error("the queue screen never adds to the menu")

        override suspend fun createAccount(request: CreateAccountRequest): CreatedAccountDto =
            error("the queue screen never takes anybody on")

        private fun decided(id: String, status: DeskStatus): DeskRequest =
            (queue.pending + queue.scheduled).first { it.id == id }.copy(status = status)
    }

    private fun desk(vararg pending: DeskRequest, scheduled: List<DeskRequest> = emptyList()) =
        FakeDesk(DeskQueue(pending.toList(), scheduled, dayEpochDay = 20_376))

    private fun TestScope.viewModel(desk: FakeDesk): AdminRequestsViewModel =
        AdminRequestsViewModel(desk).also { testScheduler.advanceUntilIdle() }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun the_queue_comes_from_the_server_and_opens_on_the_first_one_waiting() = runTest(dispatcher) {
        val desk = desk(request("a"), request("b"), scheduled = listOf(request("c", status = DeskStatus.CONFIRMED)))

        val state = viewModel(desk).state.value

        assertEquals(listOf("a", "b", "c"), state.requests.map { it.id })
        assertEquals("a", state.selectedId)
        assertEquals(20_376, state.dayEpochDay)
        assertFalse(state.loading)
        assertFalse(state.failed)
    }

    @Test
    fun a_desk_that_cannot_be_read_offers_a_retry() = runTest(dispatcher) {
        val desk = desk(request("a"))
        desk.fail = true
        val vm = viewModel(desk)
        assertTrue(vm.state.value.failed)

        desk.fail = false
        vm.onEvent(AdminRequestsEvent.Refresh)
        testScheduler.advanceUntilIdle()

        assertFalse(vm.state.value.failed)
        assertEquals(listOf("a"), vm.state.value.requests.map { it.id })
    }

    @Test
    fun confirming_sends_the_decision_and_moves_on_to_the_next_one_waiting() = runTest(dispatcher) {
        val desk = desk(request("a"), request("b", guest = "Diego Alvarez"))
        val vm = viewModel(desk)

        vm.onEvent(AdminRequestsEvent.Confirmed("a"))
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("a" to false), desk.confirmed)
        assertEquals(DeskStatus.CONFIRMED, vm.state.value.requests.first { it.id == "a" }.status)
        assertEquals("b", vm.state.value.selectedId)
        assertEquals(AdminRequestsEffect.ShowConfirmed("Hannah Pham"), vm.effects.first())
    }

    @Test
    fun a_clash_is_confirmed_deliberately_and_its_refusal_is_explained() = runTest(dispatcher) {
        val desk = desk(request("a", conflict = "Overlaps Mai 2:30 PM"))
        val vm = viewModel(desk)
        desk.refusal = DeskRefused(409, BookingErrorCodes.OVERLAPS_ANOTHER)

        vm.onEvent(AdminRequestsEvent.Confirmed("a", force = true))
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("a" to true), desk.confirmed, "the clash is sent with force, as the owner pressed it")
        val effect = assertIs<AdminRequestsEffect.ShowRefused>(vm.effects.first())
        assertTrue(effect.overlap)
        assertEquals(DeskStatus.PENDING, vm.state.value.requests.first().status, "a refusal must not change the list")
    }

    @Test
    fun declining_carries_the_chosen_reason_and_alternative() = runTest(dispatcher) {
        val desk = desk(request("a"))
        val vm = viewModel(desk)

        vm.onEvent(AdminRequestsEvent.DeclineStarted("a"))
        vm.onEvent(AdminRequestsEvent.ReasonChosen(DeclineReasonDto.NO_ROOM))
        vm.onEvent(AdminRequestsEvent.AlternativeChosen(AlternativeOffer.TOMORROW_MORNING))
        vm.onEvent(AdminRequestsEvent.DeclineConfirmed)
        testScheduler.advanceUntilIdle()

        assertEquals(listOf(Triple("a", DeclineReasonDto.NO_ROOM, "TOMORROW_MORNING")), desk.declined.toList())
        assertEquals(DeskStatus.DECLINED, vm.state.value.requests.first().status)
        assertEquals(AdminRequestsEffect.ShowDeclined("Hannah Pham", withAlternative = true), vm.effects.first())
    }

    @Test
    fun a_decline_opened_on_a_clash_starts_on_the_overlap_reason() = runTest(dispatcher) {
        val vm = viewModel(desk(request("a", conflict = "Overlaps Mai 2:30 PM")))

        vm.onEvent(AdminRequestsEvent.DeclineStarted("a"))

        assertEquals(DeclineReasonDto.OVERLAP, vm.state.value.reason)
    }

    @Test
    fun each_decline_reason_reaches_the_server_as_itself() = runTest(dispatcher) {
        for (reason in DeclineReasonDto.entries) {
            val desk = desk(request("a"))
            val vm = viewModel(desk)

            vm.onEvent(AdminRequestsEvent.DeclineStarted("a"))
            vm.onEvent(AdminRequestsEvent.ReasonChosen(reason))
            vm.onEvent(AdminRequestsEvent.DeclineConfirmed)
            testScheduler.advanceUntilIdle()

            assertEquals(listOf(Triple("a", reason, null)), desk.declined.toList())
        }
    }

    @Test
    fun a_visit_is_recordable_only_once_the_appointment_has_started() = runTest(dispatcher) {
        val started = request("a", status = DeskStatus.CONFIRMED, startAtEpochMillis = 1_000)

        assertFalse(started.visitRecordable(nowEpochMillis = 999))
        assertTrue(started.visitRecordable(nowEpochMillis = 1_001))
        assertFalse(
            request("b", status = DeskStatus.PENDING, startAtEpochMillis = 1_000).visitRecordable(nowEpochMillis = 2_000),
            "a request nobody confirmed is not a visit",
        )
    }

    @Test
    fun recording_a_visit_sends_it_and_says_which_way_it_went() = runTest(dispatcher) {
        val desk = desk(scheduled = listOf(request("a", status = DeskStatus.CONFIRMED)))
        val vm = viewModel(desk)

        vm.onEvent(AdminRequestsEvent.VisitRecorded("a", attended = false))
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("a" to false), desk.visits)
        assertEquals(DeskStatus.NO_SHOW, vm.state.value.requests.first().status)
        assertEquals(AdminRequestsEffect.ShowVisitRecorded("Hannah Pham", attended = false), vm.effects.first())
    }

    @Test
    fun a_request_that_has_waited_too_long_is_flagged() = runTest(dispatcher) {
        val vm = viewModel(
            desk(
                request("fresh", waitedMinutes = 10),
                request("old", waitedMinutes = DeskRequest.STALE_AFTER_MINUTES + 1),
            ),
        )

        assertEquals(listOf("old"), vm.state.value.stale.map { it.id })
    }
}

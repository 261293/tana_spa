package com.tana.spa.feature.booking

import com.tana.spa.feature.booking.presentation.ServiceDetailEffect
import com.tana.spa.feature.booking.presentation.ServiceDetailEvent
import com.tana.spa.feature.booking.presentation.ServiceDetailViewModel
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServiceDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeSpaCatalogRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun the_treatment_is_loading_until_the_catalogue_answers() = runTest(dispatcher) {
        val vm = ServiceDetailViewModel(repository)

        vm.onEvent(ServiceDetailEvent.Opened(ServerCatalog.facial.id))
        assertTrue(vm.state.value.loading)
        assertNull(vm.state.value.service)

        testScheduler.advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.loading)
        assertEquals(ServerCatalog.facial, state.service)
        assertFalse(state.notFound)
    }

    @Test
    fun a_slug_the_catalogue_does_not_hold_reads_as_not_found() = runTest(dispatcher) {
        val vm = ServiceDetailViewModel(repository)

        vm.onEvent(ServiceDetailEvent.Opened("hot-coal-wrap"))
        testScheduler.advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state.notFound)
        assertNull(state.service)
        assertFalse(state.failed)
    }

    @Test
    fun a_route_without_its_argument_reads_as_not_found() = runTest(dispatcher) {
        val vm = ServiceDetailViewModel(repository)

        vm.onEvent(ServiceDetailEvent.Opened(null))
        testScheduler.advanceUntilIdle()

        assertTrue(vm.state.value.notFound)
        assertEquals(0, repository.calls)
    }

    @Test
    fun a_catalogue_that_cannot_be_read_is_a_failure_a_retry_can_fix() = runTest(dispatcher) {
        repository.fail = true
        val vm = ServiceDetailViewModel(repository)

        vm.onEvent(ServiceDetailEvent.Opened(ServerCatalog.stone.id))
        testScheduler.advanceUntilIdle()

        assertTrue(vm.state.value.failed)
        assertFalse(vm.state.value.notFound)

        repository.fail = false
        vm.onEvent(ServiceDetailEvent.Retry)
        testScheduler.advanceUntilIdle()

        assertFalse(vm.state.value.failed)
        assertEquals(ServerCatalog.stone, vm.state.value.service)
    }

    @Test
    fun booking_returns_to_the_flow() = runTest(dispatcher) {
        val vm = ServiceDetailViewModel(repository)
        vm.onEvent(ServiceDetailEvent.Opened(ServerCatalog.stone.id))
        testScheduler.advanceUntilIdle()

        vm.onEvent(ServiceDetailEvent.Book)

        assertEquals(ServiceDetailEffect.ReturnToBooking, vm.effects.first())
    }
}

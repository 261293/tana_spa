package com.tana.spa.feature.booking

import com.tana.spa.core.network.CallerRole
import com.tana.spa.feature.booking.domain.Product
import com.tana.spa.feature.booking.presentation.HomeEvent
import com.tana.spa.feature.booking.presentation.HomeViewModel
import com.tana.spa.shared.auth.SpaRoles
import com.tana.spa.shared.booking.BookingStatusDto
import com.tana.spa.shared.spa.SpaCatalog
import com.tana.spa.shared.spa.SpaDate
import kotlinx.coroutines.Dispatchers
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val catalog = FakeSpaCatalogRepository()
    private val bookings = FakeGuestBookingRepository()
    private val now = 10_000L
    private val serum = Product("serum", "Serum trà xanh", "Tana", "Dưỡng ẩm", null, 450_000, "30ml")

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(role: String = SpaRoles.CUSTOMER, today: SpaDate = SpaDate(2026, 11, 13)) =
        HomeViewModel(catalog, CallerRole { role }, bookings, today) { now }.also { testScheduler.advanceUntilIdle() }

    @Test
    fun home_shows_the_treatments_and_the_shelf() = runTest(dispatcher) {
        catalog.shelf = listOf(serum)
        val state = viewModel().state.value
        assertEquals(ServerCatalog.catalog.services, state.services)
        assertEquals(listOf(serum), state.products)
        assertFalse(state.loading)
    }

    @Test
    fun a_shelf_that_fails_to_load_still_leaves_the_treatments() = runTest(dispatcher) {
        catalog.shelfFails = true
        val state = viewModel().state.value
        assertFalse(state.failed)
        assertEquals(ServerCatalog.catalog.services.size, state.services.size)
        assertTrue(state.products.isEmpty())
    }

    @Test
    fun a_failed_menu_offers_a_retry_that_recovers() = runTest(dispatcher) {
        catalog.fail = true
        val vm = viewModel()
        assertTrue(vm.state.value.failed)
        catalog.fail = false
        vm.onEvent(HomeEvent.Retry)
        testScheduler.advanceUntilIdle()
        assertFalse(vm.state.value.failed)
        assertEquals(ServerCatalog.catalog.services, vm.state.value.services)
    }

    @Test
    fun staff_see_the_page_without_booking_and_everyone_else_books() = runTest(dispatcher) {
        assertFalse(viewModel(SpaRoles.STAFF).state.value.canBook)
        for (role in listOf(SpaRoles.CUSTOMER, SpaRoles.OWNER, SpaRoles.ADMIN)) {
            assertTrue(viewModel(role).state.value.canBook, role)
        }
    }

    @Test
    fun only_offers_still_running_are_counted() = runTest(dispatcher) {
        val today = SpaDate(2026, 11, 13)
        assertEquals(SpaCatalog.livePromos(today).size, viewModel(today = today).state.value.liveOffers)
    }

    @Test
    fun the_next_visit_is_the_earliest_pending_or_confirmed_ahead_of_now() = runTest(dispatcher) {
        bookings.mine = listOf(
            guestBooking(id = "later", status = BookingStatusDto.CONFIRMED, startAtEpochMillis = 90_000),
            guestBooking(id = "soon", status = BookingStatusDto.PENDING, startAtEpochMillis = 20_000),
        )
        assertEquals("soon", viewModel().state.value.nextVisit?.id)
    }

    @Test
    fun past_and_cancelled_visits_are_not_next() = runTest(dispatcher) {
        bookings.mine = listOf(
            guestBooking(id = "past", status = BookingStatusDto.CONFIRMED, startAtEpochMillis = 5_000),
            guestBooking(id = "off", status = BookingStatusDto.CANCELLED, startAtEpochMillis = 50_000),
        )
        assertNull(viewModel().state.value.nextVisit)
    }

    @Test
    fun a_failed_bookings_call_leaves_the_menu() = runTest(dispatcher) {
        bookings.failMine = true
        val state = viewModel().state.value
        assertNull(state.nextVisit)
        assertFalse(state.failed)
        assertEquals(ServerCatalog.catalog.services, state.services)
    }

    @Test
    fun staff_have_no_next_visit_and_are_never_asked() = runTest(dispatcher) {
        bookings.mine = listOf(guestBooking(startAtEpochMillis = 20_000))
        assertNull(viewModel(SpaRoles.STAFF).state.value.nextVisit)
        assertEquals(0, bookings.mineCalls)
    }
}

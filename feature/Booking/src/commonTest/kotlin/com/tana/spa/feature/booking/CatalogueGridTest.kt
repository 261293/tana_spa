package com.tana.spa.feature.booking

import com.tana.spa.core.ui.SpaWindowSize
import com.tana.spa.feature.booking.presentation.ui.catalogueColumns
import com.tana.spa.feature.booking.presentation.ui.staggeredColumns
import kotlin.test.Test
import kotlin.test.assertEquals

class CatalogueGridTest {

    @Test
    fun one_column_keeps_the_menu_in_the_order_the_spa_listed_it() {
        assertEquals(listOf(listOf(1, 2, 3, 4, 5)), staggeredColumns(listOf(1, 2, 3, 4, 5), columns = 1))
    }

    @Test
    fun the_treatments_are_dealt_across_the_columns_in_turn() {
        assertEquals(listOf(listOf(1, 3, 5), listOf(2, 4)), staggeredColumns(listOf(1, 2, 3, 4, 5), columns = 2))
    }

    @Test
    fun a_short_menu_leaves_the_last_columns_empty_rather_than_crowding_the_first() {
        assertEquals(listOf(listOf(1), listOf(2), emptyList()), staggeredColumns(listOf(1, 2), columns = 3))
    }

    @Test
    fun a_nonsense_column_count_still_lays_the_menu_out() {
        assertEquals(listOf(listOf(1, 2)), staggeredColumns(listOf(1, 2), columns = 0))
    }

    @Test
    fun a_phone_gets_one_column_and_a_desk_gets_the_grid() {
        assertEquals(1, catalogueColumns(SpaWindowSize.COMPACT))
        assertEquals(1, catalogueColumns(SpaWindowSize.MEDIUM))
        assertEquals(2, catalogueColumns(SpaWindowSize.EXPANDED))
        assertEquals(3, catalogueColumns(SpaWindowSize.LARGE))
    }
}

package com.tana.spa.shared.booking

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpaValidationTest {

    private fun service(name: String = "Warm Stone Massage", description: String = "Basalt stones", minutes: Int = 90, price: Int = 165) =
        CreateServiceRequest(name, description, minutes, price, ServiceCategoryDto.MASSAGE)

    @Test
    fun a_complete_treatment_passes() {
        assertNull(SpaValidation.firstInvalidField(service()))
    }

    @Test
    fun each_field_is_named_in_the_order_the_form_shows_them() {
        assertEquals(SpaValidation.FIELD_NAME, SpaValidation.firstInvalidField(service(name = "  ")))
        assertEquals(SpaValidation.FIELD_DESCRIPTION, SpaValidation.firstInvalidField(service(description = "")))
        assertEquals(SpaValidation.FIELD_MINUTES, SpaValidation.firstInvalidField(service(minutes = 0)))
        assertEquals(SpaValidation.FIELD_PRICE, SpaValidation.firstInvalidField(service(price = -1)))
    }

    @Test
    fun a_duration_has_to_land_on_the_diary_grid() {
        assertEquals(DurationError.NOT_A_MULTIPLE, SpaValidation.validateMinutes(50))
        assertEquals(DurationError.TOO_LONG, SpaValidation.validateMinutes(600))
        assertNull(SpaValidation.validateMinutes(75))
    }

    @Test
    fun a_treatment_may_be_free_but_not_negative() {
        assertNull(SpaValidation.validatePrice(0))
        assertEquals(PriceError.NEGATIVE, SpaValidation.validatePrice(-1))
        assertEquals(PriceError.TOO_LARGE, SpaValidation.validatePrice(SpaValidation.MAX_PRICE + 1))
    }

    @Test
    fun a_therapist_needs_a_name_and_a_focus() {
        assertEquals(SpaValidation.FIELD_NAME, SpaValidation.firstInvalidField(CreateTherapistRequest("", "Facials")))
        assertEquals(SpaValidation.FIELD_FOCUS, SpaValidation.firstInvalidField(CreateTherapistRequest("Mai", " ")))
        assertNull(SpaValidation.firstInvalidField(CreateTherapistRequest("Mai Tran", "Stone & deep tissue")))
    }

    @Test
    fun slugs_keep_only_what_a_url_can_carry() {
        assertEquals("warm-stone-massage", SpaValidation.slugify("Warm Stone Massage"))
        assertEquals("stone-deep-tissue", SpaValidation.slugify("  Stone & deep tissue  "))
        assertEquals("cham-soc-da", SpaValidation.slugify("Cham soc da!!"))
    }
}

package com.tana.spa.core.ui

import com.tana.spa.core.ui.resources.Res
import com.tana.spa.core.ui.resources.photo_deep_tissue_massage
import com.tana.spa.core.ui.resources.photo_herbal_compress_ritual
import com.tana.spa.core.ui.resources.photo_hero
import com.tana.spa.core.ui.resources.photo_jade_roller_facial
import com.tana.spa.core.ui.resources.photo_matcha_body_polish
import com.tana.spa.core.ui.resources.photo_warm_stone_massage
import org.jetbrains.compose.resources.DrawableResource

/**
 * Stock photography bundled from the design export, under the Pexels licence (commercial use, no
 * attribution required). They illustrate the kind of treatment, not this spa's own rooms or staff,
 * and are meant to be replaced by the spa's own photographs before launch.
 */
object SpaPhotos {
    val hero: DrawableResource = Res.drawable.photo_hero

    private val byTreatmentSlug = mapOf(
        "warm-stone-massage" to Res.drawable.photo_warm_stone_massage,
        "jade-roller-facial" to Res.drawable.photo_jade_roller_facial,
        "deep-tissue-massage" to Res.drawable.photo_deep_tissue_massage,
        "matcha-body-polish" to Res.drawable.photo_matcha_body_polish,
        "herbal-compress-ritual" to Res.drawable.photo_herbal_compress_ritual,
    )

    fun treatment(slug: String?): DrawableResource? = byTreatmentSlug[slug]

    fun category(category: String?): DrawableResource? = when (category?.uppercase()) {
        "MASSAGE" -> Res.drawable.photo_warm_stone_massage
        "FACIAL" -> Res.drawable.photo_jade_roller_facial
        "BODY" -> Res.drawable.photo_matcha_body_polish
        else -> null
    }
}

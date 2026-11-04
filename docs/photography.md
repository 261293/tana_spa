# Photography

The app ships six photographs in `core/ui/src/commonMain/composeResources/drawable/`, bundled from
the design export (`Spa-Booking-UI-Redesign/assets/stock`). They are shown through `SpaImageTile`'s
`photo` parameter and mapped to treatments by slug in `SpaPhotos`.

## These are stand-ins

Every one is a stock photograph of *the kind of treatment*, not of this spa. They are here so the
layout is seen at its real density instead of as empty tonal panels, and they are meant to be
replaced by the spa's own photographs before launch. None of them may be presented as a picture of
this spa's rooms, staff or guests.

**Therapists deliberately have no photographs.** The design calls for portraits, and the export
ships five, but they are pictures of models. Putting a stranger's face beside a named therapist
presents a stock person as the spa's employee, so therapists keep their initials until the spa
supplies real staff portraits.

## Licence and credit

All six are under the [Pexels licence](https://www.pexels.com/license/): free for commercial use,
attribution not required. Credits are kept here rather than on screen.

| File | Photographer | Source |
| --- | --- | --- |
| `photo_hero.jpg` | RDNE Stock project | https://www.pexels.com/photo/a-person-wearing-brown-brassiere-spa-bathing-6724531/ |
| `photo_warm_stone_massage.jpg` | kaboompics.com | https://www.pexels.com/photo/woman-having-a-massage-with-massage-stones-6629521/ |
| `photo_jade_roller_facial.jpg` | DΛVΞ GΛRCIΛ | https://www.pexels.com/photo/relaxing-facial-treatment-in-spa-setting-37229302/ |
| `photo_deep_tissue_massage.jpg` | Tima Miroshnichenko | https://www.pexels.com/photo/a-masseuse-massaging-a-woman-6187418/ |
| `photo_matcha_body_polish.jpg` | Yan Krukau | https://www.pexels.com/photo/woman-applying-scrub-on-her-hands-7020264/ |
| `photo_herbal_compress_ritual.jpg` | Tara Winstead | https://www.pexels.com/photo/close-up-shot-of-essential-oil-bottles-and-herbs-on-white-surface-6694177/ |

## Adding a treatment

A treatment without an entry in `SpaPhotos.byTreatmentSlug` falls back to the tonal panel, which is
correct — it is better than showing someone else's treatment. Add the picture and the slug together.

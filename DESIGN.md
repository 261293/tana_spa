# Tana Spa design system

The locked system every screen shares (ADR 0009 for colour, ADR 0008 for type and shape). Colours,
faces and shapes reach a screen only through `MaterialTheme` and `SpaTheme`; the Kotlin source of
truth is `core/ui` (`SpaColors`, `AppTheme`, `SpaTextStyles`). Material 3 components first:
`docs/material-design.md`.

## Colour

The Đá bazan palette (2026-11-14): a quiet working app in the manner of the owner's reference, an
off-white ground with white panels, near-black actions and one jade for what is selected.

| Token | sRGB | Role |
| --- | --- | --- |
| Ground | `#F4F4F1` | `background`, `surfaceContainerLow` |
| Panel | `#FFFFFF` | `surface`, `surfaceContainerLowest`: panels, rows, fields |
| Field | `#ECEDE9` | `surfaceVariant`, `surfaceContainer`, `tertiaryContainer`: one tone step |
| FieldHigh | `#E2E4DF` | `surfaceContainerHigh` |
| FieldHighest | `#D8DBD5` | `surfaceContainerHighest` |
| Ink | `#151917` | `onBackground`, `onSurface`, `inverseSurface` (the toast) |
| InkSoft | `#2B312E` | body copy |
| Muted | `#55605B` | `onSurfaceVariant`, `tertiary` (5.9:1 on the ground) |
| Rule | `#DCDEDA` | `outlineVariant`: panel edges and row rules |
| Outline | `#737C77` | `outline`: control boundaries (3.9:1 on the ground) |
| Basalt | `#1D2421` | `primary`: the one filled action per view (white on it 15.8:1) |
| Jade | `#2F6F5E` | `secondary`, links: selection, focus, confirmed (5.4:1 on the ground) |
| JadeTint / JadeDeep | `#DCEBE5` / `#12392F` | `primaryContainer`, `secondaryContainer` and their `on` (10.3:1) |
| Ochre / OchreTint | `#8D5D1C` / `#F6E8D0` | warning, pending |
| Moss | `#3A6343` | success |
| Danger / DangerTint / DangerDeep | `#963828` / `#FBE1DC` / `#6B2013` | error roles |

Colour sits on the action, the selection and status, nowhere else: prices, titles, icons and
navigation are ink and grey. Surfaces are separated by the 1px rule or one tone step, never a
shadow; only what floats (a menu, a dialog, the toast) casts one. Light only (ADR 0002).
`SpaColorSchemeTest` pins every pair above to WCAG 2.2.

## Type

- **Fraunces** medium: screen titles, the brand name and the one large title on Home and the sign-in
  screens; regular: prices and the names in a facts sheet. Sentence case, roman. Never running text.
- **Be Vietnam Pro** 400/500/600: body (16sp), labels, controls, data. Untracked. Drawn for
  Vietnamese, so stacked marks (`ể`, `ẫ`, `ở`) keep their spacing at small sizes.
- Labels (`SpaTag`) are a step smaller, sentence case, never uppercase or letter-spaced.

## Shape

Material's scale, cut smaller: 4 / 8 / 12 / 16 / 24 dp (extraSmall to extraLarge). Panels use
12dp, buttons are fully round.

## Never

An italic or coloured word inside a heading; an eyebrow above a heading; a label in tracked
capitals; a decorative texture or gradient; a shadow on a resting surface; an emoji as an icon; an
invented figure, card, quote or logo.

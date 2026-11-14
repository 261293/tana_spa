# Tana Spa design system

The locked system every screen shares (ADR 0008). Colours, faces and shapes reach a screen only
through `MaterialTheme` and `SpaTheme`; the Kotlin source of truth is `core/ui`
(`SpaColors`, `AppTheme`, `SpaTextStyles`). Material 3 components first: `docs/material-design.md`.

## Colour

The mineral-spring palette (amended 2026-11-13, after the owner's reference: a hot-springs spa
site with a cool mist ground, sage fields between sections and copper actions).

| Token | sRGB | Role |
| --- | --- | --- |
| Paper | `#F2F5F4` | `background`, `surfaceContainerLow`: the mist ground |
| Paper2 | `#E3EAE8` | `surfaceVariant`, `surfaceContainer`: the sage field a section may sit on |
| Paper3 | `#D5DFDC` | `surfaceContainerHigh` |
| Card | `#FFFFFF` | `surface`, `surfaceContainerLowest`: cards and fields |
| Ink | `#16282B` | `onBackground`, `onSurface`, `inverseSurface`: slate ink |
| InkSoft | `#2C3E41` | body copy on mist |
| Muted | `#4A5A5D` | `onSurfaceVariant`, tags |
| Rule | `#C9D3D1` | `outlineVariant` |
| Outline | `#6F7F82` | `outline`, control boundaries (3:1 on card and mist) |
| Copper | `#9A521F` | `primary`: the primary action, selection, links, focus (white on it 5.8:1; on mist 5.3:1; on the sage field 4.8:1) |
| CopperTint / CopperDeep | `#F7E4D2` / `#3D1F08` | `primaryContainer` / `onPrimaryContainer` |
| CopperGlow | `#E9A66C` | the accent words of a headline set over a darkened photograph (`SpaTheme.extraColors.onPhotoAccent`) |
| Eucalyptus / EucalyptusTint / EucalyptusDeep | `#2E5B55` / `#D4E5DF` / `#0F3A35` | `secondary` roles |
| Slate / SlateTint | `#3C4F52` / `#CCD7D4` | `tertiary` roles, `surfaceContainerHighest` |
| Ochre / OchreTint | `#8D5D1C` / `#F6E8D0` | warning |
| Moss | `#3A6343` | success |
| Danger / DangerTint / DangerDeep | `#963828` / `#FBE1DC` / `#6B2013` | error roles |

Copper is the one warm signal: the action, the selection, a price, the rule under a price group.
A field of sage separates sections, with a slanted top edge; nothing is separated by a shadow.
Light only (ADR 0002). `SpaColorSchemeTest` pins every pair above to WCAG 2.2.

## Type

- **Fraunces** medium: display and screen titles and the brand name; regular: card titles and
  prices. Sentence case, roman. Never running text.
- **Be Vietnam Pro** 400/500/600: body (16sp), labels, controls, data. Untracked. Drawn for
  Vietnamese, so stacked marks (`ể`, `ẫ`, `ở`) keep their spacing at small sizes.
- Labels (`SpaTag`) are a step smaller, sentence case, never uppercase or letter-spaced.

## Shape

Material's scale, cut smaller: 4 / 8 / 12 / 16 / 24 dp (extraSmall to extraLarge).

## Never

An italic word inside a roman heading; an eyebrow above a heading; a label in tracked capitals;
a decorative texture or gradient; an emoji as an icon; an invented figure, card, quote or logo.

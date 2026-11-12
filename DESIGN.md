# Tana Spa design system

The locked system every screen shares (ADR 0008). Colours, faces and shapes reach a screen only
through `MaterialTheme` and `SpaTheme`; the Kotlin source of truth is `core/ui`
(`SpaColors`, `AppTheme`, `SpaTextStyles`). Material 3 components first: `docs/material-design.md`.

## Colour

| Token | OKLCH | sRGB | Role |
| --- | --- | --- | --- |
| Paper | 94.5% 0.010 76 | `#F1ECE6` | `background`, `surfaceContainerLow` |
| Paper2 | 91.8% 0.013 74 | `#E9E3DB` | `surfaceVariant`, `surfaceContainer` |
| Paper3 | 88% 0.015 72 | `#DED6CD` | `surfaceContainerHigh` |
| Card | 97.6% 0.007 82 | `#F9F7F2` | `surface`, `surfaceContainerLowest` |
| Ink | 19% 0.024 52 | `#1C110A` | `onBackground`, `onSurface`, `inverseSurface` |
| InkSoft | 31% 0.018 54 | `#382E28` | body copy on paper |
| Muted | 47% 0.014 60 | `#615953` | `onSurfaceVariant`, tags |
| Rule | 81% 0.011 70 | `#C6C0B9` | `outlineVariant`, hairlines |
| Outline | 58% 0.014 64 | `#807972` | `outline`, control boundaries |
| Slate | 41% 0.075 248 | `#264D71` | `primary`: the primary action, selection, links |
| SlateTint | 90.5% 0.025 248 | `#D3E2F0` | `primaryContainer` |
| SlateDeep | 30% 0.065 248 | `#0E304D` | `onPrimaryContainer` |
| Focus | 50% 0.095 248 | `#326797` | focus rings |
| Umber | 40% 0.035 58 | `#564335` | `secondary`, `tertiary` |
| UmberTint | 90% 0.018 70 | `#E6DCD2` | `secondaryContainer`, `surfaceContainerHighest` |
| Ochre / OchreTint | 52% 0.10 70 / 93.5% 0.035 80 | `#8D5D1C` / `#F6E8D0` | warning |
| Moss | 46% 0.07 150 | `#3A6343` | success |
| Danger / DangerTint / DangerDeep | 47% 0.13 32 / 93% 0.03 32 / 36% 0.11 32 | `#963828` / `#FBE1DC` / `#6B2013` | error roles |

Slate is the only chromatic signal: never a second accent, never a warm one, and never a
decorative fill. Light only (ADR 0002).

## Type

- **Newsreader** light: display and screen titles, sentence case, roman, one weight a heading.
  Regular: card titles, prices, the brand name. Never running text.
- **Hanken Grotesk** 400/500/600: body (16sp), labels, controls, data. Untracked.
- Labels (`SpaTag`) are a step smaller, sentence case, never uppercase or letter-spaced.

## Shape

Material's scale, cut smaller: 4 / 8 / 12 / 16 / 24 dp (extraSmall to extraLarge).

## Never

An italic word inside a roman heading; an eyebrow above a heading; a label in tracked capitals;
a decorative texture or gradient; an emoji as an icon; an invented figure, card, quote or logo.

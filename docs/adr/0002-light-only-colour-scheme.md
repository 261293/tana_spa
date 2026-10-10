# 2. One light colour scheme, read only through the theme

Date: 2026-10-11

## Status

Accepted

## Context

The palette in `SpaColors` is taken from one Figma file ("SPA BOOKING APP"), which specifies light
values only. There is no approved dark palette, and no designer has been asked for one.

Two separate problems were being conflated. The first is mechanical: screens were reading
`SpaColors` constants and raw `Color(0x…)` literals directly, so the Material scheme the theme
builds had no consumers — 57 direct token references against one `colorScheme` reference. A theme
change could not reach a single screen, which makes *any* second scheme impossible regardless of
what colours it holds. The second is a design question: what the app should look like in dark mode.

The pull to answer both at once is strong, because the UI rules this repo now follows say both
schemes ship together. But deriving a dark palette from a light brand hue means inventing colours
and shipping them to everyone whose device is in dark mode. Today those users get the light theme,
which reads as a deliberate choice; invented colours would read as a bug.

## Decision

**Screens read colour from `MaterialTheme.colorScheme` or `SpaTheme.extraColors`, never from
`SpaColors` or a literal.** `SpaColors` is the raw palette and stays internal to the theme's
construction. `SpaExtraColors` carries the design-file colours Material 3 has no role for: the
link pink, the status green, the two gradients and the card shadow.

**The app ships one light scheme, and that is recorded as a decision rather than left as an
unmet rule.** `AppTheme` does not call `isSystemInDarkTheme()`.

**`SpaColorSchemeTest` pins every scheme role to the token it was mapped from**, so the migration
is verifiable as colour-preserving and a later scheme edit cannot silently repaint a screen that
no longer names the token.

The same split applies to type: `SpaTextStyles` names the Figma text styles, and each token is
derived from the base its call sites used to inherit (`bodyLarge`, `labelLarge`, or nothing for the
bare field styles) so letterSpacing and lineHeight do not move.

## Consequences

Adding dark mode is now a change to constants plus one `isSystemInDarkTheme()` branch, not a
refactor: the mechanism — a scheme built from named tokens, plus a `CompositionLocal` for the
colours Material cannot express — is in place and has no remaining bypasses. When a dark palette
is approved, `SpaColorSchemeTest` will need a dark counterpart, since its assertions currently
describe the light mapping.

Until then, dark-mode devices render the light theme. That is a conscious trade, not an oversight,
and `references/ui-design.md` records it next to the rule it departs from.

Figma-derived sizes are deliberately **not** normalised to a rounded spacing scale. This UI is a
pixel-faithful implementation of a design file, so rounding 13.sp or 46.dp would regress fidelity
to satisfy a rule that exists to stop arbitrary values, not specified ones.

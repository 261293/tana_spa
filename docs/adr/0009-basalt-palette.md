# 9. The Đá bazan palette replaces mineral spring

Date: 2026-11-14

## Status

Accepted. Supersedes the palette of ADR 0008 (and its 2026-11-13 amendment). The typefaces, the
shape scale and Material's own text field from ADR 0008 stand, as do ADR 0002 (light only) and
ADR 0005 (motion).

## Context

A screenshot audit of the running app on 2026-11-14 found copper on every button, chip, switch,
price, link and rule, so the one warm signal no longer marked the action; a landing-page Home on a
working app; and a booking bar whose total disagreed with the card's price without saying why. The
owner asked for a redesign with a new palette and pointed at Setting Spec Studio as the reference
for how a working screen should feel: an off-white ground, white panels with a hairline edge,
segmented controls and full-width option rows. Three palettes were shown (Đá bazan, Ngọc bích,
Sương sông); the owner chose Đá bazan.

## Decision

- **Ground and panels:** a neutral warm ground `#F4F4F1`, white panels with a 1px `#DCDEDA` edge,
  one tone step (`#ECEDE9`) for a field. No shadow on anything at rest.
- **Ink:** `#151917`, grey `#55605B` for secondary text.
- **The action:** near-black basalt `#1D2421` (`primary`), one filled button per view.
- **The selection:** jade `#2F6F5E` (`secondary`) for the current selection, focus, links and the
  confirmed status; its tint `#DCEBE5` with `#12392F` text for a selected row or chip
  (`primaryContainer` and `secondaryContainer`).
- **Prices** are set in ink, not in a hue.
- **Status:** ochre for pending and warnings, moss for success, the existing danger reds.
- **Retired:** copper, the copper glow on photographs, the sage field with its slanted edge.

Values and contrast pairs are in `DESIGN.md`; `SpaColorSchemeTest` pins each role and every text
pairing at WCAG 2.2 AA.

## Consequences

- Every screen changes colour in one step through the theme; layouts are then recomposed screen by
  screen (`docs/superpowers/specs/2026-11-14-basalt-redesign-design.md`).
- `primaryContainer` is jade rather than a tint of `primary`: Material's selected states read as
  the jade selection, while filled buttons stay basalt.
- The web shell's ground, spinner and favicon follow the new tokens.

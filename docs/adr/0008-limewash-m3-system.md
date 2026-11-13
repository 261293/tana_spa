# 8. A limewash Material 3 system replaces Moss & Linen

Date: 2026-11-12

## Status

Accepted. Supersedes the Serein export (`Spa-Booking-UI-Redesign`) as the visual contract. ADR 0002
(light only, read only through the theme) and ADR 0005 (motion) still hold.

## Context

An audit of every customer screen on 2026-11-12 (phone and desk widths, the running web build)
found the Moss & Linen look carrying devices that read as generated rather than designed: a clay
italic word closing every headline, a tracked-capitals eyebrow above every heading and every card,
a contour-map texture behind panels, and an organic "pebble" corner. The owner asked for a new
system that follows Material 3 more closely instead of keeping the Serein export as the contract.

Two constraints shaped the choice. The faces must cover Vietnamese, the app's default language:
Instrument Sans has no Vietnamese subset on Google Fonts, so its accented letters came from a
fallback face. And the change has to land through the theme, because screens already read every
colour and text style from `MaterialTheme` and `SpaTheme` (ADR 0002).

## Decision

- **Palette:** warm limewash paper and card stock, umber ink, and one eucalyptus signal (`primary`, slate until 2026-11-12)
  reserved for the primary action, the current selection and focus. Every role maps to a token in
  `SpaColors`; the values and their OKLCH sources are listed in `DESIGN.md`.
- **Type:** Newsreader (light for display and screen titles, regular for card titles) over Hanken
  Grotesk for everything else, untracked. Both bundle the Vietnamese letters and `₫`
  (`core/ui/licenses/FONTS.md`).
- **Shape:** Material's scale cut smaller, 4/8/12/16/24 dp.
- **Removed:** the italic accent span in `SpaDisplayTitle`, page-head eyebrows (`SpaScreenHeader`
  no longer takes one), uppercase transforms on labels (`SpaEyebrow` became `SpaTag`), the
  contour texture (`spaTopo`, `bg_topo.xml`) and `SpaPebbleShape`.
- **Kept for now:** the felt tray and the cork bevel are retoned to the new palette. The dark
  pocket went with the layouts that used it (2026-11-12): the booking bar became a light
  appointment card, and the visit strip, the desk's decision and the closing panel sit on
  `primaryContainer`.

## Consequences

- Every screen changes colour, face and corner radius in one step, with no layout change.
  `SpaColorSchemeTest` and `SpaTextStylesTest` pin the new tokens; the contrast assertions cover
  every text pairing the scheme produces.
- The Serein export stops being the reference for colour and type. Its screen list never mapped
  to this app's screens anyway.
- `SpaDisplayTitle(lead, accent)` keeps both parameters so call sites compile; the accent is now
  set in the same style as the lead. Screens drop the split as they are redesigned.

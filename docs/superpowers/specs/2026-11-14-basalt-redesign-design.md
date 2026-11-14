# Basalt redesign: a quiet working app on a new palette

Date: 2026-11-14. Status: draft, waiting for the owner's approval.

## Intent

The owner asked for a redesign of the whole app with the taste skill, and chose a new palette
(direction B) over recomposing the mineral-spring one. A screenshot audit of the running desktop
app (1280dp, local stack) found:

- Home is a landing page (photo hero, centred section titles, slanted sage field) that shows the
  same five treatments three times and never answers a returning guest's first question: when is my
  next visit, and how do I book again.
- Booking step 1 shows 4.125.000đ on the card and 3.712.500đ in the bar with no reason given.
- Copper sits on every button, chip, switch, price, link and rule, so it no longer marks the action.
- Titles are landing-sized on working screens; Home sets 60sp inline, against the theme rule.
- Empty screens are thin (Lịch hẹn is one sentence in 1280dp of blank).
- The app-bar destination has two active marks at once (a grey pill and an underline).

The owner also pointed at Setting Spec Studio (setting-uiux.vercel.app) as a style and UX reference:
an off-white ground, white panels with a 1px edge, segmented controls, full-width option rows,
an overview set as labels over values, and a dark rounded toast.

## Goals

- One new palette, **Đá bazan**, through `SpaColors` → `MaterialTheme`/`SpaTheme`, with a new ADR
  (0009) superseding the palette part of ADR 0008, and `DESIGN.md` rewritten.
- Colour on the action, the selection and status only; everything else ink and grey.
- Every screen recomposed to the app-screen rules: a noun title at screen size, white panels on the
  ground, rows instead of card grids, honest empty, loading and error states inside their block.
- Home answers the returning guest's question first.

## Non-goals

- No change to typefaces (Fraunces + Be Vietnam Pro) or the shape scale (4/8/12/16/24).
- No change to flows, navigation destinations, the backend or copy meaning (wording may tighten).
  ViewModels change only where a screen needs data its feature already loads: Home reads the
  guest's bookings through `GuestBookingRepository.mine()`, which lives in the same feature. The placeholder screens (Feed, Explore, Likes, NewPost, Profile) are left alone.
- No dark theme (ADR 0002 stands).

## Anti-goals

- A tidy template with no identity: the treatment photographs and the Fraunces prices stay.
- Jade spreading onto chrome (nav pills, every chip, every switch).
- A landing-page hero on any working screen.

## The palette (Đá bazan)

| Token | sRGB | Role |
| --- | --- | --- |
| Ground | `#F4F4F1` | `background`, `surfaceContainerLow` |
| Panel | `#FFFFFF` | `surface`, `surfaceContainerLowest` |
| Field | `#ECEDE9` | `surfaceVariant`, `surfaceContainer`: a step in tone |
| FieldHigh / Highest | `#E2E4DF` / `#D8DBD5` | `surfaceContainerHigh` / `Highest` |
| Ink | `#151917` | `onBackground`, `onSurface`, `inverseSurface` (the toast) |
| InkSoft | `#2B312E` | body copy |
| Muted | `#55605B` | `onSurfaceVariant` (5.9:1 on the ground) |
| Rule | `#DCDEDA` | `outlineVariant`: panel edges and row rules (1.2:1, the reference's hairline) |
| Outline | `#737C77` | `outline`: control boundaries (3.9:1 on the ground) |
| Basalt | `#1D2421` | `primary`: the one filled action per screen (white on it 15.8:1) |
| Jade | `#2F6F5E` | `secondary`: selection, focus, links, the confirmed status (5.4:1 on the ground) |
| JadeTint / JadeDeep | `#DCEBE5` / `#12392F` | `primaryContainer`/`secondaryContainer` and their `on` (10.3:1): a selected row or chip |
| Ochre / OchreTint | `#8D5D1C` / `#F6E8D0` | warning, pending (4.7:1) |
| Moss | `#3A6343` | success |
| Danger / DangerTint / DangerDeep | `#963828` / `#FBE1DC` / `#6B2013` | error roles |

Prices are set in ink (Fraunces), not in a hue. Copper and the sage field are retired.
`CopperGlow` and the styled accent word on the photo go.

## Layout per width class

Unchanged frame: bottom bar (compact), rail (medium), destinations in the app bar (expanded and up),
every body in a `SpaPage`. Changes:

- Active destination: one mark only, ink label with the jade underline; no pill.
- Screen title: Fraunces medium, about 28sp on wide and 24sp on compact (`screenTitle`), with one
  plain sentence under it. `displayTitle` survives only for the auth screens and the Home welcome.
- Panels: white, 12dp radius, 1px `Rule` edge, no shadow; dense lists are rows divided by `Rule`.
- Choices: full-width selectable rows (the reference's "Blank · start from nothing"); a small set
  of exclusive options is a `SingleChoiceSegmentedButtonRow`.
- Toast: the Material snackbar on `inverseSurface`, 12dp corners.

## Screens and what changes

- **Login / Sign-up / Verify phone:** keep the photo half on wide; drop the logo chip on the photo
  (the mark moves to the form column); one panel holds the form.
- **Home (guest):** title row with the guest's name; first block is the next appointment (date
  block, treatment, time, status, "Xem chi tiết") or, with none, quick book; then one treatment menu
  as rows (photo thumb, name, minutes, price right-aligned) grouped by category, replacing both the
  card grid and the price list; then products, then live offers as one row. The photo becomes a
  narrow band beside the title on wide, gone on compact.
- **Home (owner/staff):** the same frame without booking actions; the desk's queue stays on the desk
  screen, because Home (feature Booking) cannot read the Admin feature's data.
- **Booking (3 steps + review):** steps as a compact segmented header; step 1 is search + category
  chips + a list of selectable rows; the discount in the appointment bar comes from a
  promo code already applied (`PromoCheck.Applied`), so the bar names the code and the amount
  (for example "Mã FIRSTVISIT: −412.500đ") beside the list price, and both prices agree; step 2 day chips and time rows; review as
  a labels-over-values sheet.
- **Service detail:** photo, then a facts sheet (minutes, price, includes) and the one action.
- **Lịch hẹn:** upcoming and past as row groups with a status pill; empty state keeps the frame and
  offers the three most-booked treatments as rows.
- **Ưu đãi:** each code a row with its terms and a copy action; expired codes dimmed in their own group.
- **Thông báo:** rows, unread marked by a jade dot and ink weight, not a tinted row.
- **Cài đặt:** grouped rows; switches neutral (ink track); the language a segmented control.
- **Desk (owner/staff requests), menu, codes:** panels and rows, colour only on status pills and
  the one primary action per view; the codes table keeps right-aligned tabular figures.
- **Assistant (welcome, consent, chat) and the update modal:** retoned only.

## Approaches considered

1. **Recompose on the existing palette (A).** Least risk, no ADR; rejected by the owner, who wants
   a new palette.
2. **New palette, same faces and shapes, recomposed screens (B, chosen).** One theme change
   recolours everything; screens then get layout work one by one.
3. **New palette and new faces.** Not asked for; the faces were chosen on 2026-11-13.

Within B, three palettes were shown (Đá bazan, Ngọc bích, Sương sông); the owner chose Đá bazan.

## Constraints

- Colours and text styles only through `MaterialTheme`/`SpaTheme`; inline `fontSize` and raw
  colours removed where touched (Home's 60sp hero first).
- `SpaColorSchemeTest`/`SpaTextStylesTest` pin the new tokens and every text pairing ≥ 4.5:1.
- The taste skill's `lint.mjs`/`inspect.mjs` cannot measure a Compose canvas; the check is its
  critique run on screenshots of the desktop app at 1280dp and at phone width.
- Gate: `./gradlew ktlintCheck jvmTest :composeApp:compileKotlinWasmJs`. No code comments, no AI
  attribution, Conventional Commits on `redesign/taste-app-screens`.

## Delivery

One branch, `redesign/taste-app-screens`, one commit for the palette (ADR, `DESIGN.md`, tokens,
tests, web shell colours) and then one commit per screen group, opened as one PR.

## Left to the builder

Whether a done step in the booking header can be tapped to go back (only if the existing events
already allow it), exact dp spacing inside rows, which Material component carries each row, the wording of new
empty-state lines (Vietnamese and English resources), and the order of screens after the theme.

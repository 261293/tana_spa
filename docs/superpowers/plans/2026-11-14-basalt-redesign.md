# Basalt redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Recolour the app to the Đá bazan palette and recompose every working screen to the app-screen rules.

**Architecture:** One theme change in `core:ui` (`SpaColors` → `SpaColorScheme`/`LightExtraColors`) repaints every screen; screens then get layout work one group at a time, reading only `MaterialTheme`/`SpaTheme`. Home gains the guest's next visit from `GuestBookingRepository.mine()`.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Material 3 (`material3 1.12.0-alpha03`), Koin.

**Spec:** `docs/superpowers/specs/2026-11-14-basalt-redesign-design.md`

## Global Constraints

- Palette values exactly as the spec's table; Fraunces + Be Vietnam Pro and the 4/8/12/16/24 shapes unchanged.
- No `Color(0x…)`, `SpaColors.*` or inline `fontSize`/`sp` in a composable outside `core/ui` theme files.
- One filled (`primary`) button per view; jade (`secondary`) only on selection, focus, links, confirmed status.
- No code comments; strings in `composeResources/values` and `values-en`.
- Gate: `./gradlew ktlintCheck jvmTest :composeApp:compileKotlinWasmJs`; screenshots of the desktop app at 1280dp and compact width per screen group.
- Conventional Commits on `redesign/taste-app-screens`, no AI attribution.

## Review Focus

- A guest with only cancelled or past bookings: Home must show quick book, not a stale "next visit".
- Two upcoming bookings: Home shows the earliest by start time.
- `mine()` failing: Home still shows the menu (next-visit block falls back to quick book), no error banner.
- Staff (`canBook = false`): Home shows no next-visit or quick-book block.
- A promo removed after being applied: the booking bar shows only the list price, no stray code line.

---

### Task 1: Đá bazan tokens

**Files:**
- Modify: `core/ui/src/commonMain/kotlin/com/tana/spa/core/ui/SpaColors.kt`, `AppTheme.kt`, `SpaExtraColors.kt`
- Modify: `composeApp/src/webMain/resources/styles.css`, `index.html` (ground `#F4F4F1`, ink, spinner)
- Create: `docs/adr/0009-basalt-palette.md`; Modify: `DESIGN.md`, `docs/material-design.md`, ADR 0008 status line
- Test: `core/ui/src/commonTest/kotlin/com/tana/spa/core/ui/SpaColorSchemeTest.kt`

**Produces:** `SpaColors.{Ground, Panel, Field, FieldHigh, FieldHighest, Ink, InkSoft, Muted, Rule, Outline, Basalt, OnBasalt, Jade, OnJade, JadeTint, JadeDeep, Ochre, OchreTint, Moss, Danger, DangerTint, DangerDeep, Shadow}`; role map: primary=Basalt, primaryContainer=JadeTint, onPrimaryContainer=JadeDeep, secondary=Jade, secondaryContainer=JadeTint, onSecondaryContainer=JadeDeep, tertiary=Muted, background=Ground, surface=Panel, surfaceVariant=Field, surfaceContainer=Field, surfaceContainerHigh=FieldHigh, surfaceContainerHighest=FieldHighest, outline=Outline, outlineVariant=Rule; extra link=Jade, onPhotoAccent=Panel.

- [ ] Rewrite `SpaColorSchemeTest` to the role map above and the pairs: OnBasalt/Basalt, Basalt/Ground, Jade/Ground, Jade/Panel, Jade/Field, JadeDeep/JadeTint, Ink on Ground/Panel/Field/FieldHighest, InkSoft on Ground/Field, Muted on Ground/Panel/Field, Ochre on Panel/OchreTint, Moss/Panel, Danger/Panel, DangerDeep/DangerTint, Ground/Ink; boundary: Outline vs Panel and Ground ≥ 3.0.
- [ ] Run `./gradlew :core:ui:jvmTest` → FAIL (unresolved tokens).
- [ ] Replace the palette, scheme and extra colours; update web shell, ADR 0009, DESIGN.md.
- [ ] Run `./gradlew :core:ui:jvmTest` → PASS; commit `feat(ui)!: replace the mineral-spring palette with Đá bazan`.

### Task 2: Shared chrome

**Files:** `core/ui/.../components/AppBars.kt`, `NavBar.kt`, `SpaSurfaces.kt` (`SpaCard` 1px `outlineVariant` edge), `SpaButtons.kt`, `SpaChips.kt`, `SpaTextStyles.kt` (+ `heroTitle`), `SpaStepper.kt`; Test: `SpaTextStylesTest.kt`

- [ ] Add `heroTitle` (Fraunces medium 48sp, line 52sp) to `SpaTextStyles` and a test pinning size, weight and family; HomeScreen's inline 38/60sp go away.
- [ ] Top-bar destination: one active mark (ink label, jade 2dp underline), no pill.
- [ ] `SpaCard`: flat white with 1dp `outlineVariant` border, hover steps it to `outline`; selected = `primaryContainer`.
- [ ] Chips: selected = `secondaryContainer`/`onSecondaryContainer`, unselected outlined in `outlineVariant`.
- [ ] Stepper: compact (number + label on one line) with jade for the current step.
- [ ] `./gradlew :core:ui:jvmTest`; commit `feat(ui): quiet chrome for the basalt system`.

### Task 3: Home

**Files:** `HomeContract.kt`, `HomeViewModel.kt`, `di/BookingModule.kt`, `ui/HomeScreen.kt`, strings; Test: `HomeViewModelTest.kt`

**Produces:** `HomeState.nextVisit: GuestBooking?`; `HomeViewModel(catalog, callerRole, bookings: GuestBookingRepository, today, now: () -> Long)`.

- [ ] Tests: `the_next_visit_is_the_earliest_pending_or_confirmed_ahead_of_now`, `past_and_cancelled_visits_are_not_next`, `a_failed_bookings_call_leaves_the_menu`, `staff_have_no_next_visit` (no `mine()` call).
- [ ] Implement; Koin passes `get()` and `{ Clock.System.now().toEpochMilliseconds() }`.
- [ ] Screen: title row (greeting noun title + photo band on wide), next-visit panel (date block, treatment, time, status pill, "Xem lịch hẹn") or quick book, one treatment menu of rows grouped by category (thumb, name, minutes, price right), shelf rows, offers row.
- [ ] jvmTest + screenshot; commit `feat(booking): lead home with the next visit and one treatment menu`.

### Task 4: Booking flow and service detail

**Files:** `BookingScreen.kt`, `BookingSections.kt`, `BookingReview.kt`, `CatalogueFilters.kt`, `ServiceDetailScreen.kt`, `core/ui/.../SpaBookingBlocks.kt`

- [ ] Step 1 treatments as selectable full-width rows; search field capped to the page; appointment bar shows list price and, when `check is PromoCheck.Applied`, "Mã {code}: −{discount}" then the total.
- [ ] Review as labels-over-values; service detail facts sheet.
- [ ] jvmTest + screenshot; commit `feat(booking): rows, a named discount and a facts sheet`.

### Task 5: Lịch hẹn, Ưu đãi, Thông báo, Cài đặt

**Files:** `BookingsScreen.kt`, `PromotionsScreen.kt`, `NotificationsScreen.kt`, `SettingsScreen.kt`

- [ ] Bookings: upcoming/past groups, status pill, empty state keeps the frame and offers booking.
- [ ] Promotions: rows with terms and copy; expired grouped and dimmed.
- [ ] Notifications: rows, unread = jade dot + ink weight.
- [ ] Settings: grouped rows, neutral switches, language as segmented control.
- [ ] jvmTest + screenshots; commit `feat: rows and quiet colour for bookings, offers, alerts and settings`.

### Task 6: Auth, assistant, update modal

**Files:** `AuthShell.kt`, `LoginScreen.kt`, `SignUpScreen.kt`, `VerifyPhoneScreen.kt`, Assistant `ui/*`, `UpdateModals.kt`

- [ ] Remove the logo chip from the photo; brand at the top of the form column; form in one panel; retone the rest.
- [ ] Screenshot; commit `feat(auth): brand in the form column on the basalt palette`.

### Task 7: Desk, menu and codes

**Files:** `AdminScaffold.kt`, `AdminRequestsScreen.kt`, `AdminSchedule.kt`, `AdminMenuScreen.kt`, `AdminCodesScreen.kt`, `CreateCodePanel.kt`

- [ ] Panels and rows, colour only on status pills and the one primary per view; screenshot as owner (local account given `role = 'owner'`).
- [ ] Commit `feat(admin): desk, menu and codes on the basalt system`.

### Task 8: Gate and review

- [ ] `./gradlew ktlintCheck jvmTest :composeApp:compileKotlinWasmJs` → BUILD SUCCESSFUL.
- [ ] Fresh reviewer on the branch diff; fix findings; push and open the PR.

# Material 3 in this app

The app is a Material 3 app wearing the Đá bazan system in `DESIGN.md` (ADR 0009). **Reach for the Material component
first**; only draw something by hand when Material has no component for it, and say so in a comment.

A Material component brings behaviour we would otherwise have to write and get wrong: focus order,
touch targets, ripple, state layers, semantics for screen readers, IME handling, RTL, and the
pressed/disabled/error states that are easy to forget.

Reference: the component catalogue at <https://m3.material.io/components>, and the Compose mapping
at <https://developer.android.com/develop/ui/compose/components>.

## What we use for what

| Need | Component | Where it is used here |
| --- | --- | --- |
| Primary / quiet / destructive action | `Button`, `FilledTonalButton` | `SpaPrimaryButton`, `SpaSecondaryButton`, `SpaSocialButton` |
| One tap on an icon | `IconButton`, `FilledTonalIconButton` | copy a code, close a sheet, the codes table's edit/disable |
| Pick one of a set, inline | `FilterChip` | `SpaFilterChip`: booking days, times, code states, eligible treatments |
| Two to five exclusive options | `SingleChoiceSegmentedButtonRow` + `SegmentedButton` | percent vs amount in the create-code panel |
| One subject, grouped content | `Card` | `SpaCard` (flat; `onClick`/`selected` for treatments and therapists) |
| A row of text with leading/trailing extras | `ListItem` | settings rows, notification rows, the request queue, promo rows |
| On/off | `Switch` on a `toggleable` `ListItem` | notification preferences |
| One of several, stacked | `RadioButton` on a `selectable` row | decline reasons |
| Choose from a list on a temporary surface | `DropdownMenu` + `DropdownMenuItem` | language, "suggest another time" |
| A date | `DatePicker` with `SelectableDates` | the booking calendar on wide screens |
| Count or unread marker | `Badge`, `BadgedBox` | the Alerts tab, the staff queue, unread notifications |
| Switch views: phone / mid / desk | `NavigationBar` / `NavigationRail` / `PrimaryTabRow` | guest tab bar, staff rail, staff tabs on a phone |
| Screen frame | `Scaffold` + `TopAppBar` | `AppScaffold` |
| A form or secondary content over the screen | `ModalBottomSheet` | create discount code |
| A decision that blocks the flow | `AlertDialog` | request sent, decline, disable code |
| Transient confirmation | `SnackbarHost` + `showSnackbar` | code copied, booking confirmed/declined |
| Progress against a limit | `LinearProgressIndicator` | discount-code usage |
| Separator | `HorizontalDivider` | the "or continue with" rule |
| Text entry | `OutlinedTextField` | `SpaTextField` |

### What is deliberately not a Material component

Two things in this design have no Material equivalent; they live in `core:ui` and are documented
where they are defined:

- **The felt tray** (`Modifier.spaFelt`) and the **cork bevel** (`Modifier.spaCork`) — recessed and
  raised surfaces. CSS inset shadows have no Compose equivalent, so both are drawn as short
  gradients.
- **`SpaStatusPill`** — a non-interactive status tag. Material's chips are all interactive, and a
  disabled chip reads as "you cannot press this", which is the wrong meaning.

`SpaIcon` is a drawn icon set because the Material icon artifact is not on the classpath.

### Material's text field

`SpaTextField` is Material 3's `OutlinedTextField` with the theme's colours: the label floats into
the outline, the outline turns jade on focus and red with an icon and a message on an error, and
the helper is its supporting text. Until 2026-11-13 it was hand-built on `BasicTextField`, because
`material3 1.11.0-alpha07` with `compose 1.12.1` threw `AbstractMethodError` (`TextFieldDefaults`
building a `CustomStyle` that foundation did not implement). `material3 1.12.0-alpha03` composes it
on the desktop and web targets; the booking dropdown on Home is `ExposedDropdownMenuBox` over the
same field.

## Adaptive layout: the web is a width, not a platform

There is no "web design" in this app and no `if (isWeb)`. A browser window is a window that is
usually wide and can be dragged down to phone width, so the layout follows Material's width
breakpoints and the web gets whichever layout it is wide enough for.

`SpaWindow.size` is the single source: the root measures the window once
(`ProvideSpaWindowSize` in `App.kt`) and publishes a `SpaWindowSize`. Screens read it; they never
measure for themselves, because a screen and its app bar that each call `BoxWithConstraints` can
disagree about how wide the window is.

| Class | Width | Navigation | Layout |
| --- | --- | --- | --- |
| `COMPACT` | `< 600dp` | bottom `NavigationBar` | one column |
| `MEDIUM` | `600–839dp` | `NavigationRail` | one column, wider gutters |
| `EXPANDED` | `840–1199dp` | destinations in the app bar | two columns where the screen has two jobs |
| `LARGE` | `>= 1200dp` | destinations in the app bar | up to three columns (booking) |

Material's own default above compact is the rail for every size. We follow it at `MEDIUM` and
deviate at `EXPANDED` and wider, where the destinations move into the app bar: that is where a
visitor to a web page looks for them, and it is what the design file draws for a desk screen.

Two more rules that matter more on a wide screen than on a phone:

- **Cap the page.** Every screen's body goes in a `SpaPage`, which centres it and caps it at a
  `SpaPageWidth` — `Reading` (720dp) for text, `Wide` (1000dp) for a list with a hero, `Full`
  (1240dp) for multi-column. A line of text that runs 2000px is unreadable and a row whose ends
  are a metre apart is unscannable. The app bar caps its contents the same way.
- **Pointer states are not optional.** A pointer hovers, so every interactive surface needs a
  hover state as well as a pressed one. Material components have both; a hand-built surface has
  neither until you give it one — `SpaCard` tints on hover through `collectIsHoveredAsState`,
  because Material's *hovered elevation* would otherwise raise a transparent card and draw a
  shadow around nothing.

The page shell (`composeApp/src/webMain/resources/`) carries the rest of the web etiquette: the
document title and description, `theme-color`, an inline SVG favicon, a page background that
matches the app so there is no white flash, and a loading indicator that the canvas hides once
Compose attaches.

## Rules

1. **Theme, never literals.** Colours come from `MaterialTheme.colorScheme` or
   `SpaTheme.extraColors`, shapes from `MaterialTheme.shapes`, text from `SpaTheme.textStyles`. No
   `Color(0x…)`, no inline `fontSize`. `SpaColorSchemeTest` pins every role to its design token.
2. **Restyle a component, don't rebuild it.** Every Material component takes `colors`, `shape` and
   `border`; that is the seam for this design. `SpaTextField` is the pattern: Material's
   `OutlinedTextField` with the theme's container and outline colours.
3. **Wrap in `core:ui` when a styling is used twice.** One call site uses the Material component
   directly; the second one makes it an `Spa*` wrapper so the styling exists once.
4. **Let Material own interaction.** Use `onClick` on the component, or `Modifier.clickable` /
   `selectable` / `toggleable` with a `Role` on the row — not a tap target drawn by hand. Material
   keeps the 48dp touch target even when the visual is smaller.
5. **State goes through MVI.** Components stay stateless: `(state, effects, onEvent)`. A component's
   own remembered state (menu open, sheet position) stays local.
6. **Accessibility is part of the component.** Every `IconButton` and icon that carries meaning gets
   a `contentDescription`; decorative icons get `null`. Keep `heading()` on screen titles and group
   labels. Colour alone never carries meaning — pair it with text or an icon.
7. **Contrast is a test, not an opinion.** Any new colour pairing gets an assertion in
   `SpaColorSchemeTest` (`contrastRatio(fg, bg) >= 4.5`).
8. **Experimental APIs:** `@OptIn(ExperimentalMaterial3Api::class)` is fine for shipped components
   (date picker, bottom sheet, segmented button, tabs). Avoid `ExperimentalMaterial3ExpressiveApi`
   — that is where this version's API is still moving; the interactive `ListItem(selected = …)`
   overloads are an example, which is why rows use a plain `ListItem` plus a `selectable` modifier.

## Motion

One grammar, taken from the spa's web identity and kept on the app's duration scale. The decision
and the numbers it came from: `docs/adr/0005-motion-and-scroll-reveal.md`.

**Easing.** `SpaEasing.OutExpo` (`cubic-bezier(0.16, 1, 0.3, 1)`) for anything arriving,
`SpaEasing.InOut` (`cubic-bezier(0.65, 0, 0.35, 1)`) for anything symmetric or leaving. Never
Compose's default easing.

**Durations** come from `SpaTheme.motion`, never a literal: `short` 160, `medium` 220, `long` 320,
`exit` (medium × ⅔), `reveal` 520, `stagger` 60 ms. `SpaMotionTest` pins their ordering.

**Reveal on scroll.** Hang `Modifier.revealOnScroll(order = n)` on each section of a page: it
fades the element in and lifts it 24–40 dp (by window size) the first time it reaches the
viewport, once. `order` cascades the first screenful and stops at the fourth slot.
`SpaRevealFrom.Start` / `.End` slide sideways instead, for a side-by-side pane.

Reveal page sections, cards and list rows. Do **not** reveal a form field, a loading skeleton, an
error message or a chat message — a control the guest is reaching for must be there when the page
is.

**Scroll state.** Inside `AppScaffold`, a scrolling screen uses `rememberSpaScrollState()` or
`rememberSpaLazyListState()` instead of Compose's; that is what lifts the top app bar onto
`surfaceContainer` with a divider once the page moves. Screens with their own frame (the staff
console, the sign-in shell) have no bar to lift and use the plain Compose state.

**Page transitions** are chosen in `App.kt`, not per screen. A route listed in `TOP_LEVEL_ROUTES`
fades through to another one; anything else slides along the shared X axis by stack depth. Adding
a destination to the navigation bar means adding its route to that set — `RouteTransitionTest`
fails otherwise.

**Reduced motion** is handled centrally: `AppTheme` swaps in `NoMotion` when the platform asks for
it, `revealOnScroll` then returns the modifier untouched, and route changes become instant. A new
animation must read `SpaTheme.motion` so it inherits this; `motion.reduced` is there for the cases
where a zero duration is not enough.

## Checklist for a new screen

1. Sketch the screen as a list of Material components from the table above. Anything left over is
   either a `core:ui` wrapper you can reuse, or a new one worth naming.
2. `Scaffold`/`AppScaffold` for the frame; `SpaScreenHeader` for the title.
3. Compose the body from `SpaCard` + `ListItem` + chips + buttons; keep the screen a stateless
   `(state, effects, onEvent)` composable, with the entry object the only thing that touches Koin.
4. Lay out for both sizes: `BoxWithConstraints` with one breakpoint (840dp for content, 900dp for
   the staff console), never a per-platform branch.
5. One-shot outputs (snackbar, navigation) go through effects, not state.
6. Strings in `composeResources/values` (+ `values-en`), colours and shapes from the theme.
7. Scroll with `rememberSpaScrollState()`, and give each section a `Modifier.revealOnScroll(order)`.
8. Run `./gradlew ktlintCheck jvmTest` and compile the web target before pushing.

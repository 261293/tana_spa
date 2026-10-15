# Material 3 in this app

The app is a Material 3 app wearing the "Moss & Linen" design. **Reach for the Material component
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
| Text entry | `TextField` (filled) | `SpaTextField` |

### What is deliberately not a Material component

Three things in this design have no Material equivalent; they live in `core:ui` and are documented
where they are defined:

- **The moss pocket** (`Modifier.spaPocket`) — the one dark area per screen that holds the primary
  action.
- **The felt tray** (`Modifier.spaFelt`) and the **cork bevel** (`Modifier.spaCork`) — recessed and
  raised surfaces. CSS inset shadows have no Compose equivalent, so both are drawn as short
  gradients.
- **`SpaStatusPill`** — a non-interactive status tag. Material's chips are all interactive, and a
  disabled chip reads as "you cannot press this", which is the wrong meaning.

`SpaIcon` is a drawn icon set because the Material icon artifact is not on the classpath.

### Known incompatibility: Material's text field

`SpaTextField` is an outlined web-form input — label above the box, pale fill, hairline outline
that thickens to moss on focus and red on an error — built on `BasicTextField`, not on Material's
`TextField`. With the versions this
project pins (`material3 1.11.0-alpha07` with `compose 1.12.1`), composing a Material text field
throws at runtime:

```
java.lang.AbstractMethodError: Receiver class androidx.compose.material3.TextFieldDefaults$$Lambda
does not define or inherit an implementation of the resolved method
'abstract void applyStyle(androidx.compose.foundation.style.CustomStyleScope)'
of interface androidx.compose.foundation.style.CustomStyle
```

material3 builds a `CustomStyle` that this foundation version does not implement. Bumping material3
is not the fix on its own — an earlier bump broke the web target and was reverted (commit
`9e759dc`). Re-try `TextField` when the two artifacts line up again; everything else in the table
above was exercised on the desktop target with these versions.

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
   `border`; that is the seam for this design. `SpaTextField` is the pattern: Material's filled
   text field with the indicator switched off and a tonal container.
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

## Checklist for a new screen

1. Sketch the screen as a list of Material components from the table above. Anything left over is
   either a `core:ui` wrapper you can reuse, or a new one worth naming.
2. `Scaffold`/`AppScaffold` for the frame; `SpaScreenHeader` for the title and its moss accent.
3. Compose the body from `SpaCard` + `ListItem` + chips + buttons; keep the screen a stateless
   `(state, effects, onEvent)` composable, with the entry object the only thing that touches Koin.
4. Lay out for both sizes: `BoxWithConstraints` with one breakpoint (840dp for content, 900dp for
   the staff console), never a per-platform branch.
5. One-shot outputs (snackbar, navigation) go through effects, not state.
6. Strings in `composeResources/values` (+ `values-en`), colours and shapes from the theme.
7. Run `./gradlew ktlintCheck jvmTest` and compile the web target before pushing.

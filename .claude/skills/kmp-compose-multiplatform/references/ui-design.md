# UI design rules

Design rules for this app's screens, across the five targets. For the aesthetic judgement behind
a new screen use the `frontend-design` skill; to review a finished screen use `design-auditor`;
for iOS expectations use `apple-hig-expert`; to formalise tokens and handoff use `ui-design-system`.

## Tokens, not literals

Every color, dimension, type style and shape comes from `MaterialTheme` — never a literal in a
composable. A hardcoded `Color(0xFF...)` or `16.dp` scattered through a screen is the single
thing that makes a Compose codebase impossible to re-theme, and it defeats dark mode silently.

- Colors: `MaterialTheme.colorScheme.*`. Define the scheme once; derive it from a seed rather than
  hand-picking 30 roles (see `libraries.md` → MaterialKolor).
- Spacing: one scale (4/8/12/16/24/32), applied through `Modifier.padding`. No arbitrary values.
- Type: `MaterialTheme.typography.*`. Do not set `fontSize` inline.
- Elevation and shape: theme-level, so iOS and web can flatten them in one place.

Dark mode is not a variant to add later: both schemes ship together or the second one never
gets tested.

## Don't ship the default look

A Material 3 app with the default purple seed, `Inter`/`Roboto`, and uniform 8dp corners reads as
unfinished — users recognise the template. Decide a deliberate identity: one seed color the brand
owns, a type pairing that is not the framework default, and a consistent radius and density. Then
hold it. The `frontend-design` skill exists for exactly this decision and should be loaded before
building a new screen, not after.

## Per-target rules

| Target | What changes |
|---|---|
| Android | Material 3 is native. Respect predictive back, edge-to-edge insets, and system dynamic color if adopted. |
| iOS | Material widgets look foreign. Match HIG for navigation, sheets, and the back gesture, or adopt an adaptive layer globally (`libraries.md` → Calf, Compose Cupertino). Never half-adapt. |
| Desktop (JVM) | Pointer, not touch: hover states, right-click, keyboard focus order, and a resizable window all become real. Phone layouts stretched to 1400px look broken. |
| Web (wasmJs) | Compose renders to a **canvas**. There is no DOM, so browser a11y tooling, DOM-based test selectors, text selection and SEO do not apply. Font loading and first paint are visible; the session is lost on reload because the web `KeyValueCache` is in memory. |

The canvas constraint is why HTML-scanning accessibility tools are useless here — accessibility on
web has to be verified through Compose's own semantics, the same as on mobile.

## Responsive, not phone-only

Desktop and browser windows resize continuously, so a fixed single-column layout is a bug on two
of the five targets. Drive layout from window size class (`libraries.md`) rather than the platform:

- Compact — single column, bottom navigation.
- Medium — wider gutters, navigation rail.
- Expanded — list/detail side by side, capped content width. Text lines should not run the full
  width of a 27" monitor.

Decide the breakpoint behaviour per screen when the screen is written. Retrofitting it later means
re-plumbing state that assumed one pane.

## Accessibility — the part that gets skipped

Verify against WCAG 2.2 success criteria:

- Contrast at least 4.5:1 for body text, 3:1 for large text and meaningful icons. Check both schemes.
- Touch targets at least 48dp on Android, 44pt on iOS, including icon-only buttons.
- Every icon-only control and image has a `contentDescription`; decorative images pass `null`.
- Honour the platform font scale. Hardcoded heights clip at 200% text size — constrain with
  `minHeight`, not `height`.
- Focus order and keyboard traversal are testable on desktop and web; do it there and mobile
  benefits.
- Never encode state in color alone — pair it with an icon, label or shape.

`design-auditor` covers contrast, token adherence and breakpoints; `a11y`-specific checks for text
fields and dynamic type are already in `compose-best-practices.md`.

## Screen templates

The app's screens fall into four shapes. Reuse the shape rather than inventing a layout per screen.

**Full-screen (login, onboarding)** — no app bar; listed in `FULL_SCREEN_ROUTES` in `App.kt`.
Single centred column, capped width (~480dp) so it does not stretch on desktop and web. One
primary action. Errors inline next to the field, not in a snackbar.

**Feed / list (Feed, Explore, Likes)** — lazy list, skeleton placeholders while loading, explicit
empty state with an action, and an error state that offers retry. All three states are designed up
front: the loading and empty cases are where stubbed screens get shipped by accident.

**Detail (post, profile)** — scrolling content under a collapsing or plain app bar, actions grouped
and never more than one primary.

**Form (NewPost, settings)** — labelled fields, validation on blur rather than per keystroke,
disabled submit while invalid, and a visible pending state. Validation rules belong in `shared`
(`AuthValidation`) so client and server agree.

Explore, Likes, NewPost and Profile are placeholders today. When one is implemented, take its
shape from this list and its state handling from the MVI rules — state in `state`, one-shot
outputs via `sendEffect`.

## Review checklist

Before calling a screen done:

- [ ] No literal colors, sizes or font sizes; everything from `MaterialTheme`
- [ ] Light and dark both checked
- [ ] Loading, empty, error and success states all exist
- [ ] Layout checked at compact, medium and expanded widths
- [ ] Contrast, touch targets and content descriptions verified
- [ ] Renders correctly on web (`:composeApp:wasmJsBrowserDevelopmentRun`), the strictest target
- [ ] Screen composable is stateless `(state, effects, onEvent)`; only `*Entry` touches Koin

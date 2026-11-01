# 5. One motion grammar: ease-out-expo, reveal on scroll, two kinds of page change

Date: 2026-11-01

## Status

Accepted

## Context

The app animated in two places and nowhere else: a route change slid the arriving screen in from
the edge, and the splash faded out. Both used Compose's default easing. Everything else appeared
instantly — a treatment menu with twenty cards painted all twenty at once, and a tab switch looked
the same as drilling into a treatment, which told the guest nothing about where they had gone.

The spa's own web identity (`latablehoian.com`) already has a motion vocabulary, and it is a
small, consistent one. Read from its stylesheet and its `IntersectionObserver`:

| | reference site |
|---|---|
| easing | `cubic-bezier(0.16, 1, 0.3, 1)` ("out-expo"), plus `cubic-bezier(0.65, 0, 0.35, 1)` for symmetric moves |
| reveal | opacity `0 → 1` with `translateY(40px) → 0`, over `0.8s` |
| sideways reveal | `translateX(±60px) → 0`, same duration |
| trigger | 15% of the element visible, with the viewport's bottom edge pulled up 50px |
| stagger | per-element `data-delay`, applied with `setTimeout` |
| replay | the class is removed when the element leaves the viewport, so it animates again |
| reduced motion | every transition and animation forced off, opacity forced to 1 |

Three of those numbers do not survive the trip to an app. `0.8s` on every card is the right weight
for an editorial page a visitor scrolls once; in a product the guest taps through daily it reads as
lag. Replaying a reveal on scroll-back is a fine flourish on a landing page and a distraction in a
list the guest is scanning for their booking. And an unbounded `data-delay` chain makes the tail of
a long list wait seconds.

## Decision

**The two curves come across exactly.** `SpaEasing.OutExpo` and `SpaEasing.InOut` are the same
cubic béziers, so a screen and the marketing site decelerate alike. Everything the app animates now
names one of them; nothing uses Compose's default easing.

**Durations stay on the app's existing scale.** `SpaMotion` keeps `short`/`medium`/`long`
(160/220/320 ms) and gains `reveal` (520 ms) and `stagger` (60 ms). 520 ms is longer than any other
transition in the app — it carries the unhurried feel — while staying inside what Material 3 and
the Apple HIG allow for a large-area entrance. `SpaMotionTest` pins the ordering and the ceiling.

**A reveal happens once.** `Modifier.revealOnScroll(order, from)` fades and lifts an element the
first time it reaches the viewport and then leaves it alone. Scrolling back up does not re-hide it,
and the flag is `rememberSaveable`, so it rides the same `SaveableStateHolder` that already keeps a
screen's scroll position: coming back to a destination shows it as the guest left it, rather than
re-animating a page they have seen. Switching tabs is frequent, and a 520 ms fade on top of the
380 ms page change makes the tenth switch feel slow.

**The stagger builds and then stops.** `SpaMotion.staggerFor(order)` is `stagger × order` clamped
at the fourth slot, so the first screenful cascades and nothing ever waits more than 180 ms. List
rows in a `LazyColumn` pass no order at all: they already arrive staggered in time, and an added
delay would make each newly scrolled-in row feel late.

**Travel scales with the window, not the platform.** 24 dp compact, 32 dp medium, 40 dp from
expanded up — the reference's 40 px is a desktop measurement, and the same distance on a phone is
a larger share of the screen than the design intends.

**The trigger is the window, read through layout rather than an observer.** The root
`BoxWithConstraints` in `ProvideSpaWindowSize` already measures the window, so it publishes the
height as well; `revealOnScroll` compares the element's `boundsInRoot().top` against
`viewportHeight - 72.dp`. The margin stands in for the reference's `-50px` *and* for the guest
frame's bottom bar, which the window height does not subtract. An unmeasured window reveals
immediately: content must never be left invisible because a measurement was missing.

**A route change says which kind of change it was.** Two sibling destinations in the bar fade
through each other (Material's pattern for a move with no spatial relationship); anything reached
from another screen slides along a shared X axis, forward or back by stack depth. `sizeTransform`
is `null` on both, so a taller page does not squeeze into the shorter one's height on the way in.
`RouteTransitionTest` asserts that every destination the bar can show is in `TOP_LEVEL_ROUTES`,
which is what keeps the two sets from drifting apart.

**The top app bar reacts to scroll.** A screen's `rememberSpaScrollState()` /
`rememberSpaLazyListState()` publishes "scrolled" through `SpaScrollLift`, and `AppTopBar` crosses
from `background` to `surfaceContainer` and fades in a divider. The published value is a
`derivedStateOf`, so the bar recomposes when the boolean flips, not on every scrolled pixel.

**Reduced motion short-circuits before anything is built.** `revealOnScroll` returns the modifier
unchanged — no `graphicsLayer`, no `onGloballyPositioned` — and `routeTransition` returns
`EnterTransition.None`. `NoMotion` zeroes `reveal` and `stagger` too.

## Consequences

Forms are deliberately left out. A sign-in field that fades in is a field the guest cannot type
into yet, so `AuthShell` reveals only its heading block and the inputs appear with the page. The
same reasoning keeps reveals off chat messages, loading skeletons and error states.

The first screen's reveal runs behind the splash, which holds for 1.2 s. The guest therefore sees
the sign-in screen already settled. That is a consequence, not a defect — re-running the entrance
after the splash would delay the first interaction.

A revealed element is invisible but present in the semantics tree, so a screen reader reaches it
before it has faded in. That is the behaviour we want and is why this is a `graphicsLayer` and not
an `AnimatedVisibility`.

`onGloballyPositioned` fires once per layout pass per revealed element while scrolling. The
callback returns immediately once the element has been reached, and the animation itself runs in
the draw phase, so no reveal causes a relayout.

Adding motion to a new screen is now: use `rememberSpaScrollState()` for the scroll, and hang
`Modifier.revealOnScroll(order = n)` on each section of the page. `docs/material-design.md` carries
the checklist.

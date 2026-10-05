# Architecture

Multi-module KMP, **layered clean architecture + MVI (unidirectional data flow)**, Koin DI,
shared Compose Multiplatform UI.

```
composeApp ──► feature/* ──► core/{mvi,navigation,network,cache,ui} ──► shared
 (wiring)      (vertical      (horizontal, feature-agnostic)            (pure Kotlin helpers)
                slices)
```

Rules: features never depend on each other; `core/*` never depends on `feature/*`;
only `composeApp` knows every feature (via `FeaturesRegistry` and `appModules()`).

## Inside a feature (see `feature/Feed`)

| Layer | Package | Responsibility |
|---|---|---|
| presentation | `ui`, `manipulator`, `mapper` | Stateless composable + `MviViewModel` (State / Event / Effect) |
| domain | `model`, `repository`, `mapper` | Pure Kotlin models and repository **interfaces** |
| data | `remote`, `cache`, `repository` | DTOs, Ktor/SQLDelight sources, repository implementation |

Dependencies point inward: presentation -> domain <- data.

## Patterns

- **MVI** (`core/mvi/MviViewModel`): `state` is one immutable `StateFlow`; user intents enter through
  `onEvent`; one-shot outputs (snackbar, navigation) leave through the `effects` channel so they are
  never replayed on recomposition or rotation.
- **Stateless screens**: `FeedScreen(state, effects, onEvent)` has no ViewModel reference, so it is
  previewable and testable. The `*Entry` object is the only place that touches Koin and the ViewModel.
- **Offline-first repository**: the cache is the single source of truth. The repository exposes
  `feed: Flow` and `refresh(): AppResult<Unit>`; the UI observes the cache and refresh only updates it.
  A failed refresh leaves cached data in place.
- **Typed errors** (`shared/result/AppResult`): expected failures are values, not exceptions.
  `appResultOf` rethrows `CancellationException`, unlike `runCatching`.
- **Use cases are optional**: add a `domain/usecase` class only when logic is shared between
  screens or is more than a pass-through to the repository.
- **Feature entry points**: each feature exposes a `FeatureEntry` (route + `Content`) and a Koin module.
- **Convention plugins** (`build-logic`): every module gets identical targets and Android namespace.

## Adding a feature

1. `feature/<Name>` module, `include` it in `settings.gradle.kts`, copy `feature/Feed/build.gradle.kts`.
2. Create `domain` interfaces, `data` implementations, a `<Name>Module` for Koin, a `MviViewModel`.
3. Add `<Name>Entry`, register it in `FeaturesRegistry` and its module in `appModules()`.

## Known next steps

- Navigation is a minimal string-route back stack (`core/navigation`). Move to Navigation 3 /
  type-safe `@Serializable` routes once screens need arguments or deep links.
- `FeedCacheImpl` is in-memory; back it with a SQLDelight table (the `core/cache` module is ready).
  The web target has no SQLDelight driver yet.
- `FeedRemoteApiImpl` returns stub data; call `NetworkClient.httpClient` with `Constants.BASE_URL`.
- Apply the Feed template (domain/data/MVI) to Auth, Explore, Likes, NewPost and Profile,
  which are still placeholder screens.

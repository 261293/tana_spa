---
name: self-documenting-code
description: Code style for this repo — no narrating comments. Names carry the meaning, complex logic is split into small named functions, and a comment is written only where the logic itself is genuinely hard. Use when writing, refactoring or reviewing any code here (Kotlin, SQL, JS, Gradle).
---

# Self-documenting code

The rule: **a reader should understand the code from its names and its shape, not from prose
beside it.** A comment is an admission that the code could not say it itself, so each one has to
earn its place.

## What to do

1. **Name it instead of describing it.** If you are about to write a comment above a line,
   consider whether a better name for the function, variable or property removes the need.
2. **Split instead of explaining.** If a function needs a comment to break it into stages, those
   stages are functions. Extract them and give each the name the comment was going to be.
3. **Comment only genuinely hard logic** — see the list below. One or two lines, saying *why*,
   never *what*.

## When a comment is justified

Keep a comment only for something a competent reader cannot derive from the code:

- a **security or correctness constraint** that looks arbitrary — a constant-time comparison, a
  check that must happen before another, a deliberate ordering
- a **non-obvious reason** for an unusual choice — why there is no foreign key, why a value is
  re-read instead of cached, why a failure is swallowed
- **domain or protocol facts** not visible locally — "the provider caps this at 10 minutes",
  "callers rely on this status code"
- a **concurrency or atomicity** guarantee, and what breaks without it
- a **workaround** for a bug or platform limitation, with what it works around

Everything else goes: no comments restating a signature, no section banners, no narration of
control flow, no "// returns null when not found" above `?: return null`.

## Examples

```kotlin
// BAD — the comment carries meaning the code should
// Overrides win over the global row, which wins over the deploy default
val enabled = overrides[flag.key] ?: globals[flag.key] ?: deployDefaults.isEnabled(flag)

// GOOD — the name carries it
private fun resolve(flag: FeatureFlag, overrides: Map<String, Boolean>, globals: Map<String, Boolean>) =
    overrides[flag.key] ?: globals[flag.key] ?: deployDefaults.isEnabled(flag)
```

```kotlin
// BAD — a comment splitting one function into stages
fun refreshIfStale() {
    // nothing fetched yet, a different user, or older than the TTL
    if (lastFetchedAt == null || tokens.accessToken() != lastToken || now() - lastFetchedAt >= ttl) refresh()
}

// GOOD — the stages are named
fun refreshIfStale() {
    if (!isFresh()) refresh()
}

private fun isFresh(): Boolean {
    val fetchedAt = lastFetchedAt ?: return false
    return tokens.accessToken() == lastToken && now() - fetchedAt < staleAfterMillis
}
```

```kotlin
// JUSTIFIED — the reason is invisible in the code, and getting it wrong is a vulnerability
if (!MessageDigest.isEqual(presented.encodeToByteArray(), expected.encodeToByteArray())) { … }
// A length-dependent or short-circuiting compare leaks the token byte by byte.
```

## Tests

A test name is the specification, so it replaces the comment: prefer
`anOverrideBeatsTheGlobalRow()` over `testFlags()` with a comment. Comment a test only where the
*setup* is strange (a clock wound forward, a deliberate race, a fixture that looks wrong but is
not).

## Other languages in this repo

- **SQL migrations** — keep a short header saying what the migration does and any constraint whose
  absence would look like an oversight (a missing foreign key, a partial unique index). Drop the
  rest.
- **JavaScript** (`server/src/main/resources/admin/`) — same rule; extract functions.
- **Gradle `.kts`** — comments must be `//`, never `/** */` (ktlint rejects KDoc in a block there).

## KDoc

Public API in `shared/` is a contract between the clients and the server, so a one-line KDoc is
allowed where the signature genuinely does not say it: units, nullability meaning, an invariant a
caller must uphold. If the name already says it, write nothing.

## Checklist before finishing

- [ ] Every remaining comment matches one of the justified cases above
- [ ] No function needs a comment to be read top to bottom
- [ ] Extracted helpers are named after the intent, not the mechanism
- [ ] `./gradlew ktlintFormat && ./gradlew ktlintCheck` still passes

## Watch out

Kotlin block comments **nest**: a `/*` inside a KDoc (for example writing a route as `otp/*`)
opens a comment that never closes, and the compiler reports `Unclosed comment` at the end of the
file.

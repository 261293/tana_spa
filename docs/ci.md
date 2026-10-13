# CI: what runs on every pull request

Workflow: `.github/workflows/ci.yml`. It runs on each pull request into `main` and on each push to `main`.

| Job (check name) | What it protects against | Run it locally |
|---|---|---|
| **Gradle wrapper validation** | A tampered `gradle-wrapper.jar` (supply-chain attack) | n/a |
| **Secret scan (gitleaks)** | Keys, tokens and passwords committed in this PR's commits | `gitleaks git --redact -v .` |
| **Code style (ktlint)** | Inconsistent formatting, wildcard imports, long lines | `./gradlew ktlintCheck` (fix: `./gradlew ktlintFormat`) |
| **Unit tests** | Regressions in shared code, features and the backend (real embedded PostgreSQL) | `./gradlew jvmTest :server:test` |
| **Build (web, Android, server)** | Code that compiles in tests but not in the shipped app; a stale `kotlin-js-store/wasm/yarn.lock`; source maps in the web bundle | `./gradlew :composeApp:verifyWebDistributionStripped :androidApp:assembleDebug :server:assemble` |
| ↳ same job, release hardening | A release APK or desktop jar that ships unobfuscated — minification turned off, a keep rule grown too broad ([docs/code-hardening.md](code-hardening.md)) | `./gradlew :androidApp:verifyReleaseObfuscation :composeApp:proguardReleaseJars` |
| **Build (iOS)** | iOS-only compile or link errors (macOS runner) | `./gradlew :composeApp:compileKotlinIosSimulatorArm64` on any host catches the Kotlin errors; only the link step needs a Mac: `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` |
| **CI passed** | Aggregates all of the above into one check | n/a |

## Make the checks required (one-time, in GitHub)

The workflow reports results; **GitHub only blocks merging if you require them**. This is a repository setting, not a file in the repo.

Settings → Rules → Rulesets → New branch ruleset, target `main`:

1. *Require a pull request before merging* (with at least 1 approval if you work in a team).
2. *Require status checks to pass* → add the single check **`CI passed`**. Because it aggregates every job, adding or renaming jobs later never needs a settings change.
3. *Block force pushes* and *Restrict deletions*.

Settings → Code security: turn on **Secret scanning** and **Push protection** (free for public repositories). Push protection rejects a push that contains a known token *before* it reaches the repository, which is stronger than CI finding it afterwards.

## How leaks are handled

- `gitleaks` scans only the commits a PR adds, with the maintained default ruleset plus `.gitleaks.toml`. Output is `--redact`ed so a leaked value is not copied into the public job log.
- **If a real secret is ever committed, deleting it in a later commit is not enough**: it stays in history and may already be cached or scraped. Revoke/rotate it at the provider first, then clean the history if needed.
- Allowlist entries in `.gitleaks.toml` must be narrow (path **and** value) and carry a comment. The one entry today is a documented fake key in an agent-skill example.
- A finding that is a false positive is best removed at the source. For a commit that is already pushed, `.gitleaksignore` clears one finding at a time by its `commit:file:rule:line` fingerprint, so an entry cannot hide anything else; each one carries a comment saying why it is safe.
- The workflow itself needs **no secrets**. Real secrets (`JWT_SECRET`, `DATABASE_PASSWORD`, `TWILIO_API_KEY_SECRET`) belong in the hosting platform's secret store, never in the repo.

## Security properties of the pipeline

- Triggers on `pull_request` only (never `pull_request_target`), so fork code never runs with repository secrets.
- `permissions: contents: read`; checkout does not persist credentials.
- Third-party actions are pinned to full commit SHAs, and the gitleaks binary is pinned to a version and verified against a SHA-256. Dependabot (`.github/dependabot.yml`) proposes updates weekly.
- Only `main` writes the Gradle build cache; pull requests can read it but cannot poison it.
- Event data (branch names, SHAs) is passed to scripts through environment variables, not interpolated into shell.

## Code style (ktlint)

Rules live in `.editorconfig` (IntelliJ/Android Studio read it too, so "Reformat code" and ktlint agree). The deliberate exceptions, each commented there:

- `@Composable` functions may be PascalCase.
- PascalCase properties are allowed for design-token objects (`SpaColors`).
- Wildcard imports are allowed for `io.ktor.**` only.
- The file-name rule is off (`main.kt` entry points, Koin files named after the module they hold).
- Generated sources under `build/` are never linted.

## Known limits (please read)

- **This pipeline has not run on GitHub yet.** The workflow passes `actionlint`, every action input was checked against its pinned `action.yml`, and each command was run locally, but the first real run may need small adjustments (most likely the Android SDK step or the iOS job).
- **The iOS job is unverified**: it needs a Mac and could not be run here. It is part of `CI passed`; if it proves flaky, remove it from `needs:` in the `ci-passed` job instead of weakening the others.
- macOS runners cost roughly ten times Linux minutes.
- Not included on purpose: dependency vulnerability scanning (GitHub's dependency review needs the dependency graph/Advanced Security for private repos; OWASP dependency-check is slow) and UI/instrumented tests.
- Updating a pinned action by hand: look up the tag's commit SHA (`git ls-remote --tags https://github.com/<owner>/<repo>`) and change the SHA and the version comment together.

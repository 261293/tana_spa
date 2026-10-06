## What and why

<!-- What does this change, and why is it needed? Link the issue if there is one. -->

## How it was tested

<!-- Tests added or run, manual steps, screenshots for UI changes. -->

## Checklist

- [ ] `./gradlew ktlintCheck` passes (`./gradlew ktlintFormat` fixes most findings)
- [ ] `./gradlew jvmTest :server:test` passes
- [ ] No secrets, tokens, keys or real personal data in the diff, tests, docs or screenshots
- [ ] New configuration is read from environment variables and documented (never committed)
- [ ] Database changes are a new `V<n>__*.sql` migration (applied migrations are never edited)
- [ ] User-facing text is in string resources for every supported language

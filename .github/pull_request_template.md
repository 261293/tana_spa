## What and why

<!-- What does this change, and why is it needed? Link the issue if there is one. -->

## How it was tested

<!-- Tests added or run, manual steps, screenshots for UI changes. -->

## Checklist

- [ ] `./gradlew ktlintCheck` passes (`./gradlew ktlintFormat` fixes most findings)
- [ ] `./gradlew jvmTest` passes, and `npx supabase test db` for a change under `supabase/`
- [ ] No secrets, tokens, keys or real personal data in the diff, tests, docs or screenshots
- [ ] New secrets live in Edge Function secrets and are documented (never committed)
- [ ] Database changes are a new file in `supabase/migrations/` (applied migrations are never edited)
- [ ] User-facing text is in string resources for every supported language

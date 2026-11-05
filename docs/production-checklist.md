# Production checklist

Everything still outstanding before this project can ship a production web build, an Android
release to Google Play and an iOS release to the App Store. Each item says **where** the value
goes, because most of them are one line in a file that already exists.

Deeper guides, not repeated here: `docs/release-signing.md` (keystore, certificates, IPA export),
`docs/notifications.md` (Firebase, APNs), `docs/feature-flags.md`, `docs/phone-verification.md`,
`docs/local-development.md`, `docs/ci.md`, `docs/adr/0007-backend-on-supabase.md`.

---

## 0. Blockers — fix before any public deployment

| # | What | Where |
|---|------|-------|
| 0.1 | **No administrator exists** | section 1.4 |

### Backend

The apps call the Supabase project compiled into `Constants` (`jnlonvutjugxxxsfpeom`). Every
migration in `supabase/migrations/` and both Edge Functions are deployed there (2026-11-05), the
Vault holds `push_url` and `push_secret`, and `PUSH_WEBHOOK_SECRET` matches it. Real pushes still
wait on `FCM_SERVICE_ACCOUNT_JSON` and the `notifications` flag.

### 0.1 First administrator

Nothing is seeded. Accounts are created by signing up (always a `customer`) or, by an owner or
admin, through the app. The first administrator therefore has to be promoted by hand — see 1.4.

---

## 1. Backend (Supabase)

One project, on the free plan: Auth, the database with every rule as a function, and two Edge
Functions. There is no server to host. The free plan pauses a project that sees little activity
for about a week.

### 1.1 Database

Apply `supabase/migrations/` to the project in order, after `npx supabase test db` passes
locally. The first fourteen files are the history the project already had; the rest are the
Supabase backend. Never edit an applied migration.

### 1.2 Edge Functions and their secrets

| Function | Secrets | Notes |
|----------|---------|-------|
| `push` | `PUSH_WEBHOOK_SECRET` (required); `FCM_SERVICE_ACCOUNT_JSON` (for real pushes); `FCM_PROJECT_ID` (only when the key is shared between projects) | `verify_jwt = false` (`supabase/config.toml`): it is called by the database, and refuses any call without the shared secret. Without the FCM key it only logs |
| `accounts` | none of its own; it uses the project's service role key, which Supabase provides to every function | keeps JWT verification on |

The service role key must never leave Edge Function secrets — not into the app, the web bundle or
the admin page.

### 1.3 Push webhook (Vault)

The trigger on `notifications` reads two Vault secrets; with either missing it posts nothing and
the notification is still stored:

| Vault secret | Value |
|---|---|
| `push_url` | `https://<project-ref>.supabase.co/functions/v1/push` |
| `push_secret` | the same value as the function's `PUSH_WEBHOOK_SECRET` |

Both are stored on the project. To set the function secret, reveal `push_secret` under
Integrations → Vault in the dashboard, copy it, and add it as `PUSH_WEBHOOK_SECRET` under Edge
Functions → Secrets. Rotating it means changing both to the same new value.

### 1.4 Auth settings and the first administrator

| Setting | Value |
|---|---|
| Email provider, "Confirm email" | **off** — signing up signs you in |
| Minimum password length | **8** (`minimum_password_length`; matches `AuthValidation.MIN_PASSWORD_LENGTH`) |
| Phone provider (Twilio Verify) | **off** while `phone_otp_verification` is off; see `docs/phone-verification.md` |

Then sign up in the app with the administrator's address and run, in the SQL editor:

```sql
update public.users set role = 'admin' where email = '…';
```

Every later owner, staff or admin account can be created from the app.

### 1.5 Spa settings

`spa_settings.timezone` is seeded with `Asia/Ho_Chi_Minh`; it drives availability, "today", the
cancel window and reminder timing. The branch address is the visible placeholder `Đang cập nhật`
until the owner supplies it.

---

## 2. Web (Cloudflare Workers static assets)

`wrangler.jsonc` serves `composeApp/build/dist/wasmJs/productionExecutable` as static assets, with
single-page-application fallback, which also ships the admin page at `/admin/`.

```bash
./gradlew :composeApp:wasmJsBrowserDistribution
npx wrangler deploy
```

The page needs no backend setting: it uses the project URL compiled into `Constants`, and
`window.TANA_API_BASE_URL` in `index.html` stays commented out unless the page should talk to a
different project.

---

## 3. Android (Google Play)

| Item | Where | Status |
|------|-------|--------|
| Release keystore | `ANDROID_KEYSTORE_FILE`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`, or `androidApp/keystore.properties` | **Not set** — release builds are produced *unsigned* |
| `google-services.json` | `androidApp/google-services.json` | **Absent** — gitignored; without it the Firebase plugin is skipped and push stays off |
| `versionCode` / `versionName` | `androidApp/build.gradle.kts` | `1` / `1.0.0` — `versionCode` must increase on every upload |
| Play Store link | `AppStoreLinks.ANDROID` | **Empty** — the update gate stays inert |
| `applicationId` | `androidApp/build.gradle.kts` | `com.tana.spa` — cannot be changed after the first publish |

Build and verify:

```bash
./gradlew :androidApp:assembleRelease
./gradlew :androidApp:verifyReleaseObfuscation   # reports signed (v2/v3) or UNSIGNED
./gradlew :androidApp:bundleRelease              # .aab for Play
```

Keep `androidApp/build/outputs/mapping/release/mapping.txt` for every release — Play needs it to
deobfuscate crash reports. Full procedure: `docs/release-signing.md`.

Play also requires store-listing material that is not in this repository: icon, feature graphic,
screenshots, a privacy policy URL, a data-safety declaration and a content rating questionnaire.

---

## 4. iOS (App Store)

| Item | Where | Status |
|------|-------|--------|
| `TEAM_ID` | `iosApp/Configuration/Config.xcconfig` | **Empty** |
| Bundle identifier | same file | `com.tana.spa.Tana_spa$(TEAM_ID)` — see the warning below |
| `MARKETING_VERSION` / `CURRENT_PROJECT_VERSION` | same file | `1.0.0` / `1` — the build number must increase on every upload |
| `GoogleService-Info.plist` | `iosApp/iosApp/` | **Absent** — gitignored |
| APNs auth key | Firebase console | Uploaded to Firebase; it is not a backend setting |
| App Store link | `AppStoreLinks.IOS` | **Empty** — the update gate stays inert |

**Bundle identifier warning:** `PRODUCT_BUNDLE_IDENTIFIER` appends `$(TEAM_ID)`, so filling in the
team id produces an identifier like `com.tana.spa.Tana_spaABCDE12345`. That is a project-template
artefact. Decide the real bundle identifier before the first upload — it cannot be changed after.

Push also needs the *Push Notifications* capability and the *Remote notifications* background mode
enabled in Xcode; see `docs/notifications.md`. Signing certificates, provisioning profiles and the
IPA export are covered by `docs/release-signing.md`. Building requires macOS with Xcode.

App Store Connect additionally requires screenshots per device class, a privacy policy URL, an App
Privacy questionnaire and an export-compliance answer.

---

## 5. Feature flags

State resolves as per-user override → global database row → compiled default. A missing row means
"no opinion", not "off". Operate them at `/admin/` with an account whose `role` is `admin`.

| Flag | Compiled default | What it waits on |
|------|------------------|------------------|
| `phone_otp_verification` | **off** | The flow is unreliable; also needs the phone provider enabled in Auth |
| `notifications` | **off** | A configured Firebase project, or the bell lists nothing |
| `app_update_gate` | **on** | Inert anyway until `AppStoreLinks` are filled in |

---

## 6. Update gate configuration

`app_update_config` (migration V7) is seeded with `minimum_version = 1.0.0` and
`latest_version = 1.0.0`, which resolves to **no prompt**. Raise either value through `/admin/`
once a release is out. Both must match `^[0-9]+\.[0-9]+\.[0-9]+$`.

The gate fails open everywhere: no row, no network, no store link, or the flag off all mean no
prompt. That is deliberate — see `docs/adr/0003-app-update-gate.md`.

---

## 7. Incomplete content, not configuration

- `FeedRemoteApiImpl` returns hardcoded placeholder banners and sections, not backend data.
- The Explore, Likes, NewPost and Profile screens are placeholders.

# Production checklist

Everything still outstanding before this project can ship a production web build, an Android
release to Google Play and an iOS release to the App Store. Each item says **where** the value
goes, because most of them are one line in a file that already exists.

Deeper guides, not repeated here: `docs/release-signing.md` (keystore, certificates, IPA export),
`docs/notifications.md` (Firebase, APNs), `docs/feature-flags.md`, `docs/phone-verification.md`,
`docs/local-development.md`, `docs/ci.md`.

---

## 0. Blockers — fix before any public deployment

| # | What | Where |
|---|------|-------|
| 0.1 | **Seed accounts need their passwords configured** | `server/.../auth/AccountSeeder.kt` |
| 0.2 | **`JWT_SECRET` must be a real secret** | server environment |
| 0.3 | **No backend or database is deployed** | see section 1 |

### 0.1 Seed accounts (critical)

Two administrator accounts (`sanghtruong@tana.local`, `duchnguyen@tana.local`) are created on
every start-up, and the spa owner with them. Their passwords come from the environment:

| Variable | What it seeds |
|---|---|
| `SEED_ADMIN_PASSWORD` | the password of the two fixed administrators (required in production) |
| `SEED_ADMINS` | further administrators, `email\|Name\|password` separated by `;` |
| `SEED_OWNER` | the spa owner, `email\|Name\|password` (required in production) |

With `APP_ENV=production` the server refuses to start without `SEED_ADMIN_PASSWORD` and
`SEED_OWNER`. Outside production a missing password is generated per start and logged once.

Seeding never promotes an existing account: an address someone already registered keeps the role
it has, and a warning is logged.

Any password that was ever committed to this repository is public — treat it as compromised and
do not reuse it.

### 0.2 JWT secret

`JWT_SECRET` is required when `APP_ENV=production` and must be 32+ random characters. Generate one
per environment and never commit it:

```bash
openssl rand -base64 48
```

### 0.3 Backend and database

The app has no deployed backend. Until one exists, every client renders its UI and fails every
request. Section 1 lists the full environment.

---

## 1. Backend and database

The server is a Ktor fat jar. `server/Dockerfile` expects the jar to be **built outside the image**
(configuring `:server` pulls in `:shared`, which applies AGP and so needs the Android SDK), so a
host that builds from the repo Dockerfile will fail. Build the jar in CI, push an image to a
registry, and deploy that image.

```bash
./gradlew :server:buildFatJar
docker build -f server/Dockerfile -t tana-spa-server .
```

### Database URL format

`DATABASE_URL` must be a **JDBC** URL — `Database.kt` enforces
`require(jdbcUrl.startsWith("jdbc:postgresql:"))`. Managed Postgres providers hand out a libpq URL
(`postgresql://user:pass@host/db`), so split it into three variables:

```
DATABASE_URL=jdbc:postgresql://<host>/<database>?sslmode=require
DATABASE_USER=<user>
DATABASE_PASSWORD=<password>
```

### Server environment

| Variable | Required | Notes |
|----------|----------|-------|
| `APP_ENV` | yes | `production` — refuses the in-memory store, the dev OTP provider and a random JWT secret |
| `PORT` | host-set | Defaults to `Constants.SERVER_PORT` (8081) |
| `JWT_SECRET` | yes | 32+ random characters |
| `DATABASE_URL` / `DATABASE_USER` / `DATABASE_PASSWORD` | yes | JDBC form, see above |
| `DATABASE_POOL_SIZE` | no | Default 10 |
| `DATABASE_MIGRATE` | no | `false` to run Flyway as a separate deploy step |
| `CORS_ALLOWED_ORIGINS` | yes | Must include the deployed web origin, e.g. `https://tana-spa.vercel.app` |
| `TRUST_PROXY` | yes behind a proxy | `true` so rate limiting keys on the real client IP |
| `DEFAULT_PHONE_REGION` | no | Default `VN` |
| `SPA_TIMEZONE` | no | The house's own zone, used to place booking slots. Default `America/Los_Angeles` |
| `TWILIO_VERIFY_SERVICE_SID` / `TWILIO_API_KEY_SID` / `TWILIO_API_KEY_SECRET` | if OTP is on | Required when `APP_ENV=production`; unset means the dev provider that prints codes to the log |
| `OTP_ALLOWED_COUNTRIES` / `OTP_BLOCKED_COUNTRIES` | no | Comma-separated ISO codes; blocked wins over allowed |
| `FCM_SERVICE_ACCOUNT_JSON` *or* `FCM_SERVICE_ACCOUNT_FILE` | if push is on | Without it, notifications are stored but no device is woken |
| `FCM_PROJECT_ID` | no | Only when the key is shared between projects |

Memory note: on a 512 MB free tier, set `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75`.

---

## 2. Web (Vercel)

| Item | Where | Status |
|------|-------|--------|
| API base URL | `composeApp/src/webMain/resources/index.html` | **Not set** — falls back to `http://localhost:8081` |
| CORS origin | server `CORS_ALLOWED_ORIGINS` | Must list the deployed web origin |

Uncomment and fill the block in `index.html`, with no trailing slash:

```html
<script>window.TANA_API_BASE_URL = "https://your-api-host";</script>
```

Unset, the browser build uses `http://localhost:8081`, which is right for
`wasmJsBrowserDevelopmentRun` and wrong for a deployed site. An HTTPS page calling `http://` is
also blocked as mixed content, so the deployed API must be HTTPS.

Build and deploy. Vercel cannot build this project — its build image ships Java 11 and this build
needs 21 — so the bundle is built locally and the output directory uploaded:

```bash
./gradlew :composeApp:wasmJsBrowserDistribution
vercel deploy --prod --yes --cwd composeApp/build/dist/wasmJs/productionExecutable \
  --project tana-spa --scope tana13
```

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
| APNs auth key | Firebase console | Uploaded to Firebase; it is not a server setting |
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

State resolves as per-user override → global database row → `FEATURE_<KEY>` environment variable →
compiled default. A missing row means "no opinion", not "off". Operate them at `/admin/` with an
account whose `role` is `admin`.

| Flag | Compiled default | What it waits on |
|------|------------------|------------------|
| `phone_otp_verification` | **off** | The flow is unreliable; also needs the Twilio variables |
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

- `FeedRemoteApiImpl` returns hardcoded placeholder banners and sections, not server data.
- The Explore, Likes, NewPost and Profile screens are placeholders.
- The web build keeps the session in an in-memory cache, so a browser reload signs the user out.

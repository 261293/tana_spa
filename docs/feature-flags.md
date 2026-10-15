# Feature flags

Switches that take a feature out of service **without a deploy** — for everybody, or for one user
at a time. Built because phone-number verification is unreliable: sign-up should be able to skip
it today, and a tester should be able to turn it back on for themselves while a fix is checked.

## How a flag is resolved

Four sources, first one that has an opinion wins:

```
1. the user's override      feature_flag_overrides   "on for this tester only"
2. the global row           feature_flags            what operations set for everybody
3. the deploy default       FEATURE_<KEY> env var    what this environment ships with
4. the compiled default     FeatureFlag.defaultEnabled
```

A **missing row means "no opinion"**, not "off". That is what makes `DELETE` a real undo: clearing
the global row hands the decision back to the deployment rather than switching the feature off.

Every resolve reads the database. A flag gets flipped to stop something that is going wrong, so it
has to take effect on the next request, not when a cache expires. Both reads are primary-key
lookups on two tiny tables; add a short-lived cache in `FeatureFlagService` if traffic ever makes
that matter, and accept the delay that comes with it.

## The catalog

`shared/.../featureflags/FeatureFlags.kt` is the single list of flags, compiled into the apps
**and** the server, so neither side can invent a key the other does not know.

| Flag | Ships | What it gates |
|---|---|---|
| `phone_otp_verification` | **off** | `POST /auth/phone/otp/send` and `/verify`, and the "verify phone" screen sign-up leads into |
| `app_update_gate` | **on** | `GET /api/v1/app-update` and the force/optional update prompts. See `docs/adr/0003-app-update-gate.md` |
| `notifications` | **off** | every `/api/v1/notifications` route, the fan-out inside `NotificationService.send` (resolved for the *recipient*), the bell in the app bar and device registration. See `docs/notifications.md` |

Most flags are temporary switches, not architecture: delete the entry once the feature is
either healthy or gone for good, and the stored rows become "no opinion" and stop being read.
`app_update_gate` is the exception — a permanent kill switch, because it is the only remote way
to stop a bad `minimum_version` locking every installed app out. It ships **on**, which is safe
because a client that cannot read the config shows no prompt at all.

### Adding one

1. Add an entry to the `FeatureFlag` enum (key, `defaultEnabled`, description).
2. Inject `FeatureFlags` where the feature begins and branch on `isEnabled(flag)`.
3. On the server, gate the route: `call.refuseIfDisabled(services.featureFlags, FLAG, userId)`,
   which answers `503 feature_disabled` — a client that still offers the feature then skips it
   instead of showing the user an error.

No migration is needed: a flag with no rows follows its compiled default.

## Turning phone verification back on

```bash
# everybody, through the portal, or by hand:
curl -X PUT http://localhost:8081/api/v1/feature-flags/phone_otp_verification \
     -H "Authorization: Bearer <admin access token>" \
     -H 'Content-Type: application/json' -d '{"enabled":true}'

# one tester:
curl -X PUT http://localhost:8081/api/v1/feature-flags/phone_otp_verification/users/<user id> \
     -H "Authorization: Bearer <admin access token>" \
     -H 'Content-Type: application/json' -d '{"enabled":true,"note":"checking the Twilio fix"}'
```

If no administrator can sign in, SQL is the fallback:

```sql
UPDATE feature_flags SET enabled = true WHERE key = 'phone_otp_verification';
```

Clients pick the change up on their next refresh (app start, sign-in, or a navigation more than
five minutes after the last one). The server sees it on the very next request.

## The `/admin` portal

`http://localhost:8081/admin/` — one static page served by the Ktor server
(`server/src/main/resources/admin/index.html`): sign in, see every flag with its stored and
effective state, toggle globally, search a user by name/email/id and toggle for them alone.
Same origin as the API, so no CORS entry and no second deployable. Plain HTML and JavaScript,
meant to be replaced.

Signing in needs an account whose `role` is `admin`. Two are created on every start-up if their
email is free (`server/.../auth/AccountSeeder.kt`):

| Email | Password |
|---|---|
| `sanghtruong@tana.local` | `SEED_ADMIN_PASSWORD` |
| `duchnguyen@tana.local` | `SEED_ADMIN_PASSWORD` |

Set `SEED_ADMIN_PASSWORD` to choose it. Without it — only possible outside production, which
refuses to start without it — a password is generated per start and printed once in the log:

```
Nothing configured for the two fixed administrators: seeding with the generated password …
```

`admin` operates the system (feature flags, discount codes). Running the spa is the **owner's**
job and a separate role: see [`owner-role.md`](owner-role.md).

Seeding is idempotent and never touches an address that already exists, so an account somebody
registered as a customer is **not** silently promoted.

## API

| Request | Who | Answer |
|---|---|---|
| `GET /api/v1/feature-flags` | **anyone** | `{flags:{key:bool}}` resolved for the caller. A bearer token is optional; an unusable one is read as anonymous, never a 401. |
| `GET /api/v1/feature-flags/admin` | admin | every flag with `globalEnabled`, `deployDefault` and its overrides |
| `PUT /api/v1/feature-flags/{key}` `{enabled}` | admin | the admin view |
| `DELETE /api/v1/feature-flags/{key}` | admin | the admin view · `404` if nothing was stored |
| `PUT /api/v1/feature-flags/{key}/users/{userId}` `{enabled, note?}` | admin | the admin view · `404` for an unknown user |
| `DELETE /api/v1/feature-flags/{key}/users/{userId}` | admin | the admin view · `404` if that user had no override |
| `GET /api/v1/admin/users?q=&limit=` | admin | up to 50 accounts matching a name, email or id |

Reading is deliberately open: the sign-in screen needs to know which features exist before there
is a session, and a flag state is not a secret. Writing needs an admin's access token — no shared
operations token, so `updated_by` records **which** administrator made the change (their id, since
the column is capped at 100 characters and an email may be 254). A customer's token gets
`403 forbidden`; the role is re-read from the store on every call, so revoking it takes effect at
once rather than when the hour-long token expires.

## Clients

`core:featureflags` holds the client half. `FeatureFlagStore` implements `FeatureFlags` with
**synchronous** reads, because the code that asks — a view model deciding whether a step exists —
cannot wait for a network call. Reads come from a snapshot, filled in this order:

1. what the backend last resolved for the signed-in user;
2. what it resolved on an earlier run (cached in `core:cache`), so a cold start offline behaves
   like the last run did;
3. the compiled default.

A failed refresh therefore changes nothing: a flag that is off stays off rather than coming back
because the network dropped. `App.kt` calls `refreshIfStale()` on every destination change; the
store only goes to the network when the token changed (sign-in or sign-out) or the data is older
than five minutes.

The token comes from `AccessTokenSource`, a seam bound in `composeApp/di/AppModule.kt` — `core`
must not depend on `feature:Auth`, which owns the session.

## Schema

`V6__feature_flags.sql`:

- `feature_flags` — `key` (primary key), `enabled`, `description`, `updated_by`, timestamps.
- `feature_flag_overrides` — `(flag_key, user_id)` primary key, `enabled`, `note`, `updated_by`.
  `user_id` cascades on delete, so removing a user takes their overrides with them.

`flag_key` has deliberately **no** foreign key to `feature_flags`: an override must be settable
for a flag that has no global row yet, and a flag deleted from the code must not make these tables
unwritable. The key is validated against the catalog in the application instead — an unknown one
is `404 unknown_flag`, never a stored row nobody reads.

Without `DATABASE_URL` the server uses in-memory stores, so the portal works on a laptop with no
Docker; everything set through it is lost on restart, and the deploy defaults come back.

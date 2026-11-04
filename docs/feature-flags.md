# Feature flags

Switches that take a feature out of service **without a deploy** — for everybody, or for one user
at a time. Built because phone-number verification is unreliable: sign-up should be able to skip
it today, and a tester should be able to turn it back on for themselves while a fix is checked.

## How a flag is resolved

Three sources, first one that has an opinion wins:

```
1. the user's override      feature_flag_overrides   "on for this tester only"
2. the global row           feature_flags            what operations set for everybody
3. the compiled default     FeatureFlag.defaultEnabled / private.flag_catalog()
```

A **missing row means "no opinion"**, not "off". That is what makes clearing a real undo: removing
the global row hands the decision back to the compiled default rather than switching the feature
off. (The Ktor server had a fourth source, a `FEATURE_<KEY>` environment variable; there is no
server environment any more, so it is gone.)

Every resolve reads the database (`private.flag_enabled`). A flag gets flipped to stop something
that is going wrong, so it has to take effect on the next call, not when a cache expires. Both
reads are primary-key lookups on two tiny tables.

## The catalog

`shared/.../featureflags/FeatureFlags.kt` is the list the apps compile in;
`private.flag_catalog()` (`supabase/migrations/20261104162513_feature_flags.sql`) is the same list
in SQL, with the same compiled defaults. **Change both together**: a key missing from the SQL copy
is `404 unknown_flag` to the admin page, and a key missing from the Kotlin copy is ignored by the
apps.

| Flag | Ships | What it gates |
|---|---|---|
| `phone_otp_verification` | **off** | the "verify phone" screen sign-up leads into. Supabase Auth cannot read this table, so the SMS itself is stopped by keeping the phone provider disabled in the project; see `docs/phone-verification.md` |
| `app_update_gate` | **on** | the force/optional update prompts. See `docs/adr/0003-app-update-gate.md` |
| `notifications` | **off** | every notification function, the send to a recipient (resolved for the *recipient*), the bell in the app bar and device registration. See `docs/notifications.md` |

Most flags are temporary switches, not architecture: delete the entry once the feature is
either healthy or gone for good, and the stored rows become "no opinion" and stop being read.
`app_update_gate` is the exception — a permanent kill switch, because it is the only remote way
to stop a bad `minimum_version` locking every installed app out. It ships **on**, which is safe
because a client that cannot read the config shows no prompt at all.

### Adding one

1. Add an entry to the `FeatureFlag` enum (key, `defaultEnabled`, description), and the same row
   to `private.flag_catalog()` in a new migration.
2. Inject `FeatureFlags` where the feature begins and branch on `isEnabled(flag)`.
3. In the database, gate the function: `perform private.require_flag('<key>', uid);`, which raises
   `503 feature_disabled` — a client that still offers the feature then skips it instead of showing
   the user an error.

No table row is needed: a flag with no rows follows its compiled default.

## Turning phone verification back on

Through the admin page, or with SQL if no administrator can sign in:

```sql
insert into public.feature_flags (key, enabled, description)
values ('phone_otp_verification', true, 'Phone-number verification with SMS one-time codes')
on conflict (key) do update set enabled = excluded.enabled;
```

…and enable the phone provider in the project's Auth settings, or no code is ever sent.

Clients pick the change up on their next refresh (app start, sign-in, or a navigation more than
five minutes after the last one). The database functions see it on the very next call.

## The `/admin` portal

`/admin/` of the web build — one static page, `composeApp/src/webMain/resources/admin/index.html`,
that talks to the project with its public URL and publishable key: sign in, see every flag with
its stored and effective state, toggle globally, search a user by name/email/id and toggle for
them alone. It also edits the app update config. Plain HTML and JavaScript,
meant to be replaced. `window.TANA_API_BASE_URL` / `window.TANA_SUPABASE_KEY` point it at another
project.

Signing in needs an account whose `role` is `admin`. None is seeded: sign up, then promote the
account in SQL —

```sql
update public.users set role = 'admin' where email = '…';
```

`admin` operates the system (feature flags, discount codes). Running the spa is the **owner's**
job and a separate role: see [`owner-role.md`](owner-role.md).

## The functions

Called as `POST /rest/v1/rpc/<name>` with the arguments as a JSON body.

| Function | Who | Answer |
|---|---|---|
| `feature_flags()` | **anyone** (`anon` and `authenticated`) | `{flags:{key:bool}}` resolved for the caller; without a session, globals and defaults only |
| `admin_feature_flags()` | `OPERATE_SYSTEM` (admin) | every flag with `globalEnabled`, `deployDefault` (the compiled default) and its overrides |
| `set_feature_flag(key, enabled)` | admin | the admin view · `404 unknown_flag` for a key not in the catalog |
| `clear_feature_flag(key)` | admin | the admin view · `404` if nothing was stored |
| `set_feature_flag_for_user(key, userId, enabled, note?)` | admin | the admin view · `404` for an unknown user |
| `clear_feature_flag_for_user(key, userId)` | admin | the admin view · `404` if that user had no override |
| `admin_users(q, limit?)` | admin | up to 50 accounts matching a name, email or id |

Reading is deliberately open: the sign-in screen needs to know which features exist before there
is a session, and a flag state is not a secret. Writing needs an administrator's session, so
`updated_by` records **which** administrator made the change (their id). Any other role gets
`403 forbidden`; the role is re-read from `users` on every call, so revoking it takes effect at
once rather than when the hour-long access token expires.

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

`V6__feature_flags.sql` (`supabase/migrations/20261104101826_v6_feature_flags.sql`):

- `feature_flags` — `key` (primary key), `enabled`, `description`, `updated_by`, timestamps.
- `feature_flag_overrides` — `(flag_key, user_id)` primary key, `enabled`, `note`, `updated_by`.
  `user_id` cascades on delete, so removing a user takes their overrides with them.

`flag_key` has deliberately **no** foreign key to `feature_flags`: an override must be settable
for a flag that has no global row yet, and a flag deleted from the code must not make these tables
unwritable. The key is validated against the catalog in the functions instead — an unknown one
is `404 unknown_flag`, never a stored row nobody reads. Like every table, both have RLS enabled
with no policy; only the functions above read or write them.

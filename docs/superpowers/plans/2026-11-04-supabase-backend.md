# Backend on Supabase Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Ktor server with Supabase (Auth, Postgres functions, two Edge Functions, pg_cron) so the whole app runs on free tiers, reprice the seed in dong, then delete `server/`.

**Architecture:** Every endpoint the Ktor server had becomes a Postgres function in `public` (called as `POST /rest/v1/rpc/<name>`) that returns JSON in the exact shape of the existing `shared/` DTO, or a Supabase Auth call (`/auth/v1/...`). Internals live in a `private` schema that PostgREST does not expose. Two Edge Functions do what SQL cannot: `push` (FCM) and `accounts` (Auth admin API). The client keeps its Ktor stack, session store and refresh logic and only changes URLs, adds the `apikey` header, and maps Supabase Auth errors.

**Tech Stack:** Postgres 17 (Supabase), PL/pgSQL, pgTAP via `supabase test db`, Supabase CLI 3.x through `npx supabase`, Deno + TypeScript Edge Functions, Kotlin Multiplatform client (Ktor 3.6.0, kotlinx.serialization).

**Spec:** `docs/superpowers/specs/2026-11-04-supabase-backend-design.md`

## Deviations from the spec (decided while planning; the owner reviews them with this plan)

1. **No `supabase-kt`.** Supabase Auth and PostgREST are plain REST, and the client already has Ktor with refresh-on-401, a refresh mutex and a session store. Switching URLs keeps all of that and removes the unverified Ktor 3.6 / supabase-kt 3.4 pairing.
2. **No direct table reads.** Every read goes through a `security definer` function too, so no table gets an RLS policy and the Data API stays fully closed. One rule (functions check everything) instead of two (functions plus per-table policies).
3. **Error responses keep their HTTP status and body shape.** Functions raise `sqlstate 'PGRST'`, which lets PostgREST answer with the Ktor status (404, 409, 403, 503, …) and a body `{"code": "<error code>", "message": "…"}`, the same fields `ErrorResponse` decodes today.
4. **The apps always talk to the remote project.** The spec wanted debug builds on the local stack, but common code has no debug/release flag and adding one is new build logic. The local stack is for the database and Edge Function tests; the web build can still be pointed anywhere through `window.TANA_API_BASE_URL`.
5. **`SpaValidation.MAX_PRICE` becomes 100_000_000 (dong).** It is 100_000 today, a dollar ceiling; in dong the owner could not add any treatment.
6. **Admin send (`NotificationApi.SEND`) answers `delivered = 0, removed = 0`.** Push becomes asynchronous through the webhook, so the counts are not known when the function returns. No client calls this endpoint today.

## Global Constraints

- Remote project ref `jnlonvutjugxxxsfpeom`, region `ap-southeast-1`, Postgres 17.11, org "Tana Spa", free plan.
- Spa timezone `Asia/Ho_Chi_Minh`, stored in `spa_settings`, never hard-coded in a function.
- Money is whole dong in every `*_vnd` column and every DTO amount; the client formats with `formatVnd` in every language.
- Every function in `public`: `security definer`, `set search_path = ''`, every object schema-qualified, `revoke execute ... from public`, then `grant execute` to the named roles only (`anon` only where the Ktor route was unauthenticated).
- Every new table: `alter table ... enable row level security`, no policy.
- Error codes are the strings already in `shared/` (`AuthErrorCodes`, `BookingErrorCodes`, `AccountErrorCodes`), with the HTTP status the Ktor route used.
- Language: functions read `Accept-Language` from `current_setting('request.headers', true)`; a value starting with `vi` means Vietnamese, anything else (or none) means English. The client already sends it on every call.
- Function parameters are named exactly like the fields of the existing request DTOs (quoted camelCase, e.g. `"serviceSlug"`), so the client posts the DTO it already has as the body.
- Permission matrix (mirror of `shared/.../auth/SpaPermissions.kt`): admin = all of `MANAGE_BOOKINGS, MANAGE_SERVICES, MANAGE_THERAPISTS, OPERATE_SYSTEM, MANAGE_ACCOUNTS`; owner = `MANAGE_BOOKINGS, MANAGE_SERVICES, MANAGE_THERAPISTS, MANAGE_ACCOUNTS`; staff = `MANAGE_BOOKINGS`; customer = none. Creatable: admin → admin/owner/staff/customer; owner → staff/customer. `canChangeRole(actor, from, to) = canCreate(actor, from) and canCreate(actor, to)`.
- Flag compiled defaults (mirror of `FeatureFlags.kt`): `phone_otp_verification` false, `app_update_gate` true, `notifications` false.
- New files use LF line endings. No code comments except for a security or ordering constraint. Commit messages carry no AI attribution.
- All Supabase CLI commands run from the repo root as `npx supabase ...`. The Supabase MCP tools load only when Claude Code is started in `D:\development\kmp\spa_tana`.

## Review Focus

1. **No `Accept-Language`, or `en-US`:** every text field falls back to the English column; owned by Task 5 (`catalog_without_language_is_english`).
2. **"Today" between 00:00 and 07:00 in Vietnam:** the UTC date is still yesterday, so `todayEpochDay`, the default availability day and the staff queue's day must come from the house zone; owned by Task 5 (`house_day_after_midnight_vietnam`).
3. **An expired access token on an RPC:** PostgREST answers 401 and the client must refresh once through `/auth/v1/token?grant_type=refresh_token` and retry; owned by Task 13 (`expired_token_on_rpc_refreshes_once`).
4. **Phone typed as `0912 345 678`, `+84912345678` or `84912345678`:** all normalise to `+84912345678`; owned by Task 3 (`normalize_phone_shapes`).
5. **The owner adds a treatment priced 4.125.000:** accepted after the MAX_PRICE change; owned by Task 7 (`create_service_accepts_dong_price`).

---

### Task 1: Local Supabase stack and the schema history in the repo

**Files:**
- Create: `supabase/config.toml` (via `npx supabase init`), `supabase/migrations/<version>_<name>.sql` × 14, `supabase/tests/database/_helpers.psql`, `supabase/tests/database/000_history.test.sql`
- Modify: `.gitignore` (add `supabase/.branches`, `supabase/.temp`, `supabase/functions/.env`)

**Interfaces:**
- Produces: `_helpers.psql` defines, inside the test's transaction, `pg_temp.new_user(role text default 'customer', email text default null) returns uuid` (inserts into `auth.users`, returns the id; the Task 3 trigger creates the `public.users` row), `pg_temp.as_user(uid uuid)` (sets `request.jwt.claims` to `{"sub": uid, "role": "authenticated"}` and `set local role authenticated`), `pg_temp.as_anon()`, `pg_temp.as_postgres()` (`reset role`). Until Task 3 exists, `new_user` also inserts the `public.users` row itself; Task 3 removes that line.

- [ ] **Step 1: Install Deno and initialise the project**

Run: `winget install --id DenoLand.Deno --exact --silent --accept-package-agreements --accept-source-agreements` then `npx supabase init` in the repo root.
In `supabase/config.toml` set `project_id = "tana-spa"`, `[db] major_version = 17`, `[auth] site_url = "https://tana-spa-web.spa-tana.workers.dev"`, `[auth.email] enable_confirmations = false`, `[auth.sms] enable_signup = false`, and `[auth.sms.test_otp] "84900000001" = "123456"` for local OTP tests.

- [ ] **Step 2: Write the remote history into `supabase/migrations/`**

With the Supabase MCP, run `select version, name, statements from supabase_migrations.schema_migrations order by version` on `jnlonvutjugxxxsfpeom` and write each row to `supabase/migrations/<version>_<name>.sql`, statements joined with `;\n` (14 files: `v1_foundation_and_identity` … `v12_offers_vietnamese`, `lock_down_data_api`, `pin_function_search_path`).

- [ ] **Step 3: Write the failing history test**

`000_history.test.sql` (`\ir _helpers.psql` at the top, `begin; select plan(4); … select * from finish(); rollback;`):
```sql
select is((select count(*)::int from pg_tables where schemaname = 'public'), 32, 'schema_has_32_tables');
select is((select count(*)::int from pg_tables where schemaname = 'public' and not rowsecurity), 0, 'every_table_has_rls');
select ok(not has_table_privilege('anon', 'public.users', 'select'), 'anon_cannot_read_users');
select lives_ok($$select pg_temp.as_user(pg_temp.new_user())$$, 'helpers_load');
```

- [ ] **Step 4: Run it**

Run: `npx supabase start` then `npx supabase test db`
Expected: PASS, 4 tests. If `\ir _helpers.psql` fails to load under `pg_prove`, inline the helper block at the top of each test file instead and note it in the commit.

- [ ] **Step 5: Prove the files equal the remote**

Save this as `supabase/tests/fingerprint.sql` (it is the query that compared the remote with the repo's original Flyway files during the design session):
```sql
select kind, count(*) as n, md5(string_agg(x, E'\n' order by x)) as hash from (
 select 'columns', table_name||'|'||column_name||'|'||data_type||'|'||is_nullable||'|'||coalesce(column_default,'')||'|'||coalesce(generation_expression,'') from information_schema.columns where table_schema='public'
 union all select 'constraints', conrelid::regclass::text||'|'||conname||'|'||pg_get_constraintdef(oid) from pg_constraint where connamespace='public'::regnamespace
 union all select 'indexes', indexname||'|'||indexdef from pg_indexes where schemaname='public'
 union all select 'triggers', c.relname||'|'||t.tgname||'|'||pg_get_triggerdef(t.oid) from pg_trigger t join pg_class c on c.oid=t.tgrelid where not t.tgisinternal and c.relnamespace='public'::regnamespace
 union all select 'views', viewname||'|'||definition from pg_views where schemaname='public'
 union all select 'functions', p.proname||'|'||pg_get_function_identity_arguments(p.oid)||'|'||md5(p.prosrc) from pg_proc p where p.pronamespace='public'::regnamespace and not exists (select 1 from pg_depend d where d.objid=p.oid and d.deptype='e')
 union all select 'seed', 'svc|'||s.slug||'|'||c.slug||'|'||s.name||'|'||coalesce(s.name_vi,'')||'|'||coalesce(s.description,'')||'|'||coalesce(s.description_vi,'')||'|'||s.price_vnd||'|'||s.duration_minutes||'|'||s.is_featured||'|'||s.sort_order from services s join service_categories c on c.id=s.category_id
 union all select 'seed', 'thr|'||slug||'|'||name||'|'||focus||'|'||coalesce(focus_vi,'')||'|'||accent||'|'||sort_order from therapists
 union all select 'seed', 'vch|'||code||'|'||title||'|'||coalesce(title_vi,'')||'|'||coalesce(description_vi,'')||'|'||discount_type||'|'||discount_value||'|'||min_order_vnd||'|'||coalesce(usage_limit::text,'')||'|'||coalesce(array_to_string(eligible_categories,','),'') from vouchers
 union all select 'seed', 'brn|'||name||'|'||address_line||'|'||city||'|'||opens_at||'|'||closes_at||'|'||(select count(*) from branch_services) from branches
 union all select 'seed', 'flg|'||key||'|'||enabled||'|'||coalesce(description,'') from feature_flags
 union all select 'seed', 'upd|'||id||'|'||minimum_version||'|'||latest_version||'|'||force_update from app_update_config
) s(kind, x) group by kind order by kind;
```
Run: `docker exec -i supabase_db_tana-spa psql -U postgres -d postgres -A -t < supabase/tests/fingerprint.sql` (psql is not installed on the host; it runs inside the stack's database container).
Expected hashes: columns `5f97fa2e5e3a39c765883ee42a7b5454` (309), constraints `a888ea50e237782c9675e42e71f49e0e` (176), indexes `dae8c2bd5be759470e444a71e5de1319` (98), triggers `9b4591543b69d7ac8ff947980f68496a` (14), views `a4b5ac0d0fe2ae68ba7121356ac81aa7` (1), functions `36f5fd22f78763c6e9dc4062f5a1e960` (2), seed `33cba93a1a22d7169b6b2953d049b3ea` (14). These hold only at this task; later migrations change them on purpose.

- [ ] **Step 6: Commit**

```bash
git add supabase .gitignore
git commit -m "Supabase project in the repo, with the schema history already applied remotely"
```

---

### Task 2: Tana Spa in Hồ Chí Minh City, priced in dong

**Files:**
- Create: `supabase/migrations/<ts>_tana_spa_in_dong.sql`, `supabase/tests/database/010_seed.test.sql`
- Modify: `shared/src/commonMain/kotlin/com/tana/spa/shared/booking/SpaValidation.kt` (`MAX_PRICE = 100_000_000`)

**Interfaces:**
- Produces: table `public.spa_settings (id text primary key default 'global' check (id = 'global'), timezone text not null, updated_at timestamptz not null default now())`, RLS on, seeded `('global', 'Asia/Ho_Chi_Minh')`.

- [ ] **Step 1: Write the failing test**

```sql
select is((select name || '|' || city || '|' || address_line from public.branches), 'Tana Spa|TP. Hồ Chí Minh|Đang cập nhật', 'branch_is_tana_spa');
select results_eq($$select slug, price_vnd from public.services order by sort_order$$,
  $$values ('warm-stone-massage', 4125000::bigint), ('jade-roller-facial', 3000000), ('deep-tissue-massage', 3500000), ('matcha-body-polish', 2450000), ('herbal-compress-ritual', 3750000)$$, 'services_in_dong');
select is((select discount_value || '|' || min_order_vnd from public.vouchers where code = 'MIDWEEK15'), '375000|3000000', 'midweek15_in_dong');
select is((select title from public.vouchers where code = 'MIDWEEK15'), '375.000đ off weekday massages', 'midweek15_english_title');
select is((select description from public.vouchers where code = 'MIDWEEK15'), 'Tue-Thu appointments. Minimum spend 3.000.000đ.', 'midweek15_english_terms');
select is((select timezone from public.spa_settings), 'Asia/Ho_Chi_Minh', 'house_zone_is_vietnam');
```

- [ ] **Step 2: Run to see it fail** — `npx supabase test db` → FAIL on all six.
- [ ] **Step 3: Write the migration** (updates by slug/code, creates `spa_settings`; the `booking_items` snapshots are empty and need nothing).
- [ ] **Step 4: Run** — `npx supabase test db` → PASS. Then `./gradlew :shared:jvmTest` → PASS (`SpaValidation` tests that asserted 100_000 move to 100_000_000).
- [ ] **Step 5: Commit** — `git commit -m "Tana Spa in Ho Chi Minh City, the menu priced in dong"`

---

### Task 3: Identity on Supabase Auth

**Files:**
- Create: `supabase/migrations/<ts>_identity_on_supabase_auth.sql`, `supabase/tests/database/020_identity.test.sql`
- Modify: `supabase/tests/database/_helpers.psql` (drop the manual `public.users` insert)

**Interfaces:**
- Produces (schema `private`, `usage` granted to nobody but `postgres`):
  - `private.fail(code text, status int, message text default null) returns void` — `raise sqlstate 'PGRST' using message = json_build_object('code', code, 'message', coalesce(message, replace(code, '_', ' ')))::text, detail = json_build_object('status', status)::text`.
  - `private.caller() returns uuid` — `auth.uid()`, or `fail('unauthorized', 401)`.
  - `private.app_role(uid uuid) returns text`, `private.has_permission(uid uuid, permission text) returns boolean`, `private.require_permission(permission text) returns uuid` (401 `unauthorized` without a user, 403 `forbidden` without the permission).
  - `private.can_create(actor text, target text) returns boolean`.
  - `private.normalize_phone(raw text) returns text` — strip spaces, dots, dashes; `0xxxxxxxxx` → `+84xxxxxxxxx`; `84xxxxxxxxx` → `+84xxxxxxxxx`; anything already `+` stays; result must match `^\+[0-9]{8,15}$` or the function returns null.
  - `private.vietnamese() returns boolean`, `private.house_zone() returns text`.
  - `public.me() returns jsonb` — `UserDto` (`id, email, displayName, phone, phoneVerified, role`), granted to `authenticated`.
  - `public.accounts_change_role(id uuid, role text) returns jsonb` — `CreatedAccountDto`; errors as `AccountRoutes.kt` (403 `own_role_unchangeable`, 404 `unknown_account`, 403 `role_not_allowed`, 400 `invalid_request` for an unknown role); requires `MANAGE_ACCOUNTS`.
  - `public.admin_users(q text, "limit" int default 20) returns jsonb` — `AdminUsersResponse`, admin only (`OPERATE_SYSTEM`), empty list for a blank `q`, `limit` clamped to 1..50, match on `app_unaccent(full_name) ilike app_unaccent('%q%')` or `email ilike '%q%'`.
- Schema changes: `users.id` references `auth.users(id) on delete cascade`; drop `users.password_hash`, `refresh_tokens`, `phone_verifications`.
- Triggers on `auth.users`: after insert → create `public.users` (`email` lowercased, `full_name` from `raw_user_meta_data->>'full_name'`, `phone` from `normalize_phone(raw_user_meta_data->>'phone')`, role `customer`), then the welcome grant (Task 9's `private.grant_voucher(uid, 'FIRSTVISIT', 'gift')`; until Task 9 lands, call a stub that returns false) inside `begin … exception when others then null; end`. After update of `phone_confirmed_at` → copy `phone` (with a leading `+`) and `phone_confirmed_at` into `users.phone` / `users.phone_verified_at`.

- [ ] **Step 1: Write the failing tests** (`020_identity.test.sql`)

- `signup_creates_customer_profile`: after `pg_temp.new_user()` with metadata `{"full_name": "Lan", "phone": "0912345678"}`, `users` has role `customer`, `full_name` `Lan`, phone `+84912345678`, `phone_verified_at` null.
- `normalize_phone_shapes`: `'0912 345 678'`, `'84912345678'`, `'+84912345678'` → `'+84912345678'`; `'12'` → null.
- `me_returns_user_dto`: as that user, `public.me()` has `role` `customer`, `phoneVerified` false, `displayName` `Lan`.
- `me_requires_a_user`: as anon, `select public.me()` throws `PGRST`.
- `permission_matrix`: 20 rows (4 roles × 5 permissions) of `private.has_permission` equal the Global Constraints matrix.
- `own_role_unchangeable` (403), `unknown_account` (404), `owner_cannot_make_admin` (403 `role_not_allowed`), `owner_can_make_staff`, `customer_forbidden` (403 `forbidden`) for `accounts_change_role`.
- `confirmed_phone_copies_to_profile`: `update auth.users set phone = '84900000001', phone_confirmed_at = now()` → `users.phone` `+84900000001`, `phone_verified_at` not null.
- `anon_cannot_execute_private`: as anon, `select private.app_role(gen_random_uuid())` throws (permission denied for schema private).

- [ ] **Step 2: Run** — FAIL. **Step 3:** write the migration. **Step 4: Run** — `npx supabase test db` → PASS.
- [ ] **Step 5: Commit** — `git commit -m "Accounts live in Supabase Auth; the profile, roles and permissions stay in users"`

---

### Task 4: Feature flags

**Files:**
- Create: `supabase/migrations/<ts>_feature_flags.sql`, `supabase/tests/database/030_flags.test.sql`

**Interfaces:**
- Consumes: Task 3 helpers.
- Produces:
  - `private.flag_default(key text) returns boolean` (compiled defaults above; unknown key → null).
  - `private.flag_enabled(key text, uid uuid) returns boolean` — override for `uid` → global row → `flag_default`.
  - `private.require_flag(key text, uid uuid)` — `fail('feature_disabled', 503)` when off.
  - `public.feature_flags() returns jsonb` — `FeatureFlagsResponse` for the caller (globals only without a user); `anon, authenticated`.
  - `public.admin_feature_flags() returns jsonb` — `FeatureFlagAdminResponse`, `deployDefault` = `flag_default`.
  - `public.set_feature_flag(key text, enabled boolean) returns jsonb`, `public.clear_feature_flag(key text) returns jsonb`, `public.set_feature_flag_for_user(key text, "userId" uuid, enabled boolean, note text default null) returns jsonb`, `public.clear_feature_flag_for_user(key text, "userId" uuid) returns jsonb` — each returns the admin view; `OPERATE_SYSTEM`; statuses and codes as `FeatureFlagRoutes.kt` (`unknown_flag`, `forbidden`, unknown user, not stored).

- [ ] **Step 1: Failing tests** — `resolution_order` (override beats global beats default for each of the three keys), `anon_gets_globals_only`, `unknown_flag_is_404`, `customer_cannot_write_flags` (403), `flag_defaults_match_kotlin` (the three rows exactly).
- [ ] **Step 2: Run** FAIL. **Step 3:** migration. **Step 4: Run** PASS.
- [ ] **Step 5: Commit** — `git commit -m "Feature flags resolve in the database: override, global row, compiled default"`

---

### Task 5: Catalogue, offers, offer check, availability

**Files:**
- Create: `supabase/migrations/<ts>_spa_catalogue.sql`, `supabase/tests/database/040_catalogue.test.sql`

**Interfaces:**
- Produces (`anon, authenticated`):
  - `public.spa_catalog() returns jsonb` — `CatalogResponse`.
  - `public.spa_offers() returns jsonb` — `OffersResponse`.
  - `public.spa_check_offer(code text, "serviceSlug" text default null) returns jsonb` — `OfferCheckResponse`.
  - `public.spa_availability(day int default null, therapist text default null, service text default null) returns jsonb` — `AvailabilityResponse`; `day` null = today in the house zone.
- Produces (internal, used by Tasks 6–8): `private.house_day(at timestamptz) returns int` (epoch day of `at` in the house zone; every "today" uses `house_day(now())`), `private.service_dto(s public.services, vi boolean) returns jsonb`, `private.therapist_dto(t public.therapists, vi boolean, next_slot jsonb) returns jsonb`, `private.offer_dto(v public.vouchers, vi boolean) returns jsonb`, `private.offer_verdict(v public.vouchers, s public.services) returns table(discount bigint, refusal text)`, `private.slot_starts(day date) returns setof timestamptz`, `private.too_close(start_at timestamptz, end_at timestamptz, held_start timestamptz, held_end timestamptz) returns boolean`, `private.held(therapist_id uuid, day date) returns setof public.bookings`.
- Rules copied from `BookingService.kt`: slot times 09:00, 10:00, 11:30, 13:00, 14:30, 16:00, 17:30, 19:00 in the house zone; turnaround 15 minutes; `too_close` = `start_at < held_end + 15min and end_at + 15min > held_start`; next free slot scans 14 days; percent discount = `least((price * value + 50) / 100, price)`; fixed discount = `least(value, price)`; held = status in (`pending`, `confirmed`). Text fallback: `_vi` column when Vietnamese and not blank, else the base. `validUntil` = `endsAt` date in UTC, `MMM d, yyyy` (English) or `d 'thg' M, yyyy` (Vietnamese). Category slug → `MASSAGE`/`FACIAL`/`BODY` (unknown → `MASSAGE`); accent → `MOSS`/`DUSK`/`AMBER` (unknown → `MOSS`).

- [ ] **Step 1: Failing tests**
```sql
-- catalog_without_language_is_english
select is((public.spa_catalog()->'services'->0->>'name'), 'Warm Stone Massage');
-- catalog_in_vietnamese
select set_config('request.headers', '{"accept-language":"vi-VN"}', true);
select is((public.spa_catalog()->'services'->0->>'name'), 'Massage đá nóng');
select is((public.spa_catalog()->'services'->0->>'price')::int, 4125000);
-- offer_check: JADE20 on jade-roller-facial → accepted, discount 600000; JADE20 on warm-stone-massage → reason invalid_code; MIDWEEK15 on matcha-body-polish (2450000 < 3000000) → invalid_code; UNKNOWN → invalid_code; no service → reason unknown_service
-- availability_marks_held_slot: insert a pending booking for mai at day D 10:00 (90 min) → for service warm-stone-massage, 09:00 and 10:00 and 11:30 unavailable for mai, 13:00 available
-- availability_any_therapist_open_while_one_free
-- past_slots_unavailable
-- house_day_after_midnight_vietnam
select is(private.house_day('2026-11-04 18:30+00'), ('2026-11-05'::date - '1970-01-01'::date));
select is(private.house_day('2026-11-04 16:59+00'), ('2026-11-04'::date - '1970-01-01'::date));
```
- [ ] **Step 2: Run** FAIL. **Step 3:** migration. **Step 4: Run** PASS.
- [ ] **Step 5: Commit** — `git commit -m "The menu, the offers and the diary are read from database functions"`

---

### Task 6: Guest bookings

**Files:**
- Create: `supabase/migrations/<ts>_spa_guest_bookings.sql`, `supabase/tests/database/050_guest_bookings.test.sql`

**Interfaces:**
- Consumes: Task 5 internals.
- Produces (`authenticated`):
  - `public.spa_bookings() returns jsonb` — `BookingsResponse`, the caller's 50 latest by start.
  - `public.spa_create_booking("serviceSlug" text, "startAtEpochMillis" bigint, "therapistSlug" text default null, code text default null, note text default null) returns jsonb` — `BookingDto`.
  - `public.spa_cancel_booking(id uuid) returns jsonb` — `BookingDto`.
  - `private.booking_dto(b public.bookings, vi boolean, conflict text default null) returns jsonb` — reference `'BK-' || booking_no`; `startHour` and `startDayEpochDay` in the house zone; missing therapist → `{"slug":"","name":"","focus":"","accent":"MOSS","nextSlot":null}`.
- Rules copied from `BookingService.create/cancel`: start in the past or more than 60 days ahead → 400 `slot_in_past`; a start that is not one of the slot times → 409 `slot_taken`; named therapist not free → 409 `slot_taken`; "any" assigns the first therapist by `sort_order` who is free; a non-blank unusable code → 400 `invalid_code`; guest name/phone from `users` (`+10000000000` when the profile has none); branch = first active by `created_at`; `total = subtotal - discount`; insert the `booking_items` snapshot. **`pg_advisory_xact_lock(hashtext('spa_bookings'))` before the free check**, so two guests cannot take the same slot (security/ordering constraint: keep a one-line comment). Cancel: not found or another guest's → 404 (`unknown_booking` / `not_your_booking`); status not pending/confirmed → 409 `invalid_transition`; start within 120 minutes → 409 `too_late_to_cancel`.

- [ ] **Step 1: Failing tests** — `create_named_therapist`, `create_any_assigns_free_therapist`, `second_booking_same_slot_is_slot_taken`, `off_grid_start_is_slot_taken`, `past_start_is_slot_in_past`, `beyond_60_days_is_slot_in_past`, `bad_code_refuses_booking` (no row inserted), `code_discounts_total` (JADE20 on facial: subtotal 3000000, discount 600000, total 2400000), `booking_shape_in_house_zone` (the 14:30 slot of day D, i.e. 07:30 UTC: `startHour` 14.5, `startDayEpochDay` D, `reference` starts with `BK-`), `cancel_own`, `cancel_other_guest_is_404`, `cancel_inside_window_is_409`, `anon_cannot_book` (401).
- [ ] **Step 2: Run** FAIL. **Step 3:** migration. **Step 4: Run** PASS.
- [ ] **Step 5: Commit** — `git commit -m "Guests book and cancel through database functions, one booking per slot"`

---

### Task 7: The owner's desk and the menu

**Files:**
- Create: `supabase/migrations/<ts>_spa_owner_desk.sql`, `supabase/tests/database/060_owner_desk.test.sql`

**Interfaces:**
- Consumes: Task 6 `booking_dto`, Task 9 `private.notify` (until Task 9 lands, a stub returning null; Task 9 replaces it and adds the notification assertions).
- Produces (`authenticated`, permission-checked):
  - `public.spa_staff_queue() returns jsonb` — `StaffQueueResponse`; `MANAGE_BOOKINGS`; pending newest first, each with `conflict` = `'Overlaps <therapist> <h:mm AM> with BK-<n>'` when another held booking is `too_close`; scheduled = held bookings of today in the house zone.
  - `public.spa_confirm_booking(id uuid, force boolean default false) returns jsonb`, `public.spa_decline_booking(id uuid, reason text, "alternativeOffer" text default null) returns jsonb`, `public.spa_complete_booking(id uuid) returns jsonb`, `public.spa_no_show_booking(id uuid) returns jsonb` — `BookingDto`; `MANAGE_BOOKINGS`.
  - `public.spa_create_service(name text, description text, minutes int, price int, category text) returns jsonb` — `ServiceDto`, 201-equivalent; `MANAGE_SERVICES`.
  - `public.spa_create_therapist(name text, focus text, accent text default 'MOSS') returns jsonb` — `TherapistDto` with `nextSlot` null; `MANAGE_THERAPISTS`.
  - `private.slugify(raw text) returns text` — mirror of `SpaValidation.slugify` (letters and digits kept, Unicode letters included, everything else a single `-`, trimmed, max 80); unique by trying `slug`, `slug-2` … `slug-20`.
- Rules copied from `BookingService.confirm/decline/recordVisit` and `SpaValidation`: confirm/decline only from `pending` (else 409 `already_decided`), confirm with a clash and `force = false` → 409 `overlaps_another`; complete/no-show only from `confirmed` (409 `invalid_transition`) and not before the start (409 `visit_not_started`); invalid field → 400 `invalid_request` with the field name in `message`; limits name 80, description 240, focus 80, minutes 15..480 in steps of 15, price 0..100_000_000. Notifications: confirm → title `Booking confirmed`, body `<service> with <therapist> on <EEE MMM d>, <h:mm AM>`; decline → title `Time unavailable`, body per `reasonWord` plus ` We can offer <offer>.`; data `{"bookingId", "reference"}`, type `booking`.

- [ ] **Step 1: Failing tests** — `staff_can_confirm`, `customer_cannot_confirm` (403), `confirm_twice_is_already_decided`, `confirm_clash_needs_force`, `decline_stores_reason`, `complete_before_start_is_visit_not_started`, `no_show_after_start`, `create_service_accepts_dong_price` (price 4125000 accepted; a second "Warm Stone Massage" gets slug `warm-stone-massage-2`), `create_service_rejects_bad_minutes` (20 → 400 with `minutes`), `staff_cannot_create_service` (403), `create_therapist_vietnamese_name_slug` (`Ngọc Anh` → `ngọc-anh`, matching Kotlin's `isLetterOrDigit`).
- [ ] **Step 2: Run** FAIL. **Step 3:** migration. **Step 4: Run** PASS.
- [ ] **Step 5: Commit** — `git commit -m "The desk confirms, declines and records visits; the owner edits the menu"`

---

### Task 8: Discount codes for the administrator

**Files:**
- Create: `supabase/migrations/<ts>_spa_admin_codes.sql`, `supabase/tests/database/070_admin_codes.test.sql`

**Interfaces:**
- Produces (`authenticated`, `OPERATE_SYSTEM`):
  - `public.spa_admin_codes() returns jsonb` — `AdminCodesResponse`; `offer` = `'<n>% off'` or `'<formatted dong> off'` (`375.000đ off`, the `formatVnd` shape: dots every three digits, `đ` suffix); `eligible` = `All treatments` or slugs capitalised joined by ` - `; dates `MMM d` in UTC; `state` per `toAdminDto`.
  - `public.spa_create_code(code text, title text, "percentOff" int default null, "amountOff" int default null, "minSpend" int default null, eligible text[] default '{}', "startsAtEpochMillis" bigint default null, "endsAtEpochMillis" bigint default null, "usageLimit" int default null) returns jsonb` — exactly one of percent/amount (else 400 `invalid_request`), duplicate → 409 `duplicate_code`, then announce (Task 9's `private.announce(code)`, stub until then) and return the list.
  - `public.spa_disable_code(code text) returns jsonb` — 404 `invalid_code` when no live code.
- [ ] **Step 1: Failing tests** — `admin_lists_codes_in_dong`, `owner_cannot_list_codes` (403), `create_both_percent_and_amount_is_400`, `create_duplicate_is_409`, `disable_unknown_is_404`, `disabled_code_shows_disabled`.
- [ ] **Step 2–4:** FAIL → migration → PASS.
- [ ] **Step 5: Commit** — `git commit -m "The administrator publishes and retires discount codes"`

---

### Task 9: Notifications, welcome gift, announcements and reminders

**Files:**
- Create: `supabase/migrations/<ts>_notifications.sql`, `supabase/tests/database/080_notifications.test.sql`
- Modify: the stubs from Tasks 3, 7 and 8 are replaced by `create or replace` in this migration.

**Interfaces:**
- Produces:
  - `private.notify(uid uuid, type text, title text, body text, data jsonb default '{}') returns uuid` — null (nothing stored) when `notifications` is off for `uid`; validates per `NotificationValidation` (title 1..120, body ≤ 500, ≤ 16 data entries).
  - `private.grant_voucher(uid uuid, code text, source text) returns boolean` — `PromotionService.grant`: live code only, insert `user_vouchers` (repeat → false, silent), notify title `Welcome gift`, body `<title without trailing dot>. Use <CODE>.` plus ` Valid until <MMM d, yyyy>.` when it ends; type `promotion`, data `{"code"}`.
  - `private.announce(code text) returns int` — notify every active user, capped at 5000, title `New offer`.
  - `private.send_due_reminders() returns int` — claim confirmed bookings starting within 24 h with `reminder_sent_at is null` (one `update … returning`), notify title `Tomorrow at the spa`; release the claim (`reminder_sent_at = null`) when `notify` returned null.
  - `public.notifications_list("limit" int default null, before bigint default null) returns jsonb` — `NotificationListResponse`; page size 20 default, 50 max; `before` and `nextBefore` are epoch **microseconds** of `created_at`; flag-gated (503 `feature_disabled`).
  - `public.notifications_unread_count()`, `public.notifications_mark_read(id uuid)` (404 `notification_not_found` when not an unread own row), `public.notifications_mark_all_read()` — each returns `UnreadCountResponse`; flag-gated.
  - `public.register_device(token text, platform text, provider text default 'fcm', locale text default null, "appVersion" text default null) returns void` — flag-gated; upsert on `token` moves it to the caller.
  - `public.unregister_device(token text) returns void` — not flag-gated.
  - `public.admin_send_notification("userId" uuid, title text, type text default 'system', body text default null, data jsonb default '{}') returns jsonb` — `NotificationSendResponse` with `devices` = the recipient's token count, `delivered` 0, `removed` 0; 404 for an unknown user; 503 `feature_disabled` when off for the recipient; `OPERATE_SYSTEM`.
  - `pg_cron` job `spa-reminders`, schedule `0 * * * *`, `select private.send_due_reminders()`.

- [ ] **Step 1: Failing tests** — `notify_refused_when_flag_off`, `signup_grants_firstvisit` (user_vouchers row exists; notification row only when the flag is on via a global row), `confirm_writes_notification` (re-run of Task 7 confirm with the flag on), `reminder_claims_once` (two runs → one notification), `reminder_released_when_flag_off`, `list_pages_by_micros`, `mark_read_other_users_is_404`, `register_moves_token_between_users`, `unregister_works_with_flag_off`.
- [ ] **Step 2–4:** FAIL → migration → PASS.
- [ ] **Step 5: Commit** — `git commit -m "Notifications, the welcome gift, offer announcements and hourly reminders in the database"`

---

### Task 10: App update gate

**Files:**
- Create: `supabase/migrations/<ts>_app_update.sql`, `supabase/tests/database/090_app_update.test.sql`

**Interfaces:**
- Produces: `public.app_update_config() returns jsonb` (`anon, authenticated`; 503 `feature_disabled` when `app_update_gate` is off globally; 404 when there is no row, as `AppUpdateRoutes.kt`), `public.save_app_update_config("minimumVersion" text, "latestVersion" text, "forceUpdate" boolean) returns jsonb` (`OPERATE_SYSTEM`; versions `^[0-9]+\.[0-9]+\.[0-9]+$` after normalising like `AppVersion.parseOrNull`; minimum above latest → 400 as the Ktor route).
- [ ] **Step 1–4:** tests `config_is_public`, `gate_off_is_503`, `minimum_above_latest_is_400`, `customer_cannot_save` → FAIL → migration → PASS.
- [ ] **Step 5: Commit** — `git commit -m "The app-update gate is served by the database"`

---

### Task 11: Edge Function `push` and its webhook

**Files:**
- Create: `supabase/functions/push/index.ts`, `supabase/functions/push/fcm.ts`, `supabase/functions/push/fcm_test.ts`, `supabase/migrations/<ts>_push_webhook.sql`
- Modify: `supabase/config.toml` (`[functions.push] verify_jwt = false`)

**Interfaces:**
- Produces: `fcm.ts` exports `accessToken(serviceAccount: ServiceAccount, fetchFn = fetch): Promise<string>` (RS256 JWT signed with WebCrypto, `scope` `https://www.googleapis.com/auth/firebase.messaging`, exchanged at the account's `token_uri`) and `send(token: string, message: PushMessage, projectId: string, bearer: string, fetchFn = fetch): Promise<"delivered" | "unregistered" | "failed">` (FCM HTTP v1; `UNREGISTERED` or 404 → `unregistered`; Android channel `tana_default`, priority `HIGH`, as `FcmPushSender.kt`).
- `index.ts`: refuses any request whose `x-push-secret` header differs from env `PUSH_WEBHOOK_SECRET` (401); reads `record` from the webhook body; loads the recipient's `device_tokens` with the service role (`SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY`, both provided by the platform); without `FCM_SERVICE_ACCOUNT_JSON` it logs and returns 200; deletes `unregistered` tokens; stamps `notifications.sent_push_at` when at least one was delivered.
- Migration: `after insert on public.notifications` trigger calling `net.http_post` with the URL and secret read from Vault (`vault.decrypted_secrets` names `push_url`, `push_secret`); no secret stored → no call.

- [ ] **Step 1: Failing Deno tests** (`deno test supabase/functions/push/`) — `rejects_wrong_secret`, `unregistered_token_is_deleted`, `no_service_account_only_logs`, `jwt_has_firebase_scope` (decode the assertion sent to the stubbed token endpoint).
- [ ] **Step 2–4:** FAIL → implement → PASS. Then `npx supabase functions serve push` and post a fake webhook body with the local secret → 200.
- [ ] **Step 5: Commit** — `git commit -m "Push is an Edge Function woken by every new notification"`

---

### Task 12: Edge Function `accounts`

**Files:**
- Create: `supabase/functions/accounts/index.ts`, `supabase/functions/accounts/rules.ts`, `supabase/functions/accounts/rules_test.ts`, `supabase/functions/accounts/index_test.ts`

**Interfaces:**
- Produces: `POST /functions/v1/accounts` with body `CreateAccountRequest` (`displayName, email, password, role`) and the caller's bearer token → 201 `CreatedAccountDto`. `rules.ts` exports `canCreate(actor: string, target: string): boolean` and `hasManageAccounts(role: string): boolean` (Global Constraints matrix).
- Behaviour: caller from `auth.getUser(jwt)` (401 `unauthorized`); caller's role from `public.users` with the service role; no `MANAGE_ACCOUNTS` → 403 `forbidden`; `!canCreate` → 403 `role_not_allowed`; unknown role → 400 `invalid_request`; `auth.admin.createUser({ email, password, email_confirm: true, user_metadata: { full_name } })`, `email_exists` → 409 `email_taken`; then `update public.users set role` and answer. Body of every error: `{"code", "message"}`.
- [ ] **Step 1: Failing tests** — `rules_test.ts`: the full 4×4 caller/target matrix; `index_test.ts` against the local stack (`npx supabase functions serve accounts`): `owner_creates_staff`, `owner_cannot_create_admin`, `customer_is_forbidden`, `duplicate_email_is_409`, `no_token_is_401`.
- [ ] **Step 2–4:** FAIL → implement → PASS.
- [ ] **Step 5: Commit** — `git commit -m "Owners and administrators create accounts through an Edge Function"`

---

### Task 13: Client: point the network layer at Supabase, and sign in with Supabase Auth

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/tana/spa/shared/Constants.kt` (`apiBaseUrl` returns `https://jnlonvutjugxxxsfpeom.supabase.co` for every platform unless `configuredApiBaseUrl()` says otherwise; add `const val SUPABASE_PUBLISHABLE_KEY = "sb_publishable_OH8tYugWOYtJEynk0AElHA_rB18Sy3t"`; drop `SERVER_PORT`), `core/network/.../NetworkClient.kt` (`DefaultRequest` also sets `apikey`), `feature/Auth/.../data/remote/source/AuthRemoteApiImpl.kt`, `feature/Auth/src/commonTest/...` (its MockEngine tests)
- Create: `shared/src/commonMain/kotlin/com/tana/spa/shared/SupabasePaths.kt`

**Interfaces:**
- Produces: `object SupabasePaths { fun rpc(name: String): String = "/rest/v1/rpc/$name"; const val SIGN_UP = "/auth/v1/signup"; const val PASSWORD_GRANT = "/auth/v1/token?grant_type=password"; const val REFRESH_GRANT = "/auth/v1/token?grant_type=refresh_token"; const val LOGOUT = "/auth/v1/logout"; const val USER = "/auth/v1/user"; const val VERIFY = "/auth/v1/verify"; fun function(name: String): String = "/functions/v1/$name" }`.
- `AuthRemoteApiImpl` keeps the `AuthRemoteApi` interface. `login`/`register`/`refresh` call Auth, then `rpc("me")` with the new access token, and return `AuthResponse(accessToken, expiresInSeconds = expires_in, user, refreshToken, refreshExpiresInSeconds = 30 days)`. `register` sends `{"email", "password", "data": {"full_name", "phone"}}`. Supabase Auth revokes a session with its **access** token, not its refresh token, so `AuthRemoteApi.logout(refreshToken: String)` becomes `logout(accessToken: String)` and `AuthRepository.revokeRefreshToken(refreshToken)` becomes `revokeSession(accessToken: String)`; the sign-out caller captures the access token before it clears the local session (clear first, revoke after, as today). `sendPhoneOtp` = `PUT USER {"phone": normalized}` → `PhoneOtpSendResponse(resendAfterSeconds = 60, expiresInSeconds = 3600, maskedPhone)`; `verifyPhoneOtp` = `POST VERIFY {"type": "phone_change", "phone", "token"}` then `rpc("me")`.
- Error mapping (Auth body `error_code`, falling back to `code`): `email_exists`/`user_already_exists` → `email_taken`; `phone_exists` → `phone_claimed` (OTP) or `phone_taken` (sign-up); `invalid_credentials` → `invalid_credentials`; `over_request_rate_limit`, `over_sms_send_rate_limit` → `too_many_requests`; `refresh_token_not_found`, `refresh_token_already_used` → `invalid_refresh_token`; `otp_expired` → `otp_expired`; `sms_send_failed` → `otp_unavailable`; `phone_provider_disabled` → `feature_disabled`; `weak_password`, `validation_failed` → `invalid_request`.
- A shared Kotlin `normalizePhone(raw: String): String?` in `shared/.../auth/AuthValidation.kt` with the same rule as `private.normalize_phone`.

- [ ] **Step 1: Failing tests** (MockEngine) — `login_posts_password_grant_then_me`, `register_sends_full_name_and_phone_metadata`, `email_exists_maps_to_email_taken`, `every_request_carries_apikey`, `expired_token_on_rpc_refreshes_once` (first `rpc` 401, then `REFRESH_GRANT`, then the retried `rpc` succeeds), `normalize_phone_shapes` (same four cases as Task 3).
- [ ] **Step 2: Run** — `./gradlew :feature:Auth:jvmTest :shared:jvmTest` → FAIL.
- [ ] **Step 3:** implement. **Step 4: Run** → PASS. Then the gate the project uses: `./gradlew ktlintCheck jvmTest :composeApp:compileKotlinWasmJs`.
- [ ] **Step 5: Commit** — `git commit -m "The app signs in with Supabase Auth and talks to the Supabase project"`

---

### Task 14: Client: every other remote call becomes an RPC

**Files:**
- Modify: `core/featureflags/.../FeatureFlagRemoteApi.kt`, `core/notifications/.../data/NotificationRemoteApi.kt`, `feature/Admin/.../data/DeskRepositoryImpl.kt`, `feature/AppUpdate/.../data/AppUpdateRemoteApi.kt`, `feature/Booking/.../data/GuestBookingRepositoryImpl.kt`, `feature/Booking/.../data/SpaCatalogRepositoryImpl.kt`, and their tests.

**Interfaces:**
- Consumes: Task 13 `SupabasePaths`; the function names and parameter names of Tasks 3–10 and Task 12.
- Every call becomes `POST baseUrl + SupabasePaths.rpc(<name>)` with the existing request DTO (or a `buildJsonObject` of the query parameters, using the function's parameter names) as the body, decoding the same response DTO. `DeskRepositoryImpl.createAccount` posts to `SupabasePaths.function("accounts")`. `register_device` and `unregister_device` answered 204 under Ktor; a `void` function may answer 200 with an empty body or 204 depending on the PostgREST version, so the client treats any 2xx as success there.
- Route → function: `CATALOG` → `spa_catalog`, `OFFERS` → `spa_offers`, `OFFER_CHECK` → `spa_check_offer`, `AVAILABILITY` → `spa_availability` (`day`, `therapist`, `service`), `BOOKINGS` GET → `spa_bookings`, POST → `spa_create_booking`, `cancel(id)` → `spa_cancel_booking`, `OWNER_BOOKINGS` → `spa_staff_queue`, `confirm/decline/complete/noShow` → `spa_confirm_booking`/`spa_decline_booking`/`spa_complete_booking`/`spa_no_show_booking` (body adds `id`), `OWNER_SERVICES` → `spa_create_service`, `OWNER_THERAPISTS` → `spa_create_therapist`, `ADMIN_CODES` → `spa_admin_codes`/`spa_create_code`/`spa_disable_code`, `FeatureFlagApi.RESOLVED` → `feature_flags`, `AppUpdateApi.CONFIG` → `app_update_config`, notifications → `notifications_list`/`notifications_unread_count`/`notifications_mark_read`/`notifications_mark_all_read`/`register_device`/`unregister_device`, `AccountsApi.role(id)` → `accounts_change_role`, `AdminApi.USERS` → `admin_users`.
- Then delete from `shared/` the route-path objects no client uses any more (`BookingApi` paths, `NotificationApi` paths, `FeatureFlagApi`, `AppUpdateApi`, `AccountsApi`, `AdminApi` paths, `AuthApi`); keep their DTOs, error codes and `BookingRules`. The compiler finds every leftover.

- [ ] **Step 1: Update the MockEngine tests** to expect the RPC URLs and bodies (one assertion per mapping row above) → run, FAIL.
- [ ] **Step 2:** implement. **Step 3: Run** — `./gradlew ktlintCheck jvmTest :composeApp:compileKotlinWasmJs` → PASS.
- [ ] **Step 4: Commit** — `git commit -m "Every remote call is a Supabase function call"`

---

### Task 15: Prices in dong in every language

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/tana/spa/shared/helpers/Money.kt` (delete `VND_PER_USD`, `usdToVnd`), `core/ui/src/commonMain/kotlin/com/tana/spa/core/ui/SpaMoneyFormat.kt` (`fun formatPrice(vnd: Int): String = formatVnd(vnd.toLong())`), `core/ui/src/commonTest/.../SpaMoneyFormatTest.kt`, `SpaPromoTextTest.kt`, the 21 call sites in `feature/Booking` that the compiler reports, and `BookingContract.kt`'s KDoc line about dollars.

- [ ] **Step 1: Failing tests** — `english_shows_dong` (`formatPrice(4_125_000)` in English → `4.125.000đ`), `vietnamese_shows_dong`, promo text for MIDWEEK15 reads `375.000đ`.
- [ ] **Step 2–3:** FAIL → implement → `./gradlew ktlintCheck jvmTest :composeApp:compileKotlinWasmJs` PASS.
- [ ] **Step 4: Commit** — `git commit -m "Prices are dong in every language"`

---

### Task 16: The admin page on Supabase, served with the web app

**Files:**
- Move: `server/src/main/resources/admin/index.html` → `composeApp/src/webMain/resources/admin/index.html`
- Modify: that page's `api(method, path, body)` helper and its login.

**Interfaces:**
- The page signs in with `POST /auth/v1/token?grant_type=password`, keeps the access token in memory, sends `apikey` and `Authorization`, and calls `admin_feature_flags`, `set_feature_flag`, `clear_feature_flag`, `set_feature_flag_for_user`, `clear_feature_flag_for_user`, `app_update_config`, `save_app_update_config`, `admin_users`, `me` through `/rest/v1/rpc/`. The Supabase URL and key come from the same `window.TANA_API_BASE_URL` override as the app, defaulting to the Constants values.
- [ ] **Step 1:** rewrite the helper and each call. **Step 2:** `./gradlew :composeApp:wasmJsBrowserDistribution`, serve `composeApp/build/dist/wasmJs/productionExecutable` with `python -m http.server`, open `/admin/`, sign in as a local admin (local stack + URL override), toggle a flag, and see it in `select * from feature_flags`.
- [ ] **Step 3: Commit** — `git commit -m "The admin page talks to Supabase and ships with the web app"`

---

### Task 17: Deploy to the remote project and smoke-test

**Files:**
- Modify: `composeApp/src/webMain/resources/index.html` (set `window.TANA_API_BASE_URL` to the project URL), `wrangler.jsonc` (unchanged unless the assets path moved)
- Create: `docs/supabase-operations.md` (the owner's checklist and runbook)

- [ ] **Step 1:** apply every migration after the 14 historical ones to `jnlonvutjugxxxsfpeom` with the MCP `apply_migration`, in order, one at a time (never in parallel: parallel calls collided on the version timestamp during the design session). Then `get_advisors` security → only the two accepted `extension_in_public` warnings and `rls_enabled_no_policy` infos remain.
- [ ] **Step 2:** deploy `push` and `accounts` with the MCP `deploy_edge_function`; set the secret `PUSH_WEBHOOK_SECRET` (random, generated in a command that does not print it); store `push_url` = `https://jnlonvutjugxxxsfpeom.supabase.co/functions/v1/push` and `push_secret` in Vault with `vault.create_secret`.
- [ ] **Step 3:** `docs/supabase-operations.md` lists what the owner must do in the dashboard: Authentication → Providers → Email: "Confirm email" **off**; Phone provider stays **off** until the OTP flag is turned on (then Twilio Verify credentials); Firebase service account JSON as `FCM_SERVICE_ACCOUNT_JSON` when push is wanted; creating the first admin (sign up in the app, then `update public.users set role = 'admin' where email = '…'`). Wait for the owner to confirm the email setting before Step 4.
- [ ] **Step 4:** `./gradlew :composeApp:wasmJsBrowserDistribution` and `npx wrangler deploy`; then smoke-test against production with a throwaway account: sign up, read the catalogue (prices in dong), book a slot, see it in `spa_bookings`, cancel it. Delete the throwaway user from Auth (cascades to `users`).
- [ ] **Step 5: Commit** — `git commit -m "The web app talks to the deployed Supabase project"`

---

### Task 18: Remove the Ktor server

**Files:**
- Delete: `server/`, `fly.toml`, `docker-compose.yml`'s `server` service and `app` profile, `docker/` if only the server used it.
- Modify: `settings.gradle.kts` (drop `include(":server")`), `.github/workflows/ci.yml` (unit tests run `./gradlew jvmTest`; build job drops the server jar; new job `supabase` runs `npx supabase start` and `npx supabase test db` and `deno test supabase/functions`), `CLAUDE.md`, `README.md`, `ARCHITECTURE.md`, `docs/architecture.md`, `docs/local-development.md`, `docs/ci.md`, `docs/production-checklist.md` (the Fly section goes; backend = Supabase), `docs/feature-flags.md` (no env layer), `docs/owner-role.md`, `docs/notifications.md`, `docs/phone-verification.md`.
- Create: `docs/adr/0007-backend-on-supabase.md` (supersedes `0006-refresh-tokens.md`; mark 0006 superseded).

- [ ] **Step 1:** delete and edit; `git grep -n "server/\|:server\|SERVER_PORT\|8081\|fly"` returns only historical ADR text.
- [ ] **Step 2:** `./gradlew ktlintCheck jvmTest :composeApp:compileKotlinWasmJs :androidApp:assembleDebug` → BUILD SUCCESSFUL; `npx supabase test db` → PASS.
- [ ] **Step 3: Commit** — `git commit -m "Remove the Ktor server: the backend is Supabase"`

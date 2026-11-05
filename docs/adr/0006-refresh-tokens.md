# 6. A rotating refresh token, so a reload is not a sign-out

Date: 2026-11-01

## Status

Superseded by [0007](0007-backend-on-supabase.md)

## Context

A sign-in produced one thing: a 1-hour HS256 access token. There was nothing to renew it with, so
every session ended after an hour wherever the guest was, and any 401 cleared the stored session
and sent them back to the form.

Three separate problems were being read as one "the app logs me out on refresh" bug:

1. **No renewal.** An hour after signing in, the next call 401s, `AuthRepositoryImpl` clears the
   session, and the guest is at sign-in. On the web, where a reload is ordinary, this is constant.
2. **`currentSession()` conflated two expiries.** It treated the access token's expiry as the end
   of the session, which is only true when there is nothing to renew with.
3. **A dev server restart invalidated everything.** `JWT_SECRET` is random per process when unset,
   so every token minted before a restart fails verification. Its own log line says so.

The schema already anticipated the fix. `refresh_tokens` has been in `V1` since the first
migration, with `family_id`, `replaced_by`, `revoked_at` and a `CHECK octet_length(token_hash) =
32` — a rotating-token design with reuse detection, fully specified and never wired up.

## Decision

**A sign-in issues both tokens.** `/register` and `/login` return the 1-hour access JWT plus an
opaque 30-day refresh token. The refresh token is 32 random bytes, base64url; only its SHA-256
reaches the database, so a reader of `refresh_tokens` cannot sign in as anybody.

**`POST /api/v1/auth/refresh` spends one and returns the next**, continuing the same family. The
old row keeps `replaced_by` pointing at its successor rather than being deleted, because a deleted
row cannot be recognised as a replay.

**A token presented twice revokes its whole family.** Either it was stolen and replayed or the
holder kept a copy; neither is a chain worth serving, and the real client will notice at its next
refresh. A replayed token and an unknown one get the same `401 invalid_refresh_token`: which it
was is the server's business, and the client's move is the same either way.

**`POST /api/v1/auth/logout` revokes the family** and is deliberately unauthenticated — the access
token it ends may already have expired, and the refresh token presented is itself the proof. An
unknown token answers `204` like any other, so this cannot be used to probe which tokens exist.

**The client refreshes once per refusal, inside the HTTP client.** `TokenRefresh` (core:network)
turns a 401 into a refresh and a single retry. Only calls that carried a bearer token are retried,
which is what keeps a wrong password at sign-in from looking like a stale token and what stops the
refresh call recursing into itself.

**Refreshing is single-flight.** This is the part that is easy to get wrong: rotation means two
concurrent refreshes spend the same token, the second looks exactly like a replay, and the guest
loses every device. `SessionRefreshCoordinator` holds a mutex and, once inside it, checks whether
somebody else already replaced the token the caller was holding.

**`currentSession()` now ends at the refresh token's expiry, not the access token's.** An expired
access token is ordinary and recoverable; a session is over when it can no longer be renewed. A
session stored before this change has no refresh token and still expires with its access token.

**Signing out clears locally first and tells the server afterwards.** The screen navigates to
sign-in on the next line and the form reads the session as it opens, so the clear cannot be inside
a coroutine. The revoke runs on an application-scoped coroutine and is allowed to fail.

**A daily sweep deletes rows past their expiry**, which is the only thing that stops the table
growing for ever, and loses nothing: an expired row can neither authorise nor incriminate.

## Consequences

A browser reload, an app restart and an hour away no longer end a session — as long as the server
keeps its state. Two caveats, both pre-existing and both logged at startup:

- Without `DATABASE_URL` the refresh tokens live in memory with the accounts, so a restart still
  signs everyone out. That is the in-memory store working as documented, not this feature failing.
- Without `JWT_SECRET` the access-token secret is random per process. Refresh tokens survive a
  restart because they are database rows rather than signatures, so with Postgres configured a
  restart now costs one extra round trip instead of a sign-out. `./gradlew :server:run` does not
  read `.env`; set `JWT_SECRET` in the environment to avoid the churn.

The 30 days is a decision about how long a lost phone stays signed in. There is no device list and
no "sign out everywhere", so the only ways to end a session early are signing out on the device
itself or deleting the rows.

Reuse detection has a false positive: a client that retries a refresh after a response is lost in
transit presents a token the server has already spent, and loses the session. The client-side
mutex removes the common cause; the remaining window is a dropped response, which costs one
sign-in. Deliberately not softened with a grace period, because a grace period is also how a
stolen token gets its second use.

Both tokens are stored in the same `KeyValueCache` as before — a plain SQLite file on the native
targets and `localStorage` in the browser. Neither is encrypted at rest, and the browser copy is
readable by any script the page runs; the refresh token now makes that a 30-day exposure rather
than an hour. Moving the native copy behind Keystore/Keychain and the web copy into a cookie the
page cannot read is still the production prerequisite recorded on `SessionStoreImpl`.

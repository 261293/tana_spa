# 1. Feature flags resolved per request, with per-user overrides

Date: 2026-10-10

## Status

Accepted

## Context

Phone-number verification shipped before it was dependable, and the only way to stop it leading
users into a broken flow was to build and deploy a new client. We wanted a switch that an operator
can flip while the system is running, plus a way to let one tester exercise a fix that everybody
else is still protected from.

Three things had to be decided together: where the state lives, how fresh a read has to be, and
who is allowed to change it.

## Decision

**State lives in PostgreSQL, in two tables** (`feature_flags`, `feature_flag_overrides`), with a
compiled catalog (`FeatureFlag`) shared by the clients and the server as the authority on which
flags exist. Resolution order is override → global row → `FEATURE_<KEY>` environment variable →
compiled default, and a missing row means "no opinion" rather than "off".

**The server reads the database on every resolve.** No cache.

**Writes are authorised by a user's role** (`users.role = 'admin'`), not by a shared operations
token, and the role is re-read from the store on each call.

**Reads are public.** `GET /api/v1/feature-flags` needs no token; a bearer token is used when
present, and an unusable one is treated as anonymity rather than a 401.

**Clients read a snapshot synchronously**, backed by a cache of the last known set, falling back
to the compiled defaults.

## Consequences

A flag change takes effect on the next request, which is the whole point of a kill switch — at the
cost of two primary-key lookups per resolve. If traffic ever makes that matter, a short-lived
cache goes in `FeatureFlagService`, and the delay it introduces has to be accepted explicitly.

"Missing means no opinion" makes `DELETE` an undo and keeps environment configuration meaningful,
but it also means you cannot switch a feature off by deleting a row — a mistake worth knowing
about when operating the portal.

Role-based writes mean `updated_by` records *which* administrator acted, and revoking someone's
role takes effect at once instead of when their token expires. The cost is that flags cannot be
flipped by an unattended script unless it holds an admin's credentials; a shared token can be
added back for that if CI ever needs it.

Public reads mean flag states are visible to anyone who can reach the server. They are product
configuration, not secrets, and the sign-in screen needs them before there is a session — but a
flag must therefore never be used to hide something whose *existence* is confidential.

Per-user overrides are keyed by user id, so they are useless for a brand-new account (its id does
not exist yet at sign-up); the global state covers that case.

## Alternatives considered

**A third-party service** (LaunchDarkly, Unleash, Firebase Remote Config) — rejected: another
vendor, another SDK per target (including wasmJs), and a network dependency on the sign-in path,
for a switch count we can count on one hand.

**Environment variables alone** — simplest, and still the fallback layer, but flipping one needs a
restart and cannot single out a user.

**A shared `X-Admin-Token` header** — built first, then removed. It authorises an action without
identifying an actor, and it is one guessable string away from letting anyone reconfigure the
product for every user.

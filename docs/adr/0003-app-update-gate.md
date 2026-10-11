# 3. The app-update gate fails open, and its flag is a kill switch

Date: 2026-10-11

## Status

Accepted

## Context

Nothing stopped an old build from running. There was no runtime version anywhere — Android declared
`versionName` in `androidApp/build.gradle.kts`, iOS declared `MARKETING_VERSION` in
`Config.xcconfig`, and neither was readable from common code — so a client with a stale contract
kept calling the API with no way to be told, and there was no channel to announce a release.

A version gate is unlike every other feature here: it is the only one that can make the app
unusable for everybody at once. A wrong `minimum_version`, a store link that does not resolve, or a
hard failure on an unreachable endpoint each produce the same outcome, and the people affected
cannot install the fix because the thing that would let them is the thing that is blocking them.
Most of the decisions below exist to remove that outcome rather than to add behaviour.

The original specification also had two undefined cases (a build exactly at the floor, and
`forceUpdate` with a build above the floor) and one bug: as written it showed an optional prompt to
users already running the newest version, since they satisfied "above the floor and not forced".

## Decision

**`latestVersion` decides whether anything shows; `forceUpdate` only escalates.**

```
current <  minimumVersion            -> forced
current <  latestVersion  & !force   -> optional
current <  latestVersion  &  force   -> forced
current >= latestVersion             -> nothing
```

Equality is not below, so a build exactly at the floor is not blocked. Versions compare segment by
segment as numbers (`AppVersion` in `shared`), never as text, so `1.10.0` is newer than `1.9.0`.
`forceUpdate` therefore has a real job — escalate a bad release to mandatory without moving the
floor — instead of making `minimumVersion` redundant.

**The gate fails open.** No stored row, an unreachable endpoint, an unparseable version, or a store
link that has not been configured all resolve to *no prompt*. The server answers `204` rather than
an error when nothing is stored, and the client treats any failure as "no gate".

**`app_update_gate` is a permanent kill switch, not a temporary flag.** This contradicts the rest of
the catalog, where a flag exists only while something is broken. Here the switch is the recovery
path, so it outlives the feature. It ships **on**, which is safe precisely because an absent config
row already means "no gate". It gates the endpoint through the usual `refuseIfDisabled`, so the
switch works even for a client that does not check flags itself.

**The client reads its own installed version from the platform**, not from a constant:
`PackageManager.versionName` on Android, `CFBundleShortVersionString` on iOS. A constant in shared
code would be a third place to bump at release, and it would be the one the gate believed.

**One shared set of values, not per platform.** Simpler schema, simpler endpoint.

**Values live in one row** (`app_update_config`, pinned by `CHECK (id = 'global')`), edited in place
through `PUT /api/v1/app-update` by an account with `role = 'admin'`, and operated from `/admin/`.
The service refuses a floor newer than the latest release, because that state strands every client.

**Dismissing an optional prompt lasts the app session.** No persistence; the next cold start asks
again.

## Consequences

The gate can be wrong in only one direction: it can fail to prompt. It cannot be the reason the app
does not start. That is the trade deliberately taken — a user who stays offline can defer a
mandatory update indefinitely, and that is preferred to an outage locking out everybody.

One shared set of values means the floor cannot be raised for Android while an iOS build is still
in review: doing so would point iOS users at a version the App Store does not have. In practice the
floor moves only after both stores have the build. If the two platforms ever need to diverge, the
table gains a platform key and the endpoint a platform parameter.

Session-only dismissal means a user who declines sees the prompt again on the next launch. If that
reads as nagging, the dismissed `latestVersion` can be persisted in `KeyValueCache` — the client is
structured so that is a change to one view model field.

The store links ship empty and the gate is inert until they are filled in. The Play URL follows from
`applicationId`; the iOS App Store id does not exist until the app is published. Until both are set,
the gate is dormant on purpose rather than showing a prompt whose only button does nothing.

`Dialog` with `dismissOnBackPress = false` is what makes a forced prompt unescapable. It is verified
to compile and render on wasmJs, and is compiled for iOS in CI, but its back-gesture behaviour on a
real iOS device has not been exercised.

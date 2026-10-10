# Glossary

The words this codebase uses, and what they mean here. One entry per term; implementation lives
in `docs/`, not in this file.

## Accounts and access

**User** — an account. Every user has an email (unique among live accounts), a display name, and
a **role**. Soft-deleted and suspended accounts are treated as absent everywhere, not as users
with a flag set.

**Display name** — what a user is called on screen. **Not unique**: two real people can share a
name, so it is never an identifier. The email is the unique handle; the id is the key.

**Role** — `customer`, `staff` or `admin`. What an account is allowed to do.

**Administrator** — a user whose role is `admin`. Operates the system (today: the flag portal).
Authorised by their own access token, never by a shared secret, so every change has a name
against it. There is no separate admin account type: an administrator is a user.

**Session** — one access token and its expiry, held by a client. A session can be *unusable*
(expired, revoked, tampered) without being absent; endpoints with nothing to protect treat an
unusable session as anonymity rather than as an error.

## Feature flags

**Feature flag** — a named switch that takes a feature out of service without a deploy. Temporary
by intent: it exists while something is broken or unfinished, and is deleted afterwards.

**Flag catalog** — the compiled list of flags, shared by the apps and the server. The catalog is
the authority on which flags exist; anything not in it is unknown, not merely unset.

**Deploy default** — what a flag falls back to in this environment when nothing is stored for it
(`FEATURE_<KEY>`, otherwise the state compiled into the catalog).

**Global state** — the stored state of a flag for everybody. *Absent* means "no opinion", which is
not the same as off: the deploy default decides instead. Clearing it is an undo, not an off switch.

**Override** — one user's exception to a flag's global state. Beats the global state for that
person only.

**Resolution order** — override → global state → deploy default → catalog default. The first
source with an opinion answers.

**Effective state** — what resolution order produces for a given caller: what the feature actually
does for them, as opposed to what is stored.

## Phone verification

**Verified phone** — a number whose owner answered a one-time code sent to it. A number is unique
only among *verified* accounts: registering with someone else's number cannot lock them out,
because whoever proves control first owns it.

**One-time code (OTP)** — a six-digit code sent by SMS. Never stored here; it lives with the
provider. Its destination is always the number already on the account, never one supplied in a
request.

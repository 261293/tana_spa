# The owner role

`admin` operates the *system*: feature flags and discount codes. Running the *spa* — answering
booking requests, recording visits, changing the menu — is the **owner's** job and has its own
role, so neither duty is carried out with the other's account.

| Role | Who | What it can do |
|---|---|---|
| `customer` | anyone who signs up | book, cancel their own booking |
| `staff` | reserved, unused today | nothing beyond a customer |
| `owner` | the spa | the desk: confirm, decline, record visits, add treatments and therapists |
| `admin` | whoever runs the servers | `/admin/`: feature flags, discount codes |

Public sign-up always creates a `customer`; there is no path that grants itself anything else. An
unknown role value read from the database resolves to `customer` (`UserRole.fromSql`).

## Getting an owner account

The owner is seeded at start-up, like the administrators, from `SEED_OWNER`:

```
SEED_OWNER=owner@tana.local|Jade Bathhouse|<password>
```

Outside production a missing `SEED_OWNER` seeds `owner@tana.local` with a password generated for
that run and printed once in the log. `APP_ENV=production` refuses to start without it.

Seeding is idempotent and never promotes: if that address already belongs to a customer, the
account is left exactly as it is and a warning is logged.

## The endpoints

Everything below is refused with `403 forbidden` for every other role, including `admin`. The
role is read from the store on each call, so revoking it takes effect at once rather than when
the caller's token expires (`requireOwner`, `server/.../admin/AdminAccess.kt`).

| Method | Path | What it does |
|---|---|---|
| `GET` | `/api/v1/owner/spa/bookings` | the queue: requests waiting, and today's diary |
| `POST` | `/api/v1/owner/spa/bookings/{id}/confirm` | confirm; `{"force":true}` accepts a clash |
| `POST` | `/api/v1/owner/spa/bookings/{id}/decline` | decline, with a reason the guest is shown |
| `POST` | `/api/v1/owner/spa/bookings/{id}/complete` | record that the visit happened |
| `POST` | `/api/v1/owner/spa/bookings/{id}/no-show` | record that it did not |
| `POST` | `/api/v1/owner/spa/services` | add a treatment to the menu |
| `POST` | `/api/v1/owner/spa/therapists` | add a therapist profile (no login of their own) |

The guest's own side stays under `/api/v1/spa/bookings`, including
`POST /api/v1/spa/bookings/{id}/cancel`, which only the account that made the booking may call.

## The rules the server enforces

- **Confirm / decline** only move a `pending` booking. A second decision is refused as
  `already_decided`, so two desks pressing at once leave one outcome.
- **Confirming a clash** is refused as `overlaps_another` unless `force` is set: the overlap is
  the owner's to resolve, by declining or by deciding it is fine.
- **Completed / no-show** only move a `confirmed` booking, and only once its start time has
  passed (`visit_not_started` before that).
- **Cancelling** is the guest's, on their own `pending` or `confirmed` booking, until
  `BookingRules.CANCEL_WINDOW_MINUTES` (two hours) before the start; after that the server
  answers `too_late_to_cancel` and the app says to phone the spa. Another guest's booking reads
  as absent (`404`), not as forbidden.
- **Any therapist**: a request with no `therapistSlug` is given whoever is free at that time when
  it is created, so the no-overlap rule still holds; if nobody is free it is `slot_taken`.
- **New treatments and therapists** are validated by `SpaValidation`, shared with the app, and
  the server generates the slug from the name (`warm-stone-massage`, then `-2` if it is taken).

## Reminders

A sweep runs hourly and writes one notification per confirmed booking about 24 hours before it
starts (`BookingRules.REMINDER_LEAD_HOURS`). The claim is part of the same update
(`bookings.reminder_sent_at`, V10), so a task that runs twice — or two instances — still reminds
each guest once. Declined, cancelled and past bookings are never reminded. Delivery follows the
usual notification rules, including the recipient's `notifications` flag.

## In the app

Signing in as the owner opens the console; every other role lands in the guest app, and the
console destination is only offered to an owner. That gating is a convenience, not a defence: the
server refuses the calls regardless, which is what makes tampering with the client pointless.

## In the console

The owner's console has three sections: the **queue** (confirm, decline, record a visit), the
**menu** (add a treatment or a therapist, validated by the same `SpaValidation` the routes use)
and the **codes**, which remain the administrator's. A guest reaches none of it, and the server
refuses the calls whatever the client shows.

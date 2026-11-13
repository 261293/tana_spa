# The owner role

`admin` operates the *system*: feature flags, the app update config and discount codes. Running
the *spa* — answering booking requests, recording visits, changing the menu — is the **owner's**
job and has its own role, so neither duty is carried out with the other's account.

Access is decided by **permission**, not by role name. `shared/.../auth/SpaPermissions.kt` is the
matrix the app reads; `private.has_permission` is the same matrix in SQL, and
`supabase/tests/database/020_identity.test.sql` enumerates it, so the two copies have to change
together.

| Role | Who | Permissions | What it can do |
|---|---|---|---|
| `customer` | anyone who signs up | none | book, cancel their own booking |
| `staff` | the spa's team | `MANAGE_BOOKINGS` | the desk's queue: confirm, decline, record visits. Staff never book: `spa_create_booking` refuses them with `403 staff_cannot_book`, and the app hides booking from them |
| `owner` | the spa | `MANAGE_BOOKINGS`, `MANAGE_SERVICES`, `MANAGE_THERAPISTS`, `MANAGE_ACCOUNTS` | the desk, plus treatments, therapists and staff/customer accounts |
| `admin` | whoever operates the system | all of the above and `OPERATE_SYSTEM` | everything, including `/admin/` |

Public sign-up always creates a `customer`: a trigger on `auth.users` inserts the `public.users`
row with that role, and no function lets an account raise its own. An unknown role value reads as
having no permission.

## Getting an owner account

Nothing is seeded. An administrator (or another owner, for staff and customers) creates the
account from the app's admin menu, which calls the `accounts` Edge Function with the email, a
password, a display name and the role. What a caller may create:

| Caller | May create |
|---|---|
| `admin` | `admin`, `owner`, `staff`, `customer` |
| `owner` | `staff`, `customer` |

An existing account's role is changed with `accounts_change_role(id, role)`: the caller needs
`MANAGE_ACCOUNTS`, and may only move an account between two roles it could create
(`role_not_allowed`); nobody changes their own role (`own_role_unchangeable`); an unknown id is
`unknown_account`.

The very first administrator has no one to create it, so it is promoted by hand after signing up:

```sql
update public.users set role = 'admin' where email = '…';
```

## The functions

Each is a `public` function called as `POST /rest/v1/rpc/<name>`. It reads the caller's role
from `users` on every call, so revoking a role takes effect at once rather than when the caller's
token expires, and refuses a caller without the permission with `403 forbidden`.

| Function | Permission | What it does |
|---|---|---|
| `spa_staff_queue()` | `MANAGE_BOOKINGS` | the queue: requests waiting, and today's diary |
| `spa_confirm_booking(id, force?)` | `MANAGE_BOOKINGS` | confirm; `force: true` accepts a clash |
| `spa_decline_booking(id, reason, alternativeOffer?)` | `MANAGE_BOOKINGS` | decline, with a reason the guest is shown |
| `spa_complete_booking(id)` | `MANAGE_BOOKINGS` | record that the visit happened |
| `spa_no_show_booking(id)` | `MANAGE_BOOKINGS` | record that it did not |
| `spa_create_service(name, description, minutes, price, category)` | `MANAGE_SERVICES` | add a treatment to the menu |
| `spa_create_product(name, brand, description, price, size)` | `MANAGE_SERVICES` | put a retail product on the shelf every role sees on Home (`spa_products()`, callable without a session) |
| `spa_create_therapist(name, focus, accent?)` | `MANAGE_THERAPISTS` | add a therapist profile (no login of their own) |

The guest's own side is `spa_bookings()`, `spa_create_booking(…)` and `spa_cancel_booking(id)`,
which only the account that made the booking may call.

## The rules the database enforces

- **Confirm / decline** only move a `pending` booking. A second decision is refused as
  `already_decided`, so two desks pressing at once leave one outcome.
- **Confirming a clash** is refused as `overlaps_another` unless `force` is set: the overlap is
  the desk's to resolve, by declining or by deciding it is fine.
- **Completed / no-show** only move a `confirmed` booking (`invalid_transition` otherwise), and
  only once its start time has passed (`visit_not_started` before that).
- **Cancelling** is the guest's, on their own `pending` or `confirmed` booking, until
  `BookingRules.CANCEL_WINDOW_MINUTES` (two hours) before the start; after that the function
  answers `too_late_to_cancel` and the app says to phone the spa. Another guest's booking reads
  as absent (`404`), not as forbidden.
- **The owner assigns the therapist.** A guest's request carries no therapist and stays
  unassigned until an account with `MANAGE_THERAPISTS` gives it one, through
  `spa_confirm_booking(id, force, therapistSlug)` or `spa_assign_therapist(id, therapistSlug)`;
  confirming an unassigned request is `therapist_required`, and assigning someone already busy
  then is `overlaps_another` without `force`. A time is offered to guests only while the held
  bookings at that moment, assigned or not, are fewer than the active therapists
  (`private.capacity_free`), so unassigned requests cannot overbook the team; past that it is
  `slot_taken`. The staff queue lists who is free for each request (`freeTherapists`).
- **The guest never sees the therapist.** The guest's functions return an empty therapist, and
  the guest's confirmation names none.
- **Shifts reach the therapist's account.** `spa_link_therapist(therapistSlug, email)` links a
  therapist to one `staff` or `owner` account (`null` unlinks); the linked account reads its
  upcoming confirmed visits with `spa_my_shifts()` and is notified, in Vietnamese, when a visit is
  confirmed to it ("Ca mới"), moved to someone else ("Ca đã chuyển") or cancelled by the guest
  ("Ca đã huỷ"). Those
  notifications follow the usual rules, so with the `notifications` flag off they are not stored.
- **New treatments and therapists** are validated against the same limits as `SpaValidation` in
  the app, and the function generates the slug from the name (`warm-stone-massage`, then `-2` if
  it is taken).
- Times are placed in the spa's own zone, `spa_settings.timezone` (`Asia/Ho_Chi_Minh`).

## Reminders

A `pg_cron` job (`spa-reminders`) runs hourly and calls `private.send_due_reminders()`, which
writes one notification per confirmed booking about 24 hours before it starts
(`BookingRules.REMINDER_LEAD_HOURS`). The claim is part of the same update
(`bookings.reminder_sent_at`), so a run that overlaps another still reminds each guest once.
Declined, cancelled and past bookings are never reminded. Delivery follows the usual notification
rules, including the recipient's `notifications` flag, and tapping the reminder in the centre
opens the visit it is about.

## In the app

An account with `MANAGE_BOOKINGS` gets the desk; every other role lands in the guest app, and the
desk destinations are only offered to accounts with the permission they need (`App.kt`). That
gating is a convenience, not a defence: the database refuses the calls regardless, which is what
makes tampering with the client pointless.

## In the console

The desk has the **queue** (confirm, decline, record a visit) and the **menu** (add a treatment
or a therapist, validated by the same `SpaValidation` limits the functions check). A guest reaches
none of it, and the database refuses the calls whatever the client shows.

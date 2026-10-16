-- =====================================================================
-- V10: the spa owner.
--
-- `admin` operates the system (feature flags, discount codes). Running the
-- spa - confirming bookings, recording visits, changing the menu - is the
-- owner's job and gets its own role rather than borrowing the system
-- administrator's. Existing rows keep whatever role they have.
--
-- `reminder_sent_at` makes the 24-hour reminder once-only: the scheduled
-- task claims a booking by stamping this column, so a task that runs twice
-- (or two instances) still sends one reminder.
-- =====================================================================

ALTER TABLE users DROP CONSTRAINT users_role_valid;
ALTER TABLE users ADD CONSTRAINT users_role_valid
    CHECK (role IN ('customer', 'staff', 'owner', 'admin'));

ALTER TABLE bookings ADD COLUMN reminder_sent_at timestamptz;

-- the reminder sweep: confirmed bookings starting soon that nobody has reminded yet
CREATE INDEX bookings_reminder_idx ON bookings (start_at)
    WHERE status = 'confirmed' AND reminder_sent_at IS NULL;

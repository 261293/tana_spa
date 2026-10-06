-- =====================================================================
-- V5: phone-number verification (SMS one-time codes)
--
-- Two changes:
--  1. phone_verifications: one row per code that was sent. The code itself
--     is NEVER stored here; it lives with the SMS provider (Twilio Verify) or,
--     in local dev, in memory. This table drives rate limits, attempt caps
--     and per-country fraud monitoring.
--  2. users.phone is now unique only among VERIFIED users. Otherwise anyone
--     could register with a stranger's number and lock the real owner out;
--     the first person to prove they control a number gets it.
-- =====================================================================

DROP INDEX users_phone_active_uq;

CREATE UNIQUE INDEX users_phone_verified_uq
    ON users (phone)
    WHERE phone IS NOT NULL AND phone_verified_at IS NOT NULL AND deleted_at IS NULL;

-- lookups ("who owns this number?") by unverified and verified alike
CREATE INDEX users_phone_idx ON users (phone) WHERE phone IS NOT NULL AND deleted_at IS NULL;

-- "verified" is meaningless without a number
ALTER TABLE users
    ADD CONSTRAINT users_verified_needs_phone CHECK (phone_verified_at IS NULL OR phone IS NOT NULL);

CREATE TABLE phone_verifications (
    id           uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- E.164 snapshot of the number the code was sent to
    phone        text        NOT NULL,
    -- ISO 3166-1 alpha-2 (VN, US, ...): lets you spot a fraud spike from one country
    country_code text        NOT NULL,
    channel      text        NOT NULL DEFAULT 'sms',
    -- 'twilio_verify', 'dev', ...
    provider     text        NOT NULL,
    provider_ref text,
    status       text        NOT NULL DEFAULT 'pending',
    -- guesses spent on this code; capped by the application AND bounded atomically in SQL
    attempts     smallint    NOT NULL DEFAULT 0,
    -- requester address, kept as text: proxies can hand us strings inet would reject
    ip           text,
    sent_at      timestamptz NOT NULL DEFAULT now(),
    expires_at   timestamptz NOT NULL,
    verified_at  timestamptz,

    CONSTRAINT phone_verifications_phone_format   CHECK (phone ~ '^\+[0-9]{8,15}$'),
    CONSTRAINT phone_verifications_country_format CHECK (country_code ~ '^[A-Z]{2}$'),
    CONSTRAINT phone_verifications_channel_valid  CHECK (channel IN ('sms', 'whatsapp', 'voice')),
    CONSTRAINT phone_verifications_status_valid   CHECK (status IN ('pending', 'approved', 'canceled', 'expired', 'failed')),
    CONSTRAINT phone_verifications_attempts_valid CHECK (attempts BETWEEN 0 AND 100),
    CONSTRAINT phone_verifications_window_valid   CHECK (expires_at > sent_at),
    CONSTRAINT phone_verifications_ip_len         CHECK (ip IS NULL OR char_length(ip) <= 45),
    CONSTRAINT phone_verifications_approved_stamp CHECK ((status = 'approved') = (verified_at IS NOT NULL))
);

-- a user has at most one live code; requesting a new one cancels the old one first
CREATE UNIQUE INDEX phone_verifications_one_pending_uq ON phone_verifications (user_id) WHERE status = 'pending';

-- the rate-limit queries: per user, per number, per requester address
CREATE INDEX phone_verifications_user_idx  ON phone_verifications (user_id, sent_at DESC);
CREATE INDEX phone_verifications_phone_idx ON phone_verifications (phone, sent_at DESC);
CREATE INDEX phone_verifications_ip_idx    ON phone_verifications (ip, sent_at DESC) WHERE ip IS NOT NULL;
-- fraud dashboards: sends vs approvals per country over time
CREATE INDEX phone_verifications_country_idx ON phone_verifications (country_code, sent_at DESC);

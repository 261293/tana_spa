-- =====================================================================
-- V8: push delivery for the notifications table added in V4.
--
-- device_tokens holds one row per app install that asked to be notified.
-- The token identifies the install, not the account, so it is unique
-- globally and registering it MOVES it to the signed-in user: without
-- that, the previous user of a shared device keeps receiving pushes
-- addressed to this one.
--
-- Tokens are rotated and revoked by the provider, so a row here is a
-- cache, never a source of truth: the sender deletes any token the
-- provider reports as unregistered.
-- =====================================================================

CREATE TABLE device_tokens (
    id           uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- opaque provider string (FCM registration token), not a secret we generate
    token        text        NOT NULL,
    platform     text        NOT NULL,
    provider     text        NOT NULL DEFAULT 'fcm',
    -- BCP-47, for localising the push text
    locale       text,
    app_version  text,
    -- refreshed on every registration, so stale installs can be pruned
    last_seen_at timestamptz NOT NULL DEFAULT now(),
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT device_tokens_platform_valid CHECK (platform IN ('android', 'ios', 'web', 'desktop')),
    CONSTRAINT device_tokens_provider_valid CHECK (provider IN ('fcm')),
    CONSTRAINT device_tokens_token_len   CHECK (char_length(token) BETWEEN 16 AND 4096),
    CONSTRAINT device_tokens_locale_len  CHECK (locale IS NULL OR char_length(locale) <= 35),
    CONSTRAINT device_tokens_version_len CHECK (app_version IS NULL OR char_length(app_version) <= 40)
);

-- One owner per install; the registration upsert targets this index.
CREATE UNIQUE INDEX device_tokens_token_uq ON device_tokens (token);
-- Drives the fan-out: "every device belonging to this user".
CREATE INDEX device_tokens_user_idx ON device_tokens (user_id);
CREATE INDEX device_tokens_last_seen_idx ON device_tokens (last_seen_at);

CREATE TRIGGER device_tokens_set_updated_at BEFORE UPDATE ON device_tokens
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ---------------------------------------------------------------------
-- notifications: bounds the text now that it is written by an API, and
-- records whether a row was ever pushed (an unpushed row still shows in
-- the app, which is why delivery failure is not an error for the caller).
-- ---------------------------------------------------------------------
ALTER TABLE notifications
    ADD COLUMN sent_push_at timestamptz,
    ADD CONSTRAINT notifications_title_len CHECK (char_length(btrim(title)) BETWEEN 1 AND 120),
    ADD CONSTRAINT notifications_body_len  CHECK (body IS NULL OR char_length(body) <= 500);

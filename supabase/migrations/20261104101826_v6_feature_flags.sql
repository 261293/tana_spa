-- =====================================================================
-- V6: feature flags, switchable at runtime without a deploy.
--
-- Resolution order (FeatureFlagService): per-user override > global row >
-- FEATURE_<KEY> env var > the state compiled into FeatureFlag. A missing
-- row therefore means "no opinion", not "off".
-- =====================================================================

CREATE TABLE feature_flags (
    key         text        PRIMARY KEY,
    enabled     boolean     NOT NULL,
    description text,
    -- free text ('admin-api', 'migration', an administrator's id), so it is
    -- deliberately not a users reference
    updated_by  text,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT feature_flags_key_format  CHECK (key ~ '^[a-z][a-z0-9_]{2,62}$'),
    CONSTRAINT feature_flags_description_len CHECK (description IS NULL OR char_length(description) <= 500),
    CONSTRAINT feature_flags_updated_by_len CHECK (updated_by IS NULL OR char_length(updated_by) <= 100)
);

-- No foreign key on flag_key, on purpose: an override must be settable for a
-- flag that has no global row yet, and a flag deleted from the code must not
-- make this table unwritable. The application validates keys instead.
CREATE TABLE feature_flag_overrides (
    flag_key   text        NOT NULL,
    user_id    uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    enabled    boolean     NOT NULL,
    note       text,
    updated_by text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),

    PRIMARY KEY (flag_key, user_id),
    CONSTRAINT feature_flag_overrides_key_format CHECK (flag_key ~ '^[a-z][a-z0-9_]{2,62}$'),
    CONSTRAINT feature_flag_overrides_note_len   CHECK (note IS NULL OR char_length(note) <= 500),
    CONSTRAINT feature_flag_overrides_updated_by_len CHECK (updated_by IS NULL OR char_length(updated_by) <= 100)
);

CREATE INDEX feature_flag_overrides_user_idx ON feature_flag_overrides (user_id);

DO $$
DECLARE t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['feature_flags', 'feature_flag_overrides'] LOOP
        EXECUTE format(
            'CREATE TRIGGER %I BEFORE UPDATE ON %I FOR EACH ROW EXECUTE FUNCTION set_updated_at()',
            t || '_set_updated_at', t);
    END LOOP;
END
$$;

-- Ships off because the flow is unreliable. Enable it for one tester with an
-- override, or globally once it is fixed.
INSERT INTO feature_flags (key, enabled, description, updated_by)
VALUES (
    'phone_otp_verification',
    false,
    'Phone-number verification with SMS one-time codes (phone/otp/send and verify)',
    'migration'
);

-- =====================================================================
-- V1: extensions, helpers and identity (users, social logins, sessions,
--     saved addresses)
-- Conventions used across every migration:
--   * ids are uuid (gen_random_uuid(), core since PostgreSQL 13)
--   * money is bigint in VND (VND has no minor unit, so no decimals/floats)
--   * timestamps are timestamptz, stored in UTC
--   * enum-like columns are text + CHECK (cheaper to evolve than CREATE TYPE)
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS pg_trgm;   -- fast LIKE '%term%' search
CREATE EXTENSION IF NOT EXISTS unaccent;  -- "cham soc da" must find "Chăm sóc da"

-- unaccent() is only STABLE, which Postgres refuses inside index expressions.
-- Pinning the dictionary makes this wrapper safely IMMUTABLE.
CREATE FUNCTION app_unaccent(value text) RETURNS text
    LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT
AS $$ SELECT public.unaccent('public.unaccent'::regdictionary, value) $$;

CREATE FUNCTION set_updated_at() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END
$$;

-- ---------------------------------------------------------------------
-- users: customers, staff and admins share one table, split by role
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id                  uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    email               text        NOT NULL,
    email_verified_at   timestamptz,
    phone               text,
    phone_verified_at   timestamptz,
    full_name           text        NOT NULL,
    -- NULL for accounts that only ever signed in with Google/Apple
    password_hash       text,
    avatar_url          text,
    role                text        NOT NULL DEFAULT 'customer',
    -- shown on the home header ("Khách hàng VIP")
    membership_tier     text        NOT NULL DEFAULT 'member',
    -- the "mã giới thiệu" a user shares with friends
    referral_code       text        NOT NULL DEFAULT upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 8)),
    referred_by         uuid        REFERENCES users (id) ON DELETE SET NULL,
    status              text        NOT NULL DEFAULT 'active',
    last_login_at       timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    -- soft delete: keeps order/booking history intact and frees the email
    deleted_at          timestamptz,

    CONSTRAINT users_email_normalised  CHECK (email = lower(email) AND char_length(email) BETWEEN 3 AND 254),
    CONSTRAINT users_phone_format      CHECK (phone IS NULL OR phone ~ '^\+?[0-9]{8,15}$'),
    CONSTRAINT users_full_name_present CHECK (char_length(btrim(full_name)) BETWEEN 1 AND 100),
    CONSTRAINT users_role_valid        CHECK (role IN ('customer', 'staff', 'admin')),
    CONSTRAINT users_tier_valid        CHECK (membership_tier IN ('member', 'silver', 'gold', 'vip')),
    CONSTRAINT users_status_valid      CHECK (status IN ('active', 'suspended')),
    CONSTRAINT users_no_self_referral  CHECK (referred_by IS NULL OR referred_by <> id)
);

-- Partial unique indexes: a soft-deleted user must not block re-registration.
CREATE UNIQUE INDEX users_email_active_uq ON users (email)  WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX users_phone_active_uq ON users (phone)  WHERE deleted_at IS NULL AND phone IS NOT NULL;
CREATE UNIQUE INDEX users_referral_code_uq ON users (referral_code);
CREATE INDEX users_referred_by_idx ON users (referred_by) WHERE referred_by IS NOT NULL;

-- ---------------------------------------------------------------------
-- auth_identities: "Đăng nhập với Google" (and Apple later)
-- ---------------------------------------------------------------------
CREATE TABLE auth_identities (
    id               uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider         text        NOT NULL,
    -- the provider's stable user id (Google "sub"), never the email
    provider_user_id text        NOT NULL,
    created_at       timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT auth_identities_provider_valid CHECK (provider IN ('google', 'apple')),
    CONSTRAINT auth_identities_provider_uq UNIQUE (provider, provider_user_id),
    CONSTRAINT auth_identities_one_per_provider UNIQUE (user_id, provider)
);

-- ---------------------------------------------------------------------
-- refresh_tokens: long-lived sessions behind the short (1h) access JWT.
-- Only a SHA-256 hash is stored, so a database leak cannot be replayed.
-- Rotation: each use revokes the row and issues a new one in the same
-- family; presenting an already-revoked token means theft -> revoke family.
-- ---------------------------------------------------------------------
CREATE TABLE refresh_tokens (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    family_id   uuid        NOT NULL,
    token_hash  bytea       NOT NULL,
    expires_at  timestamptz NOT NULL,
    revoked_at  timestamptz,
    replaced_by uuid        REFERENCES refresh_tokens (id) ON DELETE SET NULL,
    user_agent  text,
    ip          inet,
    created_at  timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT refresh_tokens_hash_len CHECK (octet_length(token_hash) = 32),
    CONSTRAINT refresh_tokens_expiry   CHECK (expires_at > created_at)
);

CREATE UNIQUE INDEX refresh_tokens_hash_uq ON refresh_tokens (token_hash);
CREATE INDEX refresh_tokens_user_idx    ON refresh_tokens (user_id);
CREATE INDEX refresh_tokens_family_idx  ON refresh_tokens (family_id);
-- lets a nightly job purge expired rows cheaply
CREATE INDEX refresh_tokens_expires_idx ON refresh_tokens (expires_at);

-- ---------------------------------------------------------------------
-- user_addresses: "Địa chỉ nhận hàng" prefilled at checkout
-- ---------------------------------------------------------------------
CREATE TABLE user_addresses (
    id             uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    label          text,
    recipient_name text        NOT NULL,
    phone          text        NOT NULL,
    address_line   text        NOT NULL,
    ward           text,
    district       text,
    city           text        NOT NULL,
    is_default     boolean     NOT NULL DEFAULT false,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT user_addresses_phone_format CHECK (phone ~ '^\+?[0-9]{8,15}$')
);

CREATE INDEX user_addresses_user_idx ON user_addresses (user_id);
-- at most one default address per user, enforced by the database
CREATE UNIQUE INDEX user_addresses_one_default_uq ON user_addresses (user_id) WHERE is_default;

-- ---------------------------------------------------------------------
-- keep updated_at honest regardless of which client writes
-- ---------------------------------------------------------------------
DO $$
DECLARE t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['users', 'user_addresses'] LOOP
        EXECUTE format(
            'CREATE TRIGGER %I BEFORE UPDATE ON %I FOR EACH ROW EXECUTE FUNCTION set_updated_at()',
            t || '_set_updated_at', t);
    END LOOP;
END
$$;

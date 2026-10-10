-- =====================================================================
-- V7: the app-update gate. One row, read by every client on launch.
--
-- minimum_version is the oldest build still supported, latest_version the
-- newest released. force_update escalates an available update to mandatory
-- without moving the floor, for a release that turns out to be bad.
--
-- A client that cannot read this row shows no prompt at all, so an outage
-- degrades to "no gate" rather than locking every installed app out.
-- =====================================================================

CREATE TABLE app_update_config (
    id              text        PRIMARY KEY DEFAULT 'global',
    minimum_version text        NOT NULL,
    latest_version  text        NOT NULL,
    force_update    boolean     NOT NULL DEFAULT false,
    -- free text ('admin-api', 'migration', an administrator's id), so it is
    -- deliberately not a users reference
    updated_by      text,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT app_update_config_singleton CHECK (id = 'global'),
    CONSTRAINT app_update_config_minimum_version_format CHECK (minimum_version ~ '^[0-9]+[.][0-9]+[.][0-9]+$'),
    CONSTRAINT app_update_config_latest_version_format  CHECK (latest_version  ~ '^[0-9]+[.][0-9]+[.][0-9]+$'),
    CONSTRAINT app_update_config_updated_by_len CHECK (updated_by IS NULL OR char_length(updated_by) <= 100)
);

CREATE TRIGGER app_update_config_set_updated_at BEFORE UPDATE ON app_update_config
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Seeded at the shipping version, which resolves to "no prompt" until an
-- operator raises one of the two versions.
INSERT INTO app_update_config (id, minimum_version, latest_version, force_update, updated_by)
VALUES ('global', '1.0.0', '1.0.0', false, 'migration');

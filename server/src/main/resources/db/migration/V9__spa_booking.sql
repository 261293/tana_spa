-- =====================================================================
-- V9: the booking flow the "Jade Bathhouse" design asks for.
--
-- V2/V3 already model a bookable catalogue and appointments. Three things
-- were missing and are added here rather than duplicated:
--   * therapists        - a booking is made with a person, not just a branch
--   * declined bookings - the front desk refuses a request with a reason
--   * voucher eligibility by service category ("Facials only")
--
-- Money: the amount columns keep their original `_vnd` names. This
-- catalogue is priced in whole dollars; the column holds the amount in
-- whatever currency the catalogue is priced in. Renaming them touches
-- orders, carts and payments, which is its own migration.
-- =====================================================================

CREATE TABLE therapists (
    id         uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    slug       text        NOT NULL,
    name       text        NOT NULL,
    focus      text        NOT NULL,
    -- which of the three tones the staff schedule paints this person's column in
    accent     text        NOT NULL DEFAULT 'moss',
    is_active  boolean     NOT NULL DEFAULT true,
    sort_order integer     NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT therapists_slug_uq     UNIQUE (slug),
    CONSTRAINT therapists_accent_valid CHECK (accent IN ('moss', 'dusk', 'amber'))
);

CREATE INDEX therapists_active_idx ON therapists (sort_order) WHERE is_active;

-- ---------------------------------------------------------------------
-- bookings: who it is with, and what happened when the desk decided
-- ---------------------------------------------------------------------
ALTER TABLE bookings
    ADD COLUMN therapist_id      uuid REFERENCES therapists (id) ON DELETE RESTRICT,
    ADD COLUMN decided_at        timestamptz,
    ADD COLUMN decided_by        uuid REFERENCES users (id) ON DELETE SET NULL,
    ADD COLUMN decline_reason    text,
    ADD COLUMN alternative_offer text;

-- 'declined' joins the statuses: the guest asked, the spa said no, and the
-- request stays on the record with its reason.
ALTER TABLE bookings DROP CONSTRAINT bookings_status_valid;
ALTER TABLE bookings ADD CONSTRAINT bookings_status_valid
    CHECK (status IN ('pending', 'confirmed', 'declined', 'completed', 'cancelled', 'no_show'));

ALTER TABLE bookings ADD CONSTRAINT bookings_decline_consistent
    CHECK ((status = 'declined') = (decline_reason IS NOT NULL));

ALTER TABLE bookings ADD CONSTRAINT bookings_decline_reason_valid
    CHECK (decline_reason IS NULL OR decline_reason IN ('overlap', 'therapist_unavailable', 'no_room'));

-- "is this therapist free then" and the staff day view
CREATE INDEX bookings_therapist_idx ON bookings (therapist_id, start_at)
    WHERE status IN ('pending', 'confirmed');

-- the front desk queue, newest request first
CREATE INDEX bookings_pending_idx ON bookings (created_at DESC) WHERE status = 'pending';

-- ---------------------------------------------------------------------
-- vouchers: eligibility by service category
-- ---------------------------------------------------------------------
-- NULL or empty = every treatment qualifies. Slugs of service_categories.
ALTER TABLE vouchers ADD COLUMN eligible_categories text[];

-- ---------------------------------------------------------------------
-- Seed: the treatment menu, the team and the live offers the design shows.
-- Idempotent, so running it against a populated database changes nothing.
-- ---------------------------------------------------------------------
-- branches has no natural key, so the house is seeded only into an empty table.
INSERT INTO branches (name, address_line, city, opens_at, closes_at)
SELECT 'Jade Bathhouse', '1452 Valencia Street', 'San Francisco', '09:00', '21:00'
WHERE NOT EXISTS (SELECT 1 FROM branches);

INSERT INTO service_categories (name, slug, sort_order)
VALUES ('Massage', 'massage', 1), ('Facial', 'facial', 2), ('Body', 'body', 3)
ON CONFLICT (slug) DO NOTHING;

INSERT INTO services (category_id, name, slug, description, price_vnd, duration_minutes, is_featured, sort_order)
SELECT c.id, v.name, v.slug, v.description, v.price, v.minutes, v.featured, v.sort_order
FROM (VALUES
    ('massage', 'Warm Stone Massage',     'warm-stone-massage',     'Basalt stones, full-body pressure work, scalp finish', 165::bigint, 90, true,  1),
    ('facial',  'Jade Roller Facial',     'jade-roller-facial',     'Double cleanse, enzyme mask, cooled jade rolling',     120::bigint, 60, true,  2),
    ('massage', 'Deep Tissue Massage',    'deep-tissue-massage',    'Targeted back, neck and shoulder release',            140::bigint, 75, false, 3),
    ('body',    'Matcha Body Polish',     'matcha-body-polish',     'Green tea salt scrub, rinse, hydrating balm',          98::bigint, 50, false, 4),
    ('body',    'Herbal Compress Ritual', 'herbal-compress-ritual', 'Steamed lemongrass compresses, light oil massage',    150::bigint, 75, false, 5)
) AS v(category_slug, name, slug, description, price, minutes, featured, sort_order)
JOIN service_categories c ON c.slug = v.category_slug
ON CONFLICT (slug) DO NOTHING;

INSERT INTO branch_services (branch_id, service_id)
SELECT b.id, s.id FROM branches b CROSS JOIN services s WHERE b.is_active
ON CONFLICT DO NOTHING;

INSERT INTO therapists (slug, name, focus, accent, sort_order)
VALUES
    ('mai',   'Mai Tran',     'Stone & deep tissue', 'moss',  1),
    ('lena',  'Lena Okafor',  'Facials & skin',      'dusk',  2),
    ('tomas', 'Tomas Ruiz',   'Sports & recovery',   'amber', 3)
ON CONFLICT (slug) DO NOTHING;

INSERT INTO vouchers (code, title, description, discount_type, discount_value, min_order_vnd,
                      applies_to, starts_at, ends_at, usage_limit, per_user_limit, eligible_categories)
VALUES
    ('JADE20', '20% off any facial', 'Facials only. One use per guest.',
     'percent', 20, 0, 'service', now() - interval '14 days', now() + interval '180 days', 250, 1, ARRAY['facial']),
    ('MIDWEEK15', '$15 off weekday massages', 'Tue-Thu appointments. Minimum spend $120.',
     'fixed', 15, 120, 'service', now() - interval '9 days', now() + interval '210 days', NULL, 1, ARRAY['massage']),
    ('FIRSTVISIT', '10% off your first booking', 'New guests. Any treatment.',
     'percent', 10, 0, 'service', now() - interval '280 days', now() + interval '30 days', NULL, 1, NULL)
ON CONFLICT (code) DO NOTHING;

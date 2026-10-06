-- =====================================================================
-- V2: catalog -- branches, spa services (+combos), shop products,
--     favourites and the home-screen banners
-- =====================================================================

-- ---------------------------------------------------------------------
-- branches: "Chi nhánh"
-- ---------------------------------------------------------------------
CREATE TABLE branches (
    id           uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    name         text         NOT NULL,
    phone        text,
    address_line text         NOT NULL,
    district     text,
    city         text         NOT NULL,
    latitude     numeric(9, 6),
    longitude    numeric(9, 6),
    opens_at     time         NOT NULL DEFAULT '09:00',
    closes_at    time         NOT NULL DEFAULT '20:00',
    is_active    boolean      NOT NULL DEFAULT true,
    created_at   timestamptz  NOT NULL DEFAULT now(),
    updated_at   timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT branches_hours_valid CHECK (closes_at > opens_at),
    CONSTRAINT branches_lat_valid   CHECK (latitude  IS NULL OR latitude  BETWEEN -90  AND 90),
    CONSTRAINT branches_lng_valid   CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180)
);

-- ---------------------------------------------------------------------
-- service_categories: the accordion on the "Dịch vụ" screen
-- (Chăm sóc da chuyên sâu, Điều trị mụn..., Combo dịch vụ)
-- ---------------------------------------------------------------------
CREATE TABLE service_categories (
    id         uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    name       text        NOT NULL,
    slug       text        NOT NULL,
    sort_order integer     NOT NULL DEFAULT 0,
    is_active  boolean     NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT service_categories_slug_uq UNIQUE (slug)
);

-- ---------------------------------------------------------------------
-- services: bookable treatments. A combo is just a service whose
-- components are listed in combo_items.
-- ---------------------------------------------------------------------
CREATE TABLE services (
    id               uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id      uuid        NOT NULL REFERENCES service_categories (id) ON DELETE RESTRICT,
    name             text        NOT NULL,
    slug             text        NOT NULL,
    description      text,
    price_vnd        bigint      NOT NULL,
    duration_minutes integer     NOT NULL,
    image_url        text,
    -- "Dịch vụ nổi bật" carousel
    is_featured      boolean     NOT NULL DEFAULT false,
    is_combo         boolean     NOT NULL DEFAULT false,
    is_active        boolean     NOT NULL DEFAULT true,
    sort_order       integer     NOT NULL DEFAULT 0,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT services_slug_uq        UNIQUE (slug),
    CONSTRAINT services_price_valid    CHECK (price_vnd >= 0),
    CONSTRAINT services_duration_valid CHECK (duration_minutes > 0)
);

CREATE INDEX services_category_idx ON services (category_id, sort_order) WHERE is_active;
CREATE INDEX services_featured_idx ON services (sort_order) WHERE is_active AND is_featured;
-- accent-insensitive "Tìm kiếm dịch vụ ..."
CREATE INDEX services_name_search_idx ON services USING gin (app_unaccent(lower(name)) gin_trgm_ops);

CREATE TABLE combo_items (
    combo_id   uuid    NOT NULL REFERENCES services (id) ON DELETE CASCADE,
    service_id uuid    NOT NULL REFERENCES services (id) ON DELETE RESTRICT,
    quantity   integer NOT NULL DEFAULT 1,

    PRIMARY KEY (combo_id, service_id),
    CONSTRAINT combo_items_not_self   CHECK (combo_id <> service_id),
    CONSTRAINT combo_items_qty_valid  CHECK (quantity > 0)
);

CREATE INDEX combo_items_service_idx ON combo_items (service_id);

-- which branch offers which service (not every branch does everything)
CREATE TABLE branch_services (
    branch_id  uuid NOT NULL REFERENCES branches (id) ON DELETE CASCADE,
    service_id uuid NOT NULL REFERENCES services (id) ON DELETE CASCADE,

    PRIMARY KEY (branch_id, service_id)
);

CREATE INDEX branch_services_service_idx ON branch_services (service_id);

-- ---------------------------------------------------------------------
-- products + variants: "SPA Beauty shop". Price/stock live on the
-- variant because the cart shows "Size - 100ML  Loại - Type" per line.
-- ---------------------------------------------------------------------
CREATE TABLE products (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    name        text        NOT NULL,
    slug        text        NOT NULL,
    brand       text,
    description text,
    image_url   text,
    is_active   boolean     NOT NULL DEFAULT true,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT products_slug_uq UNIQUE (slug)
);

CREATE INDEX products_active_idx ON products (created_at DESC) WHERE is_active;
CREATE INDEX products_name_search_idx ON products USING gin (app_unaccent(lower(name)) gin_trgm_ops);

CREATE TABLE product_variants (
    id                 uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id         uuid        NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    sku                text        NOT NULL,
    size_label         text,
    type_label         text,
    price_vnd          bigint      NOT NULL,
    -- original price, for "was 250.000" strike-through
    compare_at_vnd     bigint,
    stock_qty          integer     NOT NULL DEFAULT 0,
    image_url          text,
    is_active          boolean     NOT NULL DEFAULT true,
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT product_variants_sku_uq        UNIQUE (sku),
    CONSTRAINT product_variants_price_valid   CHECK (price_vnd >= 0),
    CONSTRAINT product_variants_compare_valid CHECK (compare_at_vnd IS NULL OR compare_at_vnd >= price_vnd),
    -- the DB refuses to oversell even if two checkouts race
    CONSTRAINT product_variants_stock_valid   CHECK (stock_qty >= 0)
);

CREATE INDEX product_variants_product_idx ON product_variants (product_id) WHERE is_active;
-- NULL labels would defeat a plain UNIQUE, so coalesce them
CREATE UNIQUE INDEX product_variants_option_uq
    ON product_variants (product_id, coalesce(size_label, ''), coalesce(type_label, ''));

CREATE TABLE product_images (
    id         uuid    PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id uuid    NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    url        text    NOT NULL,
    sort_order integer NOT NULL DEFAULT 0
);

CREATE INDEX product_images_product_idx ON product_images (product_id, sort_order);

-- the heart icon on each product card
CREATE TABLE favorites (
    user_id    uuid        NOT NULL REFERENCES users (id)    ON DELETE CASCADE,
    product_id uuid        NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT now(),

    PRIMARY KEY (user_id, product_id)
);

CREATE INDEX favorites_product_idx ON favorites (product_id);

-- ---------------------------------------------------------------------
-- banners: the promo carousel on the home screen, editable without a
-- release ("Mừng tháng của Nàng - Ưu đãi ngập tràn")
-- ---------------------------------------------------------------------
CREATE TABLE banners (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    title       text        NOT NULL,
    image_url   text        NOT NULL,
    link_type   text        NOT NULL DEFAULT 'none',
    -- service/product id or voucher code, depending on link_type
    link_target text,
    starts_at   timestamptz,
    ends_at     timestamptz,
    sort_order  integer     NOT NULL DEFAULT 0,
    is_active   boolean     NOT NULL DEFAULT true,
    created_at  timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT banners_link_type_valid CHECK (link_type IN ('none', 'service', 'product', 'voucher')),
    CONSTRAINT banners_window_valid    CHECK (starts_at IS NULL OR ends_at IS NULL OR ends_at > starts_at),
    CONSTRAINT banners_target_present  CHECK (link_type = 'none' OR link_target IS NOT NULL)
);

CREATE INDEX banners_active_idx ON banners (sort_order) WHERE is_active;

DO $$
DECLARE t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['branches', 'services', 'products', 'product_variants'] LOOP
        EXECUTE format(
            'CREATE TRIGGER %I BEFORE UPDATE ON %I FOR EACH ROW EXECUTE FUNCTION set_updated_at()',
            t || '_set_updated_at', t);
    END LOOP;
END
$$;

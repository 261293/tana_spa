-- =====================================================================
-- V3: commerce -- vouchers, cart, orders, bookings, payments
--
-- Orders and bookings SNAPSHOT names and prices into their item rows.
-- A later price change or a deleted product must never rewrite history.
-- =====================================================================

-- ---------------------------------------------------------------------
-- vouchers: "Nhập mã" (promo / referral codes) and "Quà của Anh" (gifts)
-- ---------------------------------------------------------------------
CREATE TABLE vouchers (
    id               uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    code             text        NOT NULL,
    kind             text        NOT NULL DEFAULT 'promo',
    title            text        NOT NULL,
    description      text,
    discount_type    text        NOT NULL,
    -- percent: 1..100, fixed: amount in VND
    discount_value   bigint      NOT NULL,
    -- cap for percent vouchers ("10% up to 50.000")
    max_discount_vnd bigint,
    min_order_vnd    bigint      NOT NULL DEFAULT 0,
    applies_to       text        NOT NULL DEFAULT 'all',
    starts_at        timestamptz NOT NULL DEFAULT now(),
    ends_at          timestamptz,
    -- NULL = unlimited
    usage_limit      integer,
    per_user_limit   integer     NOT NULL DEFAULT 1,
    used_count       integer     NOT NULL DEFAULT 0,
    is_active        boolean     NOT NULL DEFAULT true,
    created_at       timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT vouchers_code_uq             UNIQUE (code),
    CONSTRAINT vouchers_code_upper          CHECK (code = upper(code) AND char_length(code) BETWEEN 3 AND 32),
    CONSTRAINT vouchers_kind_valid          CHECK (kind IN ('promo', 'gift', 'referral')),
    CONSTRAINT vouchers_type_valid          CHECK (discount_type IN ('percent', 'fixed')),
    CONSTRAINT vouchers_value_valid         CHECK (
        (discount_type = 'percent' AND discount_value BETWEEN 1 AND 100)
        OR (discount_type = 'fixed' AND discount_value > 0)),
    CONSTRAINT vouchers_applies_to_valid    CHECK (applies_to IN ('all', 'product', 'service')),
    CONSTRAINT vouchers_window_valid        CHECK (ends_at IS NULL OR ends_at > starts_at),
    CONSTRAINT vouchers_limits_valid        CHECK (
        min_order_vnd >= 0
        AND (max_discount_vnd IS NULL OR max_discount_vnd > 0)
        AND (usage_limit IS NULL OR usage_limit > 0)
        AND per_user_limit > 0
        AND used_count >= 0
        AND (usage_limit IS NULL OR used_count <= usage_limit))
);

-- gifts granted to one user: the "Quà của Anh" wallet
CREATE TABLE user_vouchers (
    user_id     uuid        NOT NULL REFERENCES users (id)    ON DELETE CASCADE,
    voucher_id  uuid        NOT NULL REFERENCES vouchers (id) ON DELETE CASCADE,
    source      text        NOT NULL DEFAULT 'campaign',
    granted_at  timestamptz NOT NULL DEFAULT now(),

    PRIMARY KEY (user_id, voucher_id),
    CONSTRAINT user_vouchers_source_valid CHECK (source IN ('campaign', 'gift', 'referral', 'loyalty'))
);

CREATE INDEX user_vouchers_voucher_idx ON user_vouchers (voucher_id);

-- ---------------------------------------------------------------------
-- carts: one open cart per user, plus the code typed into
-- "Nhập mã giảm giá". Totals are computed from live prices, not stored.
-- ---------------------------------------------------------------------
CREATE TABLE carts (
    user_id    uuid        PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    voucher_id uuid        REFERENCES vouchers (id) ON DELETE SET NULL,
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE cart_items (
    user_id    uuid        NOT NULL REFERENCES carts (user_id) ON DELETE CASCADE,
    variant_id uuid        NOT NULL REFERENCES product_variants (id) ON DELETE CASCADE,
    quantity   integer     NOT NULL DEFAULT 1,
    added_at   timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),

    PRIMARY KEY (user_id, variant_id),
    -- the "-" button at quantity 1 deletes the row instead of storing 0
    CONSTRAINT cart_items_qty_valid CHECK (quantity BETWEEN 1 AND 99)
);

CREATE INDEX cart_items_variant_idx ON cart_items (variant_id);

-- ---------------------------------------------------------------------
-- orders: "Hoàn tất đơn hàng" checkout
-- ---------------------------------------------------------------------
CREATE TABLE orders (
    id               uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    -- short human-friendly reference for support and receipts
    order_no         bigint      GENERATED ALWAYS AS IDENTITY,
    user_id          uuid        NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    status           text        NOT NULL DEFAULT 'pending',
    -- delivery details are copied here, not referenced, so editing a saved
    -- address never changes an order that was already placed
    recipient_name   text        NOT NULL,
    recipient_phone  text        NOT NULL,
    shipping_address text        NOT NULL,
    payment_method   text        NOT NULL DEFAULT 'cod',
    payment_status   text        NOT NULL DEFAULT 'unpaid',
    subtotal_vnd     bigint      NOT NULL,
    discount_vnd     bigint      NOT NULL DEFAULT 0,
    tax_vnd          bigint      NOT NULL DEFAULT 0,
    shipping_fee_vnd bigint      NOT NULL DEFAULT 0,
    total_vnd        bigint      NOT NULL,
    voucher_id       uuid        REFERENCES vouchers (id) ON DELETE SET NULL,
    voucher_code     text,
    note             text,
    placed_at        timestamptz NOT NULL DEFAULT now(),
    cancelled_at     timestamptz,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT orders_order_no_uq         UNIQUE (order_no),
    CONSTRAINT orders_status_valid        CHECK (status IN ('pending', 'confirmed', 'shipping', 'completed', 'cancelled')),
    CONSTRAINT orders_payment_method_valid CHECK (payment_method IN ('cod', 'bank_transfer', 'card', 'momo', 'zalopay')),
    CONSTRAINT orders_payment_status_valid CHECK (payment_status IN ('unpaid', 'paid', 'refunded')),
    CONSTRAINT orders_amounts_valid       CHECK (
        subtotal_vnd >= 0 AND discount_vnd >= 0 AND tax_vnd >= 0 AND shipping_fee_vnd >= 0
        AND discount_vnd <= subtotal_vnd),
    -- the stored total can never disagree with its parts
    CONSTRAINT orders_total_consistent    CHECK (total_vnd = subtotal_vnd - discount_vnd + tax_vnd + shipping_fee_vnd),
    CONSTRAINT orders_cancel_consistent   CHECK ((status = 'cancelled') = (cancelled_at IS NOT NULL)),
    CONSTRAINT orders_phone_format        CHECK (recipient_phone ~ '^\+?[0-9]{8,15}$')
);

CREATE INDEX orders_user_idx   ON orders (user_id, placed_at DESC);
CREATE INDEX orders_status_idx ON orders (status, placed_at DESC) WHERE status IN ('pending', 'confirmed', 'shipping');

CREATE TABLE order_items (
    id             uuid    PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id       uuid    NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    -- nullable: a discontinued variant may be deleted, the snapshot remains
    variant_id     uuid    REFERENCES product_variants (id) ON DELETE SET NULL,
    product_name   text    NOT NULL,
    variant_label  text,
    sku            text,
    image_url      text,
    unit_price_vnd bigint  NOT NULL,
    quantity       integer NOT NULL,
    line_total_vnd bigint  GENERATED ALWAYS AS (unit_price_vnd * quantity) STORED,

    CONSTRAINT order_items_price_valid CHECK (unit_price_vnd >= 0),
    CONSTRAINT order_items_qty_valid   CHECK (quantity > 0)
);

CREATE INDEX order_items_order_idx   ON order_items (order_id);
CREATE INDEX order_items_variant_idx ON order_items (variant_id) WHERE variant_id IS NOT NULL;

-- ---------------------------------------------------------------------
-- bookings: "Đặt lịch" appointments
-- ---------------------------------------------------------------------
CREATE TABLE bookings (
    id             uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_no     bigint      GENERATED ALWAYS AS IDENTITY,
    user_id        uuid        NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    branch_id      uuid        NOT NULL REFERENCES branches (id) ON DELETE RESTRICT,
    status         text        NOT NULL DEFAULT 'pending',
    start_at       timestamptz NOT NULL,
    end_at         timestamptz NOT NULL,
    customer_name  text        NOT NULL,
    customer_phone text        NOT NULL,
    note           text,
    subtotal_vnd   bigint      NOT NULL,
    discount_vnd   bigint      NOT NULL DEFAULT 0,
    total_vnd      bigint      NOT NULL,
    voucher_id     uuid        REFERENCES vouchers (id) ON DELETE SET NULL,
    cancelled_at   timestamptz,
    cancel_reason  text,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT bookings_booking_no_uq   UNIQUE (booking_no),
    CONSTRAINT bookings_status_valid    CHECK (status IN ('pending', 'confirmed', 'completed', 'cancelled', 'no_show')),
    CONSTRAINT bookings_time_valid      CHECK (end_at > start_at),
    CONSTRAINT bookings_amounts_valid   CHECK (
        subtotal_vnd >= 0 AND discount_vnd >= 0 AND discount_vnd <= subtotal_vnd
        AND total_vnd = subtotal_vnd - discount_vnd),
    CONSTRAINT bookings_cancel_consistent CHECK ((status = 'cancelled') = (cancelled_at IS NOT NULL)),
    CONSTRAINT bookings_phone_format    CHECK (customer_phone ~ '^\+?[0-9]{8,15}$')
);

CREATE INDEX bookings_user_idx   ON bookings (user_id, start_at DESC);
-- a branch's day view and "is this slot taken" lookups
CREATE INDEX bookings_branch_idx ON bookings (branch_id, start_at) WHERE status IN ('pending', 'confirmed');

CREATE TABLE booking_items (
    id               uuid    PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id       uuid    NOT NULL REFERENCES bookings (id) ON DELETE CASCADE,
    service_id       uuid    REFERENCES services (id) ON DELETE SET NULL,
    service_name     text    NOT NULL,
    unit_price_vnd   bigint  NOT NULL,
    duration_minutes integer NOT NULL,
    quantity         integer NOT NULL DEFAULT 1,

    CONSTRAINT booking_items_price_valid    CHECK (unit_price_vnd >= 0),
    CONSTRAINT booking_items_duration_valid CHECK (duration_minutes > 0),
    CONSTRAINT booking_items_qty_valid      CHECK (quantity > 0)
);

CREATE INDEX booking_items_booking_idx ON booking_items (booking_id);

-- ---------------------------------------------------------------------
-- payments: one row per attempt. COD gets a row too, so every payment
-- method flows through the same reconciliation path.
-- ---------------------------------------------------------------------
CREATE TABLE payments (
    id           uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id     uuid        REFERENCES orders (id)   ON DELETE CASCADE,
    booking_id   uuid        REFERENCES bookings (id) ON DELETE CASCADE,
    method       text        NOT NULL,
    status       text        NOT NULL DEFAULT 'pending',
    amount_vnd   bigint      NOT NULL,
    provider     text,
    -- the gateway's transaction id; the unique index makes webhooks idempotent
    provider_ref text,
    payload      jsonb,
    paid_at      timestamptz,
    created_at   timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT payments_one_target     CHECK (num_nonnulls(order_id, booking_id) = 1),
    CONSTRAINT payments_method_valid   CHECK (method IN ('cod', 'bank_transfer', 'card', 'momo', 'zalopay')),
    CONSTRAINT payments_status_valid   CHECK (status IN ('pending', 'succeeded', 'failed', 'refunded')),
    CONSTRAINT payments_amount_valid   CHECK (amount_vnd > 0),
    CONSTRAINT payments_paid_consistent CHECK ((status = 'succeeded') = (paid_at IS NOT NULL) OR status = 'refunded')
);

CREATE UNIQUE INDEX payments_provider_ref_uq ON payments (provider, provider_ref) WHERE provider_ref IS NOT NULL;
CREATE INDEX payments_order_idx   ON payments (order_id)   WHERE order_id IS NOT NULL;
CREATE INDEX payments_booking_idx ON payments (booking_id) WHERE booking_id IS NOT NULL;

-- ---------------------------------------------------------------------
-- voucher_redemptions: the audit trail behind used_count and
-- per_user_limit (checked inside the checkout transaction)
-- ---------------------------------------------------------------------
CREATE TABLE voucher_redemptions (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    voucher_id  uuid        NOT NULL REFERENCES vouchers (id) ON DELETE RESTRICT,
    user_id     uuid        NOT NULL REFERENCES users (id)    ON DELETE CASCADE,
    order_id    uuid        REFERENCES orders (id)   ON DELETE CASCADE,
    booking_id  uuid        REFERENCES bookings (id) ON DELETE CASCADE,
    amount_vnd  bigint      NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT voucher_redemptions_one_target CHECK (num_nonnulls(order_id, booking_id) = 1),
    CONSTRAINT voucher_redemptions_amount_valid CHECK (amount_vnd >= 0)
);

-- a voucher can be applied to a given order/booking only once
CREATE UNIQUE INDEX voucher_redemptions_order_uq   ON voucher_redemptions (voucher_id, order_id)   WHERE order_id IS NOT NULL;
CREATE UNIQUE INDEX voucher_redemptions_booking_uq ON voucher_redemptions (voucher_id, booking_id) WHERE booking_id IS NOT NULL;
CREATE INDEX voucher_redemptions_user_idx ON voucher_redemptions (user_id, voucher_id);

DO $$
DECLARE t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['carts', 'cart_items', 'orders', 'bookings'] LOOP
        EXECUTE format(
            'CREATE TRIGGER %I BEFORE UPDATE ON %I FOR EACH ROW EXECUTE FUNCTION set_updated_at()',
            t || '_set_updated_at', t);
    END LOOP;
END
$$;

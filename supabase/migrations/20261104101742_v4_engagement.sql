-- =====================================================================
-- V4: engagement -- feedback, notifications and the unified history feed
-- =====================================================================

-- ---------------------------------------------------------------------
-- feedback: the "Feedback" screen. Tied to the purchase or visit it is
-- about, so reviews can be shown as verified.
-- ---------------------------------------------------------------------
CREATE TABLE feedback (
    id         uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    order_id   uuid        REFERENCES orders (id)   ON DELETE SET NULL,
    booking_id uuid        REFERENCES bookings (id) ON DELETE SET NULL,
    rating     smallint    NOT NULL,
    comment    text,
    created_at timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT feedback_rating_valid CHECK (rating BETWEEN 1 AND 5),
    -- general feedback has neither; a review is about one order OR one booking
    CONSTRAINT feedback_single_target CHECK (num_nonnulls(order_id, booking_id) <= 1),
    CONSTRAINT feedback_comment_len  CHECK (comment IS NULL OR char_length(comment) <= 2000)
);

-- one review per user per order / booking
CREATE UNIQUE INDEX feedback_order_uq   ON feedback (user_id, order_id)   WHERE order_id IS NOT NULL;
CREATE UNIQUE INDEX feedback_booking_uq ON feedback (user_id, booking_id) WHERE booking_id IS NOT NULL;
CREATE INDEX feedback_created_idx ON feedback (created_at DESC);

-- ---------------------------------------------------------------------
-- notifications: the bell icon and its red dot
-- ---------------------------------------------------------------------
CREATE TABLE notifications (
    id         uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type       text        NOT NULL,
    title      text        NOT NULL,
    body       text,
    -- deep-link payload, e.g. {"orderId": "..."}
    data       jsonb       NOT NULL DEFAULT '{}'::jsonb,
    read_at    timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT notifications_type_valid CHECK (type IN ('order', 'booking', 'promotion', 'system'))
);

CREATE INDEX notifications_user_idx   ON notifications (user_id, created_at DESC);
-- drives the red dot: "does this user have anything unread?"
CREATE INDEX notifications_unread_idx ON notifications (user_id) WHERE read_at IS NULL;

-- ---------------------------------------------------------------------
-- v_user_history: the "Lịch sử" screen is orders and bookings on one
-- timeline. A view keeps one source of truth, with no duplicated table.
-- ---------------------------------------------------------------------
CREATE VIEW v_user_history AS
SELECT user_id,
       'order'::text AS kind,
       id,
       order_no      AS reference_no,
       status,
       total_vnd,
       placed_at     AS occurred_at
FROM orders
UNION ALL
SELECT user_id,
       'booking'::text AS kind,
       id,
       booking_no      AS reference_no,
       status,
       total_vnd,
       start_at        AS occurred_at
FROM bookings;

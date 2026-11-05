UPDATE branches
SET name = 'Tana Spa', city = 'TP. Hồ Chí Minh', address_line = 'Đang cập nhật'
WHERE name = 'Jade Bathhouse';

UPDATE services AS s
SET price_vnd = v.price
FROM (VALUES
    ('warm-stone-massage', 4125000::bigint),
    ('jade-roller-facial', 3000000),
    ('deep-tissue-massage', 3500000),
    ('matcha-body-polish', 2450000),
    ('herbal-compress-ritual', 3750000)
) AS v(slug, price)
WHERE s.slug = v.slug;

UPDATE vouchers
SET title = '375.000đ off weekday massages',
    description = 'Tue-Thu appointments. Minimum spend 3.000.000đ.',
    discount_value = 375000,
    min_order_vnd = 3000000
WHERE code = 'MIDWEEK15';

CREATE TABLE spa_settings (
    id         text PRIMARY KEY DEFAULT 'global' CHECK (id = 'global'),
    timezone   text NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE spa_settings ENABLE ROW LEVEL SECURITY;
INSERT INTO spa_settings (timezone) VALUES ('Asia/Ho_Chi_Minh');

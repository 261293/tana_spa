begin;
\ir _helpers.psql
select plan(6);

select is((select name || '|' || city || '|' || address_line from public.branches), 'Tana Spa|TP. Hồ Chí Minh|Đang cập nhật', 'branch_is_tana_spa');
select results_eq($$select slug, price_vnd from public.services order by sort_order$$,
  $$values ('warm-stone-massage', 4125000::bigint), ('jade-roller-facial', 3000000), ('deep-tissue-massage', 3500000), ('matcha-body-polish', 2450000), ('herbal-compress-ritual', 3750000)$$, 'services_in_dong');
select is((select discount_value || '|' || min_order_vnd from public.vouchers where code = 'MIDWEEK15'), '375000|3000000', 'midweek15_in_dong');
select is((select title from public.vouchers where code = 'MIDWEEK15'), '375.000đ off weekday massages', 'midweek15_english_title');
select is((select description from public.vouchers where code = 'MIDWEEK15'), 'Tue-Thu appointments. Minimum spend 3.000.000đ.', 'midweek15_english_terms');
select is((select timezone from public.spa_settings), 'Asia/Ho_Chi_Minh', 'house_zone_is_vietnam');

select * from finish();
rollback;

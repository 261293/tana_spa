begin;
\ir _helpers.psql
select plan(49);

create temp table ctx as
select pg_temp.new_user() as guest,
       (now() at time zone 'Asia/Ho_Chi_Minh')::date as today;

create function pg_temp.book(therapist text, day date, starts time, ends time, status text default 'pending') returns void
language sql as $$
    insert into public.bookings (user_id, branch_id, therapist_id, start_at, end_at, status, cancelled_at,
                                 customer_name, customer_phone, subtotal_vnd, total_vnd)
    select (select guest from ctx), (select b.id from public.branches b limit 1), t.id,
           (book.day + book.starts) at time zone 'Asia/Ho_Chi_Minh', (book.day + book.ends) at time zone 'Asia/Ho_Chi_Minh',
           book.status, case when book.status = 'cancelled' then now() end, 'Guest', '+84901234567', 0, 0
    from public.therapists t where t.slug = book.therapist
$$;

create function pg_temp.slots(day int, therapist text default null, service text default null) returns setof text
language sql as $$
    select to_char(to_timestamp((s ->> 'startAtEpochMillis')::bigint / 1000) at time zone 'Asia/Ho_Chi_Minh', 'HH24:MI')
           || case when (s ->> 'available')::boolean then ' open' else ' held' end
    from jsonb_array_elements(public.spa_availability(day, therapist, service) -> 'slots') s
$$;

create function pg_temp.check(code text, service text default null) returns jsonb
language sql as $$ select public.spa_check_offer(code, service) $$;

select is(private.house_day('2026-11-04 18:30+00'), ('2026-11-05'::date - '1970-01-01'::date), 'house_day_after_midnight_vietnam');
select is(private.house_day('2026-11-04 16:59+00'), ('2026-11-04'::date - '1970-01-01'::date), 'house_day_before_midnight_vietnam');

select is(public.spa_catalog() -> 'services' -> 0 ->> 'name', 'Warm Stone Massage', 'catalog_without_language_is_english');
select is(public.spa_catalog() -> 'services' -> 0,
  jsonb_build_object('slug', 'warm-stone-massage', 'name', 'Warm Stone Massage', 'minutes', 90, 'price', 4125000,
                     'category', 'MASSAGE', 'includes', (select description from public.services where slug = 'warm-stone-massage')),
  'service_dto_shape');
select results_eq($$select s ->> 'slug', s ->> 'category' from jsonb_array_elements(public.spa_catalog() -> 'services') s$$,
  $$values ('warm-stone-massage', 'MASSAGE'), ('jade-roller-facial', 'FACIAL'), ('deep-tissue-massage', 'MASSAGE'),
           ('matcha-body-polish', 'BODY'), ('herbal-compress-ritual', 'BODY')$$,
  'services_by_sort_order_with_categories');
select results_eq($$select t ->> 'slug', t ->> 'name', t ->> 'accent' from jsonb_array_elements(public.spa_catalog() -> 'therapists') t$$,
  $$values ('mai', 'Mai Tran', 'MOSS'), ('lena', 'Lena Okafor', 'DUSK'), ('tomas', 'Tomas Ruiz', 'AMBER')$$,
  'therapists_by_sort_order_with_accents');
select is((public.spa_catalog() ->> 'todayEpochDay')::int, private.house_day(now()), 'today_is_the_house_day');

update public.services set is_active = false where slug = 'matcha-body-polish';
update public.therapists set is_active = false where slug = 'tomas';
select is(jsonb_array_length(public.spa_catalog() -> 'services'), 4, 'inactive_service_hidden');
select is(jsonb_array_length(public.spa_catalog() -> 'therapists'), 2, 'inactive_therapist_hidden');
update public.services set is_active = true where slug = 'matcha-body-polish';
update public.therapists set is_active = true where slug = 'tomas';

select set_config('request.headers', '{"accept-language":"vi-VN"}', true);
select is(public.spa_catalog() -> 'services' -> 0 ->> 'name', 'Massage đá nóng', 'catalog_in_vietnamese');
select is((public.spa_catalog() -> 'services' -> 0 ->> 'price')::int, 4125000, 'price_in_dong');
select is(public.spa_catalog() -> 'therapists' -> 0 ->> 'focus', (select coalesce(nullif(focus_vi, ''), focus) from public.therapists where slug = 'mai'),
  'therapist_focus_in_vietnamese');
update public.services set name_vi = '  ' where slug = 'warm-stone-massage';
select is(public.spa_catalog() -> 'services' -> 0 ->> 'name', 'Warm Stone Massage', 'blank_translation_falls_back');
update public.services set name_vi = null where slug = 'warm-stone-massage';
select is(public.spa_catalog() -> 'services' -> 0 ->> 'name', 'Warm Stone Massage', 'missing_translation_falls_back');
select set_config('request.headers', '{"accept-language":"en-US"}', true);
select is(public.spa_catalog() -> 'therapists' -> 0 ->> 'focus', (select focus from public.therapists where slug = 'mai'), 'therapist_focus_in_english');

select pg_temp.book('mai', (select today from ctx) + d, '08:00', '22:00') from generate_series(0, 13) d where d <> 5;
select pg_temp.book('mai', (select today from ctx) + 5, '08:00', '12:30');
select pg_temp.book('mai', (select today from ctx) + 5, '13:30', '22:00');
select is(public.spa_catalog() -> 'therapists' -> 0 -> 'nextSlot',
  jsonb_build_object('dayEpochDay', private.house_day(now()) + 5, 'hour', 13),
  'next_slot_judges_the_start_inside_a_held_window');
select pg_temp.book('mai', (select today from ctx) + 5, '12:30', '13:30');
select is(public.spa_catalog() -> 'therapists' -> 0 -> 'nextSlot', 'null'::jsonb, 'next_slot_null_when_14_days_full');
delete from public.bookings;

select results_eq($$select o ->> 'code' from jsonb_array_elements(public.spa_offers() -> 'offers') o$$,
  $$values ('MIDWEEK15'), ('JADE20'), ('FIRSTVISIT')$$, 'offers_newest_first');
select is((select o from jsonb_array_elements(public.spa_offers() -> 'offers') o where o ->> 'code' = 'MIDWEEK15') - 'title' - 'terms' - 'validUntil',
  '{"code": "MIDWEEK15", "percentOff": null, "amountOff": 375000, "minSpend": 3000000, "eligible": ["MASSAGE"]}'::jsonb,
  'fixed_offer_dto');
select is((select o from jsonb_array_elements(public.spa_offers() -> 'offers') o where o ->> 'code' = 'JADE20') - 'title' - 'terms' - 'validUntil',
  '{"code": "JADE20", "percentOff": 20, "amountOff": null, "minSpend": null, "eligible": ["FACIAL"]}'::jsonb,
  'percent_offer_dto');

insert into public.vouchers (code, title, title_vi, description, discount_type, discount_value, starts_at, ends_at, eligible_categories)
values ('LATE5', 'Late', 'Muộn', null, 'percent', 5, now() - interval '1 day', '2027-03-05 23:30+00', '{body,facial}'),
       ('GONE', 'Gone', null, null, 'percent', 5, now() - interval '2 days', now() - interval '1 day', null),
       ('SOON', 'Soon', null, null, 'percent', 5, now() + interval '1 day', null, null),
       ('OFF', 'Off', null, null, 'percent', 5, now() - interval '1 day', null, null),
       ('SPENT', 'Spent', null, null, 'percent', 5, now() - interval '3 days', null, null);
update public.vouchers set is_active = false where code = 'OFF';
update public.vouchers set usage_limit = 1, used_count = 1 where code = 'SPENT';
select results_eq($$select o ->> 'code' from jsonb_array_elements(public.spa_offers() -> 'offers') o$$,
  $$values ('LATE5'), ('SPENT'), ('MIDWEEK15'), ('JADE20'), ('FIRSTVISIT')$$, 'offers_only_live_ones');
select is((select o from jsonb_array_elements(public.spa_offers() -> 'offers') o where o ->> 'code' = 'LATE5'),
  '{"code": "LATE5", "title": "Late", "terms": "", "percentOff": 5, "amountOff": null, "minSpend": null, "eligible": ["BODY", "FACIAL"], "validUntil": "Mar 5, 2027"}'::jsonb,
  'valid_until_is_the_utc_date_in_english');
select is((select o from jsonb_array_elements(public.spa_offers() -> 'offers') o where o ->> 'code' = 'FIRSTVISIT') -> 'eligible', '[]'::jsonb,
  'no_categories_is_empty_list');
select set_config('request.headers', '{"accept-language":"vi"}', true);
select is((select o ->> 'title' || '|' || (o ->> 'validUntil') from jsonb_array_elements(public.spa_offers() -> 'offers') o where o ->> 'code' = 'LATE5'),
  'Muộn|5 thg 3, 2027', 'offer_in_vietnamese');
select set_config('request.headers', '{}', true);

select is(pg_temp.check('JADE20', 'jade-roller-facial') - 'offer',
  '{"accepted": true, "discount": 600000, "reason": null}'::jsonb, 'jade20_on_facial_accepted');
select is(pg_temp.check('JADE20', 'jade-roller-facial') -> 'offer' ->> 'code', 'JADE20', 'accepted_carries_the_offer');
select is(pg_temp.check(' jade20 ', 'jade-roller-facial') ->> 'accepted', 'true', 'code_trimmed_and_uppercased');
select is(pg_temp.check('JADE20', 'warm-stone-massage'),
  '{"accepted": false, "offer": null, "discount": 0, "reason": "invalid_code"}'::jsonb, 'jade20_on_massage_refused');
select is(pg_temp.check('MIDWEEK15', 'matcha-body-polish') ->> 'reason', 'invalid_code', 'midweek15_on_body_refused');
select is(pg_temp.check('MIDWEEK15', 'deep-tissue-massage') - 'offer',
  '{"accepted": true, "discount": 375000, "reason": null}'::jsonb, 'midweek15_on_massage_over_minimum');
select is(pg_temp.check('UNKNOWN', 'jade-roller-facial') ->> 'reason', 'invalid_code', 'unknown_code_refused');
select is(pg_temp.check('JADE20') ->> 'reason', 'unknown_service', 'no_service_is_unknown_service');
select is(pg_temp.check('GONE') ->> 'reason', 'invalid_code', 'dead_code_refused_before_service');
select is(pg_temp.check('SPENT', 'jade-roller-facial') ->> 'reason', 'invalid_code', 'spent_code_refused');

insert into public.services (category_id, name, slug, price_vnd, duration_minutes, sort_order)
select c.id, 'Tiny', 'tiny', 1050, 30, 99 from public.service_categories c where c.slug = 'massage';
insert into public.vouchers (code, title, discount_type, discount_value, starts_at)
values ('HALF5', 'Five', 'percent', 5, now() - interval '1 day'), ('BIGFIX', 'Big', 'fixed', 5000, now() - interval '1 day');
select is((pg_temp.check('HALF5', 'tiny') ->> 'discount')::int, 53, 'percent_rounds_half_up');
select is((pg_temp.check('BIGFIX', 'tiny') ->> 'discount')::int, 1050, 'fixed_capped_at_price');
update public.vouchers set min_order_vnd = 1051 where code = 'BIGFIX';
select is(pg_temp.check('BIGFIX', 'tiny') ->> 'reason', 'invalid_code', 'below_minimum_refused');

select pg_temp.book('mai', (select today from ctx) + 3, '10:00', '11:30');
select pg_temp.book('lena', (select today from ctx) + 3, '09:00', '20:00', 'cancelled');
select results_eq($$select pg_temp.slots(private.house_day(now()) + 3, 'mai', 'warm-stone-massage')$$,
  $$values ('09:00 held'), ('10:00 held'), ('11:30 held'), ('13:00 open'), ('14:30 open'), ('16:00 open'), ('17:30 open'), ('19:00 open')$$,
  'availability_marks_held_slot');
select results_eq($$select pg_temp.slots(private.house_day(now()) + 3, 'mai') limit 3$$,
  $$values ('09:00 open'), ('10:00 held'), ('11:30 held')$$,
  'without_service_only_the_start_is_judged');
select is((select count(*)::int from pg_temp.slots(private.house_day(now()) + 3, 'lena', 'warm-stone-massage') s where s like '%open'), 8,
  'cancelled_booking_holds_nothing');
select is((select s from pg_temp.slots(private.house_day(now()) + 3) s offset 1 limit 1), '10:00 open', 'availability_any_therapist_open_while_one_free');
select pg_temp.book('lena', (select today from ctx) + 3, '10:00', '11:00');
select pg_temp.book('tomas', (select today from ctx) + 3, '10:00', '11:00', 'confirmed');
select is((select s from pg_temp.slots(private.house_day(now()) + 3) s offset 1 limit 1), '10:00 held', 'any_therapist_held_when_all_busy');
select is((select s from pg_temp.slots(private.house_day(now()) + 3, 'nobody') s offset 1 limit 1), '10:00 held', 'unknown_therapist_is_any');

select is((select count(*)::int from pg_temp.slots(private.house_day(now()) - 1) s where s like '%open'), 0, 'past_slots_unavailable');
select is(public.spa_availability(private.house_day(now()) + 3) -> 'slots' -> 2,
  jsonb_build_object('startAtEpochMillis', (extract(epoch from ((select today from ctx) + 3 + time '11:30') at time zone 'Asia/Ho_Chi_Minh') * 1000)::bigint,
                     'startHour', 11.5, 'available', true),
  'slot_dto_shape');
select is((public.spa_availability() ->> 'dayEpochDay')::int, private.house_day(now()), 'no_day_means_today');

select ok(has_function_privilege('anon', 'public.spa_catalog()', 'execute')
  and has_function_privilege('anon', 'public.spa_offers()', 'execute')
  and has_function_privilege('anon', 'public.spa_check_offer(text, text)', 'execute')
  and has_function_privilege('anon', 'public.spa_availability(int, text, text)', 'execute')
  and has_function_privilege('authenticated', 'public.spa_availability(int, text, text)', 'execute'), 'public_functions_open_to_callers');
select ok(not has_function_privilege('anon', 'private.held(uuid, date)', 'execute')
  and not has_function_privilege('authenticated', 'private.offer_verdict(public.vouchers, public.services)', 'execute'), 'helpers_closed');
select pg_temp.as_anon();
select is(jsonb_array_length(public.spa_catalog() -> 'services'), 6, 'anon_reads_the_catalog');

select * from finish();
rollback;

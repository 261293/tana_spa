begin;
\ir _helpers.psql
select plan(35);

create temp table ctx as
select pg_temp.new_user('customer', 'lan@test.local', '{"full_name": "Lan", "phone": "0912345678"}') as lan,
       pg_temp.new_user('customer', 'binh@test.local', '{"full_name": "Bình"}') as binh,
       (now() at time zone 'Asia/Ho_Chi_Minh')::date as today;
grant select on ctx to authenticated, anon;

create function pg_temp.at(days int, clock time) returns bigint
language sql as $$
    select (extract(epoch from (((select today from ctx) + days + clock) at time zone 'Asia/Ho_Chi_Minh')) * 1000)::bigint
$$;

create function pg_temp.book(service text, days int, clock time, therapist text default null, code text default null,
                             note text default null) returns jsonb
language sql as $$
    select public.spa_create_booking(service, pg_temp.at(days, clock), therapist, code, note)
$$;

select pg_temp.as_user((select lan from ctx));

select is(pg_temp.book('jade-roller-facial', 2, '10:00', 'mai') -> 'therapist' ->> 'slug', '', 'guest_never_sees_a_named_therapist');
select is(pg_temp.book('jade-roller-facial', 3, '10:00') -> 'therapist' ->> 'slug', '', 'create_any_leaves_the_therapist_to_the_owner');
select is(pg_temp.book('jade-roller-facial', 3, '10:00') ->> 'status', 'PENDING', 'create_any_while_a_therapist_is_left');
select is(pg_temp.api_error($$select pg_temp.book('jade-roller-facial', 2, '10:00', 'mai')$$), 'PGRST 409 slot_taken',
  'second_booking_same_slot_is_slot_taken');
select pg_temp.book('jade-roller-facial', 3, '10:00');
select is(pg_temp.api_error($$select pg_temp.book('jade-roller-facial', 3, '10:00')$$), 'PGRST 409 slot_taken',
  'any_with_nobody_free_is_slot_taken');
select is(pg_temp.api_error($$select pg_temp.book('warm-stone-massage', 2, '09:00', 'mai')$$), 'PGRST 409 slot_taken',
  'treatment_running_into_a_held_one_is_slot_taken');
select is(pg_temp.api_error($$select pg_temp.book('jade-roller-facial', 2, '10:15', 'lena')$$), 'PGRST 409 slot_taken',
  'off_grid_start_is_slot_taken');
select is(pg_temp.api_error($$select pg_temp.book('jade-roller-facial', -1, '10:00', 'lena')$$), 'PGRST 400 slot_in_past',
  'past_start_is_slot_in_past');
select is(pg_temp.api_error($$select pg_temp.book('jade-roller-facial', 61, '10:00', 'lena')$$), 'PGRST 400 slot_in_past',
  'beyond_60_days_is_slot_in_past');
select is(pg_temp.api_error($$select pg_temp.book('no-such-treatment', 2, '10:00', 'lena')$$), 'PGRST 400 unknown_service',
  'unknown_service');
select is(pg_temp.api_error($$select pg_temp.book('jade-roller-facial', 2, '10:00', 'nobody')$$), 'PGRST 400 unknown_therapist',
  'unknown_therapist');
select is(pg_temp.api_error($$select pg_temp.book('jade-roller-facial', 4, '10:00', 'lena', 'NOPE')$$) || ' / ' ||
          pg_temp.api_error($$select pg_temp.book('warm-stone-massage', 4, '10:00', 'lena', 'JADE20')$$) || ' / ' ||
          jsonb_array_length(public.spa_bookings() -> 'bookings'),
  'PGRST 400 invalid_code / PGRST 400 invalid_code / 4', 'bad_code_refuses_booking');

select is(
  (select jsonb_build_object('subtotal', b -> 'subtotal', 'discount', b -> 'discount', 'total', b -> 'total', 'code', b -> 'code',
                             'note', b -> 'note')
   from pg_temp.book('jade-roller-facial', 5, '10:00', 'lena', ' jade20 ', E'  quiet room \n') b),
  '{"subtotal": 3000000, "discount": 600000, "total": 2400000, "code": "JADE20", "note": "quiet room"}'::jsonb,
  'code_discounts_total');
select is(
  (select jsonb_build_object('code', b -> 'code', 'note', b -> 'note', 'discount', b -> 'discount')
   from pg_temp.book('jade-roller-facial', 5, '13:00', 'lena', '   ', '   ') b),
  '{"code": null, "note": null, "discount": 0}'::jsonb, 'blank_code_and_note_ignored');

create temp table shaped as select pg_temp.book('jade-roller-facial', 6, '14:30', 'tomas') as b;
select is(
  (select jsonb_build_object('startHour', b -> 'startHour', 'startDayEpochDay', b -> 'startDayEpochDay',
                             'startAtEpochMillis', b -> 'startAtEpochMillis', 'endAtEpochMillis', b -> 'endAtEpochMillis',
                             'guestName', b -> 'guestName', 'guestPhone', b -> 'guestPhone', 'status', b -> 'status',
                             'declineReason', b -> 'declineReason', 'conflict', b -> 'conflict',
                             'referenceIsBk', b ->> 'reference' ~ '^BK-[0-9]+$')
   from shaped),
  jsonb_build_object('startHour', 14.5, 'startDayEpochDay', (select today from ctx) + 6 - date '1970-01-01',
                     'startAtEpochMillis', pg_temp.at(6, '14:30'), 'endAtEpochMillis', pg_temp.at(6, '15:30'),
                     'guestName', 'Lan', 'guestPhone', '+84912345678', 'status', 'PENDING',
                     'declineReason', null, 'conflict', null, 'referenceIsBk', true),
  'booking_shape_in_house_zone');
select is(pg_temp.at(6, '14:30') % 86400000, (7 * 3600 + 30 * 60) * 1000::bigint, 'house_1430_is_0730_utc');
select is(
  (select array_agg(k order by k) from shaped, jsonb_object_keys(b) k),
  array['alternativeOffer', 'code', 'conflict', 'createdAtEpochMillis', 'declineReason', 'discount', 'endAtEpochMillis', 'guestName',
        'guestPhone', 'id', 'note', 'reference', 'service', 'startAtEpochMillis', 'startDayEpochDay', 'startHour', 'status',
        'subtotal', 'therapist', 'total'],
  'booking_dto_serialises_every_field');
select is((select (b -> 'service' ->> 'slug') || ' ' || (b -> 'therapist' ->> 'accent') || ' ' || (b -> 'therapist' -> 'nextSlot')
           from shaped),
  'jade-roller-facial MOSS null', 'booking_carries_service_and_hides_therapist');

select pg_temp.as_user((select binh from ctx));
select is(pg_temp.book('jade-roller-facial', 7, '09:00', 'mai') ->> 'guestPhone', '+10000000000', 'guest_without_phone_gets_placeholder');
select is(pg_temp.api_error($$select pg_temp.book('jade-roller-facial', 6, '14:30', 'tomas')$$), 'PGRST 409 slot_taken',
  'another_guest_cannot_take_the_slot');
select is(jsonb_array_length(public.spa_bookings() -> 'bookings'), 1, 'bookings_lists_only_my_own');
select is(pg_temp.api_error(format('select public.spa_cancel_booking(%L)', (select b ->> 'id' from shaped))), 'PGRST 404 not_your_booking',
  'cancel_other_guest_is_404');
select is(pg_temp.api_error(format('select public.spa_cancel_booking(%L)', gen_random_uuid())), 'PGRST 404 unknown_booking',
  'cancel_unknown_is_404');

select pg_temp.as_user((select lan from ctx));
select results_eq(
  $$select (b ->> 'startAtEpochMillis')::bigint from jsonb_array_elements(public.spa_bookings() -> 'bookings') b$$,
  $$select (b ->> 'startAtEpochMillis')::bigint from jsonb_array_elements(public.spa_bookings() -> 'bookings') b
    order by 1 desc$$,
  'bookings_latest_start_first');
select is(jsonb_array_length(public.spa_bookings() -> 'bookings'), 7, 'bookings_lists_all_of_mine');

select is((select public.spa_cancel_booking((b ->> 'id')::uuid) ->> 'status' from shaped), 'CANCELLED', 'cancel_own');
select is(pg_temp.api_error(format('select public.spa_cancel_booking(%L)', (select b ->> 'id' from shaped))), 'PGRST 409 invalid_transition',
  'cancel_twice_is_invalid_transition');
select is(pg_temp.book('jade-roller-facial', 6, '14:30', 'tomas') ->> 'status', 'PENDING', 'cancelled_slot_is_free_again');

select pg_temp.as_postgres();
select is(
  (select bk.cancelled_at = now() and bk.decided_at = now() and bk.decided_by is null
   from public.bookings bk where bk.id = (select (b ->> 'id')::uuid from shaped)),
  true, 'cancel_records_when');
select is(
  (select bi.service_name || ' ' || bi.unit_price_vnd || ' ' || bi.duration_minutes
   from public.booking_items bi where bi.booking_id = (select (b ->> 'id')::uuid from shaped)),
  'Jade Roller Facial 3000000 60', 'booking_items_snapshot_inserted');
select is(
  (select bk.branch_id from public.bookings bk where bk.id = (select (b ->> 'id')::uuid from shaped)),
  (select br.id from public.branches br where br.is_active order by br.created_at limit 1), 'branch_is_first_active');

update public.bookings set therapist_id = null where id = (select (b ->> 'id')::uuid from shaped);
select is(
  (select private.booking_dto(bk, false, 'Overlaps') - array['id', 'reference', 'guestName', 'guestPhone', 'service', 'startAtEpochMillis',
        'endAtEpochMillis', 'startHour', 'startDayEpochDay', 'status', 'subtotal', 'discount', 'total', 'code', 'note',
        'createdAtEpochMillis', 'declineReason', 'alternativeOffer']
   from public.bookings bk where bk.id = (select (b ->> 'id')::uuid from shaped)),
  '{"therapist": {"slug": "", "name": "", "focus": "", "accent": "MOSS", "nextSlot": null}, "conflict": "Overlaps"}'::jsonb,
  'missing_therapist_placeholder_and_conflict');

update public.bookings set start_at = now() + interval '1 hour', end_at = now() + interval '2 hours', status = 'confirmed', cancelled_at = null
where id = (select (b ->> 'id')::uuid from shaped);
select pg_temp.as_user((select lan from ctx));
select is(pg_temp.api_error(format('select public.spa_cancel_booking(%L)', (select b ->> 'id' from shaped))), 'PGRST 409 too_late_to_cancel',
  'cancel_inside_window_is_409');

select pg_temp.as_anon();
select is(pg_temp.api_error($$select pg_temp.book('jade-roller-facial', 8, '10:00', 'mai')$$),
  '42501 permission denied for function spa_create_booking', 'anon_cannot_book');

select pg_temp.as_postgres();
select is(
  (select array_agg(p.proname::text || ':' || has_function_privilege('anon', p.oid, 'execute') || ':'
                    || has_function_privilege('authenticated', p.oid, 'execute') order by p.proname)
   from pg_proc p where p.pronamespace = 'public'::regnamespace and p.proname in ('spa_bookings', 'spa_create_booking', 'spa_cancel_booking')),
  array['spa_bookings:false:true', 'spa_cancel_booking:false:true', 'spa_create_booking:false:true'],
  'only_signed_in_guests_execute');

select * from finish();
rollback;

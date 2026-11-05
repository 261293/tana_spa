begin;
\ir _helpers.psql
select plan(33);

create temp table ctx as
select pg_temp.new_user('customer', 'hoa@test.local', '{"full_name": "Hoa", "phone": "0912345678"}') as hoa,
       pg_temp.new_user('staff', 'desk@test.local') as desk,
       pg_temp.new_user('owner', 'chu@test.local') as chu,
       private.house_day(now()) as today,
       private.house_zone() as zone;
grant select on ctx to authenticated, anon;

create temp table notices (uid uuid, type text, title text, body text, data jsonb);
create or replace function private.notify(uid uuid, type text, title text, body text, data jsonb default '{}') returns uuid
    language sql set search_path = ''
as $$
    insert into pg_temp.notices values (uid, type, title, body, data) returning null::uuid
$$;

create function pg_temp.at(days int, clock time) returns timestamptz
language sql as $$
    select (date '1970-01-01' + c.today + days + clock) at time zone c.zone from ctx c
$$;

create function pg_temp.seed(service text, therapist text, days int, clock time, status text default 'pending') returns uuid
language plpgsql as $$
declare
    s public.services;
    booking_id uuid;
begin
    select * into s from public.services x where x.slug = service;
    insert into public.bookings (user_id, branch_id, therapist_id, status, start_at, end_at, customer_name, customer_phone,
                                 subtotal_vnd, total_vnd, created_at, decline_reason)
    values ((select hoa from ctx), (select br.id from public.branches br order by br.created_at limit 1),
            (select t.id from public.therapists t where t.slug = therapist), status, pg_temp.at(days, clock),
            pg_temp.at(days, clock) + make_interval(mins => s.duration_minutes), 'Hoa', '+84912345678', s.price_vnd, s.price_vnd,
            clock_timestamp(), case when status = 'declined' then 'no_room' end)
    returning id into booking_id;
    insert into public.booking_items (booking_id, service_id, service_name, unit_price_vnd, duration_minutes)
    values (booking_id, s.id, s.name, s.price_vnd, s.duration_minutes);
    return booking_id;
end
$$;

create function pg_temp.api_message(statement text) returns text
language plpgsql as $$
declare
    body text;
begin
    execute statement;
    return 'no error';
exception when others then
    get stacked diagnostics body = message_text;
    return body::json ->> 'message';
end
$$;

create temp table bk as
select pg_temp.seed('warm-stone-massage', 'mai', 2, '10:00') as first,
       pg_temp.seed('jade-roller-facial', 'mai', 2, '11:30') as clash,
       pg_temp.seed('jade-roller-facial', 'lena', 3, '10:00') as plain,
       pg_temp.seed('jade-roller-facial', 'tomas', 3, '13:00') as declined,
       pg_temp.seed('jade-roller-facial', 'lena', 0, '09:00', 'confirmed') as today_confirmed,
       pg_temp.seed('jade-roller-facial', 'tomas', 0, '19:00') as today_pending,
       pg_temp.seed('jade-roller-facial', 'tomas', 0, '16:00', 'declined') as today_declined,
       pg_temp.seed('jade-roller-facial', 'lena', -1, '10:00', 'confirmed') as yesterday,
       pg_temp.seed('jade-roller-facial', 'lena', -1, '13:00', 'confirmed') as yesterday_done,
       pg_temp.seed('jade-roller-facial', 'mai', 4, '10:00', 'confirmed') as ahead,
       pg_temp.seed('jade-roller-facial', 'mai', 5, '10:00') as orphan;
delete from public.booking_items where booking_id = (select orphan from bk);
grant select on bk to authenticated;

create temp table refs as
select (select 'BK-' || b.booking_no from public.bookings b where b.id = bk.first) as first from bk;
grant select on refs to authenticated;

select pg_temp.as_user((select desk from ctx));
create temp table queue as select public.spa_staff_queue() as q;

select is((select (q -> 'dayEpochDay')::int from queue), (select today from ctx), 'queue_day_is_house_today');
select is(
  (select array_agg(e ->> 'id' order by n) from queue, jsonb_array_elements(q -> 'pending') with ordinality p(e, n)),
  (select array[today_pending, declined, plain, clash, first]::text[] from bk), 'queue_pending_newest_first');
select is(
  (select array_agg(e ->> 'id' order by n) from queue, jsonb_array_elements(q -> 'scheduled') with ordinality p(e, n)),
  (select array[today_confirmed, today_pending]::text[] from bk), 'queue_scheduled_is_held_today_by_start');
select is(
  (select e ->> 'conflict' from queue, jsonb_array_elements(q -> 'pending') e where e ->> 'id' = (select clash::text from bk)),
  'Overlaps Mai Tran 10:00 AM with ' || (select first from refs), 'queue_conflict_names_the_clash');
select is(
  (select e -> 'conflict' from queue, jsonb_array_elements(q -> 'pending') e where e ->> 'id' = (select plain::text from bk)),
  'null'::jsonb, 'queue_no_conflict_is_null');
select is(
  (select count(*)::int from queue, jsonb_array_elements(q -> 'pending') e where e ->> 'id' = (select first::text from bk)
     and e ->> 'conflict' like 'Overlaps Mai Tran 11:30 AM with BK-%'),
  1, 'queue_conflict_is_symmetric');

select is(pg_temp.api_error(format('select public.spa_confirm_booking(%L)', (select clash from bk))), 'PGRST 409 overlaps_another',
  'confirm_clash_needs_force');
select is(pg_temp.api_message(format('select public.spa_confirm_booking(%L)', (select clash from bk))), (select first from refs),
  'overlaps_another_names_the_reference');
select is(public.spa_confirm_booking((select clash from bk), true) ->> 'status', 'CONFIRMED', 'confirm_clash_with_force');

select is(public.spa_confirm_booking((select plain from bk)) ->> 'status', 'CONFIRMED', 'staff_can_confirm');
select is(pg_temp.api_error(format('select public.spa_confirm_booking(%L)', (select plain from bk))), 'PGRST 409 already_decided',
  'confirm_twice_is_already_decided');
select is(pg_temp.api_error(format('select public.spa_decline_booking(%L, %L)', (select plain from bk), 'NO_ROOM')),
  'PGRST 409 already_decided', 'decline_decided_is_already_decided');
select is(pg_temp.api_error(format('select public.spa_confirm_booking(%L)', gen_random_uuid())), 'PGRST 404 unknown_booking',
  'confirm_unknown_is_404');
select is(pg_temp.api_error(format('select public.spa_confirm_booking(%L)', (select orphan from bk))), 'PGRST 404 unknown_booking',
  'booking_without_items_is_unknown');

create temp table declined as
select public.spa_decline_booking((select declined from bk), 'THERAPIST_UNAVAILABLE', '  Thursday at 2:30 PM  ') as d;
select is((select jsonb_build_object('status', d -> 'status', 'declineReason', d -> 'declineReason', 'alternativeOffer', d -> 'alternativeOffer')
           from declined),
  '{"status": "DECLINED", "declineReason": "THERAPIST_UNAVAILABLE", "alternativeOffer": "Thursday at 2:30 PM"}'::jsonb,
  'decline_stores_reason');
select is(pg_temp.api_error(format('select public.spa_decline_booking(%L, %L)', (select first from bk), 'BUSY')),
  'PGRST 400 invalid_request', 'decline_unknown_reason_is_400');

select is(pg_temp.api_error(format('select public.spa_complete_booking(%L)', (select ahead from bk))), 'PGRST 409 visit_not_started',
  'complete_before_start_is_visit_not_started');
select is(pg_temp.api_error(format('select public.spa_no_show_booking(%L)', (select first from bk))), 'PGRST 409 invalid_transition',
  'no_show_pending_is_invalid_transition');
select is(public.spa_no_show_booking((select yesterday from bk)) ->> 'status', 'NO_SHOW', 'no_show_after_start');
select is(public.spa_complete_booking((select yesterday_done from bk)) ->> 'status', 'COMPLETED', 'complete_after_start');

select is(pg_temp.api_error($$select public.spa_create_service('Hot Stone', 'Basalt', 60, 1000000, 'MASSAGE')$$), 'PGRST 403 forbidden',
  'staff_cannot_create_service');
select is(pg_temp.api_error($$select public.spa_create_therapist('Linh', 'Facials')$$), 'PGRST 403 forbidden',
  'staff_cannot_create_therapist');

select pg_temp.as_postgres();
select is(
  (select array_agg(b.status || ':' || coalesce(b.decline_reason, '-') || ':' || coalesce(b.alternative_offer, '-') || ':'
                    || (b.decided_by = (select desk from ctx)) || ':' || (b.decided_at = now()) order by b.start_at)
   from public.bookings b where b.id in (select declined from bk union all select yesterday from bk union all select plain from bk)),
  array['no_show:-:-:true:true', 'confirmed:-:-:true:true', 'declined:therapist_unavailable:Thursday at 2:30 PM:true:true'],
  'decisions_record_who_and_when');
select is(
  (select array_agg(n.title || ' | ' || n.body || ' | ' || n.type || ' | ' || (n.uid = (select hoa from ctx)) || ' | '
                    || (n.data = jsonb_build_object('bookingId', b.id, 'reference', 'BK-' || b.booking_no)) order by b.start_at)
   from notices n join public.bookings b on b.id = (n.data ->> 'bookingId')::uuid),
  array['Booking confirmed | Jade Roller Facial with Mai Tran on ' || to_char(pg_temp.at(2, '11:30') at time zone (select zone from ctx),
          'Dy Mon FMDD') || ', 11:30 AM | booking | true | true',
        'Booking confirmed | Jade Roller Facial with Lena Okafor on ' || to_char(pg_temp.at(3, '10:00') at time zone (select zone from ctx),
          'Dy Mon FMDD') || ', 10:00 AM | booking | true | true',
        'Time unavailable | Tomas Ruiz is not available at 1:00 PM. We can offer Thursday at 2:30 PM. | booking | true | true'],
  'confirm_and_decline_notify_the_guest');

select pg_temp.as_user((select hoa from ctx));
select is(pg_temp.api_error(format('select public.spa_confirm_booking(%L)', (select first from bk))), 'PGRST 403 forbidden',
  'customer_cannot_confirm');
select is(pg_temp.api_error('select public.spa_staff_queue()') || ' / '
          || pg_temp.api_error(format('select public.spa_decline_booking(%L, %L)', (select first from bk), 'NO_ROOM')) || ' / '
          || pg_temp.api_error(format('select public.spa_complete_booking(%L)', (select ahead from bk))) || ' / '
          || pg_temp.api_error(format('select public.spa_no_show_booking(%L)', (select ahead from bk))) || ' / '
          || pg_temp.api_error($$select public.spa_create_service('Hot Stone', 'Basalt', 60, 1000000, 'MASSAGE')$$) || ' / '
          || pg_temp.api_error($$select public.spa_create_therapist('Linh', 'Facials')$$),
  'PGRST 403 forbidden / PGRST 403 forbidden / PGRST 403 forbidden / PGRST 403 forbidden / PGRST 403 forbidden / PGRST 403 forbidden',
  'customer_cannot_work_the_desk');
select is(
  (select count(*)::int from jsonb_array_elements(public.spa_bookings() -> 'bookings') e
   where e = 'null'::jsonb or e ->> 'id' = (select orphan::text from bk)),
  0, 'booking_without_items_absent_from_my_bookings');
select pg_temp.as_postgres();
select is(
  (select count(*)::int from queue, jsonb_array_elements((q -> 'pending') || (q -> 'scheduled')) e
   where e = 'null'::jsonb or e ->> 'id' = (select orphan::text from bk)),
  0, 'booking_without_items_absent_from_queue');

select pg_temp.as_user((select chu from ctx));
create temp table added as select public.spa_create_service('  Warm Stone Massage ', ' Basalt stones ', 90, 4125000, 'BODY') as a;
select is(
  (select jsonb_build_object('slug', a ->> 'slug', 'price', a -> 'price', 'minutes', a -> 'minutes', 'category', a -> 'category',
                             'name', a -> 'name', 'includes', a -> 'includes',
                             'second', public.spa_create_service('Warm Stone Massage', 'Again', 60, 100000000, 'FACIAL') ->> 'slug')
   from added),
  '{"slug": "warm-stone-massage-2", "price": 4125000, "minutes": 90, "category": "BODY", "name": "Warm Stone Massage",
    "includes": "Basalt stones", "second": "warm-stone-massage-3"}'::jsonb,
  'create_service_accepts_dong_price');
select is(pg_temp.api_error($$select public.spa_create_service('Quick', 'Short', 20, 100000, 'MASSAGE')$$) || ' '
          || pg_temp.api_message($$select public.spa_create_service('Quick', 'Short', 20, 100000, 'MASSAGE')$$),
  'PGRST 400 invalid_request The minutes is not valid', 'create_service_rejects_bad_minutes');
select is(pg_temp.api_message($$select public.spa_create_service(' ', 'x', 0, -1, 'MASSAGE')$$) || ' / '
          || pg_temp.api_message(format('select public.spa_create_service(%L, %L, 60, 1, %L)', 'x', repeat('d', 241), 'MASSAGE')) || ' / '
          || pg_temp.api_message($$select public.spa_create_service('x', 'y', 495, 1, 'MASSAGE')$$) || ' / '
          || pg_temp.api_message($$select public.spa_create_service('x', 'y', 60, 100000001, 'MASSAGE')$$) || ' / '
          || pg_temp.api_message($$select public.spa_create_service('x', 'y', 60, 1, 'SAUNA')$$) || ' / '
          || pg_temp.api_message($$select public.spa_create_therapist('x', '')$$) || ' / '
          || pg_temp.api_message($$select public.spa_create_therapist('x', 'y', 'RED')$$) || ' / '
          || pg_temp.api_error($$select public.spa_create_service('!!!', 'y', 60, 1, 'MASSAGE')$$),
  'The name is not valid / The description is not valid / The minutes is not valid / The price is not valid / '
    || 'The category is not valid / The focus is not valid / The accent is not valid / PGRST 409 unknown_service',
  'create_rejects_each_field_first_invalid_named');
select is(public.spa_create_therapist(' Ngọc Anh ', ' Facials ', 'DUSK'),
  '{"slug": "ngọc-anh", "name": "Ngọc Anh", "focus": "Facials", "accent": "DUSK", "nextSlot": null}'::jsonb,
  'create_therapist_vietnamese_name_slug');

select pg_temp.as_postgres();
select is(
  (select array_agg(p.proname::text || ':' || has_function_privilege('anon', p.oid, 'execute') || ':'
                    || has_function_privilege('authenticated', p.oid, 'execute') order by p.proname)
   from pg_proc p where p.pronamespace = 'public'::regnamespace
     and p.proname in ('spa_staff_queue', 'spa_confirm_booking', 'spa_decline_booking', 'spa_complete_booking', 'spa_no_show_booking',
                       'spa_create_service', 'spa_create_therapist', 'spa_bookings')),
  array['spa_bookings:false:true', 'spa_complete_booking:false:true', 'spa_confirm_booking:false:true', 'spa_create_service:false:true',
        'spa_create_therapist:false:true', 'spa_decline_booking:false:true', 'spa_no_show_booking:false:true',
        'spa_staff_queue:false:true'],
  'only_signed_in_accounts_execute');

select * from finish();
rollback;

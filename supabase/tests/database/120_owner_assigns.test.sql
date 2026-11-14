begin;
\ir _helpers.psql
select plan(23);

create temp table ctx as
select pg_temp.new_user('customer', 'lan@test.local', '{"full_name": "Lan", "phone": "0912345678"}') as lan,
       pg_temp.new_user('staff', 'desk@test.local') as desk,
       pg_temp.new_user('owner', 'chu@test.local') as chu,
       pg_temp.new_user('staff', 'Mai.Tran@test.local') as mai_account,
       (now() at time zone 'Asia/Ho_Chi_Minh')::date as today;
grant select on ctx to authenticated, anon;

create temp table notices (uid uuid, type text, title text, body text, data jsonb);
grant insert, select on notices to authenticated;
create or replace function private.notify(uid uuid, type text, title text, body text, data jsonb default '{}') returns uuid
    language sql set search_path = ''
as $$
    insert into pg_temp.notices values (uid, type, title, body, data) returning null::uuid
$$;

create function pg_temp.at(days int, clock time) returns bigint
language sql as $$
    select (extract(epoch from (((select today from ctx) + days + clock) at time zone 'Asia/Ho_Chi_Minh')) * 1000)::bigint
$$;

create function pg_temp.slot(days int, clock time) returns boolean
language sql as $$
    select (s ->> 'available')::boolean
    from jsonb_array_elements(public.spa_availability((select today from ctx) + days - date '1970-01-01', null, 'jade-roller-facial') -> 'slots') s
    where (s ->> 'startAtEpochMillis')::bigint = pg_temp.at(days, clock)
$$;

select pg_temp.as_user((select lan from ctx));
create temp table bk as
select (public.spa_create_booking('jade-roller-facial', pg_temp.at(2, '10:00')) ->> 'id')::uuid as a,
       (public.spa_create_booking('jade-roller-facial', pg_temp.at(2, '10:00')) ->> 'id')::uuid as b,
       (public.spa_create_booking('jade-roller-facial', pg_temp.at(2, '10:00')) ->> 'id')::uuid as c;
grant select on bk to authenticated;

select is(pg_temp.api_error($$select public.spa_create_booking('jade-roller-facial', pg_temp.at(2, '10:00'))$$), 'PGRST 409 slot_taken',
  'unassigned_bookings_use_up_the_team');
select is(pg_temp.slot(2, '10:00') || ' ' || pg_temp.slot(2, '11:30'), 'false true', 'availability_counts_unassigned_bookings');
select is(pg_temp.api_error($$select public.spa_create_booking('warm-stone-massage', pg_temp.at(2, '09:00'))$$), 'PGRST 409 slot_taken',
  'a_visit_running_into_a_full_slot_is_taken');

select pg_temp.as_user((select desk from ctx));
create temp table queue as select public.spa_staff_queue() as q;
select is(
  (select e -> 'freeTherapists' from queue, jsonb_array_elements(q -> 'pending') e where e ->> 'id' = (select a::text from bk)),
  '["mai", "lena", "tomas"]'::jsonb, 'queue_lists_who_is_free_for_each_request');
select is((select jsonb_agg(t ->> 'slug') from queue, jsonb_array_elements(q -> 'therapists') t), '["mai", "lena", "tomas"]'::jsonb,
  'queue_carries_the_team');
select is((select count(*)::int from queue, jsonb_array_elements(q -> 'therapists') t where t ? 'account'), 0,
  'queue_does_not_show_accounts');
select is(pg_temp.api_error(format('select public.spa_confirm_booking(%L)', (select a from bk))), 'PGRST 409 therapist_required',
  'confirm_unassigned_needs_a_therapist');
select is(pg_temp.api_error(format('select public.spa_confirm_booking(%L, false, %L)', (select a from bk), 'mai')), 'PGRST 403 forbidden',
  'staff_cannot_assign');
select is(pg_temp.api_error('select public.spa_team()') || ' / ' || pg_temp.api_error($$select public.spa_link_therapist('mai', 'desk@test.local')$$),
  'PGRST 403 forbidden / PGRST 403 forbidden', 'staff_cannot_manage_the_team');

select pg_temp.as_user((select chu from ctx));
select is(pg_temp.api_error($$select public.spa_link_therapist('mai', 'nobody@test.local')$$) || ' / '
          || pg_temp.api_error($$select public.spa_link_therapist('mai', 'lan@test.local')$$) || ' / '
          || pg_temp.api_error($$select public.spa_link_therapist('nobody', 'desk@test.local')$$),
  'PGRST 404 unknown_account / PGRST 400 role_not_allowed / PGRST 400 unknown_therapist', 'link_refuses_bad_accounts');
select is(public.spa_link_therapist('lena', ' MAI.TRAN@test.local ') ->> 'account', 'mai.tran@test.local', 'link_matches_email_ignoring_case');
select is(public.spa_link_therapist('mai', 'mai.tran@test.local') ->> 'account', 'mai.tran@test.local', 'link_again_moves_the_account');
select is((select jsonb_object_agg(t ->> 'slug', t -> 'account') from jsonb_array_elements(public.spa_team() -> 'therapists') t),
  '{"mai": "mai.tran@test.local", "lena": null, "tomas": null}'::jsonb, 'team_shows_one_account_per_person');

select is(public.spa_confirm_booking((select a from bk), false, 'mai') -> 'therapist' ->> 'slug', 'mai', 'owner_assigns_while_confirming');
select is(public.spa_assign_therapist((select b from bk), 'lena') ->> 'status', 'PENDING', 'owner_assigns_a_pending_request');
select is(pg_temp.api_error(format('select public.spa_assign_therapist(%L, %L)', (select c from bk), 'mai')), 'PGRST 409 overlaps_another',
  'assigning_a_busy_therapist_needs_force');

select pg_temp.as_user((select mai_account from ctx));
select is((select jsonb_agg(e ->> 'id') from jsonb_array_elements(public.spa_my_shifts() -> 'shifts') e), (select jsonb_build_array(a) from bk),
  'therapist_sees_the_confirmed_shift');

select pg_temp.as_user((select chu from ctx));
select is(public.spa_assign_therapist((select a from bk), 'tomas') -> 'therapist' ->> 'slug', 'tomas', 'owner_moves_a_confirmed_shift');
select pg_temp.as_user((select mai_account from ctx));
select is(jsonb_array_length(public.spa_my_shifts() -> 'shifts'), 0, 'moved_shift_leaves_the_old_list');

select pg_temp.as_postgres();
select is(
  (select array_agg(n.title || ' | ' || n.body || ' | ' || (n.uid = (select mai_account from ctx)) order by n.title desc)
   from notices n where n.title in ('Ca mới', 'Ca đã chuyển')),
  array['Ca mới | Chăm sóc da mặt con lăn ngọc, '
          || (select case extract(isodow from today + 2)::int when 7 then 'CN' else 'Th ' || (extract(isodow from today + 2)::int + 1) end
                     || ' ' || to_char(today + 2, 'DD/MM') from ctx) || ', 10:00, 60 phút. Khách: Lan. | true',
        'Ca đã chuyển | Chăm sóc da mặt con lăn ngọc, '
          || (select case extract(isodow from today + 2)::int when 7 then 'CN' else 'Th ' || (extract(isodow from today + 2)::int + 1) end
                     || ' ' || to_char(today + 2, 'DD/MM') from ctx) || ', 10:00 đã giao cho người khác. | true'],
  'the_linked_therapist_hears_of_a_new_and_a_moved_shift');
select is((select n.body from notices n where n.title = 'Booking confirmed'),
  'Jade Roller Facial on ' || to_char((select today + 2 from ctx), 'Dy Mon FMDD') || ', 10:00 AM', 'guest_confirmation_names_no_therapist');

select pg_temp.as_user((select chu from ctx));
select public.spa_assign_therapist((select a from bk), 'mai');
select pg_temp.as_user((select lan from ctx));
select public.spa_cancel_booking((select a from bk));
select pg_temp.as_postgres();
select is((select n.title || ' | ' || n.body from notices n where n.title = 'Ca đã hủy'),
  'Ca đã hủy | Chăm sóc da mặt con lăn ngọc, '
    || (select case extract(isodow from today + 2)::int when 7 then 'CN' else 'Th ' || (extract(isodow from today + 2)::int + 1) end
               || ' ' || to_char(today + 2, 'DD/MM') from ctx) || ', 10:00: khách đã hủy.',
  'a_guest_cancelling_tells_the_therapist_it_is_off');

select is(
  (select array_agg(p.proname::text || ':' || has_function_privilege('anon', p.oid, 'execute') || ':'
                    || has_function_privilege('authenticated', p.oid, 'execute') order by p.proname)
   from pg_proc p where p.pronamespace = 'public'::regnamespace
     and p.proname in ('spa_assign_therapist', 'spa_confirm_booking', 'spa_link_therapist', 'spa_my_shifts', 'spa_team')),
  array['spa_assign_therapist:false:true', 'spa_confirm_booking:false:true', 'spa_link_therapist:false:true', 'spa_my_shifts:false:true',
        'spa_team:false:true'],
  'only_signed_in_accounts_execute');

select * from finish();
rollback;

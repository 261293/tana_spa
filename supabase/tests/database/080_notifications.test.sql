begin;
\ir _helpers.psql
select plan(40);

delete from public.feature_flags where key = 'notifications';
update public.vouchers set ends_at = '2099-03-07 12:00+00' where code = 'FIRSTVISIT';

create temp table early as
select pg_temp.new_user('customer', 'quiet@test.local') as quiet,
       pg_temp.new_user('admin', 'root@test.local') as root,
       pg_temp.new_user('staff', 'desk@test.local') as desk;

select is(private.notify((select quiet from early), 'system', 'Hello', null), null::uuid, 'notify_refused_when_flag_off');
select is((select count(*)::int from public.notifications where user_id = (select quiet from early)), 0, 'nothing_stored_when_flag_off');
select is((select v.code from public.user_vouchers uv join public.vouchers v on v.id = uv.voucher_id
           where uv.user_id = (select quiet from early) and uv.source = 'gift'), 'FIRSTVISIT', 'signup_grants_firstvisit');

insert into public.feature_flags (key, enabled) values ('notifications', true);

create temp table ctx as
select e.*,
       pg_temp.new_user('customer', 'hoa@test.local') as hoa,
       pg_temp.new_user('customer', 'lan@test.local') as lan,
       pg_temp.new_user('customer', 'pager@test.local') as pager
from early e;
grant select on ctx to authenticated;
insert into public.feature_flag_overrides (flag_key, user_id, enabled) select 'notifications', lan, false from ctx;

select results_eq(format($$select type, title, body, data from public.notifications where user_id = %L$$, (select hoa from ctx)),
  $$values ('promotion'::text, 'Welcome gift'::text, '10% off your first booking. Use FIRSTVISIT. Valid until Mar 7, 2099.'::text,
            '{"code": "FIRSTVISIT"}'::jsonb)$$,
  'signup_with_flag_on_sends_welcome_gift');
select is(private.grant_voucher((select hoa from ctx), ' firstvisit ', 'gift'), false, 'repeat_grant_is_silent');
select is((select count(*)::int from public.notifications where user_id = (select hoa from ctx)), 1, 'repeat_grant_sends_nothing');
select is(private.grant_voucher((select hoa from ctx), 'NOPE', 'campaign'), false, 'unknown_code_is_not_granted');

create function pg_temp.seed(guest uuid, therapist text, start_at timestamptz, status text) returns uuid
language plpgsql as $$
declare
    s public.services;
    booking_id uuid;
begin
    select * into s from public.services x where x.slug = 'jade-roller-facial';
    insert into public.bookings (user_id, branch_id, therapist_id, status, start_at, end_at, customer_name, customer_phone,
                                 subtotal_vnd, total_vnd)
    values (guest, (select br.id from public.branches br order by br.created_at limit 1),
            (select t.id from public.therapists t where t.slug = therapist), status, start_at,
            start_at + make_interval(mins => s.duration_minutes), 'Guest', '+84912345678', s.price_vnd, s.price_vnd)
    returning id into booking_id;
    insert into public.booking_items (booking_id, service_id, service_name, unit_price_vnd, duration_minutes)
    values (booking_id, s.id, s.name, s.price_vnd, s.duration_minutes);
    return booking_id;
end
$$;

create temp table bk as
select pg_temp.seed(c.hoa, 'mai', (date '1970-01-01' + private.house_day(now()) + 2 + time '10:00') at time zone private.house_zone(),
                    'pending') as pending,
       pg_temp.seed(c.hoa, 'lena', now() + interval '2 hours', 'confirmed') as soon,
       pg_temp.seed(c.lan, 'tomas', now() + interval '3 hours', 'confirmed') as muted,
       pg_temp.seed(c.hoa, 'tomas', now() + interval '30 hours', 'confirmed') as later
from ctx c;
grant select on bk to authenticated;

select pg_temp.as_user((select desk from ctx));
select public.spa_confirm_booking((select pending from bk));
select pg_temp.as_postgres();
select results_eq(format($$select type, title, body, data from public.notifications where user_id = %L and type = 'booking'$$, (select hoa from ctx)),
  format($$values ('booking'::text, 'Booking confirmed'::text, %L::text, %L::jsonb)$$,
         (select 'Jade Roller Facial on ' || private.day_and_time(b.start_at) from public.bookings b where b.id = (select pending from bk)),
         (select jsonb_build_object('bookingId', b.id, 'reference', 'BK-' || b.booking_no) from public.bookings b where b.id = (select pending from bk))),
  'confirm_writes_notification');

select is(private.send_due_reminders(), 1, 'reminder_written_for_visit_within_a_day');
select is(private.send_due_reminders(), 0, 'reminder_claims_once');
select results_eq(format($$select body, data from public.notifications where user_id = %L and title = 'Tomorrow at the spa'$$, (select hoa from ctx)),
  format($$values (%L::text, %L::jsonb)$$,
         (select 'Jade Roller Facial with Lena Okafor on ' || private.day_and_time(b.start_at) from public.bookings b where b.id = (select soon from bk)),
         (select jsonb_build_object('bookingId', b.id, 'reference', 'BK-' || b.booking_no) from public.bookings b where b.id = (select soon from bk))),
  'reminder_text_names_treatment_therapist_and_time');
select is((select reminder_sent_at from public.bookings where id = (select soon from bk)), now(), 'reminder_claim_stamped');
select is((select reminder_sent_at from public.bookings where id = (select muted from bk)), null, 'reminder_released_when_flag_off');
select is((select count(*)::int from public.notifications where user_id = (select lan from ctx) and title = 'Tomorrow at the spa'), 0,
  'reminder_not_stored_when_flag_off');
select is((select reminder_sent_at from public.bookings where id = (select later from bk)), null, 'reminder_ignores_visits_beyond_a_day');

delete from public.notifications where user_id = (select pager from ctx);
insert into public.notifications (user_id, type, title, created_at)
select c.pager, 'system', t.title, now() - make_interval(secs => t.n / 1000000.0)
from ctx c, (values ('one', 1), ('two', 2), ('three', 3)) t(title, n);
create temp table cursor_of as
select (extract(epoch from now() - interval '2 microseconds') * 1000000)::bigint as two,
       (select id from public.notifications n where n.title = 'one' and n.user_id = (select pager from ctx)) as one_id;
grant select on cursor_of to authenticated;

select pg_temp.as_user((select pager from ctx));
create temp table page1 as select public.notifications_list(2) as p;
create temp table page2 as select public.notifications_list(2, (select p -> 'nextBefore' from page1)::bigint) as p;
select is((select array_agg(i ->> 'title') from page1, jsonb_array_elements(p -> 'items') i), array['one', 'two'], 'list_newest_first');
select is((select (p ->> 'nextBefore')::bigint from page1), (select two from cursor_of), 'list_pages_by_micros');
select is((select array_agg(i ->> 'title') from page2, jsonb_array_elements(p -> 'items') i), array['three'], 'next_page_continues_after_cursor');
select is((select p -> 'nextBefore' from page2), 'null'::jsonb, 'short_page_ends_the_list');
select is((select p -> 'items' -> 0 from page1),
  jsonb_build_object('id', (select one_id from cursor_of), 'type', 'system', 'title', 'one', 'body', null, 'data', '{}'::jsonb, 'read', false,
                     'createdAtEpochMillis', ((select two from cursor_of) + 1) / 1000),
  'list_item_shape');
select is((select (p ->> 'unreadCount')::int from page1), 3, 'list_counts_unread');
select is(jsonb_array_length(public.notifications_list() -> 'items'), 3, 'list_default_page');

select pg_temp.as_user((select hoa from ctx));
select is(pg_temp.api_error(format('select public.notifications_mark_read(%L)', (select one_id from cursor_of))), 'PGRST 404 notification_not_found',
  'mark_read_other_users_is_404');
select pg_temp.as_user((select pager from ctx));
select is(public.notifications_mark_read((select one_id from cursor_of)), '{"unreadCount": 2}'::jsonb, 'mark_read_returns_unread_count');
select is(pg_temp.api_error(format('select public.notifications_mark_read(%L)', (select one_id from cursor_of))), 'PGRST 404 notification_not_found',
  'mark_read_twice_is_404');
select is(public.notifications_mark_all_read(), '{"unreadCount": 0}'::jsonb, 'mark_all_read');
select is(public.notifications_unread_count(), '{"unreadCount": 0}'::jsonb, 'unread_count');

select pg_temp.as_user((select hoa from ctx));
select public.register_device(' fcm-token-0123456789 ', 'android', 'fcm', 'vi-VN', '1.2.3');
select pg_temp.as_user((select pager from ctx));
select public.register_device('fcm-token-0123456789', 'web', 'fcm', repeat('x', 40), repeat('9', 50));
select pg_temp.as_postgres();
select results_eq($$select user_id, platform, char_length(locale), char_length(app_version) from public.device_tokens where token = 'fcm-token-0123456789'$$,
  format($$values (%L::uuid, 'web'::text, 35, 40)$$, (select pager from ctx)), 'register_moves_token_between_users');
select pg_temp.as_user((select pager from ctx));
select is(
  pg_temp.api_error($$select public.register_device('short', 'android')$$) || ' / '
    || pg_temp.api_error($$select public.register_device('fcm-token-has space', 'android')$$) || ' / '
    || pg_temp.api_error($$select public.register_device('fcm-token-0123456789', 'fax')$$) || ' / '
    || pg_temp.api_error($$select public.register_device('fcm-token-0123456789', 'ios', 'apns')$$),
  'PGRST 400 invalid_request / PGRST 400 invalid_request / PGRST 400 invalid_request / PGRST 400 invalid_request',
  'register_validates_token_platform_provider');

select pg_temp.as_postgres();
insert into public.device_tokens (user_id, token, platform) select lan, 'lan-token-0123456789', 'ios' from ctx;
select pg_temp.as_user((select lan from ctx));
select is(
  pg_temp.api_error('select public.notifications_list()') || ' / ' || pg_temp.api_error('select public.notifications_unread_count()') || ' / '
    || pg_temp.api_error('select public.notifications_mark_all_read()') || ' / '
    || pg_temp.api_error($$select public.register_device('lan-token-0123456789', 'ios')$$),
  'PGRST 503 feature_disabled / PGRST 503 feature_disabled / PGRST 503 feature_disabled / PGRST 503 feature_disabled',
  'centre_is_flag_gated');
select public.unregister_device('lan-token-0123456789');
select pg_temp.as_postgres();
select is((select count(*)::int from public.device_tokens where token = 'lan-token-0123456789'), 0, 'unregister_works_with_flag_off');

select pg_temp.as_user((select hoa from ctx));
select is(pg_temp.api_error(format($$select public.admin_send_notification(%L, 'Hi')$$, (select pager from ctx))), 'PGRST 403 forbidden',
  'customer_cannot_send');
select pg_temp.as_user((select root from ctx));
select is(
  pg_temp.api_error($$select public.admin_send_notification(gen_random_uuid(), 'Hi')$$) || ' / '
    || pg_temp.api_error(format($$select public.admin_send_notification(%L, 'Hi')$$, (select lan from ctx))) || ' / '
    || pg_temp.api_error(format($$select public.admin_send_notification(%L, '  ')$$, (select pager from ctx))) || ' / '
    || pg_temp.api_error(format($$select public.admin_send_notification(%L, 'Hi', 'bogus')$$, (select pager from ctx))) || ' / '
    || pg_temp.api_error(format($$select public.admin_send_notification(%L, 'Hi', body => repeat('b', 501))$$, (select pager from ctx))) || ' / '
    || pg_temp.api_error(format($$select public.admin_send_notification(%L, 'Hi', data => (select jsonb_object_agg('k' || g, 'v') from generate_series(1, 17) g))$$,
                                (select pager from ctx))),
  'PGRST 404 invalid_request / PGRST 503 feature_disabled / PGRST 400 invalid_request / PGRST 400 invalid_request / PGRST 400 invalid_request / PGRST 400 invalid_request',
  'send_refusals');
create temp table sent as
select public.admin_send_notification((select pager from ctx), ' Hi ', body => ' ', data => '{"k": "v"}') as r;
select is((select r - 'notificationId' from sent), '{"devices": 1, "delivered": 0, "removed": 0}'::jsonb, 'send_counts_recipient_devices');
select pg_temp.as_postgres();
select results_eq(format($$select type, title, body, data from public.notifications where id = %L$$, (select r ->> 'notificationId' from sent)),
  $$values ('system'::text, 'Hi'::text, null::text, '{"k": "v"}'::jsonb)$$, 'send_stores_trimmed_row');

select pg_temp.as_user((select root from ctx));
select public.spa_create_code('t-new', 'Ten.', 10);
select pg_temp.as_postgres();
select results_eq(format($$select type, body, data from public.notifications where user_id = %L and title = 'New offer'$$, (select hoa from ctx)),
  $$values ('promotion'::text, 'Ten. Use T-NEW.'::text, '{"code": "T-NEW"}'::jsonb)$$, 'new_code_announced_to_active_user');
select is((select count(*)::int from public.notifications where user_id = (select lan from ctx) and title = 'New offer'), 0,
  'announcement_skips_users_with_flag_off');

select results_eq($$select schedule, command from cron.job where jobname = 'spa-reminders'$$,
  $$values ('0 * * * *'::text, 'select private.send_due_reminders()'::text)$$, 'reminder_job_runs_hourly');

select is(
  (select array_agg(p.proname::text || ':' || has_function_privilege('anon', p.oid, 'execute') || ':'
                    || has_function_privilege('authenticated', p.oid, 'execute') order by p.proname)
   from pg_proc p where p.pronamespace = 'public'::regnamespace
     and p.proname in ('notifications_list', 'notifications_unread_count', 'notifications_mark_read', 'notifications_mark_all_read',
                       'register_device', 'unregister_device', 'admin_send_notification')),
  array['admin_send_notification:false:true', 'notifications_list:false:true', 'notifications_mark_all_read:false:true',
        'notifications_mark_read:false:true', 'notifications_unread_count:false:true', 'register_device:false:true',
        'unregister_device:false:true'],
  'only_signed_in_accounts_execute');
select is(
  (select array_agg(p.proname::text || ':' || has_function_privilege('authenticated', p.oid, 'execute') order by p.proname)
   from pg_proc p where p.pronamespace = 'private'::regnamespace
     and p.proname in ('notify', 'grant_voucher', 'announce', 'send_due_reminders')),
  array['announce:false', 'grant_voucher:false', 'notify:false', 'send_due_reminders:false'],
  'private_helpers_not_callable');

select * from finish();
rollback;

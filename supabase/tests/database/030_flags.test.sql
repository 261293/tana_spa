begin;
\ir _helpers.psql
select plan(28);

create temp table people as
select pg_temp.new_user('customer', 'lan@test.local') as lan,
       pg_temp.new_user('admin', 'admin@test.local') as admin;
grant select on people to authenticated, anon;

delete from public.feature_flags;

select results_eq(
  $$select k, private.flag_default(k) from unnest(array['phone_otp_verification', 'app_update_gate', 'notifications']) with ordinality as t(k, n) order by n$$,
  $$values ('phone_otp_verification', false), ('app_update_gate', true), ('notifications', false)$$,
  'flag_defaults_match_kotlin');
select results_eq(
  $$select ord, key, default_enabled from private.flag_catalog() order by ord$$,
  $$values (1, 'phone_otp_verification', false), (2, 'app_update_gate', true), (3, 'notifications', false)$$,
  'catalog_lists_flags_once_in_order');
select is(private.flag_default('nope'), null, 'unknown_key_has_no_default');

select results_eq(
  $$select k, private.flag_enabled(k, (select lan from people)) from unnest(array['phone_otp_verification', 'app_update_gate', 'notifications']) with ordinality as t(k, n) order by n$$,
  $$values ('phone_otp_verification', false), ('app_update_gate', true), ('notifications', false)$$,
  'resolution_order_default');

insert into public.feature_flags (key, enabled) values ('phone_otp_verification', true), ('app_update_gate', false), ('notifications', true);
select results_eq(
  $$select k, private.flag_enabled(k, (select lan from people)) from unnest(array['phone_otp_verification', 'app_update_gate', 'notifications']) with ordinality as t(k, n) order by n$$,
  $$values ('phone_otp_verification', true), ('app_update_gate', false), ('notifications', true)$$,
  'resolution_order_global_beats_default');

insert into public.feature_flag_overrides (flag_key, user_id, enabled)
select k, (select lan from people), e
from (values ('phone_otp_verification', false), ('app_update_gate', true), ('notifications', false)) as v(k, e);
select results_eq(
  $$select k, private.flag_enabled(k, (select lan from people)), private.flag_enabled(k, (select admin from people)), private.flag_enabled(k, null)
    from unnest(array['phone_otp_verification', 'app_update_gate', 'notifications']) with ordinality as t(k, n) order by n$$,
  $$values ('phone_otp_verification', false, true, true), ('app_update_gate', true, false, false), ('notifications', false, true, true)$$,
  'resolution_order_override_beats_global');

select is(pg_temp.api_error($$select private.require_flag('notifications', (select lan from people))$$),
  'PGRST 503 feature_disabled', 'require_flag_refuses_when_off');
select is(pg_temp.api_error($$select private.require_flag('notifications', (select admin from people))$$),
  'no error', 'require_flag_passes_when_on');

select pg_temp.as_anon();
select is(public.feature_flags() -> 'flags', '{"phone_otp_verification": true, "app_update_gate": false, "notifications": true}'::jsonb,
  'anon_gets_globals_only');
select pg_temp.as_user((select lan from people));
select is(public.feature_flags() -> 'flags', '{"phone_otp_verification": false, "app_update_gate": true, "notifications": false}'::jsonb,
  'caller_gets_own_overrides');

select is(pg_temp.api_error('select public.admin_feature_flags()'), 'PGRST 403 forbidden', 'customer_cannot_read_admin_view');
select is(pg_temp.api_error($$select public.set_feature_flag('notifications', true)$$), 'PGRST 403 forbidden', 'customer_cannot_write_flags');
select is(pg_temp.api_error($$select public.clear_feature_flag('notifications')$$), 'PGRST 403 forbidden', 'customer_cannot_clear_flags');
select is(pg_temp.api_error(format($$select public.set_feature_flag_for_user('notifications', %L, true)$$, (select lan from people))),
  'PGRST 403 forbidden', 'customer_cannot_override_users');
select is(pg_temp.api_error(format($$select public.clear_feature_flag_for_user('notifications', %L)$$, (select lan from people))),
  'PGRST 403 forbidden', 'customer_cannot_clear_overrides');

select pg_temp.as_anon();
select is(pg_temp.api_error($$select public.set_feature_flag('notifications', true)$$),
  '42501 permission denied for function set_feature_flag', 'anon_cannot_execute_admin_functions');

select pg_temp.as_user((select admin from people));
select is(pg_temp.api_error($$select public.set_feature_flag('nope', true)$$), 'PGRST 404 unknown_flag', 'unknown_flag_is_404');
select is(pg_temp.api_error($$select public.clear_feature_flag('nope')$$), 'PGRST 404 unknown_flag', 'unknown_flag_clear_is_404');

select is((select (f ->> 'enabled') || '|' || (f ->> 'globalEnabled') || '|' || (f ->> 'deployDefault')
           from jsonb_array_elements(public.set_feature_flag('notifications', false) -> 'flags') f where f ->> 'key' = 'notifications'),
  'false|false|false', 'set_global_returns_admin_view');

select pg_temp.as_postgres();
select is((select updated_by from public.feature_flags where key = 'notifications'), (select admin::text from people), 'set_global_records_actor');
select pg_temp.as_user((select admin from people));

select is((select jsonb_agg(f ->> 'key') from jsonb_array_elements(public.admin_feature_flags() -> 'flags') f),
  '["phone_otp_verification", "app_update_gate", "notifications"]'::jsonb, 'admin_view_lists_flags_in_catalog_order');

select is((select f -> 'overrides' from jsonb_array_elements(public.admin_feature_flags() -> 'flags') f where f ->> 'key' = 'app_update_gate'),
  jsonb_build_array(jsonb_build_object('userId', (select lan from people), 'enabled', true, 'note', null)), 'admin_view_lists_overrides');

select is((select (f ->> 'enabled') || '|' || coalesce(f ->> 'globalEnabled', 'null')
           from jsonb_array_elements(public.clear_feature_flag('notifications') -> 'flags') f where f ->> 'key' = 'notifications'),
  'false|null', 'clear_global_falls_back_to_default');
select is(pg_temp.api_error($$select public.clear_feature_flag('notifications')$$), 'PGRST 404 invalid_request', 'clear_global_not_stored_is_404');

select is(pg_temp.api_error($$select public.set_feature_flag_for_user('notifications', gen_random_uuid(), true)$$),
  'PGRST 404 invalid_request', 'override_unknown_user_is_404');
select is((select f -> 'overrides' -> 0 ->> 'note'
           from jsonb_array_elements(public.set_feature_flag_for_user('notifications', (select lan from people), true, 'beta tester') -> 'flags') f
           where f ->> 'key' = 'notifications'),
  'beta tester', 'override_stores_note');
select is((select jsonb_array_length(f -> 'overrides')
           from jsonb_array_elements(public.clear_feature_flag_for_user('notifications', (select lan from people)) -> 'flags') f where f ->> 'key' = 'notifications'),
  0, 'clear_override_removes_it');
select is(pg_temp.api_error(format($$select public.clear_feature_flag_for_user('notifications', %L)$$, (select lan from people))),
  'PGRST 404 invalid_request', 'clear_override_not_stored_is_404');

select * from finish();
rollback;

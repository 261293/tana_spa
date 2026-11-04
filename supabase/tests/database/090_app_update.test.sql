begin;
\ir _helpers.psql
select plan(16);

create temp table ctx as
select pg_temp.new_user('admin', 'root@test.local') as root,
       pg_temp.new_user('customer', 'hoa@test.local') as hoa;
grant select on ctx to authenticated, anon;

select pg_temp.as_anon();
select is(public.app_update_config(), '{"minimumVersion":"1.0.0","latestVersion":"1.0.0","forceUpdate":false}'::jsonb, 'config_is_public');

select pg_temp.as_user((select root from ctx));
select is(
  public.save_app_update_config('1.2', ' 1.10.0 ', true), '{"minimumVersion":"1.2.0","latestVersion":"1.10.0","forceUpdate":true}'::jsonb,
  'save_normalises_and_returns_the_config');
select is(public.app_update_config() ->> 'latestVersion', '1.10.0', 'saved_config_is_read_back');
select pg_temp.as_postgres();
select is(
  (select updated_by from public.app_update_config), (select root::text from ctx),
  'updated_by_is_the_admin');
select pg_temp.as_user((select root from ctx));

select is(
  pg_temp.api_error($$select public.save_app_update_config('1.10.0', '1.9.0', false)$$), 'PGRST 400 invalid_request',
  'minimum_above_latest_is_400');
select is(
  public.save_app_update_config('1.9.0', '1.10.0', false) ->> 'minimumVersion', '1.9.0',
  'versions_compare_numerically');
select is(public.save_app_update_config('2.0.0', '2.0.0', false) ->> 'latestVersion', '2.0.0', 'equal_versions_are_allowed');
select is(public.save_app_update_config('0', '0.0.0', false) ->> 'minimumVersion', '0.0.0', 'one_segment_is_zero_filled');
select is(pg_temp.api_error($$select public.save_app_update_config('1.2.3.4', '2.0.0', false)$$), 'PGRST 400 invalid_request', 'four_segments_are_refused');
select is(pg_temp.api_error($$select public.save_app_update_config('1.x', '2.0.0', false)$$), 'PGRST 400 invalid_request', 'non_numeric_is_refused');
select is(pg_temp.api_error($$select public.save_app_update_config('', '2.0.0', false)$$), 'PGRST 400 invalid_request', 'empty_is_refused');
select is(pg_temp.api_error($$select public.save_app_update_config('1.0.0', '99999999999.0.0', false)$$), 'PGRST 400 invalid_request', 'int_overflow_is_refused');

select pg_temp.as_user((select hoa from ctx));
select is(pg_temp.api_error($$select public.save_app_update_config('1.0.0', '2.0.0', false)$$), 'PGRST 403 forbidden', 'customer_cannot_save');

select pg_temp.as_anon();
select is(pg_temp.api_error($$select public.save_app_update_config('1.0.0', '2.0.0', false)$$), '42501 permission denied for function save_app_update_config', 'anon_cannot_save');

select pg_temp.as_postgres();
insert into public.feature_flags (key, enabled, updated_by) values ('app_update_gate', false, 'test');
select pg_temp.as_anon();
select is(pg_temp.api_error($$select public.app_update_config()$$), 'PGRST 503 feature_disabled', 'gate_off_is_503');

select pg_temp.as_postgres();
update public.feature_flags set enabled = true where key = 'app_update_gate';
delete from public.app_update_config;
select pg_temp.as_anon();
select is(public.app_update_config(), null::jsonb, 'no_row_answers_null');

select * from finish();
rollback;

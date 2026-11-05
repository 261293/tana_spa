begin;
\ir _helpers.psql
select plan(19);

create temp table ctx as
select pg_temp.new_user('admin', 'root@test.local') as root,
       pg_temp.new_user('owner', 'chu@test.local') as chu,
       pg_temp.new_user('staff', 'desk@test.local') as desk,
       pg_temp.new_user('customer', 'hoa@test.local') as hoa;
grant select on ctx to authenticated, anon;

create function pg_temp.vrow(wanted text) returns public.vouchers
language plpgsql as $$
declare
    r public.vouchers;
begin
    perform pg_temp.as_postgres();
    select * into r from public.vouchers v where v.code = wanted;
    perform pg_temp.as_user((select root from ctx));
    return r;
end
$$;

create function pg_temp.row_of(listing jsonb, wanted text) returns jsonb
language sql as $$
    select c from jsonb_array_elements(listing -> 'codes') c where c ->> 'code' = wanted
$$;

select pg_temp.as_user((select root from ctx));

select is(
  public.spa_create_code('t-dong', 'Dong off', null, 375000, 500000, '{MASSAGE,FACIAL}'::text[]) ->> 'codes' is not null, true,
  'create_returns_the_list');
select is(
  pg_temp.row_of(public.spa_admin_codes(), 'T-DONG') ->> 'offer', '375.000đ off',
  'admin_lists_codes_in_dong');
select is(pg_temp.row_of(public.spa_admin_codes(), 'T-DONG') ->> 'eligible', 'Massage - Facial', 'eligible_names_are_capitalised');
select is(
  (pg_temp.vrow('T-DONG')).eligible_categories, array['massage', 'facial'],
  'eligible_is_stored_lowercase');
select is(
  (select (v.min_order_vnd, v.applies_to, v.kind, v.title) from pg_temp.vrow('T-DONG') v),
  row(500000::bigint, 'service'::text, 'promo'::text, 'Dong off'::text), 'create_stores_threshold_and_defaults');

select public.spa_create_code('  t-pct ', '  Ten  ', 10);
select is(pg_temp.row_of(public.spa_admin_codes(), 'T-PCT') ->> 'offer', '10% off', 'percent_offer_text');
select is(pg_temp.row_of(public.spa_admin_codes(), 'T-PCT') ->> 'eligible', 'All treatments', 'no_categories_means_all');
select is((pg_temp.vrow('T-PCT')).title, 'Ten', 'title_is_trimmed');
select is(pg_temp.row_of(public.spa_admin_codes(), 'T-PCT') ->> 'state', 'LIVE', 'new_code_is_live');

select is(
  pg_temp.api_error($$select public.spa_create_code('T-BOTH', 'x', 10, 5000)$$), 'PGRST 400 invalid_request',
  'create_both_percent_and_amount_is_400');
select is(
  pg_temp.api_error($$select public.spa_create_code('T-NONE', 'x')$$), 'PGRST 400 invalid_request',
  'create_neither_is_400');
select is(
  pg_temp.api_error($$select public.spa_create_code('t-pct', 'again', 20)$$), 'PGRST 409 duplicate_code',
  'create_duplicate_is_409');
select is((pg_temp.vrow('T-PCT')).discount_value, 10::bigint, 'duplicate_never_overwrites');

select pg_temp.as_postgres();
update public.vouchers set is_active = true, starts_at = now() + interval '1 day', ends_at = now() + interval '2 days' where code = 'T-DONG';
update public.vouchers set starts_at = now() - interval '2 days', ends_at = now() - interval '1 day' where code = 'T-PCT';
select pg_temp.as_user((select root from ctx));
select is(
  array[pg_temp.row_of(public.spa_admin_codes(), 'T-DONG') ->> 'state', pg_temp.row_of(public.spa_admin_codes(), 'T-PCT') ->> 'state'],
  array['SCHEDULED', 'EXPIRED'], 'state_follows_the_window');

select pg_temp.as_postgres();
update public.vouchers set starts_at = now() - interval '1 day', ends_at = now() - interval '1 hour' where code = 'T-DONG';
select pg_temp.as_user((select root from ctx));
select is(
  (select jsonb_path_query_array(public.spa_disable_code('T-DONG'), '$.codes[*] ? (@.code == "T-DONG").state') ->> 0), 'DISABLED',
  'disabled_code_shows_disabled');
select is(pg_temp.api_error($$select public.spa_disable_code('T-DONG')$$), 'PGRST 404 invalid_code', 'disable_twice_is_404');
select is(pg_temp.api_error($$select public.spa_disable_code('NOPE-NOPE')$$), 'PGRST 404 invalid_code', 'disable_unknown_is_404');

select is(
  (select array_agg(r) from (
     select pg_temp.api_error('select public.spa_admin_codes()') r from (select pg_temp.as_user((select chu from ctx))) a
     union all
     select pg_temp.api_error('select public.spa_admin_codes()') from (select pg_temp.as_user((select desk from ctx))) a
     union all
     select pg_temp.api_error('select public.spa_create_code(''X-CODE'', ''x'', 5)') from (select pg_temp.as_user((select hoa from ctx))) a
  ) q),
  array['PGRST 403 forbidden', 'PGRST 403 forbidden', 'PGRST 403 forbidden'], 'owner_staff_customer_cannot_list_codes');

select pg_temp.as_anon();
select is(pg_temp.api_error('select public.spa_admin_codes()'), '42501 permission denied for function spa_admin_codes', 'anon_cannot_list_codes');

select * from finish();
rollback;

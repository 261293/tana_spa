begin;
\ir _helpers.psql
select plan(18);

create temp table people as
select pg_temp.new_user('customer', 'lan@test.local', '{"full_name": "Lan", "phone": "0912345678"}') as lan,
       pg_temp.new_user('admin', 'admin@test.local', '{"full_name": "Quản Trị"}') as admin,
       pg_temp.new_user('owner', 'owner@test.local', '{"full_name": "Chủ Spa"}') as owner,
       pg_temp.new_user('staff', 'staff@test.local', '{"full_name": "Thợ"}') as staff,
       pg_temp.new_user('customer', 'Sáng.Nguyen@Test.local', '{"full_name": "Sáng Nguyễn"}') as sang,
       pg_temp.new_user('customer', 'nameless@test.local') as nameless;
grant select on people to authenticated, anon;

select is(
  (select role || '|' || full_name || '|' || phone || '|' || (phone_verified_at is null) from public.users where id = (select lan from people)),
  'customer|Lan|+84912345678|true', 'signup_creates_customer_profile');

select is(
  (select email || '|' || full_name from public.users where id = (select sang from people)) || '/' ||
  (select full_name from public.users where id = (select nameless from people)),
  'sáng.nguyen@test.local|Sáng Nguyễn/nameless', 'signup_lowercases_email_and_defaults_name');

select results_eq(
  $$select private.normalize_phone(raw) from unnest(array['0912 345 678', '84912345678', '+84912345678', '12']) with ordinality as t(raw, n) order by n$$,
  $$values ('+84912345678'), ('+84912345678'), ('+84912345678'), (null::text)$$,
  'normalize_phone_shapes');

select results_eq(
  $$select r, p, private.has_permission(uid, p)
    from (values ('admin', (select admin from people)), ('owner', (select owner from people)),
                 ('staff', (select staff from people)), ('customer', (select lan from people))) as u(r, uid)
    cross join unnest(array['MANAGE_BOOKINGS', 'MANAGE_SERVICES', 'MANAGE_THERAPISTS', 'OPERATE_SYSTEM', 'MANAGE_ACCOUNTS']) as p
    order by r, p$$,
  $$values ('admin', 'MANAGE_ACCOUNTS', true), ('admin', 'MANAGE_BOOKINGS', true), ('admin', 'MANAGE_SERVICES', true),
           ('admin', 'MANAGE_THERAPISTS', true), ('admin', 'OPERATE_SYSTEM', true),
           ('customer', 'MANAGE_ACCOUNTS', false), ('customer', 'MANAGE_BOOKINGS', false), ('customer', 'MANAGE_SERVICES', false),
           ('customer', 'MANAGE_THERAPISTS', false), ('customer', 'OPERATE_SYSTEM', false),
           ('owner', 'MANAGE_ACCOUNTS', true), ('owner', 'MANAGE_BOOKINGS', true), ('owner', 'MANAGE_SERVICES', true),
           ('owner', 'MANAGE_THERAPISTS', true), ('owner', 'OPERATE_SYSTEM', false),
           ('staff', 'MANAGE_ACCOUNTS', false), ('staff', 'MANAGE_BOOKINGS', true), ('staff', 'MANAGE_SERVICES', false),
           ('staff', 'MANAGE_THERAPISTS', false), ('staff', 'OPERATE_SYSTEM', false)$$,
  'permission_matrix');

update auth.users set phone = '84900000001', phone_confirmed_at = now() where id = (select staff from people);
select is(
  (select phone || '|' || (phone_verified_at is not null) from public.users where id = (select staff from people)),
  '+84900000001|true', 'confirmed_phone_copies_to_profile');

select pg_temp.as_user((select lan from people));
select is(
  (select me ->> 'role' || '|' || (me ->> 'phoneVerified') || '|' || (me ->> 'displayName') || '|' || (me ->> 'phone') from public.me() as me),
  'customer|false|Lan|+84912345678', 'me_returns_user_dto');
select is(pg_temp.api_error('select public.accounts_change_role((select staff from people), ''customer'')'),
  'PGRST 403 forbidden', 'customer_forbidden');

select pg_temp.as_user((select owner from people));
select is(pg_temp.api_error('select public.accounts_change_role((select owner from people), ''staff'')'),
  'PGRST 403 own_role_unchangeable', 'own_role_unchangeable');
select is(pg_temp.api_error('select public.accounts_change_role(gen_random_uuid(), ''staff'')'),
  'PGRST 404 unknown_account', 'unknown_account');
select is(pg_temp.api_error('select public.accounts_change_role((select lan from people), ''admin'')'),
  'PGRST 403 role_not_allowed', 'owner_cannot_make_admin');
select is(pg_temp.api_error('select public.accounts_change_role((select lan from people), ''wizard'')'),
  'PGRST 403 role_not_allowed', 'unknown_role_not_allowed');
select is(public.accounts_change_role((select lan from people), 'staff') ->> 'role', 'staff', 'owner_can_make_staff');
select is(pg_temp.api_error('select public.admin_users(''lan'')'), 'PGRST 403 forbidden', 'admin_users_needs_operate_system');

select pg_temp.as_user((select admin from people));
select is(public.admin_users('sang nguyen'),
  jsonb_build_object('users', jsonb_build_array(jsonb_build_object(
    'id', (select sang from people), 'displayName', 'Sáng Nguyễn', 'email', 'sáng.nguyen@test.local', 'role', 'customer'))),
  'admin_users_matches_unaccented_name');
select is(public.admin_users('  '), '{"users": []}'::jsonb, 'admin_users_blank_is_empty');

select pg_temp.as_user(null);
select is(pg_temp.api_error('select public.me()'), 'PGRST 401 unauthorized', 'me_requires_a_user');

select pg_temp.as_anon();
select throws_ok('select public.me()', '42501', 'permission denied for function me', 'anon_cannot_execute_me');
select throws_ok('select private.app_role(gen_random_uuid())', '42501', 'permission denied for schema private', 'anon_cannot_execute_private');

select pg_temp.as_postgres();
select * from finish();
rollback;

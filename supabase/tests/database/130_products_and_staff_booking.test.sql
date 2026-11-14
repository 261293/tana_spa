begin;
\ir _helpers.psql
select plan(9);

create temp table ctx as
select pg_temp.new_user('customer', 'lan@test.local', '{"full_name": "Lan"}') as lan,
       pg_temp.new_user('staff', 'desk@test.local') as desk,
       pg_temp.new_user('owner', 'chu@test.local') as chu,
       pg_temp.new_user('admin', 'ops@test.local') as ops,
       (now() at time zone 'Asia/Ho_Chi_Minh')::date as today;
grant select on ctx to authenticated, anon;

create function pg_temp.at(days int, clock time) returns bigint
language sql as $$
    select (extract(epoch from (((select today from ctx) + days + clock) at time zone 'Asia/Ho_Chi_Minh')) * 1000)::bigint
$$;

select pg_temp.as_anon();
select is(public.spa_products() -> 'products', '[]'::jsonb, 'the_shelf_starts_empty_and_anyone_can_read_it');

select pg_temp.as_user((select lan from ctx));
select is(pg_temp.api_error($$select public.spa_create_product('Serum', 'Tana', 'Serum dưỡng ẩm', 450000, '30ml')$$), 'PGRST 403 forbidden',
  'a_guest_cannot_stock_the_shelf');

select pg_temp.as_user((select chu from ctx));
select is(public.spa_create_product('  Serum trà xanh ', 'Tana', 'Serum dưỡng ẩm', 450000, '30ml') - 'imageUrl',
  '{"slug": "serum-trà-xanh", "name": "Serum trà xanh", "brand": "Tana", "description": "Serum dưỡng ẩm", "price": 450000, "size": "30ml"}'::jsonb,
  'the_owner_adds_a_product_with_its_price_and_size');
select is(public.spa_create_product('Serum trà xanh', null, 'Bản lớn', 800000) ->> 'slug', 'serum-trà-xanh-2',
  'a_second_product_of_the_same_name_gets_its_own_slug');
select is(pg_temp.api_error($$select public.spa_create_product('Dầu', null, 'Dầu massage', -1)$$), 'PGRST 400 invalid_request',
  'a_negative_price_is_refused');

select pg_temp.as_anon();
select is((select array_agg(p ->> 'name' || ':' || (p ->> 'price') order by p ->> 'slug') from jsonb_array_elements(public.spa_products() -> 'products') p),
  array['Serum trà xanh:450000', 'Serum trà xanh:800000'], 'every_role_sees_the_shelf');

select pg_temp.as_user((select desk from ctx));
select is(pg_temp.api_error($$select public.spa_create_booking('jade-roller-facial', pg_temp.at(2, '10:00'))$$), 'PGRST 403 staff_cannot_book',
  'staff_do_not_book');

select pg_temp.as_user((select chu from ctx));
select ok(public.spa_create_booking('jade-roller-facial', pg_temp.at(2, '10:00')) ->> 'id' is not null, 'the_owner_can_book');

select pg_temp.as_user((select ops from ctx));
select ok(public.spa_create_booking('jade-roller-facial', pg_temp.at(2, '11:30')) ->> 'id' is not null, 'an_admin_can_book');

select * from finish();
rollback;

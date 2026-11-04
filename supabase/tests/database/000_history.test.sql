begin;
\ir _helpers.psql
select plan(4);

select is((select count(*)::int from pg_tables where schemaname = 'public'), 33, 'schema_has_33_tables');
select is((select count(*)::int from pg_tables where schemaname = 'public' and not rowsecurity), 0, 'every_table_has_rls');
select ok(not has_table_privilege('anon', 'public.users', 'select'), 'anon_cannot_read_users');
select lives_ok($$select pg_temp.as_user(pg_temp.new_user())$$, 'helpers_load');

select * from finish();
rollback;

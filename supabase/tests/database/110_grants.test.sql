begin;
select plan(4);

create function pg_temp.own_functions(schema_name text) returns setof pg_proc
language sql as $$
    select p.* from pg_proc p
    where p.pronamespace = schema_name::regnamespace
      and not exists (select 1 from pg_depend d where d.objid = p.oid and d.deptype = 'e')
$$;

select is(
    (select array_agg(proname::text order by proname) from pg_temp.own_functions('public')
     where has_function_privilege('anon', oid, 'execute') and proname not in ('app_unaccent', 'set_updated_at')),
    array['app_update_config', 'feature_flags', 'spa_availability', 'spa_catalog', 'spa_check_offer', 'spa_offers'],
    'anon_executes_only_the_public_reads'
);

select is(
    (select count(*)::int from pg_temp.own_functions('public')
     where prosecdef and coalesce(proconfig, '{}') <> array['search_path=""']),
    0,
    'every_security_definer_function_pins_an_empty_search_path'
);

select is(
    (select count(*)::int from pg_temp.own_functions('private')
     where has_function_privilege('anon', oid, 'execute') or has_function_privilege('authenticated', oid, 'execute')),
    0,
    'private_functions_are_closed_to_the_api_roles'
);

create function public.grants_probe() returns int language sql as 'select 1';
select ok(
    not has_function_privilege('anon', 'public.grants_probe()', 'execute')
    and not has_function_privilege('authenticated', 'public.grants_probe()', 'execute'),
    'a_new_public_function_is_not_executable_by_default'
);

select * from finish();
rollback;

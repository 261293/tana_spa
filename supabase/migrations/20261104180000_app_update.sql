create function private.parse_version(raw text) returns int[]
    language plpgsql immutable set search_path = ''
as $$
declare
    segments text[] := string_to_array(btrim(raw, E' \t\r\n'), '.');
    numbers int[] := '{}';
    segment text;
    parsed bigint;
begin
    if cardinality(segments) not between 1 and 3 then
        return null;
    end if;
    foreach segment in array segments loop
        if segment !~ '^[+-]?[0-9]+$' then
            return null;
        end if;
        parsed := segment::numeric;
        if parsed < 0 or parsed > 2147483647 then
            return null;
        end if;
        numbers := numbers || parsed::int;
    end loop;
    while cardinality(numbers) < 3 loop
        numbers := numbers || 0;
    end loop;
    return numbers;
end
$$;

create function private.app_update_dto() returns jsonb
    language sql stable set search_path = ''
as $$
    select jsonb_build_object('minimumVersion', c.minimum_version, 'latestVersion', c.latest_version, 'forceUpdate', c.force_update)
    from public.app_update_config c
    where c.id = 'global'
$$;

create function public.app_update_config() returns jsonb
    language plpgsql stable security definer set search_path = ''
as $$
begin
    perform private.require_flag('app_update_gate', null);
    return private.app_update_dto();
end
$$;

create function public.save_app_update_config("minimumVersion" text, "latestVersion" text, "forceUpdate" boolean) returns jsonb
    language plpgsql security definer set search_path = ''
as $$
declare
    actor uuid := private.require_permission('OPERATE_SYSTEM');
    minimum int[] := private.parse_version("minimumVersion");
    latest int[] := private.parse_version("latestVersion");
begin
    if minimum is null then
        perform private.fail('invalid_request', 400, 'minimumVersion must look like 1.2.3');
    end if;
    if latest is null then
        perform private.fail('invalid_request', 400, 'latestVersion must look like 1.2.3');
    end if;
    if minimum > latest then
        perform private.fail('invalid_request', 400, 'minimumVersion cannot be newer than latestVersion');
    end if;
    insert into public.app_update_config (id, minimum_version, latest_version, force_update, updated_by)
    values ('global', array_to_string(minimum, '.'), array_to_string(latest, '.'), coalesce("forceUpdate", false), actor::text)
    on conflict (id) do update set
        minimum_version = excluded.minimum_version,
        latest_version = excluded.latest_version,
        force_update = excluded.force_update,
        updated_by = excluded.updated_by;
    return private.app_update_dto();
end
$$;

revoke all on function private.parse_version(text), private.app_update_dto() from public, anon, authenticated;
revoke execute on function public.app_update_config(), public.save_app_update_config(text, text, boolean) from public, anon, authenticated;
grant execute on function public.app_update_config() to anon, authenticated;
grant execute on function public.save_app_update_config(text, text, boolean) to authenticated;

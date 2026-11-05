create function private.flag_catalog() returns table (ord int, key text, default_enabled boolean, description text)
    language sql immutable set search_path = ''
as $$
    select * from (values
        (1, 'phone_otp_verification', false, 'Phone-number verification with SMS one-time codes'),
        (2, 'app_update_gate', true, 'Force and optional app-update prompts driven by app_update_config'),
        (3, 'notifications', false, 'Notification centre and push delivery to registered devices')
    ) as c(ord, key, default_enabled, description)
$$;

create function private.flag_default(key text) returns boolean
    language sql immutable set search_path = ''
as $$
    select c.default_enabled from private.flag_catalog() c where c.key = flag_default.key
$$;

create function private.flag_description(key text) returns text
    language sql immutable set search_path = ''
as $$
    select c.description from private.flag_catalog() c where c.key = flag_description.key
$$;

create function private.flag_enabled(key text, uid uuid) returns boolean
    language sql stable set search_path = ''
as $$
    select coalesce(
        (select o.enabled from public.feature_flag_overrides o where o.flag_key = key and o.user_id = uid),
        (select f.enabled from public.feature_flags f where f.key = flag_enabled.key),
        private.flag_default(key)
    )
$$;

create function private.require_flag(key text, uid uuid) returns void
    language plpgsql stable set search_path = ''
as $$
begin
    if private.flag_enabled(key, uid) is not true then
        perform private.fail('feature_disabled', 503, 'This feature is switched off');
    end if;
end
$$;

create function private.flag_admin_view() returns jsonb
    language sql stable set search_path = ''
as $$
    select jsonb_build_object('flags', coalesce(jsonb_agg(
        jsonb_build_object(
            'key', c.key,
            'description', private.flag_description(c.key),
            'enabled', coalesce(g.enabled, private.flag_default(c.key)),
            'globalEnabled', g.enabled,
            'deployDefault', private.flag_default(c.key),
            'overrides', coalesce((
                select jsonb_agg(jsonb_build_object('userId', o.user_id, 'enabled', o.enabled, 'note', o.note)
                                 order by o.created_at, o.user_id)
                from public.feature_flag_overrides o where o.flag_key = c.key
            ), '[]'::jsonb)
        ) order by c.ord), '[]'::jsonb))
    from private.flag_catalog() c
    left join public.feature_flags g on g.key = c.key
$$;

create function private.require_known_flag(key text) returns void
    language plpgsql stable set search_path = ''
as $$
begin
    if not exists (select 1 from private.flag_catalog() c where c.key = require_known_flag.key) then
        perform private.fail('unknown_flag', 404, 'No such feature flag');
    end if;
end
$$;

revoke all on function private.flag_catalog(), private.flag_default(text), private.flag_description(text), private.flag_enabled(text, uuid),
    private.require_flag(text, uuid), private.flag_admin_view(), private.require_known_flag(text) from public;

create function public.feature_flags() returns jsonb
    language sql stable security definer set search_path = ''
as $$
    select jsonb_build_object('flags', jsonb_object_agg(c.key, private.flag_enabled(c.key, auth.uid())))
    from private.flag_catalog() c
$$;

create function public.admin_feature_flags() returns jsonb
    language plpgsql stable security definer set search_path = ''
as $$
begin
    perform private.require_permission('OPERATE_SYSTEM');
    return private.flag_admin_view();
end
$$;

create function public.set_feature_flag(key text, enabled boolean) returns jsonb
    language plpgsql security definer set search_path = ''
as $$
declare
    actor uuid := private.require_permission('OPERATE_SYSTEM');
begin
    perform private.require_known_flag(key);
    insert into public.feature_flags (key, enabled, description, updated_by)
    values (key, enabled, private.flag_description(key), actor::text)
    on conflict on constraint feature_flags_pkey do update set enabled = excluded.enabled, updated_by = excluded.updated_by;
    return private.flag_admin_view();
end
$$;

create function public.clear_feature_flag(key text) returns jsonb
    language plpgsql security definer set search_path = ''
as $$
begin
    perform private.require_permission('OPERATE_SYSTEM');
    perform private.require_known_flag(key);
    delete from public.feature_flags f where f.key = clear_feature_flag.key;
    if not found then
        perform private.fail('invalid_request', 404, 'No global state is stored for this flag');
    end if;
    return private.flag_admin_view();
end
$$;

create function public.set_feature_flag_for_user(key text, "userId" uuid, enabled boolean, note text default null) returns jsonb
    language plpgsql security definer set search_path = ''
as $$
declare
    actor uuid := private.require_permission('OPERATE_SYSTEM');
begin
    perform private.require_known_flag(key);
    if not exists (select 1 from public.users u where u.id = "userId") then
        perform private.fail('invalid_request', 404, 'No such user');
    end if;
    insert into public.feature_flag_overrides (flag_key, user_id, enabled, note, updated_by)
    values (key, "userId", enabled, note, actor::text)
    on conflict on constraint feature_flag_overrides_pkey do update
        set enabled = excluded.enabled, note = excluded.note, updated_by = excluded.updated_by;
    return private.flag_admin_view();
end
$$;

create function public.clear_feature_flag_for_user(key text, "userId" uuid) returns jsonb
    language plpgsql security definer set search_path = ''
as $$
begin
    perform private.require_permission('OPERATE_SYSTEM');
    perform private.require_known_flag(key);
    delete from public.feature_flag_overrides o
    where o.flag_key = clear_feature_flag_for_user.key and o.user_id = "userId";
    if not found then
        perform private.fail('invalid_request', 404, 'This user has no override for this flag');
    end if;
    return private.flag_admin_view();
end
$$;

revoke execute on function public.feature_flags(), public.admin_feature_flags(), public.set_feature_flag(text, boolean),
    public.clear_feature_flag(text), public.set_feature_flag_for_user(text, uuid, boolean, text),
    public.clear_feature_flag_for_user(text, uuid) from public, anon, authenticated;
grant execute on function public.feature_flags() to anon, authenticated;
grant execute on function public.admin_feature_flags(), public.set_feature_flag(text, boolean),
    public.clear_feature_flag(text), public.set_feature_flag_for_user(text, uuid, boolean, text),
    public.clear_feature_flag_for_user(text, uuid) to authenticated;

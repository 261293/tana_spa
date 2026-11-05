drop table public.refresh_tokens;
drop table public.phone_verifications;
alter table public.users drop column password_hash;
alter table public.users
    add constraint users_id_auth_fk foreign key (id) references auth.users (id) on delete cascade;

create schema private;
revoke all on schema private from public, anon, authenticated;

create function private.fail(code text, status int, message text default null) returns void
    language plpgsql set search_path = ''
as $$
begin
    raise sqlstate 'PGRST' using
        message = json_build_object('code', code, 'message', coalesce(message, replace(code, '_', ' ')))::text,
        detail = json_build_object('status', status, 'headers', json_build_object())::text;
end
$$;

create function private.caller() returns uuid
    language plpgsql stable set search_path = ''
as $$
declare
    uid uuid := auth.uid();
begin
    if uid is null then
        perform private.fail('unauthorized', 401, 'Authentication required');
    end if;
    return uid;
end
$$;

create function private.app_role(uid uuid) returns text
    language sql stable set search_path = ''
as $$
    select u.role from public.users u where u.id = uid and u.deleted_at is null and u.status = 'active'
$$;

create function private.has_permission(uid uuid, permission text) returns boolean
    language sql stable set search_path = ''
as $$
    select permission = any(case private.app_role(uid)
        when 'admin' then array['MANAGE_BOOKINGS', 'MANAGE_SERVICES', 'MANAGE_THERAPISTS', 'OPERATE_SYSTEM', 'MANAGE_ACCOUNTS']
        when 'owner' then array['MANAGE_BOOKINGS', 'MANAGE_SERVICES', 'MANAGE_THERAPISTS', 'MANAGE_ACCOUNTS']
        when 'staff' then array['MANAGE_BOOKINGS']
        else array[]::text[]
    end)
$$;

create function private.require_permission(permission text) returns uuid
    language plpgsql stable set search_path = ''
as $$
declare
    uid uuid := private.caller();
begin
    if private.app_role(uid) is null then
        perform private.fail('unauthorized', 401, 'Authentication required');
    end if;
    if not private.has_permission(uid, permission) then
        perform private.fail('forbidden', 403, case permission
            when 'MANAGE_BOOKINGS' then 'This account does not work the booking desk'
            when 'MANAGE_SERVICES' then 'This account cannot change the menu'
            when 'MANAGE_THERAPISTS' then 'This account cannot change the therapist roster'
            when 'OPERATE_SYSTEM' then 'This account is not an administrator'
            when 'MANAGE_ACCOUNTS' then 'This account cannot create accounts'
        end);
    end if;
    return uid;
end
$$;

create function private.can_create(actor text, target text) returns boolean
    language sql immutable set search_path = ''
as $$
    select coalesce(target = any(case actor
        when 'admin' then array['admin', 'owner', 'staff', 'customer']
        when 'owner' then array['staff', 'customer']
        else array[]::text[]
    end), false)
$$;

create function private.normalize_phone(raw text) returns text
    language sql immutable set search_path = ''
as $$
    select case when shaped ~ '^\+[0-9]{8,15}$' then shaped end
    from (
        select case
            when digits like '+%' then digits
            when digits like '0%' then '+84' || substr(digits, 2)
            when digits like '84%' then '+' || digits
        end as shaped
        from (select regexp_replace(raw, '[[:space:].-]', '', 'g') as digits) stripped
    ) normalized
$$;

create function private.vietnamese() returns boolean
    language sql stable set search_path = ''
as $$
    select coalesce(nullif(current_setting('request.headers', true), '')::json ->> 'accept-language', '') ilike 'vi%'
$$;

create function private.house_zone() returns text
    language sql stable set search_path = ''
as $$
    select s.timezone from public.spa_settings s where s.id = 'global'
$$;

create function private.grant_voucher(uid uuid, code text, source text) returns boolean
    language sql set search_path = ''
as $$
    select false
$$;

-- security definer: fired by the Auth service role, which cannot write public.users itself.
create function private.on_auth_user_created() returns trigger
    language plpgsql security definer set search_path = ''
as $$
begin
    insert into public.users (id, email, full_name, phone, role)
    values (
        new.id,
        lower(new.email),
        left(coalesce(nullif(btrim(new.raw_user_meta_data ->> 'full_name'), ''), split_part(new.email, '@', 1)), 100),
        private.normalize_phone(new.raw_user_meta_data ->> 'phone'),
        'customer'
    );
    -- a failing welcome grant must never fail the sign-up
    begin
        perform private.grant_voucher(new.id, 'FIRSTVISIT', 'gift');
    exception when others then
        null;
    end;
    return new;
end
$$;

-- security definer: fired by the Auth service role, which cannot write public.users itself.
create function private.on_auth_phone_confirmed() returns trigger
    language plpgsql security definer set search_path = ''
as $$
begin
    update public.users
    set phone = '+' || ltrim(new.phone, '+'), phone_verified_at = new.phone_confirmed_at
    where id = new.id;
    return new;
end
$$;

create trigger on_auth_user_created
    after insert on auth.users
    for each row execute function private.on_auth_user_created();

create trigger on_auth_phone_confirmed
    after update of phone_confirmed_at on auth.users
    for each row
    when (new.phone_confirmed_at is not null
        and new.phone_confirmed_at is distinct from old.phone_confirmed_at
        and coalesce(new.phone, '') <> '')
    execute function private.on_auth_phone_confirmed();

revoke all on all functions in schema private from public, anon, authenticated;

create function public.me() returns jsonb
    language plpgsql stable security definer set search_path = ''
as $$
declare
    uid uuid := private.caller();
    profile jsonb;
begin
    select jsonb_build_object(
        'id', u.id,
        'email', u.email,
        'displayName', u.full_name,
        'phone', u.phone,
        'phoneVerified', u.phone_verified_at is not null,
        'role', u.role
    )
    into profile
    from public.users u
    where u.id = uid and u.deleted_at is null and u.status = 'active';
    if profile is null then
        perform private.fail('unauthorized', 401, 'Authentication required');
    end if;
    return profile;
end
$$;

create function public.accounts_change_role(id uuid, role text) returns jsonb
    language plpgsql security definer set search_path = ''
as $$
declare
    actor uuid := private.require_permission('MANAGE_ACCOUNTS');
    actor_role text := private.app_role(actor);
    target public.users;
begin
    if accounts_change_role.id = actor then
        perform private.fail('own_role_unchangeable', 403, 'An account cannot change its own role');
    end if;
    select * into target
    from public.users u
    where u.id = accounts_change_role.id and u.deleted_at is null and u.status = 'active'
    for update;
    if not found then
        perform private.fail('unknown_account', 404, 'No such account');
    end if;
    if not (private.can_create(actor_role, target.role) and private.can_create(actor_role, accounts_change_role.role)) then
        perform private.fail('role_not_allowed', 403, 'This account cannot make that change');
    end if;
    update public.users u set role = accounts_change_role.role where u.id = target.id;
    return jsonb_build_object('id', target.id, 'displayName', target.full_name, 'email', target.email, 'role', accounts_change_role.role);
end
$$;

create function public.admin_users(q text, "limit" int default 20) returns jsonb
    language plpgsql stable security definer set search_path = ''
as $$
declare
    needle text := btrim(coalesce(q, ''));
begin
    perform private.require_permission('OPERATE_SYSTEM');
    if needle = '' then
        return jsonb_build_object('users', jsonb_build_array());
    end if;
    return jsonb_build_object('users', coalesce((
        select jsonb_agg(jsonb_build_object('id', m.id, 'displayName', m.full_name, 'email', m.email, 'role', m.role) order by m.full_name)
        from (
            select u.id, u.full_name, u.email, u.role
            from public.users u
            where u.deleted_at is null and u.status = 'active'
              and (u.id::text = lower(needle)
                   or public.app_unaccent(u.full_name) ilike public.app_unaccent('%' || needle || '%')
                   or u.email ilike '%' || needle || '%')
            order by u.full_name
            limit greatest(1, least(50, coalesce(admin_users."limit", 20)))
        ) m
    ), jsonb_build_array()));
end
$$;

revoke execute on function public.me() from public, anon, authenticated;
revoke execute on function public.accounts_change_role(uuid, text) from public, anon, authenticated;
revoke execute on function public.admin_users(text, int) from public, anon, authenticated;
grant execute on function public.me() to authenticated;
grant execute on function public.accounts_change_role(uuid, text) to authenticated;
grant execute on function public.admin_users(text, int) to authenticated;

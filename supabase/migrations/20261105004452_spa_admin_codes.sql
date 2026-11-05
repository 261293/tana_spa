create function private.format_vnd(amount bigint) returns text
    language sql immutable set search_path = ''
as $$
    select regexp_replace(format_vnd.amount::text, '(\d)(?=(\d{3})+$)', '\1.', 'g') || 'đ'
$$;

create function private.announce(code text) returns int
    language sql set search_path = ''
as $$
    select 0
$$;

create function private.admin_code_dto(v public.vouchers) returns jsonb
    language sql stable set search_path = ''
as $$
    select jsonb_build_object(
        'code', v.code,
        'offer', case when v.discount_type = 'percent' then v.discount_value || '% off' else private.format_vnd(v.discount_value) || ' off' end,
        'eligible', case when coalesce(cardinality(v.eligible_categories), 0) = 0 then 'All treatments'
                         else (select string_agg(initcap(e.slug), ' - ' order by e.n) from unnest(v.eligible_categories) with ordinality e(slug, n)) end,
        'starts', to_char(v.starts_at at time zone 'UTC', 'FMMon FMDD'),
        'ends', to_char(v.ends_at at time zone 'UTC', 'FMMon FMDD'),
        'used', v.used_count,
        'limit', v.usage_limit,
        'state', case when not v.is_active then 'DISABLED'
                      when v.ends_at <= now() then 'EXPIRED'
                      when v.starts_at > now() then 'SCHEDULED'
                      else 'LIVE' end,
        'note', null)
$$;

create function private.admin_codes() returns jsonb
    language sql stable set search_path = ''
as $$
    select jsonb_build_object('codes', coalesce(jsonb_agg(private.admin_code_dto(v) order by v.starts_at desc), '[]'::jsonb))
    from public.vouchers v
$$;

create function public.spa_admin_codes() returns jsonb
    language plpgsql stable security definer set search_path = ''
as $$
begin
    perform private.require_permission('OPERATE_SYSTEM');
    return private.admin_codes();
end
$$;

create function public.spa_create_code(code text, title text, "percentOff" int default null, "amountOff" int default null,
                                       "minSpend" int default null, eligible text[] default '{}', "startsAtEpochMillis" bigint default null,
                                       "endsAtEpochMillis" bigint default null, "usageLimit" int default null) returns jsonb
    language plpgsql security definer set search_path = ''
as $$
declare
    inserted_id uuid;
    clean text := upper(btrim(spa_create_code.code, E' \t\r\n'));
begin
    perform private.require_permission('OPERATE_SYSTEM');
    if ("percentOff" is null) = ("amountOff" is null) then
        perform private.fail('invalid_request', 400, 'A code is either a percentage or an amount, not both');
    end if;
    insert into public.vouchers (code, title, discount_type, discount_value, min_order_vnd, applies_to, starts_at, ends_at, usage_limit,
                                 eligible_categories)
    values (clean, btrim(spa_create_code.title, E' \t\r\n'), case when "percentOff" is not null then 'percent' else 'fixed' end,
            coalesce("percentOff", "amountOff"), coalesce("minSpend", 0), 'service',
            coalesce(to_timestamp("startsAtEpochMillis" / 1000.0), now()), to_timestamp("endsAtEpochMillis" / 1000.0), "usageLimit",
            (select coalesce(array_agg(lower(c)), '{}') from unnest(coalesce(spa_create_code.eligible, '{}')) c))
    on conflict on constraint vouchers_code_uq do nothing
    returning id into inserted_id;
    if inserted_id is null then
        perform private.fail('duplicate_code', 409, 'That code already exists');
    end if;
    perform private.announce(clean);
    return private.admin_codes();
end
$$;

create function public.spa_disable_code(code text) returns jsonb
    language plpgsql security definer set search_path = ''
as $$
begin
    perform private.require_permission('OPERATE_SYSTEM');
    update public.vouchers v set is_active = false where v.code = spa_disable_code.code and v.is_active;
    if not found then
        perform private.fail('invalid_code', 404, 'No live code with that name');
    end if;
    return private.admin_codes();
end
$$;

revoke execute on function private.format_vnd(bigint), private.announce(text), private.admin_code_dto(public.vouchers),
    private.admin_codes() from public;
revoke execute on function public.spa_admin_codes(), public.spa_create_code(text, text, int, int, int, text[], bigint, bigint, int),
    public.spa_disable_code(text) from public, anon, authenticated;
grant execute on function public.spa_admin_codes(), public.spa_create_code(text, text, int, int, int, text[], bigint, bigint, int),
    public.spa_disable_code(text) to authenticated;

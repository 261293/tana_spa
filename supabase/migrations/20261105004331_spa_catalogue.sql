create function private.house_day(at timestamptz) returns int
    language sql stable set search_path = ''
as $$
    select (house_day.at at time zone private.house_zone())::date - date '1970-01-01'
$$;

create function private.hour_of_day(at timestamptz) returns float8
    language sql stable set search_path = ''
as $$
    select (extract(hour from l.t) + extract(minute from l.t) / 60.0)::float8
    from (select hour_of_day.at at time zone private.house_zone() as t) l
$$;

create function private.in_language(translated text, base text, vi boolean) returns text
    language sql immutable set search_path = ''
as $$
    select case when vi and coalesce(btrim(translated), '') <> '' then translated else base end
$$;

create function private.category_dto(slug text) returns text
    language sql immutable set search_path = ''
as $$
    select case lower(slug) when 'facial' then 'FACIAL' when 'body' then 'BODY' else 'MASSAGE' end
$$;

create function private.service_dto(s public.services, vi boolean) returns jsonb
    language sql stable set search_path = ''
as $$
    select jsonb_build_object(
        'slug', s.slug,
        'name', private.in_language(s.name_vi, s.name, vi),
        'minutes', s.duration_minutes,
        'price', s.price_vnd,
        'category', private.category_dto(c.slug),
        'includes', private.in_language(s.description_vi, coalesce(s.description, ''), vi))
    from public.service_categories c where c.id = s.category_id
$$;

create function private.therapist_dto(t public.therapists, vi boolean, next_slot jsonb) returns jsonb
    language sql immutable set search_path = ''
as $$
    select jsonb_build_object(
        'slug', t.slug,
        'name', t.name,
        'focus', private.in_language(t.focus_vi, t.focus, vi),
        'accent', case t.accent when 'dusk' then 'DUSK' when 'amber' then 'AMBER' else 'MOSS' end,
        'nextSlot', next_slot)
$$;

create function private.offer_dto(v public.vouchers, vi boolean) returns jsonb
    language sql stable set search_path = ''
as $$
    select jsonb_build_object(
        'code', v.code,
        'title', private.in_language(v.title_vi, v.title, vi),
        'terms', private.in_language(v.description_vi, coalesce(v.description, ''), vi),
        'percentOff', case when v.discount_type = 'percent' then v.discount_value end,
        'amountOff', case when v.discount_type <> 'percent' then v.discount_value end,
        'minSpend', nullif(v.min_order_vnd, 0),
        'eligible', coalesce((select jsonb_agg(private.category_dto(e.slug) order by e.n)
                              from unnest(v.eligible_categories) with ordinality e(slug, n)), '[]'::jsonb),
        'validUntil', to_char(v.ends_at at time zone 'UTC', case when vi then 'FMDD "thg" FMMM, YYYY' else 'Mon FMDD, YYYY' end))
$$;

create function private.offer_live(v public.vouchers) returns boolean
    language sql stable set search_path = ''
as $$
    select v.is_active and v.starts_at <= now() and (v.ends_at is null or v.ends_at > now())
$$;

create function private.offer_verdict(v public.vouchers, s public.services) returns table (discount bigint, refusal text)
    language sql stable set search_path = ''
as $$
    select case when r.refusal is not null then 0
                when v.discount_type = 'percent' then least((s.price_vnd * v.discount_value + 50) / 100, s.price_vnd)
                else least(v.discount_value, s.price_vnd) end,
           r.refusal
    from (select case
        when v.id is null or not private.offer_live(v) or (v.usage_limit is not null and v.used_count >= v.usage_limit) then 'invalid_code'
        when s.id is null then 'unknown_service'
        when cardinality(v.eligible_categories) > 0
             and not (select c.slug from public.service_categories c where c.id = s.category_id) = any (v.eligible_categories) then 'invalid_code'
        when s.price_vnd < v.min_order_vnd then 'invalid_code'
    end as refusal) r
$$;

create function private.slot_starts(day date) returns setof timestamptz
    language sql stable set search_path = ''
as $$
    select (slot_starts.day + t.at) at time zone private.house_zone()
    from unnest(array['09:00', '10:00', '11:30', '13:00', '14:30', '16:00', '17:30', '19:00']::time[]) with ordinality t(at, n)
    order by t.n
$$;

create function private.too_close(start_at timestamptz, end_at timestamptz, held_start timestamptz, held_end timestamptz) returns boolean
    language sql immutable set search_path = ''
as $$
    select start_at < held_end + interval '15 minutes' and end_at + interval '15 minutes' > held_start
$$;

create function private.held(therapist_id uuid, day date) returns setof public.bookings
    language sql stable set search_path = ''
as $$
    select b.* from public.bookings b
    where b.status in ('pending', 'confirmed')
      and (held.therapist_id is null or b.therapist_id = held.therapist_id)
      and b.start_at >= held.day::timestamp at time zone private.house_zone()
      and b.start_at < (held.day + 1)::timestamp at time zone private.house_zone()
    order by b.start_at
$$;

create function private.next_free_slot(therapist_id uuid) returns jsonb
    language sql stable set search_path = ''
as $$
    select jsonb_build_object('dayEpochDay', private.house_day(s.start_at), 'hour', private.hour_of_day(s.start_at))
    from generate_series(0, 13) d(n)
    cross join lateral (select (now() at time zone private.house_zone())::date + d.n as day) c
    cross join lateral private.slot_starts(c.day) s(start_at)
    where s.start_at > now()
      and not exists (select 1 from private.held(next_free_slot.therapist_id, c.day) h
                      where s.start_at >= h.start_at and s.start_at < h.end_at + interval '15 minutes')
    order by s.start_at
    limit 1
$$;

create function private.slot_free(start_at timestamptz, minutes int, therapist_ids uuid[]) returns boolean
    language sql stable set search_path = ''
as $$
    select slot_free.start_at > now() and exists (
        select 1 from unnest(therapist_ids) t(id)
        where not exists (
            select 1 from private.held(t.id, (slot_free.start_at at time zone private.house_zone())::date) h
            where private.too_close(slot_free.start_at, slot_free.start_at + make_interval(mins => minutes), h.start_at, h.end_at)))
$$;

revoke all on function private.house_day(timestamptz), private.hour_of_day(timestamptz), private.in_language(text, text, boolean),
    private.category_dto(text), private.service_dto(public.services, boolean), private.therapist_dto(public.therapists, boolean, jsonb),
    private.offer_dto(public.vouchers, boolean), private.offer_live(public.vouchers), private.offer_verdict(public.vouchers, public.services),
    private.slot_starts(date), private.too_close(timestamptz, timestamptz, timestamptz, timestamptz), private.held(uuid, date),
    private.next_free_slot(uuid), private.slot_free(timestamptz, int, uuid[]) from public;

create function public.spa_catalog() returns jsonb
    language sql stable security definer set search_path = ''
as $$
    select jsonb_build_object(
        'services', coalesce((select jsonb_agg(private.service_dto(s, l.vi) order by s.sort_order)
                              from public.services s where s.is_active), '[]'::jsonb),
        'therapists', coalesce((select jsonb_agg(private.therapist_dto(t, l.vi, private.next_free_slot(t.id)) order by t.sort_order)
                                from public.therapists t where t.is_active), '[]'::jsonb),
        'todayEpochDay', private.house_day(now()))
    from (select private.vietnamese() as vi) l
$$;

create function public.spa_offers() returns jsonb
    language sql stable security definer set search_path = ''
as $$
    select jsonb_build_object('offers', coalesce(jsonb_agg(private.offer_dto(v, private.vietnamese()) order by v.starts_at desc), '[]'::jsonb))
    from public.vouchers v where private.offer_live(v)
$$;

create function public.spa_check_offer(code text, "serviceSlug" text default null) returns jsonb
    language plpgsql stable security definer set search_path = ''
as $$
declare
    v public.vouchers;
    s public.services;
    verdict record;
begin
    select * into v from public.vouchers x where x.code = upper(btrim(spa_check_offer.code, E' \t\r\n'));
    select * into s from public.services x where x.slug = "serviceSlug" and x.is_active;
    select * into verdict from private.offer_verdict(v, s);
    return jsonb_build_object(
        'accepted', verdict.refusal is null,
        'offer', case when verdict.refusal is null then private.offer_dto(v, private.vietnamese()) end,
        'discount', verdict.discount,
        'reason', verdict.refusal);
end
$$;

create function public.spa_availability(day int default null, therapist text default null, service text default null) returns jsonb
    language sql stable security definer set search_path = ''
as $$
    select jsonb_build_object('dayEpochDay', p.day, 'slots', (
        select jsonb_agg(jsonb_build_object(
                   'startAtEpochMillis', (extract(epoch from st.start_at) * 1000)::bigint,
                   'startHour', private.hour_of_day(st.start_at),
                   'available', private.slot_free(st.start_at, p.minutes, p.diaries))
               order by st.start_at)
        from private.slot_starts(date '1970-01-01' + p.day) st(start_at)))
    from (select coalesce(spa_availability.day, private.house_day(now())) as day,
                 coalesce((select x.duration_minutes from public.services x where x.slug = spa_availability.service and x.is_active), 0) as minutes,
                 coalesce((select array[x.id] from public.therapists x where x.slug = spa_availability.therapist and x.is_active),
                          (select array_agg(x.id order by x.sort_order) from public.therapists x where x.is_active)) as diaries) p
$$;

revoke execute on function public.spa_catalog(), public.spa_offers(), public.spa_check_offer(text, text),
    public.spa_availability(int, text, text) from public, anon, authenticated;
grant execute on function public.spa_catalog(), public.spa_offers(), public.spa_check_offer(text, text),
    public.spa_availability(int, text, text) to anon, authenticated;

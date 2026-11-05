create function private.notify(uid uuid, type text, title text, body text, data jsonb default '{}') returns uuid
    language sql set search_path = ''
as $$
    select null::uuid
$$;

create function private.slugify(raw text) returns text
    language sql immutable set search_path = ''
as $$
    select left(btrim(regexp_replace(lower(coalesce(raw, '')), '[^[:alnum:]]+', '-', 'g'), '-'), 80)
$$;

create function private.slot_label(at timestamptz) returns text
    language sql stable set search_path = ''
as $$
    select to_char(slot_label.at at time zone private.house_zone(), 'FMHH12:MI AM')
$$;

create function private.day_and_time(at timestamptz) returns text
    language sql stable set search_path = ''
as $$
    select to_char(day_and_time.at at time zone private.house_zone(), 'Dy Mon FMDD') || ', ' || private.slot_label(day_and_time.at)
$$;

create function private.has_items(b public.bookings) returns boolean
    language sql stable set search_path = ''
as $$
    select exists (select 1 from public.booking_items bi join public.services s on s.id = bi.service_id where bi.booking_id = b.id)
$$;

create function private.clash(b public.bookings) returns public.bookings
    language sql stable set search_path = ''
as $$
    select h.* from public.bookings h
    where h.therapist_id = b.therapist_id
      and h.status in ('pending', 'confirmed')
      and h.start_at >= b.start_at - interval '4 hours'
      and h.start_at < b.end_at + interval '15 minutes'
      and h.id <> b.id
      and private.too_close(b.start_at, b.end_at, h.start_at, h.end_at)
      and private.has_items(h)
    order by h.start_at
    limit 1
$$;

create function private.conflict_text(b public.bookings) returns text
    language sql stable set search_path = ''
as $$
    select 'Overlaps ' || coalesce((select t.name from public.therapists t where t.id = b.therapist_id), '') || ' '
           || private.slot_label(c.start_at) || ' with BK-' || c.booking_no
    from private.clash(b) c
    where c.id is not null
$$;

create function private.desk_booking(id uuid) returns public.bookings
    language plpgsql stable set search_path = ''
as $$
declare
    booking public.bookings;
begin
    select * into booking from public.bookings b where b.id = desk_booking.id;
    if not found or not private.has_items(booking) then
        perform private.fail('unknown_booking', 404);
    end if;
    return booking;
end
$$;

create function private.notify_guest(b public.bookings, title text, body text) returns void
    language sql set search_path = ''
as $$
    select private.notify(b.user_id, 'booking', title, body, jsonb_build_object('bookingId', b.id, 'reference', 'BK-' || b.booking_no))
$$;

create function private.record_visit(id uuid, attended boolean) returns jsonb
    language plpgsql set search_path = ''
as $$
declare
    uid uuid := private.require_permission('MANAGE_BOOKINGS');
    booking public.bookings := private.desk_booking(record_visit.id);
begin
    if booking.status <> 'confirmed' then
        perform private.fail('invalid_transition', 409, booking.status);
    end if;
    if now() < booking.start_at then
        perform private.fail('visit_not_started', 409);
    end if;
    update public.bookings b
    set status = case when attended then 'completed' else 'no_show' end, decided_at = now(), decided_by = uid, updated_at = now()
    where b.id = booking.id and b.status = 'confirmed'
    returning * into booking;
    if not found then
        perform private.fail('invalid_transition', 409);
    end if;
    return private.booking_dto(booking, private.vietnamese());
end
$$;

create function private.invalid_field(field text) returns void
    language sql set search_path = ''
as $$
    select private.fail('invalid_request', 400, 'The ' || field || ' is not valid')
$$;

create function private.text_valid(raw text, max int) returns boolean
    language sql immutable set search_path = ''
as $$
    select coalesce(char_length(btrim(raw, E' \t\r\n')) between 1 and max, false)
$$;

revoke all on function private.notify(uuid, text, text, text, jsonb), private.slugify(text), private.slot_label(timestamptz),
    private.day_and_time(timestamptz), private.has_items(public.bookings), private.clash(public.bookings),
    private.conflict_text(public.bookings), private.desk_booking(uuid), private.notify_guest(public.bookings, text, text),
    private.record_visit(uuid, boolean), private.invalid_field(text), private.text_valid(text, int) from public;

create or replace function public.spa_bookings() returns jsonb
    language sql stable security definer set search_path = ''
as $$
    select jsonb_build_object('bookings', coalesce(jsonb_agg(private.booking_dto(m, private.vietnamese()) order by m.start_at desc), '[]'::jsonb))
    from (select b.* from public.bookings b where b.user_id = (private.guest()).id and private.has_items(b) order by b.start_at desc limit 50) m
$$;

create function public.spa_staff_queue() returns jsonb
    language plpgsql stable security definer set search_path = ''
as $$
declare
    vi boolean;
begin
    perform private.require_permission('MANAGE_BOOKINGS');
    vi := private.vietnamese();
    return jsonb_build_object(
        'pending', coalesce((select jsonb_agg(private.booking_dto(b, vi, private.conflict_text(b)) order by b.created_at desc)
                             from public.bookings b where b.status = 'pending' and private.has_items(b)), '[]'::jsonb),
        'scheduled', coalesce((select jsonb_agg(private.booking_dto(h, vi) order by h.start_at)
                               from private.held(null, (now() at time zone private.house_zone())::date) h
                               where private.has_items(h)), '[]'::jsonb),
        'dayEpochDay', private.house_day(now()));
end
$$;

create function public.spa_confirm_booking(id uuid, force boolean default false) returns jsonb
    language plpgsql security definer set search_path = ''
as $$
declare
    uid uuid := private.require_permission('MANAGE_BOOKINGS');
    booking public.bookings := private.desk_booking(spa_confirm_booking.id);
    clash public.bookings;
begin
    if booking.status <> 'pending' then
        perform private.fail('already_decided', 409);
    end if;
    clash := private.clash(booking);
    if clash.id is not null and not coalesce(force, false) then
        perform private.fail('overlaps_another', 409, 'BK-' || clash.booking_no);
    end if;
    update public.bookings b
    set status = 'confirmed', decided_at = now(), decided_by = uid, decline_reason = null, alternative_offer = null, updated_at = now()
    where b.id = booking.id and b.status = 'pending'
    returning * into booking;
    if not found then
        perform private.fail('already_decided', 409);
    end if;
    perform private.notify_guest(booking, 'Booking confirmed',
        (select s.name from public.booking_items bi join public.services s on s.id = bi.service_id where bi.booking_id = booking.id limit 1)
        || ' with ' || coalesce((select t.name from public.therapists t where t.id = booking.therapist_id), '')
        || ' on ' || private.day_and_time(booking.start_at));
    return private.booking_dto(booking, private.vietnamese());
end
$$;

create function public.spa_decline_booking(id uuid, reason text, "alternativeOffer" text default null) returns jsonb
    language plpgsql security definer set search_path = ''
as $$
declare
    uid uuid := private.require_permission('MANAGE_BOOKINGS');
    booking public.bookings;
    stored text := lower(reason);
    offer text := nullif(btrim("alternativeOffer", E' \t\r\n'), '');
    therapist text;
begin
    if reason is null or reason not in ('OVERLAP', 'THERAPIST_UNAVAILABLE', 'NO_ROOM') then
        perform private.invalid_field('reason');
    end if;
    booking := private.desk_booking(spa_decline_booking.id);
    if booking.status <> 'pending' then
        perform private.fail('already_decided', 409);
    end if;
    update public.bookings b
    set status = 'declined', decided_at = now(), decided_by = uid, decline_reason = stored, alternative_offer = offer, updated_at = now()
    where b.id = booking.id and b.status = 'pending'
    returning * into booking;
    if not found then
        perform private.fail('already_decided', 409);
    end if;
    therapist := coalesce((select t.name from public.therapists t where t.id = booking.therapist_id), '');
    perform private.notify_guest(booking, 'Time unavailable',
        case stored
            when 'overlap' then therapist || ' is already booked at ' || private.slot_label(booking.start_at) || '.'
            when 'therapist_unavailable' then therapist || ' is not available at ' || private.slot_label(booking.start_at) || '.'
            else 'No treatment room is free at ' || private.slot_label(booking.start_at) || '.'
        end || coalesce(' We can offer ' || offer || '.', ''));
    return private.booking_dto(booking, private.vietnamese());
end
$$;

create function public.spa_complete_booking(id uuid) returns jsonb
    language sql security definer set search_path = ''
as $$
    select private.record_visit(spa_complete_booking.id, true)
$$;

create function public.spa_no_show_booking(id uuid) returns jsonb
    language sql security definer set search_path = ''
as $$
    select private.record_visit(spa_no_show_booking.id, false)
$$;

create function public.spa_create_service(name text, description text, minutes int, price int, category text) returns jsonb
    language plpgsql security definer set search_path = ''
as $$
declare
    base text;
    category_id uuid;
    created public.services;
begin
    perform private.require_permission('MANAGE_SERVICES');
    if category is null or category not in ('MASSAGE', 'FACIAL', 'BODY') then
        perform private.invalid_field('category');
    elsif not private.text_valid(name, 80) then
        perform private.invalid_field('name');
    elsif not private.text_valid(description, 240) then
        perform private.invalid_field('description');
    elsif minutes is null or minutes <= 0 or minutes > 480 or minutes % 15 <> 0 then
        perform private.invalid_field('minutes');
    elsif price is null or price < 0 or price > 100000000 then
        perform private.invalid_field('price');
    end if;
    base := private.slugify(name);
    select c.id into category_id from public.service_categories c where c.slug = lower(spa_create_service.category);
    if base <> '' and category_id is not null then
        for attempt in 1..20 loop
            insert into public.services (category_id, name, slug, description, price_vnd, duration_minutes, sort_order)
            values (category_id, btrim(spa_create_service.name, E' \t\r\n'), case when attempt = 1 then base else base || '-' || attempt end,
                    btrim(spa_create_service.description, E' \t\r\n'), price, minutes,
                    coalesce((select max(s.sort_order) + 1 from public.services s), 1))
            on conflict (slug) do nothing
            returning * into created;
            if created.id is not null then
                return private.service_dto(created, private.vietnamese());
            end if;
        end loop;
    end if;
    perform private.fail('unknown_service', 409, 'That treatment could not be added');
end
$$;

create function public.spa_create_therapist(name text, focus text, accent text default 'MOSS') returns jsonb
    language plpgsql security definer set search_path = ''
as $$
declare
    base text;
    created public.therapists;
begin
    perform private.require_permission('MANAGE_THERAPISTS');
    if accent is null or accent not in ('MOSS', 'DUSK', 'AMBER') then
        perform private.invalid_field('accent');
    elsif not private.text_valid(name, 80) then
        perform private.invalid_field('name');
    elsif not private.text_valid(focus, 80) then
        perform private.invalid_field('focus');
    end if;
    base := private.slugify(name);
    if base <> '' then
        for attempt in 1..20 loop
            insert into public.therapists (slug, name, focus, accent, sort_order)
            values (case when attempt = 1 then base else base || '-' || attempt end, btrim(spa_create_therapist.name, E' \t\r\n'),
                    btrim(spa_create_therapist.focus, E' \t\r\n'), lower(spa_create_therapist.accent),
                    coalesce((select max(t.sort_order) + 1 from public.therapists t), 1))
            on conflict (slug) do nothing
            returning * into created;
            if created.id is not null then
                return private.therapist_dto(created, private.vietnamese(), null);
            end if;
        end loop;
    end if;
    perform private.fail('unknown_therapist', 409, 'That therapist could not be added');
end
$$;

revoke execute on function public.spa_staff_queue(), public.spa_confirm_booking(uuid, boolean), public.spa_decline_booking(uuid, text, text),
    public.spa_complete_booking(uuid), public.spa_no_show_booking(uuid), public.spa_create_service(text, text, int, int, text),
    public.spa_create_therapist(text, text, text) from public, anon, authenticated;
grant execute on function public.spa_staff_queue(), public.spa_confirm_booking(uuid, boolean), public.spa_decline_booking(uuid, text, text),
    public.spa_complete_booking(uuid), public.spa_no_show_booking(uuid), public.spa_create_service(text, text, int, int, text),
    public.spa_create_therapist(text, text, text) to authenticated;

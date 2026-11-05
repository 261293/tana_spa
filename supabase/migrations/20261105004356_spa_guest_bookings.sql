create function private.epoch_millis(at timestamptz) returns bigint
    language sql immutable set search_path = ''
as $$
    select floor(extract(epoch from epoch_millis.at) * 1000)::bigint
$$;

create function private.booking_dto(b public.bookings, vi boolean, conflict text default null) returns jsonb
    language sql stable set search_path = ''
as $$
    select jsonb_build_object(
        'id', b.id,
        'reference', 'BK-' || b.booking_no,
        'guestName', b.customer_name,
        'guestPhone', b.customer_phone,
        'service', private.service_dto(s, vi),
        'therapist', coalesce((select private.therapist_dto(t, vi, null) from public.therapists t where t.id = b.therapist_id),
                              '{"slug": "", "name": "", "focus": "", "accent": "MOSS", "nextSlot": null}'::jsonb),
        'startAtEpochMillis', private.epoch_millis(b.start_at),
        'endAtEpochMillis', private.epoch_millis(b.end_at),
        'startHour', private.hour_of_day(b.start_at),
        'startDayEpochDay', private.house_day(b.start_at),
        'status', upper(b.status),
        'subtotal', b.subtotal_vnd,
        'discount', b.discount_vnd,
        'total', b.total_vnd,
        'code', (select v.code from public.vouchers v where v.id = b.voucher_id),
        'note', b.note,
        'createdAtEpochMillis', private.epoch_millis(b.created_at),
        'declineReason', upper(b.decline_reason),
        'alternativeOffer', b.alternative_offer,
        'conflict', conflict)
    from public.booking_items bi join public.services s on s.id = bi.service_id
    where bi.booking_id = b.id
    limit 1
$$;

create function private.guest() returns public.users
    language plpgsql stable set search_path = ''
as $$
declare
    uid uuid := private.caller();
    u public.users;
begin
    select * into u from public.users x where x.id = uid and x.deleted_at is null and x.status = 'active';
    if not found then
        perform private.fail('unauthorized', 401, 'Authentication required');
    end if;
    return u;
end
$$;

revoke all on function private.epoch_millis(timestamptz), private.booking_dto(public.bookings, boolean, text), private.guest() from public;

create function public.spa_bookings() returns jsonb
    language sql stable security definer set search_path = ''
as $$
    select jsonb_build_object('bookings', coalesce(jsonb_agg(private.booking_dto(m, private.vietnamese()) order by m.start_at desc), '[]'::jsonb))
    from (select b.* from public.bookings b where b.user_id = (private.guest()).id order by b.start_at desc limit 50) m
$$;

create function public.spa_create_booking("serviceSlug" text, "startAtEpochMillis" bigint, "therapistSlug" text default null,
                                          code text default null, note text default null) returns jsonb
    language plpgsql security definer set search_path = ''
as $$
declare
    g public.users := private.guest();
    s public.services;
    t public.therapists;
    v public.vouchers;
    discount bigint := 0;
    refusal text;
    start_at timestamptz := 'epoch'::timestamptz + "startAtEpochMillis" * interval '1 millisecond';
    end_at timestamptz;
    day date;
    trimmed_code text := upper(btrim(spa_create_booking.code, E' \t\r\n'));
    booking public.bookings;
begin
    select * into s from public.services x where x.slug = "serviceSlug" and x.is_active;
    if not found then
        perform private.fail('unknown_service', 400, "serviceSlug");
    end if;
    if "therapistSlug" is not null then
        select * into t from public.therapists x where x.slug = "therapistSlug" and x.is_active;
        if not found then
            perform private.fail('unknown_therapist', 400, "therapistSlug");
        end if;
    end if;
    if start_at <= now() then
        perform private.fail('slot_in_past', 400);
    end if;
    if start_at > now() + interval '60 days' then
        perform private.fail('slot_in_past', 400, 'beyond the horizon');
    end if;
    day := (start_at at time zone private.house_zone())::date;
    if start_at not in (select private.slot_starts(day)) then
        perform private.fail('slot_taken', 409, 'not a slot the house offers');
    end if;
    end_at := start_at + make_interval(mins => s.duration_minutes);

    -- serialises every booking: without it two guests could both see the slot free and both take it
    perform pg_advisory_xact_lock(hashtext('spa_bookings'));
    if "therapistSlug" is null then
        select * into t from public.therapists x
        where x.is_active and private.slot_free(start_at, s.duration_minutes, array[x.id])
        order by x.sort_order
        limit 1;
        if not found then
            perform private.fail('slot_taken', 409, 'nobody is free');
        end if;
    elsif not private.slot_free(start_at, s.duration_minutes, array[t.id]) then
        perform private.fail('slot_taken', 409);
    end if;

    if trimmed_code <> '' then
        select * into v from public.vouchers x where x.code = trimmed_code;
        select r.discount, r.refusal into discount, refusal from private.offer_verdict(v, s) r;
        if refusal is not null then
            perform private.fail('invalid_code', 400);
        end if;
    end if;

    insert into public.bookings (user_id, branch_id, therapist_id, status, start_at, end_at, customer_name, customer_phone, note,
                                 subtotal_vnd, discount_vnd, total_vnd, voucher_id)
    values (g.id, (select br.id from public.branches br where br.is_active order by br.created_at limit 1), t.id, 'pending',
            start_at, end_at, g.full_name, coalesce(g.phone, '+10000000000'), nullif(btrim(note, E' \t\r\n'), ''),
            s.price_vnd, discount, s.price_vnd - discount, v.id)
    returning * into booking;
    insert into public.booking_items (booking_id, service_id, service_name, unit_price_vnd, duration_minutes)
    values (booking.id, s.id, s.name, s.price_vnd, s.duration_minutes);
    return private.booking_dto(booking, private.vietnamese());
end
$$;

create function public.spa_cancel_booking(id uuid) returns jsonb
    language plpgsql security definer set search_path = ''
as $$
declare
    g public.users := private.guest();
    booking public.bookings;
begin
    select * into booking from public.bookings b where b.id = spa_cancel_booking.id;
    if not found then
        perform private.fail('unknown_booking', 404);
    end if;
    if booking.user_id <> g.id then
        perform private.fail('not_your_booking', 404);
    end if;
    if booking.status not in ('pending', 'confirmed') then
        perform private.fail('invalid_transition', 409, booking.status);
    end if;
    if booking.start_at < now() + interval '120 minutes' then
        perform private.fail('too_late_to_cancel', 409);
    end if;
    update public.bookings b
    set status = 'cancelled', decided_at = now(), cancelled_at = now(), updated_at = now()
    where b.id = booking.id and b.status in ('pending', 'confirmed')
    returning * into booking;
    if not found then
        perform private.fail('invalid_transition', 409);
    end if;
    return private.booking_dto(booking, private.vietnamese());
end
$$;

revoke execute on function public.spa_bookings(), public.spa_create_booking(text, bigint, text, text, text), public.spa_cancel_booking(uuid)
    from public, anon, authenticated;
grant execute on function public.spa_bookings(), public.spa_create_booking(text, bigint, text, text, text), public.spa_cancel_booking(uuid)
    to authenticated;

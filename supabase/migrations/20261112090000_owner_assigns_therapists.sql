alter table public.therapists add column user_id uuid references public.users (id) on delete set null;
alter table public.therapists add constraint therapists_user_uq unique (user_id);

create function private.capacity_free(start_at timestamptz, minutes int, except_id uuid default null) returns boolean
    language sql stable set search_path = ''
as $$
    -- held bookings are intervals, so a free therapist for every one of them exists exactly when the busiest instant
    -- the new visit covers holds fewer bookings than there are therapists
    with visit as (select capacity_free.start_at as s, capacity_free.start_at + make_interval(mins => minutes) as e),
    held as (
        select h.start_at, h.end_at from public.bookings h, visit v
        where h.status in ('pending', 'confirmed')
          and h.id is distinct from except_id
          and h.start_at >= v.s - interval '1 day'
          and h.start_at < v.e + interval '15 minutes'
          and private.too_close(v.s, v.e, h.start_at, h.end_at))
    select capacity_free.start_at > now() and (select count(*) from public.therapists t where t.is_active) > coalesce((
        select max((select count(*) from held h where h.start_at <= p.at and p.at < h.end_at + interval '15 minutes'))
        from (select v.s as at from visit v union select h.start_at from held h, visit v where h.start_at > v.s) p), 0)
$$;

create function private.therapist_free(therapist_id uuid, start_at timestamptz, end_at timestamptz, except_id uuid) returns boolean
    language sql stable set search_path = ''
as $$
    select not exists (
        select 1 from public.bookings h
        where h.therapist_id = therapist_free.therapist_id
          and h.status in ('pending', 'confirmed')
          and h.id <> except_id
          and h.start_at >= therapist_free.start_at - interval '1 day'
          and private.too_close(therapist_free.start_at, therapist_free.end_at, h.start_at, h.end_at))
$$;

create function private.vi_moment(at timestamptz) returns text
    language sql stable set search_path = ''
as $$
    select case extract(isodow from l.t)::int when 7 then 'CN' else 'Th ' || (extract(isodow from l.t)::int + 1) end
           || ' ' || to_char(l.t, 'DD/MM') || ', ' || to_char(l.t, 'HH24:MI')
    from (select vi_moment.at at time zone private.house_zone() as t) l
$$;

create function private.service_of(b public.bookings) returns public.services
    language sql stable set search_path = ''
as $$
    select s.* from public.booking_items bi join public.services s on s.id = bi.service_id where bi.booking_id = b.id limit 1
$$;

create function private.notify_therapist(therapist_id uuid, b public.bookings, gained boolean) returns void
    language sql set search_path = ''
as $$
    select private.notify(t.user_id, 'booking',
        case when gained then 'Ca mới' when b.status = 'cancelled' then 'Ca đã huỷ' else 'Ca đã chuyển' end,
        private.in_language(s.name_vi, s.name, true) || ', ' || private.vi_moment(b.start_at)
            || case when gained then ', ' || s.duration_minutes || ' phút. Khách: ' || b.customer_name || '.'
                    when b.status = 'cancelled' then ': khách đã huỷ.'
                    else ' đã giao cho người khác.' end,
        jsonb_build_object('bookingId', b.id, 'reference', 'BK-' || b.booking_no))
    from public.therapists t, private.service_of(b) s
    where t.id = therapist_id and t.user_id is not null
$$;

create function private.guest_booking_dto(b public.bookings, vi boolean) returns jsonb
    language sql stable set search_path = ''
as $$
    select private.booking_dto(b, vi) || '{"therapist": {"slug": "", "name": "", "focus": "", "accent": "MOSS", "nextSlot": null}}'::jsonb
$$;

create function private.team_dto(vi boolean, with_accounts boolean) returns jsonb
    language sql stable set search_path = ''
as $$
    select coalesce(jsonb_agg(private.therapist_dto(t, vi, null)
                              || case when with_accounts then jsonb_build_object('account', u.email) else '{}'::jsonb end
                              order by t.sort_order), '[]'::jsonb)
    from public.therapists t left join public.users u on u.id = t.user_id
    where t.is_active
$$;

create function private.free_therapists(b public.bookings) returns jsonb
    language sql stable set search_path = ''
as $$
    select coalesce(jsonb_agg(t.slug order by t.sort_order), '[]'::jsonb)
    from public.therapists t
    where t.is_active and private.therapist_free(t.id, b.start_at, b.end_at, b.id)
$$;

create function private.desk_therapist(slug text) returns public.therapists
    language plpgsql stable set search_path = ''
as $$
declare
    t public.therapists;
begin
    select * into t from public.therapists x where x.slug = desk_therapist.slug and x.is_active;
    if not found then
        perform private.fail('unknown_therapist', 400, slug);
    end if;
    return t;
end
$$;

create function private.reassign(booking public.bookings, t public.therapists, force boolean) returns public.bookings
    language plpgsql set search_path = ''
as $$
declare
    moved public.bookings := booking;
begin
    if booking.therapist_id is not distinct from t.id then
        return booking;
    end if;
    perform pg_advisory_xact_lock(hashtext('spa_bookings'));
    moved.therapist_id := t.id;
    if not private.therapist_free(t.id, booking.start_at, booking.end_at, booking.id) and not coalesce(force, false) then
        perform private.fail('overlaps_another', 409, (select 'BK-' || c.booking_no from private.clash(moved) c));
    end if;
    update public.bookings b set therapist_id = t.id, updated_at = now()
    where b.id = booking.id and b.status = booking.status
    returning * into moved;
    if not found then
        perform private.fail('already_decided', 409);
    end if;
    return moved;
end
$$;

revoke all on function private.capacity_free(timestamptz, int, uuid), private.therapist_free(uuid, timestamptz, timestamptz, uuid),
    private.vi_moment(timestamptz), private.service_of(public.bookings), private.notify_therapist(uuid, public.bookings, boolean),
    private.guest_booking_dto(public.bookings, boolean), private.team_dto(boolean, boolean), private.free_therapists(public.bookings),
    private.desk_therapist(text), private.reassign(public.bookings, public.therapists, boolean) from public;

create or replace function public.spa_availability(day int default null, therapist text default null, service text default null) returns jsonb
    language sql stable security definer set search_path = ''
as $$
    select jsonb_build_object('dayEpochDay', p.day, 'slots', (
        select jsonb_agg(jsonb_build_object(
                   'startAtEpochMillis', (extract(epoch from st.start_at) * 1000)::bigint,
                   'startHour', private.hour_of_day(st.start_at),
                   'available', private.capacity_free(st.start_at, p.minutes)
                                and (p.named is null or private.slot_free(st.start_at, p.minutes, array[p.named])))
               order by st.start_at)
        from private.slot_starts(date '1970-01-01' + p.day) st(start_at)))
    from (select coalesce(spa_availability.day, private.house_day(now())) as day,
                 coalesce((select x.duration_minutes from public.services x where x.slug = spa_availability.service and x.is_active), 0) as minutes,
                 (select x.id from public.therapists x where x.slug = spa_availability.therapist and x.is_active) as named) p
$$;

create or replace function public.spa_bookings() returns jsonb
    language sql stable security definer set search_path = ''
as $$
    select jsonb_build_object('bookings', coalesce(jsonb_agg(private.guest_booking_dto(m, private.vietnamese()) order by m.start_at desc), '[]'::jsonb))
    from (select b.* from public.bookings b where b.user_id = (private.guest()).id and private.has_items(b) order by b.start_at desc limit 50) m
$$;

create or replace function public.spa_create_booking("serviceSlug" text, "startAtEpochMillis" bigint, "therapistSlug" text default null,
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
    if not private.capacity_free(start_at, s.duration_minutes) then
        perform private.fail('slot_taken', 409, 'nobody is free');
    end if;
    if t.id is not null and not private.slot_free(start_at, s.duration_minutes, array[t.id]) then
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
    return private.guest_booking_dto(booking, private.vietnamese());
end
$$;

create or replace function public.spa_cancel_booking(id uuid) returns jsonb
    language plpgsql security definer set search_path = ''
as $$
declare
    g public.users := private.guest();
    booking public.bookings;
    was text;
begin
    select * into booking from public.bookings b where b.id = spa_cancel_booking.id;
    was := booking.status;
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
    if was = 'confirmed' and booking.therapist_id is not null then
        perform private.notify_therapist(booking.therapist_id, booking, false);
    end if;
    return private.guest_booking_dto(booking, private.vietnamese());
end
$$;

create or replace function public.spa_staff_queue() returns jsonb
    language plpgsql stable security definer set search_path = ''
as $$
declare
    vi boolean;
begin
    perform private.require_permission('MANAGE_BOOKINGS');
    vi := private.vietnamese();
    return jsonb_build_object(
        'pending', coalesce((select jsonb_agg(private.booking_dto(b, vi, private.conflict_text(b))
                                              || jsonb_build_object('freeTherapists', private.free_therapists(b))
                                              order by b.created_at desc)
                             from public.bookings b where b.status = 'pending' and private.has_items(b)), '[]'::jsonb),
        'scheduled', coalesce((select jsonb_agg(private.booking_dto(h, vi) order by h.start_at)
                               from private.held(null, (now() at time zone private.house_zone())::date) h
                               where private.has_items(h)), '[]'::jsonb),
        'therapists', private.team_dto(vi, false),
        'dayEpochDay', private.house_day(now()));
end
$$;

drop function public.spa_confirm_booking(uuid, boolean);

create function public.spa_confirm_booking(id uuid, force boolean default false, "therapistSlug" text default null) returns jsonb
    language plpgsql security definer set search_path = ''
as $$
declare
    uid uuid := private.require_permission('MANAGE_BOOKINGS');
    booking public.bookings := private.desk_booking(spa_confirm_booking.id);
    clash public.bookings;
    s public.services;
begin
    if booking.status <> 'pending' then
        perform private.fail('already_decided', 409);
    end if;
    if "therapistSlug" is not null then
        perform private.require_permission('MANAGE_THERAPISTS');
        booking := private.reassign(booking, private.desk_therapist("therapistSlug"), force);
    end if;
    if booking.therapist_id is null then
        perform private.fail('therapist_required', 409, 'Assign a therapist first');
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
    s := private.service_of(booking);
    perform private.notify_guest(booking, 'Booking confirmed', s.name || ' on ' || private.day_and_time(booking.start_at));
    perform private.notify_therapist(booking.therapist_id, booking, true);
    return private.booking_dto(booking, private.vietnamese());
end
$$;

create function public.spa_assign_therapist(id uuid, "therapistSlug" text, force boolean default false) returns jsonb
    language plpgsql security definer set search_path = ''
as $$
declare
    booking public.bookings;
    before uuid;
begin
    perform private.require_permission('MANAGE_THERAPISTS');
    booking := private.desk_booking(spa_assign_therapist.id);
    if booking.status not in ('pending', 'confirmed') then
        perform private.fail('already_decided', 409);
    end if;
    if booking.start_at <= now() then
        perform private.fail('slot_in_past', 400);
    end if;
    before := booking.therapist_id;
    booking := private.reassign(booking, private.desk_therapist("therapistSlug"), force);
    if booking.status = 'confirmed' and before is distinct from booking.therapist_id then
        if before is not null then
            perform private.notify_therapist(before, booking, false);
        end if;
        perform private.notify_therapist(booking.therapist_id, booking, true);
    end if;
    return private.booking_dto(booking, private.vietnamese()) || jsonb_build_object('freeTherapists', private.free_therapists(booking));
end
$$;

create or replace function public.spa_decline_booking(id uuid, reason text, "alternativeOffer" text default null) returns jsonb
    language plpgsql security definer set search_path = ''
as $$
declare
    uid uuid := private.require_permission('MANAGE_BOOKINGS');
    booking public.bookings;
    stored text := lower(reason);
    offer text := nullif(btrim("alternativeOffer", E' \t\r\n'), '');
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
    perform private.notify_guest(booking, 'Time unavailable',
        case stored
            when 'no_room' then 'No treatment room is free at ' || private.slot_label(booking.start_at) || '.'
            else 'No therapist is free at ' || private.slot_label(booking.start_at) || '.'
        end || coalesce(' We can offer ' || offer || '.', ''));
    return private.booking_dto(booking, private.vietnamese());
end
$$;

create function public.spa_team() returns jsonb
    language plpgsql stable security definer set search_path = ''
as $$
begin
    perform private.require_permission('MANAGE_THERAPISTS');
    return jsonb_build_object('therapists', private.team_dto(private.vietnamese(), true));
end
$$;

create function public.spa_link_therapist("therapistSlug" text, email text default null) returns jsonb
    language plpgsql security definer set search_path = ''
as $$
declare
    t public.therapists;
    account public.users;
    address text := lower(nullif(btrim(spa_link_therapist.email, E' \t\r\n'), ''));
begin
    perform private.require_permission('MANAGE_THERAPISTS');
    t := private.desk_therapist("therapistSlug");
    if address is not null then
        select * into account from public.users u
        where lower(u.email) = address and u.deleted_at is null and u.status = 'active';
        if not found then
            perform private.fail('unknown_account', 404, 'No such account');
        end if;
        if account.role not in ('staff', 'owner') then
            perform private.fail('role_not_allowed', 400, 'Only a staff or owner account can take shifts');
        end if;
        update public.therapists x set user_id = null where x.user_id = account.id and x.id <> t.id;
    end if;
    update public.therapists x set user_id = account.id where x.id = t.id returning * into t;
    return private.therapist_dto(t, private.vietnamese(), null) || jsonb_build_object('account', account.email);
end
$$;

create function public.spa_my_shifts() returns jsonb
    language sql stable security definer set search_path = ''
as $$
    select jsonb_build_object('shifts', coalesce(jsonb_agg(private.booking_dto(b, private.vietnamese()) order by b.start_at), '[]'::jsonb))
    from public.bookings b join public.therapists t on t.id = b.therapist_id
    where t.user_id = (private.guest()).id
      and b.status = 'confirmed'
      and b.end_at > now()
      and private.has_items(b)
$$;

revoke execute on function public.spa_confirm_booking(uuid, boolean, text), public.spa_assign_therapist(uuid, text, boolean),
    public.spa_team(), public.spa_link_therapist(text, text), public.spa_my_shifts() from public, anon, authenticated;
grant execute on function public.spa_confirm_booking(uuid, boolean, text), public.spa_assign_therapist(uuid, text, boolean),
    public.spa_team(), public.spa_link_therapist(text, text), public.spa_my_shifts() to authenticated;

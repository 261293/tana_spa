create function private.product_dto(p public.products, v public.product_variants) returns jsonb
    language sql immutable set search_path = ''
as $$
    select jsonb_build_object(
        'slug', p.slug,
        'name', p.name,
        'brand', coalesce(p.brand, ''),
        'description', coalesce(p.description, ''),
        'imageUrl', p.image_url,
        'price', v.price_vnd,
        'size', coalesce(v.size_label, ''))
$$;

revoke all on function private.product_dto(public.products, public.product_variants) from public;

create function public.spa_products() returns jsonb
    language sql stable security definer set search_path = ''
as $$
    select jsonb_build_object('products', coalesce(jsonb_agg(private.product_dto(p, v) order by p.created_at desc), '[]'::jsonb))
    from public.products p
    cross join lateral (
        select x.* from public.product_variants x
        where x.product_id = p.id and x.is_active
        order by x.price_vnd limit 1) v
    where p.is_active
$$;

create function public.spa_create_product(name text, brand text, description text, price int, size text default null) returns jsonb
    language plpgsql security definer set search_path = ''
as $$
declare
    base text;
    created public.products;
    variant public.product_variants;
begin
    perform private.require_permission('MANAGE_SERVICES');
    if not private.text_valid(name, 80) then
        perform private.invalid_field('name');
    elsif brand is not null and length(btrim(brand, E' \t\r\n')) > 60 then
        perform private.invalid_field('brand');
    elsif not private.text_valid(description, 240) then
        perform private.invalid_field('description');
    elsif price is null or price < 0 or price > 100000000 then
        perform private.invalid_field('price');
    elsif size is not null and length(btrim(size, E' \t\r\n')) > 30 then
        perform private.invalid_field('size');
    end if;
    base := private.slugify(name);
    if base <> '' then
        for attempt in 1..20 loop
            insert into public.products (name, slug, brand, description)
            values (btrim(spa_create_product.name, E' \t\r\n'), case when attempt = 1 then base else base || '-' || attempt end,
                    nullif(btrim(spa_create_product.brand, E' \t\r\n'), ''), btrim(spa_create_product.description, E' \t\r\n'))
            on conflict (slug) do nothing
            returning * into created;
            if created.id is not null then
                insert into public.product_variants (product_id, sku, size_label, price_vnd)
                values (created.id, created.slug, nullif(btrim(spa_create_product.size, E' \t\r\n'), ''), price)
                returning * into variant;
                return private.product_dto(created, variant);
            end if;
        end loop;
    end if;
    perform private.fail('unknown_product', 409, 'That product could not be added');
end
$$;

revoke execute on function public.spa_products(), public.spa_create_product(text, text, text, int, text) from public, anon, authenticated;
grant execute on function public.spa_products() to anon, authenticated;
grant execute on function public.spa_create_product(text, text, text, int, text) to authenticated;

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
    if g.role = 'staff' then
        perform private.fail('staff_cannot_book', 403, 'Staff accounts do not book visits');
    end if;
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

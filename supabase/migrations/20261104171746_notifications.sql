create extension if not exists pg_cron with schema pg_catalog;

create function private.notification_problem(type text, title text, body text, data jsonb) returns text
    language sql immutable set search_path = ''
as $$
    select case
        when coalesce(char_length(btrim(title, E' \t\r\n')), 0) = 0 then 'title is invalid (EMPTY)'
        when char_length(btrim(title, E' \t\r\n')) > 120 then 'title is invalid (TOO_LONG)'
        when char_length(btrim(body, E' \t\r\n')) > 500 then 'body is invalid (TOO_LONG)'
        when jsonb_typeof(coalesce(data, '{}')) <> 'object' then 'data is invalid (KEY_EMPTY)'
        when (select count(*) from jsonb_each(data)) > 16 then 'data is invalid (TOO_MANY_ENTRIES)'
        when exists (select 1 from jsonb_each(data) e where btrim(e.key) = '' or char_length(e.key) > 64) then 'data is invalid (KEY_EMPTY)'
        when exists (select 1 from jsonb_each(data) e where char_length(e.value #>> '{}') > 512) then 'data is invalid (VALUE_TOO_LONG)'
        when type is null or type not in ('order', 'booking', 'promotion', 'system') then 'type is invalid (unknown)'
    end
$$;

create or replace function private.notify(uid uuid, type text, title text, body text, data jsonb default '{}') returns uuid
    language plpgsql set search_path = ''
as $$
declare
    stored uuid;
begin
    if private.notification_problem(type, title, body, data) is not null or not private.flag_enabled('notifications', uid) then
        return null;
    end if;
    insert into public.notifications (user_id, type, title, body, data)
    values (uid, type, btrim(title, E' \t\r\n'), nullif(btrim(body, E' \t\r\n'), ''), coalesce(data, '{}'))
    returning id into stored;
    return stored;
end
$$;

create function private.offer_body(v public.vouchers) returns text
    language sql stable set search_path = ''
as $$
    select rtrim(v.title, '.') || '. Use ' || v.code || '.' || coalesce(' Valid until ' || to_char(v.ends_at at time zone 'UTC', 'Mon FMDD, YYYY') || '.', '')
$$;

create function private.live_voucher(code text) returns public.vouchers
    language sql stable set search_path = ''
as $$
    select v.* from public.vouchers v where v.code = upper(btrim(live_voucher.code)) and private.offer_live(v)
$$;

create or replace function private.grant_voucher(uid uuid, code text, source text) returns boolean
    language plpgsql set search_path = ''
as $$
declare
    v public.vouchers := private.live_voucher(code);
begin
    if v.id is null then
        return false;
    end if;
    insert into public.user_vouchers (user_id, voucher_id, source) values (uid, v.id, source) on conflict do nothing;
    if not found then
        return false;
    end if;
    perform private.notify(uid, 'promotion', 'Welcome gift', private.offer_body(v), jsonb_build_object('code', v.code));
    return true;
end
$$;

create or replace function private.announce(code text) returns int
    language plpgsql set search_path = ''
as $$
declare
    v public.vouchers := private.live_voucher(code);
    audience int := 0;
    uid uuid;
begin
    if v.id is null then
        return 0;
    end if;
    for uid in select u.id from public.users u where u.deleted_at is null and u.status = 'active' order by u.created_at limit 5000 loop
        perform private.notify(uid, 'promotion', 'New offer', private.offer_body(v), jsonb_build_object('code', v.code));
        audience := audience + 1;
    end loop;
    return audience;
end
$$;

create function private.send_due_reminders() returns int
    language plpgsql set search_path = ''
as $$
declare
    b public.bookings;
    written int := 0;
begin
    -- the claim and the stamp are one update, so overlapping runs remind each visit once
    for b in
        update public.bookings x set reminder_sent_at = now(), updated_at = now()
        where x.status = 'confirmed' and x.reminder_sent_at is null and x.start_at > now() and x.start_at <= now() + interval '24 hours'
        returning x.*
    loop
        continue when not private.has_items(b);
        if private.notify(b.user_id, 'booking', 'Tomorrow at the spa',
               (select s.name from public.booking_items bi join public.services s on s.id = bi.service_id where bi.booking_id = b.id limit 1)
               || ' with ' || coalesce((select t.name from public.therapists t where t.id = b.therapist_id), '')
               || ' on ' || private.day_and_time(b.start_at),
               jsonb_build_object('bookingId', b.id, 'reference', 'BK-' || b.booking_no)) is null then
            update public.bookings x set reminder_sent_at = null, updated_at = now() where x.id = b.id;
        else
            written := written + 1;
        end if;
    end loop;
    return written;
end
$$;

create function private.notification_dto(n public.notifications) returns jsonb
    language sql immutable set search_path = ''
as $$
    select jsonb_build_object(
        'id', n.id,
        'type', n.type,
        'title', n.title,
        'body', n.body,
        'data', coalesce((select jsonb_object_agg(e.key, case when jsonb_typeof(e.value) = 'string' then e.value else to_jsonb(e.value::text) end)
                          from jsonb_each(case when jsonb_typeof(n.data) = 'object' then n.data else '{}' end) e), '{}'::jsonb),
        'read', n.read_at is not null,
        'createdAtEpochMillis', floor(extract(epoch from n.created_at) * 1000)::bigint)
$$;

create function private.unread_count(uid uuid) returns jsonb
    language sql stable set search_path = ''
as $$
    select jsonb_build_object('unreadCount', count(*)::int) from public.notifications n where n.user_id = uid and n.read_at is null
$$;

create function private.notifications_caller() returns uuid
    language plpgsql stable set search_path = ''
as $$
declare
    uid uuid := private.caller();
begin
    perform private.require_flag('notifications', uid);
    return uid;
end
$$;

revoke all on function private.notification_problem(text, text, text, jsonb), private.notify(uuid, text, text, text, jsonb),
    private.offer_body(public.vouchers), private.live_voucher(text), private.grant_voucher(uuid, text, text), private.announce(text),
    private.send_due_reminders(), private.notification_dto(public.notifications), private.unread_count(uuid),
    private.notifications_caller() from public;

create function public.notifications_list("limit" int default null, before bigint default null) returns jsonb
    language plpgsql stable security definer set search_path = ''
as $$
declare
    uid uuid := private.notifications_caller();
    size int := case when "limit" is null or "limit" <= 0 then 20 else least("limit", 50) end;
begin
    return (
        select jsonb_build_object(
            'items', coalesce(jsonb_agg(private.notification_dto(m) order by m.created_at desc), '[]'::jsonb),
            'unreadCount', private.unread_count(uid) -> 'unreadCount',
            'nextBefore', case when count(*) = size then (extract(epoch from min(m.created_at)) * 1000000)::bigint end)
        from (
            select n.* from public.notifications n
            where n.user_id = uid and (before is null or n.created_at < to_timestamp(0) + before * interval '1 microsecond')
            order by n.created_at desc
            limit size
        ) m);
end
$$;

create function public.notifications_unread_count() returns jsonb
    language sql stable security definer set search_path = ''
as $$
    select private.unread_count(private.notifications_caller())
$$;

create function public.notifications_mark_read(id uuid) returns jsonb
    language plpgsql security definer set search_path = ''
as $$
declare
    uid uuid := private.notifications_caller();
begin
    update public.notifications n set read_at = now() where n.id = notifications_mark_read.id and n.user_id = uid and n.read_at is null;
    if not found then
        perform private.fail('notification_not_found', 404, 'No unread notification with that id');
    end if;
    return private.unread_count(uid);
end
$$;

create function public.notifications_mark_all_read() returns jsonb
    language plpgsql security definer set search_path = ''
as $$
declare
    uid uuid := private.notifications_caller();
begin
    update public.notifications n set read_at = now() where n.user_id = uid and n.read_at is null;
    return private.unread_count(uid);
end
$$;

create function public.register_device(token text, platform text, provider text default 'fcm', locale text default null,
                                       "appVersion" text default null) returns void
    language plpgsql security definer set search_path = ''
as $$
#variable_conflict use_column
declare
    uid uuid := private.notifications_caller();
    clean text := btrim(coalesce(token, ''), E' \t\r\n');
    problem text := case
        when clean = '' then 'token is invalid (EMPTY)'
        when char_length(clean) < 16 then 'token is invalid (TOO_SHORT)'
        when char_length(clean) > 4096 then 'token is invalid (TOO_LONG)'
        when clean !~ '^[A-Za-z0-9_:.~%+/=-]+$' then 'token is invalid (UNSUPPORTED_CHARACTER)'
        when platform is null or platform not in ('android', 'ios', 'web', 'desktop') then 'platform is invalid (unknown)'
        when provider is distinct from 'fcm' then 'provider is invalid (unsupported)'
    end;
begin
    if problem is not null then
        perform private.fail('invalid_request', 400, problem);
    end if;
    insert into public.device_tokens (user_id, token, platform, provider, locale, app_version)
    values (uid, clean, platform, provider, left(locale, 35), left("appVersion", 40))
    on conflict (token) do update set
        user_id = excluded.user_id,
        platform = excluded.platform,
        provider = excluded.provider,
        locale = excluded.locale,
        app_version = excluded.app_version,
        last_seen_at = now();
end
$$;

create function public.unregister_device(token text) returns void
    language plpgsql security definer set search_path = ''
as $$
begin
    perform private.caller();
    delete from public.device_tokens d where d.token = btrim(unregister_device.token, E' \t\r\n');
end
$$;

create function public.admin_send_notification("userId" uuid, title text, type text default 'system', body text default null,
                                               data jsonb default '{}') returns jsonb
    language plpgsql security definer set search_path = ''
as $$
declare
    problem text := private.notification_problem(type, title, body, data);
    stored uuid;
begin
    perform private.require_permission('OPERATE_SYSTEM');
    if problem is not null then
        perform private.fail('invalid_request', 400, problem);
    end if;
    if private.app_role("userId") is null then
        perform private.fail('invalid_request', 404, 'No such user');
    end if;
    stored := private.notify("userId", type, title, body, data);
    if stored is null then
        perform private.fail('feature_disabled', 503, 'Notifications are switched off for this user');
    end if;
    return jsonb_build_object(
        'notificationId', stored,
        'devices', (select count(*)::int from public.device_tokens d where d.user_id = "userId"),
        'delivered', 0,
        'removed', 0);
end
$$;

revoke execute on function public.notifications_list(int, bigint), public.notifications_unread_count(), public.notifications_mark_read(uuid),
    public.notifications_mark_all_read(), public.register_device(text, text, text, text, text), public.unregister_device(text),
    public.admin_send_notification(uuid, text, text, text, jsonb) from public, anon, authenticated;
grant execute on function public.notifications_list(int, bigint), public.notifications_unread_count(), public.notifications_mark_read(uuid),
    public.notifications_mark_all_read(), public.register_device(text, text, text, text, text), public.unregister_device(text),
    public.admin_send_notification(uuid, text, text, text, jsonb) to authenticated;

select cron.schedule('spa-reminders', '0 * * * *', 'select private.send_due_reminders()');

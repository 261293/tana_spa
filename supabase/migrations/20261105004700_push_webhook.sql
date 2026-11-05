create extension if not exists pg_net with schema extensions;

create function private.push_notification() returns trigger
    language plpgsql security definer set search_path = ''
as $$
declare
    push_url text := (select decrypted_secret from vault.decrypted_secrets where name = 'push_url');
    push_secret text := (select decrypted_secret from vault.decrypted_secrets where name = 'push_secret');
begin
    if push_url is null or push_secret is null then
        return null;
    end if;
    perform net.http_post(
        url := push_url,
        body := jsonb_build_object('type', 'INSERT', 'schema', 'public', 'table', 'notifications', 'record', to_jsonb(new)),
        headers := jsonb_build_object('content-type', 'application/json', 'x-push-secret', push_secret),
        timeout_milliseconds := 10000
    );
    return null;
-- The stored row is the durable channel: a failure to enqueue the push must never roll back the insert.
exception when others then
    raise warning 'push webhook not enqueued for notification %: %', new.id, sqlstate;
    return null;
end
$$;

revoke all on function private.push_notification() from public, anon, authenticated;

create trigger notifications_push
    after insert on public.notifications
    for each row execute function private.push_notification();

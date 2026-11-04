begin;
\ir _helpers.psql
select plan(5);

delete from public.feature_flags where key = 'notifications';
insert into public.feature_flags (key, enabled) values ('notifications', true);

create temp table ctx as select pg_temp.new_user('customer', 'push@test.local') as guest;

select is(
    (select count(*)::int from net.http_request_queue),
    0,
    'queue_starts_empty'
);

select private.notify((select guest from ctx), 'system', 'Without secrets', null);
select is(
    (select count(*)::int from net.http_request_queue),
    0,
    'no_vault_secrets_no_call'
);

select vault.create_secret('http://push.test/functions/v1/push', 'push_url');
select vault.create_secret('throwaway-webhook-secret', 'push_secret');

select private.notify((select guest from ctx), 'system', 'With secrets', null);
select is(
    (select count(*)::int from net.http_request_queue),
    1,
    'one_call_per_notification'
);
select results_eq(
    $$select method::text, url, headers ->> 'x-push-secret' from net.http_request_queue$$,
    $$values ('POST'::text, 'http://push.test/functions/v1/push'::text, 'throwaway-webhook-secret'::text)$$,
    'call_goes_to_the_vault_url_with_the_secret'
);
select is(
    (select convert_from(body, 'utf8')::jsonb #>> '{record,title}' from net.http_request_queue),
    'With secrets',
    'call_carries_the_new_record'
);

select * from finish();
rollback;

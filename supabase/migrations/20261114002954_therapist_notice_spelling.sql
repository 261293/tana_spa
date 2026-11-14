create or replace function private.notify_therapist(therapist_id uuid, b public.bookings, gained boolean) returns void
    language sql set search_path = ''
as $$
    select private.notify(t.user_id, 'booking',
        case when gained then 'Ca mới' when b.status = 'cancelled' then 'Ca đã hủy' else 'Ca đã chuyển' end,
        private.in_language(s.name_vi, s.name, true) || ', ' || private.vi_moment(b.start_at)
            || case when gained then ', ' || s.duration_minutes || ' phút. Khách: ' || b.customer_name || '.'
                    when b.status = 'cancelled' then ': khách đã hủy.'
                    else ' đã giao cho người khác.' end,
        jsonb_build_object('bookingId', b.id, 'reference', 'BK-' || b.booking_no))
    from public.therapists t, private.service_of(b) s
    where t.id = therapist_id and t.user_id is not null
$$;

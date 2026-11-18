-- "X started sharing their location with you": call send-share-push when a share starts or is renewed.
create or replace function public.notify_share_started()
returns trigger language plpgsql security definer set search_path = public, extensions as $$
begin
  if tg_op = 'UPDATE' and old.expires_at is not distinct from new.expires_at then return new; end if;
  perform net.http_post(
    url := 'https://pgklzmisicdxfqikywoi.supabase.co/functions/v1/send-share-push',
    headers := '{"Content-Type": "application/json"}'::jsonb,
    body := jsonb_build_object('owner_id', new.owner_id, 'shared_with_id', new.shared_with_id)
  );
  return new;
end;
$$;
create or replace trigger on_share_send_push after insert or update on public.location_shares
for each row execute function public.notify_share_started();

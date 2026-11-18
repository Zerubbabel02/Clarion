-- Fires send-flare-push again when a Flare is resolved, so canceling actually stops the ring
-- everywhere it's currently ringing immediately, not just whenever each device's own 5-minute
-- timeout happens to fire. The function itself branches on the flare's current status
-- (resolved -> sends "flare_cancel" instead of "flare_alert" to the same recipient set).
create or replace function public.notify_flare_resolved()
returns trigger
language plpgsql
security definer
set search_path = public, extensions
as $$
begin
  perform net.http_post(
    url := 'https://pgklzmisicdxfqikywoi.supabase.co/functions/v1/send-flare-push',
    headers := '{"Content-Type": "application/json"}'::jsonb,
    body := jsonb_build_object(
      'type', 'UPDATE',
      'table', 'flares',
      'schema', 'public',
      'record', jsonb_build_object('id', new.id)
    )
  );
  return new;
end;
$$;

drop trigger if exists on_flare_resolved_send_cancel on public.flares;
create trigger on_flare_resolved_send_cancel
after update of status on public.flares
for each row
when (new.status = 'resolved' and old.status is distinct from new.status)
execute function public.notify_flare_resolved();

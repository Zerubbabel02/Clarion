-- Fires send-flare-push (real background delivery) on every new Flare. Uses the pg_net
-- extension directly (the same underlying primitive the Dashboard's Database Webhooks
-- feature and supabase_functions.http_request both wrap — this project doesn't have that
-- convenience schema provisioned, since no webhook was ever created on it via the
-- Dashboard). The Edge Function is deployed --no-verify-jwt (this trigger has no user
-- session to present a JWT with) and re-fetches the flare by id from this table before
-- doing anything, so no secret needs to travel through this trigger definition.
create extension if not exists pg_net with schema extensions;

create or replace function public.notify_flare_insert()
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
      'type', 'INSERT',
      'table', 'flares',
      'schema', 'public',
      'record', to_jsonb(new),
      'old_record', null
    )
  );
  return new;
end;
$$;

drop trigger if exists on_flare_insert_send_push on public.flares;
create trigger on_flare_insert_send_push
after insert on public.flares
for each row execute function public.notify_flare_insert();

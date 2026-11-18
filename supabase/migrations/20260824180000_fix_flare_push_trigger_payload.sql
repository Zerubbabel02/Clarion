-- Fixes send-flare-push always failing with a 500 (Deno's req.json() choked on the body).
-- Root cause: to_jsonb(new) serialized the flares row's PostGIS `location` geography column
-- into something that isn't valid JSON on the wire. The Edge Function only ever needed the
-- new flare's id anyway (it re-fetches the real row from the table rather than trusting the
-- webhook body, by design), so send just that instead of the whole row.
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
      'record', jsonb_build_object('id', new.id)
    )
  );
  return new;
end;
$$;

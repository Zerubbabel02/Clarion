-- People who are sharing their live location WITH the caller, with that location.
-- profile_directory hides lat/lng on purpose; this returns it only for active shares aimed at auth.uid().
create or replace function public.shared_with_me()
returns table (id uuid, display_name text, username text, avatar_url text, lat double precision, lng double precision, expires_at timestamptz)
language sql stable security definer set search_path = public as $$
  select p.id::uuid, p.display_name::text, p.username::text, p.avatar_url::text,
         p.lat::double precision, p.lng::double precision, s.expires_at::timestamptz
  from location_shares s
  join profiles p on p.id = s.owner_id
  where s.shared_with_id = auth.uid()
    and (s.expires_at is null or s.expires_at > now());
$$;
revoke all on function public.shared_with_me() from public, anon;
grant execute on function public.shared_with_me() to authenticated;

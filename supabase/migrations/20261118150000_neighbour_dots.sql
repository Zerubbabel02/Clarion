-- Neighbour dots: on by default, can be turned off in Profile.
alter table public.profiles add column if not exists show_on_map boolean not null default true;

-- Approximate positions of other Clarion users near the caller. Protections enforced here:
-- no names or ids returned; positions snapped to a ~200 m grid plus a per-person offset that
-- reshuffles every hour; only people seen in the last 24 h; only within 5 km; nothing at all
-- unless at least 3 people are around (so a lone person never stands out).
create or replace function public.nearby_people(radius_km double precision default 5)
returns table (lat double precision, lng double precision)
language sql stable security definer set search_path = public as $$
  with me as (select p.lat, p.lng from profiles p where p.id = auth.uid()),
  near as (
    select p.id, p.lat, p.lng from profiles p, me
    where me.lat is not null and p.id <> auth.uid() and p.show_on_map and p.lat is not null
      and p.location_updated_at > now() - interval '24 hours'
      and power((p.lat - me.lat) * 111.0, 2) + power((p.lng - me.lng) * 111.0 * cos(radians(me.lat)), 2)
          <= power(least(radius_km, 5), 2)
  )
  select (floor(n.lat / 0.002) * 0.002 + 0.001 + (hashtext(n.id::text || 'a' || to_char(now(), 'YYYYMMDDHH24')) % 1000) / 1000.0 * 0.0008)::double precision,
         (floor(n.lng / 0.002) * 0.002 + 0.001 + (hashtext(n.id::text || 'b' || to_char(now(), 'YYYYMMDDHH24')) % 1000) / 1000.0 * 0.0008)::double precision
  from near n
  where (select count(*) from near) >= 3;
$$;
revoke all on function public.nearby_people(double precision) from public, anon;
grant execute on function public.nearby_people(double precision) to authenticated;

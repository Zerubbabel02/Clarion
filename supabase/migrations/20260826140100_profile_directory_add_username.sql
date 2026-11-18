-- Adds username to the existing profile_directory view (used for contact lookup by email/
-- username/name) without touching its security posture — deliberately not
-- security_invoker=true, same as originally set up, so lookups aren't restricted to rows the
-- viewer already owns.
create or replace view public.profile_directory as
select id, display_name, email, avatar_url, circle_id, radius_km, broadcast_mode, username
from public.profiles;

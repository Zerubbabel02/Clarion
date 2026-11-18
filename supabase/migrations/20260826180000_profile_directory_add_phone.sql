-- searchProfiles now matches by phone number too — the view needs to expose it.
create or replace view public.profile_directory as
select id, display_name, email, avatar_url, circle_id, radius_km, broadcast_mode, username, phone_number
from public.profiles;

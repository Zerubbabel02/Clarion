-- Lets a Flare target the sender's trusted circle specifically, instead of the usual
-- radius broadcast. 'radius' preserves today's exact behavior for existing rows/clients.
alter table public.flares add column if not exists target_mode text not null default 'radius';

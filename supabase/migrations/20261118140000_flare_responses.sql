-- Replies to a Flare: "seen" when the alert opens, "on_way" when they tap "I'm on my way".
create table if not exists public.flare_responses (
  flare_id uuid not null references public.flares(id) on delete cascade,
  responder_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  status text not null check (status in ('seen', 'on_way')),
  updated_at timestamptz not null default now(),
  primary key (flare_id, responder_id)
);
alter table public.flare_responses enable row level security;
create policy "responders manage their own reply" on public.flare_responses
  for all to authenticated using (responder_id = auth.uid()) with check (responder_id = auth.uid());
create policy "senders read replies to their own flares" on public.flare_responses
  for select to authenticated using (exists (select 1 from public.flares f where f.id = flare_id and f.sender_id = auth.uid()));

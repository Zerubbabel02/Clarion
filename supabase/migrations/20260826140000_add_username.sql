-- A unique, stable handle distinct from display_name (which can collide between users).
-- Nullable + a plain unique index: Postgres unique indexes allow any number of NULLs, so
-- existing accounts that haven't picked one yet aren't broken by this.
alter table public.profiles add column if not exists username text;
create unique index if not exists profiles_username_key on public.profiles (username) where username is not null;

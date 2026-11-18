-- A Flare nobody closed ("I'm safe" never tapped) must not keep alerting people forever.
-- Every 10 minutes, close any Flare that has been active for more than 2 hours.
-- Run this once in the Supabase SQL editor (Claude was not allowed to apply it).
create extension if not exists pg_cron;
select cron.unschedule(jobid) from cron.job where jobname = 'expire-old-flares';
select cron.schedule(
  'expire-old-flares',
  '*/10 * * * *',
  $$update public.flares set status = 'resolved', resolved_at = now()
    where status = 'active' and created_at < now() - interval '2 hours'$$
);

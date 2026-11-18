-- Real background push: each device saves its FCM token here on login
-- (ClarionRepository.fetchAndSaveFcmToken), and the send-flare-push Edge Function reads it
-- to deliver alerts even when the app is closed.
alter table public.profiles add column if not exists fcm_token text;

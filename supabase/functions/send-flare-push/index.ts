// Real background delivery for Clarion. Triggered by a Postgres trigger on `flares` INSERT
// AND on UPDATE OF status (see supabase/migrations — pg_net-based triggers, the same
// underlying primitive the Dashboard's own Database Webhooks feature uses). Resolves
// recipients one of two ways depending on flares.target_mode: "radius" (default, Home's main
// Send a Flare) reaches every profile with a saved FCM token inside the sender's own
// broadcast radius; "trusted" (Home's Alert Circle) reaches the sender's
// trusted_circle_members regardless of distance. Either way, the sender's exclude list is
// applied. If the flare's current status is "active" this sends the real ringing alert; if
// it's "resolved" (the sender tapped "I'm Safe — Cancel Flare"), this sends a "flare_cancel"
// data message to the SAME recipient set instead, so canceling stops the ring everywhere it's
// currently ringing immediately, not just after the 5-minute timeout. Every message is a
// data-only FCM push — see ClarionFirebaseMessagingService.kt on the client.
//
// Deployed with --no-verify-jwt: the trigger that calls this has no user session to present
// a Supabase JWT with. That's safe because the webhook body is never trusted directly — the
// flare is re-fetched by id from the real table below, so a forged request without a
// matching row just gets a 404.
//
// Required secrets (Project Settings → Edge Functions → Secrets, or `supabase secrets set`):
//   FIREBASE_SERVICE_ACCOUNT_JSON  — the full contents of the Firebase service account
//                                    JSON key (Firebase Console → Project settings →
//                                    Service accounts → Generate new private key), as one
//                                    line/string.
// SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY are provided automatically to every Edge
// Function — nothing to set for those.

import { createClient } from "npm:@supabase/supabase-js@2";
import { JWT } from "npm:google-auth-library@^10";

interface FlareRecord {
  id: string;
  sender_id: string;
  lat: number;
  lng: number;
  location_label: string | null;
  status: string;
  target_mode: string;
}

interface WebhookPayload {
  type: "INSERT";
  table: string;
  record: FlareRecord;
  schema: "public";
}

interface RecipientProfile {
  id: string;
  fcm_token: string | null;
  lat: number | null;
  lng: number | null;
}

function haversineKm(lat1: number, lng1: number, lat2: number, lng2: number): number {
  const r = 6371;
  const dLat = ((lat2 - lat1) * Math.PI) / 180;
  const dLng = ((lng2 - lng1) * Math.PI) / 180;
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos((lat1 * Math.PI) / 180) * Math.cos((lat2 * Math.PI) / 180) * Math.sin(dLng / 2) ** 2;
  return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

function distanceLabel(km: number): string {
  const meters = km * 1000;
  return meters < 950 ? `${Math.round(meters)}m` : `${(km).toFixed(1)}km`;
}

async function getAccessToken(clientEmail: string, privateKey: string): Promise<string> {
  const jwtClient = new JWT({
    email: clientEmail,
    key: privateKey,
    scopes: ["https://www.googleapis.com/auth/firebase.messaging"],
  });
  const tokens = await jwtClient.authorize();
  if (!tokens.access_token) throw new Error("No access_token in JWT authorization response");
  return tokens.access_token;
}

Deno.serve(async (req) => {
  try {
    const payload: WebhookPayload = await req.json();

    const supabaseAdmin = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );

    // Never trust the webhook body's `record` directly — it's reachable without a verified
    // Supabase JWT (the pg_net trigger that calls this function can't easily present a user
    // JWT), so an unauthenticated caller could otherwise POST a fabricated flare and trigger
    // real pushes to real users. Re-fetch the row by id from the actual table instead; a
    // forged payload simply won't match a real row and fails here.
    const { data: flare, error: flareError } = await supabaseAdmin
      .from("flares")
      .select("id, sender_id, lat, lng, location_label, status, target_mode")
      .eq("id", payload.record.id)
      .single();
    if (flareError || !flare) {
      return Response.json({ skipped: "flare id not found" }, { status: 404 });
    }
    // Anything other than active/resolved (there isn't one today, but a future status
    // shouldn't silently ring or silently fail to cancel) is a no-op.
    if (flare.status !== "active" && flare.status !== "resolved") {
      return Response.json({ skipped: `flare status is "${flare.status}"` });
    }
    const isCancel = flare.status === "resolved";

    const serviceAccountJson = Deno.env.get("FIREBASE_SERVICE_ACCOUNT_JSON");
    if (!serviceAccountJson) {
      throw new Error("FIREBASE_SERVICE_ACCOUNT_JSON secret is not set");
    }
    const serviceAccount = JSON.parse(serviceAccountJson);

    const { data: sender, error: senderError } = await supabaseAdmin
      .from("profiles")
      .select("display_name, avatar_url, radius_km, broadcast_mode")
      .eq("id", flare.sender_id)
      .single();
    if (senderError || !sender) throw senderError ?? new Error("Sender profile not found");

    const { data: excludedRows } = await supabaseAdmin
      .from("exclude_list")
      .select("excluded_id")
      .eq("owner_id", flare.sender_id);
    const excludedIds = new Set((excludedRows ?? []).map((r) => r.excluded_id as string));

    let recipients: RecipientProfile[];

    if (flare.target_mode === "trusted") {
      // Home's "Alert Circle" — always reaches the sender's trusted circle regardless of
      // distance; broadcast_mode/radius_km don't apply to this mode at all, only to the
      // default radius broadcast.
      const { data: trustedRows } = await supabaseAdmin
        .from("trusted_circle_members")
        .select("member_id")
        .eq("owner_id", flare.sender_id);
      const memberIds = (trustedRows ?? []).map((r) => r.member_id as string).filter((id) => !excludedIds.has(id));
      if (memberIds.length === 0) {
        return Response.json({ sent: 0, reason: "no trusted circle members" });
      }
      const { data: trustedCandidates, error: trustedError } = await supabaseAdmin
        .from("profiles")
        .select("id, fcm_token, lat, lng")
        .in("id", memberIds)
        .not("fcm_token", "is", null) as { data: RecipientProfile[] | null; error: unknown };
      if (trustedError) throw trustedError;
      recipients = trustedCandidates ?? [];
    } else {
      if (sender.broadcast_mode !== "radius") {
        return Response.json({ skipped: `sender broadcast_mode is "${sender.broadcast_mode}"` });
      }
      const { data: candidates, error: candidatesError } = await supabaseAdmin
        .from("profiles")
        .select("id, fcm_token, lat, lng")
        .neq("id", flare.sender_id)
        .not("fcm_token", "is", null) as { data: RecipientProfile[] | null; error: unknown };
      if (candidatesError) throw candidatesError;

      recipients = (candidates ?? []).filter((p) => {
        if (excludedIds.has(p.id)) return false;
        if (p.lat == null || p.lng == null) return false;
        const km = haversineKm(p.lat, p.lng, flare.lat, flare.lng);
        return km <= sender.radius_km;
      });
    }

    if (recipients.length === 0) {
      return Response.json({ sent: 0, reason: "no recipients in radius" });
    }

    const accessToken = await getAccessToken(serviceAccount.client_email, serviceAccount.private_key);
    const fcmUrl = `https://fcm.googleapis.com/v1/projects/${serviceAccount.project_id}/messages:send`;

    const results = await Promise.allSettled(
      recipients.map((r) => {
        // A cancel reaches the exact same recipient set the original alert would have — the
        // whole point is "stop ringing everywhere it's currently ringing", regardless of
        // distance, the moment the sender marks themselves safe. It only needs the flareId;
        // the client matches that against whichever flare it's actually ringing/showing.
        const data = isCancel
          ? { type: "flare_cancel", flareId: flare.id }
          : {
            type: "flare_alert",
            flareId: flare.id,
            senderName: sender.display_name ?? "Someone nearby",
            // A trusted-circle recipient may never have set a location — distance genuinely
            // doesn't apply to this mode anyway, so just leave it out rather than show "NaN".
            distance: r.lat != null && r.lng != null
              ? distanceLabel(haversineKm(r.lat, r.lng, flare.lat, flare.lng))
              : "",
            location: flare.location_label ?? "Nearby",
            flareLat: String(flare.lat),
            flareLng: String(flare.lng),
            avatarUrl: sender.avatar_url ?? "",
          };
        return fetch(fcmUrl, {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${accessToken}`,
          },
          body: JSON.stringify({ message: { token: r.fcm_token, android: { priority: "high" }, data } }),
        });
      }),
    );

    const sent = results.filter((r) => r.status === "fulfilled" && (r.value as Response).ok).length;
    return Response.json({ sent, attempted: recipients.length });
  } catch (err) {
    console.error("send-flare-push failed:", err);
    return Response.json({ error: String(err) }, { status: 500 });
  }
});

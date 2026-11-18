// "X started sharing their location with you" — a push to the person a location share is aimed
// at, so it reaches them even when Clarion is closed. Called by the on_share_send_push trigger on
// location_shares (pg_net, no user session, so deployed with verify_jwt = false). The request body
// is never trusted: the share is re-fetched from the real table and must be active, so a forged
// call without a matching live share sends nothing.
//
// Uses the same FIREBASE_SERVICE_ACCOUNT_JSON secret as send-flare-push.

import { createClient } from "npm:@supabase/supabase-js@2";
import { JWT } from "npm:google-auth-library@^10";

async function getAccessToken(clientEmail: string, privateKey: string): Promise<string> {
  const jwtClient = new JWT({ email: clientEmail, key: privateKey, scopes: ["https://www.googleapis.com/auth/firebase.messaging"] });
  const tokens = await jwtClient.authorize();
  if (!tokens.access_token) throw new Error("No access_token in JWT authorization response");
  return tokens.access_token;
}

Deno.serve(async (req) => {
  try {
    const body = await req.json();
    const ownerId = String(body.owner_id ?? "");
    const recipientId = String(body.shared_with_id ?? "");
    if (!ownerId || !recipientId) return Response.json({ sent: 0, reason: "missing ids" }, { status: 400 });

    const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);

    const { data: share } = await admin
      .from("location_shares")
      .select("owner_id, shared_with_id, expires_at")
      .eq("owner_id", ownerId)
      .eq("shared_with_id", recipientId)
      .maybeSingle();
    if (!share) return Response.json({ sent: 0, reason: "no such share" }, { status: 404 });
    if (share.expires_at && new Date(share.expires_at).getTime() < Date.now()) return Response.json({ sent: 0, reason: "expired" });

    const { data: owner } = await admin.from("profiles").select("display_name, avatar_url").eq("id", ownerId).maybeSingle();
    const { data: recipient } = await admin.from("profiles").select("fcm_token").eq("id", recipientId).maybeSingle();
    if (!recipient?.fcm_token) return Response.json({ sent: 0, reason: "recipient has no device token" });

    const serviceAccountJson = Deno.env.get("FIREBASE_SERVICE_ACCOUNT_JSON");
    if (!serviceAccountJson) throw new Error("FIREBASE_SERVICE_ACCOUNT_JSON is not set");
    const serviceAccount = JSON.parse(serviceAccountJson);
    const accessToken = await getAccessToken(serviceAccount.client_email, serviceAccount.private_key);

    const res = await fetch(`https://fcm.googleapis.com/v1/projects/${serviceAccount.project_id}/messages:send`, {
      method: "POST",
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${accessToken}` },
      body: JSON.stringify({
        message: {
          token: recipient.fcm_token,
          android: { priority: "high" },
          data: {
            type: "share_started",
            ownerId,
            ownerName: owner?.display_name ?? "Someone",
            avatarUrl: owner?.avatar_url ?? "",
            expiresAt: share.expires_at ?? "",
          },
        },
      }),
    });
    return Response.json({ sent: res.ok ? 1 : 0 });
  } catch (err) {
    console.error("send-share-push failed:", err);
    return Response.json({ error: String(err) }, { status: 500 });
  }
});

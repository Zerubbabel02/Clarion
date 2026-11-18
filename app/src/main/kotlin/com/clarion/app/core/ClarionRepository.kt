package com.clarion.app.core

import com.google.firebase.messaging.FirebaseMessaging
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlin.coroutines.resume

object ClarionRepository {

    fun currentUserId(): String? = supabase.auth.currentUserOrNull()?.id

    suspend fun signUp(email: String, password: String) {
        supabase.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun signIn(email: String, password: String) {
        supabase.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun signOut() {
        supabase.auth.signOut()
    }

    /**
     * Exchanges a verified Google ID token (from Android's Credential Manager, see
     * AuthScreen) for a real Supabase session — the same account system as email/password,
     * not a separate identity. [nonce] must be the exact raw value hashed into the
     * GetGoogleIdOption the token was requested with; Supabase re-hashes it server-side to
     * confirm the token was actually issued for this request, not replayed from elsewhere.
     */
    suspend fun signInWithGoogleIdToken(idToken: String, nonce: String) {
        supabase.auth.signInWith(IDToken) {
            this.idToken = idToken
            this.provider = Google
            this.nonce = nonce
        }
    }

    suspend fun getMyProfile(): Profile? {
        val uid = currentUserId() ?: return null
        return supabase.from("profiles")
            .select { filter { eq("id", uid) } }
            .decodeSingleOrNull<Profile>()
    }

    /** Finds a circle by exact name, or creates it, returning its id either way. */
    suspend fun getOrCreateCircle(name: String): String {
        val trimmed = name.trim()
        val existing = supabase.from("circles")
            .select { filter { eq("name", trimmed) } }
            .decodeSingleOrNull<Circle>()
        if (existing != null) return existing.id

        val created = supabase.from("circles")
            .insert(mapOf("name" to trimmed)) { select() }
            .decodeSingle<Circle>()
        return created.id
    }

    suspend fun saveProfile(displayName: String, circleName: String?) {
        val uid = currentUserId() ?: error("Not signed in")
        val email = supabase.auth.currentUserOrNull()?.email
        val circleId = circleName?.takeIf { it.isNotBlank() }?.let { getOrCreateCircle(it) }

        @Serializable
        data class ProfileUpsert(
            val id: String,
            val email: String?,
            val display_name: String,
            val circle_id: String?,
        )

        supabase.from("profiles").upsert(ProfileUpsert(uid, email, displayName, circleId))
    }

    suspend fun hasProfile(): Boolean = getMyProfile() != null

    /**
     * The lodge/building name captured at onboarding ("Lodge, building, or shop") — this is
     * what a sent Flare's location_label should actually carry. Mapping accuracy in this
     * app's real target area is poor, so knowing the physical place by name matters at least
     * as much as the pin on the map; sendFlare was passing locationLabel = null unconditionally
     * before this existed, a real gap between what the schema supported and what actually got
     * sent.
     */
    suspend fun getMyLodgeName(): String? {
        val circleId = getMyProfile()?.circleId ?: return null
        return supabase.from("circles")
            .select { filter { eq("id", circleId) } }
            .decodeSingleOrNull<Circle>()
            ?.name
    }

    suspend fun updateLocation(lat: Double, lng: Double) {
        val uid = currentUserId() ?: return
        supabase.from("profiles")
            .update(mapOf("lat" to lat, "lng" to lng)) { filter { eq("id", uid) } }
    }

    suspend fun updateRadiusKm(km: Double) {
        val uid = currentUserId() ?: return
        supabase.from("profiles")
            .update(mapOf("radius_km" to km)) { filter { eq("id", uid) } }
    }

    suspend fun updateBroadcastMode(mode: String) {
        val uid = currentUserId() ?: return
        supabase.from("profiles")
            .update(mapOf("broadcast_mode" to mode)) { filter { eq("id", uid) } }
    }

    suspend fun updateNightMuteEnabled(enabled: Boolean) {
        val uid = currentUserId() ?: return
        supabase.from("profiles")
            .update(mapOf("night_mute_enabled" to enabled)) { filter { eq("id", uid) } }
    }

    suspend fun updateAvatarUrl(url: String) {
        val uid = currentUserId() ?: return
        supabase.from("profiles")
            .update(mapOf("avatar_url" to url)) { filter { eq("id", uid) } }
    }

    suspend fun updateFcmToken(token: String) {
        val uid = currentUserId() ?: return
        supabase.from("profiles")
            .update(mapOf("fcm_token" to token)) { filter { eq("id", uid) } }
    }

    /**
     * Called once per real login (ClarionMainShell). Covers the case where the device's FCM
     * token was generated before sign-in — onNewToken already fired and had no signed-in user
     * to save it against — as well as the normal case of a token that's simply never changed.
     */
    suspend fun fetchAndSaveFcmToken() {
        try {
            val token = suspendCancellableCoroutine<String?> { cont ->
                FirebaseMessaging.getInstance().token
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resume(null) }
            }
            if (token != null) updateFcmToken(token)
        } catch (_: Exception) {
            // Retried on next app open.
        }
    }

    // --- Flares ---

    suspend fun sendFlare(lat: Double, lng: Double, locationLabel: String?, targetMode: String = "radius"): String {
        val uid = currentUserId() ?: error("Not signed in")
        val created = supabase.from("flares")
            .insert(Flare(senderId = uid, lat = lat, lng = lng, locationLabel = locationLabel, targetMode = targetMode)) { select() }
            .decodeSingle<Flare>()
        return created.id ?: error("Flare insert returned no id")
    }

    /**
     * Active flares from OTHER users that fall within the sender's own radius setting,
     * relative to my last-known location. This is the foreground fast-path only — the real
     * always-on delivery path is the send-flare-push Edge Function (supabase/functions),
     * triggered server-side on every Flare insert and reaching devices via FCM even when
     * this app is closed. This client-side query still doesn't apply trusted-circle
     * enforcement (the Edge Function does apply the exclude list).
     *
     * Deliberately radius-mode only: a "target_mode": "trusted" Flare (Home's Alert Circle)
     * is matched by trusted_circle_members ownership, which RLS scopes to the owning user —
     * a recipient's own client can't read the sender's trusted list to check membership the
     * way it can check a plain distance. The Edge Function can (it runs with the service
     * role), so trusted-mode delivery is push-only; this poll skips those rows rather than
     * mishandle them.
     */
    suspend fun nearbyActiveFlares(myLat: Double, myLng: Double): List<Pair<Flare, Profile>> {
        val uid = currentUserId() ?: return emptyList()
        val activeFlares = supabase.from("flares")
            .select { filter { eq("status", "active"); eq("target_mode", "radius"); neq("sender_id", uid) } }
            .decodeList<Flare>()
        if (activeFlares.isEmpty()) return emptyList()

        val senderIds = activeFlares.map { it.senderId }.distinct()
        val senders = supabase.from("profile_directory")
            .select { filter { isIn("id", senderIds) } }
            .decodeList<Profile>()
            .associateBy { it.id }

        return activeFlares.mapNotNull { flare ->
            val sender = senders[flare.senderId] ?: return@mapNotNull null
            if (sender.broadcastMode != "radius") return@mapNotNull null
            val distanceKm = haversineKm(myLat, myLng, flare.lat, flare.lng)
            if (distanceKm <= sender.radiusKm) flare to sender else null
        }
    }

    private fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLng / 2) * kotlin.math.sin(dLng / 2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        return r * c
    }

    suspend fun myActiveFlare(): Flare? {
        val uid = currentUserId() ?: return null
        return supabase.from("flares")
            .select { filter { eq("sender_id", uid); eq("status", "active") } }
            .decodeSingleOrNull<Flare>()
    }

    suspend fun resolveFlare(flareId: String) {
        supabase.from("flares")
            .update(mapOf("status" to "resolved")) { filter { eq("id", flareId) } }
    }

    // --- Trusted circle / exclude list (looked up via profile_directory) ---

    /**
     * Matches by username, phone number, or email only — deliberately NOT display name, which
     * two different people can share, making a name-based match ambiguous about who you're
     * actually about to add. Excludes the caller themselves.
     */
    suspend fun searchProfiles(query: String): List<Profile> {
        val trimmed = query.trim()
        if (trimmed.length < 2) return emptyList()
        val uid = currentUserId()
        val pattern = "%$trimmed%"
        return try {
            supabase.from("profile_directory")
                .select {
                    filter {
                        or {
                            ilike("username", pattern)
                            ilike("phone_number", pattern)
                            ilike("email", pattern)
                        }
                    }
                    limit(8)
                }
                .decodeList<Profile>()
                .filter { it.id != uid }
        } catch (e: RestException) {
            emptyList()
        }
    }

    suspend fun updateDisplayName(name: String) {
        val uid = currentUserId() ?: return
        supabase.from("profiles").update(mapOf("display_name" to name)) { filter { eq("id", uid) } }
    }

    /** Returns null on success, or a message to show the user (e.g. the username is taken). */
    suspend fun updateUsername(username: String): String? {
        val uid = currentUserId() ?: return "Not signed in"
        val trimmed = username.trim()
        return try {
            supabase.from("profiles").update(mapOf("username" to trimmed)) { filter { eq("id", uid) } }
            null
        } catch (e: RestException) {
            val msg = e.message ?: ""
            if (msg.contains("duplicate key", ignoreCase = true) || msg.contains("23505")) {
                "That username is already taken"
            } else {
                "Couldn't save username"
            }
        }
    }

    suspend fun addTrustedMember(memberId: String) {
        val uid = currentUserId() ?: return
        supabase.from("trusted_circle_members")
            .insert(TrustedCircleMember(ownerId = uid, memberId = memberId))
    }

    suspend fun listTrustedMembers(): List<Profile> {
        val uid = currentUserId() ?: return emptyList()
        val ids = supabase.from("trusted_circle_members")
            .select { filter { eq("owner_id", uid) } }
            .decodeList<TrustedCircleMember>()
            .map { it.memberId }
        if (ids.isEmpty()) return emptyList()
        return supabase.from("profile_directory")
            .select { filter { isIn("id", ids) } }
            .decodeList<Profile>()
    }

    suspend fun addExcluded(excludedId: String) {
        val uid = currentUserId() ?: return
        supabase.from("exclude_list")
            .insert(ExcludeListEntry(ownerId = uid, excludedId = excludedId))
    }

    suspend fun listExcluded(): List<Profile> {
        val uid = currentUserId() ?: return emptyList()
        val ids = supabase.from("exclude_list")
            .select { filter { eq("owner_id", uid) } }
            .decodeList<ExcludeListEntry>()
            .map { it.excludedId }
        if (ids.isEmpty()) return emptyList()
        return supabase.from("profile_directory")
            .select { filter { isIn("id", ids) } }
            .decodeList<Profile>()
    }

    // --- Location sharing (everyday, non-emergency) ---

    /** [durationHours] null means share until manually stopped. */
    suspend fun shareLocationWith(recipientId: String, durationHours: Int?) {
        val uid = currentUserId() ?: return
        val expiresAt = durationHours?.let {
            java.time.Instant.now().plusSeconds(it * 3600L).toString()
        }
        supabase.from("location_shares").upsert(
            LocationShare(ownerId = uid, sharedWithId = recipientId, expiresAt = expiresAt),
        ) { onConflict = "owner_id,shared_with_id" }
    }

    suspend fun stopSharingWith(recipientId: String) {
        val uid = currentUserId() ?: return
        supabase.from("location_shares")
            .delete { filter { eq("owner_id", uid); eq("shared_with_id", recipientId) } }
    }

    suspend fun listActiveShares(): List<Profile> {
        val uid = currentUserId() ?: return emptyList()
        val shares = supabase.from("location_shares")
            .select { filter { eq("owner_id", uid) } }
            .decodeList<LocationShare>()
        val nowIso = java.time.Instant.now().toString()
        val activeIds = shares.filter { it.expiresAt == null || it.expiresAt > nowIso }.map { it.sharedWithId }
        if (activeIds.isEmpty()) return emptyList()
        return supabase.from("profile_directory")
            .select { filter { isIn("id", activeIds) } }
            .decodeList<Profile>()
    }
}

package com.clarion.app.core

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.Serializable

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

    // --- Flares ---

    suspend fun sendFlare(lat: Double, lng: Double, locationLabel: String?): String {
        val uid = currentUserId() ?: error("Not signed in")
        val created = supabase.from("flares")
            .insert(Flare(senderId = uid, lat = lat, lng = lng, locationLabel = locationLabel)) { select() }
            .decodeSingle<Flare>()
        return created.id ?: error("Flare insert returned no id")
    }

    /**
     * Active flares from OTHER users that fall within the sender's own radius setting,
     * relative to my last-known location. This is the real (not demo) receiving path for
     * now: it needs the viewer's app to be alive and polling — a proper server-side
     * function plus real push delivery (Firebase, still pending) is the next hardening
     * step, along with trusted-circle and exclude-list enforcement, which this simplified
     * v1 does not yet apply.
     */
    suspend fun nearbyActiveFlares(myLat: Double, myLng: Double): List<Pair<Flare, Profile>> {
        val uid = currentUserId() ?: return emptyList()
        val activeFlares = supabase.from("flares")
            .select { filter { eq("status", "active"); neq("sender_id", uid) } }
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

    // --- Trusted circle / exclude list (looked up by email via profile_directory) ---

    suspend fun findProfileByEmail(email: String): Profile? =
        try {
            supabase.from("profile_directory")
                .select(Columns.list("id", "display_name", "email", "avatar_url", "circle_id")) {
                    filter { eq("email", email.trim()) }
                }
                .decodeSingleOrNull<Profile>()
        } catch (e: RestException) {
            null
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

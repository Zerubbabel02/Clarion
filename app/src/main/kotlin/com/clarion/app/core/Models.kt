package com.clarion.app.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Circle(
    val id: String,
    val name: String,
)

@Serializable
data class Profile(
    val id: String,
    @SerialName("display_name") val displayName: String,
    val username: String? = null,
    @SerialName("phone_number") val phoneNumber: String? = null,
    @SerialName("circle_id") val circleId: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("broadcast_mode") val broadcastMode: String = "radius",
    @SerialName("radius_km") val radiusKm: Double = 1.2,
    @SerialName("night_mute_enabled") val nightMuteEnabled: Boolean = true,
    @SerialName("fcm_token") val fcmToken: String? = null,
    @SerialName("show_on_map") val showOnMap: Boolean = true,
)

@Serializable
data class Flare(
    val id: String? = null,
    @SerialName("sender_id") val senderId: String,
    val lat: Double,
    val lng: Double,
    @SerialName("location_label") val locationLabel: String? = null,
    val status: String = "active",
    @SerialName("target_mode") val targetMode: String = "radius",
)

@Serializable
data class TrustedCircleMember(
    @SerialName("owner_id") val ownerId: String,
    @SerialName("member_id") val memberId: String,
)

@Serializable
data class ExcludeListEntry(
    @SerialName("owner_id") val ownerId: String,
    @SerialName("excluded_id") val excludedId: String,
)

@Serializable
data class LocationShare(
    val id: String? = null,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("shared_with_id") val sharedWithId: String,
    @SerialName("expires_at") val expiresAt: String? = null,
)

/** A friend who is sharing their live location with the signed-in user (from shared_with_me()). */
@Serializable
data class SharedLocation(
    val id: String,
    @SerialName("display_name") val displayName: String,
    val username: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
)

/** A reply to a Flare: "seen" or "on_way". */
@Serializable
data class FlareResponse(
    @SerialName("flare_id") val flareId: String,
    @SerialName("responder_id") val responderId: String? = null,
    val status: String,
)

/** One anonymous, approximate neighbour position (from nearby_people()). */
@Serializable
data class NearbyDot(val lat: Double, val lng: Double)

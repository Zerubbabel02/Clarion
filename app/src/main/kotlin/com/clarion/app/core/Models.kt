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
    @SerialName("phone_number") val phoneNumber: String? = null,
    @SerialName("circle_id") val circleId: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("broadcast_mode") val broadcastMode: String = "radius",
    @SerialName("radius_km") val radiusKm: Double = 1.2,
    @SerialName("night_mute_enabled") val nightMuteEnabled: Boolean = true,
)

@Serializable
data class Flare(
    val id: String? = null,
    @SerialName("sender_id") val senderId: String,
    val lat: Double,
    val lng: Double,
    @SerialName("location_label") val locationLabel: String? = null,
    val status: String = "active",
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

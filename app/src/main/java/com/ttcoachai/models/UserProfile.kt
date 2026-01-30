/*
 * AI Coach for Table Tennis
 * User Profile Model - Cloud synced user data
 */

package com.ttcoachai.models

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * User profile data stored in Firestore.
 * Created on first login and updated on subsequent logins.
 */
data class UserProfile(
    @DocumentId
    val uid: String = "",
    val email: String? = null,
    val displayName: String? = null,
    val photoUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastLoginAt: Long = System.currentTimeMillis(),
    // Subscription fields for future RevenueCat integration
    val subscriptionStatus: String = "free", // "free", "premium", "trial"
    val subscriptionExpiresAt: Long? = null
) {
    /**
     * No-arg constructor required for Firestore deserialization
     */
    constructor() : this(uid = "")

    companion object {
        const val COLLECTION = "users"
        const val FIELD_LAST_LOGIN = "lastLoginAt"
        const val FIELD_SUBSCRIPTION_STATUS = "subscriptionStatus"
        const val FIELD_SUBSCRIPTION_EXPIRES = "subscriptionExpiresAt"
    }

    fun toMap(): Map<String, Any?> = mapOf(
        "email" to email,
        "displayName" to displayName,
        "photoUrl" to photoUrl,
        "createdAt" to createdAt,
        "lastLoginAt" to lastLoginAt,
        "subscriptionStatus" to subscriptionStatus,
        "subscriptionExpiresAt" to subscriptionExpiresAt
    )

    fun isPremium(): Boolean {
        if (subscriptionStatus != "premium" && subscriptionStatus != "trial") return false
        val expiresAt = subscriptionExpiresAt ?: return false
        return System.currentTimeMillis() < expiresAt
    }
}

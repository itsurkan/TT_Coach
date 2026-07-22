/*
 * AI Coach for Table Tennis
 * Community Drill Repository - Firestore operations for shared community drills
 */

package com.ttcoachai.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.ttcoachai.models.CommunityDrill
import com.ttcoachai.models.CommunityDrillMapper
import com.ttcoachai.models.DrillRating
import com.ttcoachai.util.RatingAggregate
import kotlinx.coroutines.tasks.await

/**
 * Thin Firestore data layer over `community_drills/{docId}` and its `ratings/{uid}`
 * subcollection. All business logic (map shaping, rating aggregation) is delegated to
 * [CommunityDrillMapper] and [RatingAggregate] — this class is I/O only.
 */
class CommunityDrillRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    companion object {
        private const val TAG = "CommunityDrillRepository"
        private const val FETCH_LIMIT = 200
    }

    /**
     * Publish a drill to the community collection. Requires a signed-in, non-anonymous user.
     */
    suspend fun publish(drill: CommunityDrill): Result<String> {
        val user = auth.currentUser
        if (user == null || user.isAnonymous) {
            return Result.failure(IllegalStateException("Must be signed in with Google to publish"))
        }
        return try {
            val ref = firestore.collection(CommunityDrill.COLLECTION).document()
            ref.set(CommunityDrillMapper.toMap(drill)).await()
            Result.success(ref.id)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to publish drill", e)
            Result.failure(e)
        }
    }

    /**
     * Remove a shared drill. Only the original creator may unshare (rules also enforce this).
     */
    suspend fun unshare(communityId: String, creatorUid: String): Result<Unit> {
        val user = auth.currentUser
        if (user == null || user.uid != creatorUid) {
            return Result.failure(IllegalStateException("Only the creator can unshare this drill"))
        }
        return try {
            firestore.collection(CommunityDrill.COLLECTION)
                .document(communityId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unshare drill $communityId", e)
            Result.failure(e)
        }
    }

    /**
     * Fetch the most recently shared drills, newest first, capped at [FETCH_LIMIT].
     */
    suspend fun fetchAll(): Result<List<CommunityDrill>> {
        return try {
            val snapshot = firestore.collection(CommunityDrill.COLLECTION)
                .orderBy("sharedAtMs", Query.Direction.DESCENDING)
                .limit(FETCH_LIMIT.toLong())
                .get()
                .await()

            Result.success(snapshot.documents.mapNotNull { fromDoc(it) })
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch community drills", e)
            Result.failure(e)
        }
    }

    /**
     * Fetch a single community drill by id, or null if it doesn't exist / is malformed.
     */
    suspend fun fetchOne(communityId: String): Result<CommunityDrill?> {
        return try {
            val doc = firestore.collection(CommunityDrill.COLLECTION)
                .document(communityId)
                .get()
                .await()
            Result.success(fromDoc(doc))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch community drill $communityId", e)
            Result.failure(e)
        }
    }

    /**
     * Fetch the current user's own rating for a drill, or null if they haven't rated it.
     */
    suspend fun myRating(communityId: String, uid: String): Result<DrillRating?> {
        return try {
            val doc = firestore.collection(CommunityDrill.COLLECTION)
                .document(communityId)
                .collection("ratings")
                .document(uid)
                .get()
                .await()

            if (!doc.exists()) {
                return Result.success(null)
            }
            val stars = doc.getLong("stars")?.toInt() ?: return Result.success(null)
            Result.success(DrillRating(stars = stars, ratedAtMs = doc.getLong("ratedAtMs") ?: 0L))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch rating for $communityId", e)
            Result.failure(e)
        }
    }

    /**
     * Rate (or re-rate) a drill. Transactionally reads the drill's current aggregate and the
     * caller's previous rating (if any), then writes both the updated aggregate and the
     * per-user rating doc.
     */
    suspend fun rate(communityId: String, uid: String, stars: Int, nowMs: Long): Result<Unit> {
        val user = auth.currentUser
        if (user == null || user.isAnonymous || user.uid != uid) {
            return Result.failure(IllegalStateException("Must be signed in as $uid to rate this drill"))
        }
        return try {
            firestore.runTransaction { txn ->
                val drillRef = firestore.collection(CommunityDrill.COLLECTION).document(communityId)
                val ratingRef = drillRef.collection("ratings").document(uid)

                val drillSnap = txn.get(drillRef)
                val ratingSnap = txn.get(ratingRef)

                val previousStars = if (ratingSnap.exists()) ratingSnap.getLong("stars")?.toInt() else null
                val currentSum = drillSnap.getLong("ratingSum") ?: 0L
                val currentCount = drillSnap.getLong("ratingCount") ?: 0L

                val agg = RatingAggregate.applyRating(currentSum, currentCount, previousStars, stars)

                txn.update(drillRef, mapOf("ratingSum" to agg.sum, "ratingCount" to agg.count))
                txn.set(ratingRef, mapOf("stars" to stars, "ratedAtMs" to nowMs))
                null
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to rate drill $communityId", e)
            Result.failure(e)
        }
    }

    /**
     * Manually maps a `community_drills` document to [CommunityDrill]. Not using
     * `doc.toObject` because the model carries a computed `averageRating` and an `id` that
     * isn't a stored field. Returns null if a required field is missing.
     */
    private fun fromDoc(doc: DocumentSnapshot): CommunityDrill? {
        val name = doc.getString("name") ?: return null
        val baseTemplate = doc.getString("baseTemplate") ?: return null
        val referenceType = doc.getString("referenceType") ?: return null
        val creatorUid = doc.getString("creatorUid") ?: return null
        val creatorName = doc.getString("creatorName") ?: return null
        val focusCsv = doc.getString("focusCsv") ?: ""
        val perPhaseTargetsJson = doc.getString("perPhaseTargetsJson") ?: ""
        val creatorPhotoUrl = doc.getString("creatorPhotoUrl") ?: ""

        return CommunityDrill(
            id = doc.id,
            name = name,
            baseTemplate = baseTemplate,
            focusCsv = focusCsv,
            referenceType = referenceType,
            strictnessX = doc.getDouble("strictnessX")?.toFloat() ?: 1.0f,
            perPhaseTargetsJson = perPhaseTargetsJson,
            creatorUid = creatorUid,
            creatorName = creatorName,
            creatorPhotoUrl = creatorPhotoUrl,
            sharedAtMs = doc.getLong("sharedAtMs") ?: 0L,
            ratingSum = doc.getLong("ratingSum") ?: 0L,
            ratingCount = doc.getLong("ratingCount") ?: 0L,
        )
    }
}

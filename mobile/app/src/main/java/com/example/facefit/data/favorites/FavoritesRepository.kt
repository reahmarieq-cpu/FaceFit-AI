package com.example.facefit.data.favorites

import android.util.Log
import com.example.facefit.BuildConfig
import androidx.compose.ui.graphics.Color
import com.example.facefit.ui.catalog.SampleFrame
import com.example.facefit.ui.catalog.sampleFrames
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** Android Firestore's persistent disk cache is enabled by default. */
class FavoritesRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    data class Favorites(
        val frames: List<SampleFrame> = emptyList(),
        val userId: String? = null,
        val loading: Boolean = false,
        val error: String? = null,
    )

    /** One listener per account, including local writes and cached snapshots while offline. */
    fun observeFavorites(): Flow<Favorites> = callbackFlow {
        var registration: ListenerRegistration? = null
        var activeUid: String? = null
        var generation = 0
        var initialized = false
        var lastFrames = emptyList<SampleFrame>()
        val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val uid = firebaseAuth.currentUser?.uid
            if (!initialized || uid != activeUid) {
                initialized = true
                generation++
                val currentGeneration = generation
                registration?.remove()
                registration = null
                activeUid = uid
                lastFrames = emptyList()
                trySend(Favorites(userId = uid, loading = uid != null))
                if (uid != null) {
                    registration = firestore.collection("users").document(uid).collection("favorites")
                        .orderBy("savedAt", Query.Direction.DESCENDING)
                        .addSnapshotListener { snapshot, error ->
                            if (generation == currentGeneration && auth.currentUser?.uid == uid) {
                                if (error != null) {
                                    logFailure("listen", error)
                                    trySend(Favorites(lastFrames, uid, error =
                                        failureMessage(error)))
                                } else {
                                    lastFrames = snapshot?.documents.orEmpty().mapNotNull(::frameFromDocument)
                                    trySend(Favorites(lastFrames, uid))
                                }
                            }
                        }
                }
            }
        }
        auth.addAuthStateListener(authListener)
        awaitClose {
            generation++
            auth.removeAuthStateListener(authListener)
            registration?.remove()
        }
    }

    /** Queue the desired value immediately. Acknowledgement may wait until the device reconnects. */
    fun setFavorite(frame: SampleFrame, saved: Boolean, onError: (String) -> Unit) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onError("Sign in to save frames to your account.")
            return
        }
        try {
            val reference = firestore.collection("users").document(uid).collection("favorites").document(frame.id)
            val operation = if (saved) {
                reference.set(mapOf(
                    "frameId" to frame.id,
                    "name" to frame.name,
                    "category" to frame.category,
                    "colorName" to frame.colorName,
                    "assetPath" to frame.assetPath,
                    "tryOnAssetPath" to frame.tryOnAssetPath,
                    "savedAt" to FieldValue.serverTimestamp(),
                    "material" to frame.material,
                    "explanation" to frame.explanation,
                    "thumbnailAssetPath" to frame.thumbnailAssetPath,
                    "has3DModel" to frame.has3DModel,
                ), SetOptions.merge())
            } else reference.delete()

            operation.addOnFailureListener { error ->
                logFailure(if (saved) "save" else "remove", error)
                if (auth.currentUser?.uid == uid) onError(failureMessage(error))
            }
        } catch (error: Exception) {
            logFailure(if (saved) "save" else "remove", error)
            if (auth.currentUser?.uid == uid) onError(failureMessage(error))
        }
    }

    private fun logFailure(operation: String, error: Exception) {
        if (BuildConfig.DEBUG) {
            val code = (error as? FirebaseFirestoreException)?.code?.name ?: error.javaClass.simpleName
            Log.e("FavoritesError",
                "Favorites operation=$operation project=${firestore.app.options.projectId} code=$code", error)
        }
    }

    private fun failureMessage(error: Exception): String =
        when ((error as? FirebaseFirestoreException)?.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                "Your account cannot save favorites yet. Contact support."
            FirebaseFirestoreException.Code.UNAUTHENTICATED ->
                "Your sign-in session expired. Sign in again to save favorites."
            FirebaseFirestoreException.Code.UNAVAILABLE ->
                "Favorites sync is unavailable. Check your connection and try again."
            else -> "Could not save the favorite change. Please try again."
        }

    suspend fun isFavorite(frameId: String): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        return firestore.collection("users").document(uid).collection("favorites")
            .document(frameId).get().await().exists()
    }

    private fun frameFromDocument(document: DocumentSnapshot): SampleFrame? {
        val id = document.getString("frameId") ?: document.id
        val name = document.getString("name") ?: return null
        // Resolve bundled frames first to preserve lens/arm anchors and model calibration.
        sampleFrames.firstOrNull { it.id == id || it.name == name }?.let { return it.copy(id = id) }
        return SampleFrame(
            name = name,
            category = document.getString("category").orEmpty(),
            score = 0,
            color = Color(0xFF596B7C),
            material = document.getString("material").orEmpty(),
            colorName = document.getString("colorName").orEmpty(),
            explanation = document.getString("explanation").orEmpty(),
            assetPath = document.getString("assetPath"),
            tryOnAssetPath = document.getString("tryOnAssetPath"),
            has3DModel = document.getBoolean("has3DModel") ?: false,
            thumbnailAssetPath = document.getString("thumbnailAssetPath"),
            id = id,
        )
    }
}

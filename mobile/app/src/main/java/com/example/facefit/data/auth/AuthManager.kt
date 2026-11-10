package com.example.facefit.data.auth

import com.example.facefit.facefit.analysis.FacialGeometry

import android.content.Context
import android.util.Log
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.tasks.await

class AuthManager {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    val currentUser
        get() = auth.currentUser

    suspend fun signUp(firstName: String, lastName: String, email: String, password: String): Result<Unit> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val userId = result.user?.uid ?: throw Exception("User creation failed")

            val userProfile = hashMapOf(
                "uid" to userId,
                "firstName" to firstName,
                "lastName" to lastName,
                "email" to email,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            db.collection("users").document(userId).set(userProfile).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signIn(email: String, password: String): Result<Unit> {
        return try {
            auth.signInWithEmailAndPassword(email, password).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signInWithGoogle(idToken: String): Result<Unit> {
        return try {
            require(idToken.isNotBlank()) { "Google sign-in could not be completed." }
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val user = auth.signInWithCredential(credential).await().user
                ?: error("Google sign-in could not be completed.")

            // Initialize a missing profile without overwriting an existing user's edits.
            // Authentication can succeed even when profile storage is unavailable.
            withTimeoutOrNull(5_000L) {
                try {
                    val reference = db.collection("users").document(user.uid)
                    val fields = googleProfileFields(user) + mapOf(
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    db.runTransaction { transaction ->
                        if (!transaction.get(reference).exists()) {
                            transaction.set(reference, fields)
                        }
                        Unit
                    }.await()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    Log.w("AuthManager", "Google sign-in succeeded; profile storage is unavailable.")
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signOut(context: Context) {
        auth.signOut()
        GoogleSignInClient.clearSignInState(context)
    }

    private fun googleProfileFields(user: FirebaseUser): Map<String, Any> {
        val name = user.displayName?.trim().orEmpty()
        val names = name.split(Regex("\\s+"), limit = 2)
        return buildMap {
            put("uid", user.uid)
            put("email", user.email.orEmpty())
            put("firstName", names.firstOrNull()?.takeIf { it.isNotBlank() }
                ?: user.email?.substringBefore('@').orEmpty())
            put("lastName", names.getOrElse(1) { "" })
            user.photoUrl?.toString()?.let { put("photoUrl", it) }
        }
    }

    suspend fun getUserProfile(): Result<Map<String, Any>> {
        val user = auth.currentUser ?: return Result.failure(Exception("Not signed in"))
        val googleProfile = user.providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID }
            .let { if (it) googleProfileFields(user) else emptyMap() }
        return try {
            val document = db.collection("users").document(user.uid).get().await()
            when {
                document.exists() -> Result.success(googleProfile + document.data.orEmpty())
                googleProfile.isNotEmpty() -> Result.success(googleProfile)
                else -> Result.failure(Exception("Profile not found"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (googleProfile.isNotEmpty()) Result.success(googleProfile) else Result.failure(e)
        }
    }

    suspend fun saveFaceScan(
        foreheadWidth: Float,
        jawline: Float,
        eyeSpacing: Float,
        faceShape: String,
        shapeMeasurements: FacialGeometry.Measurements? = null,
        shapeSampleCount: Int = 1
    ): Result<Unit> {
        return try {
            val userId = auth.currentUser?.uid ?: throw Exception("Not signed in")
            val scan = hashMapOf(
                "foreheadWidth" to foreheadWidth,
                "jawline" to jawline,
                "eyeSpacing" to eyeSpacing,
                "faceShape" to faceShape,
                "measurementVersion" to 2,
                "measurementUnit" to "fractionOfCheekWidth",
                "shapeMethod" to "geometricEstimateV2",
                "shapeSampleCount" to shapeSampleCount,
                "shapeMeasurements" to shapeMeasurements?.let {
                    mapOf(
                        "lengthToWidth" to it.lengthToWidth,
                        "foreheadToCheek" to it.foreheadToCheek,
                        "jawToCheek" to it.jawToCheek,
                        "chinToJaw" to it.chinToJaw,
                        "jawAngleDegrees" to it.jawAngle
                    )
                },
                "createdAt" to FieldValue.serverTimestamp()
            )

            db.collection("users")
                .document(userId)
                .collection("faceScans")
                .add(scan)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

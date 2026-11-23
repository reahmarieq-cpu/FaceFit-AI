package com.example.facefit.data.auth

import com.example.facefit.facefit.analysis.FacialGeometry

import android.content.Context
import android.util.Log
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.Blob
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
            GoogleAuthDiagnostics.event("firebaseExchange")
            val user = auth.signInWithCredential(credential).await().user
                ?: error("Google sign-in could not be completed.")
            GoogleAuthDiagnostics.event("firebaseSessionCreated")

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
            GoogleAuthDiagnostics.failure("firebaseExchange", e)
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

    val passwordResetEmail: String?
        get() = auth.currentUser
            ?.takeIf { user -> user.providerData.any { it.providerId == EmailAuthProvider.PROVIDER_ID } }
            ?.email
            ?.takeIf { it.isNotBlank() }

    suspend fun sendPasswordResetEmail(): Result<Unit> {
        val email = passwordResetEmail ?: return Result.failure(IllegalStateException("Password reset unavailable"))
        return try {
            auth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateProfile(
        fullName: String,
        phone: String,
        age: Int?,
        gender: String,
        photo: ByteArray?
    ): Result<Map<String, Any>> {
        val user = auth.currentUser ?: return Result.failure(IllegalStateException("Not signed in"))
        val names = fullName.trim().split(Regex("\\s+"), limit = 2)
        if (names.first().isBlank()) return Result.failure(IllegalArgumentException("Full name is required"))
        if (age != null && age !in 0..125) {
            return Result.failure(IllegalArgumentException("Age is out of range"))
        }

        val profile = db.collection("users").document(user.uid)
        val fields = buildMap<String, Any> {
            put("firstName", names.first())
            put("lastName", names.getOrElse(1) { "" })
            put("phone", phone.trim())
            put("gender", gender)
            age?.let { put("age", it) }
            photo?.let { put("profilePhoto", Blob.fromBytes(it)) }
        }
        val changes = fields + ("updatedAt" to FieldValue.serverTimestamp())
        return try {
            db.runTransaction { transaction ->
                if (transaction.get(profile).exists()) {
                    val updates = if (age == null) changes + ("age" to FieldValue.delete()) else changes
                    transaction.update(profile, updates)
                } else {
                    transaction.set(profile, changes + mapOf(
                        "uid" to user.uid,
                        "email" to user.email.orEmpty(),
                        "createdAt" to FieldValue.serverTimestamp()
                    ))
                }
                Unit
            }.await()
            Result.success(fields)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observeAccountId(onChanged: (String?) -> Unit): () -> Unit {
        val listener = FirebaseAuth.AuthStateListener { onChanged(it.currentUser?.uid) }
        auth.addAuthStateListener(listener)
        return { auth.removeAuthStateListener(listener) }
    }

    private fun faceScans(userId: String) =
        db.collection("users").document(userId).collection("faceScans")

    suspend fun getSavedScanCount(userId: String): Result<Long> {
        if (auth.currentUser?.uid != userId) return Result.failure(IllegalStateException("Account changed"))
        return try {
            Result.success(faceScans(userId).count().get(AggregateSource.SERVER).await().count)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveFaceScan(
        foreheadWidth: Float,
        jawline: Float,
        eyeSpacing: Float,
        faceShape: String,
        shapeMeasurements: FacialGeometry.Measurements? = null,
        shapeSampleCount: Int = 1,
        shapeModelId: String? = null,
        shapeModelScores: Map<String, Float> = emptyMap(),
        shapeModelFeatures: List<Double> = emptyList(),
        shapeDecisionPolicy: String? = null
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
                "shapeMethod" to shapeModelId,
                "shapeModelScores" to shapeModelScores,
                "shapeModelFeatures" to shapeModelFeatures,
                "shapeDecisionPolicy" to shapeDecisionPolicy,
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

            faceScans(userId).add(scan).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

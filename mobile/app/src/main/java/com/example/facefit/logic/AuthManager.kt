package com.example.facefit.logic

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

    fun signOut() {
        auth.signOut()
    }

    suspend fun getUserProfile(): Result<Map<String, Any>> {
        return try {
            val userId = auth.currentUser?.uid ?: throw Exception("Not signed in")
            val document = db.collection("users").document(userId).get().await()
            if (document.exists()) {
                Result.success(document.data ?: emptyMap())
            } else {
                Result.failure(Exception("Profile not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveFaceScan(
        foreheadWidth: Float,
        jawline: Float,
        eyeSpacing: Float,
        faceShape: String
    ): Result<Unit> {
        return try {
            val userId = auth.currentUser?.uid ?: throw Exception("Not signed in")
            val scan = hashMapOf(
                "foreheadWidth" to foreheadWidth,
                "jawline" to jawline,
                "eyeSpacing" to eyeSpacing,
                "faceShape" to faceShape,
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

package com.facefit.kiosk.data.auth

import com.facefit.kiosk.model.ShopAccount
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class FirebaseAuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : ShopAuthRepository {
    override val currentShopId: String? get() = auth.currentUser?.uid

    override fun signOut() = auth.signOut()

    override suspend fun restore(): AuthResult = withContext(Dispatchers.IO) {
        signInMutex.withLock {
            val user = auth.currentUser ?: return@withLock AuthResult.InvalidCredentials
            try {
                val reloaded = withTimeoutOrNull(8_000L) {
                    user.reload().await()
                    true
                } ?: false
                if (!reloaded) return@withLock AuthResult.BackendUnavailable
                val result = verify(user, user.email.orEmpty())
                if (result == AuthResult.ShopNotAuthorized) auth.signOut()
                result
            } catch (error: CancellationException) {
                throw error
            } catch (error: com.google.firebase.FirebaseException) {
                classify(error).also {
                    if (it == AuthResult.ShopNotAuthorized || it == AuthResult.InvalidCredentials) auth.signOut()
                }
            }
        }
    }

    override suspend fun signIn(email: String, password: String): AuthResult =
        withContext(Dispatchers.IO) {
            signInMutex.withLock {
                auth.signOut()
                var accepted = false
                try {
                    // Firebase sign-in can still change currentUser after an await is cancelled.
                    // Wait for that task to settle before permitting another sign-in.
                    val user = withContext(NonCancellable) {
                        auth.signInWithEmailAndPassword(email.trim(), password).await().user
                    } ?: return@withLock AuthResult.InvalidCredentials
                    ensureActive()

                    verify(user, email.trim()).also { accepted = it is AuthResult.Success }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: com.google.firebase.FirebaseException) {
                    classify(error)
                } finally {
                    if (!accepted) auth.signOut()
                }
            }
        }

    private suspend fun verify(user: FirebaseUser, email: String): AuthResult =
        withTimeoutOrNull(8_000L) {
            val shop = firestore.collection("shops").document(user.uid).get(Source.SERVER).await()
            val name = shop.getString("displayName")?.trim().orEmpty()
            val retailerId = shop.getString("retailerId")
            if (shop.get("enabled") != true || name.isBlank() || retailerId.isNullOrBlank() ||
                retailerId == "." || retailerId == ".." || '/' in retailerId || '\\' in retailerId) {
                return@withTimeoutOrNull AuthResult.ShopNotAuthorized
            }
            val retailer = firestore.collection("retailers").document(retailerId).get(Source.SERVER).await()
            val legacyPlan = isEligibleRetailer(
                user.uid, retailer.getString("userId"), retailer.getString("status"),
                retailer.getString("subscriptionPlan"), retailer.getString("subscriptionStatus")
            )
            val activeBundle = !legacyPlan && retailerId == user.uid &&
                retailer.getString("status") == "approved" && hasActiveBundle(retailerId)
            if ((!legacyPlan && !activeBundle) || auth.currentUser?.uid != user.uid) {
                return@withTimeoutOrNull AuthResult.ShopNotAuthorized
            }
            AuthResult.Success(ShopAccount(user.uid, name, user.email ?: email))
        } ?: AuthResult.BackendUnavailable

    private suspend fun hasActiveBundle(retailerId: String): Boolean {
        val subscription = firestore.collection("subscriptions").document(retailerId)
            .get(Source.SERVER).await()
        val expiresAt = subscription.get("expiresAt")
        return subscription.getString("userId") == retailerId &&
            subscription.getString("planType") == "bundle" &&
            subscription.getString("status") == "demo_active" &&
            subscription.getString("paymentMode") == "simulation" &&
            (expiresAt == null || (expiresAt is com.google.firebase.Timestamp &&
                expiresAt > com.google.firebase.Timestamp.now()))
    }

    private fun classify(error: com.google.firebase.FirebaseException): AuthResult = when {
        error is FirebaseAuthInvalidCredentialsException || error is FirebaseAuthInvalidUserException ->
            AuthResult.InvalidCredentials
        error is FirebaseFirestoreException && error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            AuthResult.ShopNotAuthorized
        else -> AuthResult.BackendUnavailable
    }

    private companion object {
        val signInMutex = Mutex()
    }
}

internal fun isEligibleRetailer(uid: String, owner: String?, status: String?, plan: String?, subscription: String?) =
    uid == owner && status == "approved" && plan == "kioskplan" && subscription == "active"

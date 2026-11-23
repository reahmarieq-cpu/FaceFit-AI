package com.example.facefit.data.auth

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.ClearCredentialException
import kotlinx.coroutines.CancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/** Opens Google's account chooser. Firebase verifies the returned token in AuthManager. */
class GoogleSignInClient(private val context: Context) {
    private val credentialManager = CredentialManager.create(context)

    suspend fun getIdToken(): String {
        val activity = generateSequence(context) { (it as? ContextWrapper)?.baseContext }
            .take(12).filterIsInstance<Activity>().firstOrNull()
            ?: error("Google sign-in needs an active app screen. Reopen sign-in and try again.")
        check(!activity.isFinishing && !activity.isDestroyed) {
            "The sign-in screen has closed. Reopen it and try again."
        }
        try {
            val option = GetSignInWithGoogleOption.Builder(serverClientId()).build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            GoogleAuthDiagnostics.event("credentialRequest")
            val credential = credentialManager.getCredential(activity, request).credential
            check(credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                "Google returned an unsupported credential. Please try again."
            }
            return GoogleIdTokenCredential.createFrom(credential.data).idToken.also {
                check(it.isNotBlank()) { "Google did not return a sign-in token. Please try again." }
                GoogleAuthDiagnostics.event("credentialReceived")
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            GoogleAuthDiagnostics.failure("credentialRequest", error)
            throw error
        }
    }

    // The Google services plugin only generates this resource when a Web OAuth client is configured.
    @SuppressLint("DiscouragedApi")
    private fun serverClientId(): String {
        val resourceId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        val clientId = if (resourceId != 0) context.getString(resourceId) else ""
        check(clientId.isNotBlank()) { "Google sign-in is not available yet. Please use email sign-in." }
        return clientId
    }

    companion object {
        suspend fun clearSignInState(context: Context) {
            try {
                CredentialManager.create(context.applicationContext)
                    .clearCredentialState(ClearCredentialStateRequest())
            } catch (_: ClearCredentialException) {
                // Firebase has already signed out; a provider failure must not leave the UI signed in.
                Log.w("GoogleSignIn", "Could not clear the credential provider's sign-in state.")
            }
        }
    }
}

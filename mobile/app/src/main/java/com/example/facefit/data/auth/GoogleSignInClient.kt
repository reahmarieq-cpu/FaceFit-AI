package com.example.facefit.data.auth

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.ClearCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/** Opens Google's account chooser. Firebase verifies the returned token in AuthManager. */
class GoogleSignInClient(private val context: Context) {
    private val credentialManager = CredentialManager.create(context)

    suspend fun getIdToken(): String {
        val option = GetSignInWithGoogleOption.Builder(serverClientId()).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential = credentialManager.getCredential(context, request).credential
        check(credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Google sign-in could not be completed. Please try again."
        }
        return GoogleIdTokenCredential.createFrom(credential.data).idToken
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

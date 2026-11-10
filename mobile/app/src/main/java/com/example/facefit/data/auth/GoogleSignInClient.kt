package com.example.facefit.data.auth

import android.content.Context
import android.util.Log
import com.example.facefit.R
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

    // Generated from the Web OAuth client in app/google-services.json.
    private fun serverClientId(): String =
        context.getString(R.string.default_web_client_id).also {
            check(it.isNotBlank()) { "Google sign-in is not available yet. Please use email sign-in." }
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

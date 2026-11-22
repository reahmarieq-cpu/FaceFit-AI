package com.example.facefit.data.auth

import android.util.Log
import androidx.credentials.exceptions.GetCredentialException
import com.example.facefit.BuildConfig
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuthException

/** Never log ID tokens, credential bundles, account identifiers, or exception messages. */
internal object GoogleAuthDiagnostics {
    fun event(stage: String) {
        if (BuildConfig.DEBUG) Log.i("GoogleSignIn", "stage=$stage")
    }

    fun failure(stage: String, error: Throwable) {
        if (!BuildConfig.DEBUG) return
        val causes = generateSequence(error) { it.cause }.take(12).toList()
        val status = causes.filterIsInstance<ApiException>().firstOrNull()?.statusCode
        val credentialType = causes.filterIsInstance<GetCredentialException>().firstOrNull()?.type
        val firebaseCode = causes.filterIsInstance<FirebaseAuthException>().firstOrNull()?.errorCode
        Log.e("GoogleSignIn", "stage=$stage exception=${error.javaClass.simpleName} " +
            "statusCode=${status ?: "unavailable"} credentialType=$credentialType firebaseCode=$firebaseCode")
    }
}

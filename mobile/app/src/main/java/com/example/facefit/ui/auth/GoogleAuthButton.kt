package com.example.facefit.ui.auth

import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.facefit.data.auth.AuthManager
import com.example.facefit.data.auth.GoogleSignInClient
import com.example.facefit.ui.theme.FaceFitTextPrimary
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Google creates an account on first use and signs in the same account on later visits. */
@Composable
internal fun GoogleAuthButton(
    authManager: AuthManager,
    enabled: Boolean,
    onLoadingChange: (Boolean) -> Unit,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    val client = remember(context) { GoogleSignInClient(context) }
    val scope = rememberCoroutineScope()
    var launching by remember { mutableStateOf(false) }
    val loadingCallback by rememberUpdatedState(onLoadingChange)
    val successCallback by rememberUpdatedState(onSuccess)

    OutlinedButton(
        onClick = {
            if (!launching) {
                launching = true
                loadingCallback(true)
                scope.launch {
                    try {
                        val token = client.getIdToken()
                        authManager.signInWithGoogle(token).getOrThrow()
                        successCallback()
                    } catch (_: GetCredentialCancellationException) {
                        // Closing the chooser leaves the form available for another attempt.
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        val message = when (e) {
                            is FirebaseAuthUserCollisionException ->
                                "This email already uses another sign-in method. Please sign in with that method."
                            is FirebaseNetworkException ->
                                "Check your internet connection and try again."
                            is NoCredentialException ->
                                "No Google account is available. Add one on your phone and try again."
                            is GetCredentialException ->
                                "Google sign-in could not complete. Check Google Play services and try again."
                            is FirebaseAuthException ->
                                "Google sign-in is unavailable. Please try email sign-in."
                            is IllegalStateException -> e.message
                                ?: "Google sign-in could not complete. Please try again."
                            else -> "Google sign-in could not complete. Please try again."
                        }
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    } finally {
                        launching = false
                        loadingCallback(false)
                    }
                }
            }
        },
        enabled = enabled && !launching,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = FaceFitTextPrimary)
    ) {
        Text("G Continue with Google", fontWeight = FontWeight.SemiBold)
    }
}

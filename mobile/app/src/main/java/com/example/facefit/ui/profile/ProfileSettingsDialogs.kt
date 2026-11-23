package com.example.facefit.ui.profile

import com.example.facefit.ui.components.AppMessages
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.facefit.data.auth.AuthManager
import kotlinx.coroutines.launch

@Composable
internal fun PrivacySecurityDialog(authManager: AuthManager, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val resetEmail = authManager.passwordResetEmail
    var sending by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    var resetError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Privacy & Security") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Review FaceFit AI’s camera access and other permissions in Android settings.")
                TextButton(onClick = { context.openProfileSystemSettings() }) {
                    Text("Manage app permissions")
                }
                if (resetEmail != null) {
                    Text("To change your password, send a reset link to your account email.")
                    TextButton(
                        enabled = !sending && !sent,
                        onClick = {
                            if (sending || sent) return@TextButton
                            sending = true
                            resetError = false
                            scope.launch {
                                sent = authManager.sendPasswordResetEmail().isSuccess
                                sending = false
                                resetError = !sent
                            }
                        }
                    ) { Text(if (sending) "Sending…" else "Send password reset email") }
                    if (sent) Text("Reset link sent. Check your email.")
                    if (resetError) Text("Could not send the email. Try again.", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

internal fun Context.openProfileSystemSettings(notifications: Boolean = false) {
    if (notifications) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        try {
            startActivity(intent)
            return
        } catch (_: ActivityNotFoundException) {
            // Some devices only expose the general app settings screen.
        }
    }

    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        AppMessages.show("App settings are unavailable on this device.")
    }
}

package com.example.facefit.ui.profile

import androidx.activity.compose.BackHandler
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.facefit.data.privacy.PrivacyRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySecurityScreen(onBack: () -> Unit, onScanCleared: () -> Unit, onAccountDeleted: () -> Unit) {
    val context = LocalContext.current
    val repository = remember(context) { PrivacyRepository(context) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var analytics by remember { mutableStateOf(repository.analyticsEnabled) }
    var cameraGranted by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf<String?>(null) }
    var document by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    BackHandler(enabled = busy) { /* Keep a destructive request attached to its screen. */ }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, context) {
        fun refresh() { cameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED }
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        refresh(); owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    fun runAction(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { snackbar.showSnackbar(error.localizedMessage ?: "Unable to complete this request. Please try again.") }
            finally { busy = false }
        }
    }
    Scaffold(
        containerColor = Color(0xFFF8FAFC),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TopAppBar(title = { Text("Privacy & Security", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack, enabled = !busy) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
            } }) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { PrivacyCard {
                Icon(Icons.Outlined.Shield, null, tint = Color(0xFF0F766E))
                Text("Face tracking runs on your device", fontWeight = FontWeight.Bold)
                Text("MediaPipe processes camera frames locally. The camera pipeline does not upload or record video. A frame stays in memory while paired try-on is displayed, then is released. Saved scan measurements can sync to your Firebase account.")
            } }
            item { Text("PERMISSIONS & STORAGE", fontWeight = FontWeight.SemiBold) }
            item { PrivacyCard {
                ToggleRow("Usage analytics", "Optional Firebase usage analytics. Off by default. Uses app-instance identifiers, not anonymous data. No face measurements are sent to analytics.", analytics, !busy) {
                    repository.setAnalyticsEnabled(it); analytics = it
                }
                HorizontalDivider()
                Text("Camera permission: " + if (cameraGranted) "Granted" else "Denied", fontWeight = FontWeight.SemiBold)
                TextButton(enabled = !busy, onClick = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName)))
                }) { Text("Manage in App Settings") }
            } }
            item { Text("YOUR DATA", fontWeight = FontWeight.SemiBold) }
            item { PrivacyCard {
                ActionRow("Export My Data", "Share account data, cloud favorites and scans, and local preferences. Requires internet when signed in.", !busy) {
                    runAction { context.startActivity(repository.exportData()) }
                }
                HorizontalDivider()
                ActionRow("Clear Stored Scan Data", "Remove measurements from this device. Cloud records are not deleted.", !busy) { confirmation = "clear" }
            } }
            item { Text("PRIVACY INFORMATION", fontWeight = FontWeight.SemiBold) }
            item { PrivacyCard {
                listOf("Privacy Policy", "Biometric Information Notice", "Terms of Service").forEach { title ->
                    ActionRow(title, null, !busy) { document = title }
                }
            } }
            item { OutlinedButton(enabled = !busy && repository.signedIn, onClick = { confirmation = "offload" },
                modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626))) {
                Text("Offload Account")
            } }
            if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Completing your request…") }
        }
    }
    confirmation?.let { kind ->
        AlertDialog(onDismissRequest = { if (!busy) confirmation = null },
            title = { Text(if (kind == "clear") "Clear stored scan data?" else "Offload your account?") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (kind == "clear") "This removes face measurements and recent results from this device. Existing cloud records remain."
                    else "Your account is archived, not deleted. Your profile, favorites and scan records stay safely stored in the cloud, and this device's copies are removed. You will be signed out. Sign in again anytime to restore everything.")
            } },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                if (kind == "clear") {
                    repository.scans.clearScan(); onScanCleared(); confirmation = null
                    scope.launch { snackbar.showSnackbar("Scan data cleared from this device.") }
                } else {
                    confirmation = null
                    runAction { repository.offloadAccount(); onAccountDeleted() }
                }
            }) { Text(if (kind == "clear") "Clear data" else "Offload") } },
            dismissButton = { TextButton(onClick = { confirmation = null }, enabled = !busy) { Text("Cancel") } })
    }
    document?.let { title ->
        ModalBottomSheet(onDismissRequest = { document = null }) {
            LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item { Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
                item { Text(privacyDocument(title)) }
                item { TextButton(onClick = { document = null }) { Text("Close") } }
            }
        }
    }
}

@Composable
private fun PrivacyCard(content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun ToggleRow(title: String, description: String, checked: Boolean, enabled: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Text(description, style = MaterialTheme.typography.bodySmall) }
        Switch(checked = checked, onCheckedChange = change, enabled = enabled)
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String?, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(vertical = 10.dp)) {
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall) } }
        Icon(Icons.Outlined.ChevronRight, null)
    }
}

private fun privacyDocument(title: String): String = when (title) {
    "Biometric Information Notice" -> "FaceFit estimates facial landmarks, proportions and face shape to position glasses and recommend frames. These estimates are not used by this feature to identify you or authenticate your identity. Camera frames are processed on your device; paired preview retains a frame only while rendering it. With scan history enabled, measurements and landmarks are cached locally; signed-in scan measurements also sync to Firebase. Clear stored scan data to remove local results. Offloading your account archives your cloud records and signs you out; signing in again restores them. A face-shape estimate is not a medical assessment."
    "Privacy Policy" -> "FaceFit uses camera access for scanning and virtual try-on, account details for sign-in, and saved favorites and measurements for recommendations. Firebase Authentication manages sign-in; Firestore stores account profiles, favorites and scan measurements. Google sign-in is handled by Google. Optional Firebase Analytics is off by default and can be disabled here; disabling resets local analytics data but does not erase previously collected server data. Nearby shops may use location if you grant permission. Local scan storage is isolated by account and excluded from Android backup. Clear local scans here, or offload your account to archive its cloud data and sign out; signing in again restores it. Exports contain personal information: share them only with a trusted destination. Cloud providers may retain operational backups under their own policies. This screen describes the implemented features; an operator privacy contact and legally reviewed retention policy must be published before commercial release."
    else -> "FaceFit provides approximate frame recommendations and virtual previews. Fit, color and appearance can differ from the physical product; confirm measurements and availability with the retailer. Keep account credentials secure and use the app only with permission to process the face shown. This is product information, not a complete commercial contract. Operator identity, contact details, jurisdiction, age requirements and legally reviewed terms must be supplied before commercial release."
}

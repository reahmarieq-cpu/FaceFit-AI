package com.example.facefit.ui.profile

import android.widget.Toast
import com.example.facefit.notifications.rememberNotificationSettingsAction
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.facefit.data.auth.AuthManager
import com.example.facefit.ui.components.FaceFitBottomNavBar
import com.example.facefit.ui.components.ProfileMenuItem
import com.example.facefit.ui.theme.*
import com.google.firebase.firestore.Blob

@Composable
fun ProfileScreen(
    authManager: AuthManager,
    accountId: String?,
    savedFrameCount: Int,
    onNavigate: (String) -> Unit = {},
    onSignOut: () -> Unit = {}
) {
    var userData by remember(accountId) { mutableStateOf<Map<String, Any>?>(null) }
    val context = LocalContext.current
    val openNotifications = rememberNotificationSettingsAction {
        context.openProfileSystemSettings(notifications = true)
    }
    val scope = rememberCoroutineScope()
    var signingOut by remember { mutableStateOf(false) }

    var loadingProfile by remember(accountId) { mutableStateOf(true) }
    var editingProfile by rememberSaveable { mutableStateOf(false) }
    var showingPrivacy by remember { mutableStateOf(false) }

    var scanCount by remember(accountId) { mutableStateOf<Long?>(null) }
    var scanCountUnavailable by remember(accountId) { mutableStateOf(false) }
    LaunchedEffect(authManager, accountId) {
        if (accountId != null) {
            authManager.getSavedScanCount(accountId)
                .onSuccess { scanCount = it }
                .onFailure { scanCountUnavailable = true }
        }
    }

    LaunchedEffect(authManager, accountId) {
        userData = authManager.getUserProfile().getOrNull()
            ?: authManager.currentUser?.email?.let { mapOf("email" to it) }
        loadingProfile = false
    }

    if (editingProfile && !loadingProfile) {
        EditProfileScreen(
            authManager = authManager,
            profile = userData.orEmpty(),
            onBack = { editingProfile = false },
            onSaved = { fields ->
                userData = (userData.orEmpty() - "age") + fields
                editingProfile = false
                Toast.makeText(context, "Profile updated", Toast.LENGTH_SHORT).show()
            }
        )
        return
    }
    if (showingPrivacy) {
        PrivacySecurityDialog(authManager = authManager, onDismiss = { showingPrivacy = false })
    }

    val openEditor: () -> Unit = {
        if (loadingProfile) {
            Toast.makeText(context, "Loading your profile. Try again in a moment.", Toast.LENGTH_SHORT).show()
        } else {
            editingProfile = true
        }
    }
    val fullName = userData.orEmpty().profileFullName()
    val photo = remember(userData) { (userData?.get("profilePhoto") as? Blob)?.toBytes() }

    Scaffold(
        bottomBar = {
            FaceFitBottomNavBar(currentRoute = "profile", onNavigate = onNavigate)
        },
        containerColor = FaceFitBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Profile",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = FaceFitTextPrimary
                )
                IconButton(
                    onClick = openEditor,
                    modifier = Modifier
                        .softContainerShadow(RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                ) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            ProfileAvatar(fullName, photo, modifier = Modifier.size(100.dp))

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = fullName.ifEmpty { if (loadingProfile) "Loading…" else "Your profile" },
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = FaceFitTextPrimary
            )
            Text(
                text = userData?.get("email") as? String ?: "...",
                fontSize = 14.sp,
                color = FaceFitTextSecondary
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Stats Row
            Card(
                modifier = Modifier.fillMaxWidth().softContainerShadow(RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(if (accountId == null) "0" else scanCount?.toString() ?: "–", "SCANS", FaceFitTextPrimary)
                    VerticalDivider(modifier = Modifier.height(40.dp), color = FaceFitBorder)
                    StatItem(savedFrameCount.toString(), "SAVED", Color(0xFFFF7878))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (scanCountUnavailable) {
                Text(
                    text = "Scan count is unavailable.",
                    fontSize = 12.sp,
                    color = FaceFitTextSecondary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            // Menu List
            Card(
                modifier = Modifier.fillMaxWidth().softContainerShadow(RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    ProfileMenuItem(icon = Icons.Outlined.Person, label = "Edit Profile", onClick = openEditor)
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = FaceFitBackground)
                    ProfileMenuItem(icon = Icons.Outlined.Notifications, label = "Notifications", onClick = openNotifications)
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = FaceFitBackground)
                    ProfileMenuItem(icon = Icons.Outlined.Shield, label = "Privacy & Security") {
                        showingPrivacy = true
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = FaceFitBackground)
                    ProfileMenuItem(icon = Icons.AutoMirrored.Outlined.Logout, label = "Sign Out") {
                        if (!signingOut) {
                            signingOut = true
                            scope.launch {
                                try {
                                    authManager.signOut(context)
                                    onSignOut()
                                } finally {
                                    signingOut = false
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(value: String, label: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = valueColor)
        Text(text = label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = FaceFitTextSecondary)
    }
}

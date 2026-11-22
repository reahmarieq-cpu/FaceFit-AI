package com.example.facefit.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.facefit.data.auth.AuthManager
import com.example.facefit.ui.components.FaceFitBottomNavBar
import com.example.facefit.ui.components.ProfileMenuItem
import com.example.facefit.ui.theme.*

@Composable
fun ProfileScreen(
    authManager: AuthManager,
    onNavigate: (String) -> Unit = {},
    onSignOut: () -> Unit = {},
    savedCount: Int = 0,
    recentFaceShape: String? = null,
    favoritesLoading: Boolean = false,
    favoritesError: String? = null,
    onNavigateToResults: () -> Unit = { onNavigate("scanResults") },
    onNavigateToScan: () -> Unit = { onNavigate("scan") }
) {
    var userData by remember { mutableStateOf<Map<String, Any>?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var signingOut by remember { mutableStateOf(false) }
    
    LaunchedEffect(authManager.currentUser?.uid) {
        authManager.getUserProfile().onSuccess {
            userData = it
        }
    }

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
                    onClick = { /* Edit Profile */ },
                    modifier = Modifier
                        .softContainerShadow(RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                ) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Avatar Section
            val initials = if (userData != null) {
                "${(userData!!["firstName"] as? String)?.take(1) ?: ""}${(userData!!["lastName"] as? String)?.take(1) ?: ""}"
            } else "..."

            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF5B7FFF), Color(0xFF128C98))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "${userData?.get("firstName") ?: "Loading"} ${userData?.get("lastName") ?: ""}",
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

            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                StatTile(
                    title = "Saved",
                    value = if (favoritesLoading) "..." else savedCount.toString(),
                    icon = Icons.Outlined.FavoriteBorder,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onClick = { onNavigate("favorites") },
                )
                StatTile(
                    title = "Face Shape",
                    value = recentFaceShape ?: "Not Scanned",
                    icon = Icons.Outlined.Face,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onClick = {
                        if (recentFaceShape != null) onNavigateToResults() else onNavigateToScan()
                    },
                )
            }
            Spacer(Modifier.height(24.dp))
            favoritesError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
            }

            // Menu List
            Card(
                modifier = Modifier.fillMaxWidth().softContainerShadow(RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    ProfileMenuItem(icon = Icons.Outlined.Person, label = "Edit Profile") {}
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = FaceFitBackground)
                    ProfileMenuItem(icon = Icons.Outlined.Notifications, label = "Notifications") {}
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = FaceFitBackground)
                    ProfileMenuItem(icon = Icons.Outlined.Shield, label = "Privacy & Security") { onNavigate("privacySecurity") }
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
private fun StatTile(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier.clip(RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null,
                tint = Color(0xFF0F766E), modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(8.dp))
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B), textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
                    Text(title, fontSize = 13.sp, color = Color(0xFF64748B),
                textAlign = TextAlign.Center)
        }
    }
}

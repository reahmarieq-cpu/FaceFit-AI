package com.example.facefit.ui.screens

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.facefit.logic.AuthManager
import com.example.facefit.ui.components.FaceFitBottomNavBar
import com.example.facefit.ui.components.ProfileMenuItem
import com.example.facefit.ui.theme.*

@Composable
fun ProfileScreen(
    authManager: AuthManager,
    onNavigate: (String) -> Unit = {},
    onSignOut: () -> Unit = {}
) {
    var userData by remember { mutableStateOf<Map<String, Any>?>(null) }
    
    LaunchedEffect(Unit) {
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
                        .clip(RoundedCornerShape(12.dp))
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

            // Stats Row
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem("3", "SCANS", FaceFitTextPrimary)
                    VerticalDivider(modifier = Modifier.height(40.dp), color = FaceFitBorder)
                    StatItem("2", "SAVED", Color(0xFFFF7878))
                    VerticalDivider(modifier = Modifier.height(40.dp), color = FaceFitBorder)
                    StatItem("Oval", "SHAPE", FaceFitTeal)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Menu List
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    ProfileMenuItem(icon = Icons.Outlined.Person, label = "Edit Profile") {}
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = FaceFitBackground)
                    ProfileMenuItem(icon = Icons.Outlined.Notifications, label = "Notifications") {}
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = FaceFitBackground)
                    ProfileMenuItem(icon = Icons.Outlined.Shield, label = "Privacy & Security") {}
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = FaceFitBackground)
                    ProfileMenuItem(icon = Icons.AutoMirrored.Outlined.Logout, label = "Sign Out") {
                        authManager.signOut()
                        onSignOut()
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

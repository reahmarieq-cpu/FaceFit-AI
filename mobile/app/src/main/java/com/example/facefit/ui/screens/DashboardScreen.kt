package com.example.facefit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.facefit.logic.AuthManager
import com.example.facefit.ui.components.FaceFitBottomNavBar
import com.example.facefit.ui.components.FaceFitCard
import com.example.facefit.ui.components.FaceFitLogo
import com.example.facefit.ui.theme.*

@Composable
fun DashboardScreen(
    authManager: AuthManager? = null,
    userName: String = "User",
    onNavigate: (String) -> Unit = {}
) {
    var fullName by remember(userName) { mutableStateOf(userName) }

    LaunchedEffect(authManager) {
        authManager?.getUserProfile()?.onSuccess { userData ->
            val firstName = userData["firstName"] as? String
            val lastName = userData["lastName"] as? String
            fullName = listOfNotNull(firstName, lastName)
                .joinToString(" ")
                .ifBlank { userName }
        }
    }

    Scaffold(
        bottomBar = {
            FaceFitBottomNavBar(currentRoute = "dashboard", onNavigate = onNavigate)
        },
        containerColor = FaceFitBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FaceFitLogo(size = 48)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(text = "Welcome,", fontSize = 12.sp, color = FaceFitTextSecondary)
                        Text(text = fullName, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = FaceFitTextPrimary)
                    }
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.NotificationsNone,
                        contentDescription = "Notifications",
                        tint = FaceFitTextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Promo Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color(0xFF128C98), Color(0xFF5B7FFF))
                        )
                    )
                    .padding(20.dp)
            ) {
                Column(modifier = Modifier.fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(
                            text = "AI FACE SCAN",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Find your perfect frames",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            lineHeight = 28.sp
                        )
                        Text(
                            text = "Scan your face and get instant\ncompatibility matches",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                    Button(
                        onClick = { onNavigate("scan") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(text = "Start scan →", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
                // Glasses Placeholder (could be an image)
                Icon(
                    imageVector = Icons.Outlined.RemoveRedEye, // Using an icon as placeholder for glasses
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(80.dp),
                    tint = Color.White.copy(alpha = 0.2f)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Explore",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = FaceFitTextPrimary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Grid
            Row(modifier = Modifier.fillMaxWidth()) {
                FaceFitCard(
                    title = "Scan History",
                    description = "Past results",
                    icon = Icons.Outlined.History,
                    iconBackgroundColor = FaceFitTeal,
                    modifier = Modifier.weight(1f)
                )
                FaceFitCard(
                    title = "Favorites",
                    description = "Saved frames",
                    icon = Icons.Outlined.Favorite,
                    iconBackgroundColor = FaceFitPink,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigate("favorites") }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                FaceFitCard(
                    title = "Nearby Shops",
                    description = "Optical stores",
                    icon = Icons.Outlined.LocationOn,
                    iconBackgroundColor = FaceFitBlue,
                    modifier = Modifier.weight(1f)
                )
                FaceFitCard(
                    title = "My Profile",
                    description = "Manage account",
                    icon = Icons.Outlined.Person,
                    iconBackgroundColor = FaceFitOrange,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigate("profile") }
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DashboardScreenPreview() {
    FacefitTheme {
        DashboardScreen()
    }
}

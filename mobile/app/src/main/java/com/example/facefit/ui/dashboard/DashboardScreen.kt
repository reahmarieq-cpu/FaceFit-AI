package com.example.facefit.ui.dashboard

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
import com.example.facefit.data.auth.AuthManager
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
                        .softContainerShadow(RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp))
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

            // Scan banner: text and CTA stay readable beside the decorative glasses.
            Box(
                modifier = Modifier.fillMaxWidth().height(200.dp)
                    .softContainerShadow(RoundedCornerShape(24.dp))
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.horizontalGradient(
                        listOf(Color(0xFF139BA7), Color(0xFF347ED1))
                    ))
            ) {
                Box(Modifier.align(Alignment.TopEnd)
                    .offset(x = 48.dp, y = (-64).dp).size(184.dp)
                    .background(Color.White.copy(alpha = 0.15f), CircleShape))
                androidx.compose.foundation.Image(
                    painter = painterResource(com.example.facefit.R.drawable.dashboard_scan_glasses),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.align(Alignment.BottomEnd)
                        .offset(x = 24.dp, y = 4.dp)
                        .fillMaxWidth(0.52f).height(114.dp)
                )
                Column(Modifier.fillMaxSize().padding(20.dp),
                    verticalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("AI FACE SCAN", fontSize = 11.sp,
                            letterSpacing = 1.sp, fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.8f))
                        Spacer(Modifier.height(8.dp))
                        Text("Find your perfect frames", fontSize = 20.sp,
                            lineHeight = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(Modifier.height(6.dp))
                        Text("Scan your face and get instant compatibility matches",
                            modifier = Modifier.fillMaxWidth(0.66f),
                            fontSize = 13.sp, lineHeight = 18.sp,
                            color = Color.White.copy(alpha = 0.88f))
                    }
                    Button(
                        onClick = { onNavigate("scan") },
                        modifier = Modifier.height(36.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(18.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                    ) {
                        Text("Start scan \u2192", fontSize = 12.sp,
                            color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
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
                    title = "Frame Catalog",
                    description = "Browse & try on",
                    icon = Icons.Outlined.AutoAwesome,
                    iconBackgroundColor = FaceFitTeal,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigate("frameMatches") }
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
                    modifier = Modifier.weight(1f).clickable { onNavigate("nearbyShops") }
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

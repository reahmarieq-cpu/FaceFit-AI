package com.example.facefit.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.facefit.data.auth.AuthManager
import com.example.facefit.ui.components.FaceFitBottomNavBar
import com.example.facefit.notifications.rememberNotificationSettingsAction
import com.example.facefit.ui.profile.openProfileSystemSettings
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
    val context = LocalContext.current
    // Red dot on the bell while there are unread notifications.
    val inboxVersion by com.example.facefit.notifications.NotificationInbox.changes.collectAsState()
    val accountId = authManager?.currentUser?.uid
    val hasUnread = remember(accountId, inboxVersion) {
        accountId != null && com.example.facefit.notifications.NotificationInbox.hasUnread(context, accountId)
    }

    LaunchedEffect(authManager) {
        authManager?.getUserProfile()?.onSuccess { userData ->
            fullName = com.example.facefit.data.auth.PersonName.fullName(
                userData["firstName"] as? String, userData["middleInitial"] as? String,
                userData["lastName"] as? String, userData["suffix"] as? String
            ).ifBlank { userName }
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
                // Everything fits on one screen: no scrolling. The card grid takes the space left.
                .padding(horizontal = 24.dp, vertical = 16.dp)
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
                        .background(Color.White)
                        .clickable(onClickLabel = "Open notifications") { onNavigate("notifications") },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.NotificationsNone,
                        contentDescription = if (hasUnread) "Notifications, new" else "Notifications",
                        tint = FaceFitTextPrimary
                    )
                    if (hasUnread) {
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(top = 11.dp, end = 12.dp)
                                .size(9.dp).clip(CircleShape).background(Color(0xFFE5484D))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Scan banner: text and CTA stay readable beside the decorative glasses.
            Box(
                modifier = Modifier.fillMaxWidth().height(176.dp)
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
                        Text("Scan your face and get instant suitability matches",
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

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Explore",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = FaceFitTextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Grid: two rows share the remaining height, so the page never needs scrolling.
            // Each card fills its row (capped on tall phones) and shows only what fits.
            Column(Modifier.fillMaxWidth().weight(1f).padding(bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                listOf(
                    listOf(Triple("Frame Catalog", "Browse & try on", "frameMatches"),
                        Triple("Favorites", "Saved frames", "favorites")),
                    listOf(Triple("Nearby Shops", "Optical stores", "nearbyShops"),
                        Triple("My Profile", "Manage account", "profile"))
                ).forEach { row ->
                    Row(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 150.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        row.forEach { (title, description, route) ->
                            FaceFitCard(
                                title = title,
                                description = description,
                                icon = when (route) {
                                    "frameMatches" -> Icons.Outlined.AutoAwesome
                                    "favorites" -> Icons.Outlined.Favorite
                                    "nearbyShops" -> Icons.Outlined.LocationOn
                                    else -> Icons.Outlined.Person
                                },
                                iconBackgroundColor = when (route) {
                                    "frameMatches" -> FaceFitTeal
                                    "favorites" -> FaceFitPink
                                    "nearbyShops" -> FaceFitBlue
                                    else -> FaceFitOrange
                                },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                onClick = { onNavigate(route) }
                            )
                        }
                    }
                }
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

package com.example.facefit.ui.notifications

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.facefit.R
import com.example.facefit.notifications.NotificationInbox
import com.example.facefit.ui.catalog.FrameIllustration
import com.example.facefit.ui.catalog.sampleFrames
import com.example.facefit.ui.theme.FaceFitBackground
import com.example.facefit.ui.theme.FaceFitTextPrimary
import com.example.facefit.ui.theme.FaceFitTextSecondary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val BadgeNavy = Color(0xFF16205A)
private val CardColor = Color(0xFFF1F2F4)

/** In-app list of notifications, newest first; tapping one opens its frame. */
@Composable
fun NotificationsScreen(
    accountId: String?,
    onBack: () -> Unit,
    onOpenFrame: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val version by NotificationInbox.changes.collectAsState()
    val entries = remember(accountId, version) {
        accountId?.let { NotificationInbox.entries(context, it) }.orEmpty()
    }
    // Show which ones are new this time, then count them as read.
    val unreadIds = remember(accountId) { entries.filter { !it.read }.map { it.id }.toSet() }
    LaunchedEffect(accountId) { accountId?.let { NotificationInbox.markAllRead(context, it) } }

    Column(Modifier.fillMaxSize().background(FaceFitBackground).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBackIosNew, contentDescription = "Back", tint = FaceFitTextPrimary)
            }
            Text("Notifications", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                color = FaceFitTextPrimary, modifier = Modifier.weight(1f))
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "Notification settings", tint = FaceFitTextSecondary)
            }
        }

        if (entries.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Outlined.NotificationsNone, contentDescription = null,
                    tint = FaceFitTextSecondary, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                Text("No notifications yet", fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    color = FaceFitTextPrimary)
                Spacer(Modifier.height(4.dp))
                Text("Scan your face and we'll let you know which frames suit you best.",
                    fontSize = 13.sp, color = FaceFitTextSecondary)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(entries, key = { it.id }) { entry ->
                    NotificationCard(entry, isNew = entry.id in unreadIds,
                        onClick = { entry.frameName?.let(onOpenFrame) })
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(entry: NotificationInbox.Entry, isNew: Boolean, onClick: () -> Unit) {
    val frame = remember(entry.frameName) { sampleFrames.firstOrNull { it.name == entry.frameName } }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(CardColor)
            .clickable(enabled = frame != null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App badge, like the system notification's app icon.
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(11.dp)).background(BadgeNavy),
            contentAlignment = Alignment.Center
        ) {
            Image(painterResource(R.drawable.facefit_logo), contentDescription = null,
                colorFilter = ColorFilter.tint(Color.White), modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isNew) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFE5484D)))
                    Spacer(Modifier.width(6.dp))
                }
                Text(entry.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111418),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text(formatTime(entry.timeMs), fontSize = 12.sp, color = Color(0xFF7A7F87), maxLines = 1)
            }
            Spacer(Modifier.height(2.dp))
            Text(entry.message, fontSize = 14.sp, lineHeight = 19.sp, color = Color(0xFF1F2328))
        }
        if (frame != null) {
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(Color.White)
                    .border(1.dp, Color(0xFFE3E5E8), RoundedCornerShape(10.dp)).padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                FrameIllustration(frame, Modifier.fillMaxWidth().height(28.dp))
            }
        }
    }
}

/** "9:12 PM" today, "Yesterday, 9:12 PM", "Sun 9:11 PM" this week, else "Nov 18, 9:11 PM". */
private fun formatTime(timeMs: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = timeMs }
    val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timeMs))
    fun dayIndex(c: Calendar) = c.get(Calendar.YEAR) * 400 + c.get(Calendar.DAY_OF_YEAR)
    val daysAgo = dayIndex(now) - dayIndex(then)
    return when {
        daysAgo <= 0 -> time
        daysAgo == 1 -> "Yesterday, $time"
        daysAgo < 7 -> SimpleDateFormat("EEE", Locale.getDefault()).format(Date(timeMs)) + " " + time
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timeMs)) + ", " + time
    }
}

package com.example.facefit.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.facefit.MainActivity
import com.example.facefit.R
import com.example.facefit.facefit.recommendation.FrameCompatibility
import com.example.facefit.ui.catalog.SampleFrame
import com.example.facefit.ui.catalog.sampleFrames
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object FrameNotifications {
    private const val CHANNEL_ID = "frame_recommendations"
    private const val TAG = "frame_match"
    private const val ACTION = "com.example.facefit.OPEN_FRAME_NOTIFICATION"
    private const val EXTRA_FRAME_NAME = "recommended_frame"

    fun createChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Frame recommendations", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    fun frameFromIntent(intent: Intent): String? {
        if (intent.action != ACTION) return null
        return intent.getStringExtra(EXTRA_FRAME_NAME)?.takeIf { name -> sampleFrames.any { it.name == name } }
    }

    fun canPost(context: Context): Boolean =
        hasNotificationPermission(context) && NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            context.getSystemService(NotificationManager::class.java)
                ?.getNotificationChannel(CHANNEL_ID)?.importance?.let { it != NotificationManager.IMPORTANCE_NONE } == true

    /** Title and message for a recommended frame, in the app's notification style. */
    fun notificationCopy(frame: SampleFrame): Pair<String, String> = when (frame.category) {
        "Rectangle" -> "Precision Perfect!" to "Clean rectangle lines for your focused lifestyle. Meet ${frame.name}."
        "Square" -> "A Statement of Quiet Confidence" to
            "${frame.name}'s square lines command attention. For the leader who values intellect."
        "Round" -> "Soft and Stylish!" to "${frame.name}'s rounded lenses soften your look for every day."
        "Aviator" -> "Classic Power!" to "Timeless aviator lines, uncompromised strength. Meet ${frame.name}."
        "Cat-Eye" -> "See the Difference!" to "${frame.name}'s lifted corners draw attention to your eyes."
        "Browline" -> "Upgrade Your Eyewear" to "${frame.name}'s bold browline brings out your best look."
        else -> "${frame.name} is a top match" to "See how this frame looks in Virtual Try-On."
    }

    suspend fun postTopMatch(context: Context, faceShape: String, accountId: String, isCurrentAccount: () -> Boolean) {
        val match = withContext(Dispatchers.IO) {
            val compatibility = FrameCompatibility.load(context)
            val frame = sampleFrames.maxByOrNull { compatibility.lookup(faceShape, it.category)?.score ?: -1.0 }
                ?: return@withContext null
            if (compatibility.lookup(faceShape, frame.category) == null) return@withContext null
            frame to frameThumbnail(context, frame)
        } ?: return
        if (!isCurrentAccount()) return
        val (frame, thumbnail) = match
        val (title, description) = notificationCopy(frame)
        // Always keep it in the in-app list, even if Android notifications are off.
        NotificationInbox.add(context, accountId, title, description, frame.name)
        if (!canPost(context)) return

        val id = sampleFrames.indexOf(frame) + 1
        val openFrame = Intent(context, MainActivity::class.java)
            .setAction(ACTION)
            .putExtra(EXTRA_FRAME_NAME, frame.name)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val tap = PendingIntent.getActivity(
            context, id, openFrame, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_frame_notification)
            .setLargeIcon(thumbnail)
            .setContentTitle(title)
            .setContentText(description)
            .setStyle(NotificationCompat.BigTextStyle().bigText(description))
            .setContentIntent(tap)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        try {
            NotificationManagerCompat.from(context).notify(TAG, id, notification)
        } catch (_: SecurityException) {
            // Permission may have been revoked since the check.
        }
    }

    fun clear(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        sampleFrames.indices.forEach { manager.cancel(TAG, it + 1) }
    }
}

private fun hasNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

@Composable
internal fun rememberNotificationSettingsAction(openSettings: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    return {
        val activity = context as? ComponentActivity
        if (activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !hasNotificationPermission(context)
        ) {
            val prefs = context.getSharedPreferences("notification_permissions", Context.MODE_PRIVATE)
            val requested = prefs.getBoolean("requested", false)
            if (!requested || ActivityCompat.shouldShowRequestPermissionRationale(
                    activity, Manifest.permission.POST_NOTIFICATIONS
                )
            ) {
                prefs.edit().putBoolean("requested", true).apply()
                permissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                openSettings()
            }
        } else {
            openSettings()
        }
    }
}

private fun frameThumbnail(context: Context, frame: SampleFrame): Bitmap {
    val size = 192
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val path = frame.thumbnailAssetPath ?: frame.assetPath?.takeUnless { it.endsWith(".glb", ignoreCase = true) }
    val image = path?.let { decodeThumbnail(context, it, size) }
    if (image != null) {
        val scale = (size - 24f) / maxOf(image.width, image.height)
        val halfWidth = image.width * scale / 2f
        val halfHeight = image.height * scale / 2f
        val center = size / 2f
        canvas.drawBitmap(image, null, RectF(center - halfWidth, center - halfHeight,
            center + halfWidth, center + halfHeight), paint)
    } else {
        paint.color = frame.color.toArgb()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5f
        paint.strokeCap = Paint.Cap.ROUND
        val radius = if (frame.category == "Round" || frame.category == "Aviator") 24f else 8f
        canvas.drawRoundRect(17f, 72f, 83f, 120f, radius, radius, paint)
        canvas.drawRoundRect(109f, 72f, 175f, 120f, radius, radius, paint)
        canvas.drawLine(83f, 93f, 109f, 93f, paint)
    }
    return bitmap
}

private fun decodeThumbnail(context: Context, path: String, size: Int): Bitmap? = try {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.assets.open(path).use { BitmapFactory.decodeStream(it, null, options) }
    var sampleSize = 1
    while (maxOf(options.outWidth, options.outHeight) / sampleSize > size * 2) sampleSize *= 2
    options.inJustDecodeBounds = false
    options.inSampleSize = sampleSize
    context.assets.open(path).use { BitmapFactory.decodeStream(it, null, options) }
} catch (_: IOException) {
    null
}

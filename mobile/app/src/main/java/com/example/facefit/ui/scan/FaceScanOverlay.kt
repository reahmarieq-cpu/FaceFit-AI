package com.example.facefit.ui.scan

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import com.example.facefit.facefit.analysis.FaceScanGuide
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FaceScanOverlay(
    statusText: String,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    captureEnabled: Boolean,
    capturing: Boolean,
    isRetake: Boolean,
    onCapture: () -> Unit,
    modifier: Modifier = Modifier,
    /** Live distance guide, shown above the status line before capture. */
    distanceHint: com.example.facefit.facefit.analysis.ScanDistanceGuide.Hint? = null,
    /** Outline half-width for 45 cm (screen px); null keeps the default size. */
    guideHalfWidth: Float? = null,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "GlowTransition")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "GlowAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0x66073D46),
                        Color(0x5505757B),
                        Color(0x66068287)
                    )
                )
            )
    ) {
        // Header, live guide and status sit above the face outline so they never cover the face.
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ScanIconButton(onClick = onBack) {
                Icon(Icons.Outlined.Close, contentDescription = "Close", tint = Color.White)
            }

            Text(
                text = "Scan Face",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            ScanIconButton(onClick = onRefresh) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Refresh", tint = Color.White)
            }
        }
            // Only shown when there is something to say (refusal, error, analysing, starting).
            if (statusText.isNotBlank()) Box(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.28f))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = statusText,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            // The outline turns green when the face is at 45 cm, level and neutral.
            drawReferenceScanFrame(glowAlpha, guideHalfWidth, ready = distanceHint?.inRange == true)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (distanceHint != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (distanceHint.inRange) Color(0xE62E7D32) else Color(0xE6B26A00))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = distanceHint.text,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xCC063B43))
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Good lighting, neutral expression, remove existing glasses for the best result.",
                    color = Color.White.copy(alpha = 0.86f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onCapture,
                enabled = captureEnabled,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF063D46)
                )
            ) {
                Text(
                    when {
                        capturing -> "Checking…"
                        isRetake -> "Retake"
                        else -> "Capture"
                    },
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ScanIconButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.14f))
    ) {
        content()
    }
}

private fun DrawScope.drawReferenceScanFrame(glowAlpha: Float, halfWidth: Float? = null, ready: Boolean = false) {
    val base = if (ready) Color(0xFF4CD964) else Color.White
    val strokeColor = base.copy(alpha = 0.85f)
    val glowColor = base.copy(alpha = (if (ready) 0.35f else 0.18f) * glowAlpha)
    val dash = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 6.dp.toPx()), 0f)
    val outline = FaceScanGuide.points(size.width, size.height, halfWidth)
    val facePath = Path().apply {
        moveTo(outline.first().x, outline.first().y)
        outline.drop(1).forEach { lineTo(it.x, it.y) }
        close()
    }
    val rect = facePath.getBounds()

    drawPath(
        path = facePath,
        color = glowColor,
        style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
    drawPath(
        path = facePath,
        color = strokeColor,
        style = Stroke(
            width = 2.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
            pathEffect = dash
        )
    )

    drawLine(
        color = Color.White.copy(alpha = 0.24f * glowAlpha),
        start = Offset(rect.left + rect.width * 0.12f, rect.top - 5.dp.toPx()),
        end = Offset(rect.right - rect.width * 0.12f, rect.top - 5.dp.toPx()),
        strokeWidth = 9.dp.toPx(),
        cap = StrokeCap.Round
    )
    drawLine(
        color = Color.White.copy(alpha = 0.52f * glowAlpha),
        start = Offset(rect.left + rect.width * 0.2f, rect.top - 4.dp.toPx()),
        end = Offset(rect.right - rect.width * 0.2f, rect.top - 4.dp.toPx()),
        strokeWidth = 2.dp.toPx(),
        cap = StrokeCap.Round
    )
}

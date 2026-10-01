package com.example.facefit.ui.components

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
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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
    isFaceDetected: Boolean,
    canCapture: Boolean,
    statusText: String,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onCapture: () -> Unit,
    modifier: Modifier = Modifier
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 18.dp),
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

        Canvas(modifier = Modifier.fillMaxSize()) {
            val frameWidth = size.width * 0.62f
            val frameHeight = frameWidth * 1.42f
            val frameLeft = (size.width - frameWidth) / 2f
            val frameTop = size.height * 0.22f
            val frameRect = Rect(frameLeft, frameTop, frameLeft + frameWidth, frameTop + frameHeight)

            drawReferenceScanFrame(frameRect, isFaceDetected, glowAlpha)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.18f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = statusText,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(72.dp))

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
                enabled = canCapture,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF128C98),
                    disabledContainerColor = Color.White.copy(alpha = 0.55f),
                    disabledContentColor = Color(0xFF128C98).copy(alpha = 0.55f)
                )
            ) {
                Icon(
                    imageVector = Icons.Outlined.CenterFocusStrong,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Capture & Analyze",
                    fontSize = 16.sp,
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

private fun DrawScope.drawReferenceScanFrame(rect: Rect, isFaceDetected: Boolean, glowAlpha: Float) {
    val strokeColor = if (isFaceDetected) Color.White else Color.White.copy(alpha = 0.62f)
    val glowColor = Color.White.copy(alpha = if (isFaceDetected) 0.28f * glowAlpha else 0.18f * glowAlpha)
    val dash = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 6.dp.toPx()), 0f)
    val cx = rect.center.x
    val cy = rect.center.y
    val rx = rect.width / 2f
    val ry = rect.height / 2f

    val facePath = Path().apply {
        moveTo(cx, cy - ry)
        cubicTo(cx + rx * 0.75f, cy - ry, cx + rx, cy - ry * 0.58f, cx + rx, cy - ry * 0.08f)
        lineTo(cx + rx, cy + ry * 0.36f)
        cubicTo(cx + rx * 0.92f, cy + ry * 0.78f, cx + rx * 0.48f, cy + ry, cx, cy + ry)
        cubicTo(cx - rx * 0.48f, cy + ry, cx - rx * 0.92f, cy + ry * 0.78f, cx - rx, cy + ry * 0.36f)
        lineTo(cx - rx, cy - ry * 0.08f)
        cubicTo(cx - rx, cy - ry * 0.58f, cx - rx * 0.75f, cy - ry, cx, cy - ry)
        close()
    }

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

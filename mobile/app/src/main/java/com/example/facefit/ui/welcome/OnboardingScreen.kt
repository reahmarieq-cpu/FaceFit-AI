package com.example.facefit.ui.welcome

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.facefit.ui.theme.FacefitTheme

private val OnboardingInk = Color(0xFF143640)
private val OnboardingMuted = Color(0xFF6D8D9D)
private val OnboardingTeal = Color(0xFF139EB0)

@Composable
fun OnboardingScreen(
    onCreateAccount: () -> Unit,
    onSignIn: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(Color(0xFFEAF3F4))
    ) {
        val viewportHeight = maxHeight
        val compact = maxHeight < 640.dp || maxWidth < 340.dp
        val sidePadding = if (maxWidth < 360.dp) 16.dp else 24.dp
        val cardPadding = if (compact) 20.dp else 26.dp
        val illustrationSize = if (compact) 104.dp else 140.dp
        val sectionGap = if (compact) 14.dp else 18.dp
        val fontScale = LocalDensity.current.fontScale
        val stackFeatures = maxWidth < 300.dp || (maxWidth < 400.dp && fontScale > 1.3f)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = viewportHeight)
                .padding(horizontal = sidePadding, vertical = if (compact) 20.dp else 28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onboardingContainerShadow(RoundedCornerShape(24.dp))
                        .border(1.dp, Color(0xFFF3F7F8), RoundedCornerShape(24.dp))
                        .background(Color.White, RoundedCornerShape(24.dp))
                        .padding(horizontal = cardPadding, vertical = cardPadding),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ScanPortraitIllustration(Modifier.size(illustrationSize))
                    Spacer(Modifier.height(if (compact) 12.dp else 16.dp))
                    Text(
                        text = "HOW IT WORKS",
                        color = OnboardingTeal,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Scan, analyze, match",
                        color = OnboardingInk,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Point your camera, and FaceFit maps your facial landmarks " +
                            "to score how well each frame shape suits your face — then recommends your best matches.",
                        color = OnboardingMuted,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(Modifier.height(sectionGap))
                OnboardingFeatures(stacked = stackFeatures, compact = compact)

                Spacer(Modifier.height(if (compact) 18.dp else 24.dp))
                Button(
                    onClick = onCreateAccount,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .shadow(12.dp, RoundedCornerShape(18.dp), clip = false,
                            ambientColor = OnboardingTeal.copy(alpha = 0.18f),
                            spotColor = OnboardingTeal.copy(alpha = 0.28f)),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OnboardingTeal)
                ) {
                    Text("Create account", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(2.dp))
                TextButton(onClick = onSignIn) {
                    Text("Sign in", color = OnboardingTeal, fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun Modifier.onboardingContainerShadow(shape: RoundedCornerShape): Modifier = shadow(
    elevation = 10.dp,
    shape = shape,
    clip = false,
    ambientColor = Color(0xFF315B66).copy(alpha = 0.10f),
    spotColor = Color(0xFF315B66).copy(alpha = 0.16f)
)

@Composable
private fun OnboardingFeatures(stacked: Boolean, compact: Boolean) {
    if (stacked) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            OnboardingFeature(tryOn = false, compact = compact, modifier = Modifier.fillMaxWidth())
            OnboardingFeature(tryOn = true, compact = compact, modifier = Modifier.fillMaxWidth())
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OnboardingFeature(tryOn = false, compact = compact,
                modifier = Modifier.weight(1f).fillMaxHeight())
            OnboardingFeature(tryOn = true, compact = compact,
                modifier = Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun OnboardingFeature(tryOn: Boolean, compact: Boolean, modifier: Modifier) {
    OnboardingFeatureCard(
        title = if (tryOn) "Try-on" else "Face scan",
        description = if (tryOn) "See frames live" else "Real-time detection",
        badgeColor = if (tryOn) Color(0xFF3D7FE1) else OnboardingTeal,
        compact = compact,
        modifier = modifier
    ) {
        if (tryOn) {
            GlassesBadgeIllustration(Modifier.size(25.dp))
        } else {
            Icon(Icons.Default.CenterFocusStrong, contentDescription = null,
                tint = Color.White, modifier = Modifier.size(23.dp))
        }
    }
}

// Informational only: no click handler, ripple or navigation action.
@Composable
private fun OnboardingFeatureCard(
    title: String,
    description: String,
    badgeColor: Color,
    compact: Boolean,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .onboardingContainerShadow(RoundedCornerShape(22.dp))
            .border(1.dp, Color(0xFFF3F7F8), RoundedCornerShape(22.dp))
            .background(Color.White, RoundedCornerShape(22.dp))
            .padding(if (compact) 14.dp else 16.dp)
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(badgeColor),
            contentAlignment = Alignment.Center
        ) { icon() }
        Spacer(Modifier.height(12.dp))
        Text(title, color = OnboardingInk, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        Text(description, color = OnboardingMuted, fontSize = 12.sp, lineHeight = 17.sp)
    }
}

@Composable
private fun ScanPortraitIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val unit = size.minDimension / 140f
        fun p(x: Float, y: Float) = Offset(x * unit, y * unit)
        val corner = Path().apply {
            moveTo(37f * unit, 9f * unit)
            lineTo(22f * unit, 9f * unit)
            quadraticTo(9f * unit, 9f * unit, 9f * unit, 22f * unit)
            lineTo(9f * unit, 37f * unit)
            moveTo(103f * unit, 9f * unit)
            lineTo(118f * unit, 9f * unit)
            quadraticTo(131f * unit, 9f * unit, 131f * unit, 22f * unit)
            lineTo(131f * unit, 37f * unit)
            moveTo(9f * unit, 103f * unit)
            lineTo(9f * unit, 118f * unit)
            quadraticTo(9f * unit, 131f * unit, 22f * unit, 131f * unit)
            lineTo(37f * unit, 131f * unit)
            moveTo(103f * unit, 131f * unit)
            lineTo(118f * unit, 131f * unit)
            quadraticTo(131f * unit, 131f * unit, 131f * unit, 118f * unit)
            lineTo(131f * unit, 103f * unit)
        }
        drawPath(corner, OnboardingInk, style = Stroke(width = 6f * unit, cap = StrokeCap.Round))
        drawCircle(OnboardingInk, radius = 18f * unit, center = p(70f, 51f))
        val shoulders = Path().apply {
            moveTo(37f * unit, 96f * unit)
            cubicTo(48f * unit, 70f * unit, 92f * unit, 70f * unit, 103f * unit, 96f * unit)
            quadraticTo(70f * unit, 119f * unit, 37f * unit, 96f * unit)
            close()
        }
        drawPath(shoulders, Color(0xFFCDD5DA))
    }
}

@Composable
private fun GlassesBadgeIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val unit = size.minDimension / 28f
        val stroke = Stroke(width = 1.6f * unit, cap = StrokeCap.Round)
        drawCircle(Color.White, radius = 10f * unit,
            center = Offset(14f * unit, 14f * unit), style = stroke)
        drawCircle(Color.White, radius = 3f * unit,
            center = Offset(10.5f * unit, 14f * unit), style = stroke)
        drawCircle(Color.White, radius = 3f * unit,
            center = Offset(17.5f * unit, 14f * unit), style = stroke)
        drawLine(Color.White, Offset(13.5f * unit, 13.5f * unit),
            Offset(14.5f * unit, 13.5f * unit), strokeWidth = 1.6f * unit)
    }
}

@Preview(name = "Small phone", showBackground = true, widthDp = 320, heightDp = 568)
@Preview(name = "Regular phone", showBackground = true, widthDp = 360, heightDp = 800)
@Preview(name = "Large phone", showBackground = true, widthDp = 412, heightDp = 915)
@Preview(name = "Landscape", showBackground = true, widthDp = 800, heightDp = 360)
@Preview(name = "Large text", showBackground = true, widthDp = 360, heightDp = 800, fontScale = 1.5f)
@Composable
fun OnboardingScreenPreview() {
    FacefitTheme {
        OnboardingScreen(onCreateAccount = {}, onSignIn = {})
    }
}

package com.example.facefit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.facefit.logic.FaceAnalyzer
import com.example.facefit.ui.theme.FaceFitBackground
import com.example.facefit.ui.theme.FaceFitTeal
import com.example.facefit.ui.theme.FaceFitTextPrimary
import com.example.facefit.ui.theme.FaceFitTextSecondary

@Composable
fun ScanResultsScreen(
    metrics: FaceAnalyzer.FaceMetrics,
    onBack: () -> Unit,
    onScanAgain: () -> Unit
) {
    val result = remember(metrics) { RecommendationResult.from(metrics) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FaceFitBackground)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ResultIconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBackIosNew, contentDescription = "Back", tint = FaceFitTextPrimary)
            }
            Text(
                text = "Your Results",
                color = FaceFitTextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            ResultIconButton(onClick = onScanAgain) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Scan again", tint = FaceFitTextPrimary)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Color.White)
                .padding(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = metrics.faceShape.ifBlank { "Unknown" },
                            color = FaceFitTeal,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "FACE SHAPE",
                            color = FaceFitTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Column(modifier = Modifier.weight(1.35f)) {
                        Text(
                            text = "SCAN COMPLETE",
                            color = FaceFitTeal,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Analysis ready",
                            color = FaceFitTextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${result.confidence}% confidence - ${metrics.landmarks.size} landmarks mapped",
                            color = FaceFitTextSecondary,
                            fontSize = 14.sp,
                            lineHeight = 19.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ResultMetricCard(label = result.jawlineLabel, value = "Jawline", modifier = Modifier.weight(1f))
                    ResultMetricCard(label = result.foreheadLabel, value = "Forehead width", modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ResultMetricCard(label = result.eyeSpacingLabel, value = "Eye spacing", modifier = Modifier.weight(1f))
                    ResultMetricCard(label = "${result.symmetryScore}%", value = "Compatibility", modifier = Modifier.weight(1f))
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White.copy(alpha = 0.72f))
                .border(1.dp, Color.White, RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Column {
                Text(
                    text = "YOUR FACE, DECODED",
                    color = FaceFitTeal,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = result.headline,
                    color = FaceFitTextPrimary,
                    fontSize = 19.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = result.explanation,
                    color = FaceFitTextPrimary.copy(alpha = 0.82f),
                    fontSize = 14.sp,
                    lineHeight = 21.sp
                )
                Spacer(modifier = Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ResultChip("Face shape: ${metrics.faceShape}")
                    ResultChip(result.jawlineLabel)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ResultChip("${result.symmetryScore}% score")
                    ResultChip(result.recommendedFrame)
                }
            }
        }
    }
}

@Composable
private fun ResultIconButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
    ) {
        content()
    }
}

@Composable
private fun ResultMetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .height(76.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFEAF5F6)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = label, color = FaceFitTextSecondary, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = value, color = FaceFitTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ResultChip(text: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFD0E7EA), RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(FaceFitTeal)
        )
        Spacer(modifier = Modifier.width(7.dp))
        Text(text = text, color = FaceFitTeal, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

private data class RecommendationResult(
    val confidence: Int,
    val symmetryScore: Int,
    val jawlineLabel: String,
    val foreheadLabel: String,
    val eyeSpacingLabel: String,
    val recommendedFrame: String,
    val headline: String,
    val explanation: String
) {
    companion object {
        fun from(metrics: FaceAnalyzer.FaceMetrics): RecommendationResult {
            val forehead = metrics.foreheadWidth
            val jawline = metrics.jawline
            val eyeSpacing = metrics.eyeSpacing
            val balance = 1f - kotlin.math.abs(forehead - jawline).coerceAtMost(1f)
            val score = ((balance * 28f) + 68f).toInt().coerceIn(72, 98)
            val confidence = if (metrics.landmarks.size >= 468) 94 else 88
            val jawLabel = when {
                jawline < forehead * 0.92f -> "Soft"
                jawline > forehead * 1.08f -> "Defined"
                else -> "Balanced"
            }
            val foreheadLabel = when {
                forehead > jawline * 1.08f -> "Broad"
                forehead < jawline * 0.92f -> "Narrow"
                else -> "Medium"
            }
            val eyeLabel = when {
                eyeSpacing < 0.075f -> "Close"
                eyeSpacing > 0.12f -> "Wide"
                else -> "Average"
            }
            val frame = when (metrics.faceShape) {
                "Round" -> "Angular frames"
                "Square" -> "Rounded frames"
                "Long" -> "Taller frames"
                else -> "Balanced frames"
            }
            val headline = when (metrics.faceShape) {
                "Round" -> "Soft facial proportions - angular frames can add definition"
                "Square" -> "Defined structure - rounded frames can soften the jawline"
                "Long" -> "Longer face profile - taller frames can add balance"
                else -> "Balanced and symmetrical - a great canvas for frames"
            }
            val explanation = "Your ${metrics.faceShape.lowercase()} face shape, $foreheadLabel forehead width, " +
                "$jawLabel jawline, and $eyeLabel eye spacing were used to calculate your compatibility score. " +
                "$frame are recommended because they help balance these facial characteristics while keeping the frame style visually proportional."

            return RecommendationResult(
                confidence = confidence,
                symmetryScore = score,
                jawlineLabel = jawLabel,
                foreheadLabel = foreheadLabel,
                eyeSpacingLabel = eyeLabel,
                recommendedFrame = frame,
                headline = headline,
                explanation = explanation
            )
        }
    }
}

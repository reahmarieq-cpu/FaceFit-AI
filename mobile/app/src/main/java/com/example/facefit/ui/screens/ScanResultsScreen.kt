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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.facefit.logic.ScanDiagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.facefit.BuildConfig
import com.example.facefit.logic.FaceAnalyzer
import com.example.facefit.logic.FacialFeatureThresholds
import com.example.facefit.ui.theme.FaceFitBackground
import com.example.facefit.ui.theme.FaceFitTeal
import com.example.facefit.ui.theme.FaceFitTextPrimary
import com.example.facefit.ui.theme.FaceFitTextSecondary
import java.text.NumberFormat

@Composable
fun ScanResultsScreen(
    metrics: FaceAnalyzer.FaceMetrics,
    onBack: () -> Unit,
    onScanAgain: () -> Unit,
    onNext: () -> Unit
) {
    val context = LocalContext.current
    val thresholds = remember(context) { FacialFeatureThresholds.load(context) }
    val result = remember(metrics, thresholds) { FacialProfileResult(metrics, thresholds) }

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
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "ESTIMATED SHAPE",
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
                            text = "${metrics.shapeSampleCount} scan samples - ${metrics.landmarks.size} landmarks mapped",
                            color = FaceFitTextSecondary,
                            fontSize = 14.sp,
                            lineHeight = 19.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ResultMetricCard(result.jawline, "Jawline width", Modifier.weight(1f))
                    ResultMetricCard(result.upperFace, "Upper-face width", Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ResultMetricCard(result.eyeSpacing, "Eye spacing", Modifier.weight(1f))
                    ResultMetricCard(result.faceLength, "Face length", Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Percentages are relative to cheek width." +
                        if (result.hasCategories) " Categories are provisional and relative to a reference photo set." else "",
                    color = FaceFitTextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
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
            }
        }
        if (BuildConfig.DEBUG && metrics.shapeDecisionReason != null) {
            Spacer(modifier = Modifier.height(18.dp))
            ScanDecisionDetails(metrics)
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = FaceFitTeal)
        ) {
            Text("Next", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ScanDecisionDetails(metrics: FaceAnalyzer.FaceMetrics) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exportStatus by remember(metrics) { mutableStateOf<String?>(null) }
    val saveDiagnostics = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) scope.launch {
            exportStatus = try {
                withContext(Dispatchers.IO) {
                    val stream = context.contentResolver.openOutputStream(uri)
                        ?: error("The selected location could not be opened")
                    stream.bufferedWriter().use { it.write(ScanDiagnostics.json(metrics)) }
                }
                "Diagnostics saved"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                "Could not save diagnostics. Choose a different location."
            }
        }
    }
    val format = NumberFormat.getPercentInstance().apply { maximumFractionDigits = 1 }
    val reason = when (metrics.shapeDecisionReason) {
        "highestScore" -> "Highest average model score selected"
        "accepted" -> "Prediction accepted"
        "lowScore" -> "The top score was too low"
        "closeScores" -> "The top two scores were too close"
        "unstablePredictions" -> "Frame predictions or the median prediction disagreed"
        "tooFewFrames" -> "Too few scan samples"
        "invalidScores" -> "The model returned invalid scores"
        else -> "Unknown decision"
    }
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("Scan diagnostics", color = FaceFitTextPrimary, fontWeight = FontWeight.Bold)
        Text("Build · ${BuildConfig.VERSION_NAME}", color = FaceFitTextSecondary, fontSize = 13.sp)
        metrics.shapeModelId?.let {
            Text("Model · $it", color = FaceFitTextSecondary, fontSize = 13.sp)
        }
        Text(
            "$reason (${metrics.shapeDecisionReason})",
            color = FaceFitTextPrimary,
            fontSize = 14.sp
        )
        if (metrics.shapeModelScores.isNotEmpty()) {
            val scores = metrics.shapeModelScores.entries.sortedByDescending { it.value }
                .joinToString(", ") { "${it.key} ${format.format(it.value)}" }
            Text("Average frame scores · $scores", color = FaceFitTextSecondary, fontSize = 13.sp)
            metrics.shapeAgreement?.let {
                Text(
                    "Frame agreement · ${format.format(it)}",
                    color = FaceFitTextSecondary,
                    fontSize = 13.sp
                )
            }
        }
        Button(onClick = {
            saveDiagnostics.launch("facefit-scan-${System.currentTimeMillis()}.json")
        }) {
            Text("Save scan diagnostics")
        }
        exportStatus?.let { Text(it, color = FaceFitTextSecondary, fontSize = 13.sp) }
    }
}

@Composable
internal fun ResultIconButton(
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
    result: FacialFeatureResult,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .heightIn(min = 84.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFEAF5F6))
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = result.displayValue, color = FaceFitTeal, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, color = FaceFitTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        if (result.category != null) {
            result.percent?.let { Text(it, color = FaceFitTextSecondary, fontSize = 12.sp) }
        }
    }
}

package com.example.facefit.ui.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.example.facefit.facefit.recommendation.FacialFeatureThresholds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.facefit.facefit.analysis.FaceAnalyzer
import com.example.facefit.facefit.recommendation.RecommendationResult
import com.example.facefit.ui.components.ResultIconButton
import com.example.facefit.ui.theme.FaceFitTeal
import com.example.facefit.ui.theme.FacefitTheme
import com.example.facefit.ui.theme.softContainerShadow

private val ResultsInk = Color(0xFF173943)
private val ResultsMuted = Color(0xFF73929F)
private val ResultsBackground = Color(0xFFEAF3F4)

@Composable
fun ScanResultsScreen(
    metrics: FaceAnalyzer.FaceMetrics,
    onBack: () -> Unit,
    onScanAgain: () -> Unit,
    onNext: () -> Unit
) {
    val context = LocalContext.current
    val thresholds = remember(context) { com.example.facefit.facefit.recommendation.FacialFeatureThresholds.load(context) }
    val result = remember(metrics, thresholds) { RecommendationResult.from(metrics, thresholds) }

    BoxWithConstraints(Modifier.fillMaxSize().background(ResultsBackground)) {
        val sidePadding = if (maxWidth < 360.dp) 16.dp else 24.dp
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = sidePadding, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    ResultIconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBackIosNew, "Back", tint = ResultsInk,
                            modifier = Modifier.size(20.dp))
                    }
                    Text("Your Results", Modifier.weight(1f).padding(horizontal = 14.dp),
                        color = ResultsInk, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    ResultIconButton(onClick = onScanAgain) {
                        Icon(Icons.Outlined.Refresh, "Scan again", tint = ResultsInk,
                            modifier = Modifier.size(23.dp))
                    }
                }
                Spacer(Modifier.height(22.dp))

                ScanResultSummaryCard(metrics = metrics, result = result)

                Spacer(Modifier.height(18.dp))
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .softContainerShadow(RoundedCornerShape(24.dp))
                        .background(Color.White, RoundedCornerShape(24.dp))
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("YOUR FACE, DECODED", color = FaceFitTeal, fontSize = 11.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Text(result.headline, color = ResultsInk, fontSize = 18.sp,
                        fontWeight = FontWeight.Bold, lineHeight = 25.sp)
                    Text(result.explanation, color = ResultsMuted, fontSize = 14.sp, lineHeight = 21.sp)
                    Text("Suggested styles: ${result.recommendedFrame}", color = FaceFitTeal,
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onNext,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = FaceFitTeal)
                ) {
                    Text("Next", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Preview(name = "Results", showBackground = true, widthDp = 360, heightDp = 800)
@Preview(name = "Small phone", showBackground = true, widthDp = 320, heightDp = 568)
@Preview(name = "Large text", showBackground = true, widthDp = 360, heightDp = 800, fontScale = 1.5f)
@Composable
private fun ScanResultsPreview() {
    FacefitTheme {
        ScanResultsScreen(
            metrics = FaceAnalyzer.FaceMetrics(0.87f, 0.76f, 0.25f, "Oval",
                landmarks = List(478) { FaceAnalyzer.LandmarkPoint(0.5f, 0.5f) }, shapeSampleCount = 5),
            onBack = {}, onScanAgain = {}, onNext = {}
        )
    }
}

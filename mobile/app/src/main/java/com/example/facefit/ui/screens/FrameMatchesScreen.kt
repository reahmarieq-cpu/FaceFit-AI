package com.example.facefit.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.facefit.logic.FaceAnalyzer
import com.example.facefit.ui.theme.*

@Composable
fun FrameMatchesScreen(
    metrics: FaceAnalyzer.FaceMetrics,
    onBack: () -> Unit,
    onScanAgain: () -> Unit,
    savedFrames: List<String>,
    onFavoriteChange: (String, Boolean) -> Unit,
    onFrameClick: (SampleFrame) -> Unit
) {
    val result = remember(metrics) { RecommendationResult.from(metrics) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(FaceFitBackground),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ResultIconButton(onBack) {
                    Icon(Icons.Outlined.ArrowBackIosNew, "Back", tint = FaceFitTextPrimary)
                }
                Text("Your Results", modifier = Modifier.weight(1f).padding(horizontal = 14.dp),
                    fontSize = 20.sp, fontWeight = FontWeight.Bold, color = FaceFitTextPrimary)
                ResultIconButton(onScanAgain) {
                    Icon(Icons.Outlined.Refresh, "Scan again", tint = FaceFitTextPrimary)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(Color.White).padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.width(90.dp).clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF2EFF9)).padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("FACE SHAPE", fontSize = 9.sp, color = FaceFitTextSecondary)
                        Icon(Icons.Outlined.Face, contentDescription = null,
                            modifier = Modifier.size(56.dp), tint = FaceFitTextPrimary)
                        Text(metrics.faceShape, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            color = FaceFitTextPrimary)
                    }
                    Column(Modifier.weight(1f).padding(start = 18.dp)) {
                        Text("SCAN COMPLETE", color = FaceFitTeal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(7.dp))
                        Text("Analysis ready", color = FaceFitTextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(7.dp))
                        Text("${metrics.landmarks.size} landmarks mapped", color = FaceFitTextSecondary,
                            fontSize = 12.sp, lineHeight = 17.sp)
                    }
                }
                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MatchMetric(result.foreheadLabel, "Forehead", Modifier.weight(1f))
                    MatchMetric(result.jawlineLabel, "Jawline", Modifier.weight(1f))
                    MatchMetric(result.eyeSpacingLabel, "Eye spacing", Modifier.weight(1f))
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Top matches", Modifier.weight(1f), color = FaceFitTextPrimary,
                    fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("${sampleFrames.size} sample frames", color = FaceFitTextSecondary, fontSize = 12.sp)
            }
            Text("Sample scores", color = FaceFitTextSecondary, fontSize = 11.sp)
        }
        itemsIndexed(sampleFrames, key = { _, frame -> frame.name }) { index, frame ->
            Column {
                if (index == 0) {
                    Text("BEST SAMPLE MATCH", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 12.dp).clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFF7A69)).padding(horizontal = 10.dp, vertical = 4.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                        .background(Color.White)
                        .then(if (index == 0) Modifier.border(1.dp, Color(0xFFFF7A69), RoundedCornerShape(20.dp)) else Modifier)
                        .clickable(onClickLabel = "View ${frame.name} details") { onFrameClick(frame) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FrameThumbnail(frame)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(frame.name, color = FaceFitTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(frame.category, color = FaceFitTextSecondary, fontSize = 12.sp)
                        Spacer(Modifier.height(9.dp))
                        Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFFE1EBEE))) {
                            Box(Modifier.fillMaxWidth(frame.score / 100f).fillMaxHeight()
                                .background(Brush.horizontalGradient(listOf(FaceFitTeal, Color(0xFF367CE5)))))
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val saved = frame.name in savedFrames
                        IconToggleButton(checked = saved, onCheckedChange = { checked ->
                            onFavoriteChange(frame.name, checked)
                        }) {
                            Icon(if (saved) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = if (saved) "Unsave ${frame.name}" else "Save ${frame.name}",
                                tint = if (saved) Color(0xFFFF7A69) else FaceFitTextSecondary,
                                modifier = Modifier.size(20.dp))
                        }
                        Text("${frame.score}", color = FaceFitTeal, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun MatchMetric(value: String, label: String, modifier: Modifier) {
    Column(modifier.height(72.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFEAF5F6))
        .padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Text(value, color = FaceFitTextSecondary, fontSize = 11.sp)
        Spacer(Modifier.height(5.dp))
        Text(label, color = FaceFitTextSecondary, fontSize = 11.sp)
    }
}

@Composable
private fun FrameThumbnail(frame: SampleFrame) {
    Box(Modifier.size(62.dp).clip(RoundedCornerShape(16.dp)).background(frame.color.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center) {
        FrameIllustration(frame, Modifier.size(52.dp, 30.dp))
    }
}

@Composable
internal fun FrameIllustration(frame: SampleFrame, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val image = remember(frame.assetPath) {
        frame.assetPath?.let { path ->
            context.assets.open(path).use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
        }
    }
    if (image != null) {
        Image(image, "${frame.name} glasses", modifier)
        return
    }
        Canvas(modifier) {
            val stroke = Stroke(2.dp.toPx())
            val lensWidth = size.width * 0.36f
            val lensHeight = size.height * 0.55f
            val top = size.height * 0.2f
            val corner = if (frame.category == "Round" || frame.category == "Aviator") lensHeight / 2 else 3.dp.toPx()
            drawRoundRect(frame.color, Offset(size.width * 0.03f, top), Size(lensWidth, lensHeight), CornerRadius(corner), style = stroke)
            drawRoundRect(frame.color, Offset(size.width * 0.61f, top), Size(lensWidth, lensHeight), CornerRadius(corner), style = stroke)
            drawLine(frame.color, Offset(size.width * 0.43f, top + lensHeight * 0.4f),
                Offset(size.width * 0.57f, top + lensHeight * 0.4f), strokeWidth = 2.dp.toPx())
        }
}

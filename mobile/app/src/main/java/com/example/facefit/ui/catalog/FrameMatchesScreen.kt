package com.example.facefit.ui.catalog

import com.example.facefit.facefit.recommendation.RecommendationResult
import com.example.facefit.ui.components.ResultIconButton
import com.example.facefit.ui.scan.ScanResultSummaryCard

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
import com.example.facefit.facefit.analysis.FaceAnalyzer
import com.example.facefit.ui.theme.*

@Composable
fun FrameMatchesScreen(
    metrics: FaceAnalyzer.FaceMetrics?,
    onBack: () -> Unit,
    onScanAgain: () -> Unit,
    onDone: () -> Unit,
    savedFrames: List<String>,
    onFavoriteChange: (String, Boolean) -> Unit,
    onFrameClick: (SampleFrame) -> Unit
) {
    val context = LocalContext.current
    val thresholds = remember(context) { com.example.facefit.facefit.recommendation.FacialFeatureThresholds.load(context) }
    val rules = remember(context) { com.example.facefit.facefit.recommendation.FacialExplanationRules.load(context) }
    // Same wording as the results screen: built from face shape, jawline, upper face and eye spacing.
    val result = remember(metrics, thresholds, rules) { metrics?.let { RecommendationResult.from(it, thresholds, rules) } }

    val compatibility = remember(context) { com.example.facefit.facefit.recommendation.FrameCompatibility.load(context) }
    val matches = remember(metrics?.faceShape, compatibility) {
        sampleFrames.map { frame -> frame.copy(compatibilityAssessment = compatibility.lookup(metrics?.faceShape, frame.category)) }
            .sortedByDescending { it.compatibilityScore ?: -1.0 }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Color(0xFFEAF3F4)),
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
        if (metrics != null && result != null) {
            item { ScanResultSummaryCard(metrics = metrics, result = result) }
            if (result.explanation.isNotBlank()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().softContainerShadow(RoundedCornerShape(20.dp))
                            .clip(RoundedCornerShape(20.dp)).background(Color.White).padding(16.dp)
                    ) {
                        ExplanationSections(result.noticed, result.styles,
                            titleColor = FaceFitTextPrimary, bodyColor = FaceFitTextSecondary)
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (matches.any { it.compatibilityScore != null }) "Top matches" else "Available frames", Modifier.weight(1f), color = FaceFitTextPrimary,
                    fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("${sampleFrames.size} sample frames", color = FaceFitTextSecondary, fontSize = 12.sp)
            }
            Text(when {
                metrics == null -> "Scan your face to see frame preferences."
                compatibility.loadError != null -> compatibility.loadError
                else -> "Frame shape preferences from survey and assessor ratings"
            }, color = FaceFitTextSecondary, fontSize = 11.sp)
        }
        itemsIndexed(matches, key = { _, frame -> frame.name }) { index, frame ->
            Column {
                if (index == 0 && frame.compatibilityScore != null) {
                    Text("TOP RATED MATCH", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 12.dp).clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFF7A69)).padding(horizontal = 10.dp, vertical = 4.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth().softContainerShadow(RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp))
                        .background(Color.White)
                        .then(if (index == 0 && frame.compatibilityScore != null) Modifier.border(1.dp, Color(0xFFFF7A69), RoundedCornerShape(20.dp)) else Modifier)
                        .clickable(onClickLabel = "View ${frame.name} details") { onFrameClick(frame) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FrameThumbnail(frame)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(frame.name, color = FaceFitTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(frame.category, color = FaceFitTextSecondary, fontSize = 12.sp)
                        frame.compatibilityScore?.let { score ->
                            Spacer(Modifier.height(9.dp))
                            Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFFE1EBEE))) {
                                Box(Modifier.fillMaxWidth((score / 100.0).toFloat()).fillMaxHeight()
                                    .background(Brush.horizontalGradient(listOf(FaceFitTeal, Color(0xFF367CE5)))))
                            }
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
                        frame.compatibilityScore?.let { score ->
                            Text("${kotlin.math.round(score).toInt()}%", color = FaceFitTeal,
                                fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        item {
            OutlinedButton(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Done", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Shows the first [collapsedLines] lines of [text], with a toggle to read all of it. */
@Composable
private fun ExpandableExplanation(text: String, key: String, collapsedLines: Int = 3) {
    var expanded by rememberSaveable(key) { mutableStateOf(false) }
    var cutOff by remember(key, text) { mutableStateOf(false) }
    Text(
        text = text,
        color = FaceFitTextSecondary,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        maxLines = if (expanded) Int.MAX_VALUE else collapsedLines,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        onTextLayout = { if (!expanded) cutOff = it.hasVisualOverflow },
    )
    if (expanded || cutOff) {
        Text(
            text = if (expanded) "Show less" else "Read full explanation",
            color = FaceFitTeal,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .padding(top = 2.dp)
                .clickable(onClickLabel = if (expanded) "Show less" else "Read full explanation") {
                    expanded = !expanded
                }
                .padding(vertical = 4.dp),
        )
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
    val thumbnail=frame.thumbnailAssetPath ?: frame.assetPath?.takeUnless { it.endsWith(".glb",ignoreCase=true) }
    val image = remember(thumbnail) {
        thumbnail?.let { path ->
            runCatching { context.assets.open(path).use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull()
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

/** "What we noticed" then "Styles to explore", as in the facial explanation rules. */
@Composable
internal fun ExplanationSections(noticed: String, styles: String?, titleColor: Color, bodyColor: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("What we noticed", color = titleColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(noticed, color = bodyColor, fontSize = 14.sp, lineHeight = 21.sp)
        if (!styles.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text("Styles to explore", color = titleColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(styles, color = bodyColor, fontSize = 14.sp, lineHeight = 21.sp)
        }
    }
}

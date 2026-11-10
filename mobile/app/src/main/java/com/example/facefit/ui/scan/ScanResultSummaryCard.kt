package com.example.facefit.ui.scan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.facefit.facefit.analysis.FaceAnalyzer
import com.example.facefit.facefit.recommendation.RecommendationResult
import com.example.facefit.ui.theme.softContainerShadow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale

private val ResultsInk = Color(0xFF173943)
private val ResultsMuted = Color(0xFF73929F)

/** One results-card implementation for scan results and the frame-matches page. */
@Composable
internal fun ScanResultSummaryCard(
    metrics: FaceAnalyzer.FaceMetrics,
    result: RecommendationResult,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth()
            .softContainerShadow(RoundedCornerShape(24.dp))
            .background(Color.White, RoundedCornerShape(24.dp))
            .padding(18.dp)
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val stackSummary = maxWidth < 250.dp || LocalDensity.current.fontScale > 1.4f
            if (stackSummary) {
                Column(Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    FaceShapePortrait(metrics.faceShape)
                    AnalysisSummary(metrics, centered = true)
                }
            } else {
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    FaceShapePortrait(metrics.faceShape)
                    AnalysisSummary(metrics, modifier = Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        ResultMetrics(result)
    }
}

@Composable
private fun AnalysisSummary(
    metrics: FaceAnalyzer.FaceMetrics,
    modifier: Modifier = Modifier,
    centered: Boolean = false
) {
    val alignment = if (centered) Alignment.CenterHorizontally else Alignment.Start
    val textAlignment = if (centered) TextAlign.Center else TextAlign.Start
    Column(modifier, horizontalAlignment = alignment) {
        Text("SCAN COMPLETE", color = Color(0xFF159CAE), fontSize = 10.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, textAlign = textAlignment)
        Spacer(Modifier.height(8.dp))
        Text("Analysis ready", color = ResultsInk, fontSize = 18.sp,
            fontWeight = FontWeight.Bold, textAlign = textAlignment)
        Spacer(Modifier.height(8.dp))
        Text("${metrics.shapeSampleCount} scan samples\n${metrics.landmarks.size} landmarks mapped",
            color = ResultsMuted, fontSize = 12.sp, lineHeight = 18.sp, textAlign = textAlignment)
    }
}

@Composable
private fun ResultMetrics(result: RecommendationResult) {
    val items = listOf(result.foreheadLabel to "Width", result.jawlineLabel to "Jawline",
        result.eyeSpacingLabel to "Eye spacing")
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 240.dp || LocalDensity.current.fontScale > 1.4f) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items.forEach { (value, label) -> ResultMetricCard(value, label, Modifier.fillMaxWidth()) }
            }
        } else {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items.forEach { (value, label) ->
                    ResultMetricCard(value, label, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun ResultMetricCard(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.heightIn(min = 78.dp)
            .background(Color(0xFFEDF7F8), RoundedCornerShape(16.dp))
            .padding(horizontal = 6.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(value, color = ResultsMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(5.dp))
        Text(label, color = ResultsMuted, fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun FaceShapePortrait(faceShape: String) {
    val context = LocalContext.current
    val key = faceShape.trim().lowercase(Locale.ROOT)
    val picture by produceState<Bitmap?>(initialValue = null, key1 = key, key2 = context) {
        value = null
        value = withContext(Dispatchers.IO) {
            try {
                val extensions = listOf("png", "webp", "jpg", "jpeg")
                val stems = when (key) {
                    "long", "oblong" -> listOf("face_shape_oblong", "face_shape_long", "oblong", "long")
                    else -> listOf("face_shape_$key", key)
                }
                val files = context.assets.list("face_shapes").orEmpty()
                val filename = stems.firstNotNullOfOrNull { stem ->
                    files.firstOrNull { name ->
                        name.substringBeforeLast('.').lowercase(Locale.ROOT) == stem &&
                            name.substringAfterLast('.').lowercase(Locale.ROOT) in extensions
                    }
                }
                filename?.let {
                    val path = "face_shapes/$it"
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.assets.open(path).use { stream -> BitmapFactory.decodeStream(stream, null, bounds) }
                    var sample = 1
                    while (bounds.outWidth / sample > 512 || bounds.outHeight / sample > 512) sample *= 2
                    val options = BitmapFactory.Options().apply { inSampleSize = sample }
                    context.assets.open(path).use { stream -> BitmapFactory.decodeStream(stream, null, options) }
                }
            } catch (_: IOException) {
                null
            }
        }
    }

    Box(
        modifier = Modifier.width(94.dp).aspectRatio(3f / 4f)
            .softContainerShadow(RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFF4F0FA)),
        contentAlignment = Alignment.Center
    ) {
        val bitmap = picture
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "$faceShape face shape illustration",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                alignment = Alignment.Center
            )
        } else {
            FaceShapeFallback(key, Modifier.fillMaxSize().padding(8.dp))
        }
    }
}

/** Simple illustration until the matching prepared picture has been added to assets. */
@Composable
private fun FaceShapeFallback(shape: String, modifier: Modifier) {
    Canvas(modifier) {
        val faceWidth = size.width * when (shape) { "round" -> 0.80f; "square" -> 0.76f; else -> 0.65f }
        val faceHeight = size.height * if (shape == "round") 0.77f else 0.92f
        val left = (size.width - faceWidth) / 2f
        val top = (size.height - faceHeight) / 2f
        val skin = Color(0xFFE8CDBA)
        if (shape == "square") {
            drawRoundRect(skin, Offset(left, top), Size(faceWidth, faceHeight),
                CornerRadius(faceWidth * 0.22f))
        } else {
            drawOval(skin, Offset(left, top), Size(faceWidth, faceHeight))
        }
        val eyeY = top + faceHeight * 0.43f
        val eyeRadius = size.width * 0.018f
        drawCircle(ResultsInk, eyeRadius, Offset(size.width * 0.38f, eyeY))
        drawCircle(ResultsInk, eyeRadius, Offset(size.width * 0.62f, eyeY))
        drawLine(Color(0xFFBE897D), Offset(size.width * 0.43f, top + faceHeight * 0.75f),
            Offset(size.width * 0.57f, top + faceHeight * 0.75f),
            strokeWidth = size.width * 0.025f, cap = StrokeCap.Round)
    }
}

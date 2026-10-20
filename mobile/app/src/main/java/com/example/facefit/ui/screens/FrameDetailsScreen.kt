package com.example.facefit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.facefit.ui.theme.*

@Composable
fun FrameDetailsScreen(
    frame: SampleFrame,
    isFavorite: Boolean,
    onBack: () -> Unit,
    onFavoriteChange: (Boolean) -> Unit,
    onStartTryOn: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().background(FaceFitBackground)
            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ResultIconButton(onBack) {
                Icon(Icons.Outlined.ArrowBackIosNew, "Back", tint = FaceFitTextPrimary)
            }
            Text("Frame Details", Modifier.weight(1f).padding(horizontal = 12.dp),
                color = FaceFitTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            IconToggleButton(isFavorite, onFavoriteChange,
                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Color.White)) {
                Icon(if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    if (isFavorite) "Unsave ${frame.name}" else "Save ${frame.name}",
                    tint = if (isFavorite) Color(0xFFFF7A69) else FaceFitTextSecondary)
            }
        }
        Box(Modifier.fillMaxWidth().height(130.dp).clip(RoundedCornerShape(20.dp))
            .background(Color(0xFFE5EBEE)).padding(24.dp), contentAlignment = Alignment.Center) {
            FrameIllustration(frame, Modifier.fillMaxWidth().height(85.dp))
        }
        Column {
            Text(frame.name, color = FaceFitTextPrimary, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text("${frame.category} · ${frame.material} · ${frame.colorName}",
                color = FaceFitTextSecondary, fontSize = 13.sp)
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White).padding(20.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("${frame.score}", color = FaceFitTeal, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(end = 22.dp))
            Column(Modifier.weight(1f)) {
                Text("COMPATIBILITY", color = FaceFitTeal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(frame.explanation, color = FaceFitTextSecondary, fontSize = 12.sp, lineHeight = 18.sp)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Sample scores", color = FaceFitTextSecondary, fontSize = 11.sp)
            DetailScore("Face shape", (frame.score + 2).coerceAtMost(100))
            DetailScore("Proportion fit", (frame.score - 1).coerceAtLeast(0))
            DetailScore("Style & color", (frame.score - 2).coerceAtLeast(0))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DetailAttribute("MATERIAL", frame.material, Modifier.weight(1f))
            DetailAttribute("COLOR", frame.colorName, Modifier.weight(1f))
        }
        Button(
            onClick = onStartTryOn,
            enabled = frame.tryOnAssetPath != null,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = FaceFitTeal)
        ) {
            Icon(Icons.Outlined.Visibility, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(if (frame.tryOnAssetPath != null) "Start Virtual Try-On" else "Try-On Not Available",
                fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DetailScore(label: String, score: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, Modifier.weight(1f), color = FaceFitTextSecondary, fontSize = 12.sp)
            Text("$score", color = FaceFitTeal, fontSize = 12.sp)
        }
        Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFFDDE7EA))) {
            Box(Modifier.fillMaxWidth(score / 100f).fillMaxHeight()
                .background(Brush.horizontalGradient(listOf(FaceFitTeal, Color(0xFF367CE5)))))
        }
    }
}

@Composable
private fun DetailAttribute(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(Color.White).padding(16.dp)) {
        Text(label, color = FaceFitTextSecondary, fontSize = 10.sp)
        Spacer(Modifier.height(5.dp))
        Text(value, color = FaceFitTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

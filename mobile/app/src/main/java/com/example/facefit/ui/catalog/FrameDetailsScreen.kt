package com.example.facefit.ui.catalog

import com.example.facefit.ui.components.ResultIconButton

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.facefit.facefit.recommendation.FrameCompatibility
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.facefit.ui.theme.*

@Composable
fun FrameDetailsScreen(
    frame: SampleFrame,
    faceShape: String?,
    isFavorite: Boolean,
    onBack: () -> Unit,
    onFavoriteChange: (Boolean) -> Unit,
    onPreviewModel: () -> Unit,
    onStartTryOn: () -> Unit
) {
    val context = LocalContext.current
    val compatibility = remember(context) { FrameCompatibility.load(context) }
    val assessment = remember(compatibility, faceShape, frame.category) {
        compatibility.lookup(faceShape, frame.category)
    }
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
                modifier = Modifier.size(46.dp).softContainerShadow(RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp)).background(Color.White)) {
                Icon(if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    if (isFavorite) "Unsave ${frame.name}" else "Save ${frame.name}",
                    tint = if (isFavorite) Color(0xFFFF7A69) else FaceFitTextSecondary)
            }
        }
        Box(Modifier.fillMaxWidth().height(130.dp).softContainerShadow(RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp))
            .background(Color(0xFFE5EBEE)).padding(24.dp), contentAlignment = Alignment.Center) {
            FrameIllustration(frame, Modifier.fillMaxWidth().height(85.dp))
        }
        Column {
            Text(frame.name, color = FaceFitTextPrimary, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text("${frame.category} · ${frame.material} · ${frame.colorName}",
                color = FaceFitTextSecondary, fontSize = 13.sp)
        }
        val cardShape = RoundedCornerShape(24.dp)
        Row(
            modifier = Modifier.fillMaxWidth()
                .softContainerShadow(cardShape)
                .background(Color.White, cardShape)
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (assessment != null) {
                Text(
                    text = kotlin.math.round(assessment.score).toInt().toString(),
                    modifier = Modifier.weight(1f),
                    color = FaceFitTeal,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
            Column(
                modifier = Modifier.weight(2f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "COMPATIBILITY",
                    color = FaceFitTeal,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = when {
                        compatibility.loadError != null -> compatibility.loadError
                        faceShape.isNullOrBlank() -> "Complete a face scan to view compatibility."
                        else -> frame.explanation
                    },
                    color = FaceFitTextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 21.sp
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DetailAttribute("MATERIAL", frame.material, Modifier.weight(1f))
            DetailAttribute("COLOR", frame.colorName, Modifier.weight(1f))
        }
        if (frame.assetPath?.endsWith(".glb", ignoreCase = true) == true) {
            OutlinedButton(
                onClick = onPreviewModel,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Preview 3D Model", fontWeight = FontWeight.Bold)
            }
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
private fun DetailAttribute(label: String, value: String, modifier: Modifier) {
    Column(modifier.softContainerShadow(RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(Color.White).padding(16.dp)) {
        Text(label, color = FaceFitTextSecondary, fontSize = 10.sp)
        Spacer(Modifier.height(5.dp))
        Text(value, color = FaceFitTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

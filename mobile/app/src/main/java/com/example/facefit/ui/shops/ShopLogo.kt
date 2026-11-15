package com.example.facefit.ui.shops

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.facefit.ui.theme.FaceFitTeal

@Composable
internal fun ShopLogo(url: String?, shopName: String, size: Dp = 48.dp) {
    var loaded by remember(url) { mutableStateOf(false) }
    var failed by remember(url) { mutableStateOf(false) }
    Box(Modifier.size(size).clip(RoundedCornerShape(14.dp)).background(Color(0xFFEFF8F9)),
        contentAlignment = Alignment.Center) {
        if (!loaded) Icon(Icons.Outlined.LocationOn, null, tint = FaceFitTeal, modifier = Modifier.size(25.dp))
        if (url != null && !failed) {
            AsyncImage(model = url, contentDescription = "$shopName logo", contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(5.dp),
                onLoading = { loaded = false }, onSuccess = { loaded = true },
                onError = { failed = true; loaded = false })
        }
    }
}


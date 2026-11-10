package com.example.facefit.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** Low elevation and muted colors give cards depth without a heavy drop shadow. */
fun Modifier.softContainerShadow(shape: Shape): Modifier = shadow(
    elevation=4.dp,
    shape=shape,
    clip=false,
    ambientColor=Color(0xFF17383E).copy(alpha=.08f),
    spotColor=Color(0xFF17383E).copy(alpha=.12f)
)

package com.facefit.kiosk.vto.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.facefit.kiosk.vto.ui.theme.softContainerShadow

@Composable
internal fun ResultIconButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(46.dp)
            .softContainerShadow(RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp))
            .background(Color.White)
    ) {
        content()
    }
}

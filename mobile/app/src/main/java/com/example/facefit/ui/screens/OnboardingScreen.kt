package com.example.facefit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.facefit.ui.components.FaceFitButton
import com.example.facefit.ui.components.FaceFitCard
import com.example.facefit.ui.theme.FaceFitTeal
import com.example.facefit.ui.theme.FaceFitTextPrimary
import com.example.facefit.ui.theme.FaceFitTextSecondary
import com.example.facefit.ui.theme.FacefitTheme

@Composable
fun OnboardingScreen(
    onCreateAccount: () -> Unit,
    onSignIn: () -> Unit,
    onFaceScan: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))
        
        // Large Illustration Placeholder
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .background(Color.White, RoundedCornerShape(24.dp))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.CenterFocusStrong,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = FaceFitTeal
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "HOW IT WORKS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = FaceFitTeal,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Scan, analyze, match",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = FaceFitTextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Point your camera, and FaceFit detects 68 facial landmarks to score how well each frame shape suits your face — then recommends your best matches.",
                    fontSize = 14.sp,
                    color = FaceFitTextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            FaceFitCard(
                title = "Face scan",
                description = "Real-time detection",
                icon = Icons.Default.CameraAlt,
                modifier = Modifier.weight(1f),
                onClick = onFaceScan
            )
            FaceFitCard(
                title = "Try-on",
                description = "See frames live",
                icon = Icons.Default.RemoveRedEye,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        FaceFitButton(
            text = "Create account",
            onClick = onCreateAccount
        )
        
        TextButton(onClick = onSignIn) {
            Text(
                text = "Sign in",
                color = FaceFitTeal,
                fontWeight = FontWeight.Medium
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Preview(showBackground = true)
@Composable
fun OnboardingScreenPreview() {
    FacefitTheme {
        OnboardingScreen(onCreateAccount = {}, onSignIn = {})
    }
}

@Composable
fun Icon(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified
) {
    androidx.compose.material3.Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint
    )
}

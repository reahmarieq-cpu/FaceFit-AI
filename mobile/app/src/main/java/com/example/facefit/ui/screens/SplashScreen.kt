package com.example.facefit.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
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
import com.example.facefit.ui.components.FaceFitLogo
import com.example.facefit.ui.theme.FaceFitTeal
import com.example.facefit.ui.theme.FaceFitTextPrimary
import com.example.facefit.ui.theme.FaceFitTextSecondary
import com.example.facefit.ui.theme.FacefitTheme

@Composable
fun SplashScreen(
    onGetStarted: () -> Unit,
    onSignIn: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        FaceFitLogo(size = 120)
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "FaceFit AI",
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            color = FaceFitTextPrimary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "AI-powered recommendations based on your unique facial features. Get personalized frame suggestions in seconds.",
            fontSize = 16.sp,
            color = FaceFitTextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(48.dp))
        FaceFitButton(
            text = "Get Started",
            onClick = onGetStarted
        )
        Spacer(modifier = Modifier.height(16.dp))
        androidx.compose.material3.TextButton(onClick = onSignIn) {
            Text(
                text = "I already have an account",
                color = FaceFitTeal,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SplashScreenPreview() {
    FacefitTheme {
        SplashScreen(onGetStarted = {}, onSignIn = {})
    }
}

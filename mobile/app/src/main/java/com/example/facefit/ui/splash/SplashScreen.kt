package com.example.facefit.ui.splash

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.facefit.R
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(
    onNavigateNext: (isLoggedIn: Boolean) -> Unit,
    start: Boolean = true,
    skipAnimation: Boolean = false,
) {
    val navigate by rememberUpdatedState(onNavigateNext)
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(.94f) }
    val pulse = remember { Animatable(.4f) }
    BackHandler { /* Startup is removed from Back history after its handoff. */ }

    LaunchedEffect(start, skipAnimation) {
        if (skipAnimation) {
            navigate(FirebaseAuth.getInstance().currentUser != null)
            return@LaunchedEffect
        }
        if (!start) return@LaunchedEffect
        coroutineScope {
            launch { alpha.animateTo(1f, tween(750, easing = LinearOutSlowInEasing)) }
            launch { scale.animateTo(1f, tween(850, easing = FastOutSlowInEasing)) }
            launch {
                // The same 600ms breathing rhythm as an infinite reverse animation,
                // bounded to the splash lifetime so no background work continues.
                repeat(3) { cycle ->
                    pulse.animateTo(if (cycle % 2 == 0) 1f else .4f,
                        tween(600, easing = EaseInOutSine))
                }
            }
            delay(1800)
        }
        navigate(FirebaseAuth.getInstance().currentUser != null)
    }

    Box(
        modifier = Modifier.fillMaxSize().clipToBounds().background(Brush.verticalGradient(listOf(
            Color(0xFF0F766E), Color(0xFF04434B), Color(0xFF022B30),
        ))),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { this.alpha = .12f }) {
            // Fixed normalized vertices preserve the optical facets across window sizes.
            val vertices = listOf(
                Offset(0f, 0f), Offset(.42f, 0f), Offset(1f, 0f),
                Offset(0f, .17f), Offset(.51f, .28f), Offset(1f, .12f),
                Offset(0f, .46f), Offset(.34f, .55f), Offset(.76f, .62f), Offset(1f, .48f),
                Offset(0f, .76f), Offset(.51f, .85f), Offset(1f, .88f),
                Offset(0f, 1f), Offset(.58f, 1f), Offset(1f, 1f),
            )
            val edges = listOf(
                0 to 4, 1 to 4, 2 to 4, 3 to 4, 4 to 5,
                3 to 7, 4 to 7, 4 to 8, 5 to 8,
                6 to 7, 7 to 8, 8 to 9, 7 to 10,
                7 to 11, 8 to 11, 9 to 11,
                10 to 11, 11 to 12, 11 to 13, 11 to 14, 12 to 14, 11 to 15,
            )
            val mesh = Path().apply {
                edges.forEach { (from, to) ->
                    val a = vertices[from]; val b = vertices[to]
                    moveTo(a.x * size.width, a.y * size.height)
                    lineTo(b.x * size.width, b.y * size.height)
                }
            }
            drawPath(mesh, Color.White, style = Stroke(1.2.dp.toPx()))
        }
        Column(
            modifier = Modifier.padding(horizontal = 32.dp).widthIn(max = 440.dp)
                .graphicsLayer {
                    this.alpha = alpha.value
                    scaleX = scale.value; scaleY = scale.value
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(painterResource(R.drawable.facefit_logo), contentDescription = "FaceFit logo",
                contentScale = ContentScale.Fit, colorFilter = ColorFilter.tint(Color.White),
                modifier = Modifier.size(96.dp))
            Spacer(Modifier.height(14.dp))
            Text("A I   E Y E W E A R", color = Color.White,
                fontFamily = FontFamily.SansSerif, fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold, letterSpacing = 4.sp,
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text("Find frames that fit you.", color = Color(0xFFE0F2FE),
                fontFamily = FontFamily.SansSerif, fontSize = 14.sp,
                fontWeight = FontWeight.Medium, letterSpacing = .5.sp,
                textAlign = TextAlign.Center)
        }
        Row(
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                .padding(bottom = 36.dp).graphicsLayer { this.alpha = pulse.value },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(3) { Box(Modifier.size(6.dp).background(Color.White, CircleShape)) }
        }
    }
}

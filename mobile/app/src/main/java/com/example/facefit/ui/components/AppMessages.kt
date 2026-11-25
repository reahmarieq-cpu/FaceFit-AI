package com.example.facefit.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

/**
 * Short in-app messages shown at the bottom of the screen, used instead of Android toasts
 * (which the system shows with the app icon). Safe to call from any thread.
 */
object AppMessages {
    data class Message(val text: String, val long: Boolean, val id: Long)

    private val nextId = AtomicLong()
    private val current = MutableStateFlow<Message?>(null)
    val message: StateFlow<Message?> get() = current

    fun show(text: String, long: Boolean = false) {
        if (text.isBlank()) return
        current.value = Message(text, long, nextId.incrementAndGet())
    }

    internal fun dismiss(id: Long) {
        current.update { if (it?.id == id) null else it }
    }
}

/** Place once, at the bottom of the app's root layout, above everything else. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppMessageHost(modifier: Modifier = Modifier) {
    val message by AppMessages.message.collectAsState()
    // The app draws edge to edge, so the keyboard covers the bottom of the screen:
    // while it is open, sit just above it instead of above the bottom navigation bar.
    val keyboardOpen = WindowInsets.isImeVisible
    // Keep the last text while the bar animates out.
    var lastText by remember { mutableStateOf("") }
    SideEffect { message?.let { lastText = it.text } }
    LaunchedEffect(message?.id) {
        val current = message ?: return@LaunchedEffect
        delay(if (current.long) 3500L else 2000L)
        AppMessages.dismiss(current.id)
    }
    AnimatedVisibility(
        visible = message != null,
        modifier = modifier,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
    ) {
        Text(
            text = message?.text ?: lastText,
            color = Color.White,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                .padding(start = 24.dp, end = 24.dp, bottom = if (keyboardOpen) 12.dp else 88.dp)
                .widthIn(max = 420.dp)
                .background(Color(0xEE1F2A30), RoundedCornerShape(14.dp))
                .padding(horizontal = 18.dp, vertical = 12.dp),
        )
    }
}

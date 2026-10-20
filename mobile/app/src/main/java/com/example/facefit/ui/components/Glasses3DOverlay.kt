package com.example.facefit.ui.components

import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@Composable
fun Glasses3DOverlay(browline: Boolean, state: Glasses3DState, onError: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val errorCallback by rememberUpdatedState(onError)
    val renderer = remember(browline) {
        Glasses3DRenderer(browline) { error ->
            ContextCompat.getMainExecutor(context).execute { errorCallback(error) }
        }
    }
    val view = remember(renderer) {
        GLSurfaceView(context).apply {
            setEGLContextClientVersion(2)
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            holder.setFormat(PixelFormat.TRANSLUCENT)
            setZOrderOnTop(true)
            preserveEGLContextOnPause = true
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        }
    }
    AndroidView(factory = { view }, modifier = Modifier.fillMaxSize(), update = {
        renderer.state = state
        it.requestRender()
    })
    DisposableEffect(view, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) view.onPause()
            if (event == Lifecycle.Event.ON_RESUME) { view.onResume(); view.requestRender() }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            view.onPause()
        }
    }
}

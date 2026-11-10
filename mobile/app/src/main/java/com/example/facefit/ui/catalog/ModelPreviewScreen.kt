package com.example.facefit.ui.catalog

import android.content.Context
import android.view.Choreographer
import android.view.TextureView
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.filament.IndirectLight
import com.google.android.filament.Skybox
import com.google.android.filament.utils.ModelViewer
import com.google.android.filament.utils.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

@Composable
fun ModelPreviewScreen(frame: SampleFrame, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var bytes by remember(frame.assetPath) { mutableStateOf<ByteArray?>(null) }
    var error by remember(frame.assetPath) { mutableStateOf<String?>(null) }
    var ready by remember(frame.assetPath) { mutableStateOf(false) }
    var reset by remember { mutableIntStateOf(0) }
    LaunchedEffect(frame.assetPath) {
        try {
            bytes = withContext(Dispatchers.IO) {
                val path = requireNotNull(frame.assetPath)
                require(path.endsWith(".glb", ignoreCase = true))
                context.assets.open(path).use { it.readBytes() }
            }
        } catch (failure: Exception) {
            error = "This model could not be opened."
            android.util.Log.e("ModelPreview", "Cannot read ${frame.assetPath}", failure)
        }
    }
    Column(Modifier.fillMaxSize().background(Color(0xFFF1F3F5))) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back") }
            Text("3D Model Preview", style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f))
            TextButton(onClick = { ready = false; error = null; reset++ }, enabled = bytes != null) {
                Text("Reset")
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            bytes?.let { modelBytes ->
                key(frame.assetPath, reset) {
                    AndroidView(
                        factory = { ctx ->
                            FilamentModelPreview(ctx, lifecycleOwner, modelBytes,
                                onReady = { ready = true },
                                onError = { error = "The 3D preview could not start on this device." })
                        },
                        modifier = Modifier.fillMaxSize().testTag("modelPreview")
                    )
                }
            }
            if (error != null) {
                Text(error!!, modifier = Modifier.background(Color.White).padding(20.dp))
            } else if (!ready) {
                CircularProgressIndicator()
            }
        }
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text(frame.name, style = MaterialTheme.typography.titleLarge)
            Text(if (ready) "Drag to rotate · Pinch to zoom" else "Loading model…",
                style = MaterialTheme.typography.bodyMedium)
            Text("Inspect the texture and both arms from every angle.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * Owns a fresh TextureView for each attachment. ModelViewer frees its engine when that child
 * detaches; our lighting and frame callback are released before removing the child.
 */
internal class FilamentModelPreview(
    context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val bytes: ByteArray,
    private val onReady: () -> Unit,
    private val onError: () -> Unit
) : FrameLayout(context), Choreographer.FrameCallback {
    private var viewer: ModelViewer? = null
    private var ambient: IndirectLight? = null
    private var sky: Skybox? = null
    private var running = false
    private var announcedReady = false
    private val choreographer = Choreographer.getInstance()
    private val observer = LifecycleEventObserver { _, _ -> updateRunning() }

    override fun onAttachedToWindow() {
        // Old children have already detached and their ModelViewer engines are gone.
        removeAllViews()
        super.onAttachedToWindow()
        try {
            Utils.init()
            val texture = TextureView(context)
            // Runs before ModelViewer's detach listener destroys its engine.
            texture.addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: android.view.View) {}
                override fun onViewDetachedFromWindow(view: android.view.View) {
                    running = false
                    choreographer.removeFrameCallback(this@FilamentModelPreview)
                    viewer?.let { current ->
                        current.scene.indirectLight = null
                        current.scene.skybox = null
                        ambient?.let { current.engine.destroyIndirectLight(it) }
                        sky?.let { current.engine.destroySkybox(it) }
                    }
                    ambient = null
                    sky = null
                }
            })
            addView(texture, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            val modelViewer = ModelViewer(texture)
            viewer = modelViewer
            texture.contentDescription = "Interactive 3D glasses"
            texture.setOnTouchListener { view, event ->
                view.parent?.requestDisallowInterceptTouchEvent(true)
                modelViewer.onTouchEvent(event)
                true
            }
            // Neutral studio-style light keeps the actual base-color texture visible.
            ambient = IndirectLight.Builder()
                .irradiance(1, floatArrayOf(0.8f, 0.8f, 0.8f))
                .intensity(30_000f).build(modelViewer.engine)
            sky = Skybox.Builder().color(0.88f, 0.91f, 0.94f, 1f).build(modelViewer.engine)
            modelViewer.scene.indirectLight = ambient
            modelViewer.scene.skybox = sky
            val lightManager = modelViewer.engine.lightManager
            lightManager.setDirection(lightManager.getInstance(modelViewer.light), -0.5f, -0.8f, -1f)
            val buffer = ByteBuffer.allocateDirect(bytes.size).apply { put(bytes); flip() }
            modelViewer.loadModelGlb(buffer)
            checkNotNull(modelViewer.asset) { "Filament rejected the GLB" }
            // Scale the entire asset root; preserve mesh nodes, UVs, materials and hinge transforms.
            modelViewer.transformToUnitCube()
            lifecycleOwner.lifecycle.addObserver(observer)
            updateRunning()
        } catch (failure: Exception) {
            android.util.Log.e("ModelPreview", "Cannot initialize preview", failure)
            releasePreview(removeChildren = true)
            post { onError() }
        }
    }

    private fun updateRunning() {
        val shouldRun = isAttachedToWindow && viewer != null &&
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        if (shouldRun == running) return
        running = shouldRun
        if (running) choreographer.postFrameCallback(this)
        else choreographer.removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        try {
            val current = viewer ?: return
            current.render(frameTimeNanos)
            if (!announcedReady && current.progress >= 1f &&
                current.scene.renderableCount > 0) {
                announcedReady = true
                onReady()
            }
            choreographer.postFrameCallback(this)
        } catch (failure: Exception) {
            running = false
            android.util.Log.e("ModelPreview", "Cannot render preview", failure)
            onError()
        }
    }

    private fun releasePreview(removeChildren: Boolean = false) {
        running = false
        choreographer.removeFrameCallback(this)
        lifecycleOwner.lifecycle.removeObserver(observer)
        // Normal parent detach already freed the child engine; do not detach it twice.
        if (removeChildren) removeAllViews()
        viewer = null
        announcedReady = false
    }

    override fun onDetachedFromWindow() {
        releasePreview()
        super.onDetachedFromWindow()
    }
}

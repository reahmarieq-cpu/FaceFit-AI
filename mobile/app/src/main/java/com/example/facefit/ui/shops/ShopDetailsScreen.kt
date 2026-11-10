package com.example.facefit.ui.shops

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.facefit.data.shops.NearbyShopsRepository
import com.example.facefit.data.shops.OpticalShop
import com.example.facefit.data.shops.ShopFrame
import com.example.facefit.ui.components.ResultIconButton
import com.example.facefit.ui.theme.*
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import java.util.Locale

@Composable
fun ShopDetailsScreen(shopId: String, onBack: () -> Unit) {
    val repository = remember { NearbyShopsRepository() }
    val context = LocalContext.current
    var shop by remember(shopId) { mutableStateOf<OpticalShop?>(null) }
    var shopLoading by remember(shopId) { mutableStateOf(true) }
    var shopError by remember(shopId) { mutableStateOf<String?>(null) }
    var frames by remember(shopId) { mutableStateOf<List<ShopFrame>>(emptyList()) }
    var framesLoading by remember(shopId) { mutableStateOf(false) }
    var framesError by remember(shopId) { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var frameRetry by remember { mutableIntStateOf(0) }
    var selectedFrame by remember(shopId) { mutableStateOf<ShopFrame?>(null) }
    var actionMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(shopId, retry) {
        shopLoading = true; shopError = null; shop = null
        try {
            repository.observeShop(shopId).collect { value ->
                shop = value; shopLoading = false
                if (value == null) selectedFrame = null
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { shop = null; shopError = "This shop is unavailable or no longer published." }
        finally { shopLoading = false }
    }
    LaunchedEffect(shop?.id, frameRetry) {
        val current = shop
        frames = emptyList(); framesError = null; selectedFrame = null
        if (current != null) {
            framesLoading = true
            try {
                repository.observeFrames(current.id).collect { value ->
                    frames = value; framesLoading = false
                    selectedFrame = selectedFrame?.let { selected -> value.find { it.id == selected.id } }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                frames = emptyList(); selectedFrame = null
                framesError = if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED)
                    "This shop's frames are not available to your account yet. Please contact FaceFit support."
                else "Could not load this shop's frames. Check your connection and retry."
            } finally { framesLoading = false }
        } else framesLoading = false
    }

    fun open(uri: Uri, action: String = Intent.ACTION_VIEW) {
        runCatching { context.startActivity(Intent(action, uri)) }
            .onFailure { actionMessage = "No app is available to open this link." }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Color(0xFFEAF3F4)),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ResultIconButton(onBack) { Icon(Icons.Outlined.ChevronLeft, "Back", tint = FaceFitTextPrimary) }
                Text("Shop Details", Modifier.weight(1f).padding(horizontal = 12.dp),
                    fontSize = 21.sp, fontWeight = FontWeight.Bold, color = FaceFitTextPrimary)
                IconButton(onClick = { retry++ }) { Icon(Icons.Outlined.Refresh, "Refresh shop", tint = FaceFitTeal) }
            }
        }
        when {
            shopLoading -> item { ShopLoading() }
            shopError != null -> item { ShopMessage(shopError!!, onRetry = { retry++ }) }
            shop == null -> item { ShopMessage("This shop is no longer available.", onRetry = { retry++ }) }
            else -> {
                val current = shop!!
                item {
                    Column(shopContainer().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        current.imageUrl?.let {
                            ShopNetworkImage(it, current.name, Modifier.fillMaxWidth().height(160.dp)
                                .clip(RoundedCornerShape(16.dp)), ContentScale.Crop)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(50.dp).background(FaceFitTealVariant, RoundedCornerShape(15.dp)),
                                contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Storefront, null, tint = FaceFitTeal) }
                            Text(current.name, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                                color = FaceFitTextPrimary, modifier = Modifier.weight(1f))
                        }
                        Text(current.address.ifBlank { "Address not provided" }, color = FaceFitTextSecondary,
                            fontSize = 14.sp, lineHeight = 21.sp)
                        current.rating?.let {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Star, null, tint = Color(0xFFF5B64B), modifier = Modifier.size(18.dp))
                                Text(" ${String.format(Locale.getDefault(), "%.1f", it)}", color = FaceFitTextSecondary)
                            }
                        }
                        Button(onClick = { if (!openShopDirections(context, current)) actionMessage = "No maps app is available." },
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = FaceFitTeal)) {
                            Icon(Icons.Outlined.Directions, null, Modifier.size(19.dp))
                            Spacer(Modifier.width(8.dp)); Text("Get directions")
                        }
                    }
                }
                item {
                    Column(shopContainer().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("About the shop", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = FaceFitTextPrimary)
                        Text(current.description.ifBlank { "This shop has not added an introduction yet." },
                            fontSize = 14.sp, lineHeight = 21.sp, color = FaceFitTextSecondary)
                        if (current.openingHours.isNotBlank()) Text("Opening hours\n${current.openingHours}", color = FaceFitTextSecondary, fontSize = 14.sp)
                        if (current.phone.isNotBlank()) TextButton(onClick = { open(Uri.fromParts("tel", current.phone, null), Intent.ACTION_DIAL) }) {
                            Icon(Icons.Outlined.Phone, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(current.phone)
                        }
                        current.website?.let { website -> TextButton(onClick = { open(Uri.parse(website)) }) {
                            Icon(Icons.Outlined.Language, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Visit website")
                        } }
                    }
                }
                if (actionMessage != null) item { Text(actionMessage!!, color = FaceFitTextSecondary, fontSize = 13.sp) }
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Frames at this shop", Modifier.weight(1f), fontSize = 19.sp,
                            fontWeight = FontWeight.Bold, color = FaceFitTextPrimary)
                        if (!framesLoading && framesError == null) Text("${frames.size}", color = FaceFitTeal, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { frameRetry++ }) { Icon(Icons.Outlined.Refresh, "Refresh frames", tint = FaceFitTeal) }
                    }
                }
                when {
                    framesLoading -> item { ShopLoading() }
                    framesError != null -> item { ShopMessage(framesError!!, onRetry = { frameRetry++ }) }
                    frames.isEmpty() -> item { ShopMessage("This shop has not published any approved frames yet.") }
                    else -> items(frames, key = { it.id }) { frame ->
                        ShopFrameCard(frame, onClick = { selectedFrame = frame })
                    }
                }
            }
        }
    }
    selectedFrame?.let { frame ->
        AlertDialog(
            onDismissRequest = { selectedFrame = null },
            title = { Text(frame.name) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ShopNetworkImage(frame.imageUrl, frame.name, Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(14.dp)))
                    FrameAttributes(frame)
                    if (frame.description.isNotBlank()) Text(frame.description, lineHeight = 21.sp)
                }
            },
            confirmButton = { TextButton(onClick = { selectedFrame = null }) { Text("Close") } }
        )
    }
}

@Composable
private fun ShopFrameCard(frame: ShopFrame, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(22.dp), color = Color.White,
        modifier = Modifier.fillMaxWidth().softContainerShadow(RoundedCornerShape(22.dp))) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            ShopNetworkImage(frame.imageUrl, frame.name, Modifier.size(100.dp).clip(RoundedCornerShape(15.dp)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(frame.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = FaceFitTextPrimary)
                val subtitle = listOf(frame.shape, frame.type, frame.color).filter { it.isNotBlank() }.distinct().joinToString(" · ")
                if (subtitle.isNotBlank()) Text(subtitle, color = FaceFitTextSecondary, fontSize = 12.sp)
                Text("View frame", color = FaceFitTeal, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = FaceFitTeal)
        }
    }
}

@Composable
private fun FrameAttributes(frame: ShopFrame) {
    listOf("Brand" to frame.brand, "Model" to frame.model, "Type" to frame.type, "Shape" to frame.shape,
        "Material" to frame.material, "Color" to frame.color).filter { it.second.isNotBlank() }.forEach { (label, value) ->
        Text("$label: $value", fontSize = 14.sp, color = FaceFitTextSecondary)
    }
}

@Composable
private fun ShopNetworkImage(url: String?, description: String, modifier: Modifier, scale: ContentScale = ContentScale.Fit) {
    var loading by remember(url) { mutableStateOf(url != null) }
    var failed by remember(url) { mutableStateOf(false) }
    Box(modifier.background(Color(0xFFF1F6F7)), contentAlignment = Alignment.Center) {
        if (url == null || failed) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.Visibility, null, tint = FaceFitTeal, modifier = Modifier.size(30.dp))
                Text("No image", color = FaceFitTextSecondary, fontSize = 11.sp)
            }
        } else {
            AsyncImage(model = url, contentDescription = description, modifier = Modifier.fillMaxSize(), contentScale = scale,
                onSuccess = { loading = false }, onError = { loading = false; failed = true })
            if (loading) CircularProgressIndicator(Modifier.size(24.dp), color = FaceFitTeal, strokeWidth = 2.dp)
        }
    }
}

private fun shopContainer(): Modifier = Modifier.fillMaxWidth().softContainerShadow(RoundedCornerShape(22.dp))
    .background(Color.White, RoundedCornerShape(22.dp))

@Composable
private fun ShopLoading() { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = FaceFitTeal) } }

@Composable
private fun ShopMessage(message: String, onRetry: (() -> Unit)? = null) {
    Column(shopContainer().padding(20.dp)) {
        Text(message, color = FaceFitTextSecondary, fontSize = 14.sp, lineHeight = 21.sp)
        onRetry?.let { TextButton(onClick = it) { Text("Retry") } }
    }
}

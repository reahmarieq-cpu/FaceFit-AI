package com.example.facefit.ui.shops

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.facefit.data.shops.NearbyShopsRepository
import com.example.facefit.data.shops.OpticalShop
import com.example.facefit.data.shops.ShopFrame
import com.example.facefit.ui.components.ResultIconButton
import com.example.facefit.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Currency

@Composable
fun ShopProductDetailsScreen(shopId: String, frameKey: String, onBack: () -> Unit, onViewShop: () -> Unit) {
    val repository = remember { NearbyShopsRepository() }
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var shop by remember(shopId, frameKey) { mutableStateOf<OpticalShop?>(null) }
    var frame by remember(shopId, frameKey) { mutableStateOf<ShopFrame?>(null) }
    var loading by remember(shopId, frameKey) { mutableStateOf(true) }
    var error by remember(shopId, frameKey) { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }

    LaunchedEffect(shopId, frameKey, retry) {
        shop = null; frame = null; loading = true; error = null
        try {
            combine(repository.observeShop(shopId), repository.observeFrame(shopId, frameKey)) { s, f -> s to f }
                .collect { (currentShop, currentFrame) ->
                    shop = currentShop; frame = currentFrame; loading = false
                }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) {
            shop = null; frame = null
            error = "This product could not be loaded. It may no longer be published, or your connection may be unavailable."
        } finally { loading = false }
    }

    Scaffold(
        containerColor = Color(0xFFEAF3F4),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                ResultIconButton(onBack) { Icon(Icons.Outlined.ChevronLeft, "Back to shop", tint = Color(0xFF17383E)) }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text("Frame details", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF17383E))
                    Text("Find your everyday fit", fontSize = 12.sp, color = FaceFitTextSecondary)
                }
                IconButton(enabled = shop != null && frame != null, onClick = {
                    val product = frame ?: return@IconButton
                    val seller = shop ?: return@IconButton
                    val text = listOf(product.name, "Available at ${seller.name}", seller.address)
                        .filter { it.isNotBlank() }.joinToString("\n")
                    runCatching {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text)
                        }, "Share frame"))
                    }.onFailure { scope.launch { snackbar.showSnackbar("No app is available to share this frame.") } }
                }) { Icon(Icons.Outlined.Share, "Share frame", tint = FaceFitTeal) }
            }
        },
        bottomBar = {
            val current = shop
            if (!loading && frame != null && current != null && error == null) {
                Surface(color = Color.White, shadowElevation = 6.dp) {
                    Button(
                        onClick = {
                            if (!openShopDirections(context, current)) scope.launch { snackbar.showSnackbar("No maps app is available.") }
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp).heightIn(min = 52.dp),
                        shape = RoundedCornerShape(17.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = FaceFitTeal)
                    ) {
                        Icon(Icons.Outlined.Directions, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp)); Text("Get directions to shop", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            when {
                loading -> item { Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = FaceFitTeal)
                } }
                error != null || shop == null || frame == null -> item {
                    Column(productCard().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Outlined.Info, null, tint = FaceFitTeal)
                        Text(error ?: "This frame is no longer available from this shop.", color = FaceFitTextSecondary, lineHeight = 22.sp)
                        TextButton(onClick = { retry++ }) { Text("Try again") }
                    }
                }
                else -> {
                    val product = frame!!
                    val seller = shop!!
                    item {
                        Column(productCard().clip(RoundedCornerShape(26.dp))) {
                            Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFFDFF2F2), Color(0xFFF9FCFC))))) {
                                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                                    Text("THE FRAME EDIT", color = FaceFitTeal, fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                                    ShopNetworkImage(product.imageUrl, product.name,
                                        Modifier.fillMaxWidth().aspectRatio(1.25f).padding(vertical = 10.dp)
                                            .clip(RoundedCornerShape(20.dp)))
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Outlined.Photo, null, tint = FaceFitTeal, modifier = Modifier.size(15.dp))
                                        Text("Product photo", fontSize = 11.sp, color = FaceFitTextSecondary)
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Column(productWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(product.name, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, color = Color(0xFF17383E))
                            val subtitle = listOf(product.shape, product.color, product.material).filter { it.isNotBlank() }.distinct()
                            if (subtitle.isNotEmpty()) Text(subtitle.joinToString("  ·  "), color = FaceFitTeal,
                                fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium)
                            if (product.price != null) {
                                Surface(color = Color.White, shape = RoundedCornerShape(16.dp)) {
                                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                        Text("LISTED PRICE", fontSize = 10.sp, letterSpacing = 1.sp, color = FaceFitTextSecondary)
                                        Text(formatProductPrice(product), fontWeight = FontWeight.Bold, color = Color(0xFF17383E), fontSize = 23.sp)
                                        if (product.currency == null) Text("Confirm currency with the shop", color = FaceFitTextSecondary, fontSize = 11.sp)
                                    }
                                }
                            } else Text("Contact the shop for pricing", fontSize = 13.sp, color = FaceFitTextSecondary)
                        }
                    }
                    item {
                        Column(productCard().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("About this frame", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF17383E))
                            Text(product.description.ifBlank { "Explore the frame's available specifications below, or visit the shop to see it in person." },
                                fontSize = 14.sp, lineHeight = 22.sp, color = FaceFitTextSecondary)
                            ProductSpecs(product)
                        }
                    }
                    item {
                        Column(productCard().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Outlined.Straighten, null, tint = FaceFitTeal, modifier = Modifier.size(21.dp))
                                Text("Size & measurements", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF17383E))
                            }
                            if (product.sizeLabel.isNotBlank()) ProductSpecRow("Frame size", product.sizeLabel)
                            if (product.measurementsMm.isEmpty()) {
                                Text("The shop has not added frame measurements yet.", fontSize = 14.sp,
                                    lineHeight = 21.sp, color = FaceFitTextSecondary)
                            } else {
                                product.measurementsMm.forEach { (label, value) ->
                                    ProductSpecRow(label, "${NumberFormat.getNumberInstance().format(value)} mm")
                                }
                                Text("Measurements provided by the shop.", fontSize = 11.sp, color = FaceFitTextSecondary)
                            }
                        }
                    }
                    item {
                        Surface(onClick = onViewShop, color = Color.White, shape = RoundedCornerShape(24.dp),
                            modifier = productWidth().softContainerShadow(RoundedCornerShape(24.dp))) {
                            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                ShopLogo(seller.logoUrl, seller.name)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("LISTED BY", fontSize = 10.sp, letterSpacing = 1.sp, color = FaceFitTeal, fontWeight = FontWeight.Bold)
                                    Text(seller.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF17383E))
                                    if (seller.address.isNotBlank()) Text(seller.address, fontSize = 12.sp, lineHeight = 18.sp, color = FaceFitTextSecondary)
                                }
                                Icon(Icons.Outlined.ChevronRight, "View shop", tint = FaceFitTeal)
                            }
                        }
                    }
                    item { Spacer(Modifier.height(6.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ProductSpecs(frame: ShopFrame) {
    val rows = listOf("Brand" to frame.brand, "Model" to frame.model, "Type" to frame.type,
        "Shape" to frame.shape, "Material" to frame.material, "Color" to frame.color).filter { it.second.isNotBlank() }
    if (rows.isEmpty()) Text("More specifications are available from the shop.", color = FaceFitTextSecondary, fontSize = 13.sp)
    rows.forEach { (label, value) -> ProductSpecRow(label, value) }
}

@Composable
private fun ProductSpecRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().background(Color(0xFFF1F7F8), RoundedCornerShape(12.dp)).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 13.sp, color = FaceFitTextSecondary)
        Text(value, Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF17383E))
    }
}

private fun productWidth(): Modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()
private fun productCard(): Modifier = productWidth().softContainerShadow(RoundedCornerShape(26.dp))
    .background(Color.White, RoundedCornerShape(26.dp))

private fun formatProductPrice(frame: ShopFrame): String {
    val amount = frame.price ?: return "Ask the shop"
    val formatter = if (frame.currency != null) NumberFormat.getCurrencyInstance().apply {
        currency = Currency.getInstance(frame.currency)
    } else NumberFormat.getNumberInstance()
    return formatter.format(amount)
}

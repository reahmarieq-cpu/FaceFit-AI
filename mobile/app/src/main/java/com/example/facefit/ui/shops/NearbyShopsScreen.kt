package com.example.facefit.ui.shops

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.facefit.data.shops.NearbyShopsRepository
import com.example.facefit.data.shops.OpticalShop
import com.example.facefit.data.shops.nearbyShopLocation
import com.example.facefit.ui.components.FaceFitBottomNavBar
import com.example.facefit.ui.theme.*
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

@Composable
fun NearbyShopsScreen(onBack: () -> Unit, onNavigate: (String) -> Unit, onShopClick: (String) -> Unit) {
    val context = LocalContext.current
    val repository = remember { NearbyShopsRepository() }
    var shops by remember { mutableStateOf<List<OpticalShop>>(emptyList()) }
    var location by remember { mutableStateOf<Location?>(null) }
    var loading by remember { mutableStateOf(true) }
    var locating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var locationMessage by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var locationAttempt by remember { mutableIntStateOf(0) }
    var nearbyOnly by rememberSaveable { mutableStateOf(true) }
    fun granted() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    var permission by remember { mutableStateOf(granted()) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permission = granted()
        if (permission) { locationMessage = null; locationAttempt++ }
        else locationMessage = "Location permission was not granted. You can still browse registered shops."
    }
    LaunchedEffect(reload) {
        loading = true; error = null; shops = emptyList()
        try {
            repository.observeShops().collect { rows -> shops = rows; loading = false }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            shops = emptyList()
            error = if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED)
                "The shop directory is not available to this account yet. Please contact FaceFit support."
            else "Could not load registered shops. Check your connection and retry."
        } finally { loading = false }
    }
    LaunchedEffect(permission, locationAttempt) {
        if (permission) {
            locating = true
            try {
                location = nearbyShopLocation(context)
                locationMessage = if (location == null) "Could not get your location. Turn on Location in phone settings and retry." else null
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { location = null; locationMessage = "Location is unavailable. You can still browse registered shops." }
            finally { locating = false }
        }
    }
    val center = location
    val listed = remember(shops, center, nearbyOnly) {
        if (center == null) shops.sortedBy { it.name.lowercase(Locale.ROOT) }
        else shops.filter { !nearbyOnly || (it.distanceKm(center.latitude, center.longitude) ?: Double.POSITIVE_INFINITY) <= 3.0 }
            .sortedWith(compareBy<OpticalShop> { it.distanceKm(center.latitude, center.longitude) ?: Double.POSITIVE_INFINITY }.thenBy { it.name })
    }
    val unmapped = if (center != null && nearbyOnly) shops.filter { !it.hasCoordinates } else emptyList()
    fun directions(shop: OpticalShop) {
        if (!openShopDirections(context, shop)) locationMessage = "No maps or browser app is available to open directions."
    }
    Scaffold(containerColor = Color(0xFFEAF3F4), bottomBar = { FaceFitBottomNavBar("nearbyShops", onNavigate) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledIconButton(onClick = onBack, shape = RoundedCornerShape(14.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = FaceFitTextPrimary)) {
                        Icon(Icons.Outlined.ChevronLeft, "Back")
                    }
                    Text("Nearby Shops", Modifier.weight(1f).padding(start = 12.dp), fontSize = 22.sp,
                        fontWeight = FontWeight.Bold, color = Color(0xFF17383E))
                    IconButton(onClick = { reload++; if (permission) locationAttempt++ }) { Icon(Icons.Outlined.Refresh, "Refresh shops", tint = FaceFitTeal) }
                }
            }
            item {
                ShopMap(center, listed.filter { it.hasCoordinates }, Modifier.fillMaxWidth().height(150.dp)
                    .softContainerShadow(RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp)))
            }
            if (!permission || center == null || locationMessage != null) item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(locationMessage ?: "Enable location to find registered optical shops within 3 km.", fontSize = 13.sp, color = FaceFitTextSecondary)
                    OutlinedButton(enabled = !locating, onClick = {
                        if (permission) locationAttempt++ else request.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    }) { Text(if (locating) "Finding your location..." else if (permission) "Retry location" else "Use my location") }
                }
            }
            if (center != null) item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(selected = nearbyOnly, onClick = { nearbyOnly = true }, label = { Text("Within 3 km") })
                    FilterChip(selected = !nearbyOnly, onClick = { nearbyOnly = false }, label = { Text("All shops") })
                }
                Text("Distances are approximate and measured in a straight line.", fontSize = 11.sp, color = FaceFitTextSecondary)
            }
            if (!loading && error == null) item {
                Text(if (center != null && nearbyOnly) "${listed.size} optical ${if (listed.size == 1) "shop" else "shops"} within 3 km"
                    else "${listed.size} registered optical ${if (listed.size == 1) "shop" else "shops"}",
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF17383E))
            }
            when {
                loading -> item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = FaceFitTeal) } }
                error != null -> item { Column { Text(error!!, color = FaceFitTextSecondary); TextButton(onClick = { reload++ }) { Text("Retry") } } }
                listed.isEmpty() -> item { Text(if (center != null && nearbyOnly) "No registered shops with a map location were found within 3 km. Try All shops."
                    else "No approved shops have been published in FaceFit yet.", color = FaceFitTextSecondary, fontSize = 14.sp) }
            }
            if (!loading && error == null) {
                items(listed, key = { it.id }) { shop -> ShopRow(shop, center, onOpenShop = { onShopClick(shop.id) }, onDirections = { directions(shop) }) }
                if (unmapped.isNotEmpty()) {
                    item { Text("Registered shops awaiting map locations", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = FaceFitTextSecondary) }
                    items(unmapped, key = { "unmapped-${it.id}" }) { shop -> ShopRow(shop, null, onOpenShop = { onShopClick(shop.id) }, onDirections = { directions(shop) }) }
                }
            }
        }
    }
}

@Composable
private fun ShopRow(shop: OpticalShop, location: Location?, onOpenShop: () -> Unit, onDirections: () -> Unit) {
    Surface(onClick = onOpenShop, shape = RoundedCornerShape(22.dp), color = Color.White,
        modifier = Modifier.fillMaxWidth().softContainerShadow(RoundedCornerShape(22.dp))) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(48.dp).background(FaceFitTealVariant, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Storefront, null, tint = FaceFitTeal)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(shop.name, fontWeight = FontWeight.Bold, color = Color(0xFF17383E), fontSize = 16.sp)
                val distance = location?.let { shop.distanceKm(it.latitude, it.longitude) }
                Text(buildString {
                    shop.rating?.let { append("★ ${String.format(Locale.getDefault(), "%.1f", it)} · ") }
                    append(if (distance != null) "${String.format(Locale.getDefault(), "%.1f", distance)} km" else "Distance unavailable")
                }, fontSize = 12.sp, color = FaceFitTextSecondary)
                Text(shop.address.ifBlank { "Address not provided" }, fontSize = 12.sp, color = FaceFitTextSecondary)
                Text("View shop & frames", fontSize = 11.sp, color = FaceFitTeal, fontWeight = FontWeight.SemiBold)
            }
            IconButton(onClick = onDirections, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Outlined.Directions, "Directions to ${shop.name}", tint = Color(0xFFFF6E36), modifier = Modifier.size(28.dp))
            }
        }
    }
}

internal fun openShopDirections(context: Context, shop: OpticalShop): Boolean {
    val target = if (shop.hasCoordinates) "${shop.latitude},${shop.longitude}" else "${shop.name} ${shop.address}"
    val uri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${Uri.encode(target)}")
    return runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)); true }.getOrDefault(false)
}


@Composable
private fun ShopMap(location: Location?,shops: List<OpticalShop>,modifier: Modifier) {
    val context=LocalContext.current
    val payload=remember(location,shops) {
        JSONObject().apply {
            if(location!=null) put("user",JSONArray(listOf(location.latitude,location.longitude)))
            put("shops",JSONArray().apply { shops.forEach { shop -> put(JSONObject().put("name",shop.name).put("lat",shop.latitude).put("lon",shop.longitude)) } })
        }.toString().replace("<","\\u003c")
    }
    val template=remember { context.assets.open("nearby/map.html").bufferedReader().use { it.readText() } }
    val html=remember(payload) { template.replace("/*SHOP_DATA*/",payload) }
    AndroidView(modifier=modifier,factory={ ctx -> WebView(ctx).apply {
        settings.javaScriptEnabled=true
        settings.allowFileAccess=false;settings.allowContentAccess=false
        settings.userAgentString=settings.userAgentString+" FaceFit/1.0"
        webViewClient=object: WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView,request: android.webkit.WebResourceRequest): Boolean {
                if(request.isForMainFrame && request.url.scheme=="https") {
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW,request.url)) }
                }
                return true
            }
        }
    } },update={ view -> if(view.tag!=html) { view.tag=html;view.loadDataWithBaseURL("https://facefit.local/",html,"text/html","UTF-8",null) } },
        onRelease={ it.stopLoading();it.destroy() })
}

package com.example.facefit.data.shops

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class NearbyShopsRepository {
    private val firestore get() = FirebaseFirestore.getInstance()

    fun observeShops(): kotlinx.coroutines.flow.Flow<List<OpticalShop>> = kotlinx.coroutines.flow.callbackFlow {
        val listener = firestore.collection("publicShops").whereEqualTo("status", "approved")
            .addSnapshotListener { snapshot, error ->
                if (error != null) close(error)
                else if (snapshot != null) trySend(snapshot.documents.mapNotNull { document ->
                    document.data?.let { OpticalShop.fromData(document.id, it) }
                }.sortedBy { it.name.lowercase(java.util.Locale.ROOT) })
            }
        awaitClose { listener.remove() }
    }

    fun observeShop(shopId: String): kotlinx.coroutines.flow.Flow<OpticalShop?> = kotlinx.coroutines.flow.callbackFlow {
        require(shopId.isNotBlank()) { "A shop ID is required" }
        val listener = firestore.collection("publicShops").document(shopId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) close(error)
                else if (snapshot != null) trySend(snapshot.data?.let { OpticalShop.fromData(snapshot.id, it) })
            }
        awaitClose { listener.remove() }
    }

    fun observeFrames(shopId: String): kotlinx.coroutines.flow.Flow<List<ShopFrame>> = kotlinx.coroutines.flow.callbackFlow {
        require(shopId.isNotBlank()) { "A shop ID is required" }
        val listener = firestore.collection("frames").whereEqualTo("retailId", shopId)
            .whereEqualTo("status", "approved").addSnapshotListener { snapshot, error ->
                if (error != null) close(error)
                else if (snapshot != null) trySend(snapshot.documents.mapNotNull { document ->
                    document.data?.let { ShopFrame.fromData(document.id, it, shopId) }
                }.sortedBy { it.name.lowercase(java.util.Locale.ROOT) })
            }
        awaitClose { listener.remove() }
    }
}


/** One foreground location request; never tracks the user in the background. */
suspend fun nearbyShopLocation(context: Context): Location? {
    val coarse=ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED
    val fine=ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
    if(!coarse && !fine) return null
    val manager=context.getSystemService(LocationManager::class.java)
    val providers=listOf(LocationManager.NETWORK_PROVIDER,LocationManager.GPS_PROVIDER)
        .filter { (it!=LocationManager.GPS_PROVIDER || fine) && runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
    val cached=providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
        .filter { SystemClock.elapsedRealtimeNanos()-it.elapsedRealtimeNanos in 0L..300_000_000_000L }
        .minByOrNull { it.accuracy }
    if(cached!=null) return cached
    for(provider in providers) {
        val result=withTimeoutOrNull(8_000L) {
            suspendCancellableCoroutine<Location?> { continuation ->
                val cancellation=CancellationSignal()
                continuation.invokeOnCancellation { cancellation.cancel() }
                try {
                    LocationManagerCompat.getCurrentLocation(manager,provider,cancellation,
                        ContextCompat.getMainExecutor(context)) { location ->
                        if(continuation.isActive) continuation.resume(location)
                    }
                } catch(_: SecurityException) { if(continuation.isActive) continuation.resume(null) }
            }
        }
        if(result!=null) return result
    }
    return null
}

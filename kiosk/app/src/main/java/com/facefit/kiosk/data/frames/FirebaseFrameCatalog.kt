package com.facefit.kiosk.data.frames

import android.net.Uri
import android.os.Looper
import android.os.SystemClock
import androidx.compose.ui.graphics.Color
import android.util.Log
import com.facefit.kiosk.facefit.recommendation.FrameCompatibility
import com.facefit.kiosk.model.FaceScanResult
import com.facefit.kiosk.model.FaceShape
import com.facefit.kiosk.model.FrameItem
import com.facefit.kiosk.model.FrameRecommendation
import com.facefit.kiosk.vto.ui.catalog.VtoFrame
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.util.Locale
import kotlin.math.roundToInt

class FirebaseFrameCatalog(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val compatibility: (() -> FrameCompatibility)? = null
) : FrameCatalog {
    private data class CatalogSnapshot(
        val frames: QuerySnapshot,
        val retailerId: String,
        val retailerCatalog: Boolean
    )
    private data class Inventory(val shopId: String, val loadedAt: Long, val data: CatalogSnapshot)

    private val cacheLock = Any()
    private var generation = 0L
    private var inventory: Inventory? = null

    fun clear() = synchronized(cacheLock) {
        generation++
        inventory = null
    }

    override fun framesFor(shopId: String): List<FrameItem> =
        loadFrames(shopId, shapeKey = null, refresh = true).map { it.frame }

    override fun recommendationsFor(profile: FaceScanResult, shopId: String): List<FrameRecommendation> {
        val shapeKey = profile.faceShape.takeUnless { it == FaceShape.Undetermined }
            ?.name?.lowercase(Locale.ROOT)
        return loadFrames(shopId, shapeKey).filter { it.frame.isAvailable }
    }

    private fun loadFrames(shopId: String, shapeKey: String?, refresh: Boolean = false): List<FrameRecommendation> {
        check(Looper.myLooper() != Looper.getMainLooper()) { "Call the catalog on a worker thread" }
        check(shopId.isNotBlank() && auth.currentUser?.uid == shopId) { "No authorized shop session" }
        val (snapshot, retailerId, retailerCatalog) = inventoryFor(shopId, refresh)
        check(auth.currentUser?.uid == shopId) { "Shop session changed during catalog read" }
        val assessorRatings = shapeKey?.let { compatibility?.invoke() }
        val ranked = snapshot.documents.mapNotNull { document ->
            document.toRecommendation(shapeKey, shopId, retailerId, retailerCatalog, assessorRatings)?.let { recommendation ->
                val rank = (document.get("displayOrder") as? Long) ?: Long.MAX_VALUE
                rank to recommendation
            }
        }
        check(snapshot.isEmpty || ranked.isNotEmpty()) { "Frame documents do not match the catalog schema" }
        return ranked.sortedWith(
            compareByDescending<Pair<Long, FrameRecommendation>> { it.second.compatibilityPercent ?: -1 }
                .thenBy { it.first }
                .thenBy { it.second.frame.id }
        ).map { it.second }
    }

    private fun inventoryFor(shopId: String, refresh: Boolean): CatalogSnapshot {
        val started = synchronized(cacheLock) {
            inventory?.takeIf { !refresh && it.shopId == shopId && SystemClock.elapsedRealtime() - it.loadedAt < 30_000L }
                ?.let { return it.data }
            generation
        }
        val data = runBlocking {
            withTimeout(10_000L) {
                try {
                    readInventory(shopId)
                } catch (error: FirebaseFirestoreException) {
                    if (error.code != FirebaseFirestoreException.Code.UNAVAILABLE &&
                        error.code != FirebaseFirestoreException.Code.DEADLINE_EXCEEDED
                    ) throw error
                    delay(350L)
                    readInventory(shopId)
                }
            }
        }
        synchronized(cacheLock) {
            check(started == generation && auth.currentUser?.uid == shopId) {
                "Shop session changed during catalog read"
            }
            inventory = Inventory(shopId, SystemClock.elapsedRealtime(), data)
        }
        return data
    }

    private suspend fun readInventory(shopId: String): CatalogSnapshot {
        val shop = firestore.collection("shops").document(shopId)
        val retailerId = shop.get(Source.SERVER).await().text("retailerId")
        check(retailerId != null && isPathSegment(retailerId)) { "No linked retailer catalog" }
        val frames = firestore.collection("retailers").document(retailerId).collection("frames")
        val published = try {
            frames.whereIn("status", listOf("live", "approved")).get(Source.SERVER).await()
        } catch (error: FirebaseFirestoreException) {
            // Older kiosk rules authorize only the exact live-status query.
            if (error.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) throw error
            frames.whereEqualTo("status", "live").get(Source.SERVER).await()
        }
        val retailerCatalog = CatalogSnapshot(published, retailerId, retailerCatalog = true)
        if (!published.isEmpty) return retailerCatalog

        return try {
            CatalogSnapshot(shop.collection("frames").get(Source.SERVER).await(), retailerId, retailerCatalog = false)
        } catch (error: FirebaseFirestoreException) {
            if (error.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) throw error
            // The primary catalog was authorized and empty. The legacy catalog is optional.
            retailerCatalog
        }
    }

    private fun DocumentSnapshot.toRecommendation(
        shapeKey: String?, shopId: String, retailerId: String, retailerCatalog: Boolean, assessorRatings: FrameCompatibility?
    ): FrameRecommendation? {
        val name = text("name")
        val shape = text("shape")
        val color = text("color")
        val material = text("material")
        val available = (get("isAvailable") as? Boolean) ?: (if (retailerCatalog) text("status") in listOf("live", "approved") else null)
        if (name == null || shape == null || color == null || material == null || available == null) {
            Log.w("FirebaseFrameCatalog", "Skipping frame with missing or invalid required fields")
            return null
        }
        val preview = if (!retailerCatalog) {
            storagePath(text("previewStoragePath"), "shops/$shopId/frame-previews/")
        } else {
            storagePath(text("productImagePath"), "frame-images/$retailerId/$id/")
        }
        val modelPrefix = "frame-models/$retailerId/$id/"
        val modelPath = sequenceOf(text("vtoAssetPath"), text("vtoAssetUrl"))
            .mapNotNull { storagePath(it, modelPrefix) }
            .firstOrNull { it.endsWith(".glb", ignoreCase = true) }
        val tryOn = if (modelPath != null || preview != null) {
            VtoFrame(
                name = name, category = shape, score = 0, color = Color.Gray,
                material = material, colorName = color, explanation = "",
                has3DModel = modelPath != null,
                modelStoragePath = modelPath,
                imageStoragePath = preview.takeIf { modelPath == null }
            )
        } else null
        val shapeScores = (get("recommendations") as? Map<*, *>)?.get(shapeKey) as? Map<*, *>
        val retailerScore = shapeScores.percent("compatibilityPercent")
        val assessorScore = if (retailerScore == null) {
            assessorRatings?.lookup(shapeKey, shape)?.score?.roundToInt()
        } else null
        return FrameRecommendation(
            frame = FrameItem(
                id, name, shape, color, material, available,
                tryOn = tryOn,
                previewStoragePath = preview,
                description = text("description"),
                measurementsMm = readMeasurements()
            ),
            compatibilityPercent = retailerScore ?: assessorScore,
            assessorBased = assessorScore != null,
            faceShapeScore = shapeScores.percent("faceShapeScore"),
            proportionFitScore = shapeScores.percent("proportionFitScore"),
            styleColorScore = shapeScores.percent("styleColorScore"),
            explanation = (shapeScores?.get("explanation") as? List<*>)
                .orEmpty().filterIsInstance<String>().map(String::trim).filter(String::isNotEmpty)
        )
    }

    private fun DocumentSnapshot.readMeasurements(): Map<String, Double> {
        val values = get("measurements") as? Map<*, *> ?: return emptyMap()
        return listOf("frameWidthMm", "lensWidthMm", "lensHeightMm", "bridgeWidthMm", "templeLengthMm")
            .mapNotNull { key ->
                val value = when (val raw = values[key]) {
                    is Number -> raw.toDouble()
                    is String -> raw.trim().toDoubleOrNull()
                    else -> null
                }
                value?.takeIf { it.isFinite() && it > 0 }?.let { key to it }
            }.toMap()
    }

    private fun storagePath(value: String?, prefix: String): String? {
        value ?: return null
        val uri = Uri.parse(value)
        val path = when (uri.scheme) {
            null -> value
            "firebase" -> value.removePrefix("firebase://")
            "gs", "https" -> {
                if (uri.scheme == "https" && uri.host != "firebasestorage.googleapis.com") return null
                val storage = FirebaseStorage.getInstance()
                val reference = runCatching { storage.getReferenceFromUrl(value) }.getOrNull()
                    ?: return null
                if (reference.bucket != storage.reference.bucket) return null
                reference.path.removePrefix("/")
            }
            else -> return null
        }
        return path.takeIf { isStorageChild(it, prefix) }
    }

    private fun isStorageChild(path: String, prefix: String): Boolean =
        path.startsWith(prefix) && path.removePrefix(prefix).split('/').all(::isPathSegment)

    private fun isPathSegment(value: String): Boolean =
        value.isNotBlank() && value != "." && value != ".." && '/' !in value && '\\' !in value

    private fun DocumentSnapshot.text(field: String): String? =
        (get(field) as? String)?.trim()?.takeIf(String::isNotEmpty)

    private fun Map<*, *>?.percent(field: String): Int? =
        (this?.get(field) as? Long)?.takeIf { it in 0L..100L }?.toInt()
}

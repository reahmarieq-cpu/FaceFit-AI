package com.example.facefit.data.session

import android.content.Context
import android.content.SharedPreferences
import com.example.facefit.facefit.analysis.FaceAnalyzer
import com.example.facefit.facefit.analysis.FacialGeometry
import org.json.JSONArray
import org.json.JSONObject
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Locale

/** Local scan history is isolated by Firebase account and available without a network. */
class UserPreferencesRepository(
    context: Context,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    data class LatestScan(
        val faceShape: String? = null,
        val metrics: FaceAnalyzer.FaceMetrics? = null,
        val userId: String? = null,
    )

    private val diagnosticsFile = context.applicationContext.filesDir.resolve("latest-scan-diagnostics.json")
    private val preferences = context.applicationContext
        .getSharedPreferences("profile_scan_history", Context.MODE_PRIVATE)

    fun observeLatestScan(): Flow<LatestScan> = callbackFlow {
        fun publish() {
            val prefix = accountPrefix()
            val shape = if (isHistoryEnabled()) preferences.getString(prefix + ".shape", null) else null
            val metrics = decodeMetrics(preferences.getString(prefix + ".result", null))
                ?.takeIf { it.faceShape == shape }
            trySend(LatestScan(shape, metrics, auth.currentUser?.uid))
        }
        val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> publish() }
        var registration: ListenerRegistration? = null
        var activeUid: String? = null
        var initialized = false
        val authListener = FirebaseAuth.AuthStateListener {
            val uid = it.currentUser?.uid
            if (!initialized || uid != activeUid) {
                initialized = true
                activeUid = uid
                registration?.remove()
                registration = null
                if (uid != null) {
                    registration = firestore.collection("users").document(uid).collection("faceScans")
                        .orderBy("createdAt", Query.Direction.DESCENDING).limit(1)
                        .addSnapshotListener { snapshot, _ ->
                            if (auth.currentUser?.uid == uid && isHistoryEnabled() &&
                                preferences.getBoolean("user.$uid.restoreCloud", true)) {
                                val doc = snapshot?.documents?.firstOrNull()
                                val shape = doc?.getString("faceShape")?.let(::normalize)
                                val timestamp = doc?.getTimestamp("createdAt")?.toDate()?.time
                                val prefix = "user.$uid"
                                if (shape != null && timestamp != null &&
                                    timestamp > maxOf(preferences.getLong(prefix + ".time", 0L),
                                        preferences.getLong(prefix + ".clearedAt", 0L))) {
                                    val metrics = doc.data?.let { fields ->
                                        val values = listOf("foreheadWidth", "jawline", "eyeSpacing")
                                        if (values.all { (fields[it] as? Number)?.toDouble()?.isFinite() == true }) {
                                            JSONObject().apply {
                                                values.forEach { key -> put(key, fields[key]) }
                                                put("faceShape", shape)
                                                put("shapeSampleCount", fields["shapeSampleCount"] ?: 1)
                                                (fields["shapeMeasurements"] as? Map<*, *>)?.let {
                                                    put("shapeMeasurements", JSONObject(it))
                                                }
                                            }.let { decodeMetrics(it.toString()) }
                                        } else null
                                    }
                                    val local = decodeMetrics(preferences.getString(prefix + ".result", null))
                                    val restored = if (metrics != null && local != null &&
                                        metrics.faceShape == local.faceShape &&
                                        metrics.foreheadWidth == local.foreheadWidth &&
                                        metrics.jawline == local.jawline && metrics.eyeSpacing == local.eyeSpacing) {
                                        local
                                    } else metrics
                                    preferences.edit().putString(prefix + ".shape", shape)
                                        .putString(prefix + ".result", restored?.let(::encodeMetrics))
                                        .putLong(prefix + ".time", timestamp).apply()
                                }
                            }
                        }
                }
            }
            publish()
        }
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        auth.addAuthStateListener(authListener)
        publish()
        awaitClose {
            preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
            auth.removeAuthStateListener(authListener)
            registration?.remove()
        }
    }

    @Synchronized
    fun saveLatestScan(metrics: FaceAnalyzer.FaceMetrics) {
        if (!isHistoryEnabled()) return
        val shape = normalize(metrics.faceShape) ?: return
        val prefix = accountPrefix()
        // Each scan stands on its own: remove any earlier-scan data a previous version kept.
        val oldKeys = preferences.all.keys.filter { it.endsWith(".recent") || it.endsWith(".shownFeatures") }
        preferences.edit().apply { oldKeys.forEach(::remove) }
            .putString(prefix + ".shape", shape)
            .putString(prefix + ".result", encodeMetrics(metrics.copy(faceShape = shape)))
            .putBoolean(prefix + ".restoreCloud", true)
            .putLong(prefix + ".time", System.currentTimeMillis()).apply()
    }

    fun isHistoryEnabled(): Boolean = preferences.getBoolean("historyEnabled", true)

    fun setHistoryEnabled(enabled: Boolean) {
        if (!enabled) clearScan()
        preferences.edit().putBoolean("historyEnabled", enabled).apply()
    }

    /**
     * Offload: removes this account's scan from the phone but keeps it restorable. Unlike
     * [clearScan] there is no tombstone, so the latest cloud scan comes back after the next sign-in.
     */
    @Synchronized
    fun clearLocalCopy() {
        diagnosticsFile.delete()
        val prefix = accountPrefix()
        preferences.edit().remove(prefix + ".shape").remove(prefix + ".result").remove(prefix + ".time")
            .putBoolean(prefix + ".restoreCloud", true).remove(prefix + ".clearedAt").apply()
    }

    /** Tombstones prevent the cloud listener from restoring an intentionally cleared scan. */
    @Synchronized
    fun clearScan() {
        diagnosticsFile.delete()
        val editor = preferences.edit()
        val prefixes = preferences.all.keys.filter { it.endsWith(".shape") || it.endsWith(".result") }
            .map { it.substringBeforeLast('.') }.toSet() + accountPrefix()
        prefixes.forEach { prefix ->
            editor.remove(prefix + ".shape").remove(prefix + ".result").remove(prefix + ".time")
                .putBoolean(prefix + ".restoreCloud", false)
                .putLong(prefix + ".clearedAt", System.currentTimeMillis())
        }
        editor.apply()
    }

    fun exportLocalScan(): JSONObject? = if (isHistoryEnabled()) {
        preferences.getString(accountPrefix() + ".result", null)?.let { runCatching { JSONObject(it) }.getOrNull() }
    } else null

    private fun encodeMetrics(metrics: FaceAnalyzer.FaceMetrics): String = JSONObject().apply {
        put("faceShape", metrics.faceShape)
        put("foreheadWidth", metrics.foreheadWidth)
        put("jawline", metrics.jawline)
        put("eyeSpacing", metrics.eyeSpacing)
        put("imageWidth", metrics.imageWidth)
        put("imageHeight", metrics.imageHeight)
        put("shapeSampleCount", metrics.shapeSampleCount)
        put("landmarks", JSONArray().apply {
            metrics.landmarks.forEach { point ->
                put(JSONArray().put(point.x).put(point.y).put(point.z))
            }
        })
        metrics.shapeMeasurements?.let {
            put("shapeMeasurements", JSONObject().put("lengthToWidth", it.lengthToWidth)
                .put("foreheadToCheek", it.foreheadToCheek).put("jawToCheek", it.jawToCheek)
                .put("chinToJaw", it.chinToJaw).put("jawAngleDegrees", it.jawAngle))
        }
    }.toString()

    private fun decodeMetrics(json: String?): FaceAnalyzer.FaceMetrics? {
        if (json == null) return null
        return runCatching {
            val data = JSONObject(json)
            val shape = normalize(data.getString("faceShape")) ?: return null
            fun number(key: String) = data.getDouble(key).toFloat().also { require(it.isFinite()) }
            val measurements = data.optJSONObject("shapeMeasurements")?.let {
                FacialGeometry.Measurements(it.getDouble("lengthToWidth").toFloat(),
                    it.getDouble("foreheadToCheek").toFloat(), it.getDouble("jawToCheek").toFloat(),
                    it.getDouble("chinToJaw").toFloat(), it.getDouble("jawAngleDegrees").toFloat())
            }
            val points = data.optJSONArray("landmarks")
            FaceAnalyzer.FaceMetrics(
                foreheadWidth = number("foreheadWidth"), jawline = number("jawline"),
                eyeSpacing = number("eyeSpacing"), faceShape = shape,
                imageWidth = data.optInt("imageWidth"), imageHeight = data.optInt("imageHeight"),
                isFaceDetected = true, shapeSampleCount = data.optInt("shapeSampleCount", 1),
                shapeMeasurements = measurements,
                landmarks = List(points?.length() ?: 0) { index ->
                    val point = requireNotNull(points).getJSONArray(index)
                    FaceAnalyzer.LandmarkPoint(point.getDouble(0).toFloat(),
                        point.getDouble(1).toFloat(), point.getDouble(2).toFloat())
                },
            )
        }.getOrNull()
    }

    private fun normalize(shape: String): String? = when (shape.trim().lowercase(Locale.ROOT)) {
        "oval" -> "Oval"
        "round" -> "Round"
        "square" -> "Square"
        "heart" -> "Heart"
        "long", "oblong" -> "Oblong"
        "diamond" -> "Diamond"
        "triangle", "triangular" -> "Triangle"
        else -> null
    }

    private fun accountPrefix(): String = auth.currentUser?.uid?.let { "user.$it" } ?: "guest"
}

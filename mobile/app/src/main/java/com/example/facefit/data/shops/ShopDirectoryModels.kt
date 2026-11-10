package com.example.facefit.data.shops

import java.net.URI

/** Only published shop information is read; private retailer applications are never queried. */
data class OpticalShop(
    val id: String,
    val name: String,
    val address: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val rating: Double? = null,
    val description: String = "",
    val phone: String = "",
    val website: String? = null,
    val openingHours: String = "",
    val imageUrl: String? = null
) {
    val hasCoordinates: Boolean
        get() = latitude != null && longitude != null && latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0

    fun distanceKm(lat: Double, lon: Double): Double? {
        if (!hasCoordinates || !lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        val a = Math.toRadians(latitude!! - lat)
        val b = Math.toRadians(longitude!! - lon)
        val h = kotlin.math.sin(a / 2).let { it * it } + kotlin.math.cos(Math.toRadians(lat)) *
            kotlin.math.cos(Math.toRadians(latitude)) * kotlin.math.sin(b / 2).let { it * it }
        return 6371.0088 * 2 * kotlin.math.asin(kotlin.math.sqrt(h.coerceIn(0.0, 1.0)))
    }

    companion object {
        internal fun fromData(id: String, data: Map<String, Any?>): OpticalShop? {
            if (data.text("status") != "approved" || data.text("name").isBlank()) return null
            val lat = (data["latitude"] as? Number)?.toDouble()
            val lon = (data["longitude"] as? Number)?.toDouble()
            val coordinatesValid = lat != null && lon != null && lat.isFinite() && lon.isFinite() &&
                lat in -90.0..90.0 && lon in -180.0..180.0
            return OpticalShop(
                id = id, name = data.text("name"), address = data.text("address"),
                latitude = lat.takeIf { coordinatesValid }, longitude = lon.takeIf { coordinatesValid },
                rating = (data["rating"] as? Number)?.toDouble()?.takeIf { it.isFinite() && it in 0.0..5.0 },
                description = data.text("description"), phone = data.text("phone"),
                website = publishedHttpsUrl(data["website"]), openingHours = data.text("openingHours"),
                imageUrl = publishedHttpsUrl(data["imageUrl"])
            )
        }
    }
}

/** A Firebase retailer listing, separate from the app's sample recommendation catalog. */
data class ShopFrame(
    val id: String,
    val retailId: String,
    val brand: String,
    val model: String,
    val type: String,
    val shape: String,
    val material: String,
    val color: String,
    val description: String,
    val imageUrl: String?
) {
    val name: String get() = listOf(brand, model).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Eyeglass frame" }

    companion object {
        internal fun fromData(id: String, data: Map<String, Any?>, shopId: String): ShopFrame? {
            if (shopId.isBlank() || data.text("status") != "approved" || data.text("retailId") != shopId) return null
            return ShopFrame(id, shopId, data.text("brand"), data.text("model"), data.text("type"),
                data.text("shape"), data.text("material"), data.text("color"), data.text("description"),
                publishedHttpsUrl(data["frameImage"]))
        }
    }
}

private fun Map<String, Any?>.text(key: String): String = (this[key] as? String)?.trim().orEmpty()

internal fun publishedHttpsUrl(value: Any?): String? {
    val text = (value as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return runCatching { URI(text) }.getOrNull()?.takeIf {
        it.scheme.equals("https", ignoreCase = true) && !it.host.isNullOrBlank() && it.userInfo == null
    }?.let { text }
}

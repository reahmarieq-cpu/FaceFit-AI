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
    val imageUrl: String? = null,
    val logoUrl: String? = null,
    /** Shop had an active FaceFit subscription when the website's shop sync last ran. */
    val subscribed: Boolean = false,
    /** When that subscription ends (epoch ms), or null when no end date was published. */
    val subscribedUntilMs: Long? = null
) {
    /** Subscribed right now: published as subscribed and its end date has not passed. */
    fun isSubscribedAt(nowMs: Long): Boolean = subscribed && (subscribedUntilMs == null || subscribedUntilMs > nowMs)

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
                imageUrl = publishedHttpsUrl(data["imageUrl"]),
                logoUrl = publishedHttpsUrl(data["logoUrl"]),
                subscribed = data["subscribed"] == true,
                subscribedUntilMs = epochMillis(data["subscribedUntil"])
            )
        }
    }
}

/** A published retailer product, separate from the sample recommendation catalog. */
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
    val imageUrl: String?,
    val displayName: String = "",
    val price: Double? = null,
    val currency: String? = null,
    val sizeLabel: String = "",
    val measurementsMm: Map<String, Double> = emptyMap()
) {
    val name: String get() = displayName.ifBlank {
        listOf(brand, model).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Eyeglass frame" }
    }

    companion object {
        internal fun fromData(id: String, data: Map<String, Any?>, shopId: String): ShopFrame? =
            parse(id, data, shopId, setOf("approved"))

        internal fun fromRetailerData(id: String, data: Map<String, Any?>, shopId: String): ShopFrame? =
            parse(id, data, shopId, setOf("live", "approved"))

        private fun parse(id: String, data: Map<String, Any?>, shopId: String, visible: Set<String>): ShopFrame? {
            val owners = listOf(data.text("retailerId"), data.text("retailId")).filter { it.isNotBlank() }
            if (shopId.isBlank() || data.text("status") !in visible || owners.isEmpty() || owners.any { it != shopId }) return null
            return ShopFrame(id, shopId, data.text("brand"), data.text("model"),
                data.text("type").ifBlank { data.text("frameType") }, data.text("shape"),
                data.text("material").ifBlank { data.text("frameMaterial") },
                data.text("color").ifBlank { data.text("frameColor") }, data.text("description"),
                listOf("productImageUrl", "frameImage", "imageURL", "imageUrl")
                    .firstNotNullOfOrNull { publishedHttpsUrl(data[it]) },
                data.text("name").ifBlank { data.text("frameName") },
                data["price"].number()?.takeIf { it >= 0.0 },
                data.text("currency").uppercase(java.util.Locale.ROOT).takeIf {
                    it.length == 3 && runCatching { java.util.Currency.getInstance(it) }.isSuccess
                }, data.text("size"), frameMeasurements(data["measurements"]))
        }
    }
}

/** Route keys only address product documents in the two supported catalogs. */
internal fun parseShopFrameKey(key: String): Pair<String, String>? {
    val parts = key.split('/', limit = 2)
    if (parts.size != 2 || parts[0] !in setOf("retailer", "catalog") ||
        parts[1].isBlank() || '/' in parts[1] || parts[1] in setOf(".", "..")) return null
    return parts[0] to parts[1]
}

private fun Any?.number(): Double? = when (this) {
    is Number -> toDouble()
    is String -> trim().toDoubleOrNull()
    else -> null
}?.takeIf { it.isFinite() }

private fun frameMeasurements(value: Any?): Map<String, Double> {
    val data = value as? Map<*, *> ?: return emptyMap()
    return linkedMapOf("frameWidthMm" to "Frame width", "lensWidthMm" to "Lens width",
        "lensHeightMm" to "Lens height", "bridgeWidthMm" to "Bridge width", "templeLengthMm" to "Temple length")
        .mapNotNull { (key, label) -> data[key].number()?.takeIf { it > 0.0 }?.let { label to it } }.toMap()
}

internal fun mergeShopFrames(vararg sources: List<ShopFrame>): List<ShopFrame> = sources.flatMap { it }
    .distinctBy { it.id }.sortedBy { it.name.lowercase(java.util.Locale.ROOT) }


/** Firestore Timestamp or Date to epoch ms; anything else is treated as no end date. */
private fun epochMillis(value: Any?): Long? = when (value) {
    is com.google.firebase.Timestamp -> value.toDate().time
    is java.util.Date -> value.time
    else -> null
}

private fun Map<String, Any?>.text(key: String): String = (this[key] as? String)?.trim().orEmpty()

internal fun publishedHttpsUrl(value: Any?): String? {
    val text = (value as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return runCatching { URI(text) }.getOrNull()?.takeIf {
        it.scheme.equals("https", ignoreCase = true) && !it.host.isNullOrBlank() && it.userInfo == null
    }?.let { text }
}

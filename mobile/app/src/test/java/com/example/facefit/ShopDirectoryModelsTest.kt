package com.example.facefit

import com.example.facefit.data.shops.OpticalShop
import com.example.facefit.data.shops.ShopFrame
import com.example.facefit.data.shops.publishedHttpsUrl
import org.junit.Assert.*
import org.junit.Test

class ShopDirectoryModelsTest {
    @Test fun onlyApprovedNamedShopsArePublished() {
        assertNull(OpticalShop.fromData("a", mapOf("name" to "Shop", "status" to "pending")))
        assertNull(OpticalShop.fromData("a", mapOf("name" to " ", "status" to "approved")))
        assertEquals("Optical", OpticalShop.fromData("a", mapOf("name" to " Optical ", "status" to "approved"))!!.name)
    }

    @Test fun malformedCoordinatesAndRatingsDoNotCrashOrBecomeNearby() {
        val shop = OpticalShop.fromData("a", mapOf("name" to "Shop", "status" to "approved",
            "latitude" to 999.0, "longitude" to 123.0, "rating" to Double.NaN))!!
        assertFalse(shop.hasCoordinates)
        assertNull(shop.rating)
        assertNull(shop.distanceKm(10.0, 123.0))
        assertNull(OpticalShop("b", "Shop", "", 10.0, 123.0).distanceKm(Double.NaN, 123.0))
    }

    @Test fun frameMustBeApprovedAndBelongToTheSelectedShop() {
        val fields = mapOf("retailId" to "shop-a", "status" to "approved", "brand" to "Brand", "model" to "Model")
        assertNull(ShopFrame.fromData("f", fields, "shop-b"))
        assertNull(ShopFrame.fromData("f", fields + ("status" to "hidden"), "shop-a"))
        assertEquals("Brand Model", ShopFrame.fromData("f", fields, "shop-a")!!.name)
    }

    @Test fun realFrameImageAndAttributesAreReadWithoutInventedValues() {
        val fields = mapOf("retailId" to "shop-a", "status" to "approved", "shape" to "Round",
            "material" to "Metal", "frameImage" to "https://example.com/frame.png")
        val frame = ShopFrame.fromData("f", fields, "shop-a")!!
        assertEquals("Round", frame.shape)
        assertEquals("Metal", frame.material)
        assertEquals("https://example.com/frame.png", frame.imageUrl)
        assertEquals("Eyeglass frame", frame.name)
        assertEquals("", frame.color)
    }

    @Test fun imageAndWebsiteLinksRequireHttpsWithoutEmbeddedCredentials() {
        for (value in listOf("javascript:alert(1)", "file:///tmp/a", "http://example.com/a", "https://user:pass@example.com/a", "invalid")) {
            assertNull(publishedHttpsUrl(value))
        }
        assertEquals("https://example.com/a", publishedHttpsUrl(" https://example.com/a "))
    }

    @Test fun onlyExplicitPublicAboutFieldsAreUsed() {
        val shop = OpticalShop.fromData("a", mapOf("status" to "approved", "name" to "Shop",
            "contactNumber" to "private-number", "permit" to "private-permit", "description" to "Public introduction"))!!
        assertEquals("Public introduction", shop.description)
        assertEquals("", shop.phone)
    }
}

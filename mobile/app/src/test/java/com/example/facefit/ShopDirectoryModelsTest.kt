package com.example.facefit

import com.example.facefit.data.shops.OpticalShop
import com.example.facefit.data.shops.ShopFrame
import com.example.facefit.data.shops.mergeShopFrames
import com.example.facefit.data.shops.parseShopFrameKey
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

    @Test fun shopLogoIsSeparateFromCoverAndUnsafeLogoFallsBackToNull() {
        val fields = mapOf("status" to "approved", "name" to "Shop",
            "logoUrl" to "https://example.com/logo.png", "imageUrl" to "https://example.com/cover.png")
        val shop = OpticalShop.fromData("a", fields)!!
        assertEquals("https://example.com/logo.png", shop.logoUrl)
        assertEquals("https://example.com/cover.png", shop.imageUrl)
        assertNull(OpticalShop.fromData("a", fields - "logoUrl")!!.logoUrl)
        assertNull(OpticalShop.fromData("a", fields + ("logoUrl" to "file:///a"))!!.logoUrl)
    }

    @Test fun actualRetailerAndOlderCatalogFrameSchemasAreSupported() {
        val retailer = ShopFrame.fromRetailerData("retailer/f", mapOf("retailerId" to "shop-a", "status" to "live",
            "name" to "Rose Frame", "shape" to "Rectangle", "productImageUrl" to "https://example.com/rose.png"), "shop-a")!!
        assertEquals("Rose Frame", retailer.name)
        assertEquals("https://example.com/rose.png", retailer.imageUrl)
        val old = ShopFrame.fromData("catalog/f", mapOf("retailerId" to "shop-a", "status" to "approved",
            "frameName" to "Black Frame", "frameType" to "Eyeglasses", "frameColor" to "Black",
            "frameMaterial" to "Metal", "imageURL" to "https://example.com/black.png"), "shop-a")!!
        assertEquals("Black Frame", old.name)
        assertEquals("Metal", old.material)
        assertEquals("Black", old.color)
        assertEquals("Eyeglasses", old.type)
        assertEquals("https://example.com/black.png", old.imageUrl)
    }

    @Test fun draftPendingWrongOwnerAndConflictingOwnershipAreExcluded() {
        val fields = mapOf("retailerId" to "shop-a", "status" to "live", "name" to "Frame")
        for (status in listOf("draft", "pending", "hidden")) {
            assertNull(ShopFrame.fromRetailerData("f", fields + ("status" to status), "shop-a"))
        }
        assertNull(ShopFrame.fromRetailerData("f", fields, "shop-b"))
        assertNull(ShopFrame.fromRetailerData("f", fields + ("retailId" to "shop-b"), "shop-a"))
        assertNull(ShopFrame.fromRetailerData("f", fields - "retailerId", "shop-a"))
        assertNull(ShopFrame.fromData("f", fields, "shop-a"))
    }

    @Test fun mergedQueriesDoNotDuplicateCatalogProductsOrLoseDistinctRetailerProducts() {
        val fields = mapOf("retailerId" to "shop-a", "status" to "approved", "frameName" to "A")
        val catalog = ShopFrame.fromData("catalog/f", fields, "shop-a")!!
        val retailer = ShopFrame.fromRetailerData("retailer/f", fields + ("name" to "B"), "shop-a")!!
        val merged = mergeShopFrames(listOf(catalog), listOf(catalog), listOf(retailer))
        assertEquals(listOf("catalog/f", "retailer/f"), merged.map { it.id })
    }

    @Test fun invalidProductImageDoesNotHideAnOtherwisePublishedFrame() {
        val fields = mapOf("retailerId" to "shop-a", "status" to "live", "name" to "A",
            "productImageUrl" to "file:///private", "imageURL" to "https://example.com/public.png")
        assertEquals("https://example.com/public.png", ShopFrame.fromRetailerData("f", fields, "shop-a")!!.imageUrl)
        assertNull(ShopFrame.fromRetailerData("f", fields - "imageURL", "shop-a")!!.imageUrl)
    }

    @Test fun productRouteOnlyAcceptsKnownCatalogsAndSingleDocumentIds() {
        assertEquals("retailer" to "frame-1", parseShopFrameKey("retailer/frame-1"))
        assertEquals("catalog" to "FRM001", parseShopFrameKey("catalog/FRM001"))
        for (key in listOf("", "users/private", "retailer/", "catalog/..", "catalog/a/b", "retailer")) {
            assertNull(parseShopFrameKey(key))
        }
    }

    @Test fun productPageReadsOnlyRealFinitePricesAndRecognizedCurrency() {
        val fields = mapOf("retailerId" to "a", "status" to "approved", "price" to 1299, "currency" to "php")
        val frame = ShopFrame.fromData("catalog/f", fields, "a")!!
        assertEquals(1299.0, frame.price!!, 0.0)
        assertEquals("PHP", frame.currency)
        assertNull(ShopFrame.fromData("f", fields - "currency", "a")!!.currency)
        assertNull(ShopFrame.fromData("f", fields + ("currency" to "invalid"), "a")!!.currency)
        for (price in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY, "unknown")) {
            assertNull(ShopFrame.fromData("f", fields + ("price" to price), "a")!!.price)
        }
    }

    @Test fun emptyOrInvalidMeasurementsAreNotInvented() {
        val fields = mapOf("retailerId" to "a", "status" to "live", "measurements" to
            mapOf("frameWidthMm" to 127, "lensWidthMm" to "53", "lensHeightMm" to null,
                "bridgeWidthMm" to -1, "templeLengthMm" to Double.NaN))
        val frame = ShopFrame.fromRetailerData("retailer/f", fields, "a")!!
        assertEquals(mapOf("Frame width" to 127.0, "Lens width" to 53.0), frame.measurementsMm)
        assertTrue(ShopFrame.fromRetailerData("f", fields - "measurements", "a")!!.measurementsMm.isEmpty())
    }
}

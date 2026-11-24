package com.example.facefit

import com.example.facefit.data.shops.OpticalShop
import com.example.facefit.data.shops.ShopFrame
import com.example.facefit.data.shops.rankShops
import com.example.facefit.data.shops.suitsFace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShopRankingTest {
    private val now = 1_795_000_000_000L

    private fun shop(id: String, subscribed: Boolean, km: Double?, untilMs: Long? = null) =
        OpticalShop(id, id, "", latitude = km?.let { 10.0 + it / 111.195 }, longitude = km?.let { 123.0 },
            subscribed = subscribed, subscribedUntilMs = untilMs)

    private fun order(shops: List<OpticalShop>, compatible: Set<String>) =
        rankShops(shops, compatible, now) { it.distanceKm(10.0, 123.0) }.map { it.id }

    @Test fun aSubscriptionPastItsEndDateRanksLikeANonSubscriber() {
        val shops = listOf(
            shop("expired-near", true, 0.1, untilMs = now - 1),
            shop("active-far", true, 2.0, untilMs = now + 86_400_000),
            shop("free-mid", false, 1.0)
        )
        assertEquals(listOf("active-far", "expired-near", "free-mid"), order(shops, emptySet()))
    }

    @Test fun subscribersFirstThenCompatibleThenNearestThenNonSubscribers() {
        val shops = listOf(
            shop("free-near-match", false, 0.1),
            shop("sub-far-match", true, 2.5),
            shop("sub-near-nomatch", true, 0.2),
            shop("sub-near-match", true, 1.0),
            shop("free-far", false, 2.0)
        )
        assertEquals(
            listOf("sub-near-match", "sub-far-match", "sub-near-nomatch", "free-near-match", "free-far"),
            order(shops, setOf("free-near-match", "sub-far-match", "sub-near-match"))
        )
    }

    @Test fun withoutAFaceScanSubscribersStillLeadAndDistanceDecides() {
        val shops = listOf(shop("free", false, 0.1), shop("sub-far", true, 2.0), shop("sub-near", true, 0.5))
        assertEquals(listOf("sub-near", "sub-far", "free"), order(shops, emptySet()))
    }

    @Test fun shopsWithoutDistanceComeAfterMappedShopsInTheirGroup() {
        val shops = listOf(shop("sub-unmapped", true, null), shop("sub-mapped", true, 2.9), shop("free", false, 0.1))
        assertEquals(listOf("sub-mapped", "sub-unmapped", "free"), order(shops, emptySet()))
    }

    @Test fun frameSuitsFaceOnlyWhenItsShapeOrTypeScoresAboveNeutral() {
        val scores = mapOf("Round" to 80.0, "Square" to 50.0, "Aviator" to 30.0)
        fun frame(shape: String, type: String = "") =
            ShopFrame("f", "s", "", "", type, shape, "", "", "", null)
        assertTrue(frame("Round").suitsFace { scores[it] })
        assertFalse(frame("Square").suitsFace { scores[it] })
        assertFalse(frame("Aviator").suitsFace { scores[it] })
        assertTrue(frame("Unknown", type = "Round").suitsFace { scores[it] })
        assertFalse(frame("").suitsFace { scores[it] })
    }

    @Test fun subscribedFlagIsReadOnlyWhenPublishedAsTrue() {
        val base = mapOf<String, Any?>("status" to "approved", "name" to "A")
        assertTrue(OpticalShop.fromData("a", base + ("subscribed" to true))!!.subscribed)
        assertFalse(OpticalShop.fromData("a", base + ("subscribed" to "true"))!!.subscribed)
        assertFalse(OpticalShop.fromData("a", base)!!.subscribed)
    }

    @Test fun subscriptionEndDateIsReadAndChecked() {
        val base = mapOf<String, Any?>("status" to "approved", "name" to "A", "subscribed" to true)
        val shop = OpticalShop.fromData("a", base + ("subscribedUntil" to java.util.Date(now + 1000)))!!
        assertEquals(now + 1000, shop.subscribedUntilMs)
        assertTrue(shop.isSubscribedAt(now))
        assertFalse(shop.isSubscribedAt(now + 1000))
        assertTrue(OpticalShop.fromData("a", base)!!.isSubscribedAt(now)) // No end date published.
    }
}

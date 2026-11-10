package com.example.facefit
import com.example.facefit.data.shops.OpticalShop
import org.junit.Assert.*
import org.junit.Test
class NearbyShopsTest {
    @Test fun distanceUsesKilometresAndIsZeroAtTheShop() {
        val shop=OpticalShop("a","Optical","Address",10.0,123.0)
        assertEquals(0.0,shop.distanceKm(10.0,123.0)!!,.00001)
        assertEquals(1.112,shop.distanceKm(10.01,123.0)!!,.002)
    }
    @Test fun invalidOrMissingCoordinatesCannotBeShownAsNearby() {
        for(shop in listOf(OpticalShop("a","A",""),OpticalShop("b","B","",91.0,123.0),OpticalShop("c","C","",Double.NaN,0.0))) {
            assertFalse(shop.hasCoordinates);assertNull(shop.distanceKm(10.0,123.0))
        }
    }
    @Test fun radiusDistinguishesShopsInsideAndOutsideThreeKm() {
        assertTrue(OpticalShop("a","A","",10.02,123.0).distanceKm(10.0,123.0)!!<3.0)
        assertTrue(OpticalShop("b","B","",10.04,123.0).distanceKm(10.0,123.0)!!>3.0)
    }
}

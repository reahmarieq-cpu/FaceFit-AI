package com.example.facefit

import androidx.test.platform.app.InstrumentationRegistry
import com.example.facefit.facefit.tryon.geometry.GlbParser
import org.junit.Assert.*
import org.junit.Test

class SquareTortoiseAssetTest {
    @Test
    fun selectedAssetLoadsActualGeometryWithPositionedTemples() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val parts = context.assets.open("frames/square_tortoise/SquareTortoise.glb").use {
            GlbParser.parseGlb(it.readBytes())
        }
        // The actual export contains 8,012 front and 756 triangles per arm.
        assertEquals(28572 * 9, parts.front.size + parts.temples.size)
        assertEquals(0, parts.front.size % 27)
        assertEquals(0, parts.temples.size % 27)
        assertTrue(parts.front.all { it.isFinite() })
        assertTrue(parts.temples.all { it.isFinite() })
        val arms = parts.temples.toList().chunked(9)
        assertTrue(arms.any { it[0] < -0.8f && it[2] > 1.5f })
        assertTrue(arms.any { it[0] > 0.8f && it[2] > 1.5f })
        assertTrue(arms.all { kotlin.math.abs(it[0]) > 0.7f })
    }
}
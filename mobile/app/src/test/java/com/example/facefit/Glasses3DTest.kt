package com.example.facefit

import com.example.facefit.logic.*
import org.junit.Assert.*
import org.junit.Test

class Glasses3DTest {
    @Test
    fun noseBridgeKeepsFrameAheadOfSkinAndForeheadStabilizesPitch() {
        val rig = GlassesRig.fromEyes(Vector3(150f, 200f, 0f), Vector3(250f, 200f, 0f),
            Vector3(200f, 350f, 0f), 1f, 0f, forehead = Vector3(200f, 100f, 0f),
            noseBridge = Vector3(200f, 205f, -30f))!!
        assertEquals(-38f, rig.origin.z, 0.001f)
        assertEquals(0f, rig.yAxis.z, 0.001f)
        assertEquals(200f, rig.origin.x, 0.001f)
        assertEquals(200f, rig.origin.y, 0.001f)
    }

    @Test
    fun frontAndTemplesHaveSeparateGeometryForSkinOcclusion() {
        for (browline in listOf(false, true)) {
            val parts = GlassesMesh.parts(browline)
            assertTrue(parts.front.isNotEmpty() && parts.temples.isNotEmpty())
            assertTrue(parts.front.toList().chunked(9).all { it[2] < 0.1f })
            assertTrue(parts.temples.toList().chunked(9).any { it[2] > 1.1f })
            assertArrayEquals(parts.front + parts.temples, GlassesMesh.create(browline), 0f)
        }
    }

    @Test
    fun modelsContainTwoStraightTempleArmsWithoutEarTipsAndValidNormals() {
        for (browline in listOf(false, true)) {
            val mesh = GlassesMesh.create(browline)
            assertEquals(0, mesh.size % 27)
            assertTrue(mesh.all { it.isFinite() })
            val vertices = mesh.toList().chunked(9)
            assertTrue(vertices.any { it[0] < -0.9f && it[2] > 1.1f })
            assertTrue(vertices.any { it[0] > 0.9f && it[2] > 1.1f })
            assertTrue(vertices.all { it[2] < 1.23f })
            val arms = vertices.filter { it[2] > 0.1f }
            assertTrue(arms.all { kotlin.math.abs(it[1] + 0.07f) <= 0.0141f })
            vertices.forEach { vertex ->
                assertEquals(1f, Vector3(vertex[3], vertex[4], vertex[5]).length(), 0.001f)
            }
        }
    }

    @Test
    fun clearLensesStayLightweightAndFinite() {
        for (browline in listOf(false, true)) {
            val parts = GlassesMesh.parts(browline)
            assertEquals(2 * 48 * 3 * 9, parts.lenses.size)
            assertTrue(parts.lenses.all { it.isFinite() })
            parts.lenses.toList().chunked(9).forEach {
                assertEquals(1f, Vector3(it[3], it[4], it[5]).length(), 0.001f)
            }
            assertTrue((parts.front.size + parts.temples.size + parts.lenses.size) / 27 < 2800)
        }
    }

    @Test
    fun turnsProjectLeftAndRightArmsOnOppositeSides() {
        val rightTurn = GlassesRig.fromEyes(Vector3(160f, 200f, -60f), Vector3(240f, 200f, 60f),
            Vector3(200f, 360f, 0f), 1f, 0f)!!
        val leftTurn = GlassesRig.fromEyes(Vector3(160f, 200f, 60f), Vector3(240f, 200f, -60f),
            Vector3(200f, 360f, 0f), 1f, 0f)!!
        assertTrue(rightTurn.transform(Vector3(-1.17f, 0f, 1.4f)).x < rightTurn.transform(Vector3(-1f, 0f, 0f)).x)
        assertTrue(leftTurn.transform(Vector3(1.17f, 0f, 1.4f)).x > leftTurn.transform(Vector3(1f, 0f, 0f)).x)
        assertTrue(rightTurn.zAxis.z > 0f && leftTurn.zAxis.z > 0f)
    }

    @Test
    fun mirroringRetainsAnOrthonormalDepthBasis() {
        val rig = GlassesRig.fromEyes(Vector3(330f, 200f, -20f), Vector3(170f, 240f, 20f),
            Vector3(250f, 400f, 30f), 1.2f, 0.1f)!!
        assertEquals(0f, rig.xAxis.dot(rig.yAxis), 0.001f)
        assertEquals(0f, rig.xAxis.dot(rig.zAxis), 0.001f)
        assertEquals(0f, rig.yAxis.dot(rig.zAxis), 0.001f)
        assertEquals(1f, rig.zAxis.length(), 0.001f)
        assertTrue(rig.matrix().all { it.isFinite() })
    }

    @Test
    fun collapsedAndNonFiniteTrackingIsRejected() {
        assertNull(GlassesRig.fromEyes(Vector3(0f, 0f, 0f), Vector3(0f, 0f, 0f), Vector3(0f, 30f, 0f), 1f, 0f))
        assertNull(GlassesRig.fromEyes(Vector3(Float.NaN, 0f, 0f), Vector3(30f, 0f, 0f), Vector3(0f, 30f, 0f), 1f, 0f))
    }
}
